package com.kitt.reader

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Exercises the real asset registration used by KittRuntime and ExploreCatalog, with no phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BundledCountyPacksTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun actualApkRegistryHasOnlyXinduAndNoSyntheticCounty() {
        val catalog = BundledCountyPacks.load(app)
        assertEquals(setOf("cn-510114"), catalog.counties.keys)
        assertEquals(mapOf("cn-510100" to listOf("cn-510114")), catalog.cities)
        val p = catalog.county("cn-510114")!!
        assertEquals(CountyProvenance.BUNDLED_APK, p.provenance)
        assertEquals(6, p.entities.size); assertEquals(22, p.factCount); assertEquals(6, p.factsView.sources.size)
        assertEquals(17, p.sources.size); assertEquals(16, p.claims.size); assertEquals("2026.10.15-review3", p.factVersion)
        assertTrue(p.stories.stories.isEmpty()); assertTrue(p.runtimeStories.stories.isEmpty())
        assertNull(catalog.county("cn-420502")); assertEquals(1, ExploreCatalog.load(app).regions.size)
        assertTrue(ExploreCatalog.load(app).regions.single().storyStatus.contains("仅基础事实"))
    }
    @Test fun registeredAdapterRunsOldAutomaticLoopLocallyWithNoAccountOrNetwork() = runTest {
        val p = BundledCountyPacks.load(app).county("cn-510114")!!
        val now = 1000000L; val voice = TestVoice(); val j = Journey({ now }, voice)
        var calls = 0
        val loop = DirectorLoop(j, ContextPipeline(), this, { now }, { calls++; error("network forbidden") },
            researchProvider = { calls++; error("research forbidden") }, online = { false },
            offlinePack = { p.factsView }, preferOffline = { true }, storyPacks = { p.runtimeStories })
        j.start(); loop.location(Fix(30.83, 104.16, now, accuracy = 10.0, source = FixSource.GPS)); runCurrent()
        assertEquals(1, voice.speech.size); assertTrue(voice.speech.single().contains("杨慎"))
        voice.finish(); loop.user("再说一遍"); assertEquals(voice.speech.first(), voice.speech.last())
        voice.finish(); assertEquals(0, calls); j.end(); loop.reset()
    }
    @Test fun readOnlyRegistryAndExploreNeverWriteSyntheticSettingsOrTwentyHistories() {
        val settings = SettingsStore(app)
        val config = ProviderConfig(ProviderKind.FAKE, model = "TEST_ONLY-offline")
        assertTrue(settings.save(config, 1.1f, "TEST_ONLY-voice").isSuccess)
        StoryInterestStore(app).save(mapOf(StoryCategory.TRAVEL to StoryInterest.LESS))
        val folder = File(app.filesDir, "TEST_ONLY-histories").apply { mkdirs() }
        repeat(20) { File(folder, "$it.json").writeText("{\"TEST_ONLY\":$it,\"ratings\":{\"content\":5},\"feedback\":\"synthetic\"}") }
        fun files() = File(app.applicationInfo.dataDir).walkTopDown().filter { it.isFile && (it.extension == "json" || it.extension == "xml") }
            .associate { it.absolutePath to it.readBytes().toList() }
        val before = files()
        repeat(2) { BundledCountyPacks.load(app); ExploreCatalog.load(app).regions.single().search("杨慎") }
        assertEquals(before, files()); assertEquals(20, folder.listFiles()!!.size)
        assertEquals("TEST_ONLY-offline", settings.read().model)
        assertEquals(StoryInterest.LESS, StoryInterestStore(app).read()[StoryCategory.TRAVEL])
    }
}
