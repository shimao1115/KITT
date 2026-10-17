package com.kitt.reader

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.io.File

/** TEST_ONLY synthetic content. Only the administrative labels are real. No test geometry. */
internal object CountyFixture {
    val key: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val categories = StoryCategory.entries
    fun source(id: String) = buildJsonObject {
        put("source_id", id); put("title", "TEST_ONLY 合成来源 $id"); put("url", "https://example.invalid/$id")
        put("kind", "OTHER"); put("access_status", "read"); put("accessed_at", "2026-10-17")
        put("rights", "CC-BY-4.0")
    }.revised("registry_revision")
    fun facts(id: String = "cn-420502"): JsonObject {
        val xindu = id == "cn-510114"
        return buildJsonObject {
            put("schema_version", 2); put("pack_id", "$id-fact"); put("content_version", "TEST_ONLY-fact-1")
            put("area_id", id); put("retrieved_at", "2026-10-17")
            put("area", buildJsonObject { put("area_id", id); put("parent_id", if (xindu) "cn-510100" else "cn-420500")
                put("province", if (xindu) "四川省" else "湖北省"); put("city", if (xindu) "成都市" else "宜昌市")
                put("district", if (xindu) "新都区" else "西陵区") })
            put("sources", JsonArray(listOf(source("s0"), source("s1"))))
            put("source_permissions", buildJsonObject { put("s0", "allowed"); put("s1", "allowed") })
            put("entities", JsonArray((0..1).map { e -> buildJsonObject {
                put("entity_id", "e$e"); put("title", "TEST_ONLY 合成同名对象$e"); put("family", "PEOPLE")
                put("basic_facts", JsonArray((0..1).map { i -> buildJsonObject {
                    put("fact_id", "b$e-$i"); put("summary", "TEST_ONLY 合成基础说明 $e/$i，不是地方史实。")
                    put("coverage", "TEST_ONLY 基础$e/$i"); put("source_ids", JsonArray(listOf(JsonPrimitive("s$e"))))
                } }))
            } }))
            put("claims", JsonArray((0..6).map { i -> buildJsonObject {
                put("claim_id", "c$i"); put("entity_id", "e${i % 2}"); put("area_ids", JsonArray(listOf(JsonPrimitive(id))))
                put("text", "TEST_ONLY 合成Claim $i，不是已核实地方知识。")
                put("status", "verified"); put("temporal_class", "stable"); put("rights", "CC-BY-4.0")
                put("evidence", JsonArray(listOf(buildJsonObject { put("source_id", "s${i % 2}"); put("locator", "TEST_ONLY paragraph $i")
                    put("relation", "supports"); put("accepted_source_revision", source("s${i % 2}").text("registry_revision")) })))
            }.revised("revision") }))
            put("claim_permissions", buildJsonObject { (0..6).forEach { put("c$it", "allowed") } })
        }
    }
    fun narratives(f: JsonObject = facts()): JsonObject = buildJsonObject {
        val id = f.text("area_id")
        put("schema_version", 2); put("pack_id", "$id-narrative"); put("content_version", "TEST_ONLY-narrative-9"); put("area_id", id)
        put("narratives", JsonArray((0..6).map { i ->
            val text = "TEST_ONLY 合成工程夹具，不可作为西陵或新都的已核实知识。只用来验证县包隔离、审核依赖和六类软排序。".repeat(3)
            buildJsonObject {
                put("narrative_id", "story-$i"); put("story_type", if (i == 6) "followup" else "main")
                put("area_id", id); put("entity_id", "e${i % 2}"); put("title", "TEST_ONLY 合成篇$i")
                put("text", text); put("text_sha256", OfflinePack.sha256(text.encodeToByteArray()))
                put("review_status", "approved"); put("fact_gate", "passed"); put("visitor_gate", "passed")
                put("approval_ref", "TEST_ONLY_NOT_REAL_APPROVAL"); put("license", "CC-BY-4.0")
                put("primary_category", categories[i % 6].name); put("secondary_categories", JsonArray(emptyList()))
                put("supported_claim_refs", JsonArray(listOf(buildJsonObject { put("claim_id", "c$i")
                    put("accepted_revision", f.getValue("claims").jsonArray[i].jsonObject.text("revision")) })))
            }
        }))
    }
    fun input(f: JsonObject = facts(), n: JsonObject = narratives(f)): CountyBytes {
        fun manifest(d: JsonObject, kind: String): Pair<ByteArray, ByteArray> {
            val b = d.toString().encodeToByteArray()
            return buildJsonObject { put("schema_version", 2); put("kind", kind); put("minimum_code", 20)
                put("area_id", d.getValue("area_id")); put("pack_id", d.getValue("pack_id")); put("content_version", d.getValue("content_version"))
                put("bytes", b.size); put("sha256", OfflinePack.sha256(b)) }.toString().encodeToByteArray() to b
        }
        val (fm, fb) = manifest(f, "fact"); val (nm, nb) = manifest(n, "narrative")
        val raw = CountyBytes(fm, fb, nm, nb)
        return raw.copy(expectedSha256 = countyDigest(raw))
    }
    private fun Signature.envelope(b: CountyBytes) {
        listOf(b.factManifest, b.facts, b.narrativeManifest, b.narratives).forEach {
            update(ByteBuffer.allocate(4).putInt(it.size).array()); update(it)
        }
    }
    fun loader(b: CountyBytes, signer: KeyPair = key, corrupt: Boolean = false): CountyPackLoader {
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(signer.private); envelope(b); sign() }
        if (corrupt) signature[0] = (signature[0].toInt() xor 1).toByte()
        return CountyPackLoader { candidate -> runCatching {
            Signature.getInstance("SHA256withRSA").run { initVerify(key.public); envelope(candidate); verify(signature) }
        }.getOrDefault(false).let { if (it) CountyProvenance.TEST_ONLY else null } }
    }
    fun read(f: JsonObject = facts(), n: JsonObject = narratives(f)): CountyPack = input(f, n).let { loader(it).read(it) }
}

