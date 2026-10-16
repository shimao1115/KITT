package com.kitt.reader

import android.content.Context
import kotlinx.serialization.json.*

/** These categories describe a story, never inferred from the legacy object's TopicFamily. */
enum class StoryCategory { NATURE, ENGINEERING, CULTURE, LOCAL_LIFE, HISTORY, TRAVEL }
enum class StoryType { main, followup }
data class LocalStory(val id: String, val type: StoryType, val entityId: String, val title: String,
    val text: String, val category: StoryCategory, val coverage: List<String>) {
    val marker get() = "story:$id"
    fun response() = DirectorResult(Action.SPEAK_NOW, "杨慎（杨升庵）", text,
        memoryUpdate = (listOf(marker) + coverage).joinToString("；"), topicFamily = TopicFamily.PEOPLE)
}

/** Independent manifests and claim revisions. A rejected dependency only disables its users.
 * No network, downloader, coordinates, accounts or persistent playback state. */
class StoryPacks private constructor(val stories: List<LocalStory>, val blocked: Map<String, String>) {
    fun select(journey: Journey, area: AreaIdentity, more: Boolean): LocalStory? {
        if (area.copy(chapter = "").key != XINDU_KEY) return null
        fun attempted(story: LocalStory) = journey.localStoryPhase(story.id) != null
        val main = stories.firstOrNull { it.type == StoryType.main } ?: return null
        if (!more) return main.takeUnless(::attempted)
        if (journey.topic != "杨慎（杨升庵）" || journey.localStoryPhase(main.id) != NarrationPhase.COMPLETED) return null
        return stories.firstOrNull { it.entityId == main.entityId && it.type == StoryType.followup && !attempted(it) &&
            it.coverage.any { fact -> fact !in main.coverage } }
    }

    /** Keep old short fragments of this entity from immediately retelling the same main story. */
    fun ownsAttempt(journey: Journey, area: AreaIdentity, title: String): Boolean =
        title == "杨慎（杨升庵）" && area.copy(chapter = "").key == XINDU_KEY &&
            journey.hasLocalStoryAttempt()

    /** The main's ys-guihu claim already covers this old introductory angle. Other garden facts
     * (planting/name, later layout, buildings) remain eligible; no second complete story is built. */
    fun coversLegacyFact(journey: Journey, area: AreaIdentity, factId: String): Boolean =
        area.copy(chapter = "").key == XINDU_KEY && journey.hasLocalStoryAttempt() && factId == "guihu-memory"

