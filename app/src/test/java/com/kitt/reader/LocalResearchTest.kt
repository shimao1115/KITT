package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic evidence only. Never a live-search benchmark or a production knowledge base. */
fun testDossier(area: AreaIdentity, at: Long): LocalDossier {
    val source = ResearchSource("s1", "测试来源", "https://example.test/evidence", SourceClass.INSTITUTIONAL)
    return LocalDossier(area, at, "确定性研究替身，不代表实际地方资料。", listOf(
        LocalFact("${area.label}测试对象", "仅用于验证事实包传递及现场机会，不是现实地方事实。", TopicFamily.OTHER, 4, "high", listOf(source))), listOf(source), emptyList())
}
val testResearch = LocalResearchProvider { area, at -> testDossier(area, at) }

@OptIn(ExperimentalCoroutinesApi::class)
class LocalResearchTest {
    private val area = AreaIdentity("测试市", "测试区", "甲镇", "测试省")
    private val url = "https://example.test/evidence"
    private fun raw(designation: String = "", summary: String = "这是当地一个历史对象，与此地的联系有可靠资料。", sourceUrl: String = url,
        kind: String = "INSTITUTIONAL", empty: Boolean = false): String = buildJsonObject {
        put("orientation", "地点背景")
        put("sources", buildJsonArray { add(buildJsonObject {
            put("id", "s1"); put("title", "文献"); put("url", sourceUrl); put("kind", kind)
        }) })
        put("facts", buildJsonArray { if (!empty) add(buildJsonObject {
            put("title", "对象"); put("summary", summary); put("family", "HISTORY"); put("salience", 4)
            put("confidence", "high"); put("source_ids", buildJsonArray { add("s1") }); put("designation", designation)
        }) })
        put("gaps", buildJsonArray {})
    }.toString()
    private fun envelope(text: String, search: Boolean = true, sources: Boolean = true) = buildJsonObject {
        put("status", "completed"); put("output", buildJsonArray {
            if (search) add(buildJsonObject {
                put("type", "web_search_call"); put("status", "completed")
                put("action", buildJsonObject { put("sources", buildJsonArray { if (sources) add(buildJsonObject { put("url", url) }) }) })
            })
            add(buildJsonObject { put("type", "message"); put("content", buildJsonArray {
                add(buildJsonObject { put("type", "output_text"); put("text", text) })
            }) })
        })
    }.toString()

