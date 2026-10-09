package com.kitt.reader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** ADB-only smoke of production state. No fixtures, credentials, coordinates or settings changes. */
internal fun Instrumentation.runtimeStatusPhoneProbe(runtime: KittRuntime): Bundle {
    val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("mode", "production-runtime-status")
        .put("provider", runtime.config.kind.name).put("model", runtime.config.model)
        .put("effort", runtime.config.effort.ifBlank { "default" })
        .put("independent_research", runtime.settings.readResearch() != null)
    val tasks = linkedSetOf<String>()
    val sources = linkedSetOf<String>()
    val voicePhases = linkedSetOf<String>()
    val result = Bundle()
    fun snapshot(name: String) {
        val value = runtime.statusSnapshot(SystemClock.elapsedRealtime())
        report.put(name, JSONObject().put("position", value.position).put("network", value.network)
            .put("services", value.services).put("primary", value.primary))
    }
    try {
        runOnMainSync {
            check(!runtime.journey.running) { "End the existing Journey before this smoke probe" }
            snapshot("idle")
            check(runtime.requests.ai == null && runtime.requests.research == null)
            ContextCompat.startForegroundService(targetContext,
                Intent(targetContext, JourneyService::class.java).setAction(JourneyService.START))
        }
        runBlocking {
            // Location and automatic Director/research use the phone's existing configuration.
            repeat(45) { second ->
                delay(1000)
                runOnMainSync {
                    runtime.requests.work.filter { it.job?.isActive != false }.forEach { tasks += it.task.name }
                    runtime.journey.currentFix?.let { sources += it.source.name }
                    (runtime.voice as? AndroidVoice)?.let { voicePhases += it.detail.phase.name }
                    if (second == 4) snapshot("startup")
                    if (second == 34) snapshot("after_35s")
                }
            }
        }
        runOnMainSync {
            snapshot("after_45s")
            runtime.quietToggle(); snapshot("quiet")
            check(runtime.statusSnapshot(SystemClock.elapsedRealtime()).primary.startsWith("安静模式"))
            runtime.journey.resume()
            runtime.loop.speak(); snapshot("listening")
            check(runtime.journey.awaitingReply)
            runtime.journey.cancelReply()
            // Same typed-user production path; no invented transcript or microphone audio.
            runtime.loop.user("河流为什么会弯曲？请只解释一般机制。")
        }
        runBlocking {
            repeat(75) {
                delay(1000)
                runOnMainSync {
                    runtime.requests.work.filter { it.job?.isActive != false }.forEach { tasks += it.task.name }
                    (runtime.voice as? AndroidVoice)?.let { voicePhases += it.detail.phase.name }
                }
            }
        }
        runOnMainSync {
            snapshot("active_result")
            report.put("ai_outcome", runtime.requests.ai?.outcome?.name ?: "NEVER_ATTEMPTED")
                .put("research_outcome", runtime.requests.research?.outcome?.name ?: "NEVER_ATTEMPTED")
                .put("tasks_observed", tasks.joinToString(",")).put("sources_observed", sources.joinToString(","))
                .put("voice_phases_observed", voicePhases.joinToString(","))
            runtime.end(); targetContext.stopService(Intent(targetContext, JourneyService::class.java))
            check(runtime.requests.work.isEmpty() && !runtime.journey.running)
            runtime.dismissSummary(); snapshot("ended")
        }
        report.put("outcome", "PASS_CURRENT_CONDITIONS_ONLY")
    } catch (e: Exception) { report.put("outcome", "FAILED").put("failure", e.javaClass.simpleName) }
    finally { runOnMainSync {
        runtime.end(); runtime.dismissSummary(); targetContext.stopService(Intent(targetContext, JourneyService::class.java))
    } }
    report.put("limits", "No real GPS-loss driving cycle, confirmed foreign VPN exit, tablet hardware, intentional service outage, or human ASR/TTS listening acceptance")
    File(targetContext.cacheDir, "runtime-status-phone.json").writeText(report.toString(2))
    result.putString("outcome", report.getString("outcome")); result.putString("report", report.toString())
    return result
}
