package com.kitt.reader

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FullSimulationTest {
    @Test fun chengduToMianyangFullGoldenPath() = runTest {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val pipeline = ContextPipeline(); var calls = 0
        val loop = DirectorLoop(journey, pipeline, this, { time }, { DirectorProvider { request -> calls++; FakeProvider().direct(request) } })
        val fixture = RouteFixture.parse(File("src/main/assets/chengdu-mianyang.json").readText())
        val source = SimulatedLocationSource(fixture, backgroundScope, { time })
        val events = mutableListOf<String>()
        loop.location(source.sample(0, time)); runCurrent()
        assertEquals(JourneyState.SPEAKING, journey.state); events.add("START → GPS → SPEAK_NOW (no destination question)")
        assertEquals("未询问", journey.destination)
        val stops = voice.stops; loop.speak(); assertEquals(stops + 1, voice.stops)
        voice.answer!!("再讲一点"); runCurrent(); voice.finish()
        events.add("user interruption → local intent → one deeper response")
        var narration = true; var quietChecked = false; var interruption = true
        val finishMs = (source.totalMeters / (80.0 / 3.6) * 1000).toLong() + 1000
        for (travelMs in 1000..finishMs step 1000) {
            time = 1000000L + travelMs
            loop.location(source.sample(travelMs, time)); runCurrent()
            if (journey.speaking && journey.topic == "平地上的道路" && !narration) {
                narration = true; events.add("new field → SPEAK_NOW")
                val stops = voice.stops; loop.speak(); assertEquals(stops + 1, voice.stops)
                voice.answer!!("再讲一点"); runCurrent(); voice.finish(); interruption = true
                events.add("interrupt → deeper reply → cooldown")
            } else if (journey.speaking) voice.finish()
            if (travelMs == 300000L) { journey.quiet(); events.add("quiet 10min") }
            if (travelMs == 900000L) {
                assertEquals(JourneyState.READING, journey.state); assertNull(journey.prepared); quietChecked = true
                journey.quiet(); journey.resume(); events.add("quiet expires / early resume")
            }
        }
        assertTrue(narration && interruption && quietChecked)
        assertEquals(31.501, journey.fix!!.latitude, 0.00001); assertEquals("雎水镇", journey.fix!!.administrative!!.chapter); assertTrue(calls < 90)
        assertTrue(voice.speech.size <= 3) // Start narration, user-requested supplement; no destination questionnaire.
        val summary = journey.end(); assertEquals(JourneyState.IDLE, journey.state); assertTrue(summary.topics.isNotEmpty())
        events.add("Mianyang → END → lightweight summary")
        val report = """
            KITT V0 full simulation: PASS (Fake Provider / fake Voice, production Context/Director/Journey)
            Fixture: ${fixture.name}
            Speed: 80 km/h; distance: ${source.totalMeters.toInt()} m; simulated duration: ${finishMs / 60000} min
            Director calls: $calls; TTS outputs: ${voice.speech.size}; bounded Context Card: ${pipeline.card(journey, time).length} chars
            ${events.joinToString("\n")}
            Physical audio/recognition/GPS/background and real AI content quality remain manual acceptance boundaries.
        """.trimIndent()
        File("build/acceptance").mkdirs(); File("build/acceptance/full-simulation.txt").writeText(report)
        println(report)
    }
}
