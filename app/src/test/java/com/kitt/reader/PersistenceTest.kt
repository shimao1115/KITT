package com.kitt.reader

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceTest {
    @Test fun abnormalReopenRestoresOnlyLightweightState() {
        val context = RuntimeEnvironment.getApplication(); var time = 1000000L
        val journey = Journey({ time }, TestVoice()); journey.start(); journey.requestInput("去绵阳")
        journey.requestInput("今天多讲工程"); journey.quiet(); val store = TripStore(context)
        store.checkpoint(journey, true, 16.0, 25000)
        val saved = TripStore(context).recovery()!!
        assertEquals("去绵阳", saved.destination); assertEquals(25000L, saved.travelMs)
        val restored = Journey({ time }, TestVoice())
        restored.restore(saved.started, saved.destination, saved.instructions, saved.topics, saved.quietUntil)
        assertEquals(JourneyState.QUIET, restored.state); assertNull(restored.prepared); assertNull(restored.fix)
        assertFalse(restored.listening); assertFalse(restored.speaking)
        val raw = File(context.filesDir, "trips/current.json").readText()
        assertFalse(raw.contains("latitude") || raw.contains("longitude") || raw.contains("narration"))
        time += 900000; restored.tick(); assertEquals(JourneyState.READING, restored.state)
        assertTrue(store.finish(restored.end())); assertNull(store.recovery())
    }
    @Test fun summaryRatingRetentionAndCorruptRecovery() {
        val context = RuntimeEnvironment.getApplication(); val store = TripStore(context)
        val summary = TripSummary(100, 200, "绵阳", listOf("道路"), 1)
        assertTrue(store.finish(summary)); assertEquals(summary, TripStore(context).latest())
        assertTrue(store.feedback(100, listOf(1, 2, 3, 4, 5), "时机不错"))
        val raw = File(context.filesDir, "trips/trip-100.json").readText()
        assertTrue(raw, raw.contains("时机不错")); assertTrue(raw, raw.contains("ratings"))
        assertFalse(store.feedback(100, listOf(0), "bad"))
        File(context.filesDir, "trips/current.json").writeText("broken")
        assertNull(store.recovery()); assertNotNull(store.lastError)
        store.clearRecovery(); assertFalse(File(context.filesDir, "trips/current.json").exists())
    }
}
