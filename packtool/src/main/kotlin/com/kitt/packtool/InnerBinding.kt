package com.kitt.packtool

import kotlinx.serialization.json.*

/** The signed outer manifest must describe exactly the pack its inner D0 blocks describe.
 *
 *  D0 only guarantees that the four blocks agree with *each other*. Without this binding a valid
 *  signature over one county could be re-labelled as another county, or advertise a different
 *  fact/narrative version or app requirement, and the release verdict would still be `Accepted`.
 *  Every field below is compared after the signature has been verified, so a mismatch is a
 *  signed-content lie rather than a tampering artefact.
 */
object InnerBinding {
    fun check(manifest: ReleaseManifest, blocks: CountyBlocks) {
        val factManifest = parse(blocks.factManifest, "fact_manifest")
        val narrativeManifest = parse(blocks.narrativeManifest, "narrative_manifest")
        val facts = parse(blocks.facts, "facts")
        val narratives = parse(blocks.narratives, "narratives")

        expectInt(factManifest, "schema_version", ReleaseFormat.SCHEMA_VERSION, "fact_manifest.schema_version")
        expectInt(narrativeManifest, "schema_version", ReleaseFormat.SCHEMA_VERSION, "narrative_manifest.schema_version")
        expect(factManifest, "kind", "fact", "fact_manifest.kind")
        expect(narrativeManifest, "kind", "narrative", "narrative_manifest.kind")

        expect(factManifest, "area_id", manifest.areaId, "fact_manifest.area_id")
        expect(narrativeManifest, "area_id", manifest.areaId, "narrative_manifest.area_id")
        expect(facts, "area_id", manifest.areaId, "facts.area_id")
        expect(narratives, "area_id", manifest.areaId, "narratives.area_id")

        expect(factManifest, "pack_id", "${manifest.areaId}-fact", "fact_manifest.pack_id")
        expect(narrativeManifest, "pack_id", "${manifest.areaId}-narrative", "narrative_manifest.pack_id")
        expect(facts, "pack_id", "${manifest.areaId}-fact", "facts.pack_id")
        expect(narratives, "pack_id", "${manifest.areaId}-narrative", "narratives.pack_id")

        val area = facts["area"] as? JsonObject ?: throw PackRejection("inner_binding", "facts.area")
        expect(area, "area_id", manifest.areaId, "facts.area.area_id")
        expect(area, "parent_id", manifest.parentId, "facts.area.parent_id")

        expect(factManifest, "content_version", manifest.factVersion, "fact_manifest.content_version")
        expect(facts, "content_version", manifest.factVersion, "facts.content_version")
        expect(narrativeManifest, "content_version", manifest.narrativeVersion, "narrative_manifest.content_version")
        expect(narratives, "content_version", manifest.narrativeVersion, "narratives.content_version")

        expectInt(factManifest, "minimum_code", manifest.minimumCode, "fact_manifest.minimum_code")
        expectInt(narrativeManifest, "minimum_code", manifest.minimumCode, "narrative_manifest.minimum_code")
    }

    private fun parse(bytes: ByteArray, field: String): JsonObject = try {
        Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
    } catch (error: Exception) {
        throw PackRejection("inner_binding", field)
    }

    private fun expect(source: JsonObject, key: String, expected: String, field: String) {
        val actual = (source[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (actual != expected) throw PackRejection("inner_binding", field)
    }

    private fun expectInt(source: JsonObject, key: String, expected: Int, field: String) {
        val actual = (source[key] as? JsonPrimitive)?.takeIf { !it.isString }?.int
        if (actual != expected) throw PackRejection("inner_binding", field)
    }
}
