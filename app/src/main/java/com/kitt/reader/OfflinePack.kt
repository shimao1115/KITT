package com.kitt.reader

import android.content.Context
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.math.*

/** Curated stable evidence, separate from hosted-search results and session playback memory. */
data class PackFact(val id: String, val summary: String, val coverage: String, val sourceIds: List<String>)
data class PackObject(val title: String, val family: TopicFamily, val facts: List<PackFact>)

class OfflinePack private constructor(val version: String, val area: AreaIdentity,
    val sources: List<ResearchSource>, val objects: List<PackObject>, val retrievedAt: String,
    private val rings: List<List<Pair<Double, Double>>>, val geometryYear: String,
    val uncertaintyMeters: Double) {
    /** WGS84 real measurements only. The entire accuracy disk must clear every ring by the margin.
     * A historical community boundary is not an official survey; no street identity is inferred. */
    fun resolve(fix: Fix?, time: Long): AreaIdentity? {
        if (fix == null || !fix.valid() || !fix.precise(time) ||
            fix.source !in setOf(FixSource.GPS, FixSource.NETWORK) || fix.accuracy <= 0) return null
        val x = fix.longitude; val y = fix.latitude
        fun inside(ring: List<Pair<Double, Double>>): Boolean {
            var hit = false
            ring.zipWithNext().forEach { (a, b) ->
                if ((a.second > y) != (b.second > y) &&
                    x < (b.first - a.first) * (y - a.second) / (b.second - a.second) + a.first) hit = !hit
            }
            return hit
        }
        if (!inside(rings.first()) || rings.drop(1).any(::inside)) return null
        val scaleX = 111320.0 * cos(Math.toRadians(y)); val scaleY = 111320.0
        val edgeDistance = rings.minOf { ring -> ring.zipWithNext().minOf { (a, b) ->
            val ax = (a.first - x) * scaleX; val ay = (a.second - y) * scaleY
            val dx = (b.first - a.first) * scaleX; val dy = (b.second - a.second) * scaleY
            val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
            hypot(ax + t * dx, ay + t * dy)
        } }
        return area.takeIf { edgeDistance > uncertaintyMeters + fix.accuracy }
    }

    fun matches(identity: AreaIdentity?) = identity?.copy(chapter = "")?.key == area.key
    fun card(identity: AreaIdentity?) = if (!matches(identity)) "" else buildString {
        appendLine("【离线审校事实包】$version；${area.fullName}；本次未联网；仅区县关联，不证明就在对象旁边或可见。")
        appendLine("来源检索日期$retrievedAt；事实为自行改写，非网页全文。缺口：当前状态、精确对象位置、镇街范围均未提供。")
        sources.forEach { appendLine("来源[${it.id}] ${it.title} ${it.url}") }
        objects.forEach { obj ->
            appendLine("对象身份=${obj.title}；${obj.family}")
            obj.facts.forEach { appendLine("事实[${it.id}] ${it.summary}；coverage=${it.coverage}；来源=${it.sourceIds.joinToString()}") }
        }
        appendLine("可直接使用以上稳定事实，不需重复搜索。沿用对象身份和coverage标签作memory_update；只能补未覆盖事实。缺口或明确最新问题才联网查，不伪称刚查到。")
    }

    private val retrievedMs get() = java.time.LocalDate.parse(retrievedAt).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    fun overview(identity: AreaIdentity): OverviewDossier? = if (!matches(identity)) null else
        OverviewDossier(identity, retrievedMs, "新都区审校离线包；本次未联网；检索日期$retrievedAt（日级精度）；只有区县关联。", objects.map {
            OverviewObject(it.title, it.facts.first().summary, it.family, 4, sourcesFor(it.facts))
        }, sources, listOf("无实时信息、无可信镇街几何、无对象精确现场位置"), historical = true)

    fun topic(identity: AreaIdentity, title: String): LocalDossier? =
        objects.find { it.title == title }?.takeIf { matches(identity) }?.let { obj ->
            LocalDossier(identity, retrievedMs, "审校离线包；本次未联网；检索日期$retrievedAt（日级精度）。", obj.facts.map {
                LocalFact(obj.title, it.summary, obj.family, 4, "medium", sources.filter { s -> s.id in it.sourceIds })
            }, sourcesFor(obj.facts), listOf("不得推断最新状态或就在眼前"), historical = true)
        }
    private fun sourcesFor(facts: List<PackFact>) = sources.filter { s -> facts.any { s.id in it.sourceIds } }

    companion object {
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun load(context: Context): OfflinePack? = runCatching {
            val assets = context.assets
            parse(assets.open("offline/xindu-manifest.json").use { it.readBytes() },
                assets.open("offline/xindu-facts.json").use { it.readBytes() },
                assets.open("offline/xindu-geometry.geojson").use { it.readBytes() })
        }.getOrNull()
        fun parse(manifest: ByteArray, facts: ByteArray, geometry: ByteArray): OfflinePack {
            require(manifest.size <= 16000 && facts.size in 1..100000 && geometry.size in 1..200000)
            val m = Json.parseToJsonElement(manifest.decodeToString()).jsonObject
            require(m.getValue("schema_version").jsonPrimitive.int == 1 && m.getValue("minimum_code").jsonPrimitive.int <= BuildConfig.VERSION_CODE)
            require(sha256(facts) == m.getValue("facts_sha256").jsonPrimitive.content && sha256(geometry) == m.getValue("geometry_sha256").jsonPrimitive.content)
            val d = Json.parseToJsonElement(facts.decodeToString()).jsonObject
            val a = d.getValue("area").jsonObject
            val area = AreaIdentity(a.getValue("city").jsonPrimitive.content, a.getValue("district").jsonPrimitive.content,
                province = a.getValue("province").jsonPrimitive.content)
            require(area.key == "四川省/成都市/新都区/")
            val sources = d.getValue("sources").jsonArray.map { v -> val s = v.jsonObject
                ResearchSource(s.getValue("id").jsonPrimitive.content, s.getValue("title").jsonPrimitive.content,
                    s.getValue("url").jsonPrimitive.content, SourceClass.valueOf(s.getValue("kind").jsonPrimitive.content)).also {
                    require(it.id.matches(Regex("[a-z0-9_-]+")) && it.url.startsWith("https://") && it.domain.isNotBlank())
                }
            }
            require(sources.size in 1..20 && sources.map { it.id }.distinct().size == sources.size)
            val objects = d.getValue("objects").jsonArray.map { v -> val o = v.jsonObject
                PackObject(o.getValue("title").jsonPrimitive.content.also { require(it.length in 1..80) },
                    TopicFamily.valueOf(o.getValue("family").jsonPrimitive.content), o.getValue("facts").jsonArray.map { f -> val p = f.jsonObject
                        PackFact(p.getValue("id").jsonPrimitive.content, p.getValue("summary").jsonPrimitive.content,
                            p.getValue("coverage").jsonPrimitive.content, p.getValue("source_ids").jsonArray.map { it.jsonPrimitive.content }).also {
                            require(it.id.matches(Regex("[a-z0-9_-]+")) && it.summary.length in 1..250 && it.coverage.length in 1..65)
                            require(it.sourceIds.isNotEmpty() && it.sourceIds.all { id -> sources.any { s -> s.id == id } })
                        }
                    }.also { require(it.size in 2..10) })
            }
            require(objects.size in 1..10 && objects.map { it.title }.distinct().size == objects.size)
            val allFacts = objects.flatMap { it.facts }
            require(allFacts.size in 1..50 && allFacts.map { it.id }.distinct().size == allFacts.size)
            val g = Json.parseToJsonElement(geometry.decodeToString()).jsonObject.getValue("geometry").jsonObject
            require(g.getValue("type").jsonPrimitive.content == "Polygon")
            val rings = g.getValue("coordinates").jsonArray.map { r -> r.jsonArray.map { p ->
                val pair = p.jsonArray; require(pair.size == 2)
                pair[0].jsonPrimitive.double to pair[1].jsonPrimitive.double
            }.also { ring ->
                require(ring.size in 4..10000 && ring.first() == ring.last())
                require(ring.all { it.first.isFinite() && it.first in 103.0..105.0 && it.second.isFinite() && it.second in 30.0..32.0 })
            } }
            require(rings.size in 1..20)
            val margin = m.getValue("boundary_margin_meters").jsonPrimitive.double
            require(margin.isFinite() && margin in 500.0..5000.0)
            val retrievedAt = d.getValue("retrieved_at").jsonPrimitive.content
            java.time.LocalDate.parse(retrievedAt)
            return OfflinePack(m.getValue("version").jsonPrimitive.content, area, sources, objects, retrievedAt, rings,
                m.getValue("geometry_year").jsonPrimitive.content, margin)
        }
    }
}

