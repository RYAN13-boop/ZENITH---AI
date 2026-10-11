package com.zenith.assistant

/** Sealed class representing parsed assistant intent commands. */
sealed class Cmd {
    // ---------- Phone & Contacts ----------
    data class Call(val name: String) : Cmd()
    object AnswerCall : Cmd()
    object RejectCall : Cmd()
    data class SearchContacts(val name: String) : Cmd()

    // ---------- Messaging ----------
    data class SendMessage(val recipient: String, val message: String) : Cmd()
    object ReadLatestMessage : Cmd()

    // ---------- Apps & Media ----------
    data class OpenApp(val name: String) : Cmd()
    data class YouTubeSearch(val query: String) : Cmd()
    data class WebSearch(val query: String) : Cmd()

    // ---------- Social Media Automation ----------
    data class InstagramSearch(val query: String) : Cmd()
    object InstagramFollow : Cmd()
    object InstagramUnfollow : Cmd()
    object InstagramLike : Cmd()

    // ---------- Accessibility System Control ----------
    object ActionBack : Cmd()
    object ActionHome : Cmd()
    object ActionScrollDown : Cmd()
    object ActionScrollUp : Cmd()
    object ActionMediaPause : Cmd()
    object ActionMediaPlay : Cmd()
    object ActionMediaNext : Cmd()

    // ---------- Alarms & Navigation ----------
    data class SetAlarm(val timeOrMinutes: String) : Cmd()
    data class NavigateTo(val destination: String) : Cmd()

    // ---------- Memory & History ----------
    data class Remember(val text: String) : Cmd()
    data class Forget(val keyword: String) : Cmd()
    object Recall : Cmd()
    object Yesterday : Cmd()
    data class SearchHistory(val query: String) : Cmd()
    object ExportData : Cmd()
    object ClearData : Cmd()

    // ---------- General / Fallback ----------
    object None : Cmd()
}

/**
 * Robust regex-based command parser for English, Hindi, and Marathi.
 * Extracted keywords map directly to structured Cmd intents.
 */
object Parser {
    private val I = RegexOption.IGNORE_CASE
    private val wake = Regex("^(?:hey\\s+zenith|ok\\s+zenith|zenith)[,.!:\\s]+", I)

    // Calls
    private val call1 = Regex("^(?:call|dial|phone)\\s+(.+)$", I)
    private val call2 = Regex("^(.+?)\\s+(?:ko|को)\\s+(?:call|कॉल|फोन|phone)\\s+(?:karo|kar|करो|कर)$", I)
    private val call3 = Regex("^(.+?)\\s+(?:la|ला)\\s+(?:call|कॉल|फोन|phone)\\s+(?:kar|कर|करा)$", I)
    private val searchContacts = Regex("^(?:find|search|lookup)\\s+(.+)\\s+(?:in\\s+contacts|in\\s+my\\s+contacts)$", I)

    // Call answering
    private val answerCall = Regex("^(?:answer|accept|pick\\s+up)\\s*(?:the\\s*call)?$", I)
    private val rejectCall = Regex("^(?:reject|decline|cut)\\s*(?:the\\s*call)?$", I)

    // Messaging
    private val sendMsg1 = Regex("^(?:tell|send\\s+message\\s+to|message|text)\\s+(.+?)\\s+(?:that|saying)?\\s+(.+)$", I)
    private val sendMsg2 = Regex("^(.+?)\\s+(?:ko|को)\\s+message\\s+(?:karo|bhejo|भेजो)\\s+(.+)$", I)
    private val readMsg = Regex("^(?:read|check)\\s+(?:my\\s+)?(?:latest|new|unread)?\\s+(?:message|messages|notification)$", I)

    // YouTube & Web Search
    private val yt1 = Regex("^(?:search\\s+youtube\\s+for|search\\s+youtube|youtube\\s+search(?:\\s+for)?|find\\s+on\\s+youtube)\\s+(.+)$", I)
    private val yt2 = Regex("^youtube\\s+(?:par|पर)\\s+(.+?)\\s+(?:search\\s+karo|खोजो|खोज|शोधा|शोध)$", I)
    private val webSearch = Regex("^(?:google|web\\s+search|search\\s+web\\s+for|search\\s+for)\\s+(.+)$", I)

    // App Control
    private val open1 = Regex("^(?:open|launch|start)\\s+(.+)$", I)
    private val open2 = Regex("^(.+?)\\s+(?:kholo|khol|खोलो|खोल|उघड|उघडा)$", I)

    // Social Media Actions
    private val igSearch = Regex("^(?:find|search)\\s+(.+?)\\s+(?:on|in)\\s+instagram$", I)
    private val igFollow = Regex("^(?:follow|follow\\s+this\\s+account|follow\\s+him|follow\\s+her)$", I)
    private val igUnfollow = Regex("^(?:unfollow|unfollow\\s+this\\s+account)$", I)
    private val igLike = Regex("^(?:like|like\\s+this\\s+post|like\\s+this\\s+reel|like\\s+video)$", I)

    // Global Accessibility Actions
    private val goBack = Regex("^(?:go\\s+back|back|पीछे\\s+जाओ|मागे\\s+जा)$", I)
    private val goHome = Regex("^(?:go\\s+home|home\\s+screen)$", I)
    private val scrollDown = Regex("^(?:scroll\\s+down|next\\s+video|scroll|नीचे\\s+करो|खाली\\s+करा)$", I)
    private val scrollUp = Regex("^(?:scroll\\s+up|previous\\s+video|ऊपर\\s+करो|वर\\s+करा)$", I)
    private val pauseMedia = Regex("^(?:pause|stop\\s+video|pause\\s+video|रुकिए|थांबा)$", I)
    private val playMedia = Regex("^(?:play|resume|play\\s+video|चलाओ|सुरू\\s+करा)$", I)

