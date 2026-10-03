package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DrivingAreaResolutionTest {
    @Test fun shortChaptersAreNotSystematicallySwallowedAt80And100Kmh() {
        val report = StringBuilder("Production GeocodeThrottle with synthetic 700m chapters, fixes every 2s; not actual platform geocoding.\n")
        for (speed in listOf(80.0, 100.0)) for (reportedSpeed in listOf(speed, 0.0)) {
            val throttle = GeocodeThrottle(); val seen = mutableSetOf<Int>(); val calls = mutableListOf<Long>()
            for (seconds in 0..(4200 / (speed / 3.6)).toInt() step 2) {
                val meters = seconds * speed / 3.6
                val fix = Fix(30 + meters / 111195, 104.0, 1000000L + seconds * 1000, reportedSpeed)
                if (throttle.begin(fix, fix.timeMs)) {
                    seen += (meters / 700).toInt(); calls += fix.timeMs; throttle.complete()
                }
            }
            assertTrue("speed=$speed reported=$reportedSpeed chapters=$seen", seen.containsAll((0..5).toList()))
            assertTrue(calls.zipWithNext().all { (a, b) -> b - a >= 15000 })
            report.appendLine("speed=$speed reported=$reportedSpeed chapters=$seen calls=${calls.size}; minInterval=${calls.zipWithNext().minOf { it.second - it.first }}ms")
        }
        File("build/acceptance").mkdirs(); File("build/acceptance/driving-area-resolution.txt").writeText(report.toString())
    }
    @Test fun cachedTownExpiresByDistanceAndAgeAndLateLookupCannotLabelNewGps() {
        val anchor = Fix(30.0, 104.0, 1000000, 100.0)
        assertTrue(areaCacheValid(anchor, anchor.copy(timeMs = 1015000, latitude = 30.003), 1015000))
        assertFalse(areaCacheValid(anchor, anchor.copy(timeMs = 1020000, latitude = 30.005), 1020000))
        assertFalse(areaCacheValid(anchor, anchor.copy(timeMs = 1030001), 1030001))
        assertFalse(areaCacheValid(anchor, anchor.copy(timeMs = 1040000, latitude = 30.02), 1040000))
    }
    @Test fun stationaryFailuresAreBoundedAndPendingLookupsNeverMultiply() {
        val throttle = GeocodeThrottle(); var calls = 0
        for (second in 0..600 step 2) {
            val fix = Fix(30.0, 104.0, 1000000L + second * 1000)
            if (throttle.begin(fix, fix.timeMs)) {
                calls++; assertFalse(throttle.begin(fix, fix.timeMs + 1000)); throttle.complete()
            }
        }
        assertEquals(6, calls)
    }
    @Test fun provinceIsPreservedWithoutInventingMissingTown() {
        val area = AreaIdentity.geocoderFields("成都市", "新都区", null, "四川省")!!
        assertEquals("四川省 / 成都市 / 新都区", area.fullName); assertEquals("", area.chapter)
        assertNotEquals(area.key, area.copy(province = "其他省").key)
    }
}
