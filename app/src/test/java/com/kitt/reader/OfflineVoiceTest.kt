package com.kitt.reader

import android.Manifest
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
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
class OfflineVoiceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun initialize() {
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(engine).onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun offlineUsesInstalledLocalVoiceWithoutChangingSavedSelection() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        val local = Voice("local", Locale.CHINA, 300, 100, false, emptySet())
        val network = Voice("cloud", Locale.CHINA, 300, 100, true, emptySet())
        ShadowTextToSpeech.addVoice(network); ShadowTextToSpeech.addVoice(local)
        val voice = AndroidVoice(app).apply { offlineRequired = { true }; voiceName = "cloud" }
        try {
            initialize(); assertTrue(voice.offlineReady)
            voice.speak("离线中文") {}; shadowOf(Looper.getMainLooper()).idle()
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            assertEquals(local, engine.currentVoice); assertEquals("离线中文", engine.lastSpokenText)
            assertEquals("cloud", voice.voiceName)
        } finally { voice.close() }
    }
    @Test fun missingOfflineDataFailsWithoutQueuingNetworkSpeech() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        ShadowTextToSpeech.addVoice(Voice("cloud", Locale.CHINA, 300, 100, true, emptySet()))
        val voice = AndroidVoice(app).apply { offlineRequired = { true } }
        try {
            initialize(); assertFalse(voice.offlineReady)
            var completion: Boolean? = null
            voice.speak("不能假装有声") { completion = it }; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(false, completion); assertNull(shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
            assertTrue(voice.voiceMessage.contains("未安装离线中文声音"))
        } finally { voice.close() }
    }
    @Test fun offlineListeningStartsOnlyBundledVoskAndNeverSystemNetworkRecognizer() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val started = mutableListOf<String>()
        fun engine(name: String) = object : SpeechEngine {
            override val id = name
            override fun start(sink: RecognitionSink) { started += name; sink.onReady() }
            override fun cancel() {}
        }
        val voice = AndroidVoice(app) { _, _ -> listOf(engine("system"), engine("vosk-cn")) }
        voice.offlineRequired = { true }
        try {
            voice.listen {}; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("vosk-cn"), started)
        } finally { voice.close() }
    }
    @Test fun localSpeechRequirementPreservesOnlineSystemRecognitionOrder() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val started = mutableListOf<String>()
        fun engine(name: String) = object : SpeechEngine {
            override val id = name
            override fun start(sink: RecognitionSink) { started += name; sink.onReady() }
            override fun cancel() {}
        }
        val voice = AndroidVoice(app) { _, _ -> listOf(engine("system"), engine("vosk-cn")) }
        voice.offlineRequired = { true }; voice.offlineListeningRequired = { false }
        try {
            voice.listen {}; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("system"), started)
        } finally { voice.close() }
    }
}
