package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

/** Candidate-source safety even when the device experiment leaves it disabled in production. */
class FusedLocationTest {
    private fun fix(t: Long, source: FixSource = FixSource.FUSED, accuracy: Double = 25.0, lat: Double = 30.0) =
        Fix(lat, 104.0, t, accuracy = accuracy, source = source, elapsedRealtimeMs = t)
    private fun offer(s: PhysicalLocationSelector, f: Fix, now: Long = f.timeMs, cached: Boolean = false) = s.offer(f, now, now, cached)
    private fun select(s: PhysicalLocationSelector, now: Long) = s.select(now, now)
    @Test fun freshFusedCanFillAnActualGapAndHealthyGpsRecovers() {
        val s = PhysicalLocationSelector()
        offer(s, fix(100_000, FixSource.GPS, 8.0)); select(s, 100_000)
        offer(s, fix(116_000)); assertEquals(FixSource.FUSED, select(s, 116_000)!!.source)
        offer(s, fix(118_000, FixSource.GPS, 8.0)); assertEquals(FixSource.GPS, select(s, 118_000)!!.source)
    }
    @Test fun fusedUsesThirtySecondWindowThenOriginalClockBridgeThenUnknown() {
        val s = PhysicalLocationSelector(); val f = fix(100_000)
        offer(s, f); assertEquals(FixSource.FUSED, select(s, 130_000)!!.source)
        val bridge = select(s, 130_001)!!
        assertEquals(FixSource.LAST_KNOWN, bridge.source); assertEquals(FixSource.FUSED, bridge.originSource)
        assertEquals(f.elapsedRealtimeMs, bridge.elapsedRealtimeMs); assertFalse(bridge.precise(130_001, 130_001))
        assertNotNull(select(s, 160_000)); assertNull(select(s, 160_001))
    }
    @Test fun fusedMirrorsNeverRefreshOriginalMeasurementOrReplayOldCoordinates() {
        val s = PhysicalLocationSelector(); val gps = fix(100_000, FixSource.GPS, 8.0)
        offer(s, gps); select(s, 100_000)
        assertTrue(offer(s, gps.copy(source = FixSource.FUSED), 110_000))
        assertEquals(FixSource.GPS, select(s, 110_000)!!.source)
        assertFalse(offer(s, gps.copy(source = FixSource.FUSED), 112_000))
        offer(s, fix(120_000, FixSource.NETWORK, 250.0)); assertEquals(FixSource.NETWORK, select(s, 120_000)!!.source)
        assertTrue(offer(s, fix(119_000), 121_000)); assertEquals(FixSource.NETWORK, select(s, 121_000)!!.source)
        assertNull(select(s, 180_001))
    }
    @Test fun fusedJumpTooCoarseStaleAndMalformedReasonsAreDistinct() {
        val reasons = mutableListOf<String>(); val s = PhysicalLocationSelector(reasons::add)
        offer(s, fix(100_000, FixSource.GPS, 8.0)); select(s, 100_000)
        val cases = listOf(fix(102_000, lat = 35.0) to "impossible_jump", fix(102_000, accuracy = 1501.0) to "accuracy_limit",
            fix(39_999) to "expired", fix(102_001) to "future_measurement", fix(102_000, lat = Double.NaN) to "invalid_measurement",
            fix(102_000, accuracy = 0.0) to "nonpositive_accuracy", fix(102_000, FixSource.SIMULATED) to "unsupported_source")
        cases.forEach { (f, reason) -> assertFalse(offer(s, f, 102_000)); assertEquals("fix rejected reason=$reason", reasons.last()) }
    }
    @Test fun cachedFusedCannotBecomeLiveOrPreciselyArrived() {
        val s = PhysicalLocationSelector(); offer(s, fix(100_000), 100_000, cached = true)
        val bridge = select(s, 100_000)!!
        assertEquals(FixSource.LAST_KNOWN, bridge.source); assertFalse(bridge.precise(100_000, 100_000))
    }
    @Test fun coarseFusedAndItsBridgeAreHonestlyDisplayed() {
        val f = fix(100_000, accuracy = 500.0)
        assertFalse(f.precise(100_000, 100_000))
        assertTrue(positionLabel(f, 102_000, 102_000).contains("FUSED · ±500m · 2秒 · 粗略"))
        val s = PhysicalLocationSelector(); offer(s, f)
        assertTrue(positionLabel(select(s, 131_000), 131_000, 131_000).contains("沿用 FUSED"))
        assertTrue(positionLabel(f, 160_001, 160_001).contains("未知"))
    }
    @Test fun fusedNeverClaimsGpsAltitude() {
        val f = fix(100_000, accuracy = 500.0).copy(elapsedRealtimeMs = null)
        val context = ContextPipeline(); val journey = Journey({ 100_000L }, TestVoice()); journey.start(); journey.location(f)
        context.accept(f)
        assertTrue(context.card(journey, 100_000).contains("来源：FUSED"))
        assertTrue(context.card(journey, 100_000).contains("不得断言已到地标"))
    }
}
