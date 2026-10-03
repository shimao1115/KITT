package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-night")
@LooperMode(LooperMode.Mode.PAUSED)
class KittPolishPortraitTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun compactDarkPortraitShowsEveryStatePlaceAndSecondaryControlsWithoutScrolling() {
        val revision = mutableIntStateOf(0); val voice = TestVoice()
        val journey = Journey({ 1000000 }, voice) { revision.intValue++ }
        var imageTaps = 0; var routeTaps = 0
        compose.runOnUiThread { compose.activity.setContent { revision.intValue; KittTheme { Surface(Modifier.fillMaxSize()) {
            DrivingScreen(journey, "新都→安州雎水 · 粗粒度模拟 / 100 km/h / 16×（非导航级）", journey::start,
                { journey.beginListening {} }, { journey.end() }, {}, {}, VoiceDetail(VoicePhase.LISTENING, 0.5f),
                onRouteImage = { routeTaps++ }, onVisualTalk = { imageTaps++ })
        } } } }
        compose.onNodeWithText("添加路线参考图（可选）").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, routeTaps) }
        compose.onNodeWithText("开始读山河").assertIsDisplayed().performClick()
        compose.runOnUiThread { journey.location(Fix(31.0, 104.0, 1000000, administrative = AreaIdentity("德阳市", "广汉市", "雒城街道"))) }
        compose.onNodeWithText("广汉市 · 雒城街道").assertIsDisplayed()
        compose.onNodeWithTag("journey-state-READING").assertIsDisplayed()
        compose.onNodeWithText("旅途看图").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, imageTaps) }
        compose.runOnUiThread { journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "三星堆与古蜀文化", "完整讲述").json()) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("speaking-indicator").assertIsDisplayed()
        compose.onNodeWithText("说点什么").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("listening-indicator").assertIsDisplayed()
        compose.onNodeWithText("安静一会儿").assertIsDisplayed().performClick()
        compose.onNodeWithTag("journey-state-QUIET").assertIsDisplayed()
        compose.onNodeWithText("剩余 10:00").assertIsDisplayed()
        compose.onNodeWithText("结束安静").assertIsDisplayed()
        compose.onNodeWithText("结束旅程").assertIsDisplayed().performClick()
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class KittPolishLandscapeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun daylightLandscapeKeepsPlaceQuietAndImageControlsVisibleWithoutScrolling() {
        val revision = mutableIntStateOf(0)
        val journey = Journey({ 1000000 }, TestVoice()) { revision.intValue++ }; journey.start()
        journey.location(Fix(31.501, 104.242, 1000000, administrative = AreaIdentity("绵阳市", "安州区", "雎水镇")))
        compose.runOnUiThread { compose.activity.setContent { revision.intValue; KittTheme { Surface(Modifier.fillMaxSize()) {
            DrivingScreen(journey, "新都→安州雎水 · 粗粒度模拟", {}, {}, { journey.end() }, {}, {})
        } } } }
        compose.onNodeWithText("安州区 · 雎水镇").assertIsDisplayed()
        listOf("旅途看图", "说点什么", "跳过", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("安静一会儿").performClick()
        compose.onNodeWithTag("journey-state-QUIET").assertIsDisplayed()
        compose.onNodeWithText("剩余 10:00").assertIsDisplayed()
        compose.onNodeWithText("结束安静").assertIsDisplayed().performClick()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }
}
