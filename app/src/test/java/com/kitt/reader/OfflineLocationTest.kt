package com.kitt.reader

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowGeocoder
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineLocationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(LocationManager::class.java)
    private val wallOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private fun setup() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setLocationEnabled(true)
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) shadowOf(manager).setProviderEnabled(p, true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
    }
    private fun deliver(provider: String = LocationManager.GPS_PROVIDER, accuracy: Float = 10f, lat: Double = 30.823) {
        shadowOf(manager).simulateLocation(Location(provider).apply {
            latitude = lat; longitude = 104.16; this.accuracy = accuracy
            time = wallOffset + SystemClock.elapsedRealtime(); elapsedRealtimeNanos = SystemClock.elapsedRealtime() * 1000000
        })
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun realSourceOfflineUsesBundledGeometryAndNeverInvokesGeocoder() {
        setup(); ShadowGeocoder.setIsPresent(false)
        val pack = OfflinePack.load(app)!!; var onlineCalls = 0; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}, { onlineCalls++; error("Geocoder must not run") })
            .offlineLookup({ pack.resolve(it, System.currentTimeMillis()) }, { false })
        try {
            source.start(fixes::add); deliver()
            assertEquals(pack.area, fixes.last().administrative)
            assertEquals(0, onlineCalls); assertNull(source.areaLookupSince)
        } finally { source.stop() }
    }
    @Test fun offlineGpsNetworkSwitchKeepsOnlyFreshCountyAndRejectsCoarse() {
        setup(); val pack = OfflinePack.load(app)!!; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}).offlineLookup({ pack.resolve(it, System.currentTimeMillis()) }, { false })
        try {
            source.start(fixes::add); deliver(); assertEquals(pack.area, fixes.last().administrative)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); deliver(LocationManager.NETWORK_PROVIDER)
            assertEquals(FixSource.NETWORK, fixes.last().source); assertEquals(pack.area, fixes.last().administrative)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); deliver(LocationManager.NETWORK_PROVIDER, 600f)
            assertNull(fixes.last().administrative)
        } finally { source.stop() }
    }
    @Test fun installedCountyWinsOverOnlineGeocoderAndSurvivesNetworkLoss() {
        setup(); var connected = true; var calls = 0; val fixes = mutableListOf<Fix>(); val pack = OfflinePack.load(app)!!
        val source = RealLocationSource(app, {}, { calls++; pack.area.copy(chapter = "旧街道") })
            .offlineLookup({ pack.resolve(it, System.currentTimeMillis()) }, { connected })
        try {
            source.start(fixes::add); deliver()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(pack.area, fixes.last().administrative)
            assertEquals(0, calls)
            connected = false; val previous = calls
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); deliver()
            assertEquals(pack.area, fixes.last().administrative); assertEquals(previous, calls)
        } finally { source.stop() }
    }
    @Test fun staleBridgeCannotReuseOfflineCounty() {
        setup(); val pack = OfflinePack.load(app)!!; val fixes = mutableListOf<Fix>()
        val source = RealLocationSource(app, {}).offlineLookup({ pack.resolve(it, System.currentTimeMillis()) }, { false })
        try {
            source.start(fixes::add); deliver()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            assertTrue(fixes.last().source == FixSource.LAST_KNOWN || fixes.last().ageMs(System.currentTimeMillis()) > 15000)
            assertNull(fixes.last().administrative)
        } finally { source.stop() }
    }
}
