package com.kitt.reader

import kotlinx.serialization.json.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Test-only promotion proves runtime behavior, never approves the real source manuscripts. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoryPacksTest {
    private val assets = File("src/main/assets/offline")
    private val source = File("../content/yangshen")
    private fun read(name: String) = File(assets, name).readBytes()
    private fun facts() = Json.parseToJsonElement(File(source, "facts.json").readText()).jsonObject
    private fun candidates(): JsonObject {
        val n = Json.parseToJsonElement(File(source, "narratives.json").readText()).jsonObject
        return JsonObject(n + ("narratives" to JsonArray(n.getValue("narratives").jsonArray.map {
            JsonObject(it.jsonObject + mapOf("review_status" to JsonPrimitive("approved"), "fact_gate" to JsonPrimitive("passed"),
                "visitor_gate" to JsonPrimitive("passed"), "approval_ref" to JsonPrimitive("TEST_ONLY_NOT_CONTENT_APPROVAL")))
        })))
    }
    private fun seal(data: JsonObject): Pair<ByteArray, ByteArray> {
        val b = data.toString().encodeToByteArray()
        return buildJsonObject {
            put("schema_version", 1); put("minimum_code", 17); put("pack_id", data.getValue("pack_id"))
            put("content_version", data.getValue("content_version")); put("bytes", b.size); put("sha256", OfflinePack.sha256(b))
        }.toString().encodeToByteArray() to b
    }
    private fun parse(f: JsonObject = facts(), n: JsonObject = candidates()): StoryPacks {
        val (fm, fb) = seal(f); val (nm, nb) = seal(n)
        return StoryPacks.parse(fm, fb, nm, nb)
    }
    private fun mutate(data: JsonObject, array: String, index: Int = 0, change: (JsonObject) -> JsonObject) =
        JsonObject(data + (array to JsonArray(data.getValue(array).jsonArray.mapIndexed { i, v -> if (i == index) change(v.jsonObject) else v })))
    private fun pack() = OfflinePack.parse(read("xindu-manifest.json"), read("xindu-facts.json"), read("xindu-geometry.geojson"))
    private var time = 1_000_000L
    private val voice = TestVoice()
    private val journey = Journey({ time }, voice)
    private fun fix() = Fix(30.83, 104.16, time, accuracy = 10.0, source = FixSource.GPS)
    private fun begin() { journey.start(); journey.location(fix()) }
    private fun send(n: OfflineNarrator, text: String? = null): DirectorResult {
        val r = n.response(journey, time, text)
        val story = parse().stories.firstOrNull { it.marker == r.memoryUpdate.substringBefore('；') }
        journey.deliver(journey.ticket(text != null), r.json(), narrationArea = pack().area,
            recordNarration = r.topic != "离线资料", localStoryId = r.memoryUpdate.takeIf { it.startsWith("story:") }
                ?.substringBefore('；')?.removePrefix("story:"), summaryLabel = story?.title.orEmpty())
        return r
    }

    @Test fun productionHasNoApprovedStoryAndKeepsEveryLegacyFact() {
        val p = StoryPacks.parse(read("ys-facts-manifest.json"), read("ys-facts.json"), read("ys-narratives-manifest.json"), read("ys-narratives.json"))
        assertTrue(p.stories.isEmpty()); assertEquals(6, pack().objects.size)
        assertEquals(22, pack().objects.sumOf { it.facts.size })
        val raw = Json.parseToJsonElement(File(source, "narratives.json").readText()).jsonObject
        assertTrue(parse(n = raw).stories.isEmpty())
        assertEquals(2, parse(n = raw).blocked.size)
    }
    @Test fun approvedFixtureParsesIndependentVersionsAndFullText() {
        val p = parse(); assertEquals(2, p.stories.size); assertTrue(p.blocked.isEmpty())
        // The equality below checks complete text; editorial duration is not a minimum length.
        assertEquals(StoryCategory.HISTORY, p.stories.first().category)
        assertEquals(candidates().getValue("narratives").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content, p.stories.first().text)
    }
    @Test fun corruptedBytesLengthSchemaAndFutureCodeRejectBeforeReadingContent() {
        val (fm, fb) = seal(facts()); val (nm, nb) = seal(candidates())
        for (m in listOf(nm.decodeToString().replace("\"schema_version\":1", "\"schema_version\":99"),
                nm.decodeToString().replace("\"minimum_code\":17", "\"minimum_code\":999"),
                nm.decodeToString().replace("\"bytes\":${nb.size}", "\"bytes\":1")))
            assertThrows(IllegalArgumentException::class.java) { StoryPacks.parse(fm, fb, m.encodeToByteArray(), nb) }
        assertThrows(IllegalArgumentException::class.java) { StoryPacks.parse(fm, fb + 32.toByte(), nm, nb) }
        assertThrows(IllegalArgumentException::class.java) { StoryPacks.parse(fm, fb, nm, nb + 32.toByte()) }
    }
    @Test fun textTamperingAndInvalidCategoriesDisableOnlyTheirStory() {
        val changes = listOf("text" to JsonPrimitive("伪造正文".repeat(100)), "primary_category" to JsonPrimitive("PEOPLE"),
            "secondary_categories" to JsonArray(listOf(JsonPrimitive("HISTORY"))), "approval_ref" to JsonPrimitive(""),
            "fact_gate" to JsonPrimitive("pending"), "visitor_gate" to JsonPrimitive("pending"),
            "license" to JsonPrimitive("unknown"), "entity_id" to JsonPrimitive("invented"))
        changes.forEach { (key, value) ->
            val p = parse(n = mutate(candidates(), "narratives") { JsonObject(it + (key to value)) })
            assertEquals(key, listOf("yangshen-followup-dianzai"), p.stories.map { it.id }); assertEquals(1, p.blocked.size)
        }
    }
    @Test fun missingOrChangedReferencedClaimDisablesMainButUnrelatedClaimDoesNot() {
        val f = facts()
        val changed = mutate(f, "claims", 0) { JsonObject(it + ("text" to JsonPrimitive("更正但尚未重审"))) }
        assertEquals(listOf("yangshen-followup-dianzai"), parse(f = changed).stories.map { it.id })
        val noIdentity = JsonObject(f + ("claims" to JsonArray(f.getValue("claims").jsonArray.drop(1))))
        assertEquals(1, parse(f = noIdentity).stories.size)
        val unrelated = mutate(f, "claims", 5) { JsonObject(it + ("text" to JsonPrimitive("无关卒年修订"))) }
        assertEquals(2, parse(f = unrelated).stories.size)
    }
    @Test fun sourceMissingDowngradedOrRightsChangedInvalidatesItsUsers() {
        val f = facts()
        val noTanci = JsonObject(f + ("sources" to JsonArray(f.getValue("sources").jsonArray.filter {
            it.jsonObject.getValue("source_id").jsonPrimitive.content != "tanci3"
        })))
        assertEquals(listOf("yangshen-followup-dianzai"), parse(f = noTanci).stories.map { it.id })
        for (key in listOf("rights", "registry_revision", "access_status")) {
            val changed = mutate(f, "sources", 2) { JsonObject(it + (key to JsonPrimitive("unknown"))) }
            assertEquals(1, parse(f = changed).stories.size)
        }
    }
    @Test fun nonVerifiedClaimCannotBeUsedEvenWhenContainerIsResealed() {
        for (status in listOf("lead", "contested", "rejected")) {
            val changed = mutate(facts(), "claims") { JsonObject(it + ("status" to JsonPrimitive(status))) }
            assertEquals(1, parse(f = changed).stories.size)
        }
    }
    @Test fun mainCompletesThenMoreHasNewFactsAndExhaustionDoesNotOverwriteReplay() {
        begin(); val n = OfflineNarrator(pack(), parse()); val main = send(n)
        assertNull(journey.replay); voice.finish(); assertEquals(main.narration, journey.replay!!.narration)
        assertEquals(listOf("从新都状元到云南远谪"), journey.summaryTopics)
        val next = n.response(journey, time)
        assertNotEquals(main.topic, next.topic) // other five legacy subjects remain eligible
        assertFalse(next.memoryUpdate.contains("桂湖为杨升庵故里纪念园林"))
        val more = send(n, "再讲一点"); assertTrue(more.memoryUpdate.contains("ys-dianzai"))
        assertNotEquals(main.narration, more.narration); voice.finish()
        send(n, "再讲一点"); voice.finish()
        assertEquals(more.narration, journey.replay!!.narration)
        assertEquals(2, journey.narrationMemory.size)
        assertFalse(journey.summaryTopics.any { "story:" in it || "claim:" in it })
    }
    @Test fun interruptionSkipQuietAndEndNeverCompleteOrReplayPartialStory() {
        for (cancel in listOf<(Journey) -> Unit>({ it.skip() }, { it.quiet() }, { it.beginListening {} }, { it.end() })) {
            begin(); val n = OfflineNarrator(pack(), parse()); val main = send(n); val late = voice.done!!
            cancel(journey); late(true); assertNull(journey.replay); assertTrue(journey.recentTopics.isEmpty())
            if (journey.running) {
                assertNotEquals(main.narration, n.response(journey, time).narration)
                assertFalse(n.response(journey, time, "再讲一点").memoryUpdate.contains("ys-dianzai"))
            }
            journey.end()
        }
    }
    @Test fun failedFollowupKeepsCompletedMainAsReplayAndNewJourneyResetsSelection() {
        begin(); val n = OfflineNarrator(pack(), parse()); val main = send(n); voice.finish()
        send(n, "再讲一点"); voice.done!!(false)
        assertEquals(main.narration, journey.replay!!.narration)
        journey.end(); begin(); assertEquals(main.narration, n.response(journey, time).narration)
    }
    @Test fun unknownCoarseStaleAndOutOfAreaRemainSilentWithApprovedFixture() {
        val n = OfflineNarrator(pack(), parse())
        for (bad in listOf(fix().copy(accuracy = 51.0), fix().copy(timeMs = time - 15001),
                fix().copy(source = FixSource.LAST_KNOWN), fix().copy(latitude = 30.65))) {
            journey.start(); journey.location(bad); assertEquals(Action.SILENT, n.response(journey, time).action)
        }
        journey.start(); assertEquals(Action.SILENT, n.response(journey, time).action)
    }
    @Test fun onlineOfflineAndUnconfiguredCloudAllDispatchCompleteFixtureLocally() = runTest {
        for (online in listOf(true, false)) {
            var calls = 0
            val p = pack(); val catalog = parse()
            val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { calls++; error("cloud unavailable/no account") },
                researchProvider = { calls++; error("cloud research forbidden") }, online = { online }, offlinePack = { p },
                preferOffline = { true }, storyPacks = { catalog })
            journey.start(); loop.location(fix()); runCurrent()
            val main = voice.speech.last(); assertEquals(catalog.stories.first().text, main)
            voice.finish(); loop.user("再说一遍"); assertEquals(main, voice.speech.last()); voice.finish()
            assertEquals(catalog.stories.first().title, journey.summaryTopics.last())
            loop.user("再讲一点"); assertEquals(catalog.stories.last().text, voice.speech.last()); voice.finish()
            loop.user("安静十分钟"); loop.check(); assertTrue(journey.isQuiet)
            assertEquals(0, calls); journey.end(); loop.reset()
        }
    }
    @Test fun missingCatalogFallsBackToOldFactsWithoutClaimingCompleteStory() {
        begin(); val r = OfflineNarrator(pack(), null).response(journey, time)
        assertEquals(2, r.memoryUpdate.split('；').size); assertTrue(r.narration.length < 300)
    }
    @Test fun followupWithOnlyMainClaimsIsNotSelectedAsNewContent() {
        val n = candidates(); val mainRefs = n.getValue("narratives").jsonArray.first().jsonObject.getValue("supported_claim_refs")
        val catalog = parse(n = mutate(n, "narratives", 1) { JsonObject(it + ("supported_claim_refs" to mainRefs)) })
        begin(); val narrator = OfflineNarrator(pack(), catalog); send(narrator); voice.finish()
        assertTrue(narrator.response(journey, time, "再讲一点").narration.contains("没有审核通过且未讲过"))
    }
    @Test fun mainDoesNotReplayAutomaticallyAfterGeneralMemoryRotates() {
        begin(); val n = OfflineNarrator(pack(), parse()); val main = send(n); voice.finish()
        repeat(40) {
            journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "其他回答$it", "普通回答", memoryUpdate = "新事实$it").json())
            voice.finish()
        }
        assertEquals(32, journey.narrationMemory.size)
        assertEquals(NarrationPhase.COMPLETED, journey.localStoryPhase("yangshen-main"))
        assertNotEquals(main.narration, n.response(journey, time).narration)
        journey.end(); assertNull(journey.localStoryPhase("yangshen-main"))
    }
    @Test fun priorExplicitCloudFailureDoesNotBlockFirstFullLocalStory() = runTest {
        var calls = 0; val p = pack(); val catalog = parse()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time },
            { DirectorProvider { calls++; error("unreachable cloud") } },
            researchProvider = { error("no automatic research") }, online = { true }, offlinePack = { p },
            preferOffline = { true }, storyPacks = { catalog })
        journey.start(); loop.user("为什么天空是蓝色？"); runCurrent(); voice.finish()
        assertEquals(1, calls)
        time += 181000; loop.location(fix()); runCurrent()
        assertEquals(catalog.stories.first().text, voice.speech.last()); assertEquals(1, calls)
        assertEquals(1, loop.counters.activeDispatched); journey.end(); loop.reset()
    }
}
