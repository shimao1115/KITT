package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterOpportunityTest {
    private val a = AreaIdentity("测试市", "测试区", "甲镇")
    @Test fun speakingListeningTypingImageQuietAndCooldownDelayButNeverEraseReadyChapter() = runTest {
        for (gate in listOf("speaking", "listening", "typing", "image", "quiet", "cooldown")) {
            var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
            var calls = 0; val cards = mutableListOf<String>()
            val context = ContextPipeline()
            val loop = DirectorLoop(journey, context, this, { time }, { DirectorProvider {
                calls++; cards += it.contextCard; DirectorResult(Action.SILENT, memoryUpdate = "已检查测试证据").json()
            } }, researchProvider = { testResearch })
            loop.location(Fix(30.0, 104.0, time, administrative = a)); runCurrent(); assertEquals(1, calls)
            when (gate) {
                "speaking" -> journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "主动回应", "测试正文").json())
                "listening", "typing" -> { journey.beginListening {}; if (gate == "typing") journey.stopListeningForTyping() }
                "image" -> journey.beginImageInteraction()
                "quiet" -> journey.quiet()
                "cooldown" -> journey.skip()
            }
            val b = a.copy(chapter = "乙街道")
            time += 1000; loop.location(Fix(30.001, 104.0, time, administrative = b)); runCurrent()
            assertEquals("gate=$gate", 1, calls); assertTrue(loop.research.opportunity)
            when (gate) {
                "speaking" -> voice.finish()
                "listening", "typing" -> journey.cancelReply()
                "image" -> journey.finishImageInteraction()
                "quiet" -> journey.resume()
            }
            time += 61000; loop.location(Fix(30.001, 104.0, time, administrative = b)); runCurrent()
            assertEquals("retained gate=$gate", 2, calls); assertFalse(loop.research.opportunity)
            assertEquals(ResearchStatus.CHECKED, loop.research.status(b))
            assertTrue("已实际搜索" in cards.last()); assertTrue("乙街道测试对象" in cards.last())
            repeat(100) { loop.check(); runCurrent() }; assertEquals(2, calls)
            journey.end(); loop.reset()
        }
    }
    @Test fun lateNarrationFromPreviousChapterDiesEvenWithinDistanceGuard() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider {
            delay(5000); DirectorResult(Action.SPEAK_NOW, "甲镇对象", "迟到正文").json()
        } }, researchProvider = { testResearch })
        loop.location(Fix(30.0, 104.0, time, administrative = a)); runCurrent()
        time += 1000; loop.location(Fix(30.001, 104.0, time, administrative = a.copy(chapter = "乙镇"))); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertTrue(voice.speech.isEmpty()); assertEquals(1, loop.counters.automatic[DeliveryOutcome.STALE])
        loop.reset(); journey.end()
    }
    @Test fun unresolvedGapDoesNotCreateRepeatedVisitOfCheckedChapter() = runTest {
        val manager = ChapterResearch(this, { 1 }, { testResearch })
        manager.enter(a); runCurrent(); manager.checked()
        manager.enter(null); manager.enter(a); assertFalse(manager.opportunity)
        manager.enter(a.copy(chapter = "乙镇")); runCurrent(); manager.enter(a); assertTrue(manager.opportunity)
        manager.clear()
    }
    @Test fun staleGpsNeverDeliversRetainedReadyChapter() = runTest {
        var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start(); var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider { calls++; DirectorResult(Action.SILENT).json() } },
            researchProvider = { LocalResearchProvider { area, at -> delay(5000); testDossier(area, at) } })
        loop.location(Fix(30.0, 104.0, time, administrative = a)); runCurrent()
        time += 61000; advanceTimeBy(5000); runCurrent(); loop.check(); runCurrent()
        assertEquals(0, calls); assertTrue(loop.research.opportunity)
        loop.reset(); journey.end()
    }
    @Test fun canceledDispatchBeforeActualDirectorCheckRetainsContextOpportunity() = runTest {
        var time = 1000000L; val journey = Journey({ time }, TestVoice()); journey.start(); var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider { calls++; DirectorResult(Action.SILENT).json() } }, researchProvider = { testResearch })
        loop.location(Fix(30.0, 104.0, time, administrative = a)); runCurrent()
        journey.quiet(); time += 1000
        loop.location(Fix(30.001, 104.0, time, administrative = a.copy(chapter = "乙镇"))); runCurrent()
        journey.resume(); time += 11000; journey.location(journey.fix!!.copy(timeMs = time))
        loop.check(); loop.cancel() // Coroutine never runs: nothing actually examined the dossier.
        assertTrue(loop.research.opportunity); assertEquals(1, calls)
        loop.check(); runCurrent(); assertEquals(2, calls); assertFalse(loop.research.opportunity)
        loop.reset(); journey.end()
    }
    @Test fun landmarkBreaksOnlyOrdinaryAutomaticSpeechCooldown() {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        journey.location(Fix(30.0, 104.0, time))
        journey.deliver(journey.ticket(false), DirectorResult(Action.SPEAK_NOW, "旧自动对象", "正文").json()); voice.finish()
        time += 1000; journey.location(Fix(30.001, 104.0, time))
        assertFalse(journey.shouldCheck()); assertTrue(journey.shouldCheck(true))
        journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "用户对象", "正文").json()); voice.finish()
        assertFalse(journey.shouldCheck(true)); journey.quiet(); assertFalse(journey.shouldCheck(true))
    }
    @Test fun dossierEntityAndLandmarkShareDeliveryDedupButFollowUpIsAllowed() {
        val node = Landmark("test-site", "共同对象", 30.0, 104.0, 500.0, LandmarkKind.HERITAGE, TopicFamily.HERITAGE, "测试依据")
        val proximity = LandmarkProximity(listOf(node)); proximity.accept(Fix(30.0, 104.0, 1))
        val dossierSpeech = DirectorResult(Action.SPEAK_NOW, "共同对象的历史", "正文", memoryUpdate = "共同对象")
        proximity.delivered(dossierSpeech)
        assertFalse(proximity.opportunity)
        assertEquals(DeliveryOutcome.SUPPRESSED, proximity.guard(dossierSpeech.copy(landmarkId = node.id), false, proximity.ids))
        assertNull(proximity.guard(dossierSpeech, true, proximity.ids))
    }
}
