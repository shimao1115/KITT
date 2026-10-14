package com.kitt.reader

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFirstRuntimeTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun connect(runtime: KittRuntime) {
        val callback = AndroidNetworkStatus::class.java.getDeclaredField("callback").apply { isAccessible = true }
            .get(runtime.network) as ConnectivityManager.NetworkCallback
        val network = ShadowNetwork.newInstance(301)
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, NetworkCapabilities().apply {
            shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        })
        assertTrue(runtime.network.snapshot.cloudAvailable)
    }
    @Test fun normalPhysicalSourceSpeaksWithBrokenChatGptConfigurationAndNoCloudCalls() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java)
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val wallOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
        val voice = TestVoice(); val runtime = KittRuntime(app, voice)
        runtime.config = ProviderConfig(ProviderKind.CHATGPT, model = "unavailable-test-model")
        try {
            runtime.start(); connect(runtime)
            shadowOf(manager).simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 30.823; longitude = 104.16; accuracy = 10f
                time = wallOffset + SystemClock.elapsedRealtime(); elapsedRealtimeNanos = SystemClock.elapsedRealtime() * 1_000_000
            })
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(runtime.source is RealLocationSource); assertFalse(runtime.simulation)
            assertEquals("新都区", runtime.journey.fix?.administrative?.district)
            assertTrue(runtime.journey.speaking); assertTrue(voice.speech.last().contains("新都区"))
            assertEquals("未询问", runtime.journey.destination)
            assertEquals(0, runtime.requests.directorCalls); assertEquals(0, runtime.requests.researchCalls)
            voice.finish(); runtime.loop.user("再讲一点"); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, runtime.requests.directorCalls); assertEquals(0, runtime.requests.researchCalls)
            assertTrue(runtime.statusSnapshot(SystemClock.elapsedRealtime()).services.contains("本地讲述"))
        } finally { runtime.end(); runtime.network.stop(); runtime.scope.cancel() }
    }
    @Test fun onlineRuntimeUsesOfflineChineseTtsWhileRecognitionRemainsOnlineCapable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINA)
        val local = Voice("local", Locale.CHINA, 300, 100, false, emptySet())
        ShadowTextToSpeech.addVoice(local)
        ShadowTextToSpeech.addVoice(Voice("cloud", Locale.CHINA, 300, 100, true, emptySet()))
        val runtime = KittRuntime(app); val voice = runtime.voice as AndroidVoice
        try {
            runtime.start(); connect(runtime)
            val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
            shadowOf(engine).onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            voice.voiceName = "cloud"
            voice.speak("联网本地中文讲述") {}; shadowOf(Looper.getMainLooper()).idle()
            assertTrue(voice.offlineRequired()); assertFalse(voice.offlineListeningRequired())
            assertEquals(local, shadowOf(engine).currentVoice)
            assertEquals("联网本地中文讲述", shadowOf(engine).lastSpokenText)
            assertEquals("cloud", voice.voiceName)
            assertEquals(0, runtime.requests.directorCalls); assertEquals(0, runtime.requests.researchCalls)
        } finally { runtime.end(); runtime.network.stop(); voice.close(); runtime.scope.cancel() }
    }
}
