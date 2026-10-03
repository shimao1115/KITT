package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationCadenceTest {
    private fun fixture() = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())

    @Test fun reproduceLegacy100Kmh16xDropsEveryFiveSecondAutoResponse() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }
        val voice = TestVoice(); val journey = Journey(clock, voice); journey.start()
        var calls = 0
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            calls++; delay(5000)
            DirectorResult(Action.SPEAK_NOW, "测试主题", "正常延迟的讲述").json()
        } })
        // Deliberately emulate M1.2: wall cadence, no interaction pause.
        // Keep the exact old 110km route for reproducing the historical six stale replies.
        val legacy = RouteFixture.parse(File("src/test/resources/chengdu-mianyang-m1_3.json").readText())
        val source = SimulatedLocationSource(legacy, backgroundScope, clock, 100.0, 16.0)
        source.start(loop::location)
        repeat(260) { advanceTimeBy(1000); runCurrent() }
        val stale = loop.counters.automatic[DeliveryOutcome.STALE] ?: 0
        val spoken = loop.counters.automatic[DeliveryOutcome.SPEAK_NOW] ?: 0
        assertTrue(source.completed)
        // M1.2 measured six late replies; chapter wake-ups add chances, but nothing that raced the car survives.
        assertTrue(calls >= 6); assertTrue(stale >= 6)
        assertEquals(calls, stale + spoken)
        assertTrue("only the terminus, where position stops changing, may deliver", spoken <= 1)
        assertEquals(spoken, voice.speech.size)
        println("M1.2 reproduction: 100 km/h x16, 5s latency, $calls auto requests, $stale stale, ${voice.speech.size} voice outputs")
        println("M1.2 reproduction: 100 km/h x16, 5s latency, $calls auto requests, 0 voice outputs, all stale")
        loop.cancel(); source.stop()
    }

    @Test fun realGpsCadenceAndSpatialProtectionKeepTheirOriginalBoundaries() {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice)
        journey.start(); journey.location(Fix(30.67, 104.06, time)); assertTrue(journey.shouldCheck())
        val ticket = journey.ticket(false)
        time += 44999; journey.location(Fix(30.70, 104.06, time)); assertFalse(journey.shouldCheck())
        time++; journey.location(journey.fix!!.copy(timeMs = time)); assertTrue(journey.shouldCheck())
        assertEquals(DeliveryOutcome.STALE, journey.deliver(ticket, DirectorResult(Action.SPEAK_NOW, "旧", "不可播放").json()))
        journey.deliver(journey.ticket(false), DirectorResult(Action.SPEAK_NOW, "新", "现场讲述").json())
        voice.finish(); time += 59999; journey.location(Fix(30.72, 104.06, time)); assertFalse(journey.shouldCheck())
        time++; journey.location(journey.fix!!.copy(timeMs = time)); assertFalse(journey.shouldCheck()) // <3 km after speech
        journey.location(Fix(30.74, 104.06, time)); assertTrue(journey.shouldCheck())
    }

    private fun TestScope.session(acceleration: Double = 16.0, provider: DirectorProvider): Triple<Journey, DirectorLoop, SimulatedLocationSource> {
        val clock = { 1000000L + testScheduler.currentTime }
        val journey = Journey(clock, TestVoice()); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { provider })
        val source = SimulatedLocationSource(fixture(), backgroundScope, clock, 100.0, acceleration,
            paused = { loop.pending || journey.speaking || journey.listening })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters })
        source.start(loop::location)
        return Triple(journey, loop, source)
    }

    @Test fun full16xRouteHasMeaningfulProgressOpportunitiesWithDelayedSilentProvider() = runTest {
        val (_, loop, source) = session(provider = DirectorProvider { delay(15000); DirectorResult(Action.SILENT).json() })
        repeat(600) { advanceTimeBy(1000); runCurrent() }
        assertTrue(source.completed)
        assertTrue(loop.counters.opportunities in 13..27) // Each FAILED chapter retains one check in addition to ordinary cadence.
        assertEquals(loop.counters.opportunities, loop.counters.automatic[DeliveryOutcome.SILENT])
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE])
        assertEquals(loop.counters.opportunities, loop.counters.dispatched)
    }

    @Test fun sixtyTimesSmokeModeIsNotLimitedByWallCadence() = runTest {
        val (_, loop, source) = session(60.0, DirectorProvider { delay(5000); DirectorResult(Action.SILENT).json() })
        repeat(200) { advanceTimeBy(1000); runCurrent() }
        assertTrue(source.completed); assertTrue(loop.counters.opportunities in 13..27)
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE])
    }

    @Test fun requestSpeechAndOneShotListeningFreezeTravelWithFreshFixesThenResume() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            delay(15000); DirectorResult(Action.ASK_USER, question = "今天准备去哪儿？").json()
        } })
        val source = SimulatedLocationSource(fixture(), backgroundScope, clock, 100.0, 16.0,
            paused = { loop.pending || journey.speaking || journey.listening })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location); runCurrent()
        assertTrue(loop.pending)
        advanceTimeBy(15000); runCurrent(); assertTrue(journey.speaking)
        assertEquals(0.0, source.traveledMeters, 0.001)
        advanceTimeBy(30000); runCurrent(); assertEquals(0.0, source.traveledMeters, 0.001)
        assertEquals(clock(), journey.fix!!.timeMs)
        voice.finish(); assertTrue(journey.listening)
        advanceTimeBy(15000); runCurrent(); assertEquals(0.0, source.traveledMeters, 0.001)
        voice.answer!!(null); advanceTimeBy(3000); runCurrent()
        assertTrue(source.traveledMeters in 800.0..1400.0) // No catch-up for the paused minute.
        assertEquals(1, voice.listeners); source.stop(); loop.cancel()
    }

    @Test fun quietTenMinutesAndNaturalLanguageDurationsStayOnWallClockAt16And60Times() = runTest {
        for (acceleration in listOf(16.0, 60.0)) {
            val (journey, loop, source) = session(acceleration, DirectorProvider { DirectorResult(Action.SILENT).json() })
            runCurrent(); journey.quiet(); loop.cancel()
            advanceTimeBy(599999); runCurrent(); journey.tick()
            assertTrue(journey.isQuiet); assertEquals(1L, journey.quietRemaining)
            assertTrue(source.completed); assertEquals(1, loop.counters.dispatched)
            advanceTimeBy(1); journey.tick(); assertFalse(journey.isQuiet)
            assertFalse(journey.shouldCheck()) // Resume grants permission, never forced speech.
            journey.requestInput("安静半小时"); assertEquals(1800000L, journey.quietRemaining)
            journey.resume(); assertFalse(journey.isQuiet); source.stop(); loop.cancel()
        }
    }

    @Test fun interruptionCancelsPendingRequestAndOldSpeechCannotReplayInSimulation() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start()
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider {
            delay(15000); DirectorResult(Action.SPEAK_NOW, "旧", "旧内容").json()
        } })
        val source = SimulatedLocationSource(fixture(), backgroundScope, clock, 100.0, 16.0,
            paused = { loop.pending || journey.speaking || journey.listening })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location); runCurrent()
        loop.speak(); assertFalse(loop.pending); assertTrue(journey.listening)
        advanceTimeBy(20000); runCurrent(); assertTrue(voice.speech.isEmpty())
        assertEquals(1, loop.counters.automatic[DeliveryOutcome.CANCELLED])
        voice.answer!!(null)
        journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "新", "一段").json())
        val oldDone = voice.done!!; val stops = voice.stops
        loop.speak(); assertEquals(stops + 1, voice.stops); oldDone(true); assertTrue(journey.listening)
        source.stop(); loop.cancel()
    }

    @Test fun prepareExpiryDeviationAndOldEpochRemainProtectedWithSimulationCadence() {
        var time = 1000000L; var progress = 0.0; val voice = TestVoice(); val journey = Journey({ time }, voice)
        journey.start(); journey.simulationCadence({ (progress * 36).toLong() }, { progress })
        journey.location(Fix(30.67, 104.06, time))
        val prepare = DirectorResult(Action.PREPARE, "目标", prepareHint = "现场重新确认").json()
        journey.deliver(journey.ticket(false), prepare); assertNotNull(journey.prepared)
        time += 300001; journey.tick(); assertNull(journey.prepared)
        journey.location(journey.fix!!.copy(timeMs = time)); journey.deliver(journey.ticket(false), prepare)
        val old = journey.ticket(false); progress = 10000.0; journey.location(Fix(30.77, 104.06, time))
        assertNull(journey.prepared)
        assertEquals(DeliveryOutcome.STALE, journey.deliver(old, DirectorResult(Action.SPEAK_NOW, "旧", "旧文").json()))
        val cancelled = journey.ticket(false); journey.quiet(); journey.resume()
        assertEquals(DeliveryOutcome.CANCELLED, journey.deliver(cancelled, prepare)); assertTrue(voice.speech.isEmpty())
    }

    @Test fun full100Kmh16xDelayedProviderDeliversAutomaticSpeechAndDialogue() = runTest {
        val clock = { 1000000L + testScheduler.currentTime }; val voice = TestVoice()
        val journey = Journey(clock, voice); journey.start(); var auto = 0
        val loop = DirectorLoop(journey, ContextPipeline(), backgroundScope, clock, { DirectorProvider { input ->
            delay(5000)
            if (input.userUtterance != null) DirectorResult(Action.SPEAK_NOW, "目的地确认", "好，路上有值得讲的再说。").json()
            else if (auto++ == 0) DirectorResult(Action.ASK_USER, question = "今天准备去哪儿？").json()
            else DirectorResult(Action.SPEAK_NOW, "现场主题$auto", "只讲清当前场景的一个问题。").json()
        } })
        val source = SimulatedLocationSource(fixture(), backgroundScope, clock, 100.0, 16.0,
            paused = { loop.pending || journey.speaking || journey.listening })
        journey.simulationCadence({ source.simulatedTravelMs }, { source.traveledMeters }); source.start(loop::location)
        var speechStarted = 0L
        repeat(1000) {
            advanceTimeBy(1000); runCurrent()
            if (journey.speaking) {
                if (speechStarted == 0L) speechStarted = clock()
                if (clock() - speechStarted >= 20000) { voice.finish(); speechStarted = 0 }
            }
            if (journey.listening) { voice.answer!!("去绵阳"); runCurrent() }
        }
        assertTrue(source.completed); assertEquals("去绵阳", journey.destination)
        assertTrue(loop.counters.opportunities in 13..27)
        assertTrue((loop.counters.automatic[DeliveryOutcome.SPEAK_NOW] ?: 0) >= 8)
        assertNull(loop.counters.automatic[DeliveryOutcome.STALE]); assertEquals(1, loop.counters.activeDispatched)
        val report = "M1.3 100 km/h x16, 5s provider latency, 20s TTS: PASS\n${loop.counters.summary()}\nVoice outputs=${voice.speech.size}; distance=${source.traveledMeters.toInt()}m"
        File("build/acceptance").mkdirs(); File("build/acceptance/accelerated-simulation.txt").writeText(report); println(report)
        assertTrue(journey.end().topics.isNotEmpty()); loop.cancel(); source.stop()
    }

    @Test fun simulationContextSeparatesTravelFromWallTimeAndOrdinaryRestartRemovesIt() {
        var wall = 1000000L; var travel = 0L; var meters = 0.0
        val voice = TestVoice(); val journey = Journey({ wall }, voice); val pipeline = ContextPipeline()
        journey.start(); journey.simulationCadence({ travel }, { meters }); journey.location(Fix(30.67, 104.06, wall))
        assertTrue(pipeline.card(journey, wall).contains("尚未询问"))
        journey.requestInput("去绵阳")
        journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "确认", "好的").json()); voice.finish()
        wall += 1000; travel += 600000; meters += 16666.0
        val card = pipeline.card(journey, wall)
        assertTrue(card.contains("距上次讲话：1 秒")); assertTrue(card.contains("距上次讲话的模拟行驶：600 秒"))
        assertFalse(card.contains("尚未询问")); assertTrue(card.length < 2000)
        journey.end(); journey.start(); journey.location(Fix(30.67, 104.06, wall)); journey.ticket(false)
        wall += 1000; journey.location(Fix(30.77, 104.06, wall))
        assertFalse(journey.shouldCheck()); assertFalse(pipeline.card(journey, wall).contains("开发模拟节奏"))
    }
}
