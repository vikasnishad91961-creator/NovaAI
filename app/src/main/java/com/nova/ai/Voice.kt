package com.nova.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Sunna (speech-to-text) aur bolna (text-to-speech). Main thread par use karein. */
class Voice(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onSpeaking: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ttsReady = false
    private val tts = TextToSpeech(ctx) { ttsReady = it == TextToSpeech.SUCCESS }
    private val rec: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { onSpeaking(true) } }
            override fun onDone(id: String?) { main.post { onSpeaking(false) } }
            @Deprecated("deprecated") override fun onError(id: String?) { main.post { onSpeaking(false) } }
        })
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { onListening(false) }
            override fun onError(e: Int) { onListening(false) }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onResults(r: Bundle?) {
                onListening(false)
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
            }
        })
    }

    fun listen() {
        stopSpeaking()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        rec.startListening(i)
    }

    fun stopListening() { rec.stopListening(); onListening(false) }

    fun speak(text: String) {
        if (!ttsReady) return
        val hindi = text.any { it in '\u0900'..'\u097F' }
        tts.language = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova")
    }

    fun stopSpeaking() { tts.stop(); onSpeaking(false) }

    fun destroy() { rec.destroy(); tts.shutdown() }
}
