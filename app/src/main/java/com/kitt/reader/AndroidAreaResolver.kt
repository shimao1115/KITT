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
    fun cached(fix: Fix): AreaIdentity? = resolved.takeIf { anchor?.distanceTo(fix)?.let { it < 3000 } == true }
    fun resolve(fix: Fix, complete: (AreaIdentity?) -> Unit) {
        if (!throttle.begin(fix, System.currentTimeMillis())) return
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
                        address?.let { AreaIdentity.geocoderFields(it.locality, it.subAdminArea, it.subLocality) }
                    }
                } }
                ensureActive()
                resolved = area; anchor = fix
                diagnostic(if (area == null) "area unresolved (platform unavailable/incomplete)" else "area resolved")
                complete(area)
            } catch (_: TimeoutCancellationException) { diagnostic("area lookup timed out; GPS continues") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { diagnostic("area lookup failed; GPS continues") }
            finally { throttle.complete() }
        }
    }
    fun stop() { job?.cancel(); job = null; resolved = null; anchor = null }
}
