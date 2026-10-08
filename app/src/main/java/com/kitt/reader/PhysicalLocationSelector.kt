package com.kitt.reader

/** Only device-location measurements enter here. Network routing and search have no input port. */
class PhysicalLocationSelector(private val diagnostic: (String) -> Unit = {}) {
    private val live = mutableMapOf<FixSource, Fix>()
    private var trusted: Fix? = null
    private var emitted: Fix? = null
    private var switchedAt = Long.MIN_VALUE / 2
    private var selectedSource: FixSource? = null

    fun offer(fix: Fix, now: Long, elapsedNow: Long, lastKnown: Boolean = false): Boolean {
        if (fix.source !in setOf(FixSource.GPS, FixSource.NETWORK, FixSource.FUSED) ||
            !fix.valid() || fix.accuracy <= 0 || fix.accuracy > 1500 ||
            fix.ageMs(now, elapsedNow) !in 0..60_000) return reject("invalid_or_expired")
        if (live[fix.source]?.let { !it.olderThan(fix) } == true) return reject("out_of_order")
        val anchor = trusted
        if (anchor != null) {
            // Independent Android providers can deliver measurements a little out of order.
            // Store a plausible recent secondary fix, but never emit an older position downstream.
            if (fix.measurementDeltaMs(anchor) < -15_000) return reject("older_than_trusted")
            val seconds = kotlin.math.abs(fix.measurementDeltaMs(anchor)) / 1000.0
            // Accuracy circles plus a generous road-speed bound, without averaging away actual travel.
            val metersPerSecond = maxOf(55.0, maxOf(anchor.speedKmh, fix.speedKmh) / 3.6 + 15.0)
            if (anchor.distanceTo(fix) > anchor.accuracy + fix.accuracy + 50 + seconds * metersPerSecond)
                return reject("impossible_jump")
        }
        if (lastKnown) {
            if (trusted == null || trusted!!.olderThan(fix)) trusted = fix
        } else {
            live[fix.source] = fix
            // The plausibility anchor also advances on usable updates from the secondary provider.
            if (trusted == null || trusted!!.olderThan(fix)) trusted = fix
        }
        return true
    }

    fun disable(source: FixSource) { live.remove(source) }

    fun select(now: Long, elapsedNow: Long): Fix? {
        fun age(fix: Fix) = fix.ageMs(now, elapsedNow)
        fun score(fix: Fix) = fix.accuracy + age(fix) / 1000.0 * maxOf(15.0, fix.speedKmh / 3.6)
        val candidates = live.values.filter { fix ->
            age(fix) in 0..(if (fix.source == FixSource.GPS) 15_000L else 30_000L) &&
                emitted?.let { !fix.olderThan(it) } != false
        }
        val best = candidates.minByOrNull(::score)
        val current = candidates.firstOrNull { it.source == selectedSource }
        val healthyGps = best?.source == FixSource.GPS && best.accuracy <= 50 && age(best) <= 8_000
        val chosen = if (current != null && best != null && current.source != best.source &&
            !healthyGps && (elapsedNow - switchedAt < 10_000 || score(best) >= score(current) * 0.7)) current else best
        val result = chosen ?: trusted?.takeIf { age(it) in 0..60_000 }?.let { fix ->
            fix.copy(source = FixSource.LAST_KNOWN, originSource = fix.source,
                accuracy = fix.accuracy + age(fix) / 1000.0 * maxOf(15.0, fix.speedKmh / 3.6),
                speedKmh = 0.0, bearing = 0.0, altitude = null, clue = "")
        }?.takeIf { it.valid() }
        if (result?.source != selectedSource) {
            diagnostic("source ${selectedSource ?: "UNKNOWN"} -> ${result?.source ?: "UNKNOWN"}")
            selectedSource = result?.source; switchedAt = elapsedNow
        }
        if (result != null) emitted = result
        return result
    }

    private fun reject(reason: String): Boolean { diagnostic("fix rejected reason=$reason"); return false }
}
