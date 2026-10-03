package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StagedResearchTest {
    private val area = AreaIdentity("测试市", "测试区", "", "测试省")
    private val url = "https://example.test/evidence"
    private fun overview(summary: String = "有可靠文献支持的当地历史对象。", sourceUrl: String = url, kind: String = "INSTITUTIONAL") = buildJsonObject {
        put("orientation", "地方概况")
        put("sources", buildJsonArray { add(buildJsonObject {
            put("id", "s1"); put("title", "文献"); put("url", sourceUrl); put("kind", kind)
        }) })
        put("objects", buildJsonArray { add(buildJsonObject {
            put("title", "测试对象"); put("why_it_matters", summary); put("family", "HISTORY"); put("salience", 5)
            put("source_ids", buildJsonArray { add("s1") })
        }) })
    }.toString()
    private fun envelope(text: String, completed: Boolean = true, sources: Boolean = true) = buildJsonObject {
        put("status", "completed"); put("output", buildJsonArray {
            add(buildJsonObject {
                put("type", "web_search_call"); put("status", if (completed) "completed" else "in_progress")
                put("action", buildJsonObject { put("sources", buildJsonArray { if (sources) add(buildJsonObject { put("url", url) }) }) })
            })
            add(buildJsonObject { put("type", "message"); put("content", buildJsonArray {
                add(buildJsonObject { put("type", "output_text"); put("text", text) })
            }) })
        })
    }.toString()
    private fun provider(overviewDelay: Long = 0, topicDelay: Long = 5000, cancelled: () -> Unit = {}) = object : LocalResearchProvider {
        override val supportsTopics = true
        override suspend fun research(area: AreaIdentity, at: Long): LocalDossier = error("Heavy chapter contract must not be called")
        override suspend fun overview(area: AreaIdentity, at: Long): OverviewDossier {
            delay(overviewDelay); return OverviewDossier.from(testDossier(area, at))
        }
        override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long): LocalDossier = try {
            delay(topicDelay); testDossier(area, at).let { it.copy(facts = it.facts.map { f -> f.copy(summary = "新专题事实") }) }
        } finally { cancelled() }
    }
    @Test fun overviewHasSmallSchemaRequiredSearchAndNoBenchmarkInjection() {
        val payload = StagedResearchContract.payload(AreaIdentity("成都市", "新都区", "", "四川省"), ProviderConfig(ProviderKind.CHATGPT, model = "gpt-5.6-luna"), true)
        val format = payload.getValue("text").jsonObject.getValue("format").jsonObject
        assertEquals("overview_dossier", format.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("orientation", "sources", "objects"), format.getValue("schema").jsonObject.getValue("properties").jsonObject.keys)
        assertFalse(payload.toString().contains("18")); assertEquals("required", payload.getValue("tool_choice").jsonPrimitive.content)
        assertTrue(payload.getValue("stream").jsonPrimitive.boolean)
        listOf("杨慎", "杨升庵", "桂湖", "宝光寺", "新繁东湖").forEach { assertFalse(it in payload.toString()) }
    }
    @Test fun overviewRequiresCompletedToolAndMatchingProvenance() {
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(), completed = false), area, 1) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(), sources = false), area, 1) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(sourceUrl = "https://invented.test/")), area, 1) }.isFailure)
        val result = StagedResearchContract.overviewResponse(envelope(overview()), area, 1)
        assertEquals(1, result.objects.size); assertTrue(url in result.evidence().text())
        assertTrue("不得根据标题扩写" in result.evidence().text())
    }
    @Test fun emptyUnsupportedAndOversizedOverviewCannotBeReady() {
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(summary = "")), area, 1) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(kind = "OTHER")), area, 1) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.overviewResponse(envelope(overview(summary = "长".repeat(101))), area, 1) }.isFailure)
    }
    @Test fun officialRecognitionStillRequiresGovernmentSource() {
        assertTrue(runCatching { StagedResearchContract.overview(overview("全国重点文物保护单位", kind = "OFFICIAL"), area, 1, setOf(url)) }.isFailure)
        val gov = "https://test.gov.cn/list"
        assertEquals(1, StagedResearchContract.overview(overview("全国重点文物保护单位", gov, "OFFICIAL"), area, 1, setOf(gov)).objects.size)
        assertTrue(runCatching { StagedResearchContract.overview(overview("市级文物保护单位"), area, 1, setOf(url)) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.overview(overview().replace("地方概况", "国家级非遗"), area, 1, setOf(url)) }.isFailure)
    }
    @Test fun readyOverviewImmediatelyProvidesOpportunityWithoutTopic() = runTest {
        val manager = ChapterResearch(this, { testScheduler.currentTime }, { provider(overviewDelay = 1000) })
        manager.enter(area); manager.deepen(); runCurrent(); assertFalse(manager.topicPending)
        advanceTimeBy(1000); runCurrent()
        assertTrue(manager.opportunity); assertNotNull(manager.overview(area)); assertFalse(manager.topicPending)
        manager.checked(); manager.deepen(); runCurrent(); assertTrue(manager.topicPending)
        assertFalse(manager.pending); assertFalse(manager.opportunity); manager.clear()
    }
    @Test fun singleTopicAddsEvidenceAndCachesOnlyUntilTripEnd() = runTest {
        var completed = 0
        val manager = ChapterResearch(this, { testScheduler.currentTime }, { provider(cancelled = { completed++ }) })
        manager.enter(area); runCurrent(); manager.checked(); repeat(20) { manager.deepen() }; runCurrent()
        assertTrue(manager.topicPending); advanceTimeBy(5000); runCurrent()
        assertEquals(1, completed); assertTrue(manager.opportunity); assertTrue("新专题事实" in manager.card())
        manager.checked(); assertFalse(manager.opportunity)
        manager.enter(null); assertEquals("", manager.card()); manager.enter(area)
        assertNotNull(manager.topic(area, "${area.label}测试对象")); assertTrue("新专题事实" in manager.card())
        manager.clear(); assertNull(manager.topic(area, "${area.label}测试对象"))
    }
    @Test fun leavingCancelsTopicAndCannotDeliverItLater() = runTest {
        var cancelled = 0
        val manager = ChapterResearch(this, { 1000 }, { provider(cancelled = { cancelled++ }) })
        manager.enter(area); runCurrent(); manager.checked(); manager.deepen(); runCurrent()
        manager.enter(area.copy(district = "乙区")); runCurrent(); advanceTimeBy(6000); runCurrent()
        assertEquals(1, cancelled); assertNull(manager.topic(area, "${area.label}测试对象")); assertFalse("新专题事实" in manager.card())
        manager.clear()
    }
    @Test fun overviewFailureRetainsSafeOpportunityWithoutTopics() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { _, _ -> throw ResearchUnavailable("尚未获得证据") } })
        manager.enter(area); runCurrent(); assertTrue(manager.opportunity); assertEquals(ResearchStatus.FAILED, manager.status(area))
        manager.deepen(); assertFalse(manager.topicPending); manager.checked(); assertFalse(manager.opportunity); manager.clear()
    }
    @Test fun userQuestionPreemptsTopicButKeepsCompletedOverview() = runTest {
        var time = 1_000_000L; var cancelled = 0; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val director = object : DirectorProvider {
            override suspend fun direct(request: DirectorRequest) = DirectorResult(Action.SILENT).json()
            override suspend fun researchNeed(request: DirectorRequest) = ResearchNeed.USE_CONTEXT
        }
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { director }, researchProvider = { provider(cancelled = { cancelled++ }) })
        loop.location(Fix(30.0, 104.0, time, administrative = area)); runCurrent()
        loop.research.deepen(); runCurrent(); assertTrue(loop.research.topicPending)
        assertFalse(loop.simulationPaused)
        loop.user("请说一下已查到的对象"); runCurrent()
        assertEquals(1, cancelled); assertFalse(loop.research.topicPending); assertNotNull(loop.research.overview(area))
        assertEquals(1, loop.counters.activeDispatched); loop.reset(); journey.end()
    }
    @Test fun topicTimeoutDoesNotRemoveOverviewOrBlockDirector() = runTest {
        val manager = ChapterResearch(this, { testScheduler.currentTime }, { provider(topicDelay = 100000) })
        manager.enter(area); runCurrent(); manager.checked(); manager.deepen(); advanceTimeBy(90001); runCurrent()
        assertFalse(manager.topicPending); assertNotNull(manager.overview(area)); assertEquals(ResearchStatus.CHECKED, manager.status(area))
        assertFalse(manager.pending); manager.clear()
    }
    @Test fun overviewReadyDispatchesDirectorWhileTopicDoesNotPauseSimulation() = runTest {
        val clock = { 1_000_000L + testScheduler.currentTime }
        val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        var groundedCalls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, clock, { DirectorProvider { request ->
            if ("【Local Dossier】" in request.contextCard) {
                groundedCalls++
                DirectorResult(Action.SPEAK_NOW, "测试区测试对象", "有来源的基础介绍。", memoryUpdate = "已讲基础介绍").json()
            } else DirectorResult(Action.SILENT).json()
        } }, researchProvider = { provider(overviewDelay = 1000) })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent()
        assertEquals(0, groundedCalls); advanceTimeBy(1000); runCurrent()
        assertEquals(1, groundedCalls); assertTrue(journey.speaking); assertTrue(loop.research.topicPending)
        voice.finish(); assertFalse(loop.simulationPaused); assertTrue(loop.research.topicPending)
        loop.reset(); journey.end()
    }
    @Test fun topicUsesNarrowPromptWithRequiredToolAndProvenanceValidation() {
        val lead = OverviewDossier.from(testDossier(area, 1)).objects.single()
        val payload = StagedResearchContract.payload(area, ProviderConfig(ProviderKind.CHATGPT), true, lead)
        assertEquals("topic_dossier", payload.getValue("text").jsonObject.getValue("format").jsonObject.getValue("name").jsonPrimitive.content)
        assertTrue(lead.title in payload.getValue("input").toString())
        assertFalse(LocalResearchContract.instructions in payload.getValue("instructions").jsonPrimitive.content)
        assertTrue(runCatching { StagedResearchContract.topicResponse(envelope("{}", sources = false), area, 1) }.isFailure)
        val text = buildJsonObject {
            val source = Json.parseToJsonElement(overview()).jsonObject.getValue("sources")
            put("orientation", "窄主题"); put("sources", source); put("gaps", buildJsonArray {})
            put("facts", buildJsonArray { add(buildJsonObject {
                put("title", "新事实"); put("summary", "有出处的新背景"); put("family", "HISTORY"); put("salience", 4)
                put("confidence", "high"); put("source_ids", buildJsonArray { add("s1") }); put("designation", "")
            }) })
        }.toString()
        assertEquals(1, StagedResearchContract.topicResponse(envelope(text), area, 1).facts.size)
        assertTrue(runCatching { StagedResearchContract.topicResponse(envelope(text, completed = false), area, 1) }.isFailure)
        assertTrue(runCatching { StagedResearchContract.topicResponse(envelope(text.replace("有出处的新背景", "全国重点文物保护单位")), area, 1) }.isFailure)
    }
    @Test fun narratedObjectWinsOverUnrelatedHighSalienceLead() = runTest {
        var chosen = ""
        val p = object : LocalResearchProvider {
            override val supportsTopics = true
            override suspend fun research(area: AreaIdentity, at: Long) = testDossier(area, at)
            override suspend fun overview(area: AreaIdentity, at: Long): OverviewDossier {
                val base = OverviewDossier.from(testDossier(area, at))
                val lead = base.objects.single()
                return base.copy(objects = listOf(lead.copy(title = "甲对象", salience = 5), lead.copy(title = "乙园与乙人物", salience = 4)))
            }
            override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long): LocalDossier {
                chosen = objectToResearch.title; return testDossier(area, at)
            }
        }
        val manager = ChapterResearch(this, { 1000 }, { p }); manager.enter(area); runCurrent()
        manager.checked(); manager.deepen("乙园的故事\n乙人物的地方关联"); runCurrent()
        assertEquals("乙园与乙人物", chosen); manager.clear()
    }
    @Test fun moreAboutCurrentTopicCarriesReadyNewFactsIntoUnchangedActiveBranch() = runTest {
        val clock = { 1_000_000L + testScheduler.currentTime }; val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        var activeContext = ""; var decisions = 0
        val p = object : DirectorProvider {
            override suspend fun researchNeed(request: DirectorRequest): ResearchNeed {
                decisions++; activeContext = request.contextCard; return ResearchNeed.USE_CONTEXT
            }
            override suspend fun direct(request: DirectorRequest): String = if ("【Local Dossier】" in request.contextCard)
                DirectorResult(Action.SPEAK_NOW, "测试区测试对象", "基础介绍", memoryUpdate = "已讲基础介绍").json()
                else DirectorResult(Action.SILENT).json()
        }
        val loop = DirectorLoop(journey, ContextPipeline(), this, clock, { p }, researchProvider = { provider(overviewDelay = 1000, topicDelay = 2000) })
        loop.location(Fix(30.0, 104.0, clock(), administrative = area)); runCurrent(); advanceTimeBy(1000); runCurrent()
        voice.finish(); advanceTimeBy(2000); runCurrent(); assertFalse(loop.research.topicPending)
        loop.user("再讲一点"); runCurrent()
        assertEquals(1, decisions); assertTrue("新专题事实" in activeContext)
        assertTrue("用户要继续刚才的“测试区测试对象”" in activeContext)
        assertTrue("不另换对象" in activeContext); loop.reset(); journey.end()
    }
}
