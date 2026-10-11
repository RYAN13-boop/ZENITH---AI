package com.zenith.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Main Assistant Orchestrator.
 * Manages intent routing, permission requests, explicit user confirmation safety checks,
 * phone automation actions, and local AI model generation.
 */
class Assistant(
    private val ctx: Context,
    private val db: Db,
    private val llm: LlmEngine
) {
    private sealed class Pending {
        data class Call(val c: Contact) : Pending()
        data class PickContact(val options: List<Contact>) : Pending()
        data class AnswerIncomingCall(val callerName: String) : Pending()
        data class SendMessage(val recipient: String, val text: String) : Pending()
        data class SocialAction(val platform: String, val action: String, val target: String) : Pending()
        object ClearData : Pending()
    }

    private var pending: Pending? = null
    private var pendingAt = 0L

    @Volatile private var permissionNeeded: String? = null

    val awaitingReply: Boolean get() = pending != null
    fun takePermissionRequest(): String? = permissionNeeded.also { permissionNeeded = null }

    fun handle(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""

        db.addMessage("user", text)
        val reply = try {
            route(text)
        } catch (e: Exception) {
            "Something went wrong (${e.javaClass.simpleName}). Nothing was done."
        }
        db.addMessage("assistant", reply)
        return reply
    }

    private fun route(text: String): String {
        val p = pending
        pending = null

        // Timeout multi-turn confirmation after 30 seconds
        if (p != null && System.currentTimeMillis() - pendingAt <= 30_000) {
            val confirmed = YesNo.parse(text)
            return when (p) {
                is Pending.PickContact -> {
                    val idx = pickIndex(text, p.options)
                    if (idx == null) "I couldn't tell which contact you meant, so I cancelled."
                    else askCallConfirm(p.options[idx])
                }

                is Pending.Call -> when (confirmed) {
                    true -> placeCall(p.c)
                    false -> "Call cancelled."
                    null -> "I didn't receive a clear yes, so I did not place the call."
                }

                is Pending.AnswerIncomingCall -> when (confirmed) {
                    true -> {
                        if (CallHandlingManager.acceptRingingCall(ctx)) {
                            "Answering call from ${p.callerName}."
                        } else {
                            permissionNeeded = Manifest.permission.ANSWER_PHONE_CALLS
                            "Permission needed to answer phone calls. Please grant it in settings."
                        }
                    }
                    false -> { CallHandlingManager.clearRingingCall(); "Call declined." }
                    null -> "I didn't get a clear answer, so the call was not answered."
                }

                is Pending.SendMessage -> when (confirmed) {
                    true -> executeSendMessage(p.recipient, p.text)
                    false -> "Message cancelled."
                    null -> "I didn't receive a clear yes, so the message was not sent."
                }

                is Pending.SocialAction -> when (confirmed) {
                    true -> executeSocialAction(p.platform, p.action, p.target)
                    false -> "Action cancelled."
                    null -> "I didn't receive a clear yes, so no action was taken."
                }

                is Pending.ClearData -> when (confirmed) {
                    true -> { db.clearAll(); "All local chats and memories were deleted." }
                    false -> "Data deletion cancelled."
                    null -> "I didn't receive a clear yes, so nothing was deleted."
                }
            }
        }

        // Check active ringing call announce state
        val ringingCall = CallHandlingManager.activeRingingCall
        if (ringingCall != null && (text.contains("call", true) || text.contains("who", true) || YesNo.parse(text) == true)) {
            setPending(Pending.AnswerIncomingCall(ringingCall.callerName))
            return "Sir, ${ringingCall.callerName} is calling. Should I answer?"
        }

        return when (val c = Parser.parse(text)) {
            // Calls
            is Cmd.Call -> startCall(c.name)
            is Cmd.SearchContacts -> searchContacts(c.name)
            is Cmd.AnswerCall -> {
                val call = CallHandlingManager.activeRingingCall
                if (call != null) {
                    setPending(Pending.AnswerIncomingCall(call.callerName))
                    "Sir, ${call.callerName} is calling. Should I answer?"
                } else "There is no incoming call right now."
            }
            is Cmd.RejectCall -> {
                CallHandlingManager.clearRingingCall()
                "Call rejected."
            }

            // Messaging
            is Cmd.SendMessage -> {
                setPending(Pending.SendMessage(c.recipient, c.message))
                "Sir, should I send this message to ${c.recipient}: \"${c.message}\"? Say yes or no."
            }
            is Cmd.ReadLatestMessage -> readLatestMessage()

            // Apps & Search
            is Cmd.OpenApp -> openApp(c.name)
            is Cmd.YouTubeSearch -> youtube(c.query)
            is Cmd.WebSearch -> webSearch(c.query)

            // Social Media
            is Cmd.InstagramSearch -> instagramSearch(c.query)
            is Cmd.InstagramFollow -> {
                setPending(Pending.SocialAction("Instagram", "follow", "current account"))
                "Sir, should I follow this account on Instagram? Say yes or no."
            }
            is Cmd.InstagramUnfollow -> {
                setPending(Pending.SocialAction("Instagram", "unfollow", "current account"))
                "Sir, should I unfollow this account on Instagram? Say yes or no."
            }
            is Cmd.InstagramLike -> {
                setPending(Pending.SocialAction("Instagram", "like", "current post"))
                "Sir, should I like this post on Instagram? Say yes or no."
            }

            // Accessibility Controls
            is Cmd.ActionBack -> executeAccessibilityAction("back")
            is Cmd.ActionHome -> executeAccessibilityAction("home")
            is Cmd.ActionScrollDown -> executeAccessibilityAction("scroll_down")
            is Cmd.ActionScrollUp -> executeAccessibilityAction("scroll_up")
            is Cmd.ActionMediaPause -> executeMediaAction("pause")
            is Cmd.ActionMediaPlay -> executeMediaAction("play")

            // Alarms & Navigation
            is Cmd.SetAlarm -> setAlarm(c.timeOrMinutes)
            is Cmd.NavigateTo -> navigateTo(c.destination)

            // Memory & History
            is Cmd.Remember -> { db.addMemory(c.text); "Saved to memory: \"${c.text}\"" }
            is Cmd.Forget -> {
                val count = db.deleteMemoryByKeyword(c.keyword)
                if (count > 0) "Removed $count memory item(s) matching \"${c.keyword}\"."
                else "No memories found matching \"${c.keyword}\"."
            }
            is Cmd.Recall -> recall()
            is Cmd.Yesterday -> yesterday()
            is Cmd.SearchHistory -> searchHistory(c.query)
            is Cmd.ExportData -> { db.exportJson(); "Exported conversation history and memory data." }
            is Cmd.ClearData -> {
                setPending(Pending.ClearData)
                "This will permanently delete ALL chats and saved memories on this phone. Say yes to confirm."
            }

            is Cmd.None -> chat(text)
        }
    }

    private fun setPending(p: Pending) {
        pending = p
        pendingAt = System.currentTimeMillis()
    }

    // ---------- Calls & Contacts ----------
    private fun hasPermission(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun startCall(name: String): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            permissionNeeded = Manifest.permission.READ_CONTACTS
            return "I need permission to read contacts to find \"$name\". Allow permission, then repeat command."
        }
        val matches = Contacts.find(ctx, name)
        return when {
            matches.isEmpty() -> "I couldn't find a contact matching \"$name\"."
            matches.size == 1 -> askCallConfirm(matches[0])
            else -> {
                setPending(Pending.PickContact(matches))
                "Found ${matches.size} contacts matching \"$name\": " +
                    matches.mapIndexed { i, c -> "${i + 1}) ${c.name}" }.joinToString(", ") +
                    ". Which one? Say the number."
            }
        }
    }

    private fun searchContacts(name: String): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            permissionNeeded = Manifest.permission.READ_CONTACTS
            return "I need permission to read contacts to search for \"$name\"."
        }
        val matches = Contacts.find(ctx, name)
        return if (matches.isEmpty()) "No contact found matching \"$name\"."
        else "Found contacts:\n" + matches.joinToString("\n") { "• ${it.name}: ${it.number}" }
    }

    private fun askCallConfirm(c: Contact): String {
        setPending(Pending.Call(c))
        return "Call ${c.name} (${c.number})? Say yes or no."
    }

    private fun pickIndex(text: String, opts: List<Contact>): Int? {
        val t = text.lowercase()
        Regex("\\d+").find(t)?.value?.toIntOrNull()?.let { if (it in 1..opts.size) return it - 1 }
        val words = listOf("first" to 0, "second" to 1, "third" to 2, "fourth" to 3, "fifth" to 4)
        words.firstOrNull { t.contains(it.first) && it.second < opts.size }?.let { return it.second }
        val byName = opts.indices.filter { t.contains(opts[it].name.lowercase()) }
        return if (byName.size == 1) byName[0] else null
    }

    private fun placeCall(c: Contact): String {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            permissionNeeded = Manifest.permission.CALL_PHONE
            return "I need Phone permission to place the call. Allow permission, then repeat command."
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(c.number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            "Placing call to ${c.name}."
        } catch (e: Exception) {
            "Couldn't start the call (${e.javaClass.simpleName})."
        }
    }

    // ---------- Messaging ----------
    private fun readLatestMessage(): String {
        val msg = ZenithNotificationService.latestMessage
            ?: return "No recent incoming messages available. Make sure Notification Access is enabled in ZENITH settings."
        return "Latest message from ${msg.sender}: \"${msg.text}\""
    }

    private fun executeSendMessage(recipient: String, text: String): String {
        val notifService = ZenithNotificationService.instance
        if (notifService != null && ZenithNotificationService.latestMessage?.sender?.contains(recipient, true) == true) {
            if (notifService.replyToLatest(text)) {
                return "Replied to ${ZenithNotificationService.latestMessage?.sender}: \"$text\"."
            }
        }

        // Fallback to launching SMS app intent
        return try {
            val matches = Contacts.find(ctx, recipient)
            val number = if (matches.isNotEmpty()) matches[0].number else recipient
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            "Opened messaging app with draft for $recipient."
        } catch (e: Exception) {
            "Could not send message automatically."
        }
    }

    // ---------- App & Social Media Control ----------
    private fun launch(i: Intent, label: String): String = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        "Launched $label."
    } catch (e: Exception) {
        "I couldn't open $label."
    }

    private fun openApp(name: String): String {
        val n = name.trim()
        if (n.equals("settings", true) || n == "सेटिंग्स") return launch(Intent(Settings.ACTION_SETTINGS), "Settings")

        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }

        val hit = apps.firstOrNull { it.first.equals(n, true) }
            ?: apps.firstOrNull { it.first.contains(n, true) }
            ?: return "I couldn't find an installed app named \"$n\"."

        val intent = pm.getLaunchIntentForPackage(hit.second) ?: return "${hit.first} cannot be launched."
        return launch(intent, hit.first)
    }

    private fun youtube(q: String): String {
        val appIntent = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(appIntent)
            "Opened YouTube search for \"$q\"."
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")), "YouTube search in browser")
        }
    }

    private fun webSearch(q: String): String {
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")), "Web search for \"$q\"")
    }

    private fun instagramSearch(q: String): String {
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$q/")), "Instagram profile for \"$q\"")
    }

    private fun executeSocialAction(platform: String, action: String, target: String): String {
        val service = ZenithAccessibilityService.instance
        if (service != null) {
            val clicked = when (action) {
                "follow" -> service.clickNodeWithText("Follow") || service.clickNodeWithText("follow")
                "unfollow" -> service.clickNodeWithText("Following") || service.clickNodeWithText("Unfollow")
                "like" -> service.clickNodeWithText("Like") || service.clickNodeWithText("like")
                else -> false
            }
            if (clicked) return "Executed $action on $platform."
        }
        return "Opened $platform. Please complete the $action action on screen."
    }

    private fun executeAccessibilityAction(action: String): String {
        val service = ZenithAccessibilityService.instance
            ?: return "Accessibility Service is not enabled. Enable ZENITH in Accessibility Settings to use screen controls."

        val success = when (action) {
            "back" -> service.performGoBack()
            "home" -> service.performGoHome()
            "scroll_down" -> service.performScrollDown()
            "scroll_up" -> service.performScrollUp()
            else -> false
        }
        return if (success) "Action completed." else "Could not execute $action."
    }

    private fun executeMediaAction(action: String): String {
        return "Media $action requested."
    }

    private fun setAlarm(timeOrMinutes: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "ZENITH Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            "Opening Clock to set alarm for $timeOrMinutes."
        } catch (e: Exception) {
            "Could not open Alarm app."
        }
    }

    private fun navigateTo(destination: String): String {
        return try {
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(mapIntent)
            "Launching navigation to $destination."
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}")), "Maps in browser")
        }
    }

    // ---------- Memory & History Retrieval ----------
    private val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

    private fun recall(): String {
        val mems = db.memories()
        return if (mems.isEmpty()) "I haven't saved any long-term memories yet. Say \"Zenith, remember that ...\" to add one."
        else "Saved memories:\n" + mems.joinToString("\n") { "• ${it.text}" }
    }

    private fun yesterday(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val msgs = db.between(cal.timeInMillis, todayStart).filter { it.role == "user" }
        return if (msgs.isEmpty()) "I have no stored conversation from yesterday."
        else "Yesterday you said:\n" + msgs.take(10).joinToString("\n") { "• ${it.text.take(100)}" }
    }

    private fun searchHistory(q: String): String {
        val r = db.search(q, 8)
        return if (r.isEmpty()) "I found nothing in history about \"$q\"."
        else "Found in history:\n" + r.joinToString("\n") { "• ${fmt.format(Date(it.ts))}: ${it.text.take(100)}" }
    }

    // ---------- Local AI Generation ----------
    private fun chat(text: String): String {
        if (!llm.isReady()) {
            return "No local AI model is currently loaded. " +
                "You can still use voice commands (calls, messaging, apps, search, memory). " +
                "Go to the Local Model tab in ZENITH to download a model for offline chat."
        }
        return llm.generate(buildPrompt(text))
    }

    private fun buildPrompt(text: String): String {
        val sb = StringBuilder("You are ZENITH, an on-device private AI assistant. Use facts from MEMORY/HISTORY when applicable.\n")
        val mem = db.memoryStrings().takeLast(15)
        if (mem.isNotEmpty()) {
            sb.append("MEMORY:\n").append(mem.joinToString("\n") { "- $it" }).append("\n")
        }
        val rel = db.search(text, 3)
        if (rel.isNotEmpty()) {
            sb.append("HISTORY:\n").append(rel.joinToString("\n") { "- ${it.text.take(120)}" }).append("\n")
        }
        return sb.append("USER: ").append(text).append("\nZENITH:").toString()
    }
}
