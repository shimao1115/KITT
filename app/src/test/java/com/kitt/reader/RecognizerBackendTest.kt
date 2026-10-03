package com.kitt.reader

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import java.time.Duration

/** Delivers one fixed outcome the moment it is started, the way an absent service answers today. */
class FixedOutcomeEngine(override val id: String, private val outcome: ListeningOutcome) : SpeechEngine {
    var starts = 0; private set
    override fun start(sink: RecognitionSink) { starts++; sink.onFinish(ListeningResult(outcome)) }
    override fun cancel() {}
}

/** Lets a test raise the events a real recogniser would raise, and count how often it was asked to listen. */
class DrivingEngine(override val id: String, override var watchdogMs: Long = 15000) : SpeechEngine {
    val sinks = mutableListOf<RecognitionSink>()
    var cancellations = 0; private set
    val starts get() = sinks.size
    override fun start(sink: RecognitionSink) { sinks += sink }
    override fun cancel() { cancellations++ }
    fun ready() { sinks.last().onReady() }
    fun rms(level: Float) { sinks.last().onRms(level) }
    fun partial(text: String) { sinks.last().onPartial(text) }
    fun finish(result: ListeningResult) { sinks.last().onFinish(result) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class RecognizerBackendTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun granted() { shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO) }
    private fun voiceWith(vararg engines: SpeechEngine): AndroidVoice { granted(); return AndroidVoice(app) { _: Context, _: Handler -> engines.toList() } }

