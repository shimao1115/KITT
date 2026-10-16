package com.kitt.reader

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic approval is confined to memory; manuscripts and standard assets are never rewritten. */
internal object ExploreFixture {
    private val assets = File("src/main/assets/offline")
    fun pack() = OfflinePack.parse(File(assets, "xindu-manifest.json").readBytes(),
        File(assets, "xindu-facts.json").readBytes(), File(assets, "xindu-geometry.geojson").readBytes())
    fun stories(change: (JsonObject) -> JsonObject = { it }): StoryPacks {
        val root = File("../content/yangshen")
        val f = Json.parseToJsonElement(File(root, "facts.json").readText()).jsonObject
        val n = Json.parseToJsonElement(File(root, "narratives.json").readText()).jsonObject
        val approved = JsonObject(n + ("narratives" to JsonArray(n.getValue("narratives").jsonArray.map {
            change(JsonObject(it.jsonObject + mapOf("review_status" to JsonPrimitive("approved"),
                "visitor_gate" to JsonPrimitive("passed"), "approval_ref" to JsonPrimitive("TEST_ONLY"))))
        })))
        fun seal(data: JsonObject): Pair<ByteArray, ByteArray> {
            val b = data.toString().encodeToByteArray()
            return buildJsonObject {
                put("schema_version", 1); put("minimum_code", 17); put("pack_id", data.getValue("pack_id"))
                put("content_version", data.getValue("content_version")); put("bytes", b.size); put("sha256", OfflinePack.sha256(b))
            }.toString().encodeToByteArray() to b
        }
        val (fm, fb) = seal(f); val (nm, nb) = seal(approved)
        return StoryPacks.parse(fm, fb, nm, nb)
    }
    fun catalog() = ExploreCatalog.from(pack(), stories())
}

class ExplorePlacesTest {
    @Test fun actualInstalledCountyObjectsFactsAndSourceIdsAreTruthful() {
        val catalog = ExploreCatalog.from(ExploreFixture.pack(), null)
        assertEquals(1, catalog.regions.size)
        val region = catalog.regions.single()
        assertEquals("新都区", region.pack.area.district); assertEquals(6, region.search("").size); assertEquals(22, region.factCount)
        assertTrue(region.stories.isEmpty())
        region.pack.objects.flatMap { it.facts }.forEach { f ->
            assertTrue(f.sourceIds.isNotEmpty()); assertTrue(f.sourceIds.all { id -> region.pack.sources.any { it.id == id } })
        }
    }
    @Test fun keywordSearchCoversNamesFactsAndWhitespaceWithoutGps() {
        val region = ExploreCatalog.from(ExploreFixture.pack(), null).regions.single()
        assertEquals("杨慎（杨升庵）", region.search(" 杨慎 ").single().title)
        val obj = region.pack.objects.first()
        assertTrue(region.search(obj.facts.first().summary.takeLast(8)).contains(obj))
        assertEquals(6, region.search(" \n ").size)
        assertTrue(region.search("未安装的广汉资料").isEmpty())
        assertTrue(region.search("杨慎 无匹配").isEmpty())
    }
    @Test fun missingPackNeverInventsInstalledRegions() {
        assertTrue(ExploreCatalog.from(null, ExploreFixture.stories()).regions.isEmpty())
    }
    @Test fun approvedFixtureOffersSeparateCompleteArticlesAndRealSources() {
        val region = ExploreFixture.catalog().regions.single()
        val stories = region.storiesFor("杨慎（杨升庵）")
        assertEquals(listOf(StoryType.main, StoryType.followup), stories.map { it.type })
        assertTrue(stories.all { it.text.length > 100 && it.sourceLinks.isNotEmpty() })
        assertTrue(stories.flatMap { it.sourceLinks }.all { it.first.isNotBlank() && it.second.startsWith("https://") })
        assertTrue(region.search(stories.last().text.takeLast(12)).any { it.title == "杨慎（杨升庵）" })
        assertTrue(region.storiesFor("宝光寺").isEmpty())
    }
    @Test fun candidateReviewRightsHashAndDependenciesStayBlockedInExplorer() {
        for ((field, value) in listOf("review_status" to "candidate", "visitor_gate" to "pending", "fact_gate" to "pending",
            "text_sha256" to "bad", "license" to "restricted")) {
            val stories = ExploreFixture.stories { JsonObject(it + (field to JsonPrimitive(value))) }
            val region = ExploreCatalog.from(ExploreFixture.pack(), stories).regions.single()
            assertTrue(field, region.stories.isEmpty()); assertEquals(2, stories.blocked.size)
            assertEquals(22, region.factCount)
        }
        val missing = ExploreFixture.stories { JsonObject(it + ("supported_claim_refs" to buildJsonArray {
            add(buildJsonObject { put("claim_id", "missing"); put("accepted_revision", "missing") })
        })) }
        assertTrue(missing.stories.isEmpty())
    }
    @Test fun exactFullTextIsPassedOnlyAfterManualPlayAndNeverAutoAdvances() {
        val voice = TestVoice(); val player = ExplorePlayback(voice) { true }
        val stories = ExploreFixture.stories().stories
        assertTrue(voice.speech.isEmpty()); player.play(stories.first())
        assertEquals(listOf(stories.first().text), voice.speech)
        voice.finish(); assertEquals(ExploreSpeech.IDLE, player.state); assertEquals(1, voice.speech.size)
    }
    @Test fun noOfflineVoiceRefusesToSpeakAndProvidesActionableMessage() {
        val voice = TestVoice(); val player = ExplorePlayback(voice) { false }
        player.play(ExploreFixture.stories().stories.first())
        assertEquals(ExploreSpeech.ERROR, player.state); assertTrue(player.message.contains("离线中文"))
        assertTrue(voice.speech.isEmpty()); assertEquals(0, voice.listeners)
    }
    @Test fun stoppedOrReplacedArticleIgnoresLateSuccessAndFailure() {
        val voice = TestVoice(); val player = ExplorePlayback(voice) { true }; val stories = ExploreFixture.stories().stories
        player.play(stories.first()); val old = voice.done!!; player.stop(); old(false)
        assertEquals(ExploreSpeech.IDLE, player.state); assertEquals("", player.message)
        player.play(stories.last()); old(true)
        assertEquals(ExploreSpeech.PREPARING, player.state); voice.done!!(false)
        assertEquals(ExploreSpeech.ERROR, player.state)
    }
    @Test fun synchronousEngineExceptionIsContainedAndCanRetry() {
        val voice = object : VoicePort {
            override fun stop() {}
            override fun speak(text: String, complete: (Boolean) -> Unit) { error("engine unavailable") }
            override fun listen(result: (String?) -> Unit) { error("must not listen") }
        }
        val player = ExplorePlayback(voice) { true }
        player.play(ExploreFixture.stories().stories.first()); assertEquals(ExploreSpeech.ERROR, player.state)
        player.stop(); assertEquals(ExploreSpeech.IDLE, player.state)
    }
}
