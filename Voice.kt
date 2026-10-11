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

/** Wraps Android's own speech recognizer + TTS. Whether recognition is offline depends on the device's engine/language pack. */
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
                        val cb = afterSpeak; afterSpeak = null
                        if (cb != null) activity.runOnUiThread(cb)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            } else onMessage("Text-to-speech isn't available on this device.")
        }
    }

    fun speak(text: String, then: (() -> Unit)? = null) {
        if (!ttsReady) { then?.invoke(); return }
        val r = tts?.setLanguage(Locale.forLanguageTag(langTag))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            onMessage("A voice for $langTag isn't installed; using the default voice.")
        }
        afterSpeak = then
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zenith")
    }

    fun stopSpeaking() { afterSpeak = null; tts?.stop() }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onMessage("No speech recognition service on this device. Please type instead.")
            return
        }
        stopSpeaking()
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(activity).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
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
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "This language needs internet for speech recognition on your device's engine."
                            else -> "Speech recognition failed (code $error). Please type instead."
                        }
                    )
                }
                override fun onResults(results: Bundle?) {
                    onListening(false)
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) onMessage("I didn't catch that.") else onText(t)
                }
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            )
        }
    }

    fun shutdown() { rec?.destroy(); tts?.stop(); tts?.shutdown() }
}
