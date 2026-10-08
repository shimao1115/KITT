package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFallbackTest {
    private fun fix(time: Long, source: FixSource = FixSource.GPS, accuracy: Double = 8.0,
        lat: Double = 30.0, speed: Double = 80.0) = Fix(lat, 104.0, time, speed,
        accuracy = accuracy, source = source)
    private fun offer(selector: PhysicalLocationSelector, fix: Fix, now: Long = fix.timeMs, cached: Boolean = false) =
        selector.offer(fix, now, now, cached)
    private fun select(selector: PhysicalLocationSelector, now: Long) = selector.select(now, now)

    @Test fun gpsFallbackAndRecoveryWithoutRestart() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000)); assertEquals(FixSource.GPS, select(selector, 100_000)!!.source)
        offer(selector, fix(102_000, FixSource.NETWORK, 300.0, 30.001))
        assertEquals(FixSource.GPS, select(selector, 102_000)!!.source)
        offer(selector, fix(116_000, FixSource.NETWORK, 300.0, 30.003))
        val fallback = select(selector, 116_000)!!
        assertEquals(FixSource.NETWORK, fallback.source); assertEquals(300.0, fallback.accuracy, 0.0)
        assertEquals(0L, fallback.ageMs(116_000)); assertFalse(fallback.precise(116_000))
        offer(selector, fix(118_000, lat = 30.004))
        assertEquals(FixSource.GPS, select(selector, 118_000)!!.source)
    }
    @Test fun weakGpsDoesNotBeatBetterNetworkBecauseOfProviderName() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000, accuracy = 700.0)); select(selector, 100_000)
        offer(selector, fix(111_000, FixSource.NETWORK, 100.0))
        assertEquals(FixSource.NETWORK, select(selector, 111_000)!!.source)
    }
    @Test fun newerAccurateNetworkCanBeatAgingGps() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000)); select(selector, 100_000)
        offer(selector, fix(112_000, FixSource.NETWORK, 100.0))
        assertEquals(FixSource.NETWORK, select(selector, 112_000)!!.source)
    }
    @Test fun smallQualityDifferencesDoNotOscillate() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000, accuracy = 100.0)); select(selector, 100_000)
        offer(selector, fix(102_000, FixSource.NETWORK, 90.0))
        assertEquals(FixSource.GPS, select(selector, 102_000)!!.source)
        offer(selector, fix(111_000, accuracy = 100.0)); select(selector, 111_000)
        offer(selector, fix(111_000, FixSource.NETWORK, 90.0))
        assertEquals(FixSource.GPS, select(selector, 111_000)!!.source)
    }
    @Test fun disablingGpsLeavesNetworkAndRecoveryPromotesGps() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000)); select(selector, 100_000)
        offer(selector, fix(102_000, FixSource.NETWORK, 300.0))
        selector.disable(FixSource.GPS)
        assertEquals(FixSource.NETWORK, select(selector, 102_000)!!.source)
        offer(selector, fix(103_000)); assertEquals(FixSource.GPS, select(selector, 103_000)!!.source)
    }
    @Test fun cachedCoordinatesNeverBecomeFreshGpsAndExpireWithoutRenewingTimestamp() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000), 120_000, cached = true)
        val bridge = select(selector, 120_000)!!
        assertEquals(FixSource.LAST_KNOWN, bridge.source); assertEquals(FixSource.GPS, bridge.originSource)
        assertEquals(100_000L, bridge.timeMs); assertEquals(20_000L, bridge.ageMs(120_000))
        assertFalse(bridge.precise(120_000))
        assertTrue(select(selector, 130_000)!!.accuracy > bridge.accuracy)
        assertNull(select(selector, 160_001))
    }
    @Test fun bothLiveSourcesExpireIntoBridgeThenUnknown() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000, FixSource.NETWORK, 500.0)); select(selector, 100_000)
        assertEquals(FixSource.LAST_KNOWN, select(selector, 130_001)!!.source)
        assertNull(select(selector, 160_001))
    }
    @Test fun impossibleHandoffAndForeignCoordinatesAreRejectedAndDoNotPoisonRecovery() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000)); select(selector, 100_000)
        assertFalse(offer(selector, fix(102_000, FixSource.NETWORK, 500.0, 40.0)))
        assertFalse(offer(selector, fix(102_000).copy(latitude = 37.77, longitude = -122.42)))
        assertTrue(offer(selector, fix(116_000, FixSource.NETWORK, 300.0, 30.003)))
        assertEquals(FixSource.NETWORK, select(selector, 116_000)!!.source)
        assertTrue(offer(selector, fix(118_000, lat = 30.004)))
        assertEquals(FixSource.GPS, select(selector, 118_000)!!.source)
    }
    @Test fun movingCarIsNotSmoothedOrFrozen() {
        val selector = PhysicalLocationSelector()
        repeat(60) { second ->
            val current = fix(100_000L + second * 2000, lat = 30.0 + second * 0.0004)
            assertTrue(offer(selector, current)); assertEquals(current, select(selector, current.timeMs))
        }
    }
    @Test fun accuracyCirclesAllowPlausibleHandoffNoise() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000)); select(selector, 100_000)
        assertTrue(offer(selector, fix(102_000, FixSource.NETWORK, 500.0, 30.004)))
        selector.disable(FixSource.GPS)
        assertEquals(FixSource.NETWORK, select(selector, 102_000)!!.source)
    }
    @Test fun invalidFutureOutOfOrderAndTooCoarseMeasurementsAreRejected() {
        val selector = PhysicalLocationSelector()
        listOf(fix(100_000, accuracy = 0.0), fix(100_000, accuracy = 1501.0),
            fix(100_000, lat = Double.NaN), fix(100_000, speed = 351.0), fix(100_001), fix(39_999),
            fix(100_000, FixSource.LAST_KNOWN), fix(100_000, FixSource.SIMULATED)).forEach {
            assertFalse(offer(selector, it, 100_000))
        }
        assertTrue(offer(selector, fix(100_000)))
        assertFalse(offer(selector, fix(99_999), 100_000)); assertFalse(offer(selector, fix(100_000)))
        assertFalse(offer(selector, fix(84_999, FixSource.NETWORK, 300.0), 100_000))
    }
    @Test fun asynchronousSecondaryFixIsStoredWithoutReplayingAnOlderPosition() {
        val selector = PhysicalLocationSelector()
        offer(selector, fix(100_000, FixSource.NETWORK, 300.0)); select(selector, 100_000)
        assertTrue(offer(selector, fix(99_000), 101_000))
        assertEquals(FixSource.NETWORK, select(selector, 101_000)!!.source)
        assertTrue(offer(selector, fix(102_000)))
        assertEquals(FixSource.GPS, select(selector, 102_000)!!.source)
    }
    @Test fun monotonicAgeAndOrderingSurviveWallClockChanges() {
        val selector = PhysicalLocationSelector()
        val first = fix(100_000).copy(elapsedRealtimeMs = 10_000)
        assertTrue(selector.offer(first, 200_000, 10_000))
        assertEquals(FixSource.GPS, selector.select(500_000, 10_000)!!.source)
        val second = fix(50_000, lat = 30.0004).copy(elapsedRealtimeMs = 12_000)
        assertTrue(selector.offer(second, 50_000, 12_000))
        assertEquals(second, selector.select(50_000, 12_000))
        assertEquals(0L, second.ageMs(900_000, 12_000)); assertNull(selector.select(50_000, 72_001))
    }
    @Test fun coarseNetworkRetainsChapterContextButClearsLandmarkAndPrecisePrepare() {
        var time = 100_000L
        val journey = Journey({ time }, TestVoice()); journey.start()
        val node = Landmark("site", "测试地标", 30.0, 104.0, 500.0, LandmarkKind.MUSEUM, TopicFamily.HERITAGE, "fixture")
        val context = ContextPipeline(listOf(node))
        val area = AreaIdentity("成都市", "新都区", "新都街道")
        val gps = fix(time).copy(administrative = area)
        journey.location(gps); context.accept(gps); assertTrue(context.proximity.opportunity)
        journey.deliver(journey.ticket(false), DirectorResult(Action.PREPARE, "测试地标", prepareHint = "接近再确认").json())
        assertNotNull(journey.prepared)
        time += 2000
        val network = fix(time, FixSource.NETWORK, 500.0).copy(administrative = area)
        journey.location(network); context.accept(network)
        assertNull(journey.prepared); assertTrue(context.proximity.ids.isEmpty())
        assertEquals(area, context.areas.active!!.area); assertTrue(journey.running)
        val card = context.card(journey, time)
        assertTrue(card.contains("来源：NETWORK")); assertTrue(card.contains("精度：500 m"))
        assertTrue(card.contains("不得断言已到地标")); assertFalse(card.contains("landmark_id=site"))
    }
    @Test fun coarseHandoffKillsLatePreciseLandmarkSpeech() = runTest {
        var time = 100_000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val node = Landmark("site", "测试地标", 30.0, 104.0, 500.0, LandmarkKind.MUSEUM, TopicFamily.HERITAGE, "fixture")
        val context = ContextPipeline(listOf(node))
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
            delay(5000); DirectorResult(Action.SPEAK_NOW, "测试地标", "到了地标", landmarkId = "site").json()
        } })
        loop.location(fix(time)); runCurrent()
        time += 2000; loop.location(fix(time, FixSource.NETWORK, 500.0)); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertTrue(voice.speech.isEmpty()); assertEquals(1, loop.counters.automatic[DeliveryOutcome.STALE]); loop.reset()
    }
    @Test fun stalePreciseFixCannotDispatchLandmarkOrDeliverLateArrival() = runTest {
        var time = 100_000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val node = Landmark("site", "测试地标", 30.0, 104.0, 500.0, LandmarkKind.MUSEUM, TopicFamily.HERITAGE, "fixture")
        val context = ContextPipeline(listOf(node))
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
            delay(20_000); DirectorResult(Action.SPEAK_NOW, "测试地标", "到了地标", landmarkId = "site").json()
        } })
        loop.location(fix(time)); runCurrent()
        time += 20_000; advanceTimeBy(20_000); runCurrent()
        assertTrue(voice.speech.isEmpty()); assertTrue(context.proximity.ids.isEmpty())
        time += 41_000; loop.check(); assertEquals("stale_location", journey.checkDelayReason())
        assertTrue(context.card(journey, time).contains("暂无可靠位置")); loop.reset()
    }
    @Test fun networkChapterStillResearchesAndSpeaksThroughExistingLoop() = runTest {
        val time = 100_000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val area = AreaIdentity("测试市", "测试区", "测试镇")
        val context = ContextPipeline(); var received = ""
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
            received = it.contextCard; DirectorResult(Action.SPEAK_NOW, "一般机制", "测试粗略章节叙述").json()
        } })
        loop.location(fix(time, FixSource.NETWORK, 300.0).copy(administrative = area)); runCurrent()
        assertEquals(area, context.areas.active!!.area); assertTrue(received.contains("NETWORK"))
        assertEquals(1, voice.speech.size); assertTrue(journey.running); loop.reset()
    }
}
