package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TotalSilenceHotfixTest {
    private val area = AreaIdentity("测试市", "测试区", "甲镇", "测试省")

    @Test fun initialAskUserRunsDuringPendingResearch() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }
        val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        val logs = mutableListOf<String>(); var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            calls++; assertTrue("RESEARCHING" in it.contextCard)
            assertFalse("【Local Dossier】" in it.contextCard)
            DirectorResult(Action.ASK_USER, question = "今天准备去哪儿？").json()
        } }, diagnostic = { logs += it }, researchProvider = { LocalResearchProvider { a, at ->
            delay(80000); testDossier(a, at)
        } })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        println("pending initial request calls=$calls research=${loop.research.pending} voice=${voice.speech.size}\n" + logs.joinToString("\n"))
        assertTrue(loop.research.pending); assertEquals(1, calls); assertEquals(1, voice.speech.size)
        voice.finish(); assertTrue(journey.listening)
        loop.reset(); journey.end()
    }

    @Test fun failureRetainsOneSafeChapterCheckWithoutForgingEvidence() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }
        val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        val logs = mutableListOf<String>(); val cards = mutableListOf<String>()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            cards += it.contextCard
            if ("FAILED" in it.contextCard) DirectorResult(Action.SPEAK_NOW, "一般机制", "以下只解释一般机制，并不描述当地事实。").json()
            else DirectorResult(Action.SILENT).json()
        } }, diagnostic = { logs += it }, researchProvider = { LocalResearchProvider { _, _ ->
            delay(5000); throw ResearchUnavailable("搜索被拒绝，不等于当地无内容")
        } })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        journey.quiet(); advanceTimeBy(5001); runCurrent()
        println("failure retained opportunity=${loop.research.opportunity} status=${loop.research.status(area)}\n" + logs.joinToString("\n"))
        assertTrue(loop.research.opportunity); assertEquals(ResearchStatus.FAILED, loop.research.status(area))
        journey.resume(); advanceTimeBy(11000); loop.location(journey.fix!!.copy(timeMs = clock())); runCurrent()
        assertEquals(1, voice.speech.size); assertFalse(loop.research.opportunity)
        assertTrue(cards.last().contains("不得用模型记忆")); assertFalse(cards.last().contains("【Local Dossier】"))
        assertNull(loop.research.dossier(area))
        repeat(100) { loop.check(); runCurrent() }; assertEquals(2, cards.size)
        loop.reset(); journey.end()
    }

    @Test fun simulationMovesWhileResearchIsPending() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }
        val journey = Journey(clock, TestVoice()); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider { DirectorResult(Action.SILENT).json() } },
            researchProvider = { LocalResearchProvider { a, at -> delay(80000); testDossier(a, at) } })
        val source = SimulatedLocationSource(RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText()),
            backgroundScope, clock, 100.0, 16.0, paused = { loop.simulationPaused })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location); runCurrent()
        advanceTimeBy(10000); runCurrent()
        assertTrue(loop.research.pending); assertTrue(source.traveledMeters > 3000)
        println("pending research simulation progress=${source.traveledMeters.toInt()}m")
        source.stop(); loop.reset(); journey.end()
    }

    @Test fun groundedLandmarkRunsWithoutWaitingForChapterResearch() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val node = Landmark("grounded", "测试节点", 30.01, 104.0, 500.0, LandmarkKind.RIVER,
            TopicFamily.GEOGRAPHY, "仅测试已有依据")
        val loop = DirectorLoop(journey, ContextPipeline(listOf(node)), backgroundScope, clock, { DirectorProvider {
            if ("landmark_id=grounded" in it.contextCard) DirectorResult(Action.SPEAK_NOW,
                "测试节点", "依据现有资料作测试说明", landmarkId = "grounded").json()
            else DirectorResult(Action.SILENT).json()
        } }, researchProvider = { LocalResearchProvider { a, at -> delay(80000); testDossier(a, at) } })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        advanceTimeBy(1000); loop.location(Fix(30.008, 104.0, clock(), administrative = area)); runCurrent()
        assertTrue(loop.research.pending); assertEquals(1, voice.speech.size)
        assertEquals(1, loop.counters.automatic[DeliveryOutcome.SPEAK_NOW]); loop.reset(); journey.end()
    }

    @Test fun typedAndAsrInputCancelAutomaticRequestDuringPendingResearch() = runTest {
        for (typed in listOf(true, false)) {
            val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
            val journey = Journey(clock, voice); journey.start()
            val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
                if (it.userUtterance == null) { delay(20000); DirectorResult(Action.SPEAK_NOW, "旧内容", "不能补播").json() }
                else DirectorResult(Action.SPEAK_NOW, "一般问题", "一般知识回应").json()
            } }, researchProvider = { LocalResearchProvider { a, at -> delay(80000); testDossier(a, at) } })
            loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent(); assertTrue(loop.pending)
            loop.speak()
            if (typed) assertTrue(journey.submitReply("河流为什么弯曲")) else voice.answer!!.invoke("河流为什么弯曲")
            runCurrent(); assertTrue(loop.research.pending)
            assertEquals(listOf("一般知识回应"), voice.speech)
            assertEquals(1, loop.counters.automatic[DeliveryOutcome.CANCELLED]); assertEquals(1, loop.counters.activeDispatched)
            loop.reset(); journey.end()
        }
    }

    @Test fun readyAfterDepartureNeverCreatesOldChapterNarration() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start(); val cards = mutableListOf<String>()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            cards += it.contextCard
            if ("【Local Dossier】" in it.contextCard) DirectorResult(Action.SPEAK_NOW, "旧章节", "不能补播").json()
            else DirectorResult(Action.SILENT).json()
        } }, researchProvider = { LocalResearchProvider { a, at -> delay(5000); testDossier(a, at) } })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        advanceTimeBy(1000); loop.location(Fix(30.001, 104.0, clock())); runCurrent()
        advanceTimeBy(5000); loop.location(journey.fix!!.copy(timeMs = clock())); runCurrent()
        assertNotNull(loop.research.dossier(area)); assertFalse(loop.research.opportunity)
        assertTrue(voice.speech.isEmpty()); assertEquals(1, cards.size)
        loop.reset(); journey.end()
    }

    @Test fun unresolvedGpsGapNeverRepeatsConsumedFailureOpportunity() = runTest {
        var calls = 0
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { _, _ -> calls++; throw ResearchUnavailable("不可用") } })
        manager.enter(area); runCurrent(); assertTrue(manager.opportunity); manager.checked()
        repeat(50) { manager.enter(null); manager.enter(area); assertFalse(manager.opportunity) }
        assertEquals(2, calls); assertEquals(ResearchStatus.FAILED, manager.status(area)); manager.clear()
    }
}
