package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NarrationLoopTest {
    private val area = AreaIdentity("测试市", "测试区", "", "测试省")
    private fun speech(coverage: String = "文献中的基础介绍", text: String = "基础介绍。") =
        DirectorResult(Action.SPEAK_NOW, "测试区测试对象", text, memoryUpdate = coverage)

    @Test fun overviewToTopicReturnSuppressesOnlyCoveredFactsAndNewFactStillSpeaks() = runTest {
        val clock = { 1_000_000L + testScheduler.currentTime }; val voice = TestVoice(); val j = Journey(clock, voice); j.start()
        var topicCalls = 0; var requests = 0; var supplement = false
        val research = object : LocalResearchProvider {
            override suspend fun research(area: AreaIdentity, at: Long) = testDossier(area, at)
            override val supportsTopics = true
            override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long): LocalDossier {
                topicCalls++; delay(2000)
                return testDossier(area, at).let { it.copy(facts = it.facts.map { fact -> fact.copy(summary = "可靠的独立新事实") }) }
            }
        }
        val context = ContextPipeline()
        val loop = DirectorLoop(j, context, this, clock, { DirectorProvider { request ->
            requests++
            if ("【Local Dossier】" !in request.contextCard) DirectorResult(Action.SILENT).json()
            else if (supplement) {
                assertTrue("可靠的独立新事实" in request.contextCard)
                speech("独立新事实", "有来源的新事实。").json()
            } else speech(text = if (requests > 2) "基础介绍换一种说法。" else "基础介绍。").json()
        } }, researchProvider = { research })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent(); voice.finish()
        assertEquals(1, voice.speech.size); assertEquals(1, topicCalls)
        advanceTimeBy(61_000); loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        assertEquals(1, loop.counters.automatic[DeliveryOutcome.SUPPRESSED]); assertEquals(1, voice.speech.size)
        supplement = true; advanceTimeBy(61_000)
        loop.location(Fix(30.03, 104.0, clock(), administrative = area)); runCurrent(); voice.finish()
        assertEquals(2, voice.speech.size); assertEquals(2, j.narrationMemory.count { it.phase == NarrationPhase.COMPLETED })
        loop.reset(); j.end()
    }

    @Test fun explicitReplayStopsPendingAudioAndOnlyReplaysPreviouslyCompletedText() = runTest {
        val voice = TestVoice(); val j = Journey({ 1_000_000 }, voice); j.start()
        var calls = 0
        val loop = DirectorLoop(j, ContextPipeline(), this, { 1_000_000 }, { DirectorProvider { calls++; speech().json() } })
        loop.location(Fix(30.0, 104.0, 1_000_000)); runCurrent()
        voice.finish() // Only a successfully completed segment is eligible for exact replay.
        loop.user("再说一遍"); runCurrent()
        val late = voice.done!!; val stops = voice.stops
        loop.user("再说一遍"); runCurrent()
        assertTrue(voice.stops > stops); assertEquals(1, calls); assertEquals(voice.speech.first(), voice.speech.last())
        late(true); assertEquals(1, j.recentTopics.size); voice.finish(); assertEquals(2, j.recentTopics.size)
        loop.reset(); j.end()
    }

    @Test fun replayAfterMovingRetainsOriginalSubjectArea() = runTest {
        var time = 1_000_000L; val voice = TestVoice(); val j = Journey({ time }, voice); j.start()
        val loop = DirectorLoop(j, ContextPipeline(), this, { time }, { DirectorProvider { speech().json() } })
        loop.location(Fix(30.0, 104.0, time, administrative = area)); runCurrent(); voice.finish()
        time += 1000; j.location(Fix(30.0, 104.0, time, administrative = area.copy(district = "另一区")))
        loop.user("再说一遍"); voice.finish()
        assertEquals(listOf(area, area), j.narrationMemory.map { it.area }); loop.reset(); j.end()
    }

    @Test fun moreWithoutNewSourcesCannotRecycleOldFactsButOrdinaryQuestionAndImageCanAnswer() = runTest {
        val voice = TestVoice(); val j = Journey({ 1_000_000 }, voice); j.start()
        val p = object : DirectorProvider {
            override val acceptsImages = true
            override suspend fun researchNeed(request: DirectorRequest) = ResearchNeed.USE_CONTEXT
            override suspend fun direct(request: DirectorRequest): String {
                if (request.image == null && followUp(request.userUtterance) == FollowUp.MORE) {
                    assertTrue("事实/角度=文献中的基础介绍" in request.contextCard)
                    assertTrue("不另换对象" in request.contextCard)
                }
                return speech().json()
            }
        }
        val loop = DirectorLoop(j, ContextPipeline(), this, { 1_000_000 }, { p })
        loop.location(Fix(30.0, 104.0, 1_000_000)); runCurrent(); voice.finish()
        loop.user("再讲一点"); runCurrent(); assertEquals("目前没有可靠的新内容。", voice.speech.last()); voice.finish()
        loop.user("石拱怎么承重？"); runCurrent(); assertEquals("基础介绍。", voice.speech.last()); voice.finish()
        loop.user("再讲一点", ImageInput("image/jpeg", "AQID")); runCurrent()
        assertEquals("基础介绍。", voice.speech.last()); loop.reset(); j.end()
    }

    @Test fun moreSearchFailureReportsFailureWithoutInventingOrWritingCoverage() = runTest {
        val voice = TestVoice(); val j = Journey({ 1_000_000 }, voice); j.start(); var searched = 0
        val p = object : DirectorProvider {
            override suspend fun direct(request: DirectorRequest) = speech().json()
            override suspend fun researchNeed(request: DirectorRequest) = ResearchNeed.SEARCH_REQUIRED
        }
        val research = object : LocalResearchProvider {
            override suspend fun research(area: AreaIdentity, at: Long) = error("not used")
            override suspend fun researchQuestion(query: LocalQuestion, at: Long): LocalDossier {
                searched++; throw ResearchUnavailable("no sources")
            }
        }
        val loop = DirectorLoop(j, ContextPipeline(), this, { 1_000_000 }, { p }, researchProvider = { research })
        loop.location(Fix(30.0, 104.0, 1_000_000)); runCurrent(); voice.finish()
        loop.user("再讲一点"); runCurrent()
        assertEquals(1, searched); assertEquals("刚才没查到可靠资料，暂时无法确认。", voice.speech.last())
        assertEquals(1, j.narrationMemory.size); loop.reset(); j.end()
    }

    @Test fun moreUsesOneSuccessfulSearchForNewEvidenceAndNormalDirector() = runTest {
        val voice = TestVoice(); val j = Journey({ 1_000_000 }, voice); j.start(); var searches = 0; var directs = 0
        val p = object : DirectorProvider {
            override suspend fun researchNeed(request: DirectorRequest) = ResearchNeed.SEARCH_REQUIRED
            override suspend fun direct(request: DirectorRequest): String {
                directs++
                return if (request.userUtterance == null) speech().json() else {
                    assertTrue("【当前主动问题的搜索证据】" in request.contextCard)
                    speech("文献中的新事实", "新依据的补充。").json()
                }
            }
        }
        val research = object : LocalResearchProvider {
            override suspend fun research(area: AreaIdentity, at: Long) = testDossier(area, at)
            override suspend fun researchQuestion(query: LocalQuestion, at: Long): LocalDossier {
                searches++; return testDossier(query.area, at)
            }
        }
        val loop = DirectorLoop(j, ContextPipeline(), this, { 1_000_000 }, { p }, researchProvider = { research })
        loop.location(Fix(30.0, 104.0, 1_000_000)); runCurrent(); voice.finish()
        loop.user("再讲一点"); runCurrent(); voice.finish()
        assertEquals(1, searches); assertEquals(2, directs); assertEquals(2, j.recentTopics.size)
        assertEquals("新依据的补充。", voice.speech.last()); loop.reset(); j.end()
    }

    @Test fun routeCanRejectRepeatedCoverageWhileEveryChapterGetsNewObjectAndNewFact() = runTest {
        val clock = { 1_000_000L + testScheduler.currentTime }; val voice = TestVoice(); val j = Journey(clock, voice); j.start()
        val context = ContextPipeline(); val chapters = linkedSetOf<String>(); var attempts = 0
        val loop = DirectorLoop(j, context, backgroundScope, clock, { DirectorProvider {
            val chapter = context.areas.active!!.area
            val covered = j.narrationMemory.filter { it.area == chapter }
            val summary = if (covered.isEmpty()) "基础事实" else if (covered.size == 1) "独立新事实" else "基础事实"
            chapters += chapter.key; attempts++
            speech(summary, "合成对象的${summary}，表达版本$attempts。").copy(topic = "本地同名对象").json()
        } }, researchProvider = { testResearch })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        // Deliberate recheck coverage, separate from the six unchanged timing simulations.
        fixture.points.distinctBy { it.administrative?.key }.forEach { point ->
            repeat(3) {
                advanceTimeBy(61_000); j.noteChapterEntry()
                loop.location(Fix(point.lat, point.lon, clock(), area = point.area,
                    administrative = point.administrative, source = FixSource.SIMULATED)); runCurrent()
                if (j.speaking) voice.finish()
            }
        }
        assertEquals(13, chapters.size)
        // Same-named subjects in different chapters are independent; each delivered 2 different facts.
        assertEquals(26, voice.speech.size); assertTrue((loop.counters.automatic[DeliveryOutcome.SUPPRESSED] ?: 0) > 0)
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE]); assertNull(loop.counters.automatic[DeliveryOutcome.FAILURE])
        val report = "V0.3.6 semantic coverage route: deliberate three rechecks/chapter, synthetic labels/Director/fake Voice, NOT human semantic acceptance or timing simulation.\n" +
            "chapters=${chapters.size}; unique new object/fact outputs=${voice.speech.size}; ${loop.counters.summary()}\n"
        File("build/acceptance/narration-memory.txt").apply { parentFile!!.mkdirs(); writeText(report) }
        loop.reset(); j.end()
    }
}