    companion object {
        private const val XINDU_KEY = "四川省/成都市/新都区/"
        private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
        private fun canonical(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
            is JsonArray -> JsonArray(value.map(::canonical))
            else -> value
        }
        private fun revision(value: JsonObject, field: String) =
            OfflinePack.sha256(canonical(JsonObject(value.filterKeys { it != field })).toString().encodeToByteArray())

        private fun checked(manifest: ByteArray, bytes: ByteArray, expectedId: String): JsonObject {
            require(manifest.size in 1..16000 && bytes.size in 1..500000)
            val m = Json.parseToJsonElement(manifest.decodeToString(throwOnInvalidSequence = true)).jsonObject
            require(m.getValue("schema_version").jsonPrimitive.int == 1)
            require(m.getValue("minimum_code").jsonPrimitive.int in 17..BuildConfig.VERSION_CODE)
            require(m.string("pack_id") == expectedId && m.string("content_version").isNotBlank())
            require(m.getValue("bytes").jsonPrimitive.int == bytes.size && m.string("sha256") == OfflinePack.sha256(bytes))
            return Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject.also {
                require(it.getValue("schema_version").jsonPrimitive.int == 1)
                require(it.string("pack_id") == expectedId && it.string("content_version") == m.string("content_version"))
            }
        }
        fun load(context: Context): StoryPacks? = runCatching {
            fun read(name: String) = context.assets.open("offline/ys-$name.json").use { it.readBytes() }
            parse(read("facts-manifest"), read("facts"), read("narratives-manifest"), read("narratives"),
                acceptanceCandidate = BuildConfig.DEBUG && BuildConfig.APPLICATION_ID == "com.kitt.reader.runtimecheck")
        }.getOrNull()

        fun parse(factManifest: ByteArray, facts: ByteArray, narrativeManifest: ByteArray, narratives: ByteArray,
            acceptanceCandidate: Boolean = false): StoryPacks {
            val f = checked(factManifest, facts, "xindu-yangshen-facts")
            val n = checked(narrativeManifest, narratives, "xindu-yangshen-narratives")
            require(f.string("area_id") == "cn-510114")
            fun index(array: JsonArray, key: String): Map<String, JsonObject> {
                require(array.size <= 200)
                val items = array.map { it.jsonObject }
                require(items.map { it.string(key) }.distinct().size == items.size)
                return items.associateBy { it.string(key) }
            }
            val entities = index(f.getValue("entities").jsonArray, "entity_id")
            val sources = index(f.getValue("sources").jsonArray, "source_id")
            val claims = index(f.getValue("claims").jsonArray, "claim_id")
            val candidates = index(n.getValue("narratives").jsonArray, "narrative_id")
            val blocked = linkedMapOf<String, String>()
            val stories = candidates.mapNotNull { (id, story) ->
                runCatching {
                    require(id.matches(Regex("yangshen-[a-z0-9-]+"))) { "invalid_id" }
                    // The acceptance APK exists so a person can audition the final text. Never
                    // represent pending human listening as passed, or admit candidates in standard loads.
                    val reviewed = story.string("review_status") == "approved" && story.string("visitor_gate") == "passed"
                    val candidate = acceptanceCandidate && story.string("review_status") == "candidate" &&
                        story.string("visitor_gate") == "pending"
                    require((reviewed || candidate) && story.string("fact_gate") == "passed" &&
                        story.string("approval_ref").isNotBlank()) { "review_pending" }
                    require(story.string("area_id") == "cn-510114" && story.string("entity_id") == "person-yang-shen" &&
                        story.string("entity_id") in entities) { "entity_or_area" }
                    require(story.string("license") == "CC-BY-4.0") { "rights" }
                    val category = StoryCategory.valueOf(story.string("primary_category"))
                    val secondary = story.getValue("secondary_categories").jsonArray.map { StoryCategory.valueOf(it.jsonPrimitive.content) }
                    require(secondary.distinct().size == secondary.size && category !in secondary) { "categories" }
                    val text = story.string("text")
                    require(text.length in 100..6000 && OfflinePack.sha256(text.encodeToByteArray()) == story.string("text_sha256")) { "text_integrity" }
                    val refs = story.getValue("supported_claim_refs").jsonArray
                    require(refs.size in 1..100)
                    val coverage = refs.map { ref ->
                        val r = ref.jsonObject
                        val claim = claims[r.string("claim_id")] ?: error("missing_claim")
                        require(claim.string("entity_id") == story.string("entity_id")) { "claim_entity" }
                        require(claim.string("status") == "verified" && claim.string("temporal_class") == "stable") { "claim_status" }
                        require(claim.string("rights").isNotBlank()) { "claim_rights" }
                        require(claim.string("revision") == revision(claim, "revision") && r.string("accepted_revision") == claim.string("revision")) { "claim_revision" }
                        val evidence = claim.getValue("evidence").jsonArray
                        require(evidence.isNotEmpty()) { "missing_evidence" }
                        evidence.forEach { entry ->
                            val e = entry.jsonObject
                            val source = sources[e.string("source_id")] ?: error("missing_source")
                            require(source.string("access_status") == "read" && source.string("rights").isNotBlank() &&
                                source.string("accessed_at").isNotBlank() && source.string("url").startsWith("https://") &&
                                e.string("locator").isNotBlank() && e.string("relation") == "supports") { "source_evidence" }
                            require(source.string("registry_revision") == revision(source, "registry_revision") &&
                                e.string("accepted_source_revision") == source.string("registry_revision")) { "source_revision" }
                        }
                        "claim:${r.string("claim_id")}" // Shared across revisions: a wording edit cannot erase heard facts.
                    }
                    require(coverage.distinct().size == coverage.size)
                    require((listOf("story:$id") + coverage).joinToString("；").length <= 240) { "coverage_limit" }
                    LocalStory(id, StoryType.valueOf(story.string("story_type")), story.string("entity_id"), story.string("title"), text, category, coverage)
                }.onFailure { blocked[id] = it.message.orEmpty() }.getOrNull()
            }
            require(stories.count { it.type == StoryType.main } <= 1)
            return StoryPacks(stories, blocked)
        }
    }
}
