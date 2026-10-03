package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BatchSimulationTest {
    @Test fun geocoderNormalizesCommonChineseFieldLayoutsWithoutInventingTown() {
        assertEquals(AreaIdentity("成都市", "新都区", "新都街道"), AreaIdentity.geocoderFields("成都市", "新都区", "新都街道"))
        assertEquals(AreaIdentity("成都市", "新都区", ""), AreaIdentity.geocoderFields("新都区", "成都市", null))
        assertEquals(AreaIdentity("德阳市", "广汉市", "雒城街道"), AreaIdentity.geocoderFields("广汉市", "德阳市", "雒城街道"))
        assertEquals(AreaIdentity("成都市", "新都区", ""), AreaIdentity.geocoderFields("成都市", null, "新都区"))
        assertNull(AreaIdentity.geocoderFields(null, null, null))
    }
    @Test fun newRouteHasDetailedChaptersAndNeutralGroundedSubjects() = runTest {
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        assertEquals(29, fixture.points.size); assertEquals("新都区", fixture.points.first().administrative!!.district)
        assertEquals(AreaIdentity("绵阳市", "安州区", "雎水镇"), fixture.points.last().administrative)
        assertTrue(fixture.points.map { it.administrative!!.key }.distinct().size >= 12)
        val candidates = chapterCandidates(fixture.points.last().administrative!!).candidates
        assertTrue(candidates.any { it.title.contains("太平桥") }); assertTrue(candidates.any { it.family == TopicFamily.PEOPLE })
        val families = candidates.map { it.family }.toSet()
        assertTrue(families.containsAll(listOf(TopicFamily.HISTORY, TopicFamily.ECONOMY, TopicFamily.TRANSPORT,
            TopicFamily.CULTURAL_SITE, TopicFamily.HERITAGE, TopicFamily.EVERYDAY_LIFE, TopicFamily.OTHER)))
        assertTrue(candidates.none { ThesisInTitle.containsMatchIn(it.title) })
    }
    @Test fun complete100Kmh16xDossierPipelineSpeaksThroughChaptersWithoutStaleReplay() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val pipeline = ContextPipeline(); pipeline.routeHint = "新都→雎水（测试截图提示）"
        val picked = mutableListOf<NarrativeCandidate>(); val seen = mutableSetOf<String>(); var sanxingduiEligible = false
        val diagnostics = mutableListOf<String>()
        val loop = DirectorLoop(journey, pipeline, backgroundScope, clock, { DirectorProvider { request ->
            assertNull(request.image); assertTrue(request.contextCard.contains("GPS 优先")); delay(5000)
            val card = pipeline.areas.active!!
            if (card.area.district == "广汉市") { assertTrue(request.contextCard.contains("三星堆")); sanxingduiEligible = true }
            // Deterministic stand-in for the Director: free choice over the whole shelf, same family allowed again.
            val choice = card.candidates.firstOrNull { it.title !in seen }
            if (choice == null) DirectorResult(Action.SILENT).json()
            else {
                seen += choice.title; picked += choice
                DirectorResult(Action.SPEAK_NOW, choice.title, "测试内容：${choice.title}。这是确定性回归内容，不代表真实 AI 内容质量。", topicFamily = choice.family).json()
            }
        } }, diagnostic = { diagnostics += it })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val source = SimulatedLocationSource(fixture, backgroundScope, clock, 100.0, 16.0,
            paused = { loop.pending || journey.speaking || journey.listening })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location)
        var speakingAt = 0L
        repeat(1000) {
            advanceTimeBy(1000); runCurrent()
            if (journey.speaking) {
                if (speakingAt == 0L) speakingAt = clock()
                if (clock() - speakingAt >= 20000) { voice.finish(); speakingAt = 0 }
            }
        }
        assertTrue(source.completed); assertTrue(sanxingduiEligible)
        assertTrue(picked.any { it.title.contains("三星堆") })
        assertTrue(picked.map { it.family }.distinct().size >= 4)
        assertTrue(picked.size > picked.map { it.family }.distinct().size) // No family blacklist: repeats are allowed.
        assertEquals(13, pipeline.areas.size); assertEquals(13, pipeline.areas.transitions)
        assertTrue(picked.size >= pipeline.areas.transitions) // Every chapter found something to say here.
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE]); assertNull(loop.counters.automatic[DeliveryOutcome.CANCELLED])
        assertTrue(loop.counters.dispatched in 13..26)
        val chapters = diagnostics.count { "area chapter=" in it }
        assertEquals(13, chapters)
        val report = "Editorial freedom refined route: PASS (deterministic Provider/fake Voice; real Context/Journey/Director)\n" +
            "${fixture.name}\n29 points; distance=${source.totalMeters.toInt()}m; speed=100km/h; acceleration=16x; latency=5s; TTS=20s\n" +
            "chapters=${pipeline.areas.transitions}; cached=${pipeline.areas.size}; chapterEntryOpportunities=$chapters; Sanxingdui eligible=true\n" +
            "subjects=${picked.size}; families=${picked.map { it.family }.distinct()}; ${loop.counters.summary()}\n" +
            "Selected subjects=${picked.joinToString { it.title }}\nReal ChatGPT variety and physical phone gates: DEFERRED TO COMBINED PHONE ACCEPTANCE."
        File("build/acceptance").mkdirs(); File("build/acceptance/batch-simulation.txt").writeText(report); println(report)
        journey.end(); loop.reset(); source.stop(); assertEquals(0, pipeline.areas.size); assertEquals("", pipeline.routeHint)
    }
    @Test fun newChaptersWithFailedProviderProduceSilenceAndBoundedOpportunities() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val pipeline = ContextPipeline()
        val loop = DirectorLoop(journey, pipeline, backgroundScope, clock, { DirectorProvider { delay(5000); error("offline") } })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val source = SimulatedLocationSource(fixture, backgroundScope, clock, 100.0, 16.0, paused = { loop.pending })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location)
        repeat(450) { advanceTimeBy(1000); runCurrent() }
        assertTrue(source.completed); assertTrue(voice.speech.isEmpty()); assertTrue(loop.counters.dispatched in 8..18)
        assertEquals(loop.counters.dispatched, loop.counters.automatic[DeliveryOutcome.FAILURE]); source.stop(); loop.cancel()
    }
}
