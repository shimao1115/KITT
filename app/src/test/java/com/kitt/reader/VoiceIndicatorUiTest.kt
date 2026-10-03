package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VoiceIndicatorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realJourneyTransitionsReplaceAndRemoveIndicatorsWithoutMovingControls() {
        val revision = mutableStateOf(0)
        val voice = TestVoice(); val journey = Journey({ 1000000 }, voice) { revision.value++ }
        compose.runOnUiThread { compose.activity.setContent {
            revision.value
            MaterialTheme { DrivingScreen(journey, "手机 GPS", journey::start, { journey.beginListening {} },
                { journey.end() }, {}, {}, VoiceDetail(VoicePhase.LISTENING, 0.6f)) }
        } }
        compose.onNodeWithText("开始读山河").performClick()
        compose.runOnUiThread { journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "主题", "讲述").json()) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("speaking-indicator").assertIsDisplayed()
        compose.onNodeWithTag("listening-indicator").assertDoesNotExist()
        compose.onNodeWithText("说点什么").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("speaking-indicator").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("listening-indicator").assertIsDisplayed()
        compose.onNodeWithText("正在听，请说话").assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("安静一会儿").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("listening-indicator").assertDoesNotExist()
        compose.onNodeWithTag("speaking-indicator").assertDoesNotExist()
        compose.onNodeWithText("结束安静").assertIsDisplayed()
        compose.onNodeWithText("结束旅程").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("开始读山河").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class VoiceIndicatorLandscapeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun listeningAndSpeakingStayVisibleWithAllLandscapeControls() {
        val revision = mutableStateOf(0); val voice = TestVoice()
        val journey = Journey({ 1000000 }, voice) { revision.value++ }; journey.start()
        compose.runOnUiThread { compose.activity.setContent {
            revision.value
            MaterialTheme { DrivingScreen(journey, "手机 GPS", {}, { journey.beginListening {} }, {}, {}, {},
                VoiceDetail(VoicePhase.LISTENING, 0.8f)) }
        } }
        compose.onNodeWithText("说点什么").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("listening-indicator").assertIsDisplayed()
        compose.runOnUiThread { journey.skip(); journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "主题", "讲述").json()) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("speaking-indicator").assertIsDisplayed()
        compose.onNodeWithTag("listening-indicator").assertDoesNotExist()
        listOf("说点什么", "跳过", "安静一会儿", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("跳过").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("speaking-indicator").assertDoesNotExist()
    }
}
