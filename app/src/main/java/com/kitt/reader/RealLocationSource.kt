package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** GPS and Android's cellular/Wi-Fi assisted provider share one physical-fix selector. No GeoIP. */
class RealLocationSource(context: Context, private val unavailable: (String) -> Unit) : LocationSource {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null
    private var areaScope: CoroutineScope? = null
    private var resolver: AndroidAreaResolver? = null
    private var latest: Fix? = null
    private var generation = 0L
    override fun start(onFix: (Fix) -> Unit) {
        stop()
        val session = generation
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            unavailable("定位权限不可用，请在系统设置允许。"); return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        areaScope = scope
        val lookup = AndroidAreaResolver(context, scope) { android.util.Log.i("KITTArea", it) }
        resolver = lookup
        val selector = PhysicalLocationSelector { android.util.Log.i("KITTLocation", it) }
        val providers = mapOf(LocationManager.GPS_PROVIDER to FixSource.GPS, LocationManager.NETWORK_PROVIDER to FixSource.NETWORK)
        var lastNotice: String? = null
        fun publish() {
            if (generation != session) return
            val now = System.currentTimeMillis()
            val selected = selector.select(now, SystemClock.elapsedRealtime())
            val notice = when {
                selected == null -> "暂无可靠定位；等待 GPS 或系统网络辅助定位。"
                selected.source == FixSource.LAST_KNOWN -> "短暂沿用上次定位，位置不确定性增加。"
                !fine -> "系统粗略定位；允许精确定位可提高现场精度。"
                else -> ""
            }
            if (notice != lastNotice) { lastNotice = notice; unavailable(notice) }
            if (selected == null) { latest = null; return }
            // A bridge keeps the previous chapter as uncertain background; it cannot create a new chapter.
            val area = if (selected.source == FixSource.LAST_KNOWN) latest?.administrative
                else locationArea(lookup.cached(selected), selected)
            val enriched = selected.copy(administrative = area)
            if (enriched != latest) {
                latest = enriched
                android.util.Log.i("KITTLocation", "accepted source=${selected.source} origin=${selected.originSource} accuracy=${selected.accuracy.toInt()} age_ms=${selected.ageMs(now)}")
                onFix(enriched)
            }
            // Last-known is continuity only; never starts a new geocode from a stale coordinate.
            if (selected.source != FixSource.LAST_KNOWN) lookup.resolve(selected) { resolved ->
                if (generation == session) latest?.takeIf { it.source != FixSource.LAST_KNOWN && areaCacheValid(selected, it, System.currentTimeMillis()) }?.let { current ->
                    val updated = current.copy(administrative = locationArea(resolved, current))
                    if (updated != latest) { latest = updated; onFix(updated) }
                }
            }
        }
        val callback = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (generation != session) return
                val source = providers[location.provider] ?: return
                val fix = deviceFix(location, source) ?: return
                if (selector.offer(fix, System.currentTimeMillis(), SystemClock.elapsedRealtime())) publish()
            }
            override fun onProviderDisabled(provider: String) {
                if (generation != session) return
                providers[provider]?.let(selector::disable); publish()
            }
            override fun onProviderEnabled(provider: String) { publish() }
            @Deprecated("Platform callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        listener = callback
        val available = providers.filterKeys { it in manager.allProviders && (fine || it != LocationManager.GPS_PROVIDER) }
        // Newest cache first: an older provider cache cannot relocate a newer trusted measurement.
        available.mapNotNull { (provider, source) ->
            try { manager.getLastKnownLocation(provider)?.let { deviceFix(it, source) } }
            catch (_: SecurityException) { null }
            catch (_: IllegalArgumentException) { null }
        }.sortedByDescending { it.elapsedRealtimeMs }.forEach {
            selector.offer(it, System.currentTimeMillis(), SystemClock.elapsedRealtime(), lastKnown = true)
        }
        available.keys.forEach { provider ->
            try {
                // Register even when disabled: Android resumes this listener when the provider returns.
                manager.requestLocationUpdates(provider, 2000L, 0f, callback, Looper.getMainLooper())
            } catch (_: SecurityException) { android.util.Log.w("KITTLocation", "provider permission unavailable: $provider") }
            catch (_: IllegalArgumentException) { android.util.Log.w("KITTLocation", "provider unavailable: $provider") }
        }
        publish()
        scope.launch { while (isActive) { delay(1000); publish() } }
    }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    override fun stop() {
        generation++
        listener?.let { try { manager.removeUpdates(it) } catch (_: SecurityException) {} }; listener = null
        resolver?.stop(); resolver = null; areaScope?.cancel(); areaScope = null; latest = null
    }
}

/** Attribution comes exclusively from LocationManager; reject fixes without a measurement clock. */
internal fun deviceFix(location: Location, source: FixSource): Fix? {
    if (!location.hasAccuracy() || location.elapsedRealtimeNanos <= 0) return null
    return Fix(location.latitude, location.longitude, location.time,
        if (location.hasSpeed()) location.speed.toDouble() * 3.6 else 0.0,
        if (location.hasBearing()) location.bearing.toDouble() else 0.0,
        if (source == FixSource.GPS && location.hasAltitude()) location.altitude else null,
        location.accuracy.toDouble(), source = source, elapsedRealtimeMs = location.elapsedRealtimeNanos / 1_000_000)
}

internal fun locationArea(area: AreaIdentity?, fix: Fix): AreaIdentity? =
    if (fix.accuracy > 500 || fix.source == FixSource.LAST_KNOWN) area?.copy(chapter = "") else area
