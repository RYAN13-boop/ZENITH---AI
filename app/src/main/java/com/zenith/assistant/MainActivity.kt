package com.zenith.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/**
 * Polished ZENITH Android UI featuring Chat Assistant,
 * Local AI Model Download Manager, Memory & History Control,
 * Wake Word Foreground Service controls, and Privacy Dashboard.
 */
class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var assistant: Assistant
    private lateinit var voice: Voice
    private lateinit var modelManager: ModelDownloadManager
    private lateinit var llmEngine: LlmEngine

    // UI Layout containers
    private lateinit var rootLayout: LinearLayout
    private lateinit var headerStatus: TextView
    private lateinit var contentContainer: LinearLayout

    // Tab buttons
    private lateinit var tabChatBtn: Button
    private lateinit var tabModelsBtn: Button
    private lateinit var tabMemoryBtn: Button
    private lateinit var tabServiceBtn: Button
    private lateinit var tabPrivacyBtn: Button

    // Chat Tab UI
    private lateinit var chatTextView: TextView
    private lateinit var chatScrollView: ScrollView
    private lateinit var inputEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var langBtn: Button
    private lateinit var speakBtn: Button

    // Model Manager UI
    private lateinit var modelCardsContainer: LinearLayout

    // Memory UI
    private lateinit var memoryListContainer: LinearLayout

    private val exec = Executors.newSingleThreadExecutor()
    private val langs = listOf("English" to "en-IN", "हिन्दी" to "hi-IN", "मराठी" to "mr-IN")
    private var langIdx = 0
    private var speakOn = true
    private var currentTab = "chat"

    private val darkBg = Color.parseColor("#0B0E14")
    private val cardBg = Color.parseColor("#161B22")
    private val cyan = Color.parseColor("#00E5FF")
    private val textPrimary = Color.parseColor("#E0F7FA")
    private val textSecondary = Color.parseColor("#8B949E")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        llmEngine = LocalLlmEngine(this)

        // Automatically load previously downloaded model if available
        modelManager = ModelDownloadManager(this)
        val downloaded = modelManager.getDownloadedModels()
        if (downloaded.isNotEmpty()) {
            llmEngine.loadModel(downloaded.first())
        }

        assistant = Assistant(this, db, llmEngine)
        voice = Voice(
            this,
            onText = { t -> runOnUiThread { submitChat(t, fromVoice = true) } },
            onMessage = { m -> runOnUiThread { statusTextView.text = m } },
            onListening = { listening ->
                runOnUiThread {
                    statusTextView.text = if (listening) "Listening… (${langs[langIdx].first})" else "Idle"
                }
            }
        )

        buildUi()
        switchTab("chat")

        // Auto-listen if launched via Wake Word
        if (intent?.getBooleanExtra("EXTRA_AUTO_LISTEN", false) == true) {
            onMicClicked()
        }
    }

    private fun buildUi() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(darkBg)
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }

        // Header Title & Status
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(TextView(this).apply {
            text = "ZENITH"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            letterSpacing = 0.25f
        })
        headerStatus = TextView(this).apply {
            text = "Local AI: ${llmEngine.getModelName()} · Privacy First"
            textSize = 12f
            setTextColor(textSecondary)
        }
        header.addView(headerStatus)
        rootLayout.addView(header)

        // Navigation Tabs Bar
        val tabsScroll = ScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        tabChatBtn = createTabBtn("Chat") { switchTab("chat") }
        tabModelsBtn = createTabBtn("AI Models") { switchTab("models") }
        tabMemoryBtn = createTabBtn("Memory") { switchTab("memory") }
        tabServiceBtn = createTabBtn("Wake Word") { switchTab("service") }
        tabPrivacyBtn = createTabBtn("Privacy") { switchTab("privacy") }

        tabsRow.addView(tabChatBtn)
        tabsRow.addView(tabModelsBtn)
        tabsRow.addView(tabMemoryBtn)
        tabsRow.addView(tabServiceBtn)
        tabsRow.addView(tabPrivacyBtn)
        tabsScroll.addView(tabsRow)
        rootLayout.addView(tabsScroll)

        // Dynamic Content Area
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(contentContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(8)
        })

        setContentView(rootLayout)
    }

    private fun createTabBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundColor(cardBg)
        setOnClickListener { onClick() }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        contentContainer.removeAllViews()

        val tabButtons = listOf(tabChatBtn, tabModelsBtn, tabMemoryBtn, tabServiceBtn, tabPrivacyBtn)
        tabButtons.forEach { it.setTextColor(textSecondary) }

        when (tab) {
            "chat" -> {
                tabChatBtn.setTextColor(cyan)
                buildChatTab()
            }
            "models" -> {
                tabModelsBtn.setTextColor(cyan)
                buildModelsTab()
            }
            "memory" -> {
                tabMemoryBtn.setTextColor(cyan)
                buildMemoryTab()
            }
            "service" -> {
                tabServiceBtn.setTextColor(cyan)
                buildServiceTab()
            }
            "privacy" -> {
                tabPrivacyBtn.setTextColor(cyan)
                buildPrivacyTab()
            }
        }
    }

    // ---------- Chat Tab ----------
    private fun buildChatTab() {
        statusTextView = TextView(this).apply {
            text = "Idle · On-Device Processing"
            textSize = 12f
            setTextColor(textSecondary)
        }
        contentContainer.addView(statusTextView)

        chatTextView = TextView(this).apply {
            textSize = 15f
            setTextColor(textPrimary)
            setTextIsSelectable(true)
        }
        chatScrollView = ScrollView(this).apply { addView(chatTextView) }
        contentContainer.addView(chatScrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(6)
        })

        // Input Controls
        val inputRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        inputEditText = EditText(this).apply {
            hint = "Type or tap 🎤 for voice command"
            setTextColor(Color.WHITE)
            setHintTextColor(textSecondary)
        }
        inputRow.addView(inputEditText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(Button(this).apply {
            text = "Send"
            setOnClickListener {
                val t = inputEditText.text.toString()
                inputEditText.setText("")
                submitChat(t, fromVoice = false)
            }
        })
        inputRow.addView(Button(this).apply {
            text = "🎤"
            setOnClickListener { onMicClicked() }
        })
        contentContainer.addView(inputRow)

        // Settings Row
        val controlsRow = LinearLayout(this)
        langBtn = Button(this).apply {
            text = "Lang: ${langs[langIdx].first}"
            isAllCaps = false
            setOnClickListener {
                langIdx = (langIdx + 1) % langs.size
                voice.langTag = langs[langIdx].second
                text = "Lang: ${langs[langIdx].first}"
            }
        }
        speakBtn = Button(this).apply {
            text = if (speakOn) "Speak: ON" else "Speak: OFF"
            isAllCaps = false
            setOnClickListener {
                speakOn = !speakOn
                text = if (speakOn) "Speak: ON" else "Speak: OFF"
                if (!speakOn) voice.stopSpeaking()
            }
        }
        controlsRow.addView(langBtn)
        controlsRow.addView(speakBtn)
        controlsRow.addView(Button(this).apply {
            text = "Stop"
            isAllCaps = false
            setOnClickListener { voice.stopSpeaking() }
        })
        contentContainer.addView(controlsRow)

        // Load conversation history
        exec.execute {
            val oldMsgs = db.recent(30)
            runOnUiThread {
                chatTextView.text = ""
                oldMsgs.forEach { msg ->
                    val who = if (msg.role == "user") "You" else "ZENITH"
                    chatTextView.append("$who: ${msg.text}\n\n")
                }
                scrollChatDown()
            }
        }
    }

    private fun scrollChatDown() {
        chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun submitChat(text: String, fromVoice: Boolean) {
        if (text.isBlank()) return
        chatTextView.append("You: $text\n\n")
        scrollChatDown()

        exec.execute {
            val reply = assistant.handle(text)
            val permReq = assistant.takePermissionRequest()

            runOnUiThread {
                chatTextView.append("ZENITH: $reply\n\n")
                scrollChatDown()

                if (permReq != null) {
                    requestPermissions(arrayOf(permReq), 1)
                }

                if (speakOn && reply.isNotEmpty()) {
                    val relisten = fromVoice && assistant.awaitingReply
                    voice.speak(reply, if (relisten) ({ voice.listen() }) else null)
                } else if (fromVoice && assistant.awaitingReply) {
                    voice.listen()
                }
            }
        }
    }

    private fun onMicClicked() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
        } else {
            voice.listen()
        }
    }

    // ---------- AI Models Manager Tab ----------
    private fun buildModelsTab() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        container.addView(TextView(this).apply {
            text = "Local AI Models (100% Offline)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })
        container.addView(TextView(this).apply {
            text = modelManager.getDeviceMemoryStats()
            textSize = 12f
            setTextColor(textSecondary)
        })

        modelCardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(modelCardsContainer)
        scroll.addView(container)
        contentContainer.addView(scroll)

        refreshModelCards()
    }

    private fun refreshModelCards() {
        modelCardsContainer.removeAllViews()

        AvailableModels.MODELS.forEach { spec ->
            val isDownloaded = modelManager.isModelDownloaded(spec)
            val isLoaded = llmEngine.getModelName() == spec.filename

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(cardBg)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, dp(8), 0, dp(8))
                layoutParams = params
            }

            card.addView(TextView(this).apply {
                text = spec.name
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            card.addView(TextView(this).apply {
                text = "${spec.description}\nDownload Size: ~${spec.sizeBytes / (1024 * 1024)} MB | RAM: ${spec.ramRequiredMb} MB"
                textSize = 12f
                setTextColor(textSecondary)
            })

            val statusText = TextView(this).apply {
                text = when {
                    isLoaded -> "Status: ACTIVE & LOADED"
                    isDownloaded -> "Status: Downloaded (Ready to Load)"
                    else -> "Status: Not Downloaded"
                }
                textSize = 12f
                setTextColor(if (isLoaded) cyan else textSecondary)
            }
            card.addView(statusText)

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            if (isDownloaded) {
                if (!isLoaded) {
                    actionRow.addView(Button(this).apply {
                        text = "Load Model"
                        isAllCaps = false
                        setOnClickListener {
                            llmEngine.loadModel(modelManager.getModelFile(spec))
                            headerStatus.text = "Local AI: ${llmEngine.getModelName()} · Privacy First"
                            refreshModelCards()
                        }
                    })
                } else {
                    actionRow.addView(Button(this).apply {
                        text = "Unload"
                        isAllCaps = false
                        setOnClickListener {
                            llmEngine.unloadModel()
                            headerStatus.text = "Local AI: None · Privacy First"
                            refreshModelCards()
                        }
                    })
                }

                actionRow.addView(Button(this).apply {
                    text = "Delete"
                    isAllCaps = false
                    setOnClickListener {
                        if (isLoaded) llmEngine.unloadModel()
                        modelManager.deleteModel(spec)
                        refreshModelCards()
                    }
                })
            } else {
                val progressPB = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    visibility = View.GONE
                }
                card.addView(progressPB)

                val downloadBtn = Button(this).apply {
                    text = "Download"
                    isAllCaps = false
                }
                downloadBtn.setOnClickListener {
                    downloadBtn.isEnabled = false
                    progressPB.visibility = View.VISIBLE

                    modelManager.downloadModel(spec, object : DownloadCallback {
                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long, speedKbps: Long) {
                            runOnUiThread {
                                val progress = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                                progressPB.progress = progress
                                statusText.text = "Downloading: $progress% ($speedKbps KB/s)"
                            }
                        }

                        override fun onSuccess(file: File) {
                            runOnUiThread {
                                progressPB.visibility = View.GONE
                                refreshModelCards()
                            }
                        }

                        override fun onError(error: String) {
                            runOnUiThread {
                                progressPB.visibility = View.GONE
                                downloadBtn.isEnabled = true
                                statusText.text = "Error: $error"
                            }
                        }
                    })
                }
                actionRow.addView(downloadBtn)
            }

            card.addView(actionRow)
            modelCardsContainer.addView(card)
        }
    }

    // ---------- Memory & History Tab ----------
    private fun buildMemoryTab() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        container.addView(TextView(this).apply {
            text = "Permanent Long-Term Memory"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })

        val memToggleBtn = Button(this).apply {
            val enabled = db.getBooleanSetting("memory_enabled", true)
            text = if (enabled) "Long-Term Memory: ENABLED" else "Long-Term Memory: DISABLED"
            isAllCaps = false
            setOnClickListener {
                val newSetting = !db.getBooleanSetting("memory_enabled", true)
                db.setBooleanSetting("memory_enabled", newSetting)
                text = if (newSetting) "Long-Term Memory: ENABLED" else "Long-Term Memory: DISABLED"
            }
        }
        container.addView(memToggleBtn)

        val actionsRow = LinearLayout(this)
        actionsRow.addView(Button(this).apply {
            text = "Export JSON"
            isAllCaps = false
            setOnClickListener {
                val json = db.exportJson()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exported Data")
                    .setMessage(json.take(500) + "\n... (Full export generated)")
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
        actionsRow.addView(Button(this).apply {
            text = "Delete All Data"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete All Memories & History?")
                    .setMessage("This will permanently delete all stored chats and saved memories.")
                    .setPositiveButton("Delete") { _, _ ->
                        db.clearAll()
                        refreshMemoryList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        container.addView(actionsRow)

        memoryListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(memoryListContainer)
        scroll.addView(container)
        contentContainer.addView(scroll)

        refreshMemoryList()
    }

    private fun refreshMemoryList() {
        memoryListContainer.removeAllViews()
        val mems = db.memories()

        if (mems.isEmpty()) {
            memoryListContainer.addView(TextView(this).apply {
                text = "No saved memories yet. Say \"Zenith, remember that...\" to save facts."
                setTextColor(textSecondary)
                setPadding(0, dp(12), 0, 0)
            })
            return
        }

        mems.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(cardBg)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, dp(4), 0, dp(4))
                layoutParams = params
            }

            row.addView(TextView(this).apply {
                text = "• ${item.text}"
                setTextColor(textPrimary)
                textSize = 14f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(Button(this).apply {
                text = "X"
                setOnClickListener {
                    db.deleteMemory(item.id)
                    refreshMemoryList()
                }
            })

            memoryListContainer.addView(row)
        }
    }

    // ---------- Wake Word & Service Tab ----------
    private fun buildServiceTab() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        container.addView(TextView(this).apply {
            text = "Wake Word & Background Listening"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })

        val serviceToggleBtn = Button(this).apply {
            text = if (WakeWordService.isRunning) "Wake Word: ACTIVE" else "Wake Word: STOPPED"
            isAllCaps = false
            setOnClickListener {
                if (WakeWordService.isRunning) {
                    WakeWordService.stopService(this@MainActivity)
                    db.setBooleanSetting("wake_word_enabled", false)
                    text = "Wake Word: STOPPED"
                } else {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        WakeWordService.startService(this@MainActivity)
                        db.setBooleanSetting("wake_word_enabled", true)
                        text = "Wake Word: ACTIVE"
                    } else {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
                    }
                }
            }
        }
        container.addView(serviceToggleBtn)

        container.addView(TextView(this).apply {
            text = "\n• Say 'Hey Zenith' anytime to wake up the assistant.\n" +
                "• Operates in background using an opt-in foreground service.\n" +
                "• Audio processing is strictly 100% local on your phone.\n" +
                "• Disable battery optimizations for ZENITH if your OS kills background services."
            textSize = 14f
            setTextColor(textSecondary)
        })

        contentContainer.addView(container)
    }

    // ---------- Privacy & Permissions Dashboard Tab ----------
    private fun buildPrivacyTab() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        container.addView(TextView(this).apply {
            text = "Privacy & Permission Dashboard"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })

        val perms = listOf(
            "Microphone (Voice Input & Wake Word)" to Manifest.permission.RECORD_AUDIO,
            "Contacts (Call Contact Resolver)" to Manifest.permission.READ_CONTACTS,
            "Phone Calls (Placing Outgoing Calls)" to Manifest.permission.CALL_PHONE,
            "Call State (Incoming Call Answering)" to Manifest.permission.ANSWER_PHONE_CALLS
        )

        perms.forEach { (label, perm) ->
            val granted = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = "$label: " + if (granted) "GRANTED" else "DENIED"
                setTextColor(if (granted) cyan else Color.RED)
                textSize = 14f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (!granted) {
                row.addView(Button(this).apply {
                    text = "Grant"
                    isAllCaps = false
                    setOnClickListener { requestPermissions(arrayOf(perm), 1) }
                })
            }
            container.addView(row)
        }

        container.addView(Button(this).apply {
            text = "Open Accessibility Settings (For Screen Automation)"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        container.addView(Button(this).apply {
            text = "Open Notification Access Settings (For Reading Messages)"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })

        container.addView(TextView(this).apply {
            text = "\nZENITH PRIVACY GUARANTEE:\n" +
                "• 100% Local AI inference on device.\n" +
                "• No paid cloud API keys required.\n" +
                "• Chat history & memories stay on your phone SQLite database.\n" +
                "• No background audio or data telemetry uploads."
            textSize = 13f
            setTextColor(textSecondary)
        })

        scroll.addView(container)
        contentContainer.addView(scroll)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val ok = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        if (code == 2 && ok) {
            voice.listen()
        }
        if (currentTab == "privacy") buildPrivacyTab()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
        exec.shutdown()
    }
}
