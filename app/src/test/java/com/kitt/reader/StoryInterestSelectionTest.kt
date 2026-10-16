package com.kitt.reader

import kotlinx.serialization.json.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * The second category exists only inside this in-memory fixture. Real manuscripts, claims, approvals
 * and standard assets are read but never rewritten, and the one-main data gate stays in force.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoryInterestSelectionTest {
    private val assets = File("src/main/assets/offline")
    private val source = File("../content/yangshen")
    private var time = 1_000_000L
    private val voice = TestVoice()
    private val journey = Journey({ time }, voice)
    private fun pack() = OfflinePack.parse(File(assets, "xindu-manifest.json").readBytes(),
        File(assets, "xindu-facts.json").readBytes(), File(assets, "xindu-geometry.geojson").readBytes())
    private val area get() = pack().area
    private fun facts() = Json.parseToJsonElement(File(source, "facts.json").readText()).jsonObject
    private fun raw() = Json.parseToJsonElement(File(source, "narratives.json").readText()).jsonObject
    private fun seal(data: JsonObject): Pair<ByteArray, ByteArray> {
        val bytes = data.toString().encodeToByteArray()
        return buildJsonObject {
            put("schema_version", 1); put("minimum_code", 17); put("pack_id", data.getValue("pack_id"))
            put("content_version", data.getValue("content_version")); put("bytes", bytes.size)
            put("sha256", OfflinePack.sha256(bytes))
        }.toString().encodeToByteArray() to bytes
    }
    private fun approved() = raw().getValue("narratives").jsonArray.map { JsonObject(it.jsonObject +
        mapOf("review_status" to JsonPrimitive("approved"), "fact_gate" to JsonPrimitive("passed"),
            "visitor_gate" to JsonPrimitive("passed"),
            "approval_ref" to JsonPrimitive("TEST_ONLY_NOT_CONTENT_APPROVAL"))) }

    private fun catalog(extra: List<JsonObject> = emptyList(), manuscripts: Boolean = false): StoryPacks {
        val narratives = if (manuscripts) raw().getValue("narratives").jsonArray.map { it.jsonObject } else approved()
        val (fm, fb) = seal(facts()); val (nm, nb) = seal(JsonObject(raw() + ("narratives" to JsonArray(narratives + extra))))
        return StoryPacks.parse(fm, fb, nm, nb)
    }
    /** Same entity, same county, same reviewed claims and comparable length: only the category differs. */
    private fun engineeringAngle(): JsonObject {
        val text = "新都的水利与街巷记录着本地人如何使用身边的水与路。".repeat(6)
        return JsonObject(approved().last() + mapOf("narrative_id" to JsonPrimitive("yangshen-followup-water"),
            "title" to JsonPrimitive("测试用水角度"), "primary_category" to JsonPrimitive("ENGINEERING"),
            "secondary_categories" to buildJsonArray { }, "text" to JsonPrimitive(text),
            "text_sha256" to JsonPrimitive(OfflinePack.sha256(text.encodeToByteArray()))))
    }
    private fun fix() = Fix(30.83, 104.16, time, accuracy = 10.0, source = FixSource.GPS)
    private fun narrator(catalog: StoryPacks, interests: Map<StoryCategory, StoryInterest> = emptyMap()) =
        OfflineNarrator(pack(), catalog, interests)
    private fun interest(vararg pairs: Pair<StoryCategory, StoryInterest>) = mapOf(*pairs)

    private fun completeMain(catalog: StoryPacks) {
        journey.start(); journey.location(fix())
        val response = narrator(catalog).response(journey, time)
        assertTrue(response.memoryUpdate.startsWith("story:yangshen-main"))
        journey.deliver(journey.ticket(false), response.json(), narrationArea = area, recordNarration = true,
            localStoryId = "yangshen-main", summaryLabel = catalog.stories.first().title)
        voice.finish()
        assertEquals(NarrationPhase.COMPLETED, journey.localStoryPhase("yangshen-main"))
    }

    @Test fun balancedInterestsKeepTheExistingDeterministicAngle() {
        val catalog = catalog(listOf(engineeringAngle())); assertEquals(3, catalog.stories.size)
        completeMain(catalog)
        val balanced = StoryCategory.entries.associateWith { StoryInterest.NORMAL }
        assertEquals("yangshen-followup-dianzai", catalog.select(journey, area, true, emptyMap())!!.id)
        assertEquals("yangshen-followup-dianzai", catalog.select(journey, area, true, balanced)!!.id)
    }
    @Test fun moreOnItsOwnCategoryRaisesThatAngleWithoutRemovingTheOther() {
        val catalog = catalog(listOf(engineeringAngle())); completeMain(catalog)
        assertEquals("yangshen-followup-water",
            catalog.select(journey, area, true, interest(StoryCategory.ENGINEERING to StoryInterest.MORE))!!.id)
        assertEquals("yangshen-followup-water",
            catalog.select(journey, area, true, interest(StoryCategory.HISTORY to StoryInterest.LESS))!!.id)
        // Both marked less still narrates one of them: soft priority never bans.
        assertEquals("yangshen-followup-dianzai", catalog.select(journey, area, true, interest(
            StoryCategory.HISTORY to StoryInterest.LESS, StoryCategory.ENGINEERING to StoryInterest.LESS))!!.id)
        val eligible = catalog.stories.filter { it.type == StoryType.followup }
        val ranked = StoryRanking.order(eligible, interest(StoryCategory.HISTORY to StoryInterest.LESS))
        assertEquals(2, ranked.size)
        assertEquals(ranked.first().id, catalog.select(journey, area, true, interest(StoryCategory.HISTORY to StoryInterest.LESS))!!.id)
    }
    @Test fun oneMainStoryStillPlaysWhenItsCategoryIsMarkedLessAndGainsNothingFromMore() {
        val catalog = catalog()
        journey.start(); journey.location(fix())
        assertEquals("yangshen-main", catalog.select(journey, area, false, interest(StoryCategory.HISTORY to StoryInterest.LESS))!!.id)
        assertEquals("yangshen-main", catalog.select(journey, area, false, interest(StoryCategory.TRAVEL to StoryInterest.MORE))!!.id)
        // Interests cannot invent a second main story past the single-main data gate.
        assertEquals(1, catalog.stories.count { it.type == StoryType.main })
        val secondMain = JsonObject(approved().first() + mapOf("narrative_id" to JsonPrimitive("yangshen-main-two")))
        assertThrows(IllegalArgumentException::class.java) { catalog(listOf(secondMain)) }
    }
    @Test fun unreviewedManuscriptsStayUnplayableEvenWhenHistoryIsWantedMore() {
        val catalog = catalog(manuscripts = true)
        assertTrue(catalog.stories.isEmpty()); assertEquals(2, catalog.blocked.size)
        journey.start(); journey.location(fix())
        assertNull(catalog.select(journey, area, false, interest(StoryCategory.HISTORY to StoryInterest.MORE)))
        val wanted = interest(StoryCategory.HISTORY to StoryInterest.MORE)
        val response = narrator(catalog, wanted).response(journey, time)
        assertEquals(Action.SPEAK_NOW, response.action)
        assertFalse(response.memoryUpdate.startsWith("story:"))
    }
    @Test fun locationAndPlaybackGatesOutrankAnyInterest() {
        val catalog = catalog(listOf(engineeringAngle()))
        journey.start(); journey.location(fix().copy(latitude = 30.65))
        assertEquals(Action.SILENT, narrator(catalog, interest(StoryCategory.HISTORY to StoryInterest.MORE))
            .response(journey, time).action)
        journey.end(); completeMain(catalog)
        // The completed main is never re-offered automatically, whatever the user prefers.
        assertNull(catalog.select(journey, area, false, interest(StoryCategory.HISTORY to StoryInterest.MORE)))
        journey.end(); journey.start(); journey.location(fix())
        assertNull(catalog.select(journey, area, true, interest(StoryCategory.ENGINEERING to StoryInterest.MORE)))
    }
    @Test fun automaticNarrationUsesSavedInterestsWhileAnExplicitRequestStaysDeterministic() = runTest {
        val catalog = catalog(listOf(engineeringAngle())); val pack = pack(); var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time },
            { calls++; error("cloud forbidden") }, researchProvider = { calls++; error("research forbidden") },
            online = { true }, offlinePack = { pack }, preferOffline = { true }, storyPacks = { catalog },
            storyInterests = { interest(StoryCategory.HISTORY to StoryInterest.LESS) })
        journey.start(); loop.location(fix()); runCurrent()
        assertEquals(catalog.stories.first { it.id == "yangshen-main" }.text, voice.speech.last())
        voice.finish(); assertEquals(0, calls)
        // 再讲一点 is an explicit spoken request: automatic interests must not reprioritize it.
        loop.user("再讲一点"); runCurrent()
        assertEquals(catalog.stories.first { it.id == "yangshen-followup-dianzai" }.text, voice.speech.last())
        voice.finish(); assertEquals(0, calls)
        journey.end(); loop.reset()
    }
}
