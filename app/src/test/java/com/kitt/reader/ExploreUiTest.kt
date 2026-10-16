package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ExploreUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val voice = TestVoice()
    private val playback = ExplorePlayback(voice) { true }
    private fun fixture(fontScale: Float = 1f) {
        val catalog = ExploreFixture.catalog()
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                KittTheme { Surface(Modifier.fillMaxSize()) { ExploreScreen(catalog, playback) {} } }
            }
        } }
    }
    private fun county() { compose.onNodeWithText("新都区", useUnmergedTree = true).performClick() }
    private fun person() {
        county(); compose.onNodeWithTag("explore-search").performTextInput("杨慎")
        compose.onNodeWithText("杨慎（杨升庵）").performClick()
    }
    private fun article() {
        person()
        val title = ExploreFixture.stories().stories.first().title
        compose.onNodeWithTag("explore-content").performScrollToNode(hasText("主篇 · $title"))
        compose.onNodeWithText("主篇 · $title").performClick()
    }
    @Test fun standardActivityBrowsesWithoutPermissionsLoginJourneyOrPersistentWrites() {
        val app = (RuntimeEnvironment.getApplication() as KittApp); val runtime = app.runtime
        val tripDir = File(app.filesDir, "trips").apply { mkdirs() }
        (1..20).forEach { File(tripDir, "trip-$it.json").writeText("{\"started\":$it,\"ended\":100,\"destination\":\"旧旅程\",\"topics\":[],\"skips\":0,\"ratings\":{\"旧评分\":5},\"feedback\":\"保留\"}") }
        fun snapshot() = (tripDir.listFiles().orEmpty().toList() + File(app.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty())
            .filter { it.isFile }.associate { it.absolutePath to OfflinePack.sha256(it.readBytes()) }
        val before = snapshot(); val count = runtime.loop.counters.summary(); val fix = runtime.journey.fix
        compose.onNodeWithText("开始沿途").assertIsDisplayed()
        compose.onNodeWithText("探索地点").assertIsDisplayed().performClick(); county()
        compose.onNodeWithTag("explore-search").performTextInput("杨慎")
        compose.onNodeWithText("杨慎（杨升庵）").performClick()
        compose.onNodeWithText("基础事实 · 未归类 · 4 条\n来源检索日期 ${runtime.offlinePack!!.retrievedAt}；本次未联网查询。").assertIsDisplayed()
        compose.onNodeWithTag("explore-play").assertDoesNotExist()
        repeat(3) { compose.onNodeWithTag("explore-back").performClick() }
        compose.onNodeWithText("开始沿途").assertIsDisplayed()
        compose.onNodeWithText("设置").performClick()
        compose.runOnIdle {
            assertFalse(runtime.journey.running); assertEquals(fix, runtime.journey.fix)
            assertTrue(runtime.journey.narrationMemory.isEmpty()); assertEquals(count, runtime.loop.counters.summary())
            assertEquals(before, snapshot()); assertEquals(20, tripDir.listFiles()!!.size)
        }
    }
    @Test fun emptyResultsClearSearchAndBackKeepOriginalEntry() {
        fixture(); county()
        compose.onNodeWithTag("explore-search").performTextInput("没有这个地区")
        compose.onNodeWithText("没有匹配内容。可换个关键词；未安装地区暂不可浏览。").assertIsDisplayed()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("杨慎（杨升庵）").assertIsDisplayed()
        compose.onNodeWithTag("explore-back").performClick()
        compose.onNodeWithText("已安装地区").assertIsDisplayed()
    }
    @Test fun longArticleManualPlayStopErrorAndReturnNeverAutoPlayFollowup() {
        fixture(); article()
        val text = ExploreFixture.stories().stories.first().text
        compose.onNodeWithTag("explore-story-text", useUnmergedTree = true).assertTextEquals(text)
        assertTrue(voice.speech.isEmpty())
        compose.onNodeWithTag("explore-play").assertIsDisplayed().performClick()
        compose.onNodeWithText("停止朗读").assertIsDisplayed()
        compose.runOnIdle { assertEquals(text, voice.speech.single()) }
        compose.onNodeWithTag("explore-play").performClick()
        compose.runOnIdle { voice.done!!(false); assertEquals(ExploreSpeech.IDLE, playback.state) }
        compose.onNodeWithTag("explore-play").performClick()
        compose.runOnIdle { voice.done!!(false) }
        compose.onNodeWithTag("explore-voice-message").assertIsDisplayed()
        compose.onNodeWithTag("explore-back").performClick()
        compose.runOnIdle { assertEquals(ExploreSpeech.IDLE, playback.state); assertEquals(2, voice.speech.size) }
    }
    private fun layout(fontScale: Float = 1f) {
        fixture(fontScale); article()
        compose.onNodeWithTag("explore-play").assertIsDisplayed()
        compose.onNodeWithTag("explore-back").assertIsDisplayed()
        compose.onNodeWithTag("explore-content").performScrollToNode(hasText("故事来源 · 本次未联网查询"))
        compose.onNodeWithText("故事来源 · 本次未联网查询").assertIsDisplayed()
        compose.onNodeWithTag("explore-play").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w914dp-h411dp-land") fun phoneLandscapeReadingAndControlsFit() = layout()
    @Test @Config(qualifiers = "w1280dp-h800dp-land") fun tabletLandscapeReadingAndControlsFit() = layout()
    @Test @Config(qualifiers = "w320dp-h568dp") fun smallPhoneLargeTextCanScrollWithVisiblePlayback() = layout(1.5f)
    @Test fun unavailableCatalogHasTruthfulEmptyState() {
        compose.runOnUiThread { compose.activity.setContent { KittTheme {
            ExploreScreen(ExploreCatalog(emptyList()), playback) {}
        } } }
        compose.onNodeWithText("没有可读取的已安装资料，文件可能缺失或校验失败。请检查应用资料。").assertIsDisplayed()
        compose.onNodeWithTag("explore-play").assertDoesNotExist()
    }
    @Test fun backgroundingStopsManualSpeechAndLateCallbacksCannotRestartIt() {
        fixture(); article(); compose.onNodeWithTag("explore-play").performClick()
        val callback = voice.done!!
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(ExploreSpeech.IDLE, playback.state); callback(false)
        assertEquals(ExploreSpeech.IDLE, playback.state)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("播放完整故事").assertIsDisplayed()
    }
    private fun homeEntry() {
        val journey = Journey({ 1000 }, TestVoice())
        var entered = false
        compose.runOnUiThread { compose.activity.setContent { KittTheme {
            DrivingScreen(journey, "未定位", {}, {}, {}, {}, {}, onExplore = { entered = true })
        } } }
        compose.onNodeWithText("开始沿途").assertIsDisplayed()
        compose.onNodeWithText("探索地点").assertIsDisplayed().performClick()
        assertTrue(entered)
    }
    @Test @Config(qualifiers = "w914dp-h411dp-land") fun phoneLandscapeHasDiscoverableHomeEntry() = homeEntry()
    @Test @Config(qualifiers = "w1280dp-h800dp-land") fun tabletHasDiscoverableHomeEntry() = homeEntry()
    @Test fun runningJourneyDoesNotOfferExplorationOrLoseItsState() {
        val journey = Journey({ 1000 }, TestVoice()).apply { start() }
        compose.runOnUiThread { compose.activity.setContent { KittTheme {
            DrivingScreen(journey, "等待可靠位置", {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("探索地点").assertDoesNotExist()
        compose.onNodeWithText("结束旅程").assertIsDisplayed()
        assertTrue(journey.running)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ExploreRestoreUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun restoredArticleRetainsTextAndNavigationButStopsPlayback() {
        val voice = TestVoice(); val playback = ExplorePlayback(voice) { true }
        val restore = StateRestorationTester(compose); val catalog = ExploreFixture.catalog()
        // Use the real declared host in both variants. ComponentActivity is only declared by
        // Debug's ui-test-manifest; remove the initial content before the rule owns restoration.
        compose.runOnUiThread {
            compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews()
        }
        restore.setContent { KittTheme { Surface(Modifier.fillMaxSize()) { ExploreScreen(catalog, playback) {} } } }
        compose.onNodeWithText("新都区", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("explore-search").performTextInput("杨慎")
        compose.onNodeWithText("杨慎（杨升庵）").performClick()
        val title = catalog.regions.single().stories.first().title
        compose.onNodeWithTag("explore-content").performScrollToNode(hasText("主篇 · $title"))
        compose.onNodeWithText("主篇 · $title").performClick()
        compose.onNodeWithTag("explore-play").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("explore-story-text", useUnmergedTree = true).assertTextEquals(catalog.regions.single().stories.first().text)
        compose.onNodeWithText("播放完整故事").assertIsDisplayed()
        compose.onNodeWithTag("explore-back").performClick()
        compose.onNodeWithTag("explore-back").performClick()
        compose.onNodeWithTag("explore-search").assertTextContains("杨慎")
        compose.runOnIdle { assertEquals(ExploreSpeech.IDLE, playback.state) }
    }
}
