package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.math.sqrt

/**
 * Main-thread sink for exactly one recognition attempt.
 * `onFinish` happens at most once and never after [SpeechEngine.cancel].
 */
interface RecognitionSink {
    fun onReady()
    fun onRms(level: Float)
    fun onEndOfSpeech()
    fun onFinish(result: ListeningResult)
}

/** One-shot recogniser backend. Audio lives only inside the active attempt and is never written to disk. */
interface SpeechEngine {
    val id: String

    /** How long the caller waits before declaring the attempt dead. */
    val watchdogMs: Long get() = 15000
    fun start(sink: RecognitionSink)
    fun cancel()

    companion object {
        /**
         * Results that mean "this backend cannot work here" rather than "the speaker was unclear".
         * Only these may trigger a fallback, and only when the backend never reached ready.
         */
        val STARTUP_FAILURES = setOf(
            ListeningOutcome.UNAVAILABLE, ListeningOutcome.BUSY, ListeningOutcome.NETWORK,
            ListeningOutcome.SERVER, ListeningOutcome.CLIENT,
        )
        fun isStartupFailure(result: ListeningResult) = result.outcome in STARTUP_FAILURES
    }
}

/** Android framework recogniser, preferred while the OEM service is functional. */
class SystemSpeechEngine(private val context: Context, private val handler: Handler) : SpeechEngine {
    override val id = "system"
    private var recognizer: SpeechRecognizer? = null
    private var sink: RecognitionSink? = null

