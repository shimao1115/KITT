package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DossierSimulationTest {
    @Test fun existingRouteResearchesEachChapterAndNarratesOnlyDeliveredEvidence() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start(); val context = ContextPipeline()
        val searched = mutableListOf<AreaIdentity>(); val narrated = mutableListOf<String>(); val evidence = mutableListOf<String>()
        val research = LocalResearchProvider { area, at -> searched += area; delay(5000); testDossier(area, at) }
        lateinit var loop: DirectorLoop
        loop = DirectorLoop(journey, context, backgroundScope, clock, { DirectorProvider { request ->
            val active = context.areas.active!!.area
            val dossier = loop.research.dossier(active) ?: return@DirectorProvider DirectorResult(Action.SILENT).json()
            assertTrue(dossier.area.fullName in request.contextCard)
            assertTrue(dossier.sources.single().url in request.contextCard)
            assertTrue(dossier.facts.single().summary in request.contextCard)
            delay(4000)
            val fact = dossier.facts.single()
            if (fact.title in narrated) DirectorResult(Action.SILENT, memoryUpdate = "该事实已讲过").json()
            else { narrated += fact.title; DirectorResult(Action.SPEAK_NOW, fact.title, fact.summary, memoryUpdate = fact.title, topicFamily = fact.family).json() }
        } }, diagnostic = { evidence += it }, researchProvider = { research })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val source = SimulatedLocationSource(fixture, backgroundScope, clock, 100.0, 16.0,
            paused = { loop.simulationPaused })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location)
        var speechAt = 0L
        repeat(1000) {
            advanceTimeBy(1000); runCurrent(); loop.check(); runCurrent()
            if (journey.speaking) {
                if (speechAt == 0L) speechAt = clock()
                if (clock() - speechAt >= 15000) { voice.finish(); speechAt = 0 }
            }
        }
        val chapters = fixture.points.mapNotNull { it.administrative }.distinctBy { it.key }
        val identities = (chapters + chapters.map { it.copy(chapter = "") }).distinctBy { it.key }
        assertTrue(source.completed); assertEquals(13, chapters.size); assertEquals(13, narrated.size)
        assertEquals(identities.map { it.key }.toSet(), searched.map { it.key }.toSet())
        assertEquals(searched.distinct(), searched)
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE]); assertNull(loop.counters.automatic[DeliveryOutcome.FAILURE])
        assertNull(loop.counters.automatic[DeliveryOutcome.CANCELLED])
        val report = "Session Local Dossier route: PASS, deterministic research/director/voice; NOT real search or Xindu discovery acceptance.\n" +
            "29 points; ${source.totalMeters.toInt()}m; 100km/h; 16x; chapter research latency=5s, director=4s, TTS=15s\n" +
            "chapters=${chapters.size}; unique research=${searched.size}; narrations=${narrated.size}; ${loop.counters.summary()}\n" + evidence.joinToString("\n")
        File("build/acceptance").mkdirs(); File("build/acceptance/dossier-simulation.txt").writeText(report)
        journey.end(); loop.reset(); source.stop(); assertEquals(0, loop.research.size)
    }
}
