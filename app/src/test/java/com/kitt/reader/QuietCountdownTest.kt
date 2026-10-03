package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class QuietCountdownTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** The countdown is a plain string; existence in the semantics tree is what must change over time. */
    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** With autoAdvance off nothing paints itself, so elapse virtual time and then take exactly one frame. */
    private fun frame(ms: Long = 0) { if (ms > 0) compose.mainClock.advanceTimeBy(ms); compose.mainClock.advanceTimeByFrame() }

    private fun screen(journey: Journey) {
        val revision = mutableStateOf(0)
        compose.runOnUiThread { compose.activity.setContent {
            revision.value
            MaterialTheme { DrivingScreen(journey, "手机 GPS", {}, {}, {}, {}, {}) }
        } }
    }

    /**
     * `quietUntil` is an absolute deadline, so a second of real time changes no observable value at all.
     * Without a clock read from composition the countdown stops at whatever it first painted.
     */
    @Test fun quietCountdownKeepsCountingWhenNothingElseChanges() {
        compose.mainClock.autoAdvance = false
        var now by mutableIntStateOf(1_000_000)
        val journey = Journey({ now.toLong() }, TestVoice()); journey.start(); journey.quiet(600_000)
        screen(journey); frame()
        assertTrue(shows("剩余 10:00"))
        now += 5_000; frame(5_000)
        assertTrue(shows("剩余 9:55"))
        now += 95_000; frame(95_000)
        assertTrue(shows("剩余 8:20"))
    }

    @Test fun countdownLeavesQuietModeTheSecondItRunsOut() {
        compose.mainClock.autoAdvance = false
        var now by mutableIntStateOf(1_000_000)
        val journey = Journey({ now.toLong() }, TestVoice()); journey.start(); journey.quiet(600_000)
        screen(journey); frame()
        assertTrue(shows("剩余 10:00"))
        assertTrue(shows("结束安静"))
        now += 601_000; frame(1_000)
        assertFalse(shows("剩余 0:00"))
        assertTrue(shows("说点什么"))
        assertEquals(JourneyState.READING, journey.state)
    }

    @Test fun indefiniteQuietNeverShowsAFakeNumber() {
        compose.mainClock.autoAdvance = false
        val journey = Journey({ 1_000_000 }, TestVoice()); journey.start(); journey.quiet(Long.MAX_VALUE)
        screen(journey); frame()
        assertTrue(shows("等你叫我"))
    }
}
