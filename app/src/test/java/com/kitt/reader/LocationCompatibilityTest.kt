package com.kitt.reader

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 31, 34])
class LocationCompatibilityTest {
    @Test fun productionStillRegistersIndependentGpsAndNetworkAndRemovesBoth() {
        val app = RuntimeEnvironment.getApplication(); val manager = app.getSystemService(LocationManager::class.java)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val source = RealLocationSource(app) {}
        try {
            source.start {}
            assertEquals(1, shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
            assertEquals(1, shadowOf(manager).getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).size)
        } finally { source.stop() }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(manager).locationUpdateListeners.isEmpty())
    }
    @Test fun deviceRejectionReasonsDoNotLeakCoordinatesAndPreserveConversion() {
        val reasons = mutableListOf<String>()
        val location = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 30.0; longitude = 104.0; time = 100_000; elapsedRealtimeNanos = 100_000_000_000
        }
        assertNull(deviceFix(location, FixSource.NETWORK, reasons::add)); assertEquals("no_accuracy", reasons.last())
        location.accuracy = 500f; location.elapsedRealtimeNanos = 0
        assertNull(deviceFix(location, FixSource.NETWORK, reasons::add)); assertEquals("no_measurement_clock", reasons.last())
        location.elapsedRealtimeNanos = 100_000_000_000; location.altitude = 999.0
        val fused = deviceFix(location, FixSource.FUSED, reasons::add)!!
        assertEquals(100_000L, fused.elapsedRealtimeMs); assertNull(fused.altitude)
        assertEquals(2, reasons.size)
    }
}
