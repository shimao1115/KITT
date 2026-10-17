package com.kitt.packtool

/** The four Schema-2 blocks. D1 only transports them; the county content rules stay in D0. */
data class CountyBlocks(
    val factManifest: ByteArray,
    val facts: ByteArray,
    val narrativeManifest: ByteArray,
    val narratives: ByteArray
) {
    fun list(): List<ByteArray> = listOf(factManifest, facts, narrativeManifest, narratives)
    fun digest(): String = ContentDigest.of(list())
}

/** Result of handing verified bytes to the county content gate (D0). Counts are reported truthfully,
 *  including the "0 stories" case that a fact-only pack must always produce. */
data class GateReport(
    val accepted: Boolean,
    val reason: String? = null,
    val facts: Int = 0,
    val sources: Int = 0,
    val claims: Int = 0,
    val stories: Int = 0,
    val blocked: Map<String, String> = emptyMap()
)

/** Injected so this module stays free of Android and of D0's build config. */
fun interface ContentGate {
    fun inspect(blocks: CountyBlocks): GateReport
}

sealed class ReleaseVerdict {
    data class Accepted(
        val manifest: ReleaseManifest,
        val blocks: CountyBlocks,
        val gate: GateReport,
        val anchorScope: AnchorScope
    ) : ReleaseVerdict()

    data class Rejected(val code: String, val detail: String = "") : ReleaseVerdict()

    val accepted get() = this is Accepted
}

/** Signature first, then region/type/version/size, then the content gate. A payload that merely
 *  carries a key id, a public key or a "trusted" flag can never authenticate itself.
 *
 *  `minimumSequence` is the caller's own floor for accepted release sequences. D1 keeps no state:
 *  the caller (later the client) supplies the floor, and anything older is refused as a rollback. */
class ReleaseVerifier(
    private val trust: TrustStore = TrustStore.EMPTY,
    private val supportedCode: Int = 20,
    private val gate: ContentGate? = null,
    private val minimumSequence: Int = 1
) {
    fun verify(container: ByteArray): ReleaseVerdict = try {
        accept(container)
    } catch (rejection: PackRejection) {
        ReleaseVerdict.Rejected(rejection.code, rejection.detail)
    } catch (error: Exception) {
        // Unexpected parsing/runtime failures still fail closed, never "probably fine".
        ReleaseVerdict.Rejected("malformed", error.javaClass.simpleName)
    }

    private fun accept(container: ByteArray): ReleaseVerdict.Accepted {
        val decoded = ReleaseContainer.decode(container)
        val manifest = ReleaseManifest.parse(decoded.region.manifestBytes)
        if (manifest.keyId != decoded.keyId) throw PackRejection("key_id_mismatch")
        if (trust.isEmpty) throw PackRejection("no_trusted_publisher")
        val anchor = trust.resolve(manifest.keyId, manifest.publisherScope)
            ?: throw PackRejection("unknown_key_id", manifest.keyId)
        if (!ReleaseSignature.verify(decoded.region.bytes, anchor.publicKey, decoded.signature)) {
            throw PackRejection("bad_signature")
        }
        ManifestRules.checkManifest(manifest, supportedCode)
        if (manifest.sequence < minimumSequence) {
            throw PackRejection("sequence_rollback", "${manifest.sequence}<$minimumSequence")
        }
        val blocks = CountyBlocks(decoded.region.blocks[0], decoded.region.blocks[1],
            decoded.region.blocks[2], decoded.region.blocks[3])
        blocks.list().forEachIndexed { index, block ->
            val reference = manifest.blocks[index]
            if (reference.bytes != block.size || reference.sha256 != ContentDigest.sha256(block)) {
                throw PackRejection("block_integrity", reference.kind)
            }
        }
        if (blocks.digest() != manifest.contentSha256) throw PackRejection("content_digest")
        // The signature covers the outer manifest, so the outer claim must match the inner pack.
        InnerBinding.check(manifest, blocks)
        val content = gate ?: throw PackRejection("content_gate_unavailable")
        val report = content.inspect(blocks)
        if (!report.accepted) throw PackRejection("content_rejected", report.reason ?: "unspecified")
        if (manifest.contentKind == ReleaseFormat.KIND_FACT_ONLY && report.stories != 0) {
            // A pack must not under-declare what it ships.
            throw PackRejection("content_kind", "fact_only_with_stories")
        }
        return ReleaseVerdict.Accepted(manifest, blocks, report, anchor.scope)
    }
}
