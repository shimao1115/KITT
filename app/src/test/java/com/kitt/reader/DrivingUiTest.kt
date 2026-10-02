package com.kitt.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class DrivingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun drivingButtonsAreVisibleAndOneTapQuietAndEndWork() {
        val revision = mutableIntStateOf(0)
        val journey = Journey({ 1000000 }, TestVoice()) { revision.intValue++ }; var summary: TripSummary? = null
        compose.setContent { revision.intValue; MaterialTheme { DrivingScreen(journey, "模拟", journey::start, { journey.beginListening {} }, { summary = journey.end() }, {}, {}) } }
        compose.onNodeWithText("开始读山河").assertIsDisplayed().performClick()
        compose.onNodeWithText("说点什么").assertIsDisplayed()
        compose.onNodeWithText("跳过").assertIsDisplayed()
        compose.onNodeWithText("安静一会儿").assertIsDisplayed().performClick()
        compose.onNodeWithText("结束安静").assertIsDisplayed().performClick()
        compose.onNodeWithText("说点什么").performClick()
        compose.runOnIdle { assertEquals(JourneyState.LISTENING, journey.state) }
        compose.onNodeWithText("跳过").performClick()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("结束旅程").assertIsDisplayed().performClick()
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(summary); assertFalse(journey.running) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class LandscapeUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun landscapeControlsRemainVisibleWithoutScrolling() {
        val journey = Journey({ 1000000 }, TestVoice()); journey.start()
        compose.setContent { MaterialTheme { DrivingScreen(journey, "粗粒度模拟 · 非导航级", {}, {}, {}, {}, {}) } }
        listOf("说点什么", "安静一会儿", "跳过", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class EndUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun endScreenSavesRatingAndReturns() {
        val app = RuntimeEnvironment.getApplication(); val store = TripStore(app)
        val summary = TripSummary(1234, 61234, "绵阳", listOf("道路与生活"), 1); assertTrue(store.finish(summary))
        var returned = false
        compose.setContent { MaterialTheme { EndScreen(summary, store) { returned = true } } }
        compose.onNodeWithText("这一程，读过的山河").assertIsDisplayed()
        compose.onNodeWithText("保存评分").performScrollTo().performClick()
        compose.onNodeWithText("已保存在本机").assertIsDisplayed()
        compose.runOnIdle { assertTrue(File(app.filesDir, "trips/trip-1234.json").readText().contains("ratings")) }
        compose.onNodeWithText("回到出发页").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(returned) }
    }
}
