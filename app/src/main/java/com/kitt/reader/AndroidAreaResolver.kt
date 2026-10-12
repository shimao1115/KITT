package com.kitt.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.location.Geocoder
import android.location.Address
import android.os.Build
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

/** Platform enrichment on IO; raw GPS is delivered immediately and never waits for the service. */
class AndroidAreaResolver(context: Context, private val scope: CoroutineScope,
    private val diagnostic: (String) -> Unit = {},
    private val lookup: (suspend (Fix) -> AreaIdentity?)? = null,
    private val now: () -> Long = System::currentTimeMillis) {
    var pendingSince by androidx.compose.runtime.mutableStateOf<Long?>(null); private set
    private val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
    private val throttle = GeocodeThrottle()
    private var job: Job? = null
    private val cache = AreaResolutionCache(diagnostic)
    fun cached(fix: Fix?): AreaIdentity? = cache.cached(fix, now())
    fun resolve(fix: Fix, complete: () -> Unit) {
        cached(fix)
        if (!areaCacheValid(fix, fix, now()) || !throttle.begin(fix, android.os.SystemClock.elapsedRealtime())) return
        val revision = cache.revision
        diagnostic("geo_started")
        pendingSince = android.os.SystemClock.elapsedRealtime()
        job = scope.launch {
            try {
                val area = withTimeout(8000) { if (lookup != null) lookup.invoke(fix) else withContext(Dispatchers.IO) {
                    if (!Geocoder.isPresent()) null else {
                        val address = if (Build.VERSION.SDK_INT >= 33) {
                            suspendCancellableCoroutine<Address?> { continuation ->
                                geocoder.getFromLocation(fix.latitude, fix.longitude, 1, object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                                    }
                                    override fun onError(errorMessage: String?) {
                                        if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("Geocoder service error")))
                                    }
                                })
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull()
                        }
                        // Thoroughfare is a road, not a town. Do not fabricate a chapter from it.
                        address?.let { coordinateArea(it, fix) }
                    }
                } }
                ensureActive()
                val accepted = cache.success(fix, area, revision, now())
                diagnostic(if (area == null) "geo_empty" else if (accepted) "geo_success" else "geo_discarded")
                complete()
            } catch (_: TimeoutCancellationException) { diagnostic("geo_timeout") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { diagnostic("geo_error") }
            finally { pendingSince = null; throttle.complete() }
        }
    }
    fun stop() { pendingSince = null; job?.cancel(); job = null; cache.clear() }
}

/** Reverse geocoding enriches the supplied coordinate, never a network-inferred country or position. */
internal fun coordinateArea(address: Address, fix: Fix): AreaIdentity? {
    if (!address.hasLatitude() || !address.hasLongitude()) return null
    val returned = Fix(address.latitude, address.longitude, fix.timeMs)
    if (!returned.valid() || returned.distanceTo(fix) > maxOf(2000.0, fix.accuracy * 2)) return null
    return locationArea(AreaIdentity.geocoderFields(address.locality, address.subAdminArea, address.subLocality, address.adminArea), fix)
}
