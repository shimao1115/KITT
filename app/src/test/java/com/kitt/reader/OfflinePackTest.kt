package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePackTest {
    private val root = File("src/main/assets/offline")
    private val manifest get() = File(root, "xindu-manifest.json").readBytes()
    private val facts get() = File(root, "xindu-facts.json").readBytes()
    private val geometry get() = File(root, "xindu-geometry.geojson").readBytes()
    private fun pack() = OfflinePack.parse(manifest, facts, geometry)
    private var time = 1_000_000L
    private fun fix(source: FixSource = FixSource.GPS) = Fix(30.823, 104.16, time, accuracy = 10.0, source = source)
    private val voice = TestVoice()
    private val journey = Journey({ time }, voice)
    private fun begin() { journey.start(); journey.location(fix().copy(administrative = pack().area)) }
    private fun send(narrator: OfflineNarrator, text: String? = null): DirectorResult {
        if (text != null) assertTrue(journey.requestInput(text))
        val result = narrator.response(journey, time, text)
        journey.deliver(journey.ticket(text != null), result.json(), narrationArea = pack().area)
        return result
    }

    @Test fun actualPackageHas22UniqueTraceableFactsAcrossSixObjects() {
        val p = pack(); assertEquals(6, p.objects.size); assertEquals(22, p.objects.sumOf { it.facts.size })
        assertEquals(22, p.objects.flatMap { it.facts }.map { it.id }.distinct().size)
        assertTrue(p.objects.flatMap { it.facts }.all { f -> f.sourceIds.all { id -> p.sources.any { it.id == id } } })
        assertTrue(p.objects.map { it.family }.distinct().size >= 4)
    }
    @Test fun realInteriorGpsAndNetworkResolveCountyWithoutStreets() {
        val p = pack()
        for (source in listOf(FixSource.GPS, FixSource.NETWORK)) {
            assertEquals("新都区", p.resolve(fix(source), time)?.district)
            assertEquals("", p.resolve(fix(source), time)?.chapter)
        }
    }
    @Test fun outsideAndTrueBoundaryVertexAreUnknown() {
        val p = pack()
        assertNull(p.resolve(fix().copy(latitude = 30.65, longitude = 104.06), time))
        val vertex = Json.parseToJsonElement(geometry.decodeToString()).jsonObject.getValue("geometry").jsonObject
            .getValue("coordinates").jsonArray[0].jsonArray[0].jsonArray
        assertNull(p.resolve(fix().copy(longitude = vertex[0].jsonPrimitive.double, latitude = vertex[1].jsonPrimitive.double), time))
    }
    @Test fun accuracyDiskAndOneKilometerBoundaryMarginAreConservative() {
        val p = pack()
        assertNull(p.resolve(fix().copy(accuracy = 51.0), time))
        assertNull(p.resolve(fix().copy(accuracy = 0.0), time))
        assertEquals(1000.0, p.uncertaintyMeters, 0.0)
    }
    @Test fun staleFutureBridgeSimulationFusedAndInvalidNeverResolve() {
        val p = pack()
        listOf(fix().copy(timeMs = time - 15001), fix().copy(timeMs = time + 1),
            fix(FixSource.LAST_KNOWN), fix(FixSource.SIMULATED), fix(FixSource.FUSED),
            fix().copy(latitude = Double.NaN)).forEach { assertNull(p.resolve(it, time)) }
        assertNull(p.resolve(null, time))
    }
    @Test fun corruptionOrEmptyContentFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) { OfflinePack.parse(manifest, facts + 32.toByte(), geometry) }
        assertThrows(IllegalArgumentException::class.java) { OfflinePack.parse(manifest, facts, geometry + 32.toByte()) }
        assertThrows(IllegalArgumentException::class.java) { OfflinePack.parse(manifest, byteArrayOf(), geometry) }
    }
    @Test fun futureSchemaOrAppVersionFailsClosed() {
        for (replacement in listOf(manifest.decodeToString().replace("\"schema_version\": 1", "\"schema_version\": 99"),
                manifest.decodeToString().replace("\"minimum_code\": 14", "\"minimum_code\": 999"))) {
            assertThrows(IllegalArgumentException::class.java) { OfflinePack.parse(replacement.encodeToByteArray(), facts, geometry) }
        }
    }
    @Test fun badReferencesFailEvenIfChecksumIsResealed() {
        val bad = facts.decodeToString().replace("\"source_ids\":[\"yang\"]", "\"source_ids\":[\"invented\"]").encodeToByteArray()
        val m = manifest.decodeToString().replace(OfflinePack.sha256(facts), OfflinePack.sha256(bad)).encodeToByteArray()
        assertThrows(IllegalArgumentException::class.java) { OfflinePack.parse(m, bad, geometry) }
    }
    @Test fun offlineDirectorSpeaksTwoGroundedFactsAndNextNewObject() {
        begin(); val narrator = OfflineNarrator(pack())
        val first = send(narrator); voice.finish()
        val next = send(narrator)
        assertEquals(2, first.memoryUpdate.split('；').size)
        assertNotEquals(first.topic, next.topic)
        assertEquals(first.topic, journey.narrationMemory.first().subject)
    }
    @Test fun allNewFactsEventuallyExhaustWithoutRepeat() {
        begin(); val narrator = OfflineNarrator(pack()); val spoken = mutableListOf<String>()
        repeat(15) {
            val r = send(narrator)
            if (r.action == Action.SPEAK_NOW) { spoken += r.memoryUpdate.split('；'); voice.finish() }
        }
        assertEquals(22, spoken.size); assertEquals(22, spoken.distinct().size)
        assertEquals(Action.SILENT, narrator.response(journey, time).action)
    }
    @Test fun moreStaysWithObjectThenHonestlyRunsOut() {
        begin(); val narrator = OfflineNarrator(pack()); val first = send(narrator); voice.finish()
        val more = send(narrator, "再讲一点"); voice.finish()
        assertEquals(first.topic, more.topic); assertNotEquals(first.memoryUpdate, more.memoryUpdate)
        assertTrue(send(narrator, "再讲一点").narration.contains("没有可靠的新内容"))
    }
    @Test fun failedOrInterruptedSegmentIsNeverCountedCompleteOrAutoReplayed() {
        begin(); val narrator = OfflineNarrator(pack()); val first = send(narrator); voice.done!!(false)
        assertTrue(journey.recentTopics.isEmpty())
        assertNotEquals(first.memoryUpdate, narrator.response(journey, time).memoryUpdate)
        send(narrator); val late = voice.done!!; journey.beginListening {}; late(true)
        assertFalse(journey.narrationMemory.any { it.phase == NarrationPhase.COMPLETED })
    }
    @Test fun newJourneyDoesNotInheritPackPlaybackMemory() {
        begin(); val n = OfflineNarrator(pack()); val first = send(n); voice.finish(); journey.end(); begin()
        assertEquals(first.memoryUpdate, n.response(journey, time).memoryUpdate)
    }
    @Test fun dynamicQuestionsAndUnsupportedImagesNeverInventAnswers() {
        begin(); val n = OfflineNarrator(pack())
        assertTrue(n.response(journey, time, "再说一遍").narration.contains("还没有可重播"))
        for (q in listOf("今天天气", "门票多少钱", "现在开放吗", "查询最新路况"))
            assertTrue(n.response(journey, time, q).narration.contains("无法确认最新状态"))
        assertTrue(n.response(journey, time, "写首诗").narration.contains("其他问题请联网"))
        assertTrue(n.response(journey, time, "看图", true).narration.contains("不能分析图片"))
    }
    @Test fun missingPackUnknownOrOldLocationNeverUsesHistoricalAreaLabel() {
        begin(); val p = pack(); val n = OfflineNarrator(p)
        journey.location(fix().copy(latitude = 30.65, administrative = p.area))
        assertEquals(Action.SILENT, n.response(journey, time).action)
        assertTrue(n.response(journey, time, "这里有什么").narration.contains("地区不确定"))
        assertEquals(Action.SILENT, OfflineNarrator(null).response(journey, time).action)
    }
    @Test fun localVoiceSelectionRejectsNetworkUninstalledAndCantoneseOnly() {
        val options = listOf(TtsVoiceOption("online", Locale.CHINA, true),
            TtsVoiceOption("missing", Locale.CHINA, false, false), TtsVoiceOption("local", Locale.CHINA, false))
        assertEquals("local", offlineChineseVoice("online", options, "online")?.name)
        assertNull(offlineChineseVoice("", options.take(2), null))
        assertNull(offlineChineseVoice("", listOf(TtsVoiceOption("yue", Locale.forLanguageTag("yue-HK"), false)), null))
    }
    @Test fun onlinePackDoesNotPretendToBeFreshHostedSearchOrPersistEvidence() = runTest {
        var calls = 0; val p = pack()
        val manager = ChapterResearch(this, { time }, { LocalResearchProvider { _, _ -> calls++; error("cloud") } }, curatedPack = { p })
        manager.enter(p.area); runCurrent()
        assertEquals(0, calls); assertTrue(manager.dossier(p.area)!!.historical)
        assertFalse(manager.dossier(p.area)!!.verifiedSearch)
        assertEquals(6, manager.completedTopics(p.area).size)
        assertTrue(p.card(p.area).contains("本次未联网")); assertEquals("", p.card(p.area.copy(district = "其他区")))
        manager.clear()
    }
    @Test fun offlineLoopHasZeroCloudCallsHonorsQuietInterruptionAndReplay() = runTest {
        var calls = 0; val p = pack(); val context = ContextPipeline()
        val loop = DirectorLoop(journey, context, this, { time }, { calls++; error("cloud director") },
            researchProvider = { calls++; error("cloud research") }, online = { false }, offlinePack = { p })
        journey.start(); loop.location(fix()); runCurrent(); assertEquals(0, calls); assertTrue(journey.speaking)
        val text = voice.speech.last(); voice.finish(); loop.user("再说一遍"); assertEquals(text, voice.speech.last())
        loop.speak(); assertTrue(journey.listening); assertFalse(journey.speaking)
        loop.user("安静十分钟"); assertTrue(journey.isQuiet)
        val count = voice.speech.size; loop.check(); assertEquals(count, voice.speech.size)
        journey.resume(); loop.check(); assertEquals(count, voice.speech.size)
        journey.end(); loop.reset(); assertEquals(0, calls)
    }
    @Test fun onlinePhonePrefersLocalAutomaticNarrationAndKnownLocalQuestion() = runTest {
        var cloudCalls = 0
        val p = pack()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time },
            { cloudCalls++; error("automatic cloud Director must not be used") },
            researchProvider = { cloudCalls++; error("automatic cloud research must not be used") },
            online = { true }, offlinePack = { p }, preferOffline = { true })
        journey.start()
        loop.location(fix())
        runCurrent()
        assertEquals(0, cloudCalls)
        assertTrue("Expected locally authored facts to be spoken without a cloud wait", journey.speaking)
        assertTrue(voice.speech.last().contains("新都"))
        voice.finish()
        loop.user("这里有什么")
        runCurrent()
        assertEquals(0, cloudCalls)
        assertTrue(voice.speech.size >= 2)
        loop.reset(); journey.end()
    }
    @Test fun onlinePhoneWithoutLocalCoverageDoesNotMakeAutomaticCloudRequest() = runTest {
        var cloudCalls = 0
        val p = pack()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time },
            { cloudCalls++; error("automatic cloud Director must not be used") },
            online = { true }, offlinePack = { p }, preferOffline = { true })
        journey.start()
        loop.location(fix().copy(latitude = 30.65))
        runCurrent()
        assertEquals(0, cloudCalls)
        assertTrue(voice.speech.isEmpty())
        loop.reset(); journey.end()
    }
    @Test fun disconnectCancelsInFlightCloudAndReconnectRestoresExistingOnlinePath() = runTest {
        var online = true; var cancelled = 0; var calls = 0
        val p = pack(); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider {
            calls++; try { delay(100000); DirectorResult(Action.SILENT).json() } finally { cancelled++ }
        } }, online = { online }, offlinePack = { p })
        loop.location(fix()); runCurrent(); assertEquals(1, calls)
        online = false; loop.check(); runCurrent(); assertEquals(1, cancelled)
        time += 46000; loop.location(fix().copy(latitude = 30.84)); runCurrent(); assertTrue(journey.speaking); voice.finish()
        online = true; loop.user("普通问题"); runCurrent(); assertTrue(calls >= 2)
        loop.reset(); journey.end(); runCurrent()
    }
}
