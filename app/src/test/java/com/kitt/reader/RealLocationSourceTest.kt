package com.kitt.reader

import android.Manifest
import android.location.Address
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealLocationSourceTest {
    private val wallOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(LocationManager::class.java)
    private fun grant(fine: Boolean = true) {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
    }
    private fun location(provider: String, accuracy: Float = 8f, age: Long = 0) = Location(provider).apply {
        latitude = 30.0; longitude = 104.0; time = wallOffset + SystemClock.elapsedRealtime() - age
        elapsedRealtimeNanos = (SystemClock.elapsedRealtime() - age) * 1_000_000
        this.accuracy = accuracy
    }
    private fun deliver(location: Location) { shadowOf(manager).simulateLocation(location); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun bothProvidersRegisterAndNetworkContinuesWhenGpsDisabledThenGpsRecovers() {
        grant(); val fixes = mutableListOf<Fix>(); val notices = mutableListOf<String>()
        val source = RealLocationSource(app, notices::add)
        try {
            source.start(fixes::add)
            assertEquals(1, shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
            assertEquals(1, shadowOf(manager).getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).size)
            deliver(location(LocationManager.GPS_PROVIDER)); assertEquals(FixSource.GPS, fixes.last().source)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            deliver(location(LocationManager.NETWORK_PROVIDER, 300f))
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(FixSource.NETWORK, fixes.last().source); assertEquals(300.0, fixes.last().accuracy, 0.0)
            assertEquals("", notices.last())
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); deliver(location(LocationManager.GPS_PROVIDER))
            assertEquals(FixSource.GPS, fixes.last().source)
        } finally { source.stop() }
        assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isEmpty())
    }
    @Test fun coarsePermissionRunsNetworkWithoutRequiringFinePermission() {
        grant(fine = false); val fixes = mutableListOf<Fix>(); val notices = mutableListOf<String>()
        val source = RealLocationSource(app, notices::add)
        try {
            source.start(fixes::add)
            assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
            deliver(location(LocationManager.NETWORK_PROVIDER, 1000f))
            assertEquals(FixSource.NETWORK, fixes.last().source); assertTrue(notices.last().contains("粗略定位"))
        } finally { source.stop() }
    }
    @Test fun missingNetworkProviderStillRunsGps() {
        grant(); shadowOf(manager).removeProvider(LocationManager.NETWORK_PROVIDER)
        val fixes = mutableListOf<Fix>(); val source = RealLocationSource(app) {}
        try { source.start(fixes::add); deliver(location(LocationManager.GPS_PROVIDER)); assertEquals(FixSource.GPS, fixes.last().source) }
        finally { source.stop() }
    }
    @Test fun missingPermissionReturnsNoticeAndRegistersNothing() {
        val notices = mutableListOf<String>(); val source = RealLocationSource(app, notices::add)
        try {
            source.start { fail("No permission must not emit") }
            assertTrue(notices.last().contains("权限")); assertTrue(shadowOf(manager).locationUpdateListeners.isEmpty())
        } finally { source.stop() }
    }
    @Test fun cachesAreLastKnownAndStaleCacheIsRejected() {
        grant(); shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, location(LocationManager.GPS_PROVIDER, age = 20_000))
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(LocationManager.NETWORK_PROVIDER, 300f, 61_000))
        val fixes = mutableListOf<Fix>(); val source = RealLocationSource(app) {}
        try {
            source.start(fixes::add); assertEquals(FixSource.LAST_KNOWN, fixes.last().source)
            assertEquals(FixSource.GPS, fixes.last().originSource); assertEquals(20_000L, fixes.last().ageMs(System.currentTimeMillis()))
            assertFalse(fixes.last().precise(System.currentTimeMillis()))
        } finally { source.stop() }
    }
    @Test fun timerBridgesAndExpiresWithoutFurtherLocationCallbacks() {
        grant(); val fixes = mutableListOf<Fix>(); val notices = mutableListOf<String>()
        val source = RealLocationSource(app, notices::add)
        try {
            source.start(fixes::add); deliver(location(LocationManager.GPS_PROVIDER))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
            assertEquals(FixSource.LAST_KNOWN, fixes.last().source)
            assertTrue(fixes.last().accuracy > 8.0); assertTrue(notices.last().contains("上次定位"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
            assertTrue(notices.last().contains("暂无可靠定位"))
            assertEquals(FixSource.LAST_KNOWN, fixes.last().source)
            assertTrue(fixes.last().ageMs(System.currentTimeMillis()) > 60_000)
        } finally { source.stop() }
    }
    @Test fun stopAndRestartRejectOldListenerAndDoNotLeakRegistrations() {
        grant(); var count = 0; val source = RealLocationSource(app) {}
        try {
            source.start { count++ }; val old = shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).single()
            source.stop(); old.onLocationChanged(location(LocationManager.GPS_PROVIDER)); assertEquals(0, count)
            source.start { count++ }; old.onLocationChanged(location(LocationManager.GPS_PROVIDER)); assertEquals(0, count)
            deliver(location(LocationManager.GPS_PROVIDER)); assertEquals(1, count)
            assertEquals(1, shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        } finally { source.stop() }
    }
    @Test fun unknownProvidersMissingAccuracyAndMissingMonotonicTimeCannotBecomePhysicalFixes() {
        grant(); val fixes = mutableListOf<Fix>(); val source = RealLocationSource(app) {}
        try {
            source.start(fixes::add)
            val callback = shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).single()
            callback.onLocationChanged(location("ip")); assertTrue(fixes.isEmpty())
            callback.onLocationChanged(location(LocationManager.GPS_PROVIDER).apply { removeAccuracy() }); assertTrue(fixes.isEmpty())
            callback.onLocationChanged(location(LocationManager.GPS_PROVIDER).apply { elapsedRealtimeNanos = 0 }); assertTrue(fixes.isEmpty())
        } finally { source.stop() }
    }
    @Test fun foreignGeocoderResultCannotChangePhysicalCoordinateOrChapter() {
        val fix = Fix(30.0, 104.0, System.currentTimeMillis(), accuracy = 300.0, source = FixSource.NETWORK)
        val foreign = Address(Locale.CHINA).apply {
            latitude = 37.77; longitude = -122.42; locality = "San Francisco"; subAdminArea = "US"
        }
        assertNull(coordinateArea(foreign, fix)); assertEquals(30.0, fix.latitude, 0.0)
        val local = Address(Locale.CHINA).apply {
            latitude = 30.0; longitude = 104.0; locality = "成都市"; subAdminArea = "新都区"; subLocality = "新都街道"
        }
        assertEquals("新都街道", coordinateArea(local, fix)!!.chapter)
        assertEquals("", coordinateArea(local, fix.copy(accuracy = 1000.0))!!.chapter)
        assertEquals("", locationArea(coordinateArea(local, fix), fix.copy(source = FixSource.LAST_KNOWN))!!.chapter)
        assertNull(coordinateArea(Address(Locale.CHINA), fix))
    }
}
