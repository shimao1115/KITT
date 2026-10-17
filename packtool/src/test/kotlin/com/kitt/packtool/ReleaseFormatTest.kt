package com.kitt.packtool

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator

/** Format-level tests. They never touch Android, D0 or any real content. */
class ReleaseFormatTest {
    private val key: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val keyId = "test-only-format"
    private val commit = "0123456789abcdef0123456789abcdef01234567"
    private val trust = TrustStore(listOf(TrustAnchor(keyId, key.public, AnchorScope.TEST_ONLY)))
    private val gate = ContentGate { GateReport(true, null, facts = 1, sources = 1, claims = 1, stories = 0) }

    private fun factPayload(area: String = "cn-420502", parent: String = "cn-420500") = buildJsonObject {
        put("schema_version", 2); put("pack_id", "$area-fact"); put("content_version", "v1"); put("area_id", area)
        put("area", buildJsonObject { put("area_id", area); put("parent_id", parent) })
    }.toString().encodeToByteArray()

    private fun narrativePayload(area: String = "cn-420502") = buildJsonObject {
        put("schema_version", 2); put("pack_id", "$area-narrative"); put("content_version", "v1"); put("area_id", area)
        put("narratives", JsonArray(emptyList()))
    }.toString().encodeToByteArray()

    private fun built(area: String = "cn-420502", parent: String = "cn-420500") = PackBuilder.build(
        area, factPayload(area, parent), narrativePayload(area), keyId, ReleaseFormat.SCOPE_TEST_ONLY, commit, 20, key.private)

    private fun verify(container: ByteArray, store: TrustStore = trust) =
        ReleaseVerifier(store, 20, gate).verify(container)

    @Test
    fun containerRoundTripsAndKeepsBlockOrder() {
        val built = built()
        val decoded = ReleaseContainer.decode(built.container)
        assertEquals(ReleaseFormat.SIGNATURE_ALGORITHM, decoded.algorithm)
        assertEquals(keyId, decoded.keyId)
        assertEquals(built.blocks.list().size, decoded.region.blocks.size)
        built.blocks.list().zip(decoded.region.blocks).forEach { (a, b) -> assertArrayEquals(a, b) }
        assertEquals(built.manifest, ReleaseManifest.parse(decoded.region.manifestBytes))
    }

    @Test
    fun signedContainerIsAcceptedAndTamperingIsNot() {
        assertTrue(verify(built().container) is ReleaseVerdict.Accepted)
        val container = built().container
        val mutated = container.copyOf()
        mutated[container.size - 1] = (mutated[container.size - 1].toInt() xor 1).toByte()
        assertEquals("bad_signature", (verify(mutated) as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun manifestRejectsUnknownFieldsAndSelfAssertedTrust() {
        val manifest = built().manifest.toJson()
        val unknown = JsonObject(manifest + ("extra" to JsonPrimitive("x"))).toString().encodeToByteArray()
        assertEquals("unknown_field", rejection(unknown))
        val trustClaim = JsonObject(manifest + ("trusted" to JsonPrimitive(true))).toString().encodeToByteArray()
        assertEquals("payload_trust_claim", rejection(trustClaim))
        val missing = JsonObject(manifest - "key_id").toString().encodeToByteArray()
        assertEquals("manifest_schema", rejection(missing))
    }

    @Test
    fun manifestEncodingDoesNotDependOnFieldOrder() {
        val manifest = built().manifest
        val reordered = JsonObject(manifest.toJson().entries.reversed().associate { it.key to it.value })
        assertArrayEquals(manifest.canonicalBytes(), ReleaseManifest.parse(reordered.toString().encodeToByteArray()).canonicalBytes())
    }

    @Test
    fun sizeLimitsFailClosed() {
        assertEquals("manifest_size", runCatching {
            ReleaseContainer.unsignedRegion(ByteArray(ReleaseFormat.MAX_MANIFEST + 1), built().blocks.list())
        }.exceptionOrNull()?.let { (it as PackRejection).code })
        assertEquals("block_size", runCatching {
            PackBuilder.seal("fact", ByteArray(ReleaseFormat.MAX_BLOCK + 1), 20)
        }.exceptionOrNull()?.let { (it as PackRejection).code })
        assertEquals("container_too_large", (verify(ByteArray(ReleaseFormat.MAX_CONTAINER + 1)) as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun trustAnchorsArePinnedToTheirScope() {
        val production = TrustStore(listOf(TrustAnchor(keyId, key.public, AnchorScope.PRODUCTION)))
        assertEquals("unknown_key_id", (verify(built().container, production) as ReleaseVerdict.Rejected).code)
        assertEquals("no_trusted_publisher", (verify(built().container, TrustStore.EMPTY) as ReleaseVerdict.Rejected).code)
        val conflicting = listOf(TrustAnchor(keyId, key.public, AnchorScope.TEST_ONLY),
            TrustAnchor(keyId, key.public, AnchorScope.PRODUCTION))
        assertEquals("ambiguous_anchor", runCatching { TrustStore(conflicting) }
            .exceptionOrNull()?.let { (it as PackRejection).code })
    }

    @Test
    fun blockIntegrityIsCheckedAgainstTheManifestEvenWhenTheSignatureVerifies() {
        val built = built()
        val decoded = ReleaseContainer.decode(built.container)
        val blocks = decoded.region.blocks.toMutableList()
        blocks[1] = blocks[1].copyOf().also { it[it.size - 2] = (it[it.size - 2].toInt() xor 1).toByte() }
        val manifestBytes = built.manifest.canonicalBytes()
        val region = ReleaseContainer.unsignedRegion(manifestBytes, blocks)
        val resigned = ReleaseContainer.assemble(SignedRegion(region, manifestBytes, blocks),
            ReleaseFormat.SIGNATURE_ALGORITHM, keyId, ReleaseSignature.sign(region, key.private))
        assertEquals("block_integrity", (verify(resigned) as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun aMissingContentGateIsNeverTreatedAsApproval() {
        val verdict = ReleaseVerifier(trust, 20, null).verify(built().container)
        assertEquals("content_gate_unavailable", (verdict as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun factOnlyDeclarationCannotHideStories() {
        val talkative = ContentGate { GateReport(true, null, facts = 1, sources = 1, claims = 1, stories = 3) }
        val verdict = ReleaseVerifier(trust, 20, talkative).verify(built().container)
        assertEquals("content_kind", (verdict as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun headerDamageIsReportedPrecisely() {
        val container = built().container
        assertEquals("magic", (verify(container.copyOf().also { it[0] = 'X'.code.toByte() }) as ReleaseVerdict.Rejected).code)
        assertEquals("format_version", (verify(container.copyOf().also { it[8] = 9 }) as ReleaseVerdict.Rejected).code)
        val trailing = container + byteArrayOf(1)
        assertEquals("trailing_bytes", (verify(trailing) as ReleaseVerdict.Rejected).code)
    }

    @Test
    fun sealingIsDeterministic() {
        val first = PackBuilder.seal("fact", factPayload(), 20)
        val second = PackBuilder.seal("fact", factPayload(), 20)
        assertArrayEquals(first.manifest, second.manifest)
        assertArrayEquals(first.payload, second.payload)
        val a = built()
        val b = built()
        assertArrayEquals(a.container, b.container)
        assertEquals(a.unsignedDigest, b.unsignedDigest)
    }

    private fun rejection(manifestBytes: ByteArray): String? = runCatching {
        ReleaseManifest.parse(manifestBytes)
    }.exceptionOrNull()?.let { (it as PackRejection).code }
}
