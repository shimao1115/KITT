package com.kitt.packtool

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Every D1 refusal is a stable machine code, never a free-form sentence. Tests assert these codes. */
class PackRejection(val code: String, val detail: String = "") :
    Exception(if (detail.isEmpty()) code else "$code: $detail")

/** Fixed block order of the container. Any other order or repetition is rejected, so two different
 *  byte sequences can never decode to the same pack. */
object BlockKind {
    const val FACT_MANIFEST = 1
    const val FACTS = 2
    const val NARRATIVE_MANIFEST = 3
    const val NARRATIVES = 4
    val ORDER = listOf(FACT_MANIFEST, FACTS, NARRATIVE_MANIFEST, NARRATIVES)
    val NAMES = mapOf(FACT_MANIFEST to "fact_manifest", FACTS to "facts",
        NARRATIVE_MANIFEST to "narrative_manifest", NARRATIVES to "narratives")
    fun name(kind: Int) = NAMES[kind] ?: "unknown"
    fun code(name: String) = NAMES.entries.singleOrNull { it.value == name }?.key
}

/** The D1 release container (KCPK v1). It is deliberately *not* a ZIP: a fixed frame has no member
 *  paths, no duplicate-member ambiguity, no compression bomb and no central-directory drift. */
object ReleaseFormat {
    const val MAGIC = "KCPK0001"
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 2
    /** PKCS#1 v1.5: available on JDK 17 and on Android API 26+, and deterministic for a given key. */
    const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    const val BUILDER_ID = "kitt-packtool/1"
    const val SCOPE_TEST_ONLY = "TEST_ONLY"
    const val SCOPE_PRODUCTION = "PRODUCTION"
    const val KIND_FACT_ONLY = "fact_only"
    const val KIND_FACT_AND_NARRATIVE = "fact_and_narrative"
    const val MAX_MANIFEST = 16_000
    const val MAX_BLOCK = 500_000
    const val MAX_CONTAINER = 1_100_000
    const val MAX_KEY_ID = 64
    const val MAX_VERSION = 80
    const val MAX_ALGORITHM = 32
    const val MAX_SIGNATURE = 1024
    const val MAX_SOURCE_COMMIT = 40
    val SCOPES = setOf(SCOPE_TEST_ONLY, SCOPE_PRODUCTION)
    val KINDS = setOf(KIND_FACT_ONLY, KIND_FACT_AND_NARRATIVE)
    val SHA256 = Regex("[0-9a-f]{64}")
    val SOURCE_COMMIT = Regex("[0-9a-f]{40}")
    val AREA_ID = Regex("cn-[0-9]{6}")
    val PARENT_ID = Regex("cn-[0-9]{4}00")
    val KEY_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    /** Fields a payload may never use to assert its own trust. */
    val TRUST_CLAIMS = setOf("public_key", "publisher_public_key", "trusted", "trust_anchor",
        "signature_verified", "signature", "certificate")
}

/** Length-prefixed SHA-256 over the four content blocks in fixed order. Manifest fields are excluded,
 *  so the value can be written into the manifest without a self-hash cycle. */
