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
    @Test fun completedSummarySurvivesCheckpointButPlaybackClaimsAndReplayNeverPersist() {
        val context = RuntimeEnvironment.getApplication(); val voice = TestVoice()
        val j = Journey({ 1_000_000 }, voice); j.start()
        val store = TripStore(context)
        j.deliver(j.ticket(true), DirectorResult(Action.SPEAK_NOW, "对象", "私有正文不可持久化", memoryUpdate = "事实角度").json())
        store.checkpoint(j, false, 1.0, 0); assertTrue(store.recovery()!!.topics.isEmpty())
        voice.finish(); store.checkpoint(j, false, 1.0, 0)
        val recovered = store.recovery()!!
        val next = Journey({ 1_000_000 }, TestVoice()); next.restore(recovered.started, recovered.destination, "", recovered.topics, 0)
        store.checkpoint(next, false, 1.0, 0)
        assertEquals(listOf("事实角度"), store.recovery()!!.topics)
        assertTrue(next.recentTopics.isEmpty()); assertTrue(next.narrationMemory.isEmpty()); assertNull(next.replay)
        val raw = File(context.filesDir, "trips/current.json").readText()
        listOf("私有正文", "COMPLETED", "landmarkId", "narrationMemory").forEach { assertFalse(it in raw) }
    }
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
    @Test fun legacyRatingsAndFeedbackRemainReadableUnchangedAndCorruptRecoveryIsSafe() {
        val context = RuntimeEnvironment.getApplication(); val store = TripStore(context)
        val summary = TripSummary(100, 200, "绵阳", listOf("道路"), 1)
        val legacy = File(context.filesDir, "trips/trip-100.json")
        legacy.writeText("""{"version":1,"started":100,"ended":200,"destination":"绵阳","topics":["道路"],"skipped":1,"ratings":[1,2,3,4,5],"feedback":"时机不错"}""")
        val saved = legacy.readBytes()
        repeat(2) { assertEquals(summary, TripStore(context).latest()); assertArrayEquals(saved, legacy.readBytes()) }
        val next = TripSummary(300, 400, "未询问", emptyList(), 0)
        assertTrue(store.finish(next))
        File(context.filesDir, "trips/trip-300.json").setLastModified(System.currentTimeMillis() + 1000)
        assertEquals(next, TripStore(context).latest()); assertArrayEquals(saved, legacy.readBytes())
        File(context.filesDir, "trips/current.json").writeText("broken")
        assertNull(store.recovery()); assertNotNull(store.lastError)
        store.clearRecovery(); assertFalse(File(context.filesDir, "trips/current.json").exists())
    }
    @Test fun finishSavesSummaryAndClearsCheckpointWithoutRatingInput() {
        val context = RuntimeEnvironment.getApplication(); val store = TripStore(context)
        val journey = Journey({ 1000000 }, TestVoice()); journey.start()
        store.checkpoint(journey, false, 1.0, 0); assertNotNull(store.recovery())
        val summary = journey.end()
        assertTrue(store.finish(summary)); assertNull(store.recovery())
        assertEquals(summary, TripStore(context).latest())
        val raw = File(context.filesDir, "trips/trip-${summary.started}.json").readText()
        assertFalse("ratings" in raw); assertFalse("feedback" in raw)
    }
    @Test fun resumedRuntimeKeepsTravelOffsetBeforeItsFirstPeriodicCheckpoint() {
        val app = RuntimeEnvironment.getApplication(); val store = TripStore(app)
        val journey = Journey(System::currentTimeMillis, TestVoice()); journey.start(); journey.requestInput("去绵阳")
        store.checkpoint(journey, true, 16.0, 25000)
        val runtime = KittRuntime(app, TestVoice()); runtime.resumeRequested = true; runtime.start()
        runtime.checkpoint(); assertTrue(store.recovery()!!.travelMs >= 25000)
        runtime.end(); runtime.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        assertNull(store.recovery())
    }
}
