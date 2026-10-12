package com.kitt.reader

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealAreaContinuityTest {
    private val wallOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private val street = AreaIdentity("成都市", "新都区", "新都街道", "四川省")
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(LocationManager::class.java)
    private fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
    }
    private fun deliver(provider: String = LocationManager.NETWORK_PROVIDER, latitudeValue: Double = 30.0,
        accuracyValue: Float = 30f, speedValue: Float = 0f) {
        shadowOf(manager).simulateLocation(Location(provider).apply {
            latitude = latitudeValue; longitude = 104.0; accuracy = accuracyValue; speed = speedValue
            time = wallOffset + SystemClock.elapsedRealtime(); elapsedRealtimeNanos = SystemClock.elapsedRealtime() * 1_000_000
        })
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun TestScope.second() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1)); advanceTimeBy(1000); runCurrent()
    }

    @Test fun tenMinutePublicationSurvivesEmptyErrorTimeoutAndRefreshWithoutFalseChapterChange() = runTest {
        grant(); Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0; val fixes = mutableListOf<Fix>(); val cards = AreaCards()
        val source = RealLocationSource(app, {}, {
            when (++calls) {
                2 -> null
                3 -> throw IllegalStateException("service unavailable")
                4 -> { delay(9000); street }
                else -> street
            }
        })
        try {
            source.start { fixes += it; cards.accept(it.administrative) }
            deliver(); runCurrent(); assertEquals(street, fixes.last().administrative)
            val initial = fixes.size
            for (second in 1..600) {
                second(); deliver(); runCurrent()
                assertEquals("second=$second", street, fixes.last().administrative)
                if (second == 369) assertNull(source.areaLookupSince)
            }
            assertTrue(fixes.drop(initial).all { it.administrative == street })
            assertEquals(6, calls); assertEquals(1, cards.transitions)
            File("build/acceptance").mkdirs()
            File("build/acceptance/area-stability.txt").writeText(
                "PASS: production RealLocationSource + resolver + AreaCards; Robolectric API34, synthetic NETWORK 30m.\n" +
                "600s, ${fixes.size} publications, $calls geocodes (success/empty/error/8s timeout/success/success), " +
                "0 null or street drops after first success, ${cards.transitions} chapter transition.\n" +
                "Not vivo/OEM service, actual network, real driving or UI screenshot evidence.\n")
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun neverSuccessfulLookupStaysUnresolvedAndDoesNotStorm() = runTest {
        grant(); Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}, { calls++; null })
        try {
            source.start(fixes::add); deliver(); runCurrent()
            repeat(300) { second(); deliver(); runCurrent() }
            assertEquals(3, calls); assertTrue(fixes.all { it.administrative == null })
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun bridgeStripsStreetThenFreshReturnAndRestartRequireNewResolution() = runTest {
        grant(); Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}, { if (++calls == 1) street else null })
        try {
            source.start(fixes::add); deliver(); runCurrent()
            assertEquals(street, fixes.last().administrative)
            repeat(31) { second() }
            assertEquals(FixSource.LAST_KNOWN, fixes.last().source)
            assertEquals(street.copy(chapter = ""), fixes.last().administrative)
            repeat(30) { second() }
            deliver(); runCurrent(); assertNull(fixes.last().administrative)
            source.stop(); runCurrent(); source.start(fixes::add); deliver(); runCurrent()
            assertNull(fixes.last().administrative)
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun drivingDistanceAndGpsNetworkSwitchInvalidateBeforeFailedRefreshCanCarryOldStreet() = runTest {
        grant(); Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}, { if (++calls == 1) street else null })
        try {
            source.start(fixes::add); deliver(); runCurrent()
            for (s in 1..20) { second(); deliver(latitudeValue = 30.0 + s * (100.0 / 3.6) / 111195, speedValue = (100.0 / 3.6).toFloat()); runCurrent() }
            assertNull(fixes.last().administrative)
            second(); deliver(LocationManager.GPS_PROVIDER, latitudeValue = 30.005); runCurrent()
            assertEquals(FixSource.GPS, fixes.last().source); assertNull(fixes.last().administrative)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            second(); deliver(latitudeValue = 30.005); runCurrent()
            assertEquals(FixSource.NETWORK, fixes.last().source); assertNull(fixes.last().administrative)
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun reliableDifferentDistrictOnStationaryRefreshReplacesLabelImmediately() = runTest {
        grant(); Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0; val fixes = mutableListOf<Fix>(); val cards = AreaCards()
        val other = street.copy(district = "金牛区", chapter = "天回镇街道")
        val source = RealLocationSource(app, {}, { if (++calls == 1) street else other })
        try {
            source.start { fixes += it; cards.accept(it.administrative) }; deliver(); runCurrent()
            repeat(120) { second(); deliver(); runCurrent() }
            assertEquals(2, calls); assertEquals(other, fixes.last().administrative)
            assertEquals(2, cards.transitions)
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun coarsePermissionNeverPublishesStreetEvenIfProviderReportsSmallAccuracy() = runTest {
        grant(); shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixes = mutableListOf<Fix>(); val source = RealLocationSource(app, {}, { street })
        try {
            source.start(fixes::add); deliver(); runCurrent()
            assertEquals(street.copy(chapter = ""), fixes.last().administrative)
        } finally { source.stop(); runCurrent(); Dispatchers.resetMain() }
    }
}
