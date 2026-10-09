package com.kitt.reader

import android.Manifest
import android.app.Instrumentation
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Short, explicitly invoked native A/B/A experiment. Coordinates live only in bounded RAM. */
internal class FusedLocationPhoneProbe(private val instrumentation: Instrumentation, arguments: Bundle?) {
    private val condition = arguments?.getString("condition")?.take(80) ?: "current-condition-unconfirmed"
    private val seconds = (arguments?.getString("seconds")?.toIntOrNull() ?: 65).coerceIn(30, 180)
    fun run(): Bundle = with(instrumentation) {
        val report = JSONObject().put("mode", "natural-native-A-B-A").put("sdk", Build.VERSION.SDK_INT)
            .put("condition", condition).put("mock_injection", false).put("phase_seconds", seconds)
        val phases = JSONArray()
        try {
            sendStatus(0, Bundle().apply { putString("stage", "launch") })
            // Android 16/OEM background-launch restrictions can leave an instrumentation process frozen.
            // Explicit shell launch grants exactly the same foreground entry as the user's adb test command.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
                "am start -n ${targetContext.packageName}/com.kitt.reader.MainActivity")).use { it.readBytes() }
            Thread.sleep(1500)
            runOnMainSync {
                check(!(targetContext.applicationContext as KittApp).runtime.journey.running) { "Existing journey must be ended first" }
            }
            val manager = targetContext.getSystemService(LocationManager::class.java)
            val fine = targetContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = targetContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            report.put("fine_permission", fine).put("coarse_permission", coarse)
                .put("all_providers", JSONArray(manager.allProviders))
                .put("fused_has_provider", Build.VERSION.SDK_INT >= 31 && manager.hasProvider(LocationManager.FUSED_PROVIDER))
            check(fine || coarse)
            runOnMainSync {
                val runtime = (targetContext.applicationContext as KittApp).runtime
                report.put("saved_config", JSONObject().put("provider", runtime.config.kind.name)
                    .put("model", runtime.config.model).put("effort", runtime.config.effort)
                    .put("account_connected", runtime.chatGpt.record.connected).put("speech_rate", runtime.settings.speechRate)
                    .put("independent_research", runtime.settings.readResearch() != null))
            }
            val cm = targetContext.getSystemService(ConnectivityManager::class.java)
            fun network(): JSONObject {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                return JSONObject().put("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                    .put("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                    .put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                    .put("vpn_visible", cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true })
            }
            report.put("network_before", network())
            for ((name, fused) in listOf("A1" to false, "B" to true, "A2" to false)) {
                val phase = Phase(manager, fine, fused)
                runOnMainSync { phase.start() }
                sendStatus(0, Bundle().apply { putString("stage", name) })
                try {
                    repeat(seconds) {
                        Thread.sleep(1000)
                        runOnMainSync {
                            val process = ActivityManager.RunningAppProcessInfo()
                            ActivityManager.getMyMemoryState(process)
                            phase.tick(process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                        }
                    }
                    runOnMainSync { phases.put(phase.report().put("phase", name)) }
                } finally { runOnMainSync { phase.stop() } }
                File(targetContext.cacheDir, "fused-location-phone-progress.json").writeText(phases.toString(2))
            }
            report.put("network_after", network()).put("outcome", "MEASURED")
        } catch (e: Exception) { report.put("outcome", "FAILED").put("failure", e.javaClass.simpleName) }
        report.put("phases", phases).put("limits", "No simulated fixes; outdoor, movement, driving, long lockscreen and energy are not established")
        File(targetContext.cacheDir, "fused-location-phone.json").writeText(report.toString(2))
        Bundle().apply { putString("outcome", report.getString("outcome")); putString("report", report.toString()) }
    }

    private class Stats {
        var raw = 0; var accepted = 0; var cache = 0; var firstValid: Long? = null; var lastCallback: Long? = null
        val rejects = linkedMapOf<String, Int>()
        val rawProviders = linkedMapOf<String, Int>()
        val accuracy = mutableListOf<Double>(); val ages = mutableListOf<Long>()
        val samples = ArrayDeque<Fix>()
        fun reject(reason: String) { rejects[reason] = (rejects[reason] ?: 0) + 1 }
        fun json(start: Long, now: Long): JSONObject {
            val sorted = accuracy.sorted()
            return JSONObject().put("raw_callbacks", raw).put("accepted_offers", accepted).put("cache_received", cache)
                .put("first_valid_callback_ms", firstValid?.minus(start) ?: JSONObject.NULL)
                .put("last_callback_age_ms", lastCallback?.let { now - it } ?: JSONObject.NULL)
                .put("rejects", JSONObject(rejects as Map<*, *>))
                .put("raw_provider_labels", JSONObject(rawProviders as Map<*, *>))
                .put("sampled_measurements", samples.size)
                .put("accuracy_min_m", sorted.firstOrNull() ?: JSONObject.NULL)
                .put("accuracy_p50_m", sorted.getOrNull(sorted.size / 2) ?: JSONObject.NULL)
                .put("accuracy_max_m", sorted.lastOrNull() ?: JSONObject.NULL)
                .put("measurement_age_min_ms", ages.minOrNull() ?: JSONObject.NULL)
                .put("measurement_age_max_ms", ages.maxOrNull() ?: JSONObject.NULL)
        }
    }
    private class Continuity {
        val selected = linkedMapOf<String, Int>()
        var fresh = 0; var known = 0; var transitions = 0; var gap = 0; var maxGap = 0; var previousKnown = false
        var freshGap = 0; var maxFreshGap = 0
        var last: Fix? = null
        fun tick(fix: Fix?) {
            last = fix
            val name = fix?.source?.name ?: "UNKNOWN"
            selected[name] = (selected[name] ?: 0) + 1
            val isKnown = fix != null
            val isFresh = isKnown && fix!!.source != FixSource.LAST_KNOWN
            if (isFresh) { fresh++; freshGap = 0 } else { freshGap++; maxFreshGap = maxOf(maxFreshGap, freshGap) }
            if (isKnown) { known++; gap = 0 } else { gap++; maxGap = maxOf(maxGap, gap) }
            if (previousKnown && !isKnown) transitions++
            previousKnown = isKnown
        }
        fun json(now: Long) = JSONObject().put("source_seconds", JSONObject(selected as Map<*, *>))
            .put("fresh_seconds", fresh).put("known_seconds", known).put("max_unknown_seconds", maxGap)
            .put("max_no_fresh_seconds", maxFreshGap).put("known_to_unknown", transitions)
            .put("final_source", last?.source?.name ?: "UNKNOWN")
            .put("final_origin", last?.originSource?.name ?: "UNKNOWN")
            .put("final_measurement_age_ms", last?.ageMs(System.currentTimeMillis(), now) ?: JSONObject.NULL)
    }
    private class Phase(private val manager: LocationManager, private val fine: Boolean, private val fused: Boolean) {
        private val start = SystemClock.elapsedRealtime()
        private val stats = linkedMapOf<FixSource, Stats>()
        private val registrations = JSONObject()
        private val listeners = mutableListOf<LocationListener>()
        private var active = true
        private var rejection: String? = null
        private val baseline = PhysicalLocationSelector()
        private val augmented = PhysicalLocationSelector { if (it.startsWith("fix rejected reason=")) rejection = it.substringAfter("reason=") }
        private val baseContinuity = Continuity(); private val fusedContinuity = Continuity()
        private var extraFreshSeconds = 0
        private var foregroundSeconds = 0
        fun start() {
            val caches = mutableListOf<Pair<Location, (Location, Boolean) -> Unit>>()
            val providers = linkedMapOf(LocationManager.GPS_PROVIDER to FixSource.GPS, LocationManager.NETWORK_PROVIDER to FixSource.NETWORK)
            if (fused && Build.VERSION.SDK_INT >= 31) providers[LocationManager.FUSED_PROVIDER] = FixSource.FUSED
            for ((provider, source) in providers) {
                val stat = Stats(); stats[source] = stat
                val present = if (Build.VERSION.SDK_INT >= 31) manager.hasProvider(provider) else provider in manager.allProviders
                val registration = JSONObject().put("present", present)
                registrations.put(source.name, registration)
                if (!present || (!fine && source == FixSource.GPS)) { registration.put("result", "SKIPPED"); continue }
                try {
                    registration.put("enabled", manager.isProviderEnabled(provider))
                    fun receive(location: Location, cache: Boolean) {
                        if (!active) return
                        val now = SystemClock.elapsedRealtime()
                        if (cache) stat.cache++ else { stat.raw++; stat.lastCallback = now }
                        val label = location.provider?.takeIf { it in setOf("gps", "network", "fused") } ?: "OTHER"
                        if (!cache) stat.rawProviders[label] = (stat.rawProviders[label] ?: 0) + 1
                        val invalid = when {
                            !location.hasAccuracy() -> "device_no_accuracy"
                            location.elapsedRealtimeNanos <= 0 -> "device_no_measurement_clock"
                            label == "OTHER" -> "device_unknown_source"
                            source != FixSource.FUSED && location.provider != provider -> "device_source_mismatch"
                            else -> null
                        }
                        if (invalid != null) { stat.reject(invalid); return }
                        val fix = deviceFix(location, source) ?: return
                        if (!cache) {
                            if (stat.samples.size < 256) {
                                stat.samples.addLast(fix)
                                if (fix.accuracy.isFinite()) stat.accuracy += fix.accuracy
                                stat.ages += fix.ageMs(System.currentTimeMillis(), now)
                            }
                        }
                        rejection = null
                        if (augmented.offer(fix, System.currentTimeMillis(), now, cache)) {
                            if (!cache) { stat.accepted++; if (stat.firstValid == null) stat.firstValid = now }
                        } else stat.reject((if (cache) "cache_" else "selector_") + (rejection ?: "unknown"))
                        if (source != FixSource.FUSED) baseline.offer(fix, System.currentTimeMillis(), now, cache)
                    }
                    manager.getLastKnownLocation(provider)?.let { caches += it to ::receive }
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) { receive(location, false) }
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            if (!active) return
                            augmented.disable(source); if (source != FixSource.FUSED) baseline.disable(source)
                        }
                        @Deprecated("Platform callback")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    listeners += listener
                    manager.requestLocationUpdates(provider, 2000L, 0f, listener, Looper.getMainLooper())
                    registration.put("result", "REGISTERED")
                } catch (_: SecurityException) { registration.put("result", "PERMISSION_FAILURE") }
                catch (_: IllegalArgumentException) { registration.put("result", "PROVIDER_FAILURE") }
            }
            // Match production's newest-cache-first plausibility anchor.
            caches.sortedByDescending { it.first.elapsedRealtimeNanos }.forEach { (location, receive) -> receive(location, true) }
        }
        fun tick(foreground: Boolean) {
            if (foreground) foregroundSeconds++
            val elapsed = SystemClock.elapsedRealtime(); val now = System.currentTimeMillis()
            val base = baseline.select(now, elapsed); val extra = augmented.select(now, elapsed)
            baseContinuity.tick(base); fusedContinuity.tick(extra)
            if (extra?.source == FixSource.FUSED && (base == null || base.source == FixSource.LAST_KNOWN)) extraFreshSeconds++
        }
        fun stop() { active = false; listeners.forEach { manager.removeUpdates(it) }; listeners.clear() }
        fun report(): JSONObject {
            val now = SystemClock.elapsedRealtime()
            val sources = JSONObject(); stats.forEach { (source, stat) -> sources.put(source.name, stat.json(start, now)) }
            val others = stats.filterKeys { it != FixSource.FUSED }.values.flatMap { it.samples }
            val fs = stats[FixSource.FUSED]?.samples.orEmpty()
            val mirrors = fs.count { f -> others.any { it.elapsedRealtimeMs == f.elapsedRealtimeMs && it.distanceTo(f) < 1.0 } }
            return JSONObject().put("registrations", registrations).put("sources", sources)
                .put("foreground_seconds", foregroundSeconds)
                .put("actual_window_ms", now - start)
                .put("baseline", baseContinuity.json(now)).put("augmented", fusedContinuity.json(now))
                .put("fused_mirrored_measurements", mirrors).put("fused_unmatched_measurements", fs.size - mirrors)
                .put("fused_extra_fresh_seconds", extraFreshSeconds)
        }
    }
}
