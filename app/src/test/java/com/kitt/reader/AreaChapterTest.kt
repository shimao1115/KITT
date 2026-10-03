package com.kitt.reader

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AreaChapterTest {
    @Test fun normalizeAndDistinguishChapterFromCounty() {
        assertEquals(AreaIdentity("德阳市", "广汉市", "雒城街道"), AreaIdentity.normalize(" 德阳市 ", "广汉市", " 雒城街道"))
        assertEquals("广汉市", AreaIdentity.normalize(null, "广汉市", "广汉市")!!.label)
        assertNull(AreaIdentity.normalize(" ", null, ""))
        assertNotEquals(AreaIdentity("德阳市", "广汉市", "雒城街道").key, AreaIdentity("德阳市", "广汉市", "三星堆镇").key)
    }
    @Test fun cacheRefreshesOnChapterChangeAndReusesVisitedCard() {
        var generated = 0
        val cache = AreaCards { generated++; chapterCandidates(it) }
        val a = AreaIdentity("德阳市", "广汉市", "雒城街道")
        val b = a.copy(chapter = "三星堆镇")
        cache.accept(a); val first = cache.active
        repeat(100) { cache.accept(a) }; cache.accept(b); cache.accept(a)
        assertSame(first, cache.active); assertEquals(2, generated); assertEquals(3, cache.transitions)
        cache.accept(null); assertNull(cache.active)
        cache.clear(); assertEquals(0, cache.size)
    }
    @Test fun geocodingThrottlesPendingMovementStalenessAndFailure() {
        val throttle = GeocodeThrottle(); val a = Fix(30.0, 104.0, 0)
        assertTrue(throttle.begin(a, 1000000)); assertFalse(throttle.begin(a.copy(latitude = 31.0), 1200000))
        throttle.complete()
        assertFalse(throttle.begin(a.copy(latitude = 31.0), 1059999))
        assertTrue(throttle.begin(a.copy(latitude = 31.0), 1060000)); throttle.complete()
        assertFalse(throttle.begin(a.copy(latitude = 31.0), 1200000))
        assertTrue(throttle.begin(a.copy(latitude = 31.0), 1360000))
    }
    @Test fun chapterExposesNeutralLocalDossierWithoutForcingNarration() = runTest {
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val point = fixture.points.first { it.administrative?.district == "广汉市" }
        val card = chapterCandidates(point.administrative!!)
        val sanxingdui = card.candidates.filter { "三星堆" in it.title }
        assertTrue(sanxingdui.isNotEmpty())
        assertTrue(sanxingdui.all { it.family == TopicFamily.HERITAGE })
        assertEquals(5, sanxingdui.maxOf { it.salience })
        assertFalse(card.text().contains("narration"))
        val source = SimulatedLocationSource(fixture, backgroundScope, { 1000000 }, 100.0, 16.0)
        assertTrue((0L..260000L step 1000).map { source.sample(it, 1000000 + it).administrative?.district }.contains("广汉市"))
    }
    @Test fun chapterEntryWakesDirectorBelowOrdinaryCadenceAndSilentStaysLegal() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val pipeline = ContextPipeline(); val seen = mutableListOf<String>()
        val loop = DirectorLoop(journey, pipeline, this, { time }, { DirectorProvider { request ->
            seen += if ("三星堆镇" in request.contextCard) "new-chapter" else "old-chapter"
            DirectorResult(Action.SILENT).json()
        } })
        loop.location(Fix(30.0, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "雒城街道"))); runCurrent()
        assertEquals(listOf("old-chapter"), seen)
        time += 60000
        // About 330 m travelled: far below the ordinary cadence gate, but a new street chapter.
        loop.location(Fix(30.003, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "三星堆镇"))); runCurrent()
        assertEquals(listOf("old-chapter", "new-chapter"), seen); assertTrue(voice.speech.isEmpty())
        assertEquals(2, pipeline.areas.transitions)
        assertTrue(pipeline.card(journey, time).contains("三星堆 / 古蜀文明"))
    }
    @Test fun quietAndCooldownSwallowChapterWakeUpWithoutLeavingAQueue() = runTest {
        var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start()
        val pipeline = ContextPipeline(); var calls = 0
        val loop = DirectorLoop(journey, pipeline, this, { time }, { DirectorProvider { calls++; DirectorResult(Action.SILENT).json() } })
        loop.location(Fix(30.0, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "雒城街道"))); runCurrent()
        journey.quiet(); time += 60000
        loop.location(Fix(30.003, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "三星堆镇"))); runCurrent()
        assertEquals(1, calls)
        journey.resume(); time += 11000
        loop.location(Fix(30.006, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "三星堆镇"))); runCurrent()
        assertEquals(1, calls) // A suppressed chapter wake-up is dropped, never replayed later.
        time += 45000
        loop.location(Fix(30.02, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "三星堆镇"))); runCurrent()
        assertEquals(2, calls); loop.reset()
    }
    @Test fun familyHistoryIsBoundedAndUnknownFamilyFailsValidation() {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        repeat(12) {
            journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "题$it", "正文", topicFamily = TopicFamily.HISTORY).json()); voice.finish(); time += 1000
        }
        assertEquals(8, journey.recentFamilies.size)
        assertTrue(runCatching { DirectorContract.parse(DirectorResult(Action.SILENT).json().replace("\"topic_family\":\"\"", "\"topic_family\":\"INVALID\"")) }.isFailure)
        val legacy = DirectorResult(Action.SILENT).json().replace(",\"topic_family\":\"\"", "").replace(",\"landmark_id\":\"\"", "")
        assertEquals(Action.SILENT, DirectorContract.parse(legacy).action)
        journey.end(); assertTrue(journey.recentFamilies.isEmpty())
    }
    @Test fun unresolvedAreaKeepsGpsAndBoundedContextAvailable() {
        val journey = Journey({ 1000000 }, TestVoice()); journey.start(); journey.location(Fix(30.0, 104.0, 1000000))
        val context = ContextPipeline(); context.accept(journey.fix!!)
        assertTrue(journey.shouldCheck()); assertTrue(context.card(journey, 1000000).contains("30.0000")); assertNull(context.areas.active)
        context.reset(); assertEquals(0, context.areas.size)
    }
}
