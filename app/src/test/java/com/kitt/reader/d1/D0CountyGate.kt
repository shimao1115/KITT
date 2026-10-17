package com.kitt.reader.d1

import com.kitt.packtool.ContentGate
import com.kitt.packtool.CountyBlocks
import com.kitt.packtool.GateReport
import com.kitt.reader.CountyBytes
import com.kitt.reader.CountyPackLoader
import com.kitt.reader.CountyProvenance
import com.kitt.reader.countyDigest

/** Bridges the D1 release verdict to the D0 county reader. The signature has already been verified
 *  by the caller, which is why provenance is supplied here and never read from the payload.
 *
 *  D1 is stricter than D0's read path on purpose: D0 may show "basic facts only" while hiding a
 *  blocked dependency, but a *release candidate* that silently drops facts or stories would
 *  misrepresent what the publisher shipped, so any blocked entry rejects the whole pack.
 */
object D0CountyGate : ContentGate {
    override fun inspect(blocks: CountyBlocks): GateReport = runCatching {
        val raw = CountyBytes(blocks.factManifest, blocks.facts, blocks.narrativeManifest, blocks.narratives)
        val pack = CountyPackLoader { CountyProvenance.TEST_ONLY }.read(raw.copy(expectedSha256 = countyDigest(raw)))
        val blocked = pack.blockedFacts + pack.stories.blocked
        val counts = Counts(pack.factCount, pack.sources.size, pack.claims.size, pack.stories.stories.size)
        if (blocked.isEmpty()) counts.report(true, null) else counts.report(false, blocked.values.first(), blocked)
    }.getOrElse { error -> GateReport(false, error.message ?: "content") }

    private data class Counts(val facts: Int, val sources: Int, val claims: Int, val stories: Int) {
        fun report(accepted: Boolean, reason: String?, blocked: Map<String, String> = emptyMap()) =
            GateReport(accepted, reason, facts, sources, claims, stories, blocked)
    }
}
