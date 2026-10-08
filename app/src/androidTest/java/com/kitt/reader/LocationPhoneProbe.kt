package com.kitt.reader

import android.app.Instrumentation
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Natural device measurements only. Does not inject fixes, toggle radios/VPN, or start a research journey. */
internal fun Instrumentation.locationPhoneProbe(runtime: KittRuntime): Bundle {
    val result = Bundle()
    val manager = targetContext.getSystemService(LocationManager::class.java)
    val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("mode", "natural-device-location")
        .put("mock_injection", false).put("window_ms", 45_000)
        .put("provider", runtime.config.kind.name).put("model", runtime.config.model)
        .put("effort", runtime.config.effort.ifBlank { "default" }).put("independent_research", runtime.settings.readResearch() != null)
    val counts = linkedMapOf<String, Int>()
    var last: Fix? = null
    var notice = ""
    var source: RealLocationSource? = null
    var freshFix = false
    try {
        runOnMainSync {
            check(!runtime.journey.running) { "End the existing journey before this standalone location probe" }
            source = RealLocationSource(targetContext) { notice = it }
            source!!.start { fix ->
                counts[fix.source.name] = (counts[fix.source.name] ?: 0) + 1
                if (fix.source != FixSource.LAST_KNOWN && fix.ageMs(System.currentTimeMillis()) in 0..30_000) freshFix = true
                last = fix
            }
        }
        runBlocking { delay(45_000) }
        runOnMainSync {
            report.put("sources", JSONObject(counts as Map<*, *>)).put("fresh_device_fix", freshFix)
                .put("gps_enabled", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                .put("network_available", LocationManager.NETWORK_PROVIDER in manager.allProviders)
                .put("network_enabled", LocationManager.NETWORK_PROVIDER in manager.allProviders && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                .put("last_notice", notice)
            last?.let {
                report.put("last_source", it.source.name).put("accuracy_m", it.accuracy)
                    .put("age_ms", it.ageMs(System.currentTimeMillis())).put("area", it.administrative?.fullName.orEmpty())
            }
            source?.stop()
        }
        report.put("outcome", if (freshFix) "PASS_CURRENT_CONDITIONS_ONLY" else "NO_FRESH_FIX_IN_WINDOW")
    } catch (e: Exception) { report.put("outcome", "FAILED").put("failure", e.javaClass.simpleName) }
    finally { runOnMainSync { source?.stop() } }
    // No coordinates/track, identifiers, credentials or network geography in this diagnostic artifact.
    report.put("field_cases", "Outdoor GPS loss/recovery, Wi-Fi/cellular switching and foreign VPN exit are NOT established by this probe")
    File(targetContext.cacheDir, "location-fallback-phone.json").writeText(report.toString(2))
    result.putString("report", report.toString()); result.putString("outcome", report.getString("outcome"))
    return result
}