    override fun start(sink: RecognitionSink) {
        this.sink = sink
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            sink.onFinish(ListeningResult(ListeningOutcome.UNAVAILABLE)); return
        }
        try {
            val service = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val onDevice = granted && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            Log.i("KITTVoice", "engine=system start service=$service language=zh-CN onDeviceAvailable=$onDevice")
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { post { it.onReady() } }
                    override fun onBeginningOfSpeech() { post { it.onReady() } }
                    override fun onRmsChanged(rmsdB: Float) { post { it.onRms(rmsdB) } }
                    override fun onBufferReceived(buffer: ByteArray?) {} // never retained
                    override fun onEndOfSpeech() { post { it.onEndOfSpeech() } }
                    override fun onError(error: Int) {
                        Log.i("KITTVoice", "engine=system error code=$error")
                        post { it.onFinish(ListeningResult.error(error)) }
                    }
                    override fun onResults(results: Bundle?) {
                        val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        Log.i("KITTVoice", "engine=system results")
                        post { it.onFinish(ListeningResult.recognized(value)) }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (error: Exception) {
            Log.i("KITTVoice", "engine=system startup exception=${error.javaClass.simpleName}")
            val outcome = if (error is SecurityException) ListeningOutcome.PERMISSION_DENIED else ListeningOutcome.CLIENT
            sink.onFinish(ListeningResult(outcome))
        }
    }

    private inline fun post(crossinline block: (RecognitionSink) -> Unit) {
        val target = sink ?: return
        handler.post { if (sink === target) block(target) }
    }

    override fun cancel() {
        sink = null
        val old = recognizer; recognizer = null
        runCatching { old?.cancel() }; runCatching { old?.destroy() }
    }
}

/**
 * Bundled offline Chinese recogniser, used only after the system path proves it cannot transcribe.
 * The model loads at most once per process; microphone frames stay in one short-lived array and are
 * released as soon as the attempt ends.
 */
class VoskSpeechEngine(
    private val context: Context,
    private val handler: Handler,
    private val assetName: String = VOSK_ASSET,
) : SpeechEngine {
    override val id = "vosk-cn"
    private var model: Model? = null
    private var loading = false
    private var loadFailed = false
    @Volatile private var sink: RecognitionSink? = null
    @Volatile private var cancelled = false
    @Volatile private var record: AudioRecord? = null

    override val watchdogMs get() = if (model == null) MODEL_WATCHDOG_MS else 15000

    override fun start(sink: RecognitionSink) {
        this.sink = sink; cancelled = false
        if (!microphoneAvailable()) { postFinish(sink) { it.onFinish(ListeningResult(ListeningOutcome.PERMISSION_DENIED)) }; return }
        if (loadFailed) { handler.post { sink.onFinish(ListeningResult(ListeningOutcome.UNAVAILABLE)) }; return }
        model?.let { capture(it, sink); return }
        if (loading) return // the running load finishes into whichever sink is current
        loading = true
        Log.i("KITTVoice", "engine=vosk model load start asset=$assetName")
        StorageService.unpack(context, assetName, "vosk-models",
            { loaded -> handler.post {
                loading = false; model = loaded
                Log.i("KITTVoice", "engine=vosk model load finished")
                val target = this.sink
                if (target != null && !cancelled) capture(loaded, target)
            } },
            { error -> handler.post {
                loading = false; loadFailed = true
                Log.i("KITTVoice", "engine=vosk model load failed error=${error.javaClass.simpleName}")
                this.sink?.onFinish(ListeningResult(ListeningOutcome.UNAVAILABLE))
            } })
    }

    /**
     * Opens the microphone under an explicit permission guard, then recognises on a short-lived thread.
     * A missing or revoked permission answers PERMISSION_DENIED — it is never rewritten as "didn't catch that".
     */
    private fun capture(model: Model, sink: RecognitionSink) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            postFinish(sink) { it.onFinish(ListeningResult(ListeningOutcome.PERMISSION_DENIED)) }; return
        }
        val minBuffer = runCatching { AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) }.getOrDefault(0)
        val capture = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer * 2, 4096))
        }.getOrElse { error ->
            val outcome = if (error is SecurityException) ListeningOutcome.PERMISSION_DENIED else ListeningOutcome.UNAVAILABLE
            postFinish(sink) { it.onFinish(ListeningResult(outcome)) }; return
        }
        if (capture.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { capture.release() }
            postFinish(sink) { it.onFinish(ListeningResult(ListeningOutcome.UNAVAILABLE)) }; return
        }
        this.record = capture
        Thread({ recognise(model, capture, sink) }, "kitt-vosk").also { it.isDaemon = true; it.start() }
    }

    private fun recognise(model: Model, capture: AudioRecord, sink: RecognitionSink) {
        val recognizer = runCatching { Recognizer(model, SAMPLE_RATE.toFloat()) }.getOrNull()
        if (recognizer == null) {
            runCatching { capture.stop() }; runCatching { capture.release() }; record = null
            postFinish(sink) { it.onFinish(ListeningResult(ListeningOutcome.CLIENT)) }
            return
        }
        // A driver pauses between words; the default endpointer would cut the sentence short.
        recognizer.setEndpointerMode(Recognizer.EndpointerMode.LONG)
        var endpoint = false
        var voiced = false
        var peakRms = 0.0
        var frames = 0
        try {
            if (cancelled) return
            capture.startRecording()
            if (capture.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                postFinish(sink) { it.onFinish(ListeningResult(ListeningOutcome.BUSY)) }; return
            }
            postFinish(sink) { it.onReady() }
            val frame = ShortArray(FRAME_SAMPLES)
            while (!cancelled && frames < FRAMES) {
                val read = capture.read(frame, 0, FRAME_SAMPLES)
                if (read <= 0) { frames++; continue }
                var sum = 0.0
                for (index in 0 until read) { val value = frame[index].toDouble(); sum += value * value }
                val rms = sqrt(sum / read)
                if (rms > VOICE_FLOOR) voiced = true
                peakRms = maxOf(peakRms, rms)
                val level = (RMS_GAIN * (rms / VOICE_FULL_SCALE).toFloat()).coerceIn(0f, ANDROID_RMS_CEILING)
                postFinish(sink) { it.onRms(level) }
                if (recognizer.acceptWaveForm(frame, read)) { endpoint = true; break }
                frames++
            }
            if (cancelled) return
            postFinish(sink) { it.onEndOfSpeech() }
            val transcript = runCatching { voskTranscript(recognizer.finalResult) }.getOrDefault("")
            // The language model happily turns room noise into plausible Chinese, so a transcript only
            // counts as one when the window actually carried voice energy.
            val outcome = when {
                !voiced -> ListeningResult(ListeningOutcome.TIMEOUT)
                transcript.isNotBlank() -> ListeningResult.recognized(transcript)
                else -> ListeningResult(ListeningOutcome.NO_MATCH)
            }
            Log.i("KITTVoice", "engine=vosk finish outcome=${outcome.outcome} endpoint=$endpoint voiced=$voiced peakRms=%.0f frames=$frames text=${outcome.text.take(80)}".format(peakRms))
            postFinish(sink) { it.onFinish(outcome) }
        } catch (error: Exception) {
            Log.i("KITTVoice", "engine=vosk capture exception=${error.javaClass.simpleName}")
            val outcome = if (error is SecurityException) ListeningOutcome.PERMISSION_DENIED else ListeningOutcome.CLIENT
            postFinish(sink) { it.onFinish(ListeningResult(outcome)) }
        } finally {
            // Nothing outlives the one-shot attempt: no file, no retained buffer.
            runCatching { capture.stop() }
            record = null
            runCatching { capture.release() }
            runCatching { recognizer.close() }
        }
    }

    private inline fun postFinish(target: RecognitionSink, crossinline block: (RecognitionSink) -> Unit) {
        handler.post { if (sink === target && !cancelled) block(target) }
    }

    override fun cancel() {
        cancelled = true
        sink = null
        val live = record; record = null
        // Unblocks a thread parked in read() so the microphone is handed back immediately.
        runCatching { live?.stop() }
    }

    private fun microphoneAvailable() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val VOSK_ASSET = "vosk-model-small-cn-0.22"
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 1600 // 100 ms
        const val FRAMES = 110 // hard cap on one utterance
        const val MODEL_WATCHDOG_MS = 60000L
        const val VOICE_FLOOR = 300.0
        const val VOICE_FULL_SCALE = 6000.0
        const val RMS_GAIN = 5.0f
        const val ANDROID_RMS_CEILING = 5.0f

        /** Vosk's Chinese model returns spaced characters: "再 讲 一 点". */
        fun voskTranscript(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val text = runCatching { Json.parseToJsonElement(raw).jsonObject["text"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            return text.orEmpty().replace(" ", "").trim()
        }
    }
}

/** A device's recogniser order: the OEM service first, then the bundled offline model. */
fun defaultSpeechEngines(context: Context, handler: Handler): List<SpeechEngine> =
    listOf(SystemSpeechEngine(context, handler), VoskSpeechEngine(context, handler))