    // Navigation & Alarm
    private val navigate = Regex("^(?:navigate\\s+to|take\\s+me\\s+to|directions\\s+to)\\s+(.+)$", I)
    private val alarm = Regex("^(?:set\\s+alarm\\s+for|alarm\\s+for)\\s+(.+)$", I)

    // Memory
    private val remember = Regex("^(?:remember(?:\\s+that)?|yaad\\s+rakho|याद\\s+रखो|लक्षात\\s+ठेव)\\s+(.+)$", I)
    private val forget = Regex("^(?:forget|delete\\s+memory\\s+about|remove\\s+memory\\s+about)\\s+(.+)$", I)
    private val recall = Regex("^(?:what\\s+do\\s+you\\s+remember|show\\s+(?:my\\s+)?memories|तुम्हें\\s+क्या\\s+याद\\s+है|तुला\\s+काय\\s+आठवते)$", I)
    private val yesterday = Regex("(?:what\\s+did\\s+we\\s+(?:discuss|talk\\s+about)\\s+yesterday|yesterday\\s+(?:chat|conversation)|कल\\s+क्या\\s+बात|काल\\s+काय\\s+बोलणं)", I)
    private val searchHist = Regex("^(?:search\\s+(?:history|chats)\\s+for|what\\s+did\\s+i\\s+say\\s+about)\\s+(.+)$", I)
    private val exportData = Regex("^(?:export|backup)\\s+(?:my\\s+)?(?:data|history|memories)$", I)
    private val clear = Regex("^(?:clear|delete)\\s+all\\s+(?:data|memory|memories|chats)$", I)

    fun parse(raw: String): Cmd {
        val t = wake.replace(raw.trim(), "").trim().trimEnd('.', '!', '?', '।').trim()
        if (t.isEmpty()) return Cmd.None

        // Memory & System Data
        if (clear.matches(t)) return Cmd.ClearData
        if (exportData.matches(t)) return Cmd.ExportData
        if (recall.matches(t)) return Cmd.Recall
        if (yesterday.containsMatchIn(t)) return Cmd.Yesterday
        forget.find(t)?.let { return Cmd.Forget(it.groupValues[1].trim()) }
        searchHist.find(t)?.let { return Cmd.SearchHistory(it.groupValues[1].trim()) }
        remember.find(t)?.let { return Cmd.Remember(it.groupValues[1].trim()) }

        // Call Answering
        if (answerCall.matches(t)) return Cmd.AnswerCall
        if (rejectCall.matches(t)) return Cmd.RejectCall

        // Messaging
        if (readMsg.matches(t)) return Cmd.ReadLatestMessage
        sendMsg1.find(t)?.let { return Cmd.SendMessage(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        sendMsg2.find(t)?.let { return Cmd.SendMessage(it.groupValues[1].trim(), it.groupValues[2].trim()) }

        // YouTube & Search
        yt1.find(t)?.let { return Cmd.YouTubeSearch(it.groupValues[1].trim()) }
        yt2.find(t)?.let { return Cmd.YouTubeSearch(it.groupValues[1].trim()) }
        webSearch.find(t)?.let { return Cmd.WebSearch(it.groupValues[1].trim()) }

        // Social Media
        igSearch.find(t)?.let { return Cmd.InstagramSearch(it.groupValues[1].trim()) }
        if (igFollow.matches(t)) return Cmd.InstagramFollow
        if (igUnfollow.matches(t)) return Cmd.InstagramUnfollow
        if (igLike.matches(t)) return Cmd.InstagramLike

        // Accessibility Actions
        if (goBack.matches(t)) return Cmd.ActionBack
        if (goHome.matches(t)) return Cmd.ActionHome
        if (scrollDown.matches(t)) return Cmd.ActionScrollDown
        if (scrollUp.matches(t)) return Cmd.ActionScrollUp
        if (pauseMedia.matches(t)) return Cmd.ActionMediaPause
        if (playMedia.matches(t)) return Cmd.ActionMediaPlay

        // Calls
        searchContacts.find(t)?.let { return Cmd.SearchContacts(it.groupValues[1].trim()) }
        call1.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }
        call2.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }
        call3.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }

        // Navigation & Alarms
        navigate.find(t)?.let { return Cmd.NavigateTo(it.groupValues[1].trim()) }
        alarm.find(t)?.let { return Cmd.SetAlarm(it.groupValues[1].trim()) }

        // Apps
        open1.find(t)?.let { return Cmd.OpenApp(it.groupValues[1].trim()) }
        open2.find(t)?.let { return Cmd.OpenApp(it.groupValues[1].trim()) }

        return Cmd.None
    }
}

/** Parses affirmative and negative user answers for confirmation safety checks. */
object YesNo {
    private val yes = setOf(
        "yes", "yeah", "yep", "ok", "okay", "sure", "confirm", "accept", "answer",
        "haan", "han", "ha", "हाँ", "हां", "हा", "हो", "होय", "ho", "hoy", "ji", "जी", "करो", "पाठवा"
    )
    private val no = setOf(
        "no", "nope", "nahi", "nahin", "cancel", "stop", "reject", "decline",
        "नहीं", "नही", "नको", "nako", "ना", "मत করো", "नाही"
    )

    fun parse(s: String): Boolean? {
        val first = s.lowercase().trim().trimEnd('.', '!', '?', '।').split(Regex("\\s+")).firstOrNull() ?: return null
        return when {
            first in yes -> true
            first in no -> false
            else -> null
        }
    }
}
