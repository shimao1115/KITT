package com.kitt.reader

import android.content.Context
import android.location.Geocoder
import android.location.Address
import android.os.Build
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

/** Platform enrichment on IO; raw GPS is delivered immediately and never waits for the service. */
class AndroidAreaResolver(context: Context, private val scope: CoroutineScope,
    private val diagnostic: (String) -> Unit = {}) {
    private val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
    private val throttle = GeocodeThrottle()
    private var job: Job? = null
    private var anchor: Fix? = null
    private var resolved: AreaIdentity? = null
    fun cached(fix: Fix): AreaIdentity? = resolved.takeIf { anchor?.let { areaCacheValid(it, fix, System.currentTimeMillis()) } == true }
    fun resolve(fix: Fix, complete: (AreaIdentity?) -> Unit) {
        if (!throttle.begin(fix, System.currentTimeMillis())) return
        diagnostic("area lookup started speedKmh=${fix.speedKmh.toInt()}")
        job = scope.launch {
            try {
                val area = withTimeout(8000) { withContext(Dispatchers.IO) {
                    if (!Geocoder.isPresent()) null else {
                        val address = if (Build.VERSION.SDK_INT >= 33) {
                            suspendCancellableCoroutine<Address?> { continuation ->
                                geocoder.getFromLocation(fix.latitude, fix.longitude, 1, object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                                    }
                                    override fun onError(errorMessage: String?) {
                                        if (continuation.isActive) continuation.resume(null)
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
                resolved = area; anchor = fix
                diagnostic(if (area == null) "area unresolved (platform unavailable/incomplete)" else "area resolved ${area.fullName}")
                complete(area)
            } catch (_: TimeoutCancellationException) { diagnostic("area lookup timed out; GPS continues") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { diagnostic("area lookup failed; GPS continues") }
            finally { throttle.complete() }
        }
    }
    fun stop() { job?.cancel(); job = null; resolved = null; anchor = null }
}

/** Reverse geocoding enriches the supplied coordinate, never a network-inferred country or position. */
internal fun coordinateArea(address: Address, fix: Fix): AreaIdentity? {
    if (!address.hasLatitude() || !address.hasLongitude()) return null
    val returned = Fix(address.latitude, address.longitude, fix.timeMs)
    if (!returned.valid() || returned.distanceTo(fix) > maxOf(2000.0, fix.accuracy * 2)) return null
    return locationArea(AreaIdentity.geocoderFields(address.locality, address.subAdminArea, address.subLocality, address.adminArea), fix)
}
