package com.kitt.reader.d1

import com.kitt.packtool.AnchorScope
import com.kitt.packtool.CountyBlocks
import com.kitt.packtool.PackBuilder
import com.kitt.packtool.Pem
import com.kitt.packtool.ReleaseContainer
import com.kitt.packtool.ReleaseFormat
import com.kitt.packtool.ReleaseManifest
import com.kitt.packtool.ReleaseVerdict
import com.kitt.packtool.ReleaseVerifier
import com.kitt.packtool.SignedRegion
import com.kitt.packtool.TrustAnchor
import com.kitt.packtool.TrustStore
import com.kitt.reader.CountyBytes
import com.kitt.reader.CountyPackLoader
import com.kitt.reader.CountyProvenance
import com.kitt.reader.countyDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey

/** D1 golden path and its refusals. Every accepted case is TEST_ONLY synthetic content; no real
 *  county, no real story and no publisher key is involved anywhere in this file. */
class ReleaseD1Test {
    private val key: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    private val keyId = "test-only-d1-local"
    private val commit = "0123456789abcdef0123456789abcdef01234567"
    private val trust = TrustStore(listOf(TrustAnchor(keyId, key.public, AnchorScope.TEST_ONLY)))

    private val factBytes = File("../tools/d1/sample/fact-only/facts.json").readBytes()
    private val emptyNarratives = File("../tools/d1/sample/fact-only/narratives.json").readBytes()
    private val storyNarratives = File("../tools/d1/sample/with-story/narratives.json").readBytes()

    private fun build(
        fact: ByteArray = factBytes,
        narrative: ByteArray = emptyNarratives,
        areaId: String = D1Sample.AREA_ID,
        scope: String = ReleaseFormat.SCOPE_TEST_ONLY,
        minimumCode: Int = 20,
        declaredKeyId: String = keyId,
        privateKey: PrivateKey = key.private,
        sequence: Int = 1
    ) = PackBuilder.build(areaId, fact, narrative, declaredKeyId, scope, commit, minimumCode, privateKey, sequence)

    private fun verify(container: ByteArray, supportedCode: Int = 20, store: TrustStore = trust,
                       minimumSequence: Int = 1) =
        ReleaseVerifier(store, supportedCode, D0CountyGate, minimumSequence).verify(container)

    private fun rejection(verdict: ReleaseVerdict): ReleaseVerdict.Rejected {
        assertTrue("expected a refusal but got $verdict", verdict is ReleaseVerdict.Rejected)
        return verdict as ReleaseVerdict.Rejected
    }

    private fun rejected(verdict: ReleaseVerdict, code: String) = assertEquals(code, rejection(verdict).code)

    private fun accepted(verdict: ReleaseVerdict): ReleaseVerdict.Accepted {
        assertTrue("expected acceptance but got $verdict", verdict is ReleaseVerdict.Accepted)
        return verdict as ReleaseVerdict.Accepted
    }

    @Test
    fun factOnlyPackIsAcceptedAndReportsZeroStories() {
        val pack = accepted(verify(build().container))
        assertEquals(D1Sample.AREA_ID, pack.manifest.areaId)
        assertEquals(ReleaseFormat.KIND_FACT_ONLY, pack.manifest.contentKind)
        assertEquals(AnchorScope.TEST_ONLY, pack.anchorScope)
        assertEquals(1, pack.manifest.sequence)
        assertEquals(4, pack.gate.facts)
        assertEquals(2, pack.gate.sources)
        assertEquals(7, pack.gate.claims)
        assertEquals(0, pack.gate.stories)
        assertTrue(pack.gate.blocked.isEmpty())
    }

    @Test
    fun reviewedSyntheticStoriesAreCountedWhenTheNarrativeBlockShipsThem() {
        val pack = accepted(verify(build(narrative = storyNarratives).container))
        assertEquals(ReleaseFormat.KIND_FACT_AND_NARRATIVE, pack.manifest.contentKind)
        assertEquals(2, pack.gate.stories)
        assertTrue(pack.gate.blocked.isEmpty())
    }

    @Test
    fun oneChangedByteBreaksTheSignature() {
        val container = build().container
        val mutated = container.copyOf()
        val offset = container.size / 2
        mutated[offset] = (mutated[offset].toInt() xor 1).toByte()
        rejected(verify(mutated), "bad_signature")
    }

