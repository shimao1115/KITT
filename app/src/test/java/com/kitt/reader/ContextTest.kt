package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

class ContextTest {
    @Test fun sourceIndependentPipelineAndAltitudeSummary() {
        val time = 2000000L; val j = Journey({ time }, TestVoice()); j.start()
        val fixes = listOf(Fix(30.67, 104.06, time - 10000, altitude = 500.0), Fix(30.68, 104.07, time, altitude = 510.0))
        fun runSource(source: LocationSource): String {
            val pipeline = ContextPipeline()
            source.start { j.location(it); pipeline.accept(it) }
            return pipeline.card(j, time)
        }
        val source = object : LocationSource {
            override fun start(onFix: (Fix) -> Unit) { fixes.forEach(onFix) }
            override fun stop() {}
        }
        val a = runSource(source); assertTrue(a.contains("海拔变化约 10 m"))
        assertTrue(a.contains("无地图增强")); assertTrue(a.length < 2000)
        assertFalse(Fix(Double.NaN, 104.0, time).valid())
        assertTrue(Fix(30.0, 104.0, time, accuracy = 999.0, source = FixSource.NETWORK).valid())
        assertFalse(Fix(30.0, 104.0, time, accuracy = 999.0, source = FixSource.NETWORK).precise(time))
        assertFalse(Fix(30.0, 104.0, time, accuracy = 10001.0).valid())
    }
}
