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
    @Test fun fixtureDiscoversSanxingduiAsCandidateWithoutNarration() = runTest {
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val point = fixture.points.first { it.administrative?.district == "广汉市" }
        val card = chapterCandidates(point.administrative!!)
        val ranked = card.ranked(listOf(TopicFamily.TRANSPORT, TopicFamily.TRANSPORT, TopicFamily.EVERYDAY_LIFE))
        assertTrue(ranked.first().title.contains("三星堆")); assertEquals(TopicFamily.HERITAGE, ranked.first().family)
        assertFalse(card.text(emptyList()).contains("narration"))
        val source = SimulatedLocationSource(fixture, backgroundScope, { 1000000 }, 100.0, 16.0)
        assertTrue((0L..260000L step 1000).map { source.sample(it, 1000000 + it).administrative?.district }.contains("广汉市"))
    }
    @Test fun boundaryRefreshGivesContextButNeverForcesSpeech() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val pipeline = ContextPipeline()
        val loop = DirectorLoop(journey, pipeline, this, { time }, { DirectorProvider { DirectorResult(Action.SILENT).json() } })
        loop.location(Fix(30.0, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "雒城街道"))); runCurrent()
        time += 60000
        loop.location(Fix(30.1, 104.0, time, administrative = AreaIdentity("德阳市", "广汉市", "三星堆镇"))); runCurrent()
        assertEquals(2, pipeline.areas.transitions); assertTrue(voice.speech.isEmpty())
        assertTrue(pipeline.card(journey, time).contains("三星堆"))
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
