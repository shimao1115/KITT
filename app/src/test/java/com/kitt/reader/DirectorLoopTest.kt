package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectorLoopTest {
    @Test fun automaticFailureWaitsForNewInformation() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider { calls++; error("offline") } })
        loop.location(Fix(30.67, 104.06, time)); runCurrent()
        repeat(100) { time += 1000; loop.location(Fix(30.67, 104.06, time)); runCurrent() }
        assertEquals(1, calls); assertTrue(voice.speech.isEmpty())
        loop.user("再讲一点"); runCurrent(); assertEquals(2, calls); assertEquals(1, voice.speech.size)
    }
    @Test fun lateAutomaticResponseCannotOverrideUserIntent() = runTest {
        val voice = TestVoice(); val journey = Journey({ 1000000 }, voice); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), this, { 1000000 }, { DirectorProvider {
            delay(5000); DirectorResult(Action.SPEAK_NOW, "旧主题", "不能补播").json()
        } })
        loop.location(Fix(30.67, 104.06, 1000000)); runCurrent(); journey.quiet()
        advanceUntilIdle(); assertTrue(voice.speech.isEmpty()); assertEquals(JourneyState.QUIET, journey.state)
    }
    @Test fun prepareIsOnlyRevalidationContextAndCannotBecomeAudioByItself() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), this, { time }, { DirectorProvider { request ->
            calls++
            if (calls == 1) DirectorResult(Action.PREPARE, "前方目标", prepareHint = "重新确认").json()
            else { assertTrue(request.contextCard.contains("待重新确认")); DirectorResult(Action.SILENT).json() }
        } })
        loop.location(Fix(30.67, 104.06, time)); runCurrent(); time += 60000
        loop.location(Fix(30.684, 104.061, time)); runCurrent()
        assertEquals(2, calls); assertTrue(voice.speech.isEmpty())
    }
}
