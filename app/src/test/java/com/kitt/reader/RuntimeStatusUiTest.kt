package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

private val sampleStatus = DrivingRuntimeSnapshot("NETWORK · ±300m · 12秒 · 粗略",
    "Wi-Fi · 系统联网已验证 · VPN已检测", "AI 最近失败 12 秒前 / 研究 未验证",
    "正在搜索本地概况 · 已等待 38 秒", "地区名称未解析；物理定位独立运行")

private fun SemanticsNodeInteraction.fitsText() {
    val results = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    // Windows Robolectric legacy glyph advances don't match paragraph constraint widths.
    // Check line/height clipping here; real width and legibility are verified in phone screenshots.
    assertTrue(results.isNotEmpty())
    assertFalse(results[0].didOverflowHeight)
    assertFalse(results[0].multiParagraph.didExceedMaxLines)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeStatusPortraitUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun statusUpdatesWithoutMovingControlsAndNoCoordinatesOrFalseGreen() {
        val status = mutableStateOf(sampleStatus)
        val j = Journey({ 1_000_000 }, TestVoice()).also { it.start(); it.location(Fix(30.1234, 104.1234, 1_000_000)) }
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            DrivingScreen(j, "手机定位", {}, {}, {}, {}, {}, runtimeStatus = { status.value })
        } } }
        listOf("runtime-position", "runtime-network", "runtime-services", "runtime-activity").forEach {
            compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed().fitsText()
        }
        val before = compose.onNodeWithText("说点什么").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread { status.value = sampleStatus.copy(primary = "上次请求失败，等待下次触发", hint = "") }
        compose.onNodeWithText("上次请求失败，等待下次触发").assertIsDisplayed()
        val after = compose.onNodeWithText("说点什么").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onAllNodesWithText("30.123", substring = true).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
        listOf("说点什么", "跳过", "安静一会儿", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
    @Test fun compactPortraitReplyKeepsInputCancelAndDrivingControlsVisible() {
        val j = Journey({ 1_000_000 }, TestVoice()).also { it.start(); it.beginListening {} }
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            DrivingScreen(j, "手机定位", {}, {}, {}, {}, {}, transcript = TranscriptText("我想听工程"),
                runtimeStatus = { sampleStatus.copy(primary = "正在听，请说话") })
        } } }
        listOf("runtime-position", "runtime-network", "runtime-services", "typed-reply", "cancel-reply", "spoken-transcript").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        listOf("说点什么", "跳过", "安静一会儿", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeStatusLandscapeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun phoneLandscapeStatusAndReplyKeepControlsVisible() {
        val j = Journey({ 1_000_000 }, TestVoice()).also { it.start() }
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            DrivingScreen(j, "手机定位", {}, { j.beginListening {} }, {}, {}, {},
                transcript = TranscriptText("我想听当地工程"), runtimeStatus = { sampleStatus })
        } } }
        listOf("runtime-position", "runtime-network", "runtime-services", "runtime-activity").forEach {
            compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed().fitsText()
        }
        compose.onNodeWithText("说点什么").performClick()
        listOf("typed-reply", "cancel-reply", "spoken-transcript").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        listOf("说点什么", "跳过", "安静一会儿", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeStatusTabletUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun tabletSixteenToTenStatusAndControlsFit() {
        val j = Journey({ 1_000_000 }, TestVoice()).also { it.start() }
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            DrivingScreen(j, "手机定位", {}, {}, {}, {}, {}, runtimeStatus = { sampleStatus })
        } } }
        listOf("runtime-position", "runtime-network", "runtime-services", "runtime-activity").forEach {
            compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed().fitsText()
        }
        listOf("说点什么", "跳过", "安静一会儿", "结束旅程").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
    }
}
