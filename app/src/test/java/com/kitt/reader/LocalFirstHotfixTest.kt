package com.kitt.reader

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalFirstHotfixTest {
    private val root = File("src/main/assets/offline")
    private val pack = OfflinePack.parse(File(root, "xindu-manifest.json").readBytes(),
        File(root, "xindu-facts.json").readBytes(), File(root, "xindu-geometry.geojson").readBytes())
    private var time = 1_000_000L
    private val voice = TestVoice()
    private val journey = Journey({ time }, voice)
    private fun fix() = Fix(30.823, 104.16, time, accuracy = 10.0, source = FixSource.GPS, administrative = pack.area)
    private fun loop(scope: CoroutineScope, online: () -> Boolean = { true },
        provider: () -> DirectorProvider = { error("Unexpected cloud Director") },
        research: () -> LocalResearchProvider = { error("Unexpected cloud research") },
        installed: () -> OfflinePack? = { pack }) = DirectorLoop(journey, ContextPipeline(), scope, { time },
        provider, researchProvider = research, online = online, offlinePack = installed, preferOffline = { true })

    @Test fun onlineOfflineAndUnreachableCloudAllSpeakSynchronouslyWithZeroRequests() = runTest {
        for (connected in listOf(true, false, true)) {
            var selections = 0
            val l = loop(this, { connected }, { selections++; error("ChatGPT unreachable") },
                { selections++; error("Research unreachable") })
            journey.start(); l.location(fix())
            // No coroutine/network timeout needs to run before local speech is submitted.
            assertTrue(journey.speaking); assertEquals(0, selections)
            assertEquals(0, l.counters.dispatched); assertFalse(l.research.pending)
            voice.finish(); runCurrent(); assertEquals(0, selections)
            journey.end(); l.reset()
        }
    }
    @Test fun unknownStaleCoarseBridgeAndUncoveredPositionsStaySilentOnline() = runTest {
        val l = loop(this)
        val positions = listOf(null, fix().copy(timeMs = time - 15_001), fix().copy(accuracy = 51.0),
            fix().copy(source = FixSource.LAST_KNOWN), fix().copy(latitude = 30.65))
        for (position in positions) {
            journey.start(); if (position != null) l.location(position) else l.check()
            runCurrent(); assertTrue(voice.speech.isEmpty()); assertEquals(0, l.counters.dispatched)
            journey.end(); l.reset()
        }
    }
    @Test fun missingPackNeverFallsBackToAutomaticCloud() = runTest {
        val l = loop(this, installed = { null })
        journey.start(); l.location(fix()); runCurrent()
        assertTrue(voice.speech.isEmpty()); assertEquals(0, l.counters.dispatched)
        journey.end(); l.reset()
    }
    @Test fun localHereMoreAndReplayStayLocalOnlineAndPreserveFactCoverage() = runTest {
        val l = loop(this)
        journey.start(); l.location(fix()); val first = voice.speech.last(); voice.finish()
        l.user("再讲一点"); val more = voice.speech.last(); voice.finish()
        assertNotEquals(first, more); assertTrue(more.contains("还有两点"))
        l.user("再说一遍"); assertEquals(more, voice.speech.last()); voice.finish()
        l.user("这里有什么？"); voice.finish(); runCurrent()
        assertEquals(0, l.counters.dispatched)
        journey.end(); l.reset()
    }
    @Test fun openQuestionUsesCloudOnlyOnDemandAndNextAutomaticStoryRemainsLocal() = runTest {
        var calls = 0
        val l = loop(this, provider = { DirectorProvider { calls++; DirectorResult(Action.SPEAK_NOW, "回答", "一般解释").json() } })
        journey.start(); l.location(fix()); voice.finish(); assertEquals(0, calls)
        l.user("为什么天空是蓝色？"); runCurrent(); assertEquals(1, calls); voice.finish()
        time += 46_000; l.location(fix().copy(latitude = 30.84)); runCurrent()
        // 1.9km within three minutes is still below the existing 3km speech cadence.
        assertFalse(journey.speaking); assertEquals(1, calls)
        time += 135_000; l.location(fix().copy(latitude = 30.84)); runCurrent()
        assertTrue(journey.speaking); assertTrue(voice.speech.last().contains("新都区")); assertEquals(1, calls)
        journey.end(); l.reset()
    }
    @Test fun explicitWeatherSearchFailureReportsBrieflyThenLocalStoryRecovers() = runTest {
        var searches = 0; var directors = 0
        val l = loop(this, provider = { DirectorProvider { directors++; error("Failed search must not reach Director") } },
            research = { object : LocalResearchProvider {
                override suspend fun research(area: AreaIdentity, at: Long): LocalDossier = error("No automatic overview")
                override suspend fun researchQuestion(query: LocalQuestion, at: Long): LocalDossier {
                    searches++; error("Network unavailable")
                }
            } })
        journey.start(); l.location(fix()); voice.finish()
        l.user("查询今天天气"); runCurrent()
        assertEquals(1, searches); assertEquals(0, directors)
        assertEquals("刚才没查到可靠资料，暂时无法确认。", voice.speech.last()); voice.finish()
        time += 181_000; l.location(fix().copy(latitude = 30.84)); runCurrent()
        assertTrue(voice.speech.last().contains("新都区")); assertTrue(journey.speaking)
        assertEquals(1, searches); assertEquals(0, directors)
        journey.end(); l.reset()
    }
    @Test fun explicitCloudTimeoutEndsAndCannotBlockSubsequentLocalNarration() = runTest {
        var calls = 0
        val l = loop(this, provider = { DirectorProvider { calls++; awaitCancellation() } })
        journey.start(); l.location(fix()); voice.finish()
        l.user("解释一下彩虹"); runCurrent(); assertTrue(l.pending)
        advanceTimeBy(35_001); time += 35_001; runCurrent()
        assertFalse(l.pending); assertEquals("刚才没连上，稍后再试。", voice.speech.last()); voice.finish()
        time += 181_000; l.location(fix().copy(latitude = 30.84)); runCurrent()
        assertTrue(journey.speaking); assertTrue(voice.speech.last().contains("新都区")); assertEquals(1, calls)
        journey.end(); l.reset()
    }
    @Test fun localQuestionCancelsOldCloudAnswerAndLateResponseNeverReplays() = runTest {
        var calls = 0
        val l = loop(this, provider = { DirectorProvider {
            calls++
            withContext(NonCancellable) { delay(1000) }
            DirectorResult(Action.SPEAK_NOW, "旧云内容", "不可补播的旧回答").json()
        } })
        journey.start(); l.location(fix()); voice.finish()
        l.user("解释彩虹"); runCurrent(); assertTrue(l.pending)
        l.user("再讲一点"); val local = voice.speech.last(); assertFalse(l.pending)
        advanceTimeBy(1001); runCurrent()
        assertEquals(local, voice.speech.last()); assertFalse(voice.speech.any { it.contains("旧回答") })
        assertEquals(1, calls); journey.end(); l.reset()
    }
    @Test fun networkRestorationDoesNotInterruptLocalSpeechOrStartResearch() = runTest {
        var connected = false; val l = loop(this, { connected })
        journey.start(); l.location(fix()); val stops = voice.stops
        connected = true; l.check(); runCurrent()
        assertTrue(journey.speaking); assertEquals(stops, voice.stops); assertFalse(l.research.pending)
        connected = false; l.check(); runCurrent()
        assertTrue(journey.speaking); assertEquals(stops, voice.stops)
        journey.end(); l.reset()
    }
    @Test fun disconnectCancelsExplicitCloudWithoutRestartingItOnReconnect() = runTest {
        var connected = true; var calls = 0; var cancelled = 0
        val l = loop(this, { connected }, { DirectorProvider {
            calls++; try { awaitCancellation() } finally { cancelled++ }
        } })
        journey.start(); l.location(fix()); voice.finish()
        l.user("解释彩虹"); runCurrent()
        connected = false; l.check(); runCurrent(); assertFalse(l.pending); assertEquals(1, cancelled)
        connected = true; l.check(); runCurrent(); assertEquals(1, calls)
        time += 181_000; l.location(fix().copy(latitude = 30.84)); runCurrent()
        assertTrue(journey.speaking); assertEquals(1, calls); assertFalse(l.research.pending)
        journey.end(); l.reset()
    }
    @Test fun onlineButStaleCloudFailureDoesNotMaskLocalHealthOrUnknownLocation() {
        journey.start(); journey.location(fix())
        val failed = RequestObservation(RequestOutcome.TIMEOUT, 0)
        fun activity() = runtimeActivity(journey, emptyList(), VoiceDetail(), 0, time,
            pack.area.key, null, failed, failed, offlineFirst = true).first
        assertEquals("本地选题待命 · 等待适合内容", activity())
        time += 60_001; assertTrue(activity().startsWith("位置已过时"))
    }
}