    @Test
    fun anOldSignatureCannotCoverANewManifest() {
        val decoded = ReleaseContainer.decode(build().container)
        val manifest = Json.parseToJsonElement(decoded.region.manifestBytes.decodeToString()).jsonObject
        val edited = JsonObject(manifest + ("fact_version" to JsonPrimitive("TEST_ONLY-d1-fact-2")))
            .toString().encodeToByteArray()
        val region = ReleaseContainer.unsignedRegion(edited, decoded.region.blocks)
        val container = ReleaseContainer.assemble(SignedRegion(region, edited, decoded.region.blocks),
            ReleaseFormat.SIGNATURE_ALGORITHM, decoded.keyId, decoded.signature)
        rejected(verify(container), "bad_signature")
    }

    @Test
    fun anotherCountysNarrativeCannotBeSplicedIntoThisCounty() {
        val own = build()
        val otherFacts = mutate(factBytes) { facts ->
            JsonObject(facts + mapOf(
                "area_id" to JsonPrimitive("cn-510114"),
                "pack_id" to JsonPrimitive("cn-510114-fact"),
                "area" to buildJsonObject {
                    put("area_id", "cn-510114"); put("parent_id", "cn-510100")
                    put("province", "四川省"); put("city", "成都市"); put("district", "新都区")
                }
            ))
        }
        val otherNarratives = mutate(emptyNarratives) { narratives ->
            JsonObject(narratives + mapOf(
                "area_id" to JsonPrimitive("cn-510114"),
                "pack_id" to JsonPrimitive("cn-510114-narrative")
            ))
        }
        val other = build(fact = otherFacts, narrative = otherNarratives, areaId = "cn-510114")
        val spliced = CountyBlocks(own.blocks.factManifest, own.blocks.facts,
            other.blocks.narrativeManifest, other.blocks.narratives)
        val container = PackBuilder.assembleBlocks(D1Sample.AREA_ID, D1Sample.PARENT_ID, spliced, "v-fact", "v-narrative",
            ReleaseFormat.KIND_FACT_ONLY, keyId, ReleaseFormat.SCOPE_TEST_ONLY, commit, 20, key.private).container
        val refusal = rejection(verify(container))
        assertEquals("inner_binding", refusal.code)
        assertEquals("narrative_manifest.area_id", refusal.detail)
    }

    @Test
    fun unregisteredKeyIdIsRejected() {
        rejected(verify(build(declaredKeyId = "test-only-unregistered").container), "unknown_key_id")
    }

    @Test
    fun productionScopeCannotBeAuthenticatedByATestOnlyAnchor() {
        rejected(verify(build(scope = ReleaseFormat.SCOPE_PRODUCTION).container), "unknown_key_id")
    }

    @Test
    fun anEmptyTrustStoreAcceptsNothing() {
        rejected(verify(build().container, store = TrustStore.EMPTY), "no_trusted_publisher")
    }

    @Test
    fun aPayloadCannotNominateItsOwnPublicKey() {
        val decoded = ReleaseContainer.decode(build().container)
        val manifest = Json.parseToJsonElement(decoded.region.manifestBytes.decodeToString()).jsonObject
        val hostile = JsonObject(manifest + ("public_key" to JsonPrimitive(Pem.publicPem(key.public))))
            .toString().encodeToByteArray()
        val region = ReleaseContainer.unsignedRegion(hostile, decoded.region.blocks)
        val container = ReleaseContainer.assemble(SignedRegion(region, hostile, decoded.region.blocks),
            ReleaseFormat.SIGNATURE_ALGORITHM, decoded.keyId, decoded.signature)
        rejected(verify(container), "payload_trust_claim")
    }

    @Test
    fun wrongAppCodeIsRejected() {
        rejected(verify(build(minimumCode = 21).container, supportedCode = 20), "minimum_code")
    }

