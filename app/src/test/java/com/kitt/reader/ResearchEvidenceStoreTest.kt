package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Synthetic hosted-search envelopes and public URL shapes, never live network/content acceptance. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResearchEvidenceStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val district = AreaIdentity("成都市", "新都区", "", "四川省")
    private val town = district.copy(chapter = "新都街道")
    private val url = "https://www.sc.gov.cn/synthetic-test"
    private val title = "测试历史对象"
    private fun evidence(area: AreaIdentity = town, at: Long = 1000, sourceUrl: String = url): LocalDossier {
        val raw = buildJsonObject {
            put("orientation", "合成测试概况，不代表真实地方事实")
            put("sources", buildJsonArray { add(buildJsonObject {
                put("id", "s1"); put("title", "合成测试来源"); put("url", sourceUrl); put("kind", "OFFICIAL")
            }) })
            put("facts", buildJsonArray { add(buildJsonObject {
                put("title", title); put("summary", "用于验证证据缓存生命周期的合成稳定事实")
                put("family", "HISTORY"); put("salience", 4); put("confidence", "medium"); put("designation", "")
                put("source_ids", buildJsonArray { add("s1") })
            }) }); put("gaps", buildJsonArray { add("合成资料不能作为实际研究验收") })
        }
        val envelope = buildJsonObject {
            put("status", "completed"); put("output", buildJsonArray {
                add(buildJsonObject {
                    put("type", "web_search_call"); put("status", "completed")
                    put("action", buildJsonObject { put("sources", buildJsonArray { add(buildJsonObject { put("url", sourceUrl) }) }) })
                })
                add(buildJsonObject { put("type", "message"); put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "output_text"); put("text", raw.toString()) })
                }) })
            })
        }
        return LocalResearchContract.response(envelope.toString(), area, at)
    }
    private fun store(dir: File, scheduler: TestCoroutineScheduler, entries: Int = 128, bytes: Long = 2097152,
        entryBytes: Int = 40000, events: MutableList<String> = mutableListOf()) = ResearchEvidenceStore(dir,
        diagnostic = { events += it }, maxEntries = entries, maxBytes = bytes, maxEntryBytes = entryBytes,
        io = StandardTestDispatcher(scheduler))
    private fun provider(calls: MutableList<String>) = object : LocalResearchProvider {
        override val supportsTopics = true
        override suspend fun research(area: AreaIdentity, at: Long): LocalDossier { calls += "overview:${area.key}"; return evidence(area, at) }
        override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long): LocalDossier {
            calls += "topic:${area.key}"; return evidence(area, at).copy(facts = evidence(area, at).facts.map { it.copy(summary = "另一条合成专题稳定事实") })
        }
    }
    @Test fun districtAndTownSurviveJourneyEndAndNewStoreWithZeroRepeatCalls() = runTest {
        val dir = temporary.newFolder(); val calls = mutableListOf<String>(); val events = mutableListOf<String>()
        val first = ChapterResearch(this, { 1000 }, { provider(calls) }, evidenceStore = store(dir, testScheduler, events = events))
        first.enter(town); runCurrent(); assertEquals(2, calls.size)
        first.checked(); first.enter(null); first.enter(town); runCurrent(); assertEquals(2, calls.size)
        first.deepen(title); runCurrent(); assertEquals(3, calls.size); first.clear()
        val second = ChapterResearch(this, { 999999999999 }, { UnavailableResearch("网络拒绝") }, evidenceStore = store(dir, testScheduler, events = events))
        second.enter(town); runCurrent()
        assertEquals(3, calls.size); assertEquals(ResearchStatus.READY_UNCHECKED, second.status(town))
        assertTrue(second.opportunity); assertNotNull(second.overview(district)); assertNotNull(second.overview(town))
        assertNotNull(second.topic(town, "  $title  ")); assertTrue("另一条合成专题稳定事实" in second.card())
        assertTrue("本次未联网复查" in second.card()); assertFalse("；已实际搜索；" in second.card())
        assertEquals(1000, second.dossier(town)!!.searchedAt); second.checked(); assertFalse(second.opportunity)
        second.deepen(); runCurrent(); assertEquals(3, calls.size)
        assertEquals(3, events.count { "evidence_cache write" in it }); assertEquals(3, events.count { "evidence_cache hit" in it })
        File("build/acceptance/research-cache.txt").apply { parentFile?.mkdirs(); writeText(
            "Synthetic evidence, no real Provider/device network.\nFirst journey: district Overview=1 town Overview=1 Topic=1 writes=3\n" +
                "New ChapterResearch + new disk store, unavailable Provider: district Overview=0 town Overview=0 Topic=0 hits=3\n" +
                "Opportunity READY_UNCHECKED; original searched_at=1000; historical label=true; no journey state persisted.\n") }
        second.clear()
    }
    @Test fun newDirectorActuallyJudgesCachedEvidenceAfterEndAndReconstruction() = runTest {
        val dir = temporary.newFolder(); val calls = mutableListOf<String>(); var judged = 0
        repeat(2) { pass ->
            val clock = { 1000000L + testScheduler.currentTime }; val journey = Journey(clock, TestVoice()); journey.start()
            val loop = DirectorLoop(journey, ContextPipeline(), this, clock, { DirectorProvider {
                if ("【Local Dossier】" in it.contextCard) judged++
                DirectorResult(Action.SILENT).json()
            } }, researchProvider = { if (pass == 0) provider(calls) else UnavailableResearch() }, evidenceStore = store(dir, testScheduler))
            loop.location(Fix(30.0, 104.0, clock(), administrative = town)); runCurrent()
            assertFalse(loop.research.pending); assertEquals(ResearchStatus.CHECKED, loop.research.status(town))
            loop.reset(); journey.end()
        }
        assertEquals(2, calls.size); assertEquals(2, judged)
    }
    @Test fun topicNormalizationAndFullIdentityNeverCollide() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler); var calls = 0
        s.topic(town, " Ａ   对象 ") { calls++; evidence() }
        s.topic(town, "a 对象") { error("cache miss") }
        for (area in listOf(district, town.copy(province = "其他省"), town.copy(city = "其他市"), town.copy(district = "其他区"), town.copy(chapter = "新都镇"))) {
            s.topic(area, "a 对象") { calls++; evidence(area) }
        }
        assertEquals(6, calls)
    }
    @Test fun concurrentStoresAndManagersCoalesceSameOverviewAndTopic() = runTest {
        val dir = temporary.newFolder(); var calls = 0
        val a = store(dir, testScheduler); val b = store(dir, testScheduler)
        val tasks = (1..20).map { async {
            (if (it % 2 == 0) a else b).overview(town) { calls++; delay(1000); OverviewDossier.from(evidence()) }
        } }; advanceUntilIdle(); tasks.awaitAll(); assertEquals(1, calls)
        val topics = (1..20).map { async { b.topic(town, title) { calls++; delay(1000); evidence() } } }
        advanceUntilIdle(); topics.awaitAll(); assertEquals(2, calls); assertEquals(2, dir.listFiles()!!.size)
    }
    @Test fun cancellationReleasesSingleFlightAndDoesNotSaveLateContent() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler); var calls = 0
        val cancelled = launch { s.overview(town) { calls++; delay(1000); OverviewDossier.from(evidence()) } }
        runCurrent(); cancelled.cancel(); runCurrent()
        s.overview(town) { calls++; OverviewDossier.from(evidence()) }; assertEquals(2, calls)
        assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun emptyUnverifiedFakeWrongIdentityAndInvalidFactsNeverSave() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler)
        val base = evidence()
        for (bad in listOf(base.copy(verifiedSearch = false), base.copy(facts = emptyList()), base.copy(sources = emptyList()),
            base.copy(area = district), base.copy(searchedAt = 0), evidence(sourceUrl = "https://example.test/fake"),
            base.copy(facts = base.facts.map { it.copy(sources = emptyList()) }),
            base.copy(facts = base.facts.map { it.copy(confidence = "low") }),
            base.copy(facts = base.facts.map { it.copy(salience = 99) }))) {
            assertSame(bad, s.topic(town, title) { bad }); assertTrue(dir.listFiles()!!.isEmpty())
        }
    }
    @Test fun providerExceptionCannotCreateOrOverwriteEvidence() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler)
        assertTrue(runCatching { s.overview(town) { error("network denied") } }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
        s.overview(town) { OverviewDossier.from(evidence()) }
        assertTrue(s.overview(town) { error("must not search") }.historical)
    }
    @Test fun oldEmptyDamagedAndAlteredFilesSafelyResearchAgain() = runTest {
        val dir = temporary.newFolder(); var calls = 0
        val s = store(dir, testScheduler)
        suspend fun seed() = s.overview(town) { calls++; OverviewDossier.from(evidence()) }
        seed()
        for (damage in listOf<(String) -> String>({ "" }, { "{" }, { it.replace("\"version\":1", "\"version\":0") },
            { it.replace("合成测试概况", "被改写的概况") }, { it.replace("新都街道", "错误街道") })) {
            val file = dir.listFiles()!!.single(); file.writeText(damage(file.readText()))
            seed()
        }
        assertEquals(6, calls); assertTrue(s.overview(town) { error("miss") }.historical)
    }
    @Test fun interruptedAtomicReplaceKeepsPriorFileAndIgnoresOrphanTemporary() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler)
        s.overview(district) { OverviewDossier.from(evidence(district)) }; val prior = dir.listFiles()!!.single().readBytes()
        val broken = ResearchEvidenceStore(dir, io = StandardTestDispatcher(testScheduler), replace = { _, _ -> error("power loss before rename") })
        assertFalse(broken.overview(town) { OverviewDossier.from(evidence()) }.historical)
        assertArrayEquals(prior, dir.listFiles()!!.single().readBytes())
        File(dir, "orphan.json.new").writeText("unfinished")
        s.overview(town) { OverviewDossier.from(evidence()) }
        assertTrue(s.overview(district) { error("lost prior file") }.historical)
    }
    @Test fun unavailableStorageDoesNotBlockReadyEvidenceOrDirector() = runTest {
        val dir = temporary.newFile(); val calls = mutableListOf<String>()
        val manager = ChapterResearch(this, { 1000 }, { provider(calls) }, evidenceStore = store(dir, testScheduler))
        manager.enter(town); runCurrent(); assertTrue(manager.opportunity); assertNotNull(manager.dossier(town))
        assertEquals(2, calls.size); manager.clear()
    }
    @Test fun entryAndTotalCapacityBoundariesEvictWithoutRequestStorms() = runTest {
        val dir = temporary.newFolder(); val tiny = store(dir, testScheduler, entryBytes = 10)
        tiny.overview(town) { OverviewDossier.from(evidence()) }; assertTrue(dir.listFiles()!!.isEmpty())
        val count = store(dir, testScheduler, entries = 2)
        repeat(4) { n -> val area = town.copy(chapter = "镇$n"); count.overview(area) { OverviewDossier.from(evidence(area)) } }
        assertEquals(2, dir.listFiles()!!.size)
        val sampleBytes = dir.listFiles()!!.first().length()
        val bytes = store(dir, testScheduler, bytes = sampleBytes + 30)
        bytes.overview(town) { OverviewDossier.from(evidence()) }
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= sampleBytes + 30)
    }
    @Test fun invalidationIsExplicitAndFailedRefreshStaysHonest() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler); s.overview(town) { OverviewDossier.from(evidence()) }
        s.invalidate(town)
        assertTrue(runCatching { s.overview(town) { error("no realtime service") } }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
    }
    @Test fun simulationAndFakeModeBypassPersistentReadsAndWrites() = runTest {
        val dir = temporary.newFolder(); val calls = mutableListOf<String>(); val s = store(dir, testScheduler)
        s.overview(town) { OverviewDossier.from(evidence()) }; val before = dir.listFiles()!!.single().readBytes()
        val manager = ChapterResearch(this, { 1000 }, { provider(calls) }, evidenceStore = s, persistentAllowed = { false })
        manager.enter(town); runCurrent(); assertEquals(2, calls.size)
        assertFalse(manager.overview(town)!!.historical); manager.clear()
        assertEquals(1, dir.listFiles()!!.size); assertArrayEquals(before, dir.listFiles()!!.single().readBytes())
    }
    @Test fun incompleteRegionAndCoarseLocationNeverReuseOldTownEvidence() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler); val calls = mutableListOf<String>()
        s.overview(town) { OverviewDossier.from(evidence()) }
        s.overview(town.copy(province = "")) { OverviewDossier.from(evidence(town.copy(province = ""))) }
        assertEquals(1, dir.listFiles()!!.size)
        var time = 1000000L; val clock = { time }; val journey = Journey(clock, TestVoice()); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), this, clock, { DirectorProvider { DirectorResult(Action.SILENT).json() } },
            researchProvider = { provider(calls) }, evidenceStore = s)
        // Use the production enrichment cache; raw injected labels bypass RealLocationSource's coarse guard.
        val resolution = AreaResolutionCache()
        val fine = Fix(30.0, 104.0, clock(), accuracy = 30.0)
        resolution.cached(fine, clock()); assertTrue(resolution.success(fine, town, resolution.revision, clock()))
        val coarse = fine.copy(accuracy = 1000.0)
        loop.location(coarse.copy(administrative = resolution.cached(coarse, clock()))); runCurrent()
        assertEquals(district, loop.research.active); assertFalse("新都街道；此前" in loop.research.card())
        time += 15001; assertNull(resolution.cached(fine, clock()))
        time++; val fresh = fine.copy(timeMs = clock())
        loop.location(fresh.copy(administrative = resolution.cached(fresh, clock())))
        runCurrent(); assertNull(loop.research.active); assertEquals("", loop.research.card())
        loop.reset(); journey.end()
    }
    @Test fun realtimeAndExplicitRefreshBypassCacheAndDoNotPersistDynamicAnswer() = runTest {
        val dir = temporary.newFolder(); val s = store(dir, testScheduler)
        s.overview(district) { OverviewDossier.from(evidence(district)) }; s.overview(town) { OverviewDossier.from(evidence()) }
        val before = dir.listFiles()!!.associate { it.name to it.readText() }; var searches = 0; var decisions = 0
        val clock = { 1000000L }; val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        val p = object : LocalResearchProvider {
            override suspend fun research(area: AreaIdentity, at: Long): LocalDossier = error("overview repeated")
            override suspend fun researchQuestion(query: LocalQuestion, at: Long): LocalDossier {
                searches++; if (query.question.contains("天气")) throw ResearchUnavailable("实时网络拒绝")
                return evidence(query.area).copy(orientation = "合成实时答案，不持久化")
            }
        }
        val director = object : DirectorProvider {
            override suspend fun researchNeed(request: DirectorRequest): ResearchNeed { decisions++; return ResearchNeed.USE_CONTEXT }
            override suspend fun direct(request: DirectorRequest) = DirectorResult(Action.SILENT).json()
        }
        val loop = DirectorLoop(journey, ContextPipeline(), this, clock, { director }, researchProvider = { p }, evidenceStore = s)
        loop.location(Fix(30.0, 104.0, clock(), administrative = town)); runCurrent()
        val questions = listOf("查最新", "重新研究", "重新搜索", "今天开放吗", "票价多少", "天气如何", "路况", "临时展览", "最近时事")
        questions.forEach { loop.user(it); runCurrent(); if (journey.speaking) voice.finish() }
        assertEquals(questions.size, searches); assertEquals(0, decisions)
        assertTrue("刚才没查到可靠资料，暂时无法确认。" in voice.speech)
        assertEquals(before, dir.listFiles()!!.associate { it.name to it.readText() }); loop.reset(); journey.end()
    }
    @Test fun filesContainOnlyFactsAndSourcesAndDiagnosticsContainNoIdentityOrUrl() = runTest {
        val dir = temporary.newFolder(); val events = mutableListOf<String>(); val s = store(dir, testScheduler, events = events)
        s.overview(town) { OverviewDossier.from(evidence()) }; s.overview(town) { error("miss") }
        val raw = dir.listFiles()!!.single().readText()
        for (secret in listOf("api_key", "access_token", "user_utterance", "latitude", "longitude", "pending", "examined", "narration", "audio")) assertFalse(secret, secret in raw)
        assertFalse(events.any { town.district in it || url in it }); assertTrue(events.any { "hit" in it })
    }
}
