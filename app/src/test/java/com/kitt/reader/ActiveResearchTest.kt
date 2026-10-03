package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveResearchTest {
    private val area = AreaIdentity("测试市", "测试区", "测试镇", "测试省")
    private fun setup(scope: CoroutineScope, clock: () -> Long, research: LocalResearchProvider,
        need: ResearchNeed = ResearchNeed.USE_CONTEXT, decisions: () -> Unit = {},
        narrated: (DirectorRequest) -> Unit = {}): Triple<Journey, TestVoice, DirectorLoop> {
        val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        val director = object : DirectorProvider {
            override suspend fun researchNeed(request: DirectorRequest): ResearchNeed { decisions(); return need }
            override suspend fun direct(request: DirectorRequest): String {
                if (request.userUtterance == null) return DirectorResult(Action.SILENT).json()
                narrated(request)
                return DirectorResult(Action.SPEAK_NOW, "当前问题回应", "测试回答，实际声音仍由原 Journey 发出。").json()
            }
        }
        val loop = DirectorLoop(journey, ContextPipeline(), scope, clock, { director }, researchProvider = { research })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area))
        return Triple(journey, voice, loop)
    }
    private fun research(searches: (LocalQuestion) -> Unit = {}, fail: Boolean = false, latency: Long = 0) = object : LocalResearchProvider {
        override suspend fun research(area: AreaIdentity, at: Long) = testDossier(area, at)
        override suspend fun researchQuestion(query: LocalQuestion, at: Long): LocalDossier {
            searches(query); delay(latency)
            if (fail) throw ResearchUnavailable("测试搜索服务不可用")
            return testDossier(query.area, at)
        }
    }
    @Test fun sufficientDossierAnswersDirectlyWithoutDuplicateSearch() = runTest {
        var searches = 0; var decisions = 0; val inputs = mutableListOf<DirectorRequest>()
        val (journey, voice, loop) = setup(this, { 1000000 }, research({ searches++ }), decisions = { decisions++ }, narrated = { inputs += it })
        runCurrent(); loop.user("这个对象是什么？"); runCurrent()
        assertEquals(0, searches); assertEquals(1, decisions)
        assertTrue("已实际搜索" in inputs.single().contextCard); assertTrue("本次未联网" in inputs.single().contextCard)
        assertEquals(1, voice.speech.size); assertTrue(journey.running); loop.reset(); journey.end()
    }
    @Test fun explicitSearchAndRealtimeAlwaysForceResearchEvenWithSufficientDossier() = runTest {
        for (question in listOf("查一下附近有什么博物馆", "帮我找附近值得去的地方", "这个寺庙今天开放吗", "附近今天有什么活动", "这里最近有什么新闻", "这条路为什么堵", "刚才的人物再查详细一点")) {
            var searches = 0; var decisions = 0; val inputs = mutableListOf<DirectorRequest>()
            val (journey, voice, loop) = setup(this, { 1000000 }, research({ q -> searches++; assertEquals(area, q.area); assertTrue("30.0000" in q.context) }),
                decisions = { decisions++ }, narrated = { inputs += it })
            runCurrent(); loop.user(question); runCurrent()
            assertEquals(question, 1, searches); assertEquals(0, decisions)
            assertTrue("当前主动问题的搜索证据" in inputs.single().contextCard)
            assertTrue("example.test/evidence" in inputs.single().contextCard)
            assertEquals(1, voice.speech.size); loop.reset(); journey.end()
        }
    }
    @Test fun missingLocalEvidenceSearchesBeforeOriginalDirectorNarrates() = runTest {
        val order = mutableListOf<String>()
        val (journey, voice, loop) = setup(this, { 1000000 }, research({ order += "search" }), ResearchNeed.SEARCH_REQUIRED,
            { order += "decision" }, { order += "director" })
        runCurrent(); loop.user("这座寺庙是怎样建起来的？"); runCurrent()
        assertEquals(listOf("decision", "search", "director"), order); assertEquals(1, voice.speech.size)
        loop.reset(); journey.end()
    }
    @Test fun ordinaryStableKnowledgeDoesNotSearch() = runTest {
        var searches = 0
        val (journey, voice, loop) = setup(this, { 1000000 }, research({ searches++ }), ResearchNeed.GENERAL_KNOWLEDGE)
        runCurrent(); loop.user("河流为什么会弯曲？"); runCurrent()
        assertEquals(0, searches); assertEquals(1, voice.speech.size); loop.reset(); journey.end()
    }
    @Test fun failedSearchBrieflyReportsFailureAndNeverFallsBackToModelMemory() = runTest {
        var narratives = 0
        val (journey, voice, loop) = setup(this, { 1000000 }, research(fail = true), narrated = { narratives++ })
        runCurrent(); loop.user("帮我查今天是否开放"); runCurrent()
        assertEquals(0, narratives); assertEquals(listOf("刚才没查到可靠资料，暂时无法确认。"), voice.speech)
        assertEquals(1, loop.counters.active[DeliveryOutcome.FAILURE]); assertTrue(journey.running)
        voice.finish(); assertEquals(JourneyState.READING, journey.state); loop.reset(); journey.end()
    }
    @Test fun searchTimeoutIsHonestAndDoesNotRetry() = runTest {
        var searches = 0
        val (journey, voice, loop) = setup(this, { 1000000L + testScheduler.currentTime }, research({ searches++ }, latency = 100000))
        runCurrent(); loop.user("查一下当前状态"); runCurrent(); advanceTimeBy(90001); runCurrent()
        assertEquals(1, searches); assertEquals(listOf("刚才没查到可靠资料，暂时无法确认。"), voice.speech)
        loop.reset(); journey.end()
    }
    @Test fun questionFinishesInSameJourneyChapterAndSessionDossierWithoutMergingDynamicFacts() = runTest {
        val (journey, voice, loop) = setup(this, { 1000000 }, research())
        runCurrent(); val started = journey.started; val dossier = loop.research.dossier(area)
        loop.user("帮我查附近今天有什么活动"); runCurrent(); voice.finish()
        assertEquals(started, journey.started); assertEquals(area, loop.research.active); assertSame(dossier, loop.research.dossier(area))
        assertEquals(JourneyState.READING, journey.state); assertTrue(journey.running)
        loop.reset(); journey.end()
    }
    @Test fun typedAndAsrRepliesUseSameSearchDecisionAndDirectorPath() = runTest {
        for (typed in listOf(true, false)) {
            var searches = 0; val inputs = mutableListOf<String>()
            val (journey, voice, loop) = setup(this, { 1000000 }, research({ searches++ }), narrated = { inputs += it.userUtterance!! })
            runCurrent(); loop.speak()
            if (typed) assertTrue(journey.submitReply("查一下附近博物馆")) else voice.answer!!.invoke("查一下附近博物馆")
            runCurrent(); assertEquals(1, searches); assertEquals(listOf("查一下附近博物馆"), inputs)
            assertEquals(1, voice.speech.size); assertFalse(journey.listening); loop.reset(); journey.end()
        }
    }
    @Test fun newUserIntentCancelsLateSearchAndCannotQueueOldAnswer() = runTest {
        val inputs = mutableListOf<String>()
        val (journey, voice, loop) = setup(this, { 1000000 }, research(latency = 10000), narrated = { inputs += it.userUtterance!! })
        runCurrent(); loop.user("查一下附近活动"); runCurrent(); loop.user("说个河流常识"); runCurrent()
        advanceTimeBy(10001); runCurrent()
        assertEquals(listOf("说个河流常识"), inputs); assertEquals(1, voice.speech.size)
        assertEquals(1, loop.counters.active[DeliveryOutcome.CANCELLED]); loop.reset(); journey.end()
    }
    @Test fun activeSearchCanTakeMoreThan45SecondsButAutoTicketStillExpires() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }
        val (journey, voice, loop) = setup(this, clock, research(latency = 50000))
        runCurrent(); loop.user("查一下附近新闻"); runCurrent(); advanceTimeBy(50001); runCurrent()
        assertEquals(1, voice.speech.size); assertEquals(1, loop.counters.active[DeliveryOutcome.SPEAK_NOW])
        loop.reset(); journey.end()
    }
    @Test fun sameProviderDecisionUsesOriginalStrictEightFields() {
        val raw = DirectorResult(Action.SILENT, memoryUpdate = "SEARCH_REQUIRED").json()
        assertEquals(ResearchNeed.SEARCH_REQUIRED, ActiveResearchPolicy.parse(raw))
        assertTrue(runCatching { ActiveResearchPolicy.parse(DirectorResult(Action.SPEAK_NOW, "题", "文").json()) }.isFailure)
        assertTrue(runCatching { ActiveResearchPolicy.parse(DirectorResult(Action.SILENT, memoryUpdate = "unknown").json()) }.isFailure)
    }
    @Test fun nearbySearchPromptKeepsGpsGroundingButNeverPromisesMapDistances() {
        val query = LocalQuestion(area, "附近有什么博物馆？", "当前位置 GPS30,104；无地图距离数据")
        val payload = LocalResearchContract.payload(area, ProviderConfig(ProviderKind.OPENAI), question = query).toString()
        assertTrue("当前位置 GPS30,104" in payload); assertTrue("不得伪造" in payload)
        assertTrue("搜索时间不等于事实生效时间" in payload)
        assertEquals("required", payload.getJsonString("tool_choice"))
    }
    @Test fun realtimeResearchUsesChinaDateRatherThanHostOrUtcDate() {
        val at = java.time.Instant.parse("2026-10-02T20:00:00Z").toEpochMilli()
        val payload = LocalResearchContract.payload(area, ProviderConfig(ProviderKind.OPENAI),
            question = LocalQuestion(area, "今天开放吗？", "现场"), at = at).toString()
        assertTrue("2026-10-03" in payload); assertTrue("Asia/Shanghai" in payload)
    }
    private fun String.getJsonString(key: String) = kotlinx.serialization.json.Json.parseToJsonElement(this)
        .let { (it as kotlinx.serialization.json.JsonObject)[key].toString().trim('"') }
}
