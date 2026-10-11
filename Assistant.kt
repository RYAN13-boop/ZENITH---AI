package com.zenith.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pipeline: text -> parse -> permission/confirmation check -> Android action -> honest reply.
 * Calls and data deletion ALWAYS need an explicit yes. Unclear answers cancel.
 */
class Assistant(
    private val ctx: Context,
    private val db: Db,
    private val llm: LlmEngine
) {
    private sealed class Pending {
        data class Call(val c: Contact) : Pending()
        data class Pick(val options: List<Contact>) : Pending()
        class Clear : Pending()
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
        if (p != null && System.currentTimeMillis() - pendingAt <= 30_000) {
            return when (p) {
                is Pending.Pick -> {
                    val idx = pickIndex(text, p.options)
                    if (idx == null) "I couldn't tell which one you meant, so I cancelled. Nothing was done."
                    else askConfirm(p.options[idx])
                }
                is Pending.Call -> when (YesNo.parse(text)) {
                    true -> placeCall(p.c)
                    false -> "Okay, cancelled."
                    null -> "I didn't get a clear yes, so I did not call."
                }
                is Pending.Clear -> when (YesNo.parse(text)) {
                    true -> { db.clearAll(); "All local chats and memories were deleted." }
                    false -> "Okay, nothing was deleted."
                    null -> "I didn't get a clear yes, so nothing was deleted."
                }
            }
        }

        return when (val c = Parser.parse(text)) {
            is Cmd.Call -> startCall(c.name)
            is Cmd.OpenApp -> openApp(c.name)
            is Cmd.YouTube -> youtube(c.query)
            is Cmd.Remember -> { db.addMemory(c.text); "Saved to memory: ${c.text}" }
            is Cmd.Recall -> recall()
            is Cmd.Yesterday -> yesterday()
            is Cmd.SearchHistory -> searchHistory(c.query)
            is Cmd.ClearData -> { setPending(Pending.Clear()); "This deletes ALL chats and memories on this phone. Say yes to confirm." }
            is Cmd.None -> chat(text)
        }
    }

    private fun setPending(p: Pending) { pending = p; pendingAt = System.currentTimeMillis() }

    // ---------- calls ----------
    private fun has(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun startCall(name: String): String {
        if (!has(Manifest.permission.READ_CONTACTS)) {
            permissionNeeded = Manifest.permission.READ_CONTACTS
            return "I need permission to read contacts to find \"$name\". Allow it, then repeat your command."
        }
        val matches = Contacts.find(ctx, name)
        return when {
            matches.isEmpty() -> "I couldn't find a contact matching \"$name\"."
            matches.size == 1 -> askConfirm(matches[0])
            else -> {
                setPending(Pending.Pick(matches))
                "I found ${matches.size} matches: " +
                    matches.mapIndexed { i, c -> "${i + 1}) ${c.name}" }.joinToString(", ") +
                    ". Which one? Say the number."
            }
        }
    }

    private fun askConfirm(c: Contact): String {
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
        if (!has(Manifest.permission.CALL_PHONE)) {
            permissionNeeded = Manifest.permission.CALL_PHONE
            return "I need Phone permission to place the call. Allow it, then repeat your command."
        }
        return try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(c.number)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Starting the call to ${c.name}. (I can't verify whether it connected.)"
        } catch (e: Exception) {
            "I couldn't start the call (${e.javaClass.simpleName})."
        }
    }

    // ---------- apps ----------
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
            ?: return "I couldn't find an installed app called \"$n\"."
        val intent = pm.getLaunchIntentForPackage(hit.second) ?: return "${hit.first} can't be launched."
        return launch(intent, hit.first)
    }

    private fun youtube(q: String): String {
        val app = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(app); "Opened YouTube search for \"$q\"."
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))), "YouTube search in the browser")
        }
    }

    // ---------- memory ----------
    private val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

    private fun recall(): String {
        val m = db.memories()
        return if (m.isEmpty()) "I haven't saved any memories yet. Say \"remember that ...\" to add one."
        else "Saved memories:\n" + m.joinToString("\n") { "• $it" }
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
        return if (r.isEmpty()) "I found nothing in our history about \"$q\"."
        else "Found in our history:\n" + r.joinToString("\n") { "• ${fmt.format(Date(it.ts))}: ${it.text.take(100)}" }
    }

    // ---------- free chat (needs local model) ----------
    private fun chat(text: String): String {
        if (!llm.isReady()) {
            return "No local AI model is installed yet, so I can only run commands " +
                "(call, open app, search YouTube, remember, what did we discuss yesterday). " +
                "I won't pretend to answer."
        }
        return llm.generate(buildPrompt(text))
    }

    private fun buildPrompt(text: String): String {
        val sb = StringBuilder("You are ZENITH, a private on-device assistant. Use only facts in MEMORY/HISTORY; if unknown, say so.\n")
        val mem = db.memories().takeLast(20)
        if (mem.isNotEmpty()) sb.append("MEMORY:\n").append(mem.joinToString("\n") { "- $it" }).append("\n")
        val rel = db.search(text, 3)
        if (rel.isNotEmpty()) sb.append("HISTORY:\n").append(rel.joinToString("\n") { "- ${it.text.take(120)}" }).append("\n")
        return sb.append("USER: ").append(text).append("\nZENITH:").toString()
    }
}
