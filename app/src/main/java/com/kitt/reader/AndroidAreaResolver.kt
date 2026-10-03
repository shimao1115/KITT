package com.kitt.reader

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.*
import java.util.Locale

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
                val area = withContext(Dispatchers.IO) {
                    if (!Geocoder.isPresent()) null else {
                        @Suppress("DEPRECATION")
                        val address = geocoder.getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull()
                        // Thoroughfare is a road, not a town. Do not fabricate a chapter from it.
                        address?.let { AreaIdentity.normalize(it.locality, it.subAdminArea, it.subLocality) }
                    }
                }
                ensureActive()
                resolved = area; anchor = fix
                diagnostic(if (area == null) "area unresolved (platform unavailable/incomplete)" else "area resolved")
                complete(area)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { diagnostic("area lookup failed; GPS continues") }
            finally { throttle.complete() }
        }
    }
    fun stop() { job?.cancel(); job = null; resolved = null; anchor = null }
}
