package com.kitt.reader

import android.content.Context
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** D0 is a bounded in-memory reader, not a file importer or an installation service. */
internal data class CountyBytes(val factManifest: ByteArray, val facts: ByteArray,
    val narrativeManifest: ByteArray, val narratives: ByteArray, val expectedSha256: String = "") {
    fun snapshot() = copy(factManifest = factManifest.copyOf(), facts = facts.copyOf(),
        narrativeManifest = narrativeManifest.copyOf(), narratives = narratives.copyOf())
}

/** Authenticated by the caller's verifier, never read from the JSON. No real external publisher in D0. */
internal enum class CountyProvenance { BUNDLED_APK, TEST_ONLY }
internal fun countyDigest(b: CountyBytes): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(b.factManifest, b.facts, b.narrativeManifest, b.narratives).forEach {
        digest.update(ByteBuffer.allocate(4).putInt(it.size).array()); digest.update(it)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class CountyArea(val id: String, val parentId: String, val identity: AreaIdentity)
internal data class CountyEntity(val id: String, val title: String, val family: TopicFamily,
    val facts: List<PackFact>)
internal data class CountyPack(val area: CountyArea, val factVersion: String, val narrativeVersion: String,
    val entities: Map<String, CountyEntity>, val sources: Map<String, JsonObject>,
    val claims: Map<String, JsonObject>, val stories: StoryPacks, val blockedFacts: Map<String, String>,
    val factsView: OfflinePack, val provenance: CountyProvenance, val runtimeStories: StoryPacks = stories) {
    val factCount get() = entities.values.sumOf { it.facts.size }
    val status get() = if (stories.stories.isEmpty()) "仅基础事实；目前没有审核通过且依赖有效的完整故事。" else
        "完整故事已通过内容校验，点击文章后可手动播放。"
}

/** City entries contain county IDs only. Local entity/source/claim IDs are scoped by area, never title. */
internal class CountyCatalog(packs: List<CountyPack>) {
    val counties: Map<String, CountyPack>
    val cities: Map<String, List<String>>
    init {
        require(packs.size <= 100) { "county_limit" }
        require(packs.map { it.area.id }.distinct().size == packs.size) { "duplicate_area" }
        require(packs.map { it.area.identity.key }.distinct().size == packs.size) { "duplicate_identity" }
        packs.groupBy { it.area.parentId }.values.forEach { group ->
            require(group.map { it.area.identity.province to it.area.identity.city }.distinct().size == 1) { "parent_conflict" }
        }
        require(packs.groupBy { it.area.identity.province to it.area.identity.city }.values.all {
            it.map { p -> p.area.parentId }.distinct().size == 1 }) { "parent_alias" }
        counties = packs.associateBy { it.area.id }
        cities = packs.groupBy { it.area.parentId }.mapValues { (_, p) -> p.map { it.area.id } }
    }
    fun county(areaId: String) = counties[areaId]
    fun entity(areaId: String, entityId: String) = county(areaId)?.entities?.get(entityId)
}

/** Default verifier rejects every input. Only the private APK adapter below supplies production trust.
 * A SHA digest or a payload-provided 'trusted' flag is never an authentication decision.
 * Test verifiers/keys are declared exclusively under src/test. No external input entry point exists. */
internal class CountyPackLoader(private val verifier: (CountyBytes) -> CountyProvenance? = { null }) {
    fun read(input: CountyBytes): CountyPack {
        val b = input.snapshot()
        require(b.factManifest.size in 1..16000 && b.narrativeManifest.size in 1..16000 &&
            b.facts.size in 1..500000 && b.narratives.size in 1..500000) { "size" }
        val provenance = verifier(b.snapshot()) ?: error("untrusted_input")
        require(b.expectedSha256 == countyDigest(b)) { "envelope_integrity" }
        val (fm, f) = checked(b.factManifest, b.facts, "fact")
        val (nm, n) = checked(b.narrativeManifest, b.narratives, "narrative")
        val a = f.getValue("area").jsonObject
        val id = a.text("area_id")
        require(id.matches(Regex("cn-[0-9]{6}")) && !id.endsWith("00")) { "area_id" }
        val parent = a.text("parent_id")
        require(parent.matches(Regex("cn-[0-9]{4}00")) && parent != id) { "parent_id" }
        // D0 supports the ordinary county -> prefecture code hierarchy only. Province-direct
        // groupings (90) and missing prefecture levels (00) need separately reviewed rules.
        require(parent == id.dropLast(2) + "00" && id.substring(5, 7) !in setOf("00", "90")) { "parent_relationship" }
        val identity = AreaIdentity(a.bounded("city", 80), a.bounded("district", 80), province = a.bounded("province", 80))
        require(listOf(fm, nm, f, n).all { it.text("area_id") == id }) { "area_mismatch" }
        require(fm.text("pack_id") == "$id-fact" && nm.text("pack_id") == "$id-narrative") { "pack_id" }
        val sources = index(f, "sources", "source_id")
        val claims = index(f, "claims", "claim_id")
        val entities = index(f, "entities", "entity_id")
        index(n, "narratives", "narrative_id") // Duplicate/malformed IDs are fatal, not silently overwritten.
        val permissions = f.getValue("source_permissions").jsonObject
        f.getValue("claim_permissions").jsonObject
        val blocked = linkedMapOf<String, String>()
        val factIds = mutableSetOf<String>()
        val entityViews = entities.mapValues { (entityId, e) ->
            val facts = e.getValue("basic_facts").jsonArray
            require(facts.size <= 100) { "fact_limit" }
            val valid = facts.mapNotNull { v ->
                val fact = v.jsonObject
                val factId = fact.identifier("fact_id")
                require(factIds.add(factId)) { "duplicate_fact" }
                try {
                    val refs = fact.getValue("source_ids").jsonArray.map { it.jsonPrimitive.content }
                    require(refs.size in 1..20 && refs.distinct().size == refs.size) { "fact_sources" }
                    refs.forEach { sourceId ->
                        val source = sources[sourceId] ?: error("missing_source")
                        require(permissions[sourceId]?.jsonPrimitive?.content == "allowed") { "source_rights" }
                        require(source.text("access_status") == "read" && countyRights(source.text("rights")) &&
                            source["status"]?.jsonPrimitive?.content !in setOf("contested", "withdrawn", "rejected") &&
                            source.text("accessed_at").isNotBlank() && source.text("url").startsWith("https://") &&
                            source.text("registry_revision") == countyRevision(source, "registry_revision")) { "source_evidence" }
                    }
                    PackFact(factId, fact.bounded("summary", 250), fact.bounded("coverage", 65), refs)
                } catch (_: Exception) { blocked[factId] = "basic_fact_dependency"; null }
            }
            CountyEntity(entityId, e.bounded("title", 80), TopicFamily.valueOf(e.text("family")), valid)
        }
        require(entityViews.values.map { it.title }.distinct().size == entityViews.size) { "ambiguous_entity_title" }
        require(claims.values.all { it.text("entity_id") in entities }) { "dangling_entity" }
        val stories = StoryPacks.county(f, n, id, identity.key)
        val usedSources = entityViews.values.flatMap { it.facts }.flatMap { it.sourceIds }.toSet()
        val sourceViews = usedSources.map { sourceId -> val s = sources.getValue(sourceId)
            ResearchSource(sourceId, s.bounded("title", 250), s.text("url"), SourceClass.valueOf(s.text("kind"))) }
        val objects = entityViews.values.filter { it.facts.isNotEmpty() }.map { PackObject(it.title, it.family, it.facts) }
        val view = OfflinePack.readOnly(fm.text("content_version"), identity, sourceViews, objects, f.text("retrieved_at"))
        return CountyPack(CountyArea(id, parent, identity), fm.text("content_version"), nm.text("content_version"),
            entityViews, sources, claims, stories, blocked, view, provenance = provenance)
    }

    private fun checked(manifest: ByteArray, bytes: ByteArray, kind: String): Pair<JsonObject, JsonObject> {
        fun json(data: ByteArray) = Json.parseToJsonElement(data.decodeToString(throwOnInvalidSequence = true)).jsonObject
        val m = json(manifest)
        require(m.getValue("schema_version").jsonPrimitive.int == 2 && m.text("kind") == kind) { "manifest_schema" }
        require(m.getValue("minimum_code").jsonPrimitive.int in 20..BuildConfig.VERSION_CODE) { "minimum_code" }
        require(m.getValue("bytes").jsonPrimitive.int == bytes.size && m.text("sha256") == OfflinePack.sha256(bytes)) { "integrity" }
        m.bounded("content_version", 80)
        val d = json(bytes)
        require(d.getValue("schema_version").jsonPrimitive.int == 2 && d.text("pack_id") == m.text("pack_id") &&
            d.text("content_version") == m.text("content_version")) { "payload_schema" }
        return m to d
    }
    private fun index(data: JsonObject, field: String, key: String): Map<String, JsonObject> {
        val array = data.getValue(field).jsonArray
        require(array.size <= 200) { "index_limit" }
        val entries = array.map { it.jsonObject }
        val ids = entries.map { it.identifier(key) }
        require(ids.distinct().size == ids.size) { "duplicate_id" }
        return ids.zip(entries).toMap()
    }
}

internal fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
/** Exact supported licenses / existing curated facts-only scopes; never permission to copy a
 * copyrighted source's expression. Unknown or mixed/restricted statements fail closed. */
private val countyAllowedRights = setOf(
    "CC-BY-4.0",
    "KITT summaries CC-BY-4.0; source text excluded",
    "KITT自行概括事实，CC-BY-4.0；来源表达不在许可内",
    "仅古籍事实与书名；不复制平台标点、注释或全文",
    "古籍正文公有领域；不复制现代注释/网页编排",
    "现代专家文章保留版权；只自行概括作者归属与篇章关系，不复制全文",
    "现代报道保留版权；自行表述基本纪念关联，不转载原文/照片",
    "现代文章保留版权；只自行概括基本事实，不转载原文、图片或页面",
    "现代网页保留版权；仅提取事实自行表述",
    "论文保留版权；只自行概括版本流传结论，不公开镜像PDF"
).map { it.lowercase() }.toSet()
internal fun countyRights(value: String) = value.trim().lowercase() in countyAllowedRights
private fun JsonObject.bounded(key: String, max: Int) = text(key).also { require(it.isNotBlank() && it.length <= max) { "field_size" } }
private fun JsonObject.identifier(key: String) = text(key).also { require(it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))) { "identifier" } }
internal fun countyRevision(value: JsonObject, field: String): String {
    fun canonical(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(e.map(::canonical))
        else -> e
    }
    return OfflinePack.sha256(canonical(JsonObject(value.filterKeys { it != field })).toString().encodeToByteArray())
}

