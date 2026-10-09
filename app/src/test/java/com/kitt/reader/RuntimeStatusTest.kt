package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeStatusTest {
    private val config = ProviderConfig(ProviderKind.CHATGPT)
    private val area = AreaIdentity("测试市", "测试区")
    private val wall = 1_000_000L
    private fun journey(voice: TestVoice = TestVoice()) = Journey({ wall }, voice).also {
        it.start(); it.location(Fix(30.0, 104.0, wall, accuracy = 8.0, administrative = area))
    }
    private fun activity(journey: Journey, status: RuntimeRequests, elapsed: Long = 0,
        voice: VoiceDetail = VoiceDetail(), pendingArea: Long? = null) = runtimeActivity(journey,
        status.work, voice, elapsed, wall, area.key, pendingArea, status.ai, status.research)
    private fun status(journey: Journey, clock: () -> Long = { 0 }) = RuntimeRequests(clock) { journey.epoch }
    private fun slow(delayMs: Long) = object : LocalResearchProvider {
        override val supportsTopics = true
        override suspend fun research(area: AreaIdentity, at: Long): LocalDossier {
            delay(delayMs); return testDossier(area, at)
        }
        override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long) = research(area, at)
        override suspend fun researchQuestion(query: LocalQuestion, at: Long) = research(query.area, at)
    }
    @Test fun untestedAndDemoCannotBecomeSuccessfulService() = runTest {
        val j = journey(); val s = status(j)
        assertEquals("未验证", observationLabel(s.ai, 0))
        ObservedDirector(FakeProvider(), ProviderConfig(ProviderKind.FAKE), s).direct(DirectorRequest(contextCard = ""))
        assertNull(s.ai); assertTrue(s.work.isEmpty())
    }
    @Test fun successfulCallAndInvalidReplyAreObservedSeparately() = runTest {
        val j = journey(); val s = status(j)
        ObservedDirector(DirectorProvider { DirectorResult(Action.SILENT).json() }, config, s).direct(DirectorRequest(contextCard = ""))
        assertEquals(RequestOutcome.SUCCESS, s.ai?.outcome)
        ObservedDirector(DirectorProvider { "invalid secret payload" }, config, s).direct(DirectorRequest(contextCard = ""))
        assertEquals(RequestOutcome.INVALID_RESPONSE, s.ai?.outcome)
        assertFalse(s.ai.toString().contains("secret")); assertTrue(s.work.isEmpty())
    }
    @Test fun failedRequestClearsInflightAndDoesNotInventVpnFailure() = runTest {
        val j = journey(); val s = status(j)
        val observed = ObservedDirector(DirectorProvider { error("secret header") }, config, s)
        assertTrue(runCatching { observed.direct(DirectorRequest(contextCard = "")) }.isFailure)
        assertTrue(s.work.isEmpty()); assertEquals(RequestOutcome.FAILED, s.ai?.outcome)
        assertEquals("上次请求失败，等待下次触发", activity(j, s).first)
    }
    @Test fun overviewPastThirtySecondsHasElapsedButDirectorStillRuns() = runTest {
        val j = journey(); val s = status(j) { testScheduler.currentTime }
        val research = ChapterResearch(backgroundScope, { wall + testScheduler.currentTime },
            { ObservedResearch(slow(60_000), config, s) })
        research.enter(area); runCurrent(); advanceTimeBy(38_000); runCurrent()
        assertEquals("正在搜索本地概况 · 已等待 38 秒", activity(j, s, 38_000).first)
        val director = ObservedDirector(DirectorProvider { DirectorResult(Action.SILENT).json() }, config, s)
        director.direct(DirectorRequest(contextCard = research.card()))
        assertTrue(research.pending); assertEquals(RequestOutcome.SUCCESS, s.ai?.outcome)
        assertTrue(j.running); research.clear(); runCurrent(); assertTrue(s.work.isEmpty())
    }
    @Test fun researchTimeoutClearsSearchAndRetainsFailure() = runTest {
        val j = journey(); val s = status(j) { testScheduler.currentTime }
        val research = ChapterResearch(this, { wall + testScheduler.currentTime }, { ObservedResearch(slow(100_000), config, s) })
        research.enter(area); advanceUntilIdle()
        assertFalse(research.pending); assertTrue(s.work.isEmpty())
        assertEquals(RequestOutcome.TIMEOUT, s.research?.outcome)
    }
    @Test fun cancellationIsNotAnOutageAndCannotClearReplacementWork() = runTest {
        val j = journey(); val s = status(j) { testScheduler.currentTime }
        val old = launch { s.observe(config, RuntimeTask.DECIDING, true) { awaitCancellation() } }
        runCurrent(); old.cancel()
        val fresh = launch { s.observe(config, RuntimeTask.QUESTION, true, searching = true) { delay(2000) } }
        runCurrent(); assertNull(s.ai); assertEquals(RuntimeTask.QUESTION, s.work.single().task)
        fresh.cancel(); runCurrent(); assertTrue(s.work.isEmpty()); assertNull(s.research)
    }
    @Test fun topicInterruptClearsImmediatelyAndActiveQuestionWins() = runTest {
        val j = journey(); val s = status(j) { testScheduler.currentTime }
        val r = ChapterResearch(backgroundScope, { wall }, { ObservedResearch(slow(5000), config, s) })
        r.enter(area); runCurrent(); advanceTimeBy(5000); runCurrent()
        r.deepen(); runCurrent(); assertEquals(RuntimeTask.TOPIC, s.work.single().task)
        r.cancelTopic()
        val job = launch { s.observe(config, RuntimeTask.QUESTION, true, searching = true) { awaitCancellation() } }
        runCurrent(); assertEquals("正在搜索你的问题 · 已等待 0 秒", activity(j, s, 5000).first)
        assertTrue(s.work.none { it.task == RuntimeTask.TOPIC }); job.cancel(); r.clear(); runCurrent()
    }
    @Test fun departedAreaOverviewCanContinueButCannotLookLikeCurrentWork() = runTest {
        val j = journey(); val s = status(j)
        val job = launch { s.observe(config, RuntimeTask.OVERVIEW, false, searching = true,
            areaKey = area.copy(district = "旧区").key) { awaitCancellation() } }
        runCurrent(); assertEquals("正常运行，等待值得讲的新内容", activity(j, s).first)
        job.cancel(); runCurrent()
    }
    @Test fun oldActiveChainKeepsTicketOwnerAfterSkipEvenIfNextStageStartsLate() = runTest {
        val j = journey(); val s = status(j)
        val ticket = j.ticket(true)
        val job = launch { withContext(RuntimeRequestOwner(ticket.epoch)) {
            delay(1000); s.observe(config, RuntimeTask.DECIDING, true) { awaitCancellation() }
        } }
        runCurrent(); j.skip(); advanceTimeBy(1000); runCurrent()
        assertEquals(ticket.epoch, s.work.single().epoch)
        assertFalse(activity(j, s).first.contains("AI 正在")); job.cancel(); runCurrent()
    }
    @Test fun quietAndListeningOverrideBackgroundWork() = runTest {
        val j = journey(); val s = status(j)
        val job = launch { s.observe(config, RuntimeTask.OVERVIEW, false, searching = true, areaKey = area.key) { awaitCancellation() } }
        runCurrent(); j.quiet(); assertTrue(activity(j, s).first.startsWith("安静模式"))
        j.resume(); j.beginListening {}
        assertEquals("正在准备听你说话", activity(j, s, voice = VoiceDetail(VoicePhase.PREPARING_LISTEN)).first)
        assertEquals("正在识别你的话", activity(j, s, voice = VoiceDetail(VoicePhase.PROCESSING)).first)
        assertTrue(activity(j, s).second.contains("搜索本地概况")); job.cancel(); runCurrent()
    }
    @Test fun speechQueuedAndPlaybackStartAreDistinctAndBackgroundIsSecondary() = runTest {
        val j = journey(); val s = status(j)
        val job = launch { s.observe(config, RuntimeTask.TOPIC, false, searching = true, areaKey = area.key) { awaitCancellation() } }
        runCurrent(); j.deliver(j.ticket(true), DirectorResult(Action.SPEAK_NOW, "主题", "内容").json())
        assertEquals("正在准备语音", activity(j, s, voice = VoiceDetail(VoicePhase.PREPARING_SPEECH)).first)
        assertEquals("正在讲述", activity(j, s, voice = VoiceDetail(VoicePhase.SPEAKING)).first)
        assertTrue(activity(j, s).second.contains("研究当地专题")); job.cancel(); runCurrent()
    }
    @Test fun prepareNeverClaimsNarrationReadyAndCooldownIsIntentional() {
        val v = TestVoice(); val j = journey(v); val s = status(j)
        j.deliver(j.ticket(false), DirectorResult(Action.PREPARE, "目标", prepareHint = "等待接近").json())
        assertEquals("已有准备线索，等待新现场确认", activity(j, s).first)
        j.skip(); assertEquals("刚刚讲过或已接管，暂时等待新内容", activity(j, s).first)
    }
    @Test fun noFixAndAreaLookupHaveIndependentAcquisitionLabels() {
        val j = Journey({ wall }, TestVoice()); val s = status(j)
        assertEquals("开车上路后点一下开始", activity(j, s).first)
        j.start(); assertEquals("正在获取位置", activity(j, s).first)
        fun withoutPermission() = runtimeActivity(j, emptyList(), VoiceDetail(), 0, wall,
            null, null, null, null, locationPermissionUnavailable = true).first
        assertEquals("定位权限不可用", withoutPermission())
        j.quiet(); assertTrue(withoutPermission().startsWith("安静模式"))
        j.resume(); j.beginListening {}; assertEquals("正在听，请说话", withoutPermission())
        j.cancelReply()
        j.location(Fix(30.0, 104.0, wall, accuracy = 300.0))
        assertEquals("正在识别所在地区 · 已等待 5 秒", activity(j, s, 5000, pendingArea = 0).first)
    }
    @Test fun locationQualityFollowsSelectorFallbackRecoveryAndMonotonicAge() {
        val selector = PhysicalLocationSelector()
        val gps = Fix(30.0, 104.0, wall, accuracy = 8.0, elapsedRealtimeMs = 10_000)
        selector.offer(gps, wall, 10_000)
        assertEquals("GPS · ±8m · 1秒 · 精确", positionLabel(selector.select(wall, 10_000), wall - 999_000, 11_000))
        selector.offer(gps.copy(timeMs = wall + 2000, elapsedRealtimeMs = 12_000, source = FixSource.NETWORK, accuracy = 300.0), wall + 2000, 12_000)
        selector.disable(FixSource.GPS)
        assertTrue(positionLabel(selector.select(wall + 2000, 12_000), wall, 12_000).contains("NETWORK · ±300m · 0秒 · 粗略"))
        selector.offer(gps.copy(timeMs = wall + 4000, elapsedRealtimeMs = 14_000), wall + 4000, 14_000)
        assertTrue(positionLabel(selector.select(wall + 4000, 14_000), wall, 14_000).startsWith("GPS"))
        assertTrue(positionLabel(selector.select(wall + 20_000, 30_000), wall, 30_000).contains("沿用 GPS"))
        assertEquals("定位未知 · 等待可靠位置", positionLabel(gps, wall, 71_000))
    }
    @Test fun unavailableResearchDoesNotPretendAnAiConnectionWasTested() = runTest {
        val j = journey(); val s = status(j)
        runCatching { ObservedResearch(UnavailableResearch(), config, s).overview(area, wall) }
        assertNull(s.ai); assertEquals(RequestOutcome.UNAVAILABLE, s.research?.outcome); assertTrue(s.work.isEmpty())
    }
    @Test fun configurationChangeResetsEvidenceAndOldCompletionCannotRestoreIt() = runTest {
        val j = journey(); val s = status(j)
        val job = launch { s.observe(config, RuntimeTask.DECIDING, true) { delay(1000) } }
        runCurrent(); s.select(config.copy(model = "another"), false); advanceUntilIdle()
        assertNull(s.ai); assertTrue(s.work.isEmpty()); job.join()
    }
    @Test fun endAndRecoveryCannotRestorePendingLabelsOrAcceptOldCompletion() = runTest {
        val j = journey(); val s = status(j)
        val job = launch { s.observe(config, RuntimeTask.DECIDING, false) { delay(1000) } }
        runCurrent(); j.end(); s.clearWork(); j.restore(wall, "绵阳", "", emptyList(), 0)
        advanceUntilIdle(); assertTrue(s.work.isEmpty()); assertNull(s.ai)
        assertEquals("正在获取位置", activity(j, s).first); job.join()
    }
    @Test fun recentSuccessDoesNotEraseSeparateResearchFailure() = runTest {
        val j = journey(); val s = status(j)
        runCatching { s.observe(config, RuntimeTask.OVERVIEW, false, searching = true) { error("failed") } }
        s.observe(config, RuntimeTask.DECIDING, false) { "success" }
        assertEquals(RequestOutcome.SUCCESS, s.ai?.outcome); assertEquals(RequestOutcome.FAILED, s.research?.outcome)
        assertEquals("最近成功 2 分钟前", observationLabel(s.ai, 120_000))
    }
}
