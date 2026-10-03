package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var defaultVoice: String? = null
    private var serial = 0L
    private var pendingSpeech: (() -> Unit)? = null
    private var complete: ((Boolean) -> Unit)? = null
    private var finalId = ""
    private var recognizer: SpeechRecognizer? = null
    private var pendingListen: ((ListeningResult) -> Unit)? = null
    private var activeListen: ((ListeningResult) -> Unit)? = null
    private var watchdog: Runnable? = null
    private val feedback = ListeningFeedback()
    var detail by mutableStateOf(VoiceDetail()); private set
    var voices by mutableStateOf<List<TtsVoiceOption>>(emptyList()); private set
    var voiceMessage by mutableStateOf("正在加载系统中文声音…"); private set
    var requestMicrophone: (() -> Unit)? = null
    var speechRate = 1.0f
    var voiceName = ""
    val canSpeak get() = ready == true
    init {
        tts = TextToSpeech(context.applicationContext) { status -> handler.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready == true) {
                val languageStatus = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                defaultVoice = tts?.voice?.name
                voices = chineseVoices(tts?.voices.orEmpty().map {
                    TtsVoiceOption(it.name, it.locale, it.isNetworkConnectionRequired,
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty())
                })
                ready = languageStatus >= 0 || voices.any { it.installed }
                voiceMessage = if (ready == true) "系统中文声音 · ${voices.size} 个" else "系统中文语音不可用，请检查系统 TTS 设置。"
                Log.i("KITTVoice", "tts initialized languageStatus=$languageStatus voices=${voices.size} default=$defaultVoice")
                applyVoice(voiceName, speechRate)
            } else voiceMessage = "系统中文语音不可用，请检查系统 TTS 设置。"
            val execute = pendingSpeech; pendingSpeech = null; execute?.invoke()
        } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { handler.post {
                if (complete != null && id?.startsWith("$serial:") == true) {
                    detail = VoiceDetail(VoicePhase.SPEAKING)
                    Log.i("KITTVoice", "tts started session=$serial")
                }
            } }
            override fun onDone(id: String?) { handler.post { if (id == finalId) finishSpeech(true) } }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { handler.post { if (id?.startsWith("$serial:") == true) finishSpeech(false) } }
            override fun onError(id: String?, errorCode: Int) { onError(id) }
        })
    }
    override fun stop() {
        serial++; pendingSpeech = null; complete = null; finalId = ""
        val cancelled = activeListen ?: pendingListen
        pendingListen = null; activeListen = null
        clearWatchdog()
        tts?.stop(); releaseRecognizer()
        feedback.reset(); detail = VoiceDetail()
        if (cancelled != null) {
            Log.i("KITTVoice", "asr cancelled session=${serial - 1}")
            cancelled(ListeningResult(ListeningOutcome.CANCELLED))
        }
    }
    private fun clearWatchdog() { watchdog?.let(handler::removeCallbacks); watchdog = null }
    private fun releaseRecognizer() {
        val old = recognizer; recognizer = null
        runCatching { old?.cancel() }; runCatching { old?.destroy() }
    }
    private fun applyVoice(name: String, rate: Float): Boolean {
        val engine = tts ?: return false
        val chosen = selectedChineseVoice(name, voices, defaultVoice)
        val platformVoice = engine.voices?.find { it.name == chosen?.name }
        var status = if (platformVoice != null) engine.setVoice(platformVoice) else engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (status < 0) status = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        val rateStatus = engine.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
        Log.i("KITTVoice", "tts configured voice=${engine.voice?.name} fallback=${name.isNotBlank() && name != chosen?.name} rate=${rate.coerceIn(0.5f, 1.5f)} status=$status")
        return status >= 0 && rateStatus == TextToSpeech.SUCCESS
    }
    override fun speak(text: String, complete: (Boolean) -> Unit) = speakWith(text, voiceName, speechRate, complete)
    /** Settings-only operation: fixed local text, draft voice/rate; does not touch Journey or Provider. */
    fun preview(name: String, rate: Float, complete: (Boolean) -> Unit) =
        speakWith("你好，我是路上读山河。前面的风景，值得慢慢听。", name, rate, complete)
    private fun speakWith(text: String, name: String, rate: Float, complete: (Boolean) -> Unit) {
        stop(); this.complete = complete; val token = serial
        val execute = {
            if (serial == token) {
                if (ready != true || !applyVoice(name, rate)) finishSpeech(false) else {
                    val chunks = speechChunks(text)
                    if (chunks.isEmpty()) finishSpeech(false) else {
                        finalId = "$token:${chunks.lastIndex}"
                        clearWatchdog()
                        watchdog = Runnable { if (serial == token) finishSpeech(false) }
                        handler.postDelayed(watchdog!!, (text.length * 350L + 20000).coerceAtMost(600000))
                        for ((index, chunk) in chunks.withIndex()) {
                            val status = tts?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$token:$index")
                            if (status != TextToSpeech.SUCCESS) { finishSpeech(false); break }
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
        clearWatchdog(); detail = VoiceDetail()
        if (!success) tts?.stop()
        Log.i("KITTVoice", "tts finished session=$serial success=$success")
        callback(success)
    }
    override fun listen(result: (String?) -> Unit) = listenOutcome {
        result(it.text.takeIf { _ -> it.outcome == ListeningOutcome.SUCCESS })
    }
    override fun listenOutcome(result: (ListeningResult) -> Unit) {
        stop(); feedback.preparing(); detail = feedback.detail
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val request = requestMicrophone
            if (request == null) { finishBeforeStart(result, ListeningOutcome.PERMISSION_DENIED); return }
            pendingListen = result
            try { request() } catch (_: Exception) { pendingListen = null; finishBeforeStart(result, ListeningOutcome.CLIENT) }
            return
        }
        startRecognition(result)
    }
    private fun finishBeforeStart(result: (ListeningResult) -> Unit, outcome: ListeningOutcome) {
        feedback.reset(); detail = feedback.detail
        Log.i("KITTVoice", "asr finish session=$serial outcome=$outcome beforeStart=true")
        result(ListeningResult(outcome))
    }
    fun permissionResult(granted: Boolean) {
        val result = pendingListen ?: return; pendingListen = null
        if (granted) startRecognition(result) else finishBeforeStart(result, ListeningOutcome.PERMISSION_DENIED)
    }
    private fun startRecognition(result: (ListeningResult) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { finishBeforeStart(result, ListeningOutcome.UNAVAILABLE); return }
        val token = serial; activeListen = result
        val began = SystemClock.elapsedRealtime()
        var rmsCount = 0; var rmsMin = Float.POSITIVE_INFINITY; var rmsMax = Float.NEGATIVE_INFINITY
        fun event(name: String) { Log.i("KITTVoice", "asr session=$token elapsedMs=${SystemClock.elapsedRealtime() - began} $name") }
        fun finish(value: ListeningResult) {
            if (token != serial) return
            val callback = activeListen ?: return; activeListen = null
            clearWatchdog(); releaseRecognizer(); feedback.reset(); detail = feedback.detail
            event("finish outcome=${value.outcome} code=${value.androidCode} rmsCallbacks=$rmsCount rmsMin=${if (rmsCount == 0) 0f else rmsMin} rmsMax=${if (rmsCount == 0) 0f else rmsMax} textLength=${value.text.length}")
            callback(value)
        }
        fun live(block: () -> Unit) { handler.post { if (token == serial && activeListen != null) block() } }
        try {
            val service = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            event("start service=$service language=zh-CN onDeviceAvailable=${Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { live { event("ready"); feedback.ready(); detail = feedback.detail } }
                override fun onBeginningOfSpeech() { live { event("beginning"); feedback.ready(); detail = feedback.detail } }
                override fun onRmsChanged(rmsdB: Float) { live {
                    if (rmsdB.isFinite()) {
                        rmsCount++; rmsMin = minOf(rmsMin, rmsdB); rmsMax = maxOf(rmsMax, rmsdB)
                        feedback.rms(rmsdB); detail = feedback.detail
                    }
                } }
                override fun onBufferReceived(buffer: ByteArray?) {} // never retained
                override fun onEndOfSpeech() { live { event("end"); feedback.endSpeech(); detail = feedback.detail } }
                override fun onError(error: Int) { live { event("error code=$error"); finish(ListeningResult.error(error)) } }
                override fun onResults(results: Bundle?) {
                    val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    live { event("results"); finish(ListeningResult.recognized(value)) }
                }
                override fun onPartialResults(partialResults: Bundle?) { live { event("partial") } }
                override fun onEvent(eventType: Int, params: Bundle?) { live { event("event type=$eventType") } }
            })
            watchdog = Runnable { event("watchdog"); finish(ListeningResult(ListeningOutcome.TIMEOUT)) }
            handler.postDelayed(watchdog!!, 15000)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (error: Exception) {
            event("startup exception=${error.javaClass.simpleName}")
            finish(ListeningResult(if (error is SecurityException) ListeningOutcome.PERMISSION_DENIED else ListeningOutcome.CLIENT))
        }
    }
    fun close() { stop(); tts?.shutdown(); tts = null }
}