/** The only production county registration. No directory scanning, arbitrary paths or test registry.
 * Existing files remain byte-for-byte intact. Translation is in memory and confers no new review. */
internal object BundledCountyPacks {
    fun load(context: Context, acceptanceCandidate: Boolean = false): CountyCatalog = runCatching {
        val pack = OfflinePack.load(context) ?: return CountyCatalog(emptyList())
        fun read(name: String) = context.assets.open("offline/ys-$name.json").use { it.readBytes() }
        CountyCatalog(listOf(adapt(pack, read("facts-manifest"), read("facts"),
            read("narratives-manifest"), read("narratives"), acceptanceCandidate)))
    }.getOrElse {
        // A bad Narrative must never hide separately verified legacy basic facts.
        val pack = OfflinePack.load(context) ?: return@getOrElse CountyCatalog(emptyList())
        CountyCatalog(listOf(basicOnly(pack)))
    }

    private fun basicOnly(pack: OfflinePack): CountyPack {
        val id = "cn-510114"
        val empty = buildJsonObject { put("entities", JsonArray(emptyList())); put("sources", JsonArray(emptyList()))
            put("claims", JsonArray(emptyList())); put("claim_permissions", JsonObject(emptyMap())); put("source_permissions", JsonObject(emptyMap())) }
        val stories = StoryPacks.county(empty, buildJsonObject { put("narratives", JsonArray(emptyList())) }, id, pack.area.key)
        val entities = pack.objects.mapIndexed { i, o -> "legacy-$i" to CountyEntity("legacy-$i", o.title, o.family, o.facts) }.toMap()
        return CountyPack(CountyArea(id, "cn-510100", pack.area), pack.version, "unavailable", entities,
            emptyMap(), emptyMap(), stories, emptyMap(), pack, CountyProvenance.BUNDLED_APK)
    }

