package com.kitt.reader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** One natural production session, existing account/evidence. No GPS injection or credential output.
 * Stops after one engine-confirmed narration or a bounded observation; semantic listening is manual.
 */
internal fun Instrumentation.narrationMemoryPhoneProbe(runtime: KittRuntime): Bundle {
    val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE)
        .put("mode", "natural-production-director-tts").put("provider", runtime.config.kind.name)
        .put("model", runtime.config.model).put("effort", runtime.config.effort.ifBlank { "default" })
    val phases = linkedSetOf<String>(); var completed = false; var fresh = 0; var seconds = 0
    val before = runtime.config
    try {
        runOnMainSync {
            check(!runtime.journey.running && runtime.recovery == null) { "End existing journey first" }
            check(runtime.config.kind != ProviderKind.FAKE)
            runtime.start()
            // A test-only session preference, cleared on end. No destination or saved setting is invented.
            runtime.loop.cancel()
            runtime.journey.requestInput("本次偏好：不需要询问目的地，只讲已有可靠来源支持的未讲内容；无资料可安静。")
        }
        runBlocking {
            while (seconds < 180 && !completed) {
                delay(1000); seconds++
                runOnMainSync {
                    if (runtime.journey.currentFix != null) fresh++
                    runtime.journey.narrationMemory.forEach { phases += it.phase.name }
                    completed = runtime.journey.narrationMemory.any { it.phase == NarrationPhase.COMPLETED }
                }
            }
        }
        runOnMainSync {
            report.put("automatic_speak_now", runtime.loop.counters.automatic[DeliveryOutcome.SPEAK_NOW] ?: 0)
                .put("automatic_silent", runtime.loop.counters.automatic[DeliveryOutcome.SILENT] ?: 0)
                .put("automatic_stale", runtime.loop.counters.automatic[DeliveryOutcome.STALE] ?: 0)
                .put("automatic_failure", runtime.loop.counters.automatic[DeliveryOutcome.FAILURE] ?: 0)
                .put("automatic_suppressed", runtime.loop.counters.automatic[DeliveryOutcome.SUPPRESSED] ?: 0)
                .put("director_request_outcome", runtime.requests.ai?.outcome?.name ?: "NEVER_ATTEMPTED")
                .put("research_request_outcome", runtime.requests.research?.outcome?.name ?: "NEVER_ATTEMPTED")
                .put("completed_narrations", runtime.journey.narrationMemory.count { it.phase == NarrationPhase.COMPLETED })
                .put("attempted_narrations", runtime.journey.narrationMemory.size)
                .put("settings_unchanged", before == runtime.config)
        }
        report.put("automatic_chain", if (completed) "PASS_ENGINE_CALLBACK_ONLY" else "PENDING")
    } catch (e: Exception) {
        report.put("automatic_chain", "PENDING").put("failure_class", e.javaClass.simpleName)
    } finally {
        runOnMainSync {
            runtime.end(); runtime.dismissSummary(); targetContext.stopService(Intent(targetContext, JourneyService::class.java))
        }
    }
    report.put("observed_seconds", seconds).put("fresh_position_seconds", fresh)
        .put("narration_phases", phases.joinToString(","))
        .put("human_semantic_acceptance", "PENDING: repeat segment/more/replay listening must be checked by passenger or when parked")
        .put("limits", "No human hearing proof, no forced movement/coordinates, no account export, no repeated paid search, no field-location acceptance")
    File(targetContext.cacheDir, "narration-memory-phone.json").writeText(report.toString(2))
    return Bundle().apply { putString("report", report.toString()); putString("automatic_chain", report.optString("automatic_chain")) }
}
