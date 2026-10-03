package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisualTalkTest {
    private val image = ImageInput("image/jpeg", "aW1hZ2U=")
    private fun TestScope.setup(block: suspend (DirectorRequest) -> String): Triple<Journey, DirectorLoop, VisualTalk> {
        val clock = { 1000000L + testScheduler.currentTime }
        val journey = Journey(clock, TestVoice()); journey.start(); journey.location(Fix(30.0, 104.0, clock()))
        val provider = object : DirectorProvider {
            override val acceptsImages = true
            override suspend fun direct(request: DirectorRequest) = block(request)
        }
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { provider })
        return Triple(journey, loop, VisualTalk(journey, loop, backgroundScope) { provider })
    }
    @Test fun imageIsActiveMessageIncludesContextAndNeverLeaksIntoAutoChecks() = runTest {
        val requests = mutableListOf<DirectorRequest>()
        val (journey, loop, visual) = setup { requests += it; DirectorResult(Action.SILENT).json() }
        visual.begin(); assertTrue(journey.imageInteraction); assertFalse(journey.shouldCheck())
        visual.select { image }; runCurrent(); assertTrue(visual.ready)
        visual.send(); runCurrent()
        assertFalse(visual.open); assertFalse(visual.ready); assertFalse(journey.imageInteraction)
        assertEquals("帮我看看这个", requests.single().userUtterance); assertSame(image, requests.single().image)
        assertTrue(requests.single().contextCard.contains("30.0000"))
        advanceTimeBy(60000); journey.location(Fix(30.1, 104.0, 1060000)); loop.check(); runCurrent()
        assertEquals(2, requests.size); assertNull(requests.last().image); assertNull(requests.last().userUtterance)
    }
    @Test fun openingVisualTalkInterruptsVoiceAndCancellationDropsSelectedImage() = runTest {
        val (journey, _, visual) = setup { DirectorResult(Action.SILENT).json() }
        journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "当前", "正在讲述").json())
        assertTrue(journey.speaking); visual.begin(); assertFalse(journey.speaking)
        visual.select { image }; runCurrent(); visual.clear()
        assertFalse(visual.ready); assertFalse(visual.open); assertFalse(journey.imageInteraction)
    }
    @Test fun quietSkipEndAndNewUserInputInvalidatePendingPhoto() = runTest {
        val (journey, loop, visual) = setup { DirectorResult(Action.SILENT).json() }
        for (change in listOf<() -> Unit>({ journey.quiet() }, journey::skip, { loop.user("多讲历史") }, { journey.end() })) {
            if (!journey.running) journey.start()
            visual.begin(); visual.select { delay(5000); image }; runCurrent()
            change(); visual.sync(); advanceTimeBy(6000); runCurrent()
            assertFalse(visual.open); assertFalse(visual.ready); assertFalse(journey.imageInteraction)
        }
    }
    @Test fun latestSelectionWinsAndOptionalTypedQuestionUsesSameDirector() = runTest {
        val requests = mutableListOf<DirectorRequest>()
        val (journey, _, visual) = setup { requests += it; DirectorResult(Action.SILENT).json() }
        visual.begin(); visual.select { delay(10000); ImageInput("image/jpeg", "b2xk") }; runCurrent()
        visual.select { image }; runCurrent(); visual.send("这座桥是什么结构？"); runCurrent(); advanceTimeBy(12000); runCurrent()
        assertEquals(1, requests.size); assertSame(image, requests.single().image)
        assertEquals("这座桥是什么结构？", requests.single().userUtterance); assertFalse(journey.imageInteraction)
    }
    @Test fun unsupportedPhotoIsExplainedWithoutLoadingAndTimeoutDoesNotRetry() = runTest {
        val clock = { 1000000L }; val journey = Journey(clock, TestVoice()); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { FakeProvider() })
        val visual = VisualTalk(journey, loop, backgroundScope) { FakeProvider() }; var loads = 0
        visual.begin(); assertTrue(visual.notice.contains("不支持看图")); visual.select { loads++; image }; runCurrent()
        assertEquals(0, loads); assertFalse(visual.ready); visual.clear()
        val (_, _, supported) = setup { DirectorResult(Action.SILENT).json() }
        supported.begin(); supported.select { loads++; delay(20000); image }; runCurrent(); advanceTimeBy(15001); runCurrent()
        assertTrue(supported.notice.contains("超时")); assertFalse(supported.busy); assertEquals(1, loads)
        supported.clear()
    }
    @Test fun visualRequestFailureSpeaksBrieflyThroughNormalFlow() = runTest {
        val (journey, _, visual) = setup { error("model rejected image") }
        visual.begin(); visual.select { image }; runCurrent(); visual.send(); runCurrent()
        assertTrue(journey.speaking); assertFalse(visual.ready); assertFalse(visual.open)
    }
}
