package com.kitt.reader

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** An upgrade from code18 arrives with no interest keys at all; that must read as a balanced default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryInterestStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun file(name: String) = File(File(app.applicationInfo.dataDir, "shared_prefs"), "$name.xml")
    private fun rawSettings() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Test fun legacyInstallWithoutAnyInterestKeysReadsEveryCategoryAsNormal() {
        assertTrue(app.getSharedPreferences(StoryInterestStore.FILE, Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(StoryCategory.entries.associateWith { StoryInterest.NORMAL }, StoryInterestStore(app).read())
    }
    @Test fun corruptForeignOrMissingStoredValuesFailClosedToNormalWithoutCrashing() {
        val prefs = app.getSharedPreferences(StoryInterestStore.FILE, Context.MODE_PRIVATE)
        prefs.edit().putString("HISTORY", "OFTEN").putString("TRAVEL", "").putString("NATURE", "多讲")
            .putString("an_unknown_key", "MORE").putInt("CULTURE", 7).commit()
        assertEquals(StoryCategory.entries.associateWith { StoryInterest.NORMAL }, StoryInterestStore(app).read())
        assertEquals(5, prefs.all.size)
    }
    @Test fun saveWritesAllSixKeysAndSurvivesARestart() {
        val store = StoryInterestStore(app)
        assertTrue(store.save(mapOf(StoryCategory.HISTORY to StoryInterest.MORE,
            StoryCategory.ENGINEERING to StoryInterest.LESS)).isSuccess)
        val expected = StoryCategory.entries.associateWith { category ->
            when (category) {
                StoryCategory.HISTORY -> StoryInterest.MORE
                StoryCategory.ENGINEERING -> StoryInterest.LESS
                else -> StoryInterest.NORMAL
            }
        }
        assertEquals(expected, store.read())
        assertEquals(expected, StoryInterestStore(app).read())
        assertEquals(6, app.getSharedPreferences(StoryInterestStore.FILE, Context.MODE_PRIVATE).all.size)
        assertTrue(file(StoryInterestStore.FILE).readText().contains("HISTORY"))
    }
    @Test fun interestSaveNeverRewritesAccountProviderOrLegacySettingKeys() {
        val settings = SettingsStore(app, object : SecretCipher {
            override fun encrypt(text: String) = "enc:$text"
            override fun decrypt(text: String) = text.removePrefix("enc:")
        })
        assertTrue(settings.save(ProviderConfig(ProviderKind.COMPATIBLE, "https://example.com/v1",
            "user-model", "low", "example-secret"), 1.2f, "voice-a").isSuccess)
        assertTrue(settings.saveResearch(ProviderConfig(ProviderKind.OPENAI, model = "research-model",
            apiKey = "search-secret")).isSuccess)
        settings.saveChatGpt("registration-record")
        val before = rawSettings().all.toMap()
        val beforeBytes = file("settings").readBytes().copyOf()
        assertTrue(StoryInterestStore(app).save(mapOf(StoryCategory.TRAVEL to StoryInterest.MORE)).isSuccess)
        assertEquals(before, rawSettings().all)
        assertArrayEquals(beforeBytes, file("settings").readBytes())
        assertEquals("user-model", settings.read().model); assertEquals("voice-a", settings.voiceName)
        assertEquals(1.2f, settings.speechRate, 0.01f); assertEquals("registration-record", settings.readChatGpt())
        assertEquals("search-secret", settings.readResearch()?.apiKey)
        assertFalse(file("settings").readText().contains("TRAVEL"))
        assertEquals(StoryInterest.MORE, StoryInterestStore(app).read()[StoryCategory.TRAVEL])
    }
    @Test fun anEmptyDraftIsSavedAsBalancedDefaultsRatherThanSilentlyKeepingStaleKeys() {
        val store = StoryInterestStore(app)
        assertTrue(store.save(mapOf(StoryCategory.HISTORY to StoryInterest.LESS)).isSuccess)
        assertTrue(store.save(emptyMap()).isSuccess)
        assertEquals(StoryCategory.entries.associateWith { StoryInterest.NORMAL }, store.read())
    }
}
