package com.kitt.reader

import android.os.Looper
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeVoiceTest {
    @Implements(TextToSpeech::class)
    class ManualTts : ShadowTextToSpeech() {
        val queued = mutableListOf<Pair<String, String>>()
        @Implementation
        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, id: String): Int {
            queued += id to text.toString()
            return TextToSpeech.SUCCESS // Let this test supply actual progress callbacks explicitly.
        }
    }
    @Config(shadows = [ManualTts::class])
    @Test fun longOfflineTextOnlyCompletesAfterFinalChunkAndLateCallbacksAfterStopAreIgnored() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        ShadowTextToSpeech.addVoice(Voice("local", Locale.CHINA, 300, 100, false, emptySet()))
        val voice = AndroidVoice(RuntimeEnvironment.getApplication(), engineFactory = { _, _ -> emptyList() })
        voice.offlineRequired = { true }
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle()
        val text = "完整中文故事，不能在前半段就标完成。".repeat(240)
        val chunks = speechChunks(text)
        assertEquals(text, chunks.joinToString("")); assertTrue(chunks.size > 1)
        var completed = 0
        voice.speak(text) { if (it) completed++ }
        val listener = engine.utteranceProgressListener
        listener.onStart("1:0"); listener.onDone("1:0"); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, completed)
        assertEquals(text, (engine as ManualTts).queued.joinToString("") { it.second })
        listener.onDone("1:${chunks.lastIndex}"); shadowOf(Looper.getMainLooper()).idle(); assertEquals(1, completed)
        voice.speak(text) { if (it) completed++ }
        voice.stop(); listener.onDone("2:${chunks.lastIndex}"); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, completed); voice.close()
    }
    @Test fun coldTtsIsPreparingAndStartupWatchdogClearsIt() {
        val voice = AndroidVoice(RuntimeEnvironment.getApplication(), engineFactory = { _, _ -> emptyList() })
        var completed: Boolean? = null
        voice.speak("你好") { completed = it }
        assertEquals(VoicePhase.PREPARING_SPEECH, voice.detail.phase)
        assertNotNull(voice.speechQueuedSince)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertEquals(false, completed); assertEquals(VoicePhase.IDLE, voice.detail.phase)
        assertNull(voice.speechQueuedSince); voice.close()
    }
    @Test fun interruptionDropsQueuedSpeechAndLateStartCannotClaimPlayback() {
        val voice = AndroidVoice(RuntimeEnvironment.getApplication(), engineFactory = { _, _ -> emptyList() })
        var called = false
        voice.speak("你好") { called = true }
        val listener = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).utteranceProgressListener
        voice.stop(); listener.onStart("1:0"); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VoicePhase.IDLE, voice.detail.phase); assertNull(voice.speechQueuedSince); assertFalse(called)
        voice.close()
    }
}
