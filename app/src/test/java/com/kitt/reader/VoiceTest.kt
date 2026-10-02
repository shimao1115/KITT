package com.kitt.reader
import org.junit.Assert.*
import org.junit.Test
class VoiceTest {
    @Test fun paragraphChunksRespectSystemLimitAndPreserveText() {
        val text = ("这是一段解释。".repeat(800))
        val chunks = speechChunks(text)
        assertEquals(text, chunks.joinToString("")); assertTrue(chunks.all { it.length <= 1800 })
        assertTrue(chunks.dropLast(1).all { it.endsWith("。") })
        assertEquals(emptyList<String>(), speechChunks(""))
    }
    @Test fun duplicateRecognitionAndOldCompletionCannotRestartDialogue() {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        journey.location(Fix(30.6, 104.0, time)); var answers = 0
        journey.deliver(journey.ticket(false), DirectorResult(Action.ASK_USER, question = "去哪？").json()) { answers++ }
        voice.finish(); val old = voice.answer!!; old("绵阳"); old("绵阳")
        assertEquals(1, answers); assertFalse(journey.listening)
        journey.end(); voice.finish(); old("去绵阳"); assertEquals(JourneyState.IDLE, journey.state)
        assertEquals(1, answers)
    }
}
