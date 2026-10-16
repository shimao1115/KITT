package com.kitt.reader

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExploreVoiceTest {
    @Test fun dedicatedAndroidVoiceUsesOfflineChineseAndSavedRateWithoutTouchingRuntimeVoice() {
        val app = (RuntimeEnvironment.getApplication() as KittApp)
        val runtime = app.runtime; val journeyState = runtime.journey.state
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        val local = Voice("explore-local", Locale.CHINA, 300, 100, false, emptySet())
        ShadowTextToSpeech.addVoice(local)
        ShadowTextToSpeech.addVoice(Voice("cloud", Locale.CHINA, 300, 100, true, emptySet()))
        val voice = AndroidVoice(app).apply { offlineRequired = { true }; voiceName = "cloud"; speechRate = 0.5f }
        try {
            val platform = ShadowTextToSpeech.getLastTextToSpeechInstance()
            shadowOf(platform).onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            val player = ExplorePlayback(voice) { voice.offlineReady }
            val story = ExploreFixture.stories().stories.first()
            player.play(story); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(story.text, shadowOf(platform).lastSpokenText)
            assertEquals(local, shadowOf(platform).currentVoice)
            assertEquals(0.5f, voice.speechRate); assertEquals("cloud", voice.voiceName)
            assertEquals(journeyState, runtime.journey.state); assertTrue(runtime.journey.narrationMemory.isEmpty())
            player.stop(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(11))
            assertEquals(ExploreSpeech.IDLE, player.state)
        } finally { voice.close() }
    }
    @Test fun engineWatchdogMakesStalledManualPlaybackRetryable() {
        val app = (RuntimeEnvironment.getApplication() as KittApp)
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        ShadowTextToSpeech.addVoice(Voice("local", Locale.CHINA, 300, 100, false, emptySet()))
        val voice = AndroidVoice(app).apply { offlineRequired = { true } }
        try {
            shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            // Simulate an engine which accepts speech but never delivers progress or completion.
            ShadowTextToSpeech.getLastTextToSpeechInstance().setOnUtteranceProgressListener(null)
            val player = ExplorePlayback(voice) { voice.offlineReady }
            player.play(ExploreFixture.stories().stories.first())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(11))
            assertEquals(ExploreSpeech.ERROR, player.state)
            player.stop(); assertEquals(ExploreSpeech.IDLE, player.state)
        } finally { voice.close() }
    }
}
