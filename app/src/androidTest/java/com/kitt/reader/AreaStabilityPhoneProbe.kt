package com.kitt.reader

import android.app.Instrumentation
import android.app.ActivityManager
import android.app.KeyguardManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import org.json.JSONObject
import java.io.File

/** Explicit ten-minute natural source + production screen probe. No radio, settings or TripStore writes. */
internal class AreaStabilityPhoneProbe(private val instrumentation: Instrumentation) {
    fun run(): Bundle = with(instrumentation) {
        val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("mock_injection", false)
            .put("mode", "natural-source-production-screen-temporary-journey").put("window_seconds", 600)
        var activity: MainActivity? = null
        var source: RealLocationSource? = null
        val cards = AreaCards()
        val voice = object : VoicePort {
            override fun stop() {}
            override fun speak(text: String, complete: (Boolean) -> Unit) {}
            override fun listen(result: (String?) -> Unit) {}
        }
        val journey = Journey(System::currentTimeMillis, voice)
        var last: Fix? = null
        var notice = ""
        var publications = 0; var firstSuccess: Long? = null
        var drops = 0; var freshDrops = 0; var freshSeconds = 0
        var foregroundSeconds = 0; var interactiveSeconds = 0; var unlockedSeconds = 0
        var uiSamples = 0; var uiUnresolved = 0; var uiMatched = 0; var uiUnavailable = 0
        var minAccuracy = Double.POSITIVE_INFINITY; var maxAccuracy = 0.0; var maxAge = 0L
        var maxAnchorDistance = 0.0; var anchor: Fix? = null
        val sources = linkedMapOf<String, Int>()
        var previousSource: FixSource? = null; var sourceTransitions = 0
        val started = SystemClock.elapsedRealtime()
        try {
            sendStatus(0, Bundle().apply { putString("stage", "launch") })
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
                "am start -f 0x10008000 -n ${targetContext.packageName}/com.kitt.reader.MainActivity")).use { it.readBytes() }
            activity = monitor.waitForActivityWithTimeout(10000) as? MainActivity
            removeMonitor(monitor)
            check(activity != null) { "No foreground activity observed" }
            sendStatus(0, Bundle().apply { putString("stage", "sampling") })
            runOnMainSync {
                val runtime = (targetContext.applicationContext as KittApp).runtime
                check(!runtime.journey.running) { "Existing journey must not be changed by this probe" }
                report.put("saved_config", JSONObject().put("provider", runtime.config.kind.name)
                    .put("model", runtime.config.model).put("effort", runtime.config.effort)
                    .put("account_connected", runtime.chatGpt.record.connected)
                    .put("credential_unavailable", runtime.settings.credentialUnavailable)
                    .put("speech_rate", runtime.settings.speechRate)
                    .put("independent_research", runtime.settings.readResearch() != null)
                    .put("recovery_present", runtime.recovery != null))
                journey.start()
                activity!!.setContent { KittTheme { Surface {
                    DrivingScreen(journey, "自然定位验收采样 · 临时旅程 · 不调用AI", {}, {}, {}, {}, {})
                } } }
                activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                source = RealLocationSource(targetContext) { notice = it }
                source!!.start { fix ->
                    publications++; last = fix; journey.location(fix); cards.accept(fix.administrative)
                    if (previousSource != null && previousSource != fix.source) sourceTransitions++
                    previousSource = fix.source
                    if (fix.administrative != null && firstSuccess == null) {
                        firstSuccess = SystemClock.elapsedRealtime(); anchor = fix
                    }
                }
            }
            val power = targetContext.getSystemService(PowerManager::class.java)
            val keyguard = targetContext.getSystemService(KeyguardManager::class.java)
            repeat(600) { second ->
                Thread.sleep(1000)
                var label: String? = null; var observeUI = false
                runOnMainSync {
                    val elapsed = SystemClock.elapsedRealtime()
                    val process = ActivityManager.RunningAppProcessInfo()
                    ActivityManager.getMyMemoryState(process)
                    if (process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) foregroundSeconds++
                    if (power.isInteractive) interactiveSeconds++
                    if (!keyguard.isKeyguardLocked) unlockedSeconds++
                    val fix = last
                    val age = fix?.ageMs(System.currentTimeMillis()) ?: Long.MAX_VALUE
                    val fresh = source!!.waitingSince == null && fix != null && fix.source in setOf(FixSource.GPS, FixSource.NETWORK) &&
                        age in 0..(if (fix.source == FixSource.GPS) 15000L else 30000L) && fix.accuracy <= 500
                    sources[fix?.source?.name ?: "UNKNOWN"] = (sources[fix?.source?.name ?: "UNKNOWN"] ?: 0) + 1
                    if (fresh) {
                        freshSeconds++; minAccuracy = minOf(minAccuracy, fix!!.accuracy)
                        maxAccuracy = maxOf(maxAccuracy, fix.accuracy); maxAge = maxOf(maxAge, age)
                        anchor?.let { maxAnchorDistance = maxOf(maxAnchorDistance, it.distanceTo(fix)) }
                    }
                    if (firstSuccess != null && journey.currentFix?.administrative == null) { drops++; if (fresh) freshDrops++ }
                    label = journey.currentFix?.administrative?.label
                    observeUI = second % 15 == 14 && firstSuccess?.let { elapsed - it >= 2000 } == true
                }
                if (observeUI) {
                    Thread.sleep(200) // Let the production Compose label commit before reading accessibility.
                    val root = uiAutomation.rootInActiveWindow
                    if (root == null) uiUnavailable++ else {
                        val texts = mutableListOf<String>()
                        fun collect(node: AccessibilityNodeInfo) {
                            node.text?.toString()?.let(texts::add)
                            for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it); it.recycle() }
                        }
                        collect(root); root.recycle(); uiSamples++
                        if (texts.any { "地区名称未解析" in it }) uiUnresolved++
                        if (label != null && texts.any { label!! in it }) uiMatched++
                    }
                }
                if (second % 60 == 59) sendStatus(0, Bundle().apply {
                    putString("stage", "observed_${second + 1}s"); putInt("fresh_seconds", freshSeconds)
                    putInt("fresh_name_drops", freshDrops)
                })
            }
            report.put("outcome", when {
                foregroundSeconds != 600 || unlockedSeconds != 600 -> "INVALID_FOREGROUND_WINDOW"
                firstSuccess == null -> "NO_SUCCESSFUL_GEOCODE"
                freshSeconds < 590 -> "POSITION_GAPS_REQUIRE_REVIEW"
                freshDrops != 0 || uiUnresolved != 0 -> "NAME_DROPS_REQUIRE_REVIEW"
                uiSamples == 0 || uiMatched != uiSamples -> "UI_REQUIRES_REVIEW"
                else -> "PASS_CURRENT_CONDITIONS_ONLY"
            })
        } catch (e: Exception) { report.put("outcome", "FAILED").put("failure", e.javaClass.simpleName) }
        finally { runOnMainSync {
            source?.stop(); journey.end(); activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.finish()
        } }
        val logs = android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
            "logcat -d --pid=${android.os.Process.myPid()} -v brief -s KITTArea:I '*:S'")).use { it.readBytes().toString(Charsets.UTF_8) }
        val events = JSONObject()
        for (event in listOf("geo_started", "geo_success", "geo_empty", "geo_error", "geo_timeout", "geo_discarded", "cache_invalidated"))
            events.put(event, logs.lineSequence().count { event in it })
        report.put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("publications", publications)
            .put("first_success_ms", firstSuccess?.minus(started) ?: JSONObject.NULL)
            .put("fresh_seconds", freshSeconds).put("foreground_seconds", foregroundSeconds)
            .put("interactive_seconds", interactiveSeconds).put("unlocked_seconds", unlockedSeconds)
            .put("sources", JSONObject(sources as Map<*, *>)).put("name_drops_after_success", drops)
            .put("source_transitions", sourceTransitions)
            .put("fresh_name_drops_after_success", freshDrops).put("chapter_transitions", cards.transitions)
            .put("accuracy_min_m", minAccuracy.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("accuracy_max_m", maxAccuracy).put("age_max_ms", maxAge).put("max_anchor_distance_m", maxAnchorDistance)
            .put("ui_samples", uiSamples).put("ui_label_matched", uiMatched).put("ui_unresolved", uiUnresolved)
            .put("ui_unavailable", uiUnavailable).put("geocode_events", events)
            .put("geocode_events_note", if (logs.isBlank()) "No observable KITTArea output; zero counts are not query or invalidation evidence" else "Counts of visible KITTArea logs only")
            .put("limits", "Temporary local Journey/AreaCards + production screen/source; no AI/research, real journey writes, movement, injected failures, radio/VPN changes, or account network validation")
        File(targetContext.cacheDir, "area-stability-phone.json").writeText(report.toString(2))
        Bundle().apply { putString("outcome", report.getString("outcome")); putString("report", report.toString()) }
    }
}
