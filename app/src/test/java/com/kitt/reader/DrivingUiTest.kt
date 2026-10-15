package com.kitt.reader

import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun drivingButtonsAreVisibleAndOneTapQuietAndEndWork() {
        val revision = mutableIntStateOf(0)
        val journey = Journey({ 1000000 }, TestVoice()) { revision.intValue++ }; var summary: TripSummary? = null
        compose.runOnUiThread { compose.activity.setContent { revision.intValue; MaterialTheme { DrivingScreen(journey, "模拟", journey::start, { journey.beginListening {} }, { summary = journey.end() }, {}, {}) } } }
        compose.onNodeWithText("开始沿途").assertIsDisplayed().performClick()
        compose.onNodeWithText("说点什么").assertIsDisplayed()
        compose.onNodeWithText("跳过").assertIsDisplayed()
        compose.onNodeWithText("安静一会儿").assertIsDisplayed().performClick()
        compose.onNodeWithText("结束安静").assertIsDisplayed().performClick()
        compose.onNodeWithText("说点什么").performClick()
        compose.runOnIdle { assertEquals(JourneyState.LISTENING, journey.state) }
        compose.onNodeWithText("跳过").performClick()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("结束旅程").assertIsDisplayed().performClick()
        compose.onNodeWithText("开始沿途").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(summary); assertFalse(journey.running) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class LandscapeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun landscapeControlsRemainVisibleWithoutScrolling() {
        val journey = Journey({ 1000000 }, TestVoice()); journey.start()
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme { DrivingScreen(journey, "粗粒度模拟 · 非导航级", {}, {}, {}, {}, {}) } } }
        listOf("说点什么", "安静一会儿", "跳过", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class EndUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun assertNoRatingControls() {
        listOf("内容值得听吗？", "开口时机合适吗？", "长短合适吗？", "讲得清楚/可信吗？", "整体体验怎么样？",
            "想说的话（可选）", "保存评分", "已保存在本机", "评分暂时无法保存。").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        (1..5).forEach { compose.onNodeWithText("$it").assertDoesNotExist() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun endScreenShowsSavedSummaryWithoutRatingsAndReturns() {
        val app = RuntimeEnvironment.getApplication(); val store = TripStore(app)
        val summary = TripSummary(1234, 61234, "绵阳", listOf("道路与生活"), 1); assertTrue(store.finish(summary))
        var returned = false
        val file = File(app.filesDir, "trips/trip-1234.json"); val saved = file.readBytes()
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme { EndScreen(summary) { returned = true } } } }
        compose.onNodeWithText("这一程，读过的山河").assertIsDisplayed()
        compose.onNodeWithText("旅程意图：绵阳").assertIsDisplayed()
        compose.onNodeWithText("时长约 1 分钟 · 跳过 1 次").assertIsDisplayed()
        compose.onNodeWithText("道路与生活").assertIsDisplayed()
        assertNoRatingControls()
        compose.onNodeWithText("回到出发页").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(returned); assertEquals(summary, TripStore(app).latest())
            assertArrayEquals(saved, file.readBytes())
        }
    }
    @Test fun technicalDestinationStatesAreHiddenAndEmptyTripStaysQuiet() {
        listOf("未询问", "未提供", "等待回答", "", "  ").forEach { destination ->
            compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
                EndScreen(TripSummary(1234, 61234, destination, emptyList(), 0)) {}
            } } }
            compose.onAllNodes(hasText("旅程意图：", substring = true)).assertCountEquals(0)
            compose.onNodeWithText("这一程以安静相伴。").assertIsDisplayed()
            compose.onNodeWithText("回到出发页").assertIsDisplayed()
            assertNoRatingControls()
        }
    }
    private fun assertCompactSummaryAndReturn() {
        var returned = false
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            EndScreen(TripSummary(1234, 61234, "去绵阳", listOf("道路与生活", "山河"), 2)) { returned = true }
        } } }
        listOf("这一程，读过的山河", "旅程意图：去绵阳", "时长约 1 分钟 · 跳过 2 次", "道路与生活、山河", "回到出发页")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertNoRatingControls()
        compose.onNodeWithText("回到出发页").performClick()
        compose.runOnIdle { assertTrue(returned) }
    }
    @Test @Config(qualifiers = "w891dp-h411dp-land")
    fun landscapeSummaryAndReturnAreVisibleWithoutScrolling() = assertCompactSummaryAndReturn()
    @Test @Config(qualifiers = "w1280dp-h800dp-land")
    fun tabletSummaryAndReturnAreVisibleWithoutScrolling() = assertCompactSummaryAndReturn()
    @Test fun actualEndButtonAutomaticallySavesBeforeReturningToStart() {
        val app = compose.activity.application as KittApp
        val runtime = app.runtime
        val started = System.currentTimeMillis() - 60000
        compose.runOnUiThread { runtime.journey.restore(started, "去绵阳", "", listOf("道路与生活"), 0) }
        compose.onNodeWithText("结束旅程").assertIsDisplayed().performClick()
        compose.onNodeWithText("这一程，读过的山河").assertIsDisplayed()
        compose.onNodeWithText("道路与生活").assertIsDisplayed()
        assertNoRatingControls()
        lateinit var saved: ByteArray
        val file = File(app.filesDir, "trips/trip-$started.json")
        compose.runOnIdle {
            assertFalse(runtime.journey.running); assertNull(runtime.store.recovery())
            assertEquals(runtime.summary, TripStore(app).latest())
            saved = file.readBytes()
            val raw = file.readText(); assertFalse("ratings" in raw); assertFalse("feedback" in raw)
        }
        compose.onNodeWithText("回到出发页").assertIsDisplayed().performClick()
        compose.onNodeWithText("开始沿途").assertIsDisplayed()
        compose.runOnIdle { assertNull(runtime.summary); assertArrayEquals(saved, file.readBytes()) }
    }
}