    @Test
    fun duplicateUnknownAndMissingBlocksAreRejected() {
        val manifest = build().manifest.canonicalBytes()
        val blocks = build().blocks.list()
        assertEquals("duplicate_block", rejection(verify(frame(manifest, blocks, listOf(1, 1, 3, 4)))).code)
        assertEquals("unknown_block", rejection(verify(frame(manifest, blocks, listOf(9, 2, 3, 4)))).code)
        assertEquals("missing_block", rejection(verify(frame(manifest, blocks.take(3), listOf(1, 2, 3)))).code)
        assertEquals("block_kind", rejection(verify(frame(manifest, blocks, listOf(2, 1, 3, 4)))).code)
    }

    @Test
    fun trailingBytesAndTruncationAreRejected() {
        val container = build().container
        rejected(verify(container + byteArrayOf(0)), "trailing_bytes")
        assertTrue(verify(container.copyOf(container.size - 8)) is ReleaseVerdict.Rejected)
        assertTrue(verify(ByteArray(64)) is ReleaseVerdict.Rejected)
    }

    @Test
    fun oversizedContainerIsRejectedBeforeParsing() {
        rejected(verify(ByteArray(ReleaseFormat.MAX_CONTAINER + 1)), "container_too_large")
    }

    @Test
    fun restrictedSourceRightsRejectThePackEvenWithAValidSignature() {
        val hostile = mutate(factBytes) { facts ->
            withEntry(facts, "sources", 0) { JsonObject(it + ("rights" to JsonPrimitive("All rights reserved"))) }
        }
        val refusal = rejection(verify(build(fact = hostile).container))
        assertEquals("content_rejected", refusal.code)
    }

    @Test
    fun missingClaimDependencyIsRejected() {
        val hostile = mutate(storyNarratives) { narratives ->
            withEntry(narratives, "narratives", 0) { story ->
                JsonObject(story + ("supported_claim_refs" to JsonArray(emptyList())))
            }
        }
        assertEquals("content_rejected", rejection(verify(build(narrative = hostile).container)).code)
    }

    @Test
    fun staleClaimRevisionIsRejected() {
        val hostile = mutate(factBytes) { facts ->
            withEntry(facts, "claims", 0) { claim ->
                JsonObject(claim + ("text" to JsonPrimitive("TEST_ONLY 被改写但未更新修订的Claim")))
            }
        }
        assertEquals("content_rejected",
            rejection(verify(build(fact = hostile, narrative = storyNarratives).container)).code)
    }

    @Test
    fun pendingVisitorGateIsRejected() {
        val hostile = mutate(storyNarratives) { narratives ->
            withEntry(narratives, "narratives", 0) { JsonObject(it + ("visitor_gate" to JsonPrimitive("pending"))) }
        }
        assertEquals("content_rejected", rejection(verify(build(narrative = hostile).container)).code)
    }

    @Test
    fun candidateReviewStatusIsRejected() {
        val hostile = mutate(storyNarratives) { narratives ->
            withEntry(narratives, "narratives", 0) { JsonObject(it + ("review_status" to JsonPrimitive("candidate"))) }
        }
        assertEquals("content_rejected", rejection(verify(build(narrative = hostile).container)).code)
    }

    @Test
    fun aSignedOuterManifestCannotRelabelAnotherCounty() {
        val built = build()
        val relabelled = PackBuilder.assembleBlocks("cn-510114", "cn-510100", built.blocks,
            built.manifest.factVersion, built.manifest.narrativeVersion, built.manifest.contentKind,
            keyId, ReleaseFormat.SCOPE_TEST_ONLY, commit, 20, key.private).container
        val refusal = rejection(verify(relabelled))
        assertEquals("inner_binding", refusal.code)
        assertEquals("fact_manifest.area_id", refusal.detail)
    }

    @Test
    fun aSignedOuterManifestCannotRelabelTheFactVersion() {
        val built = build()
        val relabelled = PackBuilder.assembleBlocks(D1Sample.AREA_ID, D1Sample.PARENT_ID, built.blocks,
            "TEST_ONLY-d1-fact-9", built.manifest.narrativeVersion, built.manifest.contentKind,
            keyId, ReleaseFormat.SCOPE_TEST_ONLY, commit, 20, key.private).container
        val refusal = rejection(verify(relabelled))
        assertEquals("inner_binding", refusal.code)
        assertEquals("fact_manifest.content_version", refusal.detail)
    }

