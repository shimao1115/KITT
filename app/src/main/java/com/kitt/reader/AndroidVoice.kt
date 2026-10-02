package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

fun speechChunks(text: String, max: Int = 1800): List<String> {
    val chunks = mutableListOf<String>(); var remainder = text.trim()
    while (remainder.length > max) {
        val boundary = remainder.take(max).indexOfLast { it in "。！？；\n" }.takeIf { it >= max / 2 } ?: max - 1
        chunks.add(remainder.take(boundary + 1)); remainder = remainder.drop(boundary + 1)
    }
    if (remainder.isNotBlank()) chunks.add(remainder)
    return chunks
}

class AndroidVoice(private val context: Context) : VoicePort {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready: Boolean? = null
    private var serial = 0L
    private var pendingSpeech: (() -> Unit)? = null
    private var complete: ((Boolean) -> Unit)? = null
    private var finalId = ""
    private var recognizer: SpeechRecognizer? = null
    private var pendingListen: ((String?) -> Unit)? = null
    private var activeListen: ((String?) -> Unit)? = null
    private var watchdog: Runnable? = null
    var requestMicrophone: (() -> Unit)? = null
    var speechRate = 1.0f
    init {
        tts = TextToSpeech(context.applicationContext) { status -> handler.post {
            ready = status == TextToSpeech.SUCCESS && (tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) ?: -2) >= 0
            pendingSpeech?.invoke(); pendingSpeech = null
        } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { if (id == finalId) finishSpeech(true) } }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { handler.post { if (id?.startsWith("$serial:") == true) finishSpeech(false) } }
            override fun onError(id: String?, errorCode: Int) { onError(id) }
        })
    }
    override fun stop() {
        serial++; pendingSpeech = null; complete = null; finalId = ""
        pendingListen = null; activeListen = null
        watchdog?.let(handler::removeCallbacks); watchdog = null
        tts?.stop(); recognizer?.cancel(); recognizer?.destroy(); recognizer = null
    }
    override fun speak(text: String, complete: (Boolean) -> Unit) {
        stop(); this.complete = complete; val token = serial
        val execute = {
            if (serial == token) {
                if (ready != true) finishSpeech(false) else {
                    val chunks = speechChunks(text)
                    if (chunks.isEmpty()) finishSpeech(false) else {
                        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 1.5f))
                        finalId = "$token:${chunks.lastIndex}"
                        watchdog?.let(handler::removeCallbacks)
                        watchdog = Runnable { if (serial == token) finishSpeech(false) }
                        handler.postDelayed(watchdog!!, (text.length * 350L + 20000).coerceAtMost(600000))
                        chunks.forEachIndexed { index, chunk ->
                            val status = tts?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$token:$index")
                            if (status != TextToSpeech.SUCCESS) finishSpeech(false)
                        }
                    }
                }
            }
        }
        if (ready == null) {
            pendingSpeech = execute
            watchdog = Runnable { if (serial == token && ready == null) { pendingSpeech = null; finishSpeech(false) } }
            handler.postDelayed(watchdog!!, 8000)
        } else execute()
    }
    private fun finishSpeech(success: Boolean) {
        val callback = complete ?: return; complete = null; finalId = ""
        watchdog?.let(handler::removeCallbacks); watchdog = null
        if (!success) tts?.stop()
        callback(success)
    }
    override fun listen(result: (String?) -> Unit) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val request = requestMicrophone
            if (request == null) { result(null); return }
            pendingListen = result; request(); return
        }
        startRecognition(result)
    }
    fun permissionResult(granted: Boolean) {
        val result = pendingListen ?: return; pendingListen = null
        if (granted) startRecognition(result) else result(null)
    }
    private fun startRecognition(result: (String?) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { result(null); return }
        val token = serial; activeListen = result
        fun finish(value: String?) {
            if (token != serial) return
            val callback = activeListen ?: return; activeListen = null
            watchdog?.let(handler::removeCallbacks); watchdog = null
            recognizer?.cancel(); recognizer?.destroy(); recognizer = null
            callback(value)
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {} // never retained
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { handler.post { finish(null) } }
                override fun onResults(results: Bundle?) {
                    val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    handler.post { finish(value) }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            watchdog = Runnable { finish(null) }; handler.postDelayed(watchdog!!, 15000)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { finish(null) }
    }
    fun close() { stop(); tts?.shutdown(); tts = null }
}
