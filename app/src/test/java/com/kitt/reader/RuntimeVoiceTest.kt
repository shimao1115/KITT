package com.kitt.reader

import android.os.Looper
import android.speech.tts.TextToSpeech
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeVoiceTest {
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