internal fun JsonObject.revised(field: String) = JsonObject(this + (field to JsonPrimitive(countyRevision(this, field))))
internal fun JsonObject.changed(key: String, value: String) = JsonObject(this + (key to JsonPrimitive(value)))
internal fun JsonObject.mutate(array: String, i: Int = 0, transform: (JsonObject) -> JsonObject) =
    JsonObject(this + (array to JsonArray(getValue(array).jsonArray.mapIndexed { j, v -> if (i == j) transform(v.jsonObject) else v })))

class CountyPacksTest {
    private fun rejected(input: CountyBytes, reason: String? = null) {
        val normalized = input.copy(expectedSha256 = countyDigest(input))
        val error = assertThrows(Exception::class.java) { CountyFixture.loader(normalized).read(normalized) }
        if (reason != null) assertEquals(reason, error.message)
    }
    @Test fun independentVersionsAndMultipleMainStoriesAreReadByIds() {
        val p = CountyFixture.read()
        assertEquals("TEST_ONLY-fact-1", p.factVersion); assertEquals("TEST_ONLY-narrative-9", p.narrativeVersion)
        assertEquals(2, p.entities.size); assertEquals(4, p.factCount); assertEquals(2, p.sources.size); assertEquals(7, p.claims.size)
        assertEquals(6, p.stories.stories.count { it.type == StoryType.main }); assertTrue(p.stories.blocked.isEmpty())
        assertEquals(StoryCategory.entries, p.stories.stories.take(6).map { it.category })
    }
    @Test fun nonAdjacentCountiesUseScopedIdsAndCityIndexesWithoutBodyCopies() {
        val x = CountyFixture.read(CountyFixture.facts("cn-510114")); val y = CountyFixture.read()
        val c = CountyCatalog(listOf(x, y))
        assertEquals(listOf("cn-510114"), c.cities["cn-510100"]); assertEquals(listOf("cn-420502"), c.cities["cn-420500"])
        assertSame(x, c.county("cn-510114")); assertSame(y.entities["e0"], c.entity("cn-420502", "e0"))
        assertEquals(c.entity("cn-510114", "e0")!!.title, c.entity("cn-420502", "e0")!!.title)
        assertNotEquals(x.stories.stories.first().marker, y.stories.stories.first().marker)
        assertNull(c.county("cn-000001")); assertNull(c.entity("cn-000001", "e0"))
    }
    @Test fun duplicateCountiesAndConflictingParentIndexesNeverOverwrite() {
        val x = CountyFixture.read(); assertThrows(IllegalArgumentException::class.java) { CountyCatalog(listOf(x, x)) }
        val other = CountyFixture.read(CountyFixture.facts("cn-510114"))
        assertThrows(IllegalArgumentException::class.java) { CountyCatalog(listOf(x, other.copy(area = other.area.copy(parentId = x.area.parentId)))) }
    }
    @Test fun shaIsNotTrustAndPayloadFlagsCannotAuthorizeAnything() {
        val b = CountyFixture.input()
        assertThrows(IllegalStateException::class.java) { CountyPackLoader().read(b) }
        val forged = CountyFixture.input(CountyFixture.facts().changed("trusted", "true"))
        assertThrows(IllegalStateException::class.java) { CountyPackLoader().read(forged) }
    }
    @Test fun testOnlySignatureRejectsWrongKeyBadSignatureAndPostSignatureChanges() {
        val b = CountyFixture.input()
        val wrong = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertThrows(IllegalStateException::class.java) { CountyFixture.loader(b, wrong).read(b) }
        assertThrows(IllegalStateException::class.java) { CountyFixture.loader(b, corrupt = true).read(b) }
        val l = CountyFixture.loader(b)
        assertThrows(IllegalStateException::class.java) { l.read(b.copy(narratives = b.narratives + 32.toByte())) }
        assertThrows(IllegalStateException::class.java) { l.read(b.copy(factManifest = b.factManifest + 32.toByte())) }
    }
    @Test fun evenAuthenticatedBadHashesAndLengthsAreFatal() {
        val b = CountyFixture.input()
        rejected(b.copy(facts = b.facts + 32.toByte()), "integrity")
        rejected(b.copy(narrativeManifest = Json.parseToJsonElement(b.narrativeManifest.decodeToString()).jsonObject.changed("sha256", "0".repeat(64)).toString().encodeToByteArray()), "integrity")
        val wrongEnvelope = b.copy(expectedSha256 = "0".repeat(64))
        assertEquals("envelope_integrity", assertThrows(IllegalArgumentException::class.java) {
            CountyFixture.loader(wrongEnvelope).read(wrongEnvelope) }.message)
    }
    @Test fun boundedSizeSchemaMinimumAppAndMalformedUtf8AreFatal() {
        val b = CountyFixture.input()
        rejected(b.copy(facts = ByteArray(500001)), "size"); rejected(b.copy(factManifest = ByteArray(16001)), "size")
        rejected(b.copy(narratives = byteArrayOf()), "size")
        rejected(b.copy(factManifest = byteArrayOf(0xff.toByte())))
        val m = Json.parseToJsonElement(b.factManifest.decodeToString()).jsonObject
        for ((key, value) in listOf("schema_version" to "99", "minimum_code" to "999", "minimum_code" to "-1", "kind" to "narrative"))
            rejected(b.copy(factManifest = m.changed(key, value).toString().encodeToByteArray()))
        rejected(CountyFixture.input(CountyFixture.facts().changed("schema_version", "99")))
    }
    @Test fun mismatchedAreaManifestsAndPackIdsAreFatal() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        rejected(CountyFixture.input(f, n.changed("area_id", "cn-510114")), "area_mismatch")
        rejected(CountyFixture.input(f.changed("pack_id", "cn-510114-fact"), n), "pack_id")
        val wrong = JsonObject(f + ("area" to f.getValue("area").jsonObject.changed("area_id", "cn-420500")))
        rejected(CountyFixture.input(wrong, n), "area_id")
    }
    @Test fun resealedCountyCannotBeIndexedUnderAnotherPrefectureOrProvince() {
        val f = CountyFixture.facts()
        for (parent in listOf("cn-420100", "cn-510100")) {
            val wrong = JsonObject(f + ("area" to f.getValue("area").jsonObject.changed("parent_id", parent)))
            rejected(CountyFixture.input(wrong), "parent_relationship")
        }
    }
    @Test fun specialAdministrativeLevelsNeedExplicitRulesRatherThanAnInventedPrefecture() {
        for (id in listOf("cn-429004", "cn-420002")) {
            val f = CountyFixture.facts(id)
            val special = JsonObject(f + ("area" to f.getValue("area").jsonObject.changed("parent_id", id.dropLast(2) + "00")))
            rejected(CountyFixture.input(special), "parent_relationship")
        }
    }
    @Test fun duplicateEntitySourceClaimStoryAndBasicFactIdsAreFatal() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        for (array in listOf("entities", "sources", "claims")) {
            val original = f.getValue(array).jsonArray
            rejected(CountyFixture.input(JsonObject(f + (array to JsonArray(original + original.first()))), n), "duplicate_id")
        }
        val stories = n.getValue("narratives").jsonArray
        rejected(CountyFixture.input(f, JsonObject(n + ("narratives" to JsonArray(stories + stories.first())))), "duplicate_id")
        val dupFact = f.mutate("entities") { e -> val a = e.getValue("basic_facts").jsonArray
            JsonObject(e + ("basic_facts" to JsonArray(a + a.first()))) }
        rejected(CountyFixture.input(dupFact, n), "duplicate_fact")
    }
    @Test fun indexAndFieldLimitsAreChecked() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        rejected(CountyFixture.input(JsonObject(f + ("claims" to JsonArray(List(201) { f.getValue("claims").jsonArray.first() }))), n), "index_limit")
        rejected(CountyFixture.input(f.mutate("entities") { it.changed("title", "x".repeat(81)) }, n), "field_size")
        rejected(CountyFixture.input(f.mutate("claims") { it.changed("entity_id", "missing") }, n), "dangling_entity")
    }
    @Test fun pendingReviewRightsAndTextFailuresBlockOnlyTheirStoryBeforeRanking() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        for ((key, value) in listOf("visitor_gate" to "pending", "review_status" to "candidate", "fact_gate" to "pending",
            "approval_ref" to "", "license" to "unknown", "text_sha256" to "bad", "primary_category" to "PEOPLE", "area_id" to "cn-510114")) {
            val p = CountyFixture.read(f, n.mutate("narratives") { it.changed(key, value) })
            assertEquals(key, 6, p.stories.stories.size); assertEquals(4, p.factCount); assertEquals(1, p.stories.blocked.size)
            val j = Journey({ 1000000L }, TestVoice()); j.start()
            assertFalse(p.stories.select(j, p.area.identity, false, mapOf(StoryCategory.NATURE to StoryInterest.MORE))!!.id.endsWith("story-0"))
        }
    }
    @Test fun withdrawnContestedOrChangedClaimAffectsExactlyItsReferencingStory() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        for (status in listOf("withdrawn", "contested", "rejected", "lead")) {
            val p = CountyFixture.read(f.mutate("claims") { it.changed("status", status).revised("revision") }, n)
            assertEquals(6, p.stories.stories.size); assertEquals("claim_status", p.stories.blocked["story-0"])
            assertEquals(4, p.factCount)
        }
        val p = CountyFixture.read(f.mutate("claims") { it.changed("text", "TEST_ONLY revised").revised("revision") }, n)
        assertEquals(6, p.stories.stories.size); assertEquals("claim_revision", p.stories.blocked["story-0"])
    }
    @Test fun missingClaimAndCrossCountySameIdCannotBeResolvedByAnotherCounty() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        val missing = JsonObject(f + ("claims" to JsonArray(f.getValue("claims").jsonArray.drop(1))))
        val p = CountyFixture.read(missing, n)
        val c = CountyCatalog(listOf(p, CountyFixture.read(CountyFixture.facts("cn-510114"))))
        assertEquals("missing_claim", c.county("cn-420502")!!.stories.blocked["story-0"])
        val wrong = CountyFixture.read(f.mutate("claims") { JsonObject(it + ("area_ids" to JsonArray(listOf(JsonPrimitive("cn-510114"))))).revised("revision") }, n)
        assertEquals("claim_area", wrong.stories.blocked["story-0"])
    }
    @Test fun sourceLossRightsDisputeAndRegistryRevisionPreciselyDisableUsers() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        val missing = JsonObject(f + ("sources" to JsonArray(f.getValue("sources").jsonArray.drop(1))))
        val p = CountyFixture.read(missing, n)
        assertEquals(listOf("cn-420502/story-1", "cn-420502/story-3", "cn-420502/story-5"), p.stories.stories.map { it.id })
        assertEquals(2, p.factCount); assertEquals(2, p.blockedFacts.size)
        for (field in listOf("access_status", "rights", "registry_revision")) {
            val q = CountyFixture.read(f.mutate("sources") { it.changed(field, if (field == "rights") "" else "withdrawn") }, n)
            assertEquals(3, q.stories.stories.size); assertEquals(2, q.factCount)
        }
        val denied = JsonObject(f + ("source_permissions" to f.getValue("source_permissions").jsonObject.changed("s0", "disputed")))
        assertEquals(3, CountyFixture.read(denied, n).stories.stories.size)
        val deniedClaim = JsonObject(f + ("claim_permissions" to f.getValue("claim_permissions").jsonObject.changed("c0", "unknown")))
        assertEquals(6, CountyFixture.read(deniedClaim, n).stories.stories.size)
    }
    private val restrictedRights = listOf("restricted", "all rights reserved", "ALL RIGHTS RESERVED",
        "  Restricted  ", "CC-BY-4.0; restricted", "不允许转载", "custom permission")

    /** Refresh source, evidence, Claim and Story references so rejection cannot be a stale-hash side effect. */
    private fun sourceRights(rights: String): JsonObject {
        val sourceChanged = CountyFixture.facts().mutate("sources") { it.changed("rights", rights).revised("registry_revision") }
        val revision = sourceChanged.getValue("sources").jsonArray[0].jsonObject.text("registry_revision")
        return JsonObject(sourceChanged + ("claims" to JsonArray(sourceChanged.getValue("claims").jsonArray.map { value ->
            val claim = value.jsonObject
            JsonObject(claim + ("evidence" to JsonArray(claim.getValue("evidence").jsonArray.map { entry ->
                val e = entry.jsonObject
                if (e.text("source_id") == "s0") e.changed("accepted_source_revision", revision) else e
            }))).revised("revision")
        })))
    }
    @Test fun restrictedSourceRightsOverrideAllowedPermissionAndFreshAcceptedRevisions() {
        for (rights in restrictedRights) {
            val f = sourceRights(rights)
            assertEquals("allowed", f.getValue("source_permissions").jsonObject.text("s0"))
            val p = CountyFixture.read(f)
            assertEquals(rights, listOf("cn-420502/story-1", "cn-420502/story-3", "cn-420502/story-5"), p.stories.stories.map { it.id })
            assertEquals(rights, "source_rights", p.stories.blocked["story-0"])
            assertEquals(rights, 2, p.factCount); assertEquals(rights, 2, p.blockedFacts.size)
        }
    }
    @Test fun restrictedClaimRightsOverrideAllowedPermissionAndFreshAcceptedRevision() {
        for (rights in restrictedRights) {
            val f = CountyFixture.facts().mutate("claims") { it.changed("rights", rights).revised("revision") }
            assertEquals("allowed", f.getValue("claim_permissions").jsonObject.text("c0"))
            val p = CountyFixture.read(f)
            assertEquals(rights, 6, p.stories.stories.size)
            assertEquals(rights, mapOf("story-0" to "claim_rights"), p.stories.blocked)
            assertEquals(rights, 4, p.factCount)
        }
    }
    @Test fun explicitLicenseRemainsUsableWithFreshDependencyFingerprints() {
        val p = CountyFixture.read(sourceRights(" cc-by-4.0 "))
        assertEquals(7, p.stories.stories.size); assertEquals(4, p.factCount)
        assertTrue(p.stories.blocked.isEmpty()); assertTrue(p.blockedFacts.isEmpty())
    }
    @Test fun existingCuratedSummaryScopesRemainDistinctFromUnclearedSourceRights() {
        val f = Json.parseToJsonElement(File("src/main/assets/offline/ys-facts.json").readText()).jsonObject
        val unreviewed = setOf("旧包仅保留既有事实摘要，不新增转载", "未取得全文核读；不复制")
        f.getValue("sources").jsonArray.forEach { source ->
            val rights = source.jsonObject.text("rights")
            assertEquals(rights, rights !in unreviewed, countyRights(rights))
        }
        f.getValue("claims").jsonArray.forEach { assertTrue(countyRights(it.jsonObject.text("rights"))) }
    }
    @Test fun basicFactsRemainReadableWhenAllNarrativesArePending() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        val pending = JsonObject(n + ("narratives" to JsonArray(n.getValue("narratives").jsonArray.map { it.jsonObject.changed("visitor_gate", "pending") })))
        val p = CountyFixture.read(f, pending); val region = ExploreCatalog.from(CountyCatalog(listOf(p))).regions.single()
        assertEquals(4, region.factCount); assertTrue(region.stories.isEmpty()); assertTrue(region.storyStatus.contains("仅基础事实"))
    }
    @Test fun interestsOnlyBiasEligibleSameCountyMainsAndDefaultKeepsOrder() {
        val p = CountyFixture.read(); val x = CountyFixture.read(CountyFixture.facts("cn-510114"))
        val j = Journey({ 1000000L }, TestVoice()); j.start()
        assertEquals("cn-420502/story-0", p.stories.select(j, p.area.identity, false)!!.id)
        assertEquals("cn-420502/story-4", p.stories.select(j, p.area.identity, false, mapOf(StoryCategory.HISTORY to StoryInterest.MORE))!!.id)
        assertNull(p.stories.select(j, x.area.identity, false))
        assertNull(x.stories.select(j, p.area.identity, false))
    }
    @Test fun completedMainFollowupAndAttemptMemoryRemainScopedAcrossCounties() {
        val p = CountyFixture.read(); val x = CountyFixture.read(CountyFixture.facts("cn-510114"))
        val voice = TestVoice(); val j = Journey({ 1000000L }, voice); j.start()
        val main = p.stories.select(j, p.area.identity, false)!!
        assertNull(p.stories.select(j, p.area.identity, true))
        j.deliver(j.ticket(true), main.response().json(), narrationArea = p.area.identity, localStoryId = main.id); voice.finish()
        assertEquals("cn-420502/story-6", p.stories.select(j, p.area.identity, true)!!.id)
        assertEquals("cn-420502/story-1", p.stories.select(j, p.area.identity, false)!!.id)
        assertEquals("cn-510114/story-0", x.stories.select(j, x.area.identity, false)!!.id)
        assertNull(x.stories.select(j, p.area.identity, true))
    }
    @Test fun differentMainIdCannotRetellAnAlreadyAttemptedClaim() {
        val f = CountyFixture.facts(); val n = CountyFixture.narratives(f)
        val sameClaim = n.mutate("narratives", 2) { story -> JsonObject(story +
            ("supported_claim_refs" to n.getValue("narratives").jsonArray[0].jsonObject.getValue("supported_claim_refs"))) }
        val p = CountyFixture.read(f, sameClaim); val voice = TestVoice()
        val j = Journey({ 1000000L }, voice); j.start()
        val main = p.stories.select(j, p.area.identity, false)!!
        j.deliver(j.ticket(true), main.response().json(), narrationArea = p.area.identity, localStoryId = main.id); voice.finish()
        assertEquals("cn-420502/story-4", p.stories.select(j, p.area.identity, false,
            mapOf(StoryCategory.NATURE to StoryInterest.MORE, StoryCategory.CULTURE to StoryInterest.MORE,
                StoryCategory.HISTORY to StoryInterest.MORE))!!.id)
    }
    @Test fun genericBrowsingHasNoGeometryAndCannotChangeJourneyFixOrAutoNarrate() {
        val p = CountyFixture.read(); val j = Journey({ 1000000L }, TestVoice()); j.start()
        val fix = Fix(30.83, 104.16, 1000000L, accuracy = 10.0, source = FixSource.GPS); j.location(fix)
        ExploreCatalog.from(CountyCatalog(listOf(p))).regions.single().search("西陵区")
        assertSame(fix, j.fix); assertNull(p.factsView.resolve(fix, 1000000L))
        assertTrue(p.factsView.overview(p.area.identity)!!.toString().contains("西陵区审校离线包"))
        assertFalse(p.factsView.overview(p.area.identity)!!.toString().contains("新都区审校离线包"))
        assertEquals(Action.SILENT, OfflineNarrator(p.factsView, p.stories).response(j, 1000000L).action)
        j.start(); assertEquals(Action.SILENT, OfflineNarrator(p.factsView, p.stories).response(j, 1000000L).action)
    }
    @Test fun realBundledAdapterPreservesSixObjectsTwentyTwoFactsAndSixBasicSources() {
        val dir = File("src/main/assets/offline")
        fun read(n: String) = File(dir, n).readBytes()
        val legacy = OfflinePack.parse(read("xindu-manifest.json"), read("xindu-facts.json"), read("xindu-geometry.geojson"))
        val p = BundledCountyPacks.adapt(legacy, read("ys-facts-manifest.json"), read("ys-facts.json"), read("ys-narratives-manifest.json"), read("ys-narratives.json"))
        assertEquals(6, p.entities.size); assertEquals(22, p.factCount); assertEquals(6, p.factsView.sources.size)
        assertSame(legacy, p.factsView); assertTrue(p.stories.stories.isEmpty()); assertTrue(p.runtimeStories.stories.isEmpty())
        assertEquals(legacy.objects, ExploreCatalog.from(CountyCatalog(listOf(p))).regions.single().search(""))
        val j = Journey({ 1000000L }, TestVoice()); j.start(); j.location(Fix(30.83, 104.16, 1000000L, accuracy = 10.0, source = FixSource.GPS))
        assertEquals(OfflineNarrator(legacy).response(j, 1000000L), OfflineNarrator(p.factsView, p.runtimeStories).response(j, 1000000L))
    }
}