    @Test
    fun aSignedOuterManifestCannotRelabelTheAppRequirement() {
        val built = build(minimumCode = 20)
        val relabelled = PackBuilder.assembleBlocks(D1Sample.AREA_ID, D1Sample.PARENT_ID, built.blocks,
            built.manifest.factVersion, built.manifest.narrativeVersion, built.manifest.contentKind,
            keyId, ReleaseFormat.SCOPE_TEST_ONLY, commit, 19, key.private).container
        val refusal = rejection(verify(relabelled))
        assertEquals("inner_binding", refusal.code)
        assertEquals("fact_manifest.minimum_code", refusal.detail)
    }

    @Test
    fun anOlderSignedPackIsRefusedOnceTheCallerRaisesTheFloor() {
        val older = build(sequence = 3)
        assertTrue(verify(older.container, minimumSequence = 3) is ReleaseVerdict.Accepted)
        rejected(verify(older.container, minimumSequence = 4), "sequence_rollback")
    }

    @Test
    fun unsignedBytesAndTheWholeContainerRepeatForTheSameKey() {
        val first = build()
        val second = build()
        first.blocks.list().zip(second.blocks.list()).forEach { (a, b) -> assertArrayEquals(a, b) }
        assertEquals(first.unsignedDigest, second.unsignedDigest)
        // PKCS#1 v1.5 is deterministic: nothing random or time-based may enter the container.
        assertArrayEquals(first.container, second.container)
    }

    @Test
    fun containerCarriesNoMachineIdentity() {
        val text = String(build().container, Charsets.ISO_8859_1)
        listOf("C:\\", "H:\\", "Users", "Church", "kitt-d1-keys", "gradle").forEach {
            assertFalse("container must not leak $it", text.contains(it))
        }
        assertFalse(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:").containsMatchIn(text))
    }

    @Test
    fun committedSampleMatchesAFreshRender() {
        D1Sample.render().forEach { (path, bytes) ->
            assertArrayEquals("committed sample drifted: $path", bytes, File("../$path").readBytes())
        }
    }

    @Test
    fun manifestEncodingIsCanonicalAndRoundTrips() {
        val built = build()
        val parsed = ReleaseManifest.parse(built.manifest.canonicalBytes())
        assertEquals(built.manifest, parsed)
        assertArrayEquals(built.manifest.canonicalBytes(), parsed.canonicalBytes())
        assertEquals("SHA256withRSA", ReleaseContainer.decode(built.container).algorithm)
    }

    @Test
    fun acceptedBlocksStayReadableByTheD0Reader() {
        val pack = accepted(verify(build().container))
        val raw = CountyBytes(pack.blocks.factManifest, pack.blocks.facts, pack.blocks.narrativeManifest, pack.blocks.narratives)
        val county = CountyPackLoader { CountyProvenance.TEST_ONLY }.read(raw.copy(expectedSha256 = countyDigest(raw)))
        assertEquals(4, county.factCount)
        assertEquals(2, county.entities.size)
        assertEquals(0, county.stories.stories.size)
    }

    private fun mutate(source: ByteArray, transform: (JsonObject) -> JsonObject): ByteArray =
        transform(Json.parseToJsonElement(source.decodeToString()).jsonObject).toString().encodeToByteArray()

    private fun withEntry(array: JsonObject, field: String, index: Int, transform: (JsonObject) -> JsonObject) =
        JsonObject(array + (field to JsonArray(array.getValue(field).jsonArray.mapIndexed { position, element ->
            if (position == index) transform(element.jsonObject) else element
        })))

    /** Hostile frame construction: only structural refusals are expected from these bytes. */
    private fun frame(manifest: ByteArray, blocks: List<ByteArray>, kinds: List<Int>): ByteArray {
        val size = 13 + manifest.size + blocks.zip(kinds).sumOf { (block, _) -> 5 + block.size }
        return ByteBuffer.allocate(size).apply {
            put(ReleaseFormat.MAGIC.encodeToByteArray())
            put(ReleaseFormat.FORMAT_VERSION.toByte())
            putInt(manifest.size)
            put(manifest)
            blocks.zip(kinds).forEach { (block, kind) ->
                put(kind.toByte()); putInt(block.size); put(block)
            }
        }.array()
    }
}
