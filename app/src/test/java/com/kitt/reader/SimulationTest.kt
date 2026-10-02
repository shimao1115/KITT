package com.kitt.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SimulationTest {
    fun fixture() = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
    @Test fun continuous80KmhRouteCompletesAndCanAccelerate() = runTest {
        val f = fixture(); val source = SimulatedLocationSource(f, backgroundScope, { 1000000L })
        assertEquals(80.0, f.speedKmh, 0.01); assertTrue(source.totalMeters > 100000)
        val start = source.sample(0, 1000000L); val next = source.sample(1000, 1001000L)
        assertEquals(80.0 / 3.6, start.distanceTo(next), 0.1)
        val finish = source.sample(10000000L, 11000000L)
        assertEquals(31.47, finish.latitude, 0.00001); assertEquals(104.74, finish.longitude, 0.00001)
        assertEquals(0.0, finish.speedKmh, 0.01)
        val fast = SimulatedLocationSource(f, backgroundScope, { 0L }, acceleration = 60.0)
        assertEquals(source.sample(60000, 1).latitude, fast.sample(1000, 1).latitude, 0.00001)
    }
    @Test fun simulationUsesSameContextAndNoImaginaryAltitude() = runTest {
        var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start()
        val pipeline = ContextPipeline(); val source = SimulatedLocationSource(fixture(), backgroundScope, { time })
        repeat(100) {
            time += 10000; val fix = source.sample(it * 10000L, time); journey.location(fix); pipeline.accept(fix)
        }
        val card = pipeline.card(journey, time)
        assertTrue(card.contains("非导航级")); assertTrue(card.contains("海拔趋势未知")); assertTrue(card.length < 2000)
    }
}
