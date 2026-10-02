package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.math.*

data class RoutePoint(val lat: Double, val lon: Double, val area: String)
data class RouteFixture(val name: String, val speedKmh: Double, val points: List<RoutePoint>) {
    companion object {
        fun parse(raw: String): RouteFixture {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val points = obj.getValue("points").jsonArray.map { item ->
                val p = item.jsonObject
                RoutePoint(p.getValue("lat").jsonPrimitive.double, p.getValue("lon").jsonPrimitive.double, p.getValue("area").jsonPrimitive.content)
            }
            require(points.size in 2..500 && points.all { Fix(it.lat, it.lon, 0).valid() })
            val speed = obj.getValue("speed_kmh").jsonPrimitive.double
            require(speed in 1.0..200.0)
            return RouteFixture(obj.getValue("name").jsonPrimitive.content, speed, points)
        }
    }
}

/** Linear coarse fixture interpolation. Acceleration changes travel distance only, not quiet/voice clocks. */
class SimulatedLocationSource(
    val fixture: RouteFixture, private val scope: CoroutineScope, private val now: () -> Long,
    val speedKmh: Double = fixture.speedKmh, val acceleration: Double = 1.0,
    private val initialTravelMs: Long = 0L, private val onFinished: () -> Unit = {}
) : LocationSource {
    private val lengths = fixture.points.zipWithNext().map { (a, b) -> Fix(a.lat, a.lon, 0).distanceTo(Fix(b.lat, b.lon, 0)) }
    val totalMeters = lengths.sum()
    var traveledMeters = 0.0; private set
    var completed = false; private set
    var travelMs = initialTravelMs; private set
    private var job: Job? = null
    init { require(speedKmh in 1.0..200.0 && acceleration in 1.0..120.0) }
    fun sample(elapsedMs: Long, timeMs: Long): Fix {
        var distance = (elapsedMs.coerceAtLeast(0) / 1000.0 * speedKmh / 3.6 * acceleration).coerceAtMost(totalMeters)
        traveledMeters = distance
        var index = 0
        while (index < lengths.lastIndex && distance > lengths[index]) { distance -= lengths[index]; index++ }
        val fraction = (distance / lengths[index].coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
        val a = fixture.points[index]; val b = fixture.points[index + 1]
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat); val lon = Math.toRadians(b.lon - a.lon)
        val bearing = (Math.toDegrees(atan2(sin(lon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon))) + 360) % 360
        return Fix(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction, timeMs,
            if (traveledMeters >= totalMeters) 0.0 else speedKmh, bearing, area = if (fraction >= 0.99) b.area else a.area,
            clue = "粗粒度模拟测试线索；非真实道路、非导航级，无已核验当地节点。")
    }
    override fun start(onFix: (Fix) -> Unit) {
        stop(); completed = false; val startTime = now()
        job = scope.launch {
            do {
                travelMs = initialTravelMs + now() - startTime
                onFix(sample(travelMs, now()))
                if (traveledMeters >= totalMeters) { completed = true; onFinished(); break }
                delay(1000)
            } while (isActive)
        }
    }
    override fun stop() { job?.cancel(); job = null }
}
