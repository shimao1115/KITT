package com.kitt.reader

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp")
@LooperMode(LooperMode.Mode.PAUSED)
class StoryInterestUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val categories = mapOf(StoryCategory.NATURE to "自然地理生态", StoryCategory.ENGINEERING to "交通与工程",
        StoryCategory.CULTURE to "人文文化社会", StoryCategory.LOCAL_LIFE to "物产产业生活",
        StoryCategory.HISTORY to "历史与人物", StoryCategory.TRAVEL to "旅行现场与常识")
    private val levels = mapOf(StoryInterest.MORE to "多讲", StoryInterest.NORMAL to "正常", StoryInterest.LESS to "少讲")
    private val app get() = RuntimeEnvironment.getApplication() as KittApp

    private fun show(fontScale: Float = 1f, onReturn: () -> Unit = {}) {
        val runtime = app.runtime
        compose.runOnUiThread { compose.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                KittTheme { Surface(Modifier.fillMaxSize()) { SettingsScreen(runtime, true, {}, onReturn) } }
            }
        } }
    }
    private fun tag(category: StoryCategory, interest: StoryInterest) = "interest-${category}-$interest"
    private fun pick(category: StoryCategory, interest: StoryInterest) {
        compose.onNodeWithTag(tag(category, interest)).performScrollTo().performClick()
        compose.runOnIdle { }
    }
    private fun save() {
        compose.onNodeWithText("保存讲述兴趣").performScrollTo().performClick()
        compose.runOnIdle { }
    }
    private fun histories() = File(app.filesDir, "trips").apply { mkdirs() }.let { dir ->
        (1..20).forEach { File(dir, "trip-$it.json").writeText(
            "{\"started\":$it,\"ended\":100,\"destination\":\"旧旅程\",\"topics\":[],\"skips\":0,\"ratings\":{\"旧评分\":5},\"feedback\":\"保留\"}") }
        dir
    }
    private fun snapshot() = (histories().listFiles().orEmpty().toList() +
        File(app.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty())
        .filter { it.isFile }.associate { it.absolutePath to OfflinePack.sha256(it.readBytes()) }

    @Test fun sixCategoriesDefaultToNormalWithTruthfulStoryCountNotice() {
        val runtime = app.runtime
        show()
        assertEquals(StoryCategory.entries.associateWith { StoryInterest.NORMAL }, runtime.storyInterest)
        compose.onNodeWithText("讲述兴趣").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("当前没有已通过审核门禁的完整故事", substring = true).performScrollTo().assertIsDisplayed()
        categories.forEach { (category, label) ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            levels.keys.forEach { interest -> compose.onNodeWithTag(tag(category, interest)).performScrollTo().assertIsDisplayed() }
        }
        levels.values.forEach { text -> compose.onAllNodesWithText(text).assertCountEquals(6) }
        val tags = StoryCategory.entries.flatMap { category -> StoryInterest.entries.map { tag(category, it) } }
        assertEquals(18, tags.size)
        tags.forEach { compose.onAllNodes(hasTestTag(it)).assertCountEquals(1) }
    }
    @Test fun choosingTwoCategoriesSavesOnlyInterestsAndSurvivesARestart() {
        val runtime = app.runtime
        val settings = SettingsStore(app, object : SecretCipher {
            override fun encrypt(text: String) = "enc:$text"
            override fun decrypt(text: String) = text.removePrefix("enc:")
        })
        assertTrue(settings.save(ProviderConfig(ProviderKind.COMPATIBLE, "https://example.com/v1",
            "user-model", "low", "example-secret"), 1.1f, "voice-a").isSuccess)
        settings.saveChatGpt("registration-record")
        val before = snapshot()
        show()
        pick(StoryCategory.HISTORY, StoryInterest.MORE); pick(StoryCategory.ENGINEERING, StoryInterest.LESS)
        save()
        compose.onNodeWithText("已保存讲述兴趣").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(StoryInterest.MORE, runtime.storyInterest[StoryCategory.HISTORY])
            assertEquals(StoryInterest.LESS, runtime.storyInterest[StoryCategory.ENGINEERING])
            assertEquals(4, runtime.storyInterest.values.count { it == StoryInterest.NORMAL })
            assertEquals(StoryInterest.MORE, StoryInterestStore(app).read()[StoryCategory.HISTORY])
            assertEquals("user-model", settings.read().model); assertEquals("voice-a", settings.voiceName)
            assertEquals(1.1f, settings.speechRate, 0.01f); assertEquals("registration-record", settings.readChatGpt())
            assertFalse(runtime.journey.running); assertNull(runtime.journey.fix)
            assertTrue(runtime.loop.counters.summary().contains("opportunities=0"))
        }
        val after = snapshot().filterKeys { !it.endsWith("story_interests.xml") }
        assertEquals(before, after)
        // Reopening the settings page keeps both choices; a plain restart reads them from disk.
        show(); save()
        compose.runOnIdle {
            assertEquals(StoryInterest.MORE, StoryInterestStore(app).read()[StoryCategory.HISTORY])
            assertEquals(StoryInterest.LESS, StoryInterestStore(app).read()[StoryCategory.ENGINEERING])
        }
        assertEquals(20, histories().listFiles()!!.size)
    }
    @Test fun leavingWithoutSavingNeverWritesThePreferenceFile() {
        show()
        pick(StoryCategory.CULTURE, StoryInterest.MORE)
        var returned = false
        show(onReturn = { returned = true })
        compose.onNodeWithTag("interest-CULTURE-NORMAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("返回").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(returned)
            assertEquals(StoryInterest.NORMAL, app.runtime.storyInterest[StoryCategory.CULTURE])
            assertTrue(app.getSharedPreferences(StoryInterestStore.FILE, Context.MODE_PRIVATE).all.isEmpty())
        }
    }
    @Test fun oneCategoryChangeNeverRewritesTheOtherFive() {
        val runtime = app.runtime
        assertTrue(runtime.saveStoryInterest(mapOf(StoryCategory.TRAVEL to StoryInterest.MORE,
            StoryCategory.LOCAL_LIFE to StoryInterest.LESS)).isSuccess)
        show(); pick(StoryCategory.NATURE, StoryInterest.LESS); save()
        compose.runOnIdle {
            assertEquals(StoryInterest.MORE, runtime.storyInterest[StoryCategory.TRAVEL])
            assertEquals(StoryInterest.LESS, runtime.storyInterest[StoryCategory.LOCAL_LIFE])
            assertEquals(StoryInterest.LESS, runtime.storyInterest[StoryCategory.NATURE])
            assertEquals(3, runtime.storyInterest.values.count { it == StoryInterest.NORMAL })
        }
    }
    private fun layout(fontScale: Float = 1f) {
        show(fontScale)
        categories.forEach { (category, label) ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            levels.keys.forEach { interest ->
                compose.onNodeWithTag(tag(category, interest)).performScrollTo().performClick().assertIsDisplayed()
            }
            pick(category, StoryInterest.NORMAL)
        }
        save()
        compose.onNodeWithText("已保存讲述兴趣").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(StoryCategory.entries.associateWith { StoryInterest.NORMAL }, app.runtime.storyInterest) }
    }
    @Test @Config(qualifiers = "w914dp-h411dp-land") fun phoneLandscapeCanReachEveryCategoryAndSave() = layout()
    @Test @Config(qualifiers = "w1280dp-h800dp-land") fun tabletLandscapeCanReachEveryCategoryAndSave() = layout()
    @Test @Config(qualifiers = "w320dp-h568dp") fun smallPhoneLargeTextCanScrollAndSave() = layout(1.5f)
}
