package com.zenith.assistant

/** Pure Kotlin (no Android imports) so it can be unit-tested. */
sealed class Cmd {
    data class Call(val name: String) : Cmd()
    data class OpenApp(val name: String) : Cmd()
    data class YouTube(val query: String) : Cmd()
    data class Remember(val text: String) : Cmd()
    data class SearchHistory(val query: String) : Cmd()
    object Recall : Cmd()
    object Yesterday : Cmd()
    object ClearData : Cmd()
    object None : Cmd()
}

/**
 * NOTE: this is a plain pattern matcher for a fixed set of commands in English,
 * Hindi and Marathi. It is NOT the AI model. Free-form conversation goes to the
 * local LLM (see LlmEngine) once one is installed.
 */
object Parser {
    private val I = RegexOption.IGNORE_CASE
    private val wake = Regex("^(?:hey\\s+zenith|ok\\s+zenith|zenith)[,.!:\\s]+", I)

    private val call1 = Regex("^(?:call|dial|phone)\\s+(.+)$", I)
    private val call2 = Regex("^(.+?)\\s+(?:ko|को)\\s+(?:call|कॉल|फोन|phone)\\s+(?:karo|kar|करो|कर)$", I)
    private val call3 = Regex("^(.+?)\\s+(?:la|ला)\\s+(?:call|कॉल|फोन|phone)\\s+(?:kar|कर|करा)$", I)

    private val yt1 = Regex("^(?:search\\s+youtube\\s+for|search\\s+youtube|youtube\\s+search(?:\\s+for)?|find\\s+on\\s+youtube)\\s+(.+)$", I)
    private val yt2 = Regex("^youtube\\s+(?:par|पर)\\s+(.+?)\\s+(?:search\\s+karo|खोजो|खोज|शोधा|शोध)$", I)

    private val open1 = Regex("^(?:open|launch|start)\\s+(.+)$", I)
    private val open2 = Regex("^(.+?)\\s+(?:kholo|khol|खोलो|खोल|उघड|उघडा)$", I)

    private val remember = Regex("^(?:remember(?:\\s+that)?|yaad\\s+rakho|याद\\s+रखो|लक्षात\\s+ठेव)\\s+(.+)$", I)
    private val recall = Regex("^(?:what\\s+do\\s+you\\s+remember|show\\s+(?:my\\s+)?memories|तुम्हें\\s+क्या\\s+याद\\s+है|तुला\\s+काय\\s+आठवते)$", I)
    private val yesterday = Regex("(?:what\\s+did\\s+we\\s+(?:discuss|talk\\s+about)\\s+yesterday|yesterday\\s+(?:chat|conversation)|कल\\s+क्या\\s+बात|काल\\s+काय\\s+बोलणं)", I)
    private val searchHist = Regex("^(?:search\\s+(?:history|chats)\\s+for|what\\s+did\\s+i\\s+say\\s+about)\\s+(.+)$", I)
    private val clear = Regex("^(?:clear|delete)\\s+all\\s+(?:data|memory|memories|chats)$", I)

    fun parse(raw: String): Cmd {
        val t = wake.replace(raw.trim(), "").trim().trimEnd('.', '!', '?', '।').trim()
        if (t.isEmpty()) return Cmd.None

        if (clear.matches(t)) return Cmd.ClearData
        if (recall.matches(t)) return Cmd.Recall
        if (yesterday.containsMatchIn(t)) return Cmd.Yesterday
        searchHist.find(t)?.let { return Cmd.SearchHistory(it.groupValues[1].trim()) }
        remember.find(t)?.let { return Cmd.Remember(it.groupValues[1].trim()) }

        yt1.find(t)?.let { return Cmd.YouTube(it.groupValues[1].trim()) }
        yt2.find(t)?.let { return Cmd.YouTube(it.groupValues[1].trim()) }

        call1.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }
        call2.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }
        call3.find(t)?.let { return Cmd.Call(it.groupValues[1].trim()) }

        open1.find(t)?.let { return Cmd.OpenApp(it.groupValues[1].trim()) }
        open2.find(t)?.let { return Cmd.OpenApp(it.groupValues[1].trim()) }
        return Cmd.None
    }
}

/** Only an explicit yes/no counts. Anything else (including silence) is NOT consent. */
object YesNo {
    private val yes = setOf("yes", "yeah", "yep", "ok", "okay", "sure", "confirm", "haan", "han", "ha", "हाँ", "हां", "हा", "हो", "होय", "ho", "hoy", "ji", "जी")
    private val no = setOf("no", "nope", "nahi", "nahin", "cancel", "stop", "नहीं", "नही", "नको", "nako", "ना")

    fun parse(s: String): Boolean? {
        val first = s.lowercase().trim().trimEnd('.', '!', '?', '।').split(Regex("\\s+")).firstOrNull() ?: return null
        return when {
            first in yes -> true
            first in no -> false
            else -> null
        }
    }
}
