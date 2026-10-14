package com.kitt.reader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** Natural real-device no-network check. Never changes radios, settings, credentials or coordinates.
 * The operator disables data/Wi-Fi first. Engine callbacks do not establish human hearing. */
internal fun Instrumentation.offlinePhoneProbe(runtime: KittRuntime): Bundle {
    val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE)
        .put("mode", "natural-physical-location-offline-pack-local-tts")
    val before = runtime.config
    val directorBefore = runtime.requests.directorCalls; val researchBefore = runtime.requests.researchCalls
    val phases = linkedSetOf<String>(); val sources = linkedSetOf<String>()
    var completed = false; var freshGpsSeconds = 0; var freshCountySeconds = 0; var seconds = 0
    var ownedSession = false
    fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    try {
        main {
            runtime.network.refresh()
            report.put("physical_internet", runtime.network.snapshot.physicalInternet)
                .put("default_validated", runtime.network.snapshot.validated).put("vpn_detected", runtime.network.snapshot.vpn)
            check(runtime.network.snapshot.physicalInternet == false) { "Disconnect physical Internet before this probe" }
            check(!runtime.journey.running && runtime.recovery == null) { "End existing journey first" }
            val began = SystemClock.elapsedRealtimeNanos()
            val pack = OfflinePack.load(targetContext)
            report.put("pack_read_parse_us", (SystemClock.elapsedRealtimeNanos() - began) / 1000)
            check(pack != null)
            report.put("pack_version", pack.version).put("facts", pack.objects.sumOf { it.facts.size })
            runtime.start()
            ownedSession = true
        }
        runBlocking {
            while (seconds < 120 && !completed) {
                delay(1000); seconds++
                main {
                    check(runtime.network.snapshot.physicalInternet == false) { "Physical Internet returned; no-network observation invalid" }
                    val fix = runtime.journey.fix
                    fix?.let { sources += it.source.name }
                    if (fix?.source == FixSource.GPS && fix.precise(System.currentTimeMillis())) freshGpsSeconds++
                    if (runtime.offlinePack?.resolve(fix, System.currentTimeMillis()) != null) freshCountySeconds++
                    runtime.journey.narrationMemory.forEach { phases += it.phase.name }
                    completed = runtime.journey.narrationMemory.any { it.phase == NarrationPhase.COMPLETED }
                }
            }
        }
        main {
            report.put("offline_voice_metadata_ready", (runtime.voice as? AndroidVoice)?.offlineReady ?: false)
                .put("tts_message", (runtime.voice as? AndroidVoice)?.voiceMessage.orEmpty())
                .put("completed_narrations", runtime.journey.narrationMemory.count { it.phase == NarrationPhase.COMPLETED })
                .put("subject", runtime.journey.topic)
                .put("director_calls", runtime.requests.directorCalls - directorBefore)
                .put("research_calls", runtime.requests.researchCalls - researchBefore)
                .put("settings_unchanged", runtime.config == before)
                .put("engine_chain", if (completed && freshGpsSeconds > 0) "PASS_ENGINE_ONLY" else "PENDING")
            check(runtime.requests.directorCalls == directorBefore && runtime.requests.researchCalls == researchBefore)
        }
    } catch (e: Exception) {
        report.put("engine_chain", "PENDING").put("failure_class", e.javaClass.simpleName)
    } finally {
        main {
            if (ownedSession) { runtime.end(); runtime.dismissSummary(); targetContext.stopService(Intent(targetContext, JourneyService::class.java)) }
        }
    }
    report.put("seconds", seconds).put("fresh_gps_seconds", freshGpsSeconds).put("trusted_county_seconds", freshCountySeconds)
        .put("physical_sources", sources.joinToString(",")).put("phases", phases.joinToString(","))
        .put("human_hearing", "PENDING: requires explicit listener feedback")
        .put("limits", "Historical open boundary, no town boundary, no coordinates retained or injected; no-network engine completion does not prove hearing")
    File(targetContext.cacheDir, "offline-phone.json").writeText(report.toString(2))
    return Bundle().apply { putString("report", report.toString()) }
}
