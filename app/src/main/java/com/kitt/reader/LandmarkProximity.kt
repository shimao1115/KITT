package com.kitt.reader

import kotlinx.serialization.json.*

enum class LandmarkKind { MOUNTAIN, PEAK, RIVER, CROSSING, LAKE, RESERVOIR, LANDFORM,
    BRIDGE, DAM, TUNNEL, LANDMARK_BUILDING, MUSEUM, RUINS, HERITAGE }
data class Landmark(val id: String, val name: String, val latitude: Double, val longitude: Double,
    val radiusMeters: Double, val kind: LandmarkKind, val family: TopicFamily, val grounding: String) {
    fun distance(fix: Fix) = fix.distanceTo(Fix(latitude, longitude, fix.timeMs))
}
object LandmarkCatalog {
    fun parse(raw: String): List<Landmark> {
        val items = Json.parseToJsonElement(raw).jsonArray
        require(items.size <= 64)
        val nodes = items.map { item ->
            val p = item.jsonObject
            fun text(key: String) = p.getValue(key).jsonPrimitive.content
            Landmark(text("id"), text("name"), p.getValue("lat").jsonPrimitive.double,
                p.getValue("lon").jsonPrimitive.double, p.getValue("radius_m").jsonPrimitive.double,
                LandmarkKind.valueOf(text("kind")), TopicFamily.valueOf(text("family")), text("grounding"))
        }
        require(nodes.map { it.id }.distinct().size == nodes.size)
        require(nodes.all { it.id.matches(Regex("[a-z0-9_-]{1,64}")) && it.name.length in 1..100 &&
            it.grounding.length in 1..600 && Fix(it.latitude, it.longitude, 0).valid() && it.radiusMeters in 300.0..30000.0 })
        return nodes
    }
}

/** Spatial opportunities, not a narration queue. Only live approach zones remain eligible. */
class LandmarkProximity(private val nodes: List<Landmark>) {
    private data class Approach(var closest: Double)
    private val approaches = mutableMapOf<String, Approach>()
    private val checked = mutableSetOf<String>()
    private val spoken = mutableSetOf<String>()
    private var live = emptyList<Landmark>()
    val opportunity get() = live.any { it.id !in checked && it.id !in spoken }
    val ids get() = live.map { it.id }.toSet()
    fun accept(fix: Fix) {
        live = nodes.filter { node ->
            val distance = node.distance(fix)
            if (distance > node.radiusMeters) { approaches.remove(node.id); false }
            else {
                val approach = approaches.getOrPut(node.id) { Approach(distance) }
                approach.closest = minOf(approach.closest, distance)
                // Position noise does not immediately discard a roadside site. Moving clearly away does.
                distance <= approach.closest + 250 && node.id !in spoken
            }
        }
    }
    fun consumeOpportunity(requested: Set<String> = ids) { checked.addAll(requested) }
    private fun matching(result: DirectorResult): String? = result.landmarkId.takeIf(String::isNotBlank)
        ?: nodes.firstOrNull { it.name in result.topic || it.name in result.memoryUpdate }?.id
    fun guard(result: DirectorResult?, active: Boolean, requested: Set<String>): DeliveryOutcome? {
        if (result == null || active || result.action != Action.SPEAK_NOW) return null
        val id = matching(result) ?: return null
        if (id in spoken) return DeliveryOutcome.SUPPRESSED
        if (result.landmarkId.isNotBlank() && id !in requested) return DeliveryOutcome.STALE
        if (id in requested && id !in ids) return DeliveryOutcome.STALE
        return null
    }
    fun delivered(result: DirectorResult) { matching(result)?.let { spoken += it } }
    fun card(): String = if (live.isEmpty()) "" else buildString {
        appendLine("【独立地标接近】以下是当前 GPS 接近的高价值候选，行政章节不变也可检查。不是导航或可见性判断。")
        live.take(4).forEach { node ->
            appendLine("landmark_id=${node.id}；${node.name}；${node.kind} / ${node.family}；依据：${node.grounding}；${if (node.id in checked) "本次接近已检查，仍须有新价值" else "新的接近机会"}")
        }
        appendLine("优先有依据的重要地理/地标事件，再考虑重复的道路或聚落机制。可以 SILENT；只讲一个，不要求逐一播放。")
    }
    fun clear() { approaches.clear(); checked.clear(); spoken.clear(); live = emptyList() }
}