    @Test fun requiredSearchUsesFullIdentityAndSeparateStrictSchema() {
        val config = ProviderConfig(ProviderKind.OPENAI)
        val payload = LocalResearchContract.payload(area, config)
        assertEquals("required", payload["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("web_search", payload["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(payload["input"].toString().contains("测试省 / 测试市 / 测试区 / 甲镇"))
        assertEquals(false, payload["store"]!!.jsonPrimitive.boolean)
        assertTrue(payload["include"].toString().contains("web_search_call.action.sources"))
        assertEquals("local_dossier", payload["text"]!!.jsonObject["format"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(8, DirectorContract.keys.size)
        assertFalse(payload.toString().contains("director"))
    }
    @Test fun modelMemoryWithoutSearchOrEvidenceCannotBeReady() {
        assertTrue(runCatching { LocalResearchContract.response(envelope(raw(), search = false), area, 1) }.isFailure)
        assertTrue(runCatching { LocalResearchContract.response(envelope(raw(), sources = false), area, 1) }.isFailure)
    }
    @Test fun sourceProvenanceSurvivesCompressionAndInventedLinksFail() {
        val dossier = LocalResearchContract.response(envelope(raw()), area, 123)
        val text = dossier.text()
        listOf(url, "example.test", "文献", "INSTITUTIONAL", "searched_at=123", "依据[s1]").forEach { assertTrue(it in text) }
        assertTrue(runCatching { LocalResearchContract.parse(raw(sourceUrl = "https://invented.test/"), area, 1, setOf(url)) }.isFailure)
        assertTrue(runCatching { LocalResearchContract.parse(raw(sourceUrl = "https://user:pass@example.test/"), area, 1, setOf("https://user:pass@example.test/")) }.isFailure)
    }
    @Test fun officialDesignationsRequireGovernmentEvidenceEvenIfModelLabelsBlogOfficial() {
        val blog = LocalResearchContract.parse(raw("全国重点文物保护单位", kind = "OFFICIAL"), area, 1, setOf(url))
        assertTrue(blog.facts.isEmpty()); assertTrue(blog.gaps.any { "政府依据" in it })
        val embedded = LocalResearchContract.parse(raw(summary = "是省级文物保护单位。"), area, 1, setOf(url))
        assertTrue(embedded.facts.isEmpty())
        val officialUrl = "https://www.test.gov.cn/list"
        val official = LocalResearchContract.parse(raw("全国重点文物保护单位", sourceUrl = officialUrl, kind = "OFFICIAL"), area, 1, setOf(officialUrl))
        assertEquals(1, official.facts.size); assertEquals("全国重点文物保护单位", official.facts.single().designation)
    }
    @Test fun citationAnnotationsAlsoProvideEvidenceButIncompleteSearchDoesNot() {
        val root = Json.parseToJsonElement(envelope(raw(), sources = false)).jsonObject
        val output = root["output"]!!.jsonArray.toMutableList()
        output[1] = buildJsonObject { put("type", "message"); put("content", buildJsonArray { add(buildJsonObject {
            put("type", "output_text"); put("text", raw()); put("annotations", buildJsonArray { add(buildJsonObject {
                put("type", "url_citation"); put("url", url); put("title", "文献")
            }) })
        }) }) }
        val cited = JsonObject(root + ("output" to JsonArray(output))).toString()
        assertEquals(1, LocalResearchContract.response(cited, area, 1).facts.size)
        assertTrue(runCatching { LocalResearchContract.response(cited.replace("\"web_search_call\",\"status\":\"completed\"", "\"web_search_call\",\"status\":\"in_progress\""), area, 1) }.isFailure)
    }
    @Test fun compatibleEndpointCannotPretendToSearchOrCallNetwork() = runTest {
        var network = 0
        val provider = ApiLocalResearch(ProviderConfig(ProviderKind.COMPATIBLE, apiKey = "test"), JsonTransport { _, _, _ -> network++; "" })
        assertTrue(runCatching { provider.research(area, 1) }.exceptionOrNull() is ResearchUnavailable)
        assertEquals(0, network)
    }
    @Test fun emptyActualResearchIsDistinctFromUnavailableAndStillGetsOneCheck() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at ->
            LocalResearchContract.response(envelope(raw(empty = true)), a, at)
        } })
        manager.enter(area); runCurrent()
        assertTrue(manager.opportunity); assertEquals(ResearchStatus.READY_UNCHECKED, manager.status(area))
        assertTrue(manager.dossier(area)!!.facts.isEmpty()); manager.checked(); assertFalse(manager.opportunity)
        manager.clear()
    }
    @Test fun chapterAndDistrictResearchAreOncePerTripIncludingRevisit() = runTest {
        val searched = mutableListOf<AreaIdentity>()
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at -> searched += a; testDossier(a, at) } })
        manager.enter(area); runCurrent(); repeat(100) { manager.enter(area) }
        assertEquals(listOf(area.copy(chapter = ""), area), searched)
        manager.checked(); manager.enter(area.copy(chapter = "乙街道")); runCurrent()
        assertEquals(3, searched.size); manager.enter(area); runCurrent()
        assertEquals(3, searched.size); assertTrue(manager.opportunity)
        manager.clear(); assertEquals(0, manager.size); assertNull(manager.active)
        manager.enter(area); runCurrent(); assertEquals(5, searched.size); manager.clear()
    }
    @Test fun leavingBeforeResearchCompletesCachesEvidenceButDropsDeliveryOpportunity() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at -> delay(5000); testDossier(a, at) } })
        manager.enter(area); runCurrent(); manager.enter(null)
        advanceTimeBy(5000); runCurrent()
        assertNotNull(manager.dossier(area)); assertFalse(manager.opportunity); assertEquals("", manager.card())
        manager.enter(area); assertTrue(manager.opportunity); manager.clear()
    }
    @Test fun failureIsAccurateAndNeverRepeatedByGpsTicks() = runTest {
        var calls = 0
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { _, _ -> calls++; throw ResearchUnavailable("搜索不可用，不是没有内容") } })
        manager.enter(area); runCurrent(); repeat(1000) { manager.enter(area) }
        assertEquals(2, calls); assertEquals(ResearchStatus.FAILED, manager.status(area)); assertTrue(manager.opportunity)
        assertTrue("搜索不可用" in manager.card()); assertFalse("已实际搜索" in manager.card())
        manager.checked(); assertFalse(manager.opportunity); assertEquals(ResearchStatus.FAILED, manager.status(area)); manager.clear()
    }
    @Test fun rapidChaptersHaveAtMostTwoResearchRequestsAndEndCancelsAll() = runTest {
        var active = 0; var maximum = 0; var completed = 0
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at ->
            active++; maximum = maxOf(active, maximum)
            try { delay(5000); completed++; testDossier(a, at) } finally { active-- }
        } })
        repeat(20) { manager.enter(area.copy(chapter = "镇$it")) }; runCurrent()
        assertEquals(2, maximum); assertEquals(2, active)
        manager.clear(); runCurrent(); advanceTimeBy(10000); runCurrent()
        assertEquals(0, active); assertEquals(0, completed); assertEquals(0, manager.size)
    }
    @Test fun researchTimeoutIsBoundedAndNeverBecomesNoContent() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { _, _ -> delay(100000); error("late") } })
        manager.enter(area); advanceTimeBy(90001); runCurrent()
        assertEquals(ResearchStatus.FAILED, manager.status(area)); assertTrue("超时" in manager.card())
        assertTrue(manager.opportunity); manager.checked(); assertFalse(manager.opportunity); manager.clear()
    }
    @Test fun interruptedTripNeverAcceptsLateResearch() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at ->
            withContext(NonCancellable) { delay(5000) }; testDossier(a, at)
        } })
        manager.enter(area); runCurrent(); manager.clear(); advanceTimeBy(5000); runCurrent()
        assertEquals(0, manager.size); assertNull(manager.dossier(area)); assertFalse(manager.opportunity)
    }
    @Test fun lateDistrictEvidenceGetsAnOpportunityEvenAfterTownWasChecked() = runTest {
        val manager = ChapterResearch(this, { 1000 }, { LocalResearchProvider { a, at ->
            delay(if (a.chapter.isEmpty()) 20000 else 5000); testDossier(a, at)
        } })
        manager.enter(area); advanceTimeBy(5001); runCurrent(); assertTrue(manager.opportunity)
        manager.checked(); assertFalse(manager.opportunity)
        advanceTimeBy(15000); runCurrent(); assertTrue(manager.opportunity)
        manager.checked(); assertFalse(manager.opportunity); manager.clear()
    }
    @Test fun lowQualityLeadsAreNotEnoughForGroundedFacts() {
        val dossier = LocalResearchContract.parse(raw(kind = "OTHER"), area, 1, setOf(url))
        assertTrue(dossier.facts.isEmpty()); assertTrue(dossier.gaps.any { "低质量来源" in it })
    }
    @Test fun xinduProductionRequestIsBlackBoxAndOutsiderDepthHasNoScript() {
        val xindu = AreaIdentity("成都市", "新都区", "", "四川省")
        val request = LocalResearchContract.payload(xindu, ProviderConfig(ProviderKind.OPENAI)).toString()
        listOf("杨升庵", "杨慎", "桂湖", "宝光寺", "新繁东湖").forEach { assertFalse("injected answer: $it", it in request) }
        val production = File("src/main/assets/chengdu-mianyang.json").readText() + chapterCandidates(xindu).text()
        listOf("杨升庵", "杨慎", "桂湖", "宝光寺", "新繁东湖").forEach { assertFalse(it in production) }
        listOf("第一次来", "自成一体", "陌生人物", "新角度", "Local Dossier").forEach { assertTrue(it in DirectorContract.constitution) }
        assertFalse(LocalResearchContract.instructions.contains("改变态度"))
    }
    @Test fun researchSseKeepsToolItemsCitationsAndDoesNotUseDirectorParser() {
        val call = """{"type":"web_search_call","status":"completed","action":{"sources":[{"url":"$url"}]}}"""
        val events = "data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":$call}\n\n" +
            "data: {\"type\":\"response.output_text.delta\",\"delta\":${JsonPrimitive(raw())}}\n\n" +
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n"
        val result = ChatGptStream.read(events.reader().buffered(), research = true)
        assertEquals(1, LocalResearchContract.response(result, area, 1).facts.size)
        assertTrue(runCatching { ChatGptStream.read(events.reader().buffered()) }.isFailure)
    }
}