object ContentDigest {
    fun of(blocks: List<ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        blocks.forEach { digest.update(ByteBuffer.allocate(4).putInt(it.size).array()); digest.update(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

data class BlockRef(val kind: String, val bytes: Int, val sha256: String)

/** Versioned, ordered release manifest. Field names are stable; the encoding is canonical
 *  (sorted keys, compact separators) so equal content always yields equal bytes. */
data class ReleaseManifest(
    val areaId: String,
    val parentId: String,
    val publisherScope: String,
    val keyId: String,
    val sourceCommit: String,
    val minimumCode: Int,
    val sequence: Int,
    val factVersion: String,
    val narrativeVersion: String,
    val contentKind: String,
    val blocks: List<BlockRef>,
    val contentSha256: String,
    val formatVersion: Int = ReleaseFormat.FORMAT_VERSION,
    val schemaVersion: Int = ReleaseFormat.SCHEMA_VERSION,
    val builder: String = ReleaseFormat.BUILDER_ID
) {
    fun toJson(): JsonObject {
        val fields = linkedMapOf<String, JsonElement>(
            "area_id" to JsonPrimitive(areaId),
            "blocks" to JsonArray(blocks.map { block ->
                JsonObject(linkedMapOf("bytes" to JsonPrimitive(block.bytes), "kind" to JsonPrimitive(block.kind),
                    "sha256" to JsonPrimitive(block.sha256)))
            }),
            "builder" to JsonPrimitive(builder),
            "content_kind" to JsonPrimitive(contentKind),
            "content_sha256" to JsonPrimitive(contentSha256),
            "fact_version" to JsonPrimitive(factVersion),
            "format" to JsonPrimitive("KCPK"),
            "format_version" to JsonPrimitive(formatVersion),
            "key_id" to JsonPrimitive(keyId),
            "minimum_code" to JsonPrimitive(minimumCode),
            "narrative_version" to JsonPrimitive(narrativeVersion),
            "parent_id" to JsonPrimitive(parentId),
            "publisher_scope" to JsonPrimitive(publisherScope),
            "schema_version" to JsonPrimitive(schemaVersion),
            "sequence" to JsonPrimitive(sequence),
            "source_commit" to JsonPrimitive(sourceCommit)
        )
        // Sorted keys make the encoding independent of declaration order.
        return JsonObject(fields.toSortedMap())
    }

    fun canonicalBytes(): ByteArray = toJson().toString().encodeToByteArray()

    companion object {
        private val KEYS = setOf("area_id", "blocks", "builder", "content_kind", "content_sha256", "fact_version",
            "format", "format_version", "key_id", "minimum_code", "narrative_version", "parent_id",
            "publisher_scope", "schema_version", "sequence", "source_commit")

        /** Strict parse. Unknown fields, embedded trust claims and non-canonical shapes fail closed. */
        fun parse(bytes: ByteArray): ReleaseManifest {
            val root = try {
                Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
            } catch (e: Exception) {
                throw PackRejection("manifest_schema", "not_a_json_object")
            }
            root.keys.firstOrNull { it in ReleaseFormat.TRUST_CLAIMS }?.let {
                throw PackRejection("payload_trust_claim", it)
            }
            root.keys.firstOrNull { it !in KEYS }?.let { throw PackRejection("unknown_field", it) }
            if (root.keys != KEYS) throw PackRejection("manifest_schema", "missing_field")
            if (root.str("format") != "KCPK") throw PackRejection("manifest_schema", "format")
            val blocks = root.getValue("blocks").jsonArray.map { element ->
                val block = element.jsonObject
                if (block.keys != setOf("bytes", "kind", "sha256")) throw PackRejection("manifest_schema", "block")
                BlockRef(block.str("kind"), block.int("bytes"), block.str("sha256"))
            }
            return ReleaseManifest(
                areaId = root.str("area_id"), parentId = root.str("parent_id"),
                publisherScope = root.str("publisher_scope"), keyId = root.str("key_id"),
                sourceCommit = root.str("source_commit"), minimumCode = root.int("minimum_code"),
                sequence = root.int("sequence"),
                factVersion = root.str("fact_version"), narrativeVersion = root.str("narrative_version"),
                contentKind = root.str("content_kind"), blocks = blocks,
                contentSha256 = root.str("content_sha256"), formatVersion = root.int("format_version"),
                schemaVersion = root.int("schema_version"), builder = root.str("builder")
            )
        }

        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
        private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
    }
}

/** Structural validation shared by the builder and the verifier. Nothing here trusts the payload. */
object ManifestRules {
    fun checkArea(areaId: String, parentId: String) {
        if (!ReleaseFormat.AREA_ID.matches(areaId) || areaId.endsWith("00")) throw PackRejection("area_id")
        if (!ReleaseFormat.PARENT_ID.matches(parentId) || parentId == areaId) throw PackRejection("parent_relationship")
        // Ordinary county -> prefecture hierarchy only. Province-direct (90) and missing levels need
        // separately reviewed rules and stay rejected, exactly as in D0.
        if (parentId != areaId.dropLast(2) + "00" || areaId.substring(5, 7) in setOf("00", "90")) {
            throw PackRejection("parent_relationship")
        }
    }

    fun checkManifest(m: ReleaseManifest, supportedCode: Int) {
        if (m.formatVersion != ReleaseFormat.FORMAT_VERSION) throw PackRejection("format_version")
        if (m.schemaVersion != ReleaseFormat.SCHEMA_VERSION) throw PackRejection("manifest_schema", "schema_version")
        if (m.builder != ReleaseFormat.BUILDER_ID) throw PackRejection("builder_version")
        if (m.publisherScope !in ReleaseFormat.SCOPES) throw PackRejection("publisher_scope")
        if (!ReleaseFormat.KEY_ID.matches(m.keyId)) throw PackRejection("field_size", "key_id")
        if (!ReleaseFormat.SOURCE_COMMIT.matches(m.sourceCommit)) throw PackRejection("source_commit")
        if (m.minimumCode !in 1..supportedCode) throw PackRejection("minimum_code", "${m.minimumCode}")
        // Monotonic release sequence. The verifier additionally refuses anything below the caller's
        // minimum accepted sequence, which is what makes an old-but-valid pack a rollback.
        if (m.sequence < 1) throw PackRejection("sequence", "${m.sequence}")
        if (m.contentKind !in ReleaseFormat.KINDS) throw PackRejection("content_kind")
        listOf("fact_version" to m.factVersion, "narrative_version" to m.narrativeVersion).forEach { (name, value) ->
            if (value.isBlank() || value.length > ReleaseFormat.MAX_VERSION) throw PackRejection("field_size", name)
        }
        checkArea(m.areaId, m.parentId)
        if (!ReleaseFormat.SHA256.matches(m.contentSha256)) throw PackRejection("content_digest")
        if (m.blocks.size != BlockKind.ORDER.size) throw PackRejection("block_count")
        m.blocks.forEachIndexed { index, block ->
            if (BlockKind.code(block.kind) != BlockKind.ORDER[index]) throw PackRejection("block_kind")
            if (block.bytes !in 1..ReleaseFormat.MAX_BLOCK) throw PackRejection("block_size")
            if (!ReleaseFormat.SHA256.matches(block.sha256)) throw PackRejection("block_integrity")
        }
    }
}
