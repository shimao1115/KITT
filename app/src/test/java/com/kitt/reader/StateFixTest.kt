package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceIsolationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun runtime() = KittRuntime(app, TestVoice())
    private fun assertReal(runtime: KittRuntime) {
        assertTrue(runtime.source is RealLocationSource)
        assertFalse(runtime.simulation)
        assertTrue(runtime.sourceLabel.startsWith("手机 GPS"))
        assertFalse(runtime.sourceLabel.contains("成都"))
    }
    private fun assertIdle(runtime: KittRuntime) {
        assertFalse(runtime.journey.running)
        assertNull(runtime.source)
        assertFalse(runtime.simulation)
        assertEquals("手机 GPS · 无地图增强", runtime.sourceLabel)
        assertEquals("", runtime.sourceNotice)
    }
    @Test fun upgradeDiscardsLegacySimulationButPreservesProviderAndSpeedPreferences() {
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("simulation", true).putString("provider", "CHATGPT")
            .putString("model", "gpt-5.6-sol").putString("effort", "medium")
            .putFloat("acceleration", 16f).putFloat("simulation_speed", 100f).commit()
        val runtime = runtime()
        assertIdle(runtime)
        assertFalse(prefs.contains("simulation"))
        assertEquals(ProviderKind.CHATGPT, runtime.config.kind)
        assertEquals("gpt-5.6-sol", runtime.config.model)
        assertEquals("medium", runtime.config.effort)
        assertEquals(16.0, runtime.acceleration, 0.0)
        assertEquals(100.0, runtime.simulationSpeed, 0.0)
        runtime.start(); assertReal(runtime)
        runtime.end(); runtime.scope.cancel()
    }
    @Test fun allProvidersUseRealGpsNormallyAndSimulationOnlyWhenExplicitlyStarted() {
        ProviderKind.entries.forEach { kind ->
            val runtime = runtime()
            runtime.config = runtime.config.copy(kind = kind)
            runtime.start(); assertReal(runtime)
            runtime.end(); assertIdle(runtime); runtime.dismissSummary()
            runtime.developer(speed = 60.0, speedKmh = 80.0)
            assertIdle(runtime)
            runtime.start(simulated = true)
            assertTrue(runtime.source is SimulatedLocationSource)
            assertTrue(runtime.simulation)
            assertTrue(runtime.sourceLabel.contains("新都→安州雎水"))
            runtime.end(); assertIdle(runtime); runtime.dismissSummary()
            runtime.start(); assertReal(runtime)
            assertEquals(kind, runtime.config.kind)
            runtime.end(); runtime.scope.cancel()
        }
    }
    @Test fun stoppedSourceClearsRouteAndCompletionNotice() {
        val runtime = runtime()
        runtime.start(simulated = true)
        runtime.locationUnavailable("模拟已到终点")
        runtime.end(); assertIdle(runtime)
        runtime.end(); assertIdle(runtime)
        runtime.scope.cancel()
    }
    @Test fun serviceLossKeepsExplicitSimulationRecoveryWithoutMakingItTheDefault() {
        val runtime = runtime()
        runtime.start(simulated = true); runtime.journey.requestInput("去绵阳")
        runtime.serviceLost(); assertIdle(runtime)
        assertTrue(runtime.recovery!!.simulation)
        val restarted = runtime()
        assertIdle(restarted)
        restarted.resumeRequested = true; restarted.start()
        assertTrue(restarted.source is SimulatedLocationSource)
        assertEquals("去绵阳", restarted.journey.destination)
        restarted.end(); assertIdle(restarted)
        restarted.dismissSummary(); restarted.start(); assertReal(restarted)
        restarted.end(); restarted.scope.cancel(); runtime.scope.cancel()
    }
    @Test fun serviceStartDefaultsToRealAndExplicitExtraStartsSimulation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val runtime = (app as KittApp).runtime
        val service = Robolectric.buildService(JourneyService::class.java).create()
        val start = Intent(app, JourneyService::class.java).setAction(JourneyService.START)
        service.get().onStartCommand(start, 0, 1); assertReal(runtime)
        runtime.end()
        service.get().onStartCommand(start.putExtra(JourneyService.SIMULATED, true), 0, 2)
        assertTrue(runtime.source is SimulatedLocationSource)
        service.get().onStartCommand(Intent(app, JourneyService::class.java).setAction(JourneyService.END), 0, 3)
        assertIdle(runtime)
        service.destroy(); runtime.scope.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsBackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun systemBackFromSettingsReturnsToMainWithoutFinishingAndPageReturnStillWorks() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("KITT V0 · ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
        compose.runOnIdle { assertFalse(compose.activity.isFinishing) }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("返回").performScrollTo().performClick()
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
    }
    @Test fun idleMainSystemBackRetainsPlatformExitBehavior() {
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(compose.activity.isFinishing)
    }
    @Test fun developerStartPassesSimulationOnlyForThatAction() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        repeat(5) { compose.onNodeWithText("路上读山河").performClick() }
        compose.onNodeWithText("开始").performClick()
        compose.runOnIdle {
            assertTrue(shadowOf(app).nextStartedService.getBooleanExtra(JourneyService.SIMULATED, false))
        }
        compose.onNodeWithText("开始读山河").performClick()
        compose.runOnIdle {
            assertFalse(shadowOf(app).nextStartedService.getBooleanExtra(JourneyService.SIMULATED, true))
        }
    }
}