/** Select 2–4 new fact fragments, reuse the authoritative Journey playback lifecycle, no cloud calls. */
class OfflineNarrator(private val pack: OfflinePack?, private val stories: StoryPacks? = null) {
    fun response(journey: Journey, time: Long, utterance: String? = null, image: Boolean = false): DirectorResult {
        fun message(text: String) = DirectorResult(Action.SPEAK_NOW, "离线资料", text)
        val active = utterance != null
        if (image) return message("当前离线，不能分析图片。")
        if (followUp(utterance) == FollowUp.REPEAT && journey.replay == null)
            return message("这趟旅程还没有可重播的内容。")
        if (active && (ActiveResearchPolicy.forced(utterance.orEmpty()) ||
                listOf("天气", "票价", "门票", "开放", "营业", "路况", "封路", "最新", "今天", "现在").any { it in utterance.orEmpty() }))
            return message("当前离线，无法确认最新状态。联网后可以再问。")
        val identity = pack?.resolve(journey.fix, time)
        if (identity == null) return if (active) message("离线地区不确定，或当地没有可靠资料；需要新鲜、精确的物理定位。") else DirectorResult(Action.SILENT)
        val more = followUp(utterance) == FollowUp.MORE
        if (active && !more && utterance?.trim()?.trimEnd('？', '?', '。') !in setOf("这里有什么", "这里有什么好讲的", "讲讲这里"))
            return message("离线时可以问这里有什么、再讲一点或再说一遍；其他问题请联网后再问。")
        stories?.select(journey, identity, more)?.let { return it.response() }
        if (more && stories?.ownsAttempt(journey, identity, journey.topic) == true)
            return message("目前没有审核通过且未讲过的延伸故事。")
        val covered = journey.narrationMemory.filter { it.area?.copy(chapter = "")?.key == identity.key }
        val choices = pack.objects.filter { (!more || it.title == journey.topic) && stories?.ownsAttempt(journey, identity, it.title) != true }
            .sortedBy { obj -> covered.count { it.subject == obj.title } }
        val chosen = choices.firstNotNullOfOrNull { obj ->
            val previous = covered.filter { it.subject == obj.title }.flatMap { it.coverage.split('；') }.toSet()
            obj.facts.filter { it.coverage !in previous && stories?.coversLegacyFact(journey, identity, it.id) != true }
                .take(2).takeIf { it.size >= 2 }?.let { obj to it }
        } ?: return if (active) message("目前没有可靠的新内容。") else DirectorResult(Action.SILENT)
        val (obj, facts) = chosen
        return DirectorResult(Action.SPEAK_NOW, obj.title,
            (if (!more) "新都区有个值得了解的对象，${obj.title}。" else "关于${obj.title}，还有两点。") + facts.joinToString("") { it.summary },
            memoryUpdate = facts.joinToString("；") { it.coverage }, topicFamily = obj.family)
    }
}