    @Test fun brokenSystemRecognizerFallsBackOnceAndDeliversOneLocalTranscript() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle()
        assertEquals(1, system.starts); assertEquals(0, local.starts)
        assertEquals("system", voice.recognitionSource)
        // The vivo service answers ERROR_CLIENT within milliseconds, before it ever starts listening.
        system.finish(ListeningResult.error(5)); idle()
        assertEquals(1, local.starts)
        local.rms(3f); local.finish(ListeningResult.recognized("再讲一点")); idle()
        assertEquals(listOf(ListeningOutcome.SUCCESS), outcomes.map { it.outcome })
        assertEquals("再讲一点", outcomes.single().text)
        assertEquals("vosk-cn", voice.recognitionSource)
        voice.close()
    }

    @Test fun anUnusableRecognizerIsNotProbedAgainOnTheNextListen() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle(); system.finish(ListeningResult.error(5)); idle()
        local.finish(ListeningResult.recognized("跳过")); idle()
        // Second tap must not pay for the dead recogniser again — no probe, no retry storm.
        voice.listenOutcome(outcomes::add); idle()
        assertEquals(1, system.starts); assertEquals(2, local.starts)
        local.finish(ListeningResult.recognized("再讲一点")); idle()
        assertEquals(listOf(ListeningOutcome.SUCCESS, ListeningOutcome.SUCCESS), outcomes.map { it.outcome })
        voice.close()
    }

    @Test fun silenceAndNoMatchAreNeverRewrittenAsABackendFallback() {
        listOf(ListeningOutcome.NO_MATCH, ListeningOutcome.TIMEOUT).forEach { acoustic ->
            val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
            val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
            voice.listenOutcome(outcomes::add); idle()
            system.ready(); idle()
            system.finish(ListeningResult(acoustic)); idle()
            assertEquals(0, local.starts)
            assertEquals(listOf(acoustic), outcomes.map { it.outcome })
            voice.close()
        }
    }

    @Test fun aTransientErrorAfterListeningStartedDoesNotRestartRecognition() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle()
        system.ready(); idle()
        system.finish(ListeningResult.error(5)); idle()
        assertEquals(0, local.starts)
        assertEquals(ListeningOutcome.CLIENT, outcomes.single().outcome)
        assertEquals(5, outcomes.single().androidCode)
        voice.close()
    }

    @Test fun whenNoBackendCanServeTheFailureStaysAccurateAndIsNotCalledNoMatch() {
        val system = FixedOutcomeEngine("system", ListeningOutcome.UNAVAILABLE)
        val local = FixedOutcomeEngine("vosk-cn", ListeningOutcome.UNAVAILABLE)
        val voice = voiceWith(system, local); var outcome: ListeningResult? = null
        voice.listenOutcome { outcome = it }; idle()
        assertEquals(1, system.starts); assertEquals(1, local.starts)
        assertEquals(ListeningOutcome.UNAVAILABLE, outcome?.outcome)
        assertEquals("系统语音识别暂不可用。", outcome?.notice)
        assertFalse(outcome!!.notice.contains("没听清"))
        voice.close()
    }

    @Test fun everyBackendUnusableIsRememberedSoTheNextTapAnswersImmediately() {
        val system = FixedOutcomeEngine("system", ListeningOutcome.UNAVAILABLE)
        val local = FixedOutcomeEngine("vosk-cn", ListeningOutcome.UNAVAILABLE)
        val voice = voiceWith(system, local); var outcomes = 0
        voice.listenOutcome { outcomes++ }; idle()
        voice.listenOutcome { outcomes++ }; idle()
        assertEquals(2, outcomes)
        assertEquals(1, system.starts); assertEquals(1, local.starts)
        voice.close()
    }

    @Test fun cancellingDuringTheFallbackDiscardsTheLateLocalTranscript() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle(); system.finish(ListeningResult.error(5)); idle()
        local.ready(); idle()
        voice.stop(); idle()
        assertEquals(listOf(ListeningOutcome.CANCELLED), outcomes.map { it.outcome })
        local.finish(ListeningResult.recognized("过期")); idle()
        assertEquals(1, outcomes.size)
        assertEquals(VoiceDetail(), voice.detail)
        voice.close()
    }

    @Test fun theAbandonedAttemptCannotFireASecondFallbackWhenTheWatchdogExpires() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle(); system.finish(ListeningResult.error(5)); idle()
        assertEquals(1, local.starts)
        // The local engine goes quiet without ever answering; only its own watchdog may close the attempt.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15)); idle()
        assertEquals(listOf(ListeningOutcome.TIMEOUT), outcomes.map { it.outcome })
        assertEquals(1, system.starts); assertEquals(1, local.starts)
        assertEquals(VoiceDetail(), voice.detail)
        voice.close()
    }

    @Test fun localEngineLevelsDriveTheSameListeningMeter() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local)
        voice.listenOutcome { }; idle(); system.finish(ListeningResult.error(5)); idle()
        local.ready(); idle(); assertEquals(VoicePhase.LISTENING, voice.detail.phase)
        local.rms(3f); idle(); assertTrue(voice.detail.level > 0f)
        val level = voice.detail.level
        local.rms(Float.NaN); local.rms(Float.POSITIVE_INFINITY); idle()
        assertEquals(level, voice.detail.level, 0.001f)
        voice.close()
    }

    @Test fun spokenFallbackTextReachesTheQuietCommandPath() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local)
        val journey = Journey({ 1000000L }, voice); journey.start()
        val answers = mutableListOf<String>()
        // DirectorLoop.speak wires beginListening to requestInput; that is the path a spoken command takes.
        journey.beginListening { text -> answers += text; journey.requestInput(text) }; idle()
        system.finish(ListeningResult.error(5)); idle()
        local.finish(ListeningResult.recognized("安静十分钟")); idle()
        assertEquals(listOf("安静十分钟"), answers)
        assertEquals(JourneyState.QUIET, journey.state)
        assertEquals("", journey.notice)
        voice.close()
    }

    @Test fun interruptionOfSpeechStillOpensTheMicrophoneForOneShotListening() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.SIMPLIFIED_CHINESE)
        val engine = DrivingEngine("system")
        val voice = voiceWith(engine)
        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        tts.onInitListener.onInit(TextToSpeech.SUCCESS); idle()
        voice.speak("正式朗读") { }; idle()
        assertTrue(tts.lastSpokenText.startsWith("正式朗读"))
        // Tapping the mic during narration must still reach a recogniser rather than dead-end.
        voice.listenOutcome { }; idle()
        assertEquals(1, engine.starts)
        assertEquals(VoicePhase.PREPARING_LISTEN, voice.detail.phase)
        voice.close()
    }

    @Test fun aBusyRecogniserIsRetriedNextTimeInsteadOfBeingWrittenOff() {
        val system = DrivingEngine("system"); val local = DrivingEngine("vosk-cn")
        val voice = voiceWith(system, local)
        voice.listenOutcome { }; idle(); system.finish(ListeningResult.error(8)); idle()
        local.finish(ListeningResult(ListeningOutcome.NO_MATCH)); idle()
        // BUSY is momentary, so the next tap must offer the system recogniser another chance.
        voice.listenOutcome { }; idle()
        assertEquals(2, system.starts)
        voice.close()
    }

    @Test fun theLocalEngineRefusesToOpenTheMicrophoneWithoutThePermissionAndSaysSo() {
        // No grantPermissions() here on purpose: the engine guards itself rather than trusting its caller.
        val engine = VoskSpeechEngine(app, Handler(Looper.getMainLooper()))
        val outcomes = mutableListOf<ListeningResult>()
        engine.start(object : RecognitionSink {
            override fun onReady() {}
            override fun onRms(level: Float) {}
            override fun onEndOfSpeech() {}
            override fun onFinish(result: ListeningResult) { outcomes += result }
        })
        idle()
        assertEquals(listOf(ListeningOutcome.PERMISSION_DENIED), outcomes.map { it.outcome })
        assertNotEquals(ListeningOutcome.NO_MATCH, outcomes.single().outcome)
        engine.cancel()
    }

    @Test fun interimHypothesisAppearsOnScreenButIsNeverSubmitted() {
        val engine = DrivingEngine("system"); val voice = voiceWith(engine)
        val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle()
        engine.ready(); engine.partial("再讲"); idle()
        assertEquals(TranscriptText("再讲"), voice.transcript)
        assertTrue(voice.transcript.present)
        assertEquals(0, outcomes.size)
        engine.partial("再讲一点"); idle()
        assertEquals("再讲一点", voice.transcript.text)
        assertEquals(0, outcomes.size)
        voice.close()
    }

    @Test fun finalTranscriptStaysVisibleAfterTheAttemptCloses() {
        val engine = DrivingEngine("system"); val voice = voiceWith(engine)
        voice.listenOutcome { }; idle()
        engine.ready(); engine.partial("跳过"); engine.finish(ListeningResult.recognized("跳过")); idle()
        assertEquals(TranscriptText("跳过", final = true), voice.transcript)
        assertEquals(VoiceDetail(), voice.detail)
        voice.close()
    }

    @Test fun aMissOrASilenceNeverLeavesAnInventedTranscriptOnScreen() {
        listOf(ListeningOutcome.NO_MATCH, ListeningOutcome.TIMEOUT, ListeningOutcome.UNAVAILABLE).forEach { outcome ->
            val engine = DrivingEngine("system"); val voice = voiceWith(engine)
            voice.listenOutcome { }; idle()
            engine.ready(); engine.partial("半句"); idle()
            engine.finish(ListeningResult(outcome)); idle()
            assertEquals("$outcome", TranscriptText(), voice.transcript)
            voice.close()
        }
    }

    @Test fun cancelAndTheNextListenBothStartFromAnEmptyTranscript() {
        val engine = DrivingEngine("system"); val voice = voiceWith(engine)
        voice.listenOutcome { }; idle(); engine.ready(); engine.partial("再讲"); idle()
        voice.clearTranscript(); assertEquals(TranscriptText(), voice.transcript)
        engine.partial("残留"); idle()
        voice.listenOutcome { }; idle()
        assertEquals("a new attempt must not inherit the previous text", TranscriptText(), voice.transcript)
        voice.close()
    }

    @Test fun chineseTranscriptsLoseVoskSpacingAndEmptyJsonStaysEmpty() {
        assertEquals("再讲一点", VoskSpeechEngine.voskTranscript("""{"text":"再 讲 一 点"}"""))
        assertEquals("", VoskSpeechEngine.voskTranscript("""{"text":""}"""))
        assertEquals("", VoskSpeechEngine.voskTranscript(null))
        assertEquals("", VoskSpeechEngine.voskTranscript("not json"))
        assertEquals("三星堆为什么这么有名",
            VoskSpeechEngine.voskTranscript("""{"text":"三 星 堆 为 什 么 这 么 有 名","result":[]}"""))
    }

    @Test fun startupFailuresAreExactlyTheBackendBreakages() {
        ListeningOutcome.entries.forEach { outcome ->
            val fallsBack = SpeechEngine.isStartupFailure(ListeningResult(outcome))
            assertEquals(outcome in setOf(ListeningOutcome.UNAVAILABLE, ListeningOutcome.BUSY,
                ListeningOutcome.NETWORK, ListeningOutcome.SERVER, ListeningOutcome.CLIENT), fallsBack)
        }
    }
}
