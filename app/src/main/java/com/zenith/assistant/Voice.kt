package com.zenith.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Android Speech-To-Text (STT) and Text-To-Speech (TTS) integration.
 * Supports English (en-IN), Hindi (hi-IN), Marathi (mr-IN) with offline recognition preference.
 */
class Voice(
    private val activity: Activity,
    private val onText: (String) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onListening: (Boolean) -> Unit
) {
    var langTag = "en-IN"
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var rec: SpeechRecognizer? = null
    private var afterSpeak: (() -> Unit)? = null

    init {
        tts = TextToSpeech(activity) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        val cb = afterSpeak
                        afterSpeak = null
                        if (cb != null) activity.runOnUiThread(cb)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            } else {
                onMessage("Text-to-speech is not initialized on this device.")
            }
        }
    }

    fun speak(text: String, then: (() -> Unit)? = null) {
        if (!ttsReady) {
            then?.invoke()
            return
        }
        val locale = Locale.forLanguageTag(langTag)
        val r = tts?.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            onMessage("Voice for $langTag is not installed; using default system voice.")
        }
        afterSpeak = then
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zenith_utt")
    }

    fun stopSpeaking() {
        afterSpeak = null
        tts?.stop()
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onMessage("Speech recognition service is not available on this device. Please type your message.")
            return
        }
        stopSpeaking()
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(activity).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListening(true)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    onListening(false)
                    onMessage(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech pack missing. Please install offline language pack or check connection."
                            else -> "Speech recognition error ($error). Please type instead."
                        }
                    )
                }
                override fun onResults(results: Bundle?) {
                    onListening(false)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        onMessage("I didn't catch that.")
                    } else {
                        onText(text)
                    }
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            r.startListening(intent)
        }
    }

    fun shutdown() {
        rec?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
