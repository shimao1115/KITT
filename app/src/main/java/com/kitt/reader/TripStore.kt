package com.kitt.reader

import android.content.Context
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

data class Recovery(val started: Long, val destination: String, val instructions: String,
    val topics: List<String>, val quietUntil: Long, val simulation: Boolean, val acceleration: Double, val travelMs: Long)

/** Bounded lightweight JSON; no raw positions, transcript, audio or generated narration. */
class TripStore(context: Context) {
    private val directory = File(context.filesDir, "trips").apply { mkdirs() }
    private val current = File(directory, "current.json")
    var lastError: String? = null; private set
    private fun write(file: File, value: JsonObject) {
        val temporary = File(file.parentFile, file.name + ".new")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(value.toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            lastError = null
        } catch (e: Exception) { temporary.delete(); throw e }
    }
    fun checkpoint(journey: Journey, simulation: Boolean, acceleration: Double, travelMs: Long) {
        if (!journey.running) return
        runCatching { write(current, buildJsonObject {
            put("version", 1); put("started", journey.started); put("destination", journey.destination)
            put("instructions", journey.instructions); put("topics", JsonArray(journey.recentTopics.map(::JsonPrimitive)))
            put("quiet_until", journey.quietUntil); put("simulation", simulation); put("acceleration", acceleration)
            put("travel_ms", travelMs)
        }) }.onFailure { lastError = "旅程恢复信息暂时无法保存。" }
    }
    fun recovery(): Recovery? = runCatching {
        if (!current.exists()) return null
        val obj = Json.parseToJsonElement(current.readText()).jsonObject
        require(obj.getValue("version").jsonPrimitive.int == 1)
        Recovery(obj.getValue("started").jsonPrimitive.long, obj.getValue("destination").jsonPrimitive.content.take(120),
            obj.getValue("instructions").jsonPrimitive.content.take(600),
            obj.getValue("topics").jsonArray.takeLast(8).map { it.jsonPrimitive.content.take(240) },
            obj.getValue("quiet_until").jsonPrimitive.long, obj.getValue("simulation").jsonPrimitive.boolean,
            obj.getValue("acceleration").jsonPrimitive.double.coerceIn(1.0, 120.0),
            obj.getValue("travel_ms").jsonPrimitive.long.coerceAtLeast(0))
    }.getOrElse { lastError = "上次旅程恢复信息损坏，未自动恢复。"; null }
    fun finish(summary: TripSummary): Boolean {
        return runCatching {
            write(File(directory, "trip-${summary.started}.json"), buildJsonObject {
                put("version", 1); put("started", summary.started); put("ended", summary.ended)
                put("destination", summary.destination); put("topics", JsonArray(summary.topics.map(::JsonPrimitive))); put("skipped", summary.skipped)
            })
            clearRecovery()
            directory.listFiles { file -> file.name.startsWith("trip-") && file.extension == "json" }
                ?.sortedByDescending(File::lastModified)?.drop(20)?.forEach(File::delete)
            true
        }.getOrElse { lastError = "旅程纪要暂时无法保存。"; false }
    }
    fun feedback(started: Long, ratings: List<Int>, comment: String): Boolean = runCatching {
        require(ratings.size in 1..5 && ratings.all { it in 1..5 })
        val file = File(directory, "trip-$started.json")
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        write(file, buildJsonObject {
            obj.forEach { (key, value) -> put(key, value) }
            put("ratings", JsonArray(ratings.map(::JsonPrimitive))); put("feedback", comment.take(800))
        }); true
    }.getOrElse { lastError = "评分暂时无法保存。"; false }
    fun latest(): TripSummary? = runCatching {
        val file = directory.listFiles { file -> file.name.startsWith("trip-") && file.extension == "json" }
            ?.maxByOrNull(File::lastModified) ?: return null
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        TripSummary(obj.getValue("started").jsonPrimitive.long, obj.getValue("ended").jsonPrimitive.long,
            obj.getValue("destination").jsonPrimitive.content, obj.getValue("topics").jsonArray.map { it.jsonPrimitive.content }, obj.getValue("skipped").jsonPrimitive.int)
    }.getOrNull()
    fun clearRecovery() { current.delete(); File(directory, "current.json.new").delete() }
}
