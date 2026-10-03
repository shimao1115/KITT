package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.Locale

class VoiceOutcomeTest {
    @Test fun everyAndroidErrorMapsExplicitlyIncludingNewerCodes() {
        val expected = mapOf(1 to ListeningOutcome.NETWORK, 2 to ListeningOutcome.NETWORK,
            3 to ListeningOutcome.CLIENT, 4 to ListeningOutcome.SERVER, 5 to ListeningOutcome.CLIENT,
            6 to ListeningOutcome.TIMEOUT, 7 to ListeningOutcome.NO_MATCH, 8 to ListeningOutcome.BUSY,
            9 to ListeningOutcome.PERMISSION_DENIED, 10 to ListeningOutcome.BUSY, 11 to ListeningOutcome.SERVER,
            12 to ListeningOutcome.UNAVAILABLE, 13 to ListeningOutcome.UNAVAILABLE,
            14 to ListeningOutcome.CLIENT, 15 to ListeningOutcome.CLIENT)
        expected.forEach { (code, outcome) ->
            val result = ListeningResult.error(code)
            assertEquals(outcome, result.outcome); assertEquals(code, result.androidCode)
        }
        assertEquals(ListeningOutcome.CLIENT, ListeningResult.error(999).outcome)
    }
    @Test fun failuresCannotMasqueradeAsNoMatchAndCancelledHasNoNotice() {
        val noMatch = ListeningResult(ListeningOutcome.NO_MATCH).notice
        listOf(ListeningOutcome.UNAVAILABLE, ListeningOutcome.BUSY, ListeningOutcome.NETWORK,
            ListeningOutcome.SERVER, ListeningOutcome.PERMISSION_DENIED, ListeningOutcome.CLIENT, ListeningOutcome.TIMEOUT).forEach {
            assertNotEquals(noMatch, ListeningResult(it).notice)
            assertFalse(ListeningResult(it).notice.contains("没听清"))
        }
        assertEquals("", ListeningResult(ListeningOutcome.CANCELLED).notice)
        assertEquals(ListeningOutcome.SUCCESS, ListeningResult.recognized(" 再讲一点 ").outcome)
        assertEquals("再讲一点", ListeningResult.recognized(" 再讲一点 ").text)
        assertEquals(ListeningOutcome.NO_MATCH, ListeningResult.recognized("  ").outcome)
    }
    @Test fun rmsIsSmoothedClampedFiniteAndResetsAtEveryBoundary() {
        val feedback = ListeningFeedback()
        feedback.rms(100f); assertEquals(0f, feedback.detail.level)
        feedback.preparing(); assertEquals(VoicePhase.PREPARING_LISTEN, feedback.detail.phase)
        feedback.ready(); feedback.rms(100f); assertEquals(0.35f, feedback.detail.level, 0.001f)
        feedback.rms(-100f); assertEquals(0.287f, feedback.detail.level, 0.001f)
        val before = feedback.detail
        feedback.rms(Float.NaN); feedback.rms(Float.POSITIVE_INFINITY); assertEquals(before, feedback.detail)
        repeat(100) { feedback.rms(100f) }; assertTrue(feedback.detail.level in 0f..1f)
        feedback.endSpeech(); assertEquals(VoiceDetail(VoicePhase.PROCESSING), feedback.detail)
        feedback.rms(100f); assertEquals(0f, feedback.detail.level)
        feedback.reset(); assertEquals(VoiceDetail(), feedback.detail)
        feedback.ready(); feedback.rms(10f); feedback.preparing(); assertEquals(0f, feedback.detail.level)
    }
    private class OutcomeVoice : VoicePort {
        val fake = TestVoice()
        var answer: ((ListeningResult) -> Unit)? = null
        override fun stop() { fake.stop(); val old = answer; answer = null; old?.invoke(ListeningResult(ListeningOutcome.CANCELLED)) }
        override fun speak(text: String, complete: (Boolean) -> Unit) = fake.speak(text, complete)
        override fun listen(result: (String?) -> Unit) = error("Structured path required")
        override fun listenOutcome(result: (ListeningResult) -> Unit) { answer = result }
    }
    @Test fun journeyUsesStructuredFailuresAndOnlySuccessGoesToDirector() {
        val voice = OutcomeVoice(); val journey = Journey({ 1000000 }, voice); journey.start()
        val answers = mutableListOf<String>()
        ListeningOutcome.entries.filter { it != ListeningOutcome.SUCCESS }.forEach { outcome ->
            journey.beginListening(answers::add); voice.answer!!(ListeningResult(outcome))
            assertEquals(ListeningResult(outcome).notice, journey.notice)
            assertEquals(JourneyState.READING, journey.state)
        }
        assertTrue(answers.isEmpty())
        journey.beginListening(answers::add); voice.answer!!(ListeningResult.recognized("再讲一点"))
        assertEquals(listOf("再讲一点"), answers)
    }
    @Test fun changedIntentCancelsWithoutFalseErrorsOrLateTranscripts() {
        listOf<(Journey) -> Unit>({ it.skip() }, { it.quiet() }, { it.end() }, { it.invalidateProvider() }).forEach { action ->
            val voice = OutcomeVoice(); val journey = Journey({ 1000000 }, voice); journey.start()
            var answers = 0; journey.beginListening { answers++ }; val old = voice.answer!!
            action(journey); old(ListeningResult.error(7)); old(ListeningResult.recognized("过期"))
            assertEquals("", journey.notice); assertEquals(0, answers)
            assertEquals(VoiceVisual.IDLE, voiceVisual(journey.state))
        }
    }
    @Test fun speakingVisualEndsOnCompletionFailureInterruptSkipQuietAndEnd() {
        listOf<(Journey, TestVoice) -> Unit>({ _, v -> v.finish() }, { _, v -> v.done!!(false) },
            { j, _ -> j.beginListening {} }, { j, _ -> j.skip() }, { j, _ -> j.quiet() }, { j, _ -> j.end() }).forEach { finish ->
            val voice = TestVoice(); val journey = Journey({ 1000000 }, voice); journey.start()
            journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "主题", "讲述").json())
            assertEquals(VoiceVisual.SPEAKING, voiceVisual(journey.state))
            finish(journey, voice)
            assertNotEquals(VoiceVisual.SPEAKING, voiceVisual(journey.state))
            assertEquals(if (journey.listening) VoiceVisual.LISTENING else VoiceVisual.IDLE, voiceVisual(journey.state))
        }
    }
    @Test fun chineseVoiceOrderPreservesNamesLocalesAndNetworkMetadata() {
        val local = TtsVoiceOption("系统名称", Locale.SIMPLIFIED_CHINESE, false)
        val network = local.copy(name = "另一个名称", networkRequired = true)
        val other = TtsVoiceOption("台湾", Locale.TRADITIONAL_CHINESE, false)
        val cantonese = TtsVoiceOption("粤语", Locale.forLanguageTag("yue-HK"), false)
        val missing = local.copy(name = "需下载", installed = false)
        val english = local.copy(name = "English", locale = Locale.US)
        assertEquals(listOf(local, network, missing, other, cantonese), chineseVoices(listOf(english, other, network, missing, local, local, cantonese)))
        assertTrue(network.label.contains("需联网")); assertTrue(local.label.contains("zh-CN"))
        assertFalse(local.label.contains("女")); assertFalse(local.label.contains("男"))
    }
    @Test fun missingVoiceFallsBackToChineseSystemDefaultThenUsableChinese() {
        val voice = TtsVoiceOption("系统", Locale.SIMPLIFIED_CHINESE, false)
        val selected = voice.copy(name = "选中的")
        assertEquals(selected, selectedChineseVoice("选中的", listOf(voice, selected), "系统"))
        assertEquals(voice, selectedChineseVoice("消失了", listOf(voice, selected), "系统"))
        assertEquals(voice, selectedChineseVoice("消失了", listOf(voice), "也消失了"))
        assertNull(selectedChineseVoice("选中的", listOf(selected.copy(installed = false)), null))
        assertNull(selectedChineseVoice("", listOf(voice.copy(locale = Locale.US)), null))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidVoiceExperienceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun recognizerAvailable() {
        val info = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = "example.recognizer"; name = "Recognition"
                applicationInfo = ApplicationInfo().apply { packageName = "example.recognizer" } }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), info)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }
    @Test fun androidCallbacksDriveLiveRmsAndReturnOnlyOneResult() {
        recognizerAvailable(); val voice = AndroidVoice(app); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle()
        val platform = ShadowSpeechRecognizer.getLatestSpeechRecognizer(); val recognizer = shadowOf(platform)
        assertEquals(VoicePhase.PREPARING_LISTEN, voice.detail.phase)
        recognizer.triggerOnReadyForSpeech(Bundle()); idle(); assertEquals(VoicePhase.LISTENING, voice.detail.phase)
        recognizer.triggerOnRmsChanged(10f); idle(); assertTrue(voice.detail.level > 0f)
        recognizer.triggerOnEndOfSpeech(); idle(); assertEquals(VoiceDetail(VoicePhase.PROCESSING), voice.detail)
        val result = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("再讲一点")) }
        recognizer.triggerOnResults(result); idle()
        assertEquals(listOf(ListeningResult.recognized("再讲一点")), outcomes)
        assertEquals(VoiceDetail(), voice.detail); assertTrue(recognizer.isDestroyed)
        recognizer.triggerOnError(7); idle(); assertEquals(1, outcomes.size)
        voice.close()
    }
    @Test fun androidCancellationRejectsQueuedRmsAndLateResult() {
        recognizerAvailable(); val voice = AndroidVoice(app); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle(); val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        recognizer.triggerOnReadyForSpeech(Bundle()); recognizer.triggerOnRmsChanged(8f)
        voice.stop(); idle(); assertEquals(VoiceDetail(), voice.detail)
        recognizer.triggerOnResults(Bundle()); idle()
        assertEquals(listOf(ListeningResult(ListeningOutcome.CANCELLED)), outcomes)
        assertTrue(recognizer.isDestroyed); voice.close()
    }
    @Test fun androidErrorResetsLevelAndPreservesCode() {
        recognizerAvailable(); val voice = AndroidVoice(app); var outcome: ListeningResult? = null
        voice.listenOutcome { outcome = it }; idle(); val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        recognizer.triggerOnReadyForSpeech(Bundle()); recognizer.triggerOnRmsChanged(8f); idle()
        recognizer.triggerOnError(5); idle()
        assertEquals(ListeningResult.error(5), outcome); assertEquals(VoiceDetail(), voice.detail)
        assertTrue(recognizer.isDestroyed); voice.close()
    }
    @Test fun androidWatchdogFinishesOnceAndReleasesMicrophone() {
        recognizerAvailable(); val voice = AndroidVoice(app); val outcomes = mutableListOf<ListeningResult>()
        voice.listenOutcome(outcomes::add); idle(); val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertEquals(listOf(ListeningResult(ListeningOutcome.TIMEOUT)), outcomes)
        assertEquals(VoiceDetail(), voice.detail); assertTrue(recognizer.isDestroyed); voice.close()
    }
    @Test fun permissionDenialAndUnavailableServiceHaveAccurateOutcomes() {
        val voice = AndroidVoice(app); var result: ListeningResult? = null
        voice.listenOutcome { result = it }; assertEquals(ListeningOutcome.PERMISSION_DENIED, result?.outcome)
        voice.requestMicrophone = {}; voice.listenOutcome { result = it }
        assertEquals(VoicePhase.PREPARING_LISTEN, voice.detail.phase)
        voice.permissionResult(false); assertEquals(ListeningOutcome.PERMISSION_DENIED, result?.outcome)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        voice.listenOutcome { result = it }; assertEquals(ListeningOutcome.UNAVAILABLE, result?.outcome)
        assertEquals(VoiceDetail(), voice.detail); voice.close()
    }
    @Test fun permissionCancellationDoesNotStartARecognizerAfterGrant() {
        val voice = AndroidVoice(app); val outcomes = mutableListOf<ListeningResult>()
        voice.requestMicrophone = {}; voice.listenOutcome(outcomes::add); voice.stop(); voice.permissionResult(true)
        assertEquals(listOf(ListeningResult(ListeningOutcome.CANCELLED)), outcomes)
        assertEquals(VoiceDetail(), voice.detail); voice.close()
    }
    @Test fun selectedVoiceAndRatePersistRestoreAndLegacySavesPreserveSelection() {
        val store = SettingsStore(app)
        assertEquals("", store.voiceName)
        assertTrue(store.save(ProviderConfig(), 1.2f, "zh-special").isSuccess)
        val restored = SettingsStore(app); assertEquals("zh-special", restored.voiceName)
        val runtime = KittRuntime(app); val voice = runtime.voice as AndroidVoice
        assertEquals("zh-special", voice.voiceName); assertEquals(1.2f, voice.speechRate, 0.001f)
        assertTrue(restored.save(ProviderConfig(), 0.8f).isSuccess)
        assertEquals("zh-special", SettingsStore(app).voiceName)
        assertTrue(restored.save(ProviderConfig(), 1f, "").isSuccess)
        assertEquals("", SettingsStore(app).voiceName); voice.close()
    }
    @Test fun previewUsesDraftVoiceRateAndLeavesJourneyProviderAndSavedVoiceAlone() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.SIMPLIFIED_CHINESE)
        val first = Voice("zh-first", Locale.SIMPLIFIED_CHINESE, 300, 100, false, emptySet())
        val second = Voice("zh-second", Locale.SIMPLIFIED_CHINESE, 300, 100, false, emptySet())
        ShadowTextToSpeech.addVoice(first); ShadowTextToSpeech.addVoice(second)
        val runtime = KittRuntime(app); val voice = runtime.voice as AndroidVoice
        voice.voiceName = first.name; voice.speechRate = 0.8f
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance(); val shadow = shadowOf(engine)
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS); idle()
        val epoch = runtime.journey.epoch; val config = runtime.config
        voice.preview(second.name, 1.2f) {}; idle()
        assertEquals(second, shadow.currentVoice)
        assertEquals(120, ReflectionHelpers.getField<Bundle>(engine, "mParams").getInt("rate"))
        assertTrue(shadow.lastSpokenText.startsWith("你好，我是路上读山河"))
        assertFalse(runtime.journey.running); assertEquals(epoch, runtime.journey.epoch)
        assertEquals(config, runtime.config); assertFalse(runtime.loop.pending)
        assertEquals(first.name, voice.voiceName); assertEquals(0.8f, voice.speechRate, 0.001f)
        voice.speak("正式朗读") {}; idle(); assertEquals(first, shadow.currentVoice)
        assertEquals(80, ReflectionHelpers.getField<Bundle>(engine, "mParams").getInt("rate"))
        voice.voiceName = "missing"; voice.speak("仍能朗读") {}; idle()
        assertNotNull(shadow.currentVoice); voice.close()
    }
    @Test fun systemChineseDefaultCanStillPreviewWhenEngineDoesNotEnumerateVoices() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.SIMPLIFIED_CHINESE)
        val voice = AndroidVoice(app)
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS); idle()
        assertTrue(voice.voices.isEmpty()); assertTrue(voice.canSpeak)
        voice.voiceName = "no-longer-installed"
        voice.preview(voice.voiceName, 1f) {}; idle()
        assertTrue(shadow.lastSpokenText.startsWith("你好，我是路上读山河"))
        assertEquals(Locale.SIMPLIFIED_CHINESE, shadow.currentLanguage)
        voice.close()
    }
}
