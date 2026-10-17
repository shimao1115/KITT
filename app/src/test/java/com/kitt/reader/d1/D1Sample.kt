package com.kitt.reader.d1

import com.kitt.reader.StoryCategory
import com.kitt.reader.countyRevision
import com.kitt.reader.text
import kotlinx.serialization.json.*
import com.kitt.reader.OfflinePack

/** Deterministic TEST_ONLY sample content for D1.
 *
 * The administrative labels are real (Hubei/Yichang/Xiling, non-adjacent to Xindu) but every object,
 * fact, claim, source and story below is synthetic. This is the same stance D0 took for its second
 * county: it exists to exercise packaging, signatures and refusals, never to describe a real place.
 *
 * The bytes are rendered by pure functions so that a committed copy can be compared against a fresh
 * render, which is what keeps the reproducibility claim honest.
 */
object D1Sample {
    const val AREA_ID = "cn-420502"
    const val PARENT_ID = "cn-420500"
    private const val FACT_VERSION = "TEST_ONLY-d1-fact-1"
    private const val NARRATIVE_VERSION = "TEST_ONLY-d1-narrative-1"
    private const val EMPTY_NARRATIVE_VERSION = "TEST_ONLY-d1-narrative-empty-1"
    private const val RIGHTS = "CC-BY-4.0"

    fun render(): Map<String, ByteArray> = mapOf(
        "tools/d1/sample/fact-only/facts.json" to pretty(facts()),
        "tools/d1/sample/fact-only/narratives.json" to pretty(emptyNarratives()),
        "tools/d1/sample/with-story/facts.json" to pretty(facts()),
        "tools/d1/sample/with-story/narratives.json" to pretty(storyNarratives())
    )

    fun emptyNarratives(): JsonObject = narratives(emptyList())
    fun storyNarratives(): JsonObject = narratives(storyIds())

    fun facts(): JsonObject {
        val sources = listOf(source("s0"), source("s1"))
        val entities = (0..1).map { entity ->
            buildJsonObject {
                put("entity_id", "e$entity")
                put("title", "TEST_ONLY 合成同名对象$entity")
                put("family", "PEOPLE")
                put("basic_facts", JsonArray((0..1).map { index ->
                    buildJsonObject {
                        put("fact_id", "b$entity-$index")
                        put("summary", "TEST_ONLY 合成基础说明 $entity/$index，不是地方史实。")
                        put("coverage", "TEST_ONLY 基础$entity/$index")
                        put("source_ids", JsonArray(listOf(JsonPrimitive("s$entity"))))
                    }
                }))
            }
        }
        val claims = (0..6).map { index -> claim(index, sources) }
        return buildJsonObject {
            put("schema_version", 2)
            put("pack_id", "$AREA_ID-fact")
            put("content_version", FACT_VERSION)
            put("area_id", AREA_ID)
            put("retrieved_at", "2026-10-17")
            put("area", buildJsonObject {
                put("area_id", AREA_ID); put("parent_id", PARENT_ID)
                put("province", "湖北省"); put("city", "宜昌市"); put("district", "西陵区")
            })
            put("sources", JsonArray(sources))
            put("source_permissions", buildJsonObject { put("s0", "allowed"); put("s1", "allowed") })
            put("entities", JsonArray(entities))
            put("claims", JsonArray(claims))
            put("claim_permissions", buildJsonObject { (0..6).forEach { put("c$it", "allowed") } })
        }
    }

    private fun source(id: String): JsonObject {
        val raw = buildJsonObject {
            put("source_id", id)
            put("title", "TEST_ONLY 合成来源 $id")
            put("url", "https://example.invalid/$id")
            put("kind", "OTHER")
            put("access_status", "read")
            put("accessed_at", "2026-10-17")
            put("rights", RIGHTS)
        }
        return JsonObject(raw + ("registry_revision" to JsonPrimitive(countyRevision(raw, "registry_revision"))))
    }

    private fun claim(index: Int, sources: List<JsonObject>): JsonObject {
        val source = sources[index % sources.size]
        val raw = buildJsonObject {
            put("claim_id", "c$index")
            put("entity_id", "e${index % 2}")
            put("area_ids", JsonArray(listOf(JsonPrimitive(AREA_ID))))
            put("text", "TEST_ONLY 合成Claim $index，不是已核实地方知识。")
            put("status", "verified")
            put("temporal_class", "stable")
            put("rights", RIGHTS)
            put("evidence", JsonArray(listOf(buildJsonObject {
                put("source_id", source.text("source_id"))
                put("locator", "TEST_ONLY paragraph $index")
                put("relation", "supports")
                put("accepted_source_revision", source.text("registry_revision"))
            })))
        }
        return JsonObject(raw + ("revision" to JsonPrimitive(countyRevision(raw, "revision"))))
    }

    /** `null` renders the explicit "0 published stories" narrative block. */
    private fun narratives(ids: List<String>): JsonObject = buildJsonObject {
        put("schema_version", 2)
        put("pack_id", "$AREA_ID-narrative")
        put("content_version", if (ids.isEmpty()) EMPTY_NARRATIVE_VERSION else NARRATIVE_VERSION)
        put("area_id", AREA_ID)
        put("narratives", JsonArray(ids.mapIndexed { index, id -> story(id, index) }))
    }

    private fun storyIds() = listOf("story-0", "story-1")

    private fun story(id: String, index: Int): JsonObject {
        val claim = claim(index * 2, listOf(source("s0"), source("s1")))
        val body = ("TEST_ONLY 合成工程夹具，不可作为西陵或新都的已核实知识。" +
            "只用来验证县级双包签名、拼包拒绝、审核依赖与可复现打包。").repeat(2)
        return buildJsonObject {
            put("narrative_id", id)
            put("story_type", if (index == 0) "main" else "followup")
            put("area_id", AREA_ID)
            put("entity_id", "e0")
            put("title", "TEST_ONLY 合成篇$index")
            put("text", body)
            put("text_sha256", OfflinePack.sha256(body.encodeToByteArray()))
            put("review_status", "approved")
            put("fact_gate", "passed")
            put("visitor_gate", "passed")
            put("approval_ref", "TEST_ONLY_NOT_REAL_APPROVAL")
            put("license", RIGHTS)
            put("primary_category", StoryCategory.entries[index % StoryCategory.entries.size].name)
            put("secondary_categories", JsonArray(emptyList()))
            put("supported_claim_refs", JsonArray(listOf(buildJsonObject {
                put("claim_id", claim.text("claim_id"))
                put("accepted_revision", claim.text("revision"))
            })))
        }
    }

    private fun pretty(value: JsonElement): ByteArray =
        Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), value).encodeToByteArray()
}
