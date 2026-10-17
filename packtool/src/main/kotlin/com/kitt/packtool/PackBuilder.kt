package com.kitt.packtool

import kotlinx.serialization.json.*

/** Deterministic assembly of a D1 container. No clock, no absolute path, no user name, no host name
 *  and no build-directory state enters the bytes: equal inputs always produce equal output. */
object PackBuilder {
    data class Sealed(val manifest: ByteArray, val payload: ByteArray)

    data class Built(
        val container: ByteArray,
        val manifest: ReleaseManifest,
        val blocks: CountyBlocks,
        val unsignedDigest: String
    )

    fun seal(kind: String, payload: ByteArray, minimumCode: Int): Sealed {
        if (kind !in setOf("fact", "narrative")) throw PackRejection("block_kind", kind)
        if (payload.size !in 1..ReleaseFormat.MAX_BLOCK) throw PackRejection("block_size", kind)
        val body = try {
            Json.parseToJsonElement(payload.decodeToString(throwOnInvalidSequence = true)).jsonObject
        } catch (error: Exception) {
            throw PackRejection("payload_schema", kind)
        }
        val areaId = body.textOrNull("area_id") ?: throw PackRejection("payload_schema", "area_id")
        val packId = body.textOrNull("pack_id") ?: throw PackRejection("payload_schema", "pack_id")
        val version = body.textOrNull("content_version") ?: throw PackRejection("payload_schema", "content_version")
        if (packId != "$areaId-$kind") throw PackRejection("pack_id", packId)
        if (body.getValue("schema_version").jsonPrimitive.int != ReleaseFormat.SCHEMA_VERSION) {
            throw PackRejection("payload_schema", "schema_version")
        }
        val manifest = linkedMapOf<String, JsonElement>(
            "area_id" to JsonPrimitive(areaId),
            "bytes" to JsonPrimitive(payload.size),
            "content_version" to JsonPrimitive(version),
            "kind" to JsonPrimitive(kind),
            "minimum_code" to JsonPrimitive(minimumCode),
            "pack_id" to JsonPrimitive(packId),
            "schema_version" to JsonPrimitive(ReleaseFormat.SCHEMA_VERSION),
            "sha256" to JsonPrimitive(ContentDigest.sha256(payload))
        )
        return Sealed(JsonObject(manifest).toString().encodeToByteArray(), payload)
    }

    fun build(
        areaId: String,
        factPayload: ByteArray,
        narrativePayload: ByteArray,
        keyId: String,
        scope: String,
        sourceCommit: String,
        minimumCode: Int,
        privateKey: java.security.PrivateKey,
        sequence: Int = 1,
        builder: String = ReleaseFormat.BUILDER_ID
    ): Built {
        val fact = seal("fact", factPayload, minimumCode)
        val narrative = seal("narrative", narrativePayload, minimumCode)
        val factBody = Json.parseToJsonElement(fact.payload.decodeToString()).jsonObject
        val narrativeBody = Json.parseToJsonElement(narrative.payload.decodeToString()).jsonObject
        if (factBody.textOrNull("area_id") != areaId || narrativeBody.textOrNull("area_id") != areaId) {
            throw PackRejection("area_mismatch")
        }
        val parentId = factBody.getValue("area").jsonObject.textOrNull("parent_id")
            ?: throw PackRejection("payload_schema", "parent_id")
        ManifestRules.checkArea(areaId, parentId)
        val stories = narrativeBody.getValue("narratives").jsonArray.size
        val blocks = CountyBlocks(fact.manifest, fact.payload, narrative.manifest, narrative.payload)
        val manifest = ReleaseManifest(
            areaId = areaId, parentId = parentId, publisherScope = scope, keyId = keyId,
            sourceCommit = sourceCommit, minimumCode = minimumCode, sequence = sequence,
            factVersion = factBody.getValue("content_version").jsonPrimitive.content,
            narrativeVersion = narrativeBody.getValue("content_version").jsonPrimitive.content,
            contentKind = if (stories == 0) ReleaseFormat.KIND_FACT_ONLY else ReleaseFormat.KIND_FACT_AND_NARRATIVE,
            blocks = listOf(
                BlockRef("fact_manifest", fact.manifest.size, ContentDigest.sha256(fact.manifest)),
                BlockRef("facts", fact.payload.size, ContentDigest.sha256(fact.payload)),
                BlockRef("narrative_manifest", narrative.manifest.size, ContentDigest.sha256(narrative.manifest)),
                BlockRef("narratives", narrative.payload.size, ContentDigest.sha256(narrative.payload))
            ),
            contentSha256 = blocks.digest(), builder = builder
        )
        ManifestRules.checkManifest(manifest, minimumCode)
        val manifestBytes = manifest.canonicalBytes()
        val region = ReleaseContainer.unsignedRegion(manifestBytes, blocks.list())
        val signature = ReleaseSignature.sign(region, privateKey)
        val container = ReleaseContainer.assemble(SignedRegion(region, manifestBytes, blocks.list()),
            ReleaseFormat.SIGNATURE_ALGORITHM, keyId, signature)
        return Built(container, manifest, blocks, ContentDigest.of(blocks.list()))
    }

    /** Low-level assembly from already sealed blocks. Only hostile-input tests use it: it lets them
     *  splice two counties together or reuse an old signature, which the normal path never does. */
    fun assembleBlocks(
        areaId: String,
        parentId: String,
        blocks: CountyBlocks,
        factVersion: String,
        narrativeVersion: String,
        contentKind: String,
        keyId: String,
        scope: String,
        sourceCommit: String,
        minimumCode: Int,
        privateKey: java.security.PrivateKey,
        sequence: Int = 1,
        builder: String = ReleaseFormat.BUILDER_ID
    ): Built {
        ManifestRules.checkArea(areaId, parentId)
        val manifest = ReleaseManifest(
            areaId = areaId, parentId = parentId, publisherScope = scope, keyId = keyId,
            sourceCommit = sourceCommit, minimumCode = minimumCode, sequence = sequence,
            factVersion = factVersion, narrativeVersion = narrativeVersion, contentKind = contentKind,
            blocks = listOf(
                BlockRef("fact_manifest", blocks.factManifest.size, ContentDigest.sha256(blocks.factManifest)),
                BlockRef("facts", blocks.facts.size, ContentDigest.sha256(blocks.facts)),
                BlockRef("narrative_manifest", blocks.narrativeManifest.size, ContentDigest.sha256(blocks.narrativeManifest)),
                BlockRef("narratives", blocks.narratives.size, ContentDigest.sha256(blocks.narratives))
            ),
            contentSha256 = blocks.digest(), builder = builder
        )
        ManifestRules.checkManifest(manifest, minimumCode)
        val manifestBytes = manifest.canonicalBytes()
        val region = ReleaseContainer.unsignedRegion(manifestBytes, blocks.list())
        val signature = ReleaseSignature.sign(region, privateKey)
        val container = ReleaseContainer.assemble(SignedRegion(region, manifestBytes, blocks.list()),
            ReleaseFormat.SIGNATURE_ALGORITHM, keyId, signature)
        return Built(container, manifest, blocks, ContentDigest.of(blocks.list()))
    }

    private fun JsonObject.textOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
