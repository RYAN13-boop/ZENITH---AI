package com.zenith.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var assistant: Assistant
    private lateinit var voice: Voice
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var langBtn: Button
    private lateinit var speakBtn: Button

    private val exec = Executors.newSingleThreadExecutor()
    private val langs = listOf("English" to "en-IN", "हिन्दी" to "hi-IN", "मराठी" to "mr-IN")
    private var langIdx = 0
    private var speakOn = true

    private val cyan = Color.parseColor("#00E5FF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        assistant = Assistant(this, db, NoModelEngine())
        voice = Voice(
            this,
            onText = { t -> runOnUiThread { submit(t, true) } },
            onMessage = { m -> runOnUiThread { status.text = m } },
            onListening = { on -> runOnUiThread { status.text = if (on) "Listening… (${langs[langIdx].first})" else "Idle" } }
        )
        buildUi()
        exec.execute {
            val old = db.recent(30)
            runOnUiThread {
                old.forEach { chat.append((if (it.role == "user") "You: " else "ZENITH: ") + it.text + "\n\n") }
                scrollDown()
            }
        }
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }
        root.addView(TextView(this).apply {
            text = "ZENITH"; textSize = 28f; setTextColor(cyan); letterSpacing = 0.3f
        })
        status = TextView(this).apply {
            text = "Idle · local only · no cloud AI"; textSize = 12f; setTextColor(Color.GRAY)
        }
        root.addView(status)

        chat = TextView(this).apply { textSize = 15f; setTextColor(Color.parseColor("#E0F7FA")); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(chat) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        val row1 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Type or tap the mic"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        row1.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(btn("Send") {
            val t = input.text.toString(); input.setText(""); submit(t, false)
        })
        row1.addView(btn("🎤") { onMic() })
        root.addView(row1)

        val row2 = LinearLayout(this)
        langBtn = btn("Lang: ${langs[0].first}") {
            langIdx = (langIdx + 1) % langs.size
            voice.langTag = langs[langIdx].second
            langBtn.text = "Lang: ${langs[langIdx].first}"
        }
        speakBtn = btn("Speak: ON") {
            speakOn = !speakOn
            speakBtn.text = if (speakOn) "Speak: ON" else "Speak: OFF"
            if (!speakOn) voice.stopSpeaking()
        }
        row2.addView(langBtn); row2.addView(speakBtn)
        row2.addView(btn("Stop") { voice.stopSpeaking() })
        row2.addView(btn("Delete") { confirmDelete() })
        root.addView(row2)
        setContentView(root)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage("Delete ALL chats and memories on this phone?")
            .setPositiveButton("Delete") { _, _ ->
                exec.execute { db.clearAll() }
                chat.text = ""
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }

    private fun append(who: String, text: String) { chat.append("$who: $text\n\n"); scrollDown() }

    private fun submit(text: String, fromVoice: Boolean) {
        if (text.isBlank()) return
        append("You", text)
        exec.execute {
            val reply = assistant.handle(text)
            val perm = assistant.takePermissionRequest()
            runOnUiThread {
                append("ZENITH", reply)
                if (perm != null) requestPermissions(arrayOf(perm), 1)
                if (speakOn && reply.isNotEmpty()) {
                    val relisten = fromVoice && assistant.awaitingReply
                    voice.speak(reply, if (relisten) ({ voice.listen() }) else null)
                } else if (fromVoice && assistant.awaitingReply) voice.listen()
            }
        }
    }

    private fun onMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
        } else voice.listen()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val ok = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        when (code) {
            2 -> if (ok) voice.listen() else status.text = "Microphone permission denied. You can still type."
            1 -> append("ZENITH", if (ok) "Permission granted. Please repeat your command." else "Permission denied, so I can't do that.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
        exec.shutdown()
    }
}
