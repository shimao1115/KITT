package com.kitt.reader

import kotlin.math.*
import java.util.Locale

data class Fix(val latitude: Double, val longitude: Double, val timeMs: Long,
    val speedKmh: Double = 0.0, val bearing: Double = 0.0, val altitude: Double? = null,
    val accuracy: Double = 0.0, val area: String = "", val clue: String = "", val administrative: AreaIdentity? = null) {
    fun valid() = latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() &&
        longitude in -180.0..180.0 && speedKmh.isFinite() && speedKmh in 0.0..350.0 &&
        bearing.isFinite() && accuracy.isFinite() && accuracy in 0.0..200.0 && (altitude == null || altitude.isFinite())
    fun distanceTo(other: Fix): Double {
        val dLat = Math.toRadians(other.latitude - latitude); val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 6371000 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1 - a).coerceIn(0.0, 1.0)))
    }
}
fun angleDifference(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
interface LocationSource { fun start(onFix: (Fix) -> Unit); fun stop() }

class ContextPipeline(landmarks: List<Landmark> = emptyList()) {
    private val recent = ArrayDeque<Fix>()
    val areas = AreaCards()
    val proximity = LandmarkProximity(landmarks)
    var researchCard: () -> String = { "" }
    var routeHint = ""
    fun reset() { recent.clear(); areas.clear(); proximity.clear(); routeHint = "" }
    fun accept(fix: Fix) {
        if (!fix.valid() || (recent.lastOrNull()?.timeMs ?: Long.MIN_VALUE) > fix.timeMs) return
        recent.addLast(fix)
        areas.accept(fix.administrative)
        proximity.accept(fix)
        while (recent.size > 120 || (recent.firstOrNull()?.timeMs ?: fix.timeMs) < fix.timeMs - 1200000) recent.removeFirst()
    }
    fun card(journey: Journey, time: Long): String {
        val fix = journey.fix
        val first = recent.firstOrNull()
        val distance = recent.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        val climb = if (first?.altitude != null && fix?.altitude != null) fix.altitude - first.altitude else null
        return buildString {
            appendLine("【旅程意图】\n目的地：${journey.destination}\n本次临时偏好：${journey.instructions.ifBlank { "无" }}")
            if (routeHint.isNotBlank()) appendLine("【路线参考】${routeHint.take(240)}。截图仅为意图提示，当前 GPS 优先，不能据此推断已经到达或更改目的地。")
            appendLine("【当前位置】")
            if (fix == null) appendLine("暂无可靠位置，不断言现场。") else {
                appendLine(String.format(Locale.ROOT, "坐标：%.4f, %.4f；速度：%.0f km/h；方向：%.0f°；精度：%.0f m", fix.latitude, fix.longitude, fix.speedKmh, fix.bearing, fix.accuracy))
                appendLine("位置年龄：${(time - fix.timeMs).coerceAtLeast(0) / 1000} 秒；区域：${fix.area.ifBlank { "未知" }}")
                fix.altitude?.let { appendLine("海拔：${it.toInt()} m") }
            }
            appendLine("【最近行驶】短期约 ${distance.toInt()} m；${climb?.let { "海拔变化约 ${it.toInt()} m" } ?: "海拔趋势未知"}")
            appendLine("【附近/前方可靠线索】${fix?.clue?.ifBlank { "无地图增强；不猜桥名、河名和道路" } ?: "无"}")
            appendLine("【最近讲过】${journey.recentTopics.joinToString("；").ifBlank { "无" }}")
            appendLine("【最近题材】${journey.recentFamilies.distinct().joinToString().ifBlank { "无" }}；只用于避免重复同样的内容，不是黑名单，也不要求轮换题材。")
            areas.active?.let { append(it.text()) }
            append(researchCard())
            append(proximity.card())
            appendLine("【当前交互状态】${journey.state}；${if (journey.foreground) "前台" else "后台/锁屏，不主动提问"}；距上次讲话：${if (journey.lastSpeech == 0L) "无" else "${(time - journey.lastSpeech) / 1000} 秒"}")
            journey.simulatedTravelMs?.let { travel ->
                appendLine("【开发模拟节奏】累计模拟行驶 ${travel / 1000} 秒 / ${journey.simulatedMeters?.toInt()} m；距上次讲话的模拟行驶：${journey.simulatedSinceSpeechMs?.let { "${it / 1000} 秒" } ?: "无"}。")
                appendLine("只压缩交互之间的行驶；思考、讲话、监听时暂停移动，语音和安静计时仍是真实时间。按模拟行驶变化判断新现场价值，无新价值仍应 SILENT。")
                if (journey.destination == "未询问") appendLine("首次定位后的旅程意图尚未询问，可以先问一次目的地；‘未询问’不表示用户已拒绝回答。")
            }
            journey.prepared?.let { appendLine("【待重新确认】${it.topic}：${it.hint}。必须用当前现场重新判断；不补播。") }
        }
    }
}
