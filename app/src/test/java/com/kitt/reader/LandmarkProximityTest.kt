package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LandmarkProximityTest {
    private val area = AreaIdentity("测试市", "同一区", "同一镇")
    private fun node(kind: LandmarkKind = LandmarkKind.RIVER) = Landmark("site", "测试节点", 30.01, 104.0,
        500.0, kind, TopicFamily.GEOGRAPHY, "测试用位置与依据；不代表实际地物")
    private fun fix(lat: Double, time: Long) = Fix(lat, 104.0, time, administrative = area)
    private fun speech(id: String = "site", topic: String = "测试节点") = DirectorResult(Action.SPEAK_NOW,
        topic, "确定性测试旁白", topicFamily = TopicFamily.GEOGRAPHY, landmarkId = id)

    @Test fun everySupportedKindCreatesIndependentOpportunityInsideSameChapterBelowNormalDistance() = runTest {
        for (kind in LandmarkKind.entries) {
            var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start()
            val context = ContextPipeline(listOf(node(kind))); val requests = mutableListOf<DirectorRequest>()
            val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
                requests += it; DirectorResult(Action.SILENT).json()
            } })
            loop.location(fix(30.0, time)); runCurrent(); assertEquals(1, requests.size)
            time += 44000; loop.location(fix(30.008, time)); runCurrent(); assertEquals(1, requests.size)
            time += 1000; loop.location(fix(30.009, time)); runCurrent()
            assertEquals("$kind independently triggers", 2, requests.size)
            assertEquals(1, context.areas.transitions)
            assertTrue(requests.last().contextCard.contains("landmark_id=site"))
            assertTrue(requests.last().contextCard.contains(kind.name)); loop.reset()
        }
    }
    @Test fun silentIsValidAndStationaryNodeDoesNotCreateRetryStorm() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val context = ContextPipeline(listOf(node())); var calls = 0
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider { calls++; DirectorResult(Action.SILENT).json() } })
        loop.location(fix(30.009, time)); runCurrent()
        repeat(700) { time += 1000; loop.location(fix(30.009, time)); runCurrent() }
        assertEquals(1, calls); assertTrue(voice.speech.isEmpty()); assertFalse(context.proximity.opportunity)
    }
    @Test fun quietPassesNodeAndResumeNeverQueuesIt() = runTest {
        var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start()
        val context = ContextPipeline(listOf(node())); val requests = mutableListOf<String>()
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider { requests += it.contextCard; DirectorResult(Action.SILENT).json() } })
        loop.location(fix(30.0, time)); runCurrent(); journey.quiet()
        time += 60000; loop.location(fix(30.009, time)); runCurrent(); assertEquals(1, requests.size)
        time += 60000; loop.location(fix(30.016, time)); runCurrent(); assertTrue(context.proximity.ids.isEmpty())
        journey.resume(); time += 50000; loop.location(fix(30.017, time)); runCurrent()
        assertTrue(requests.drop(1).none { "landmark_id=site" in it })
    }
    @Test fun imageAndListeningUserPriorityHoldAutomaticOpportunity() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val context = ContextPipeline(listOf(node())); var calls = 0
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider { calls++; DirectorResult(Action.SILENT).json() } })
        loop.location(fix(30.0, time)); runCurrent(); journey.beginImageInteraction()
        time += 60000; loop.location(fix(30.009, time)); runCurrent(); assertEquals(1, calls)
        journey.finishImageInteraction(); journey.beginListening {}
        time += 60000; loop.location(fix(30.009, time)); runCurrent(); assertEquals(1, calls)
        voice.answer?.invoke(null); time += 31000; loop.location(fix(30.009, time)); runCurrent(); assertEquals(2, calls)
    }
    @Test fun recedingWithinExisting1500mGuardStillKillsDelayedLandmarkResponse() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val context = ContextPipeline(listOf(node())); var calls = 0
        val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
            if (++calls == 1) DirectorResult(Action.SILENT).json() else { delay(5000); speech().json() }
        } })
        loop.location(fix(30.0, time)); runCurrent()
        time += 60000; loop.location(fix(30.009, time)); runCurrent()
        time += 1000; loop.location(fix(30.014, time)); runCurrent() // 556m traveled, 445m away: still inside radius.
        assertTrue(context.proximity.ids.isEmpty()); advanceTimeBy(5000); runCurrent()
        assertTrue(voice.speech.isEmpty()); assertEquals(1, loop.counters.automatic[DeliveryOutcome.STALE])
    }
    @Test fun entityDedupSurvivesRetitledNarrationButActiveDeepeningIsAllowedAndResetClears() {
        val proximity = LandmarkProximity(listOf(node())); proximity.accept(fix(30.009, 1000000))
        val ids = proximity.ids; assertNull(proximity.guard(speech(), false, ids)); proximity.delivered(speech())
        assertEquals(DeliveryOutcome.SUPPRESSED, proximity.guard(speech(topic = "换个标题"), false, ids))
        assertEquals(DeliveryOutcome.SUPPRESSED, proximity.guard(speech(id = ""), false, ids))
        assertNull(proximity.guard(speech(topic = "再讲一点"), true, ids))
        proximity.accept(fix(30.0, 1060000)); proximity.accept(fix(30.009, 1120000)); assertFalse(proximity.opportunity)
        proximity.clear(); proximity.accept(fix(30.009, 1180000)); assertTrue(proximity.opportunity)
    }
    @Test fun unknownIdCannotCreateAnAutomaticLandmarkAndSchemaRetainsSixAndSevenFieldCompatibility() {
        val proximity = LandmarkProximity(listOf(node())); proximity.accept(fix(30.009, 1000000))
        assertEquals(DeliveryOutcome.STALE, proximity.guard(speech(id = "invented"), false, proximity.ids))
        val full = DirectorResult(Action.SILENT).json()
        val seven = full.replace(",\"landmark_id\":\"\"", "")
        val six = seven.replace(",\"topic_family\":\"\"", "")
        listOf(full, seven, six).forEach { assertEquals(Action.SILENT, DirectorContract.parse(it).action) }
        assertTrue(runCatching { DirectorContract.parse(speech(id = "unknown space").json()) }.isFailure)
    }
    @Test fun catalogRejectsUngroundedInvalidAndDuplicateNodes() {
        val raw = File("src/main/assets/landmarks.json").readText(); val nodes = LandmarkCatalog.parse(raw)
        assertEquals(4, nodes.size); assertTrue(nodes.all { "https://" in it.grounding })
        val item = Json.parseToJsonElement(raw).jsonArray.first().jsonObject
        for ((key, value) in listOf("lat" to JsonPrimitive(100), "radius_m" to JsonPrimitive(0), "grounding" to JsonPrimitive(""))) {
            val bad = JsonArray(listOf(JsonObject(item + (key to value)))).toString()
            assertTrue(runCatching { LandmarkCatalog.parse(bad) }.isFailure)
        }
        assertTrue(runCatching { LandmarkCatalog.parse(JsonArray(listOf(item, item)).toString()) }.isFailure)
    }
    @Test fun completeRefinedRouteExercisesRealCatalogWithoutStaleOrDuplicateNarration() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val nodes = LandmarkCatalog.parse(File("src/main/assets/landmarks.json").readText())
        val context = ContextPipeline(nodes); val diagnostics = mutableListOf<String>(); val selected = mutableListOf<String>()
        val loop = DirectorLoop(journey, context, backgroundScope, clock, { DirectorProvider {
            val current = context.proximity.ids.firstOrNull(); delay(5000)
            if (current == null) DirectorResult(Action.SILENT).json()
            else {
                selected += current
                val target = nodes.first { it.id == current }
                speech(current, target.name).copy(topicFamily = target.family).json()
            }
        } }, diagnostic = { diagnostics += it })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val source = SimulatedLocationSource(fixture, backgroundScope, clock, 100.0, 16.0,
            paused = { loop.pending || journey.speaking || journey.listening || journey.imageInteraction })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location)
        var speakingAt = 0L
        repeat(1000) {
            advanceTimeBy(1000); runCurrent()
            if (journey.speaking) {
                if (speakingAt == 0L) speakingAt = clock()
                if (clock() - speakingAt >= 20000) { voice.finish(); speakingAt = 0 }
            }
        }
        assertTrue(source.completed); assertEquals(nodes.map { it.id }.toSet(), selected.toSet())
        assertEquals(selected.distinct(), selected); assertEquals(13, context.areas.transitions)
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE]); assertNull(loop.counters.automatic[DeliveryOutcome.FAILURE])
        assertNull(loop.counters.automatic[DeliveryOutcome.CANCELLED]); assertTrue(loop.counters.dispatched in 8..20)
        assertTrue(diagnostics.count { "landmark opportunity" in it } >= 4)
        val report = "Independent proximity: PASS (production catalog/Context/Journey/Loop; deterministic Provider/fake Voice)\n" +
            "100km/h; 16x; latency=5s; TTS=20s; chapters=${context.areas.transitions}\n" +
            "Selected=${selected.joinToString()}; ${loop.counters.summary()}\n" +
            "Catalog is four coarse regional references, not a nationwide POI service. Real phone/content quality: DEFERRED TO COMBINED PHONE ACCEPTANCE."
        File("build/acceptance").mkdirs(); File("build/acceptance/landmark-simulation.txt").writeText(report); println(report)
        journey.end(); loop.reset(); source.stop(); assertTrue(context.proximity.ids.isEmpty())
    }
}
