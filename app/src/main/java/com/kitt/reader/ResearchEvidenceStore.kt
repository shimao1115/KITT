package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer

/** Bounded app-private facts, not journey state. No TTL, requests, pages, location or credentials. */
class ResearchEvidenceStore(private val directory: File, private val diagnostic: (String) -> Unit = {},
    private val maxEntries: Int = 128, private val maxBytes: Long = 2 * 1024 * 1024,
    private val maxEntryBytes: Int = 40000, private val io: CoroutineDispatcher = Dispatchers.IO,
    private val replace: (File, File) -> Unit = { from, to ->
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }) {
    private class Coordination {
        val disk = Any()
        class Lock(val mutex: Mutex = Mutex(), var users: Int = 0)
        val requests = mutableMapOf<String, Lock>()
    }
    companion object {
        private val coordinators = mutableMapOf<String, Coordination>()
        fun topicName(title: String) = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
        private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private val coordination = synchronized(coordinators) {
        coordinators.getOrPut(directory.canonicalPath) { Coordination() }
    }
    private fun key(area: AreaIdentity, topic: String) = area.key + "|" + topic
    private fun report(event: String, reason: String = "") = diagnostic("evidence_cache $event${if (reason.isBlank()) "" else " reason=$reason"}")
    private suspend fun <T> single(key: String, block: suspend () -> T): T {
        val lock = synchronized(coordination.requests) {
            coordination.requests.getOrPut(key) { Coordination.Lock() }.also { it.users++ }
        }
        try { return lock.mutex.withLock { block() } }
        finally { synchronized(coordination.requests) { if (--lock.users == 0) coordination.requests.remove(key) } }
    }
    suspend fun overview(area: AreaIdentity, fetch: suspend () -> OverviewDossier): OverviewDossier = single(key(area, "")) {
        read(area, "")?.let { return@single OverviewDossier.from(it) }
        report("miss", "overview"); report("research_call", "overview")
        fetch().also { result ->
            write(area, "", LocalDossier(result.area, result.searchedAt, result.orientation,
                result.objects.map { LocalFact(it.title, it.whyItMatters, it.family, it.salience, "medium", it.sources) },
                result.sources, result.gaps, result.verifiedSearch))
        }
    }
    suspend fun topic(area: AreaIdentity, title: String, fetch: suspend () -> LocalDossier): LocalDossier {
        val topic = topicName(title)
        return single(key(area, topic)) {
            read(area, topic)?.let { return@single it }
            report("miss", "topic"); report("research_call", "topic")
            fetch().also { write(area, topic, it) }
        }
    }
    suspend fun cachedTopic(area: AreaIdentity, title: String) = read(area, topicName(title))
    /** Internal explicit invalidation, never a start-time refresh. Active realtime research bypasses this store. */
    suspend fun invalidate(area: AreaIdentity, title: String = "") = single(key(area, topicName(title))) {
        withContext(io) { synchronized(coordination.disk) { file(area, topicName(title)).delete() } }
    }
    private fun file(area: AreaIdentity, topic: String) = File(directory, hash(key(area, topic)) + ".json")
    private fun validArea(area: AreaIdentity) = area.province.isNotBlank() && area.city.isNotBlank() && area.district.isNotBlank() &&
        listOf(area.province, area.city, area.district, area.chapter).all { it.length <= 60 && '/' !in it && '|' !in it }
    private fun payload(dossier: LocalDossier) = buildJsonObject {
        put("orientation", dossier.orientation)
        put("sources", buildJsonArray { dossier.sources.forEach { source -> add(buildJsonObject {
            put("id", source.id); put("title", source.title); put("url", source.url); put("kind", source.kind.name)
        }) } })
        put("facts", buildJsonArray { dossier.facts.forEach { fact -> add(buildJsonObject {
            put("title", fact.title); put("summary", fact.summary); put("family", fact.family.name)
            put("salience", fact.salience); put("confidence", fact.confidence); put("designation", fact.designation)
            put("source_ids", buildJsonArray { fact.sources.forEach { add(it.id) } })
        }) } })
        put("gaps", buildJsonArray { dossier.gaps.forEach { add(it) } })
    }
    private fun validate(area: AreaIdentity, dossier: LocalDossier): LocalDossier {
        require(validArea(area) && area == dossier.area && dossier.verifiedSearch && dossier.searchedAt > 0)
        require(dossier.facts.isNotEmpty() && dossier.sources.isNotEmpty())
        dossier.sources.forEach { source ->
            val host = URI(source.url).host?.lowercase().orEmpty()
            require(host.contains('.') && host != "localhost" && !host.endsWith(".test") &&
                !host.endsWith(".invalid") && !host.endsWith(".example") &&
                host !in setOf("example.com", "example.org", "example.net") && !host.matches(Regex("[0-9.]+")))
        }
        require(!LocalResearchContract.officialClaim(dossier.orientation) || dossier.sources.any { it.government && it.kind == SourceClass.OFFICIAL })
        val parsed = LocalResearchContract.parse(payload(dossier).toString(), area, dossier.searchedAt, dossier.sources.map { it.url }.toSet())
        // Reject partial validation; do not silently save a filtered or altered dossier.
        require(parsed.facts == dossier.facts && parsed.sources == dossier.sources && parsed.gaps == dossier.gaps)
        return parsed.copy(verifiedSearch = true, historical = true)
    }
    private suspend fun read(area: AreaIdentity, topic: String): LocalDossier? = withContext(io) {
        synchronized(coordination.disk) {
            val target = file(area, topic)
            if (!target.isFile) return@synchronized null
            try {
                require(target.length() in 1..maxEntryBytes.toLong())
                val envelope = Json.parseToJsonElement(target.readText(Charsets.UTF_8)).jsonObject
                require(envelope.keys == setOf("version", "identity", "topic", "searched_at", "provenance", "evidence", "sha256"))
                require(envelope.getValue("version").jsonPrimitive.int == 1)
                require(envelope.getValue("identity").jsonArray.map { it.jsonPrimitive.content } ==
                    listOf(area.province, area.city, area.district, area.chapter))
                require(envelope.getValue("topic").jsonPrimitive.content == topic)
                require(envelope.getValue("provenance").jsonPrimitive.content == "completed_hosted_search")
                val body = JsonObject(envelope.filterKeys { it != "sha256" })
                require(hash(body.toString()) == envelope.getValue("sha256").jsonPrimitive.content)
                val evidence = envelope.getValue("evidence").jsonObject
                val visited = evidence.getValue("sources").jsonArray.map { it.jsonObject.getValue("url").jsonPrimitive.content }.toSet()
                val parsed = LocalResearchContract.parse(evidence.toString(), area, envelope.getValue("searched_at").jsonPrimitive.long, visited)
                    .copy(verifiedSearch = true)
                require(payload(parsed) == evidence)
                validate(area, parsed).also { report("hit", if (topic.isEmpty()) "overview" else "topic") }
            } catch (_: Exception) {
                target.delete(); report("rejected", "invalid_file"); null
            }
        }
    }
    private suspend fun write(area: AreaIdentity, topic: String, dossier: LocalDossier) = withContext(io) {
        synchronized(coordination.disk) {
            val target = file(area, topic); val temporary = File(directory, target.name + ".new")
            try {
                validate(area, dossier)
                val body = buildJsonObject {
                    put("version", 1); put("identity", buildJsonArray {
                        listOf(area.province, area.city, area.district, area.chapter).forEach { add(it) }
                    }); put("topic", topic); put("searched_at", dossier.searchedAt)
                    put("provenance", "completed_hosted_search"); put("evidence", payload(dossier))
                }
                val encoded = JsonObject(body + ("sha256" to JsonPrimitive(hash(body.toString())))).toString().toByteArray(Charsets.UTF_8)
                require(maxEntries > 0 && encoded.size <= maxEntryBytes && encoded.size <= maxBytes)
                check(directory.isDirectory || directory.mkdirs())
                // Abandoned pre-rename files are never evidence; remove them before the next write.
                directory.listFiles { f -> f.name.endsWith(".json.new") }.orEmpty().forEach { check(it.delete()) }
                FileOutputStream(temporary).use { it.write(encoded); it.fd.sync() }
                replace(temporary, target)
                // Oldest writes evicted; stable evidence is not invalidated on every journey start.
                val entries = directory.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.lastModified() }.toMutableList()
                var bytes = entries.sumOf { it.length() }
                while (entries.size > maxEntries || bytes > maxBytes) {
                    val old = entries.removeAt(0); val length = old.length()
                    check(old.delete()); bytes -= length
                }
                report("write")
            } catch (_: Exception) { report("rejected", "write_or_validation") }
            finally { temporary.delete() }
        }
    }
}