    internal fun adapt(pack: OfflinePack, fm: ByteArray, fb: ByteArray, nm: ByteArray, nb: ByteArray,
        acceptanceCandidate: Boolean = false): CountyPack {
        val legacyStories = StoryPacks.parse(fm, fb, nm, nb, acceptanceCandidate)
        val f = Json.parseToJsonElement(fb.decodeToString()).jsonObject
        val n = Json.parseToJsonElement(nb.decodeToString()).jsonObject
        val sources = f.getValue("sources").jsonArray.toMutableList()
        pack.sources.forEach { s ->
            val raw = buildJsonObject { put("source_id", "legacy-${s.id}"); put("title", s.title); put("url", s.url)
                put("kind", s.kind.name); put("access_status", "read"); put("accessed_at", pack.retrievedAt)
                put("rights", "KITT summaries CC-BY-4.0; source text excluded") }
            sources += JsonObject(raw + ("registry_revision" to JsonPrimitive(countyRevision(raw, "registry_revision"))))
        }
        val entities = pack.objects.mapIndexed { i, o ->
            val old = f.getValue("entities").jsonArray.map { it.jsonObject }.singleOrNull { it.text("title") == o.title }
            buildJsonObject { put("entity_id", old?.text("entity_id") ?: "legacy-$i"); put("title", o.title); put("family", o.family.name)
                put("basic_facts", JsonArray(o.facts.map { fact -> buildJsonObject { put("fact_id", fact.id)
                    put("summary", fact.summary); put("coverage", fact.coverage)
                    put("source_ids", JsonArray(fact.sourceIds.map { JsonPrimitive("legacy-$it") })) } })) }
        }
        val id = "cn-510114"
        val facts = JsonObject(f + mapOf("schema_version" to JsonPrimitive(2), "pack_id" to JsonPrimitive("$id-fact"),
            "retrieved_at" to JsonPrimitive(pack.retrievedAt), "sources" to JsonArray(sources), "entities" to JsonArray(entities),
            "source_permissions" to JsonObject(sources.associate { it.jsonObject.text("source_id") to JsonPrimitive("allowed") }),
            "claim_permissions" to JsonObject(f.getValue("claims").jsonArray.associate { it.jsonObject.text("claim_id") to JsonPrimitive("allowed") }),
            "area" to buildJsonObject { put("area_id", id); put("parent_id", "cn-510100"); put("province", pack.area.province)
                put("city", pack.area.city); put("district", pack.area.district) }))
        val narratives = JsonObject(n + mapOf("schema_version" to JsonPrimitive(2), "pack_id" to JsonPrimitive("$id-narrative"), "area_id" to JsonPrimitive(id)))
        fun seal(d: JsonObject, kind: String): Pair<ByteArray, ByteArray> {
            val bytes = d.toString().encodeToByteArray()
            val m = buildJsonObject { put("schema_version", 2); put("minimum_code", 20); put("kind", kind); put("area_id", id)
                put("pack_id", d.getValue("pack_id")); put("content_version", d.getValue("content_version"))
                put("bytes", bytes.size); put("sha256", OfflinePack.sha256(bytes)) }
            return m.toString().encodeToByteArray() to bytes
        }
        val (cfm, cfb) = seal(facts, "fact"); val (cnm, cnb) = seal(narratives, "narrative")
        val raw = CountyBytes(cfm, cfb, cnm, cnb)
        val input = raw.copy(expectedSha256 = countyDigest(raw))
        // These exact translated bytes originate exclusively from verified APK assets above.
        val verified = CountyPackLoader { candidate ->
            listOf(candidate.factManifest to cfm, candidate.facts to cfb, candidate.narrativeManifest to cnm,
                candidate.narratives to cnb).all { (a, b) -> a.contentEquals(b) }
                .let { if (it) CountyProvenance.BUNDLED_APK else null }
        }.read(input)
        return verified.copy(factsView = pack, runtimeStories = legacyStories)
    }
}
