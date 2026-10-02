package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

class TestVoice : VoicePort {
    var stops = 0; val speech = mutableListOf<String>(); var listeners = 0
    var done: ((Boolean) -> Unit)? = null; var answer: ((String?) -> Unit)? = null
    override fun stop() { stops++ }
    override fun speak(text: String, complete: (Boolean) -> Unit) { speech.add(text); done = complete }
    override fun listen(result: (String?) -> Unit) { listeners++; answer = result }
    fun finish() = done?.invoke(true)
}
class JourneyTest {
    var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice)
    fun start() { journey.start(); journey.location(Fix(30.67, 104.06, time, 80.0, 35.0)) }
    fun send(result: DirectorResult, active: Boolean = false) { journey.deliver(journey.ticket(active), result.json()) }
    @Test fun startEndAndQuietExpiryEarlyExit() {
        assertEquals(JourneyState.IDLE, journey.state); start(); assertEquals(JourneyState.READING, journey.state)
        journey.quiet(); assertEquals(600000L, journey.quietRemaining); time += 599999; journey.tick()
        assertEquals(JourneyState.QUIET, journey.state); time++; journey.tick(); assertEquals(JourneyState.READING, journey.state)
        assertTrue(voice.speech.isEmpty()); journey.quiet(); time += 1; journey.resume(); assertFalse(journey.isQuiet)
        val summary = journey.end(); assertEquals(1000000L, summary.started); assertNull(journey.prepared)
        assertEquals(JourneyState.IDLE, journey.state); assertFalse(journey.listening)
    }
    @Test fun interruptStopsImmediatelyAndStaleCallbackDies() {
        start(); send(DirectorResult(Action.SPEAK_NOW, "主题", "一段话")); val oldDone = voice.done!!
        val stops = voice.stops; journey.beginListening {}; assertEquals(stops + 1, voice.stops)
        assertEquals(JourneyState.LISTENING, journey.state); oldDone(true); assertTrue(journey.listening)
        voice.answer?.invoke(null); assertEquals(JourneyState.READING, journey.state)
    }
    @Test fun staleRequestNeverReplaysAfterUserAction() {
        start(); val old = journey.ticket(false); journey.quiet(); journey.resume()
        journey.deliver(old, DirectorResult(Action.SPEAK_NOW, "old", "旧文").json()); assertTrue(voice.speech.isEmpty())
        val ticket = journey.ticket(false); time += 46000
        journey.deliver(ticket, DirectorResult(Action.SPEAK_NOW, "old", "旧文").json()); assertTrue(voice.speech.isEmpty())
    }
    @Test fun askListensOnceAndDoesNotNag() {
        start(); val ask = DirectorResult(Action.ASK_USER, question = "今天准备去哪儿？")
        send(ask); voice.finish(); assertEquals(1, voice.listeners); voice.answer?.invoke(null)
        assertEquals("未提供", journey.destination); time += 700000; journey.location(Fix(30.68, 104.07, time))
        send(ask); assertEquals(1, voice.speech.size); assertEquals(1, voice.listeners)
    }
    @Test fun prepareReplacesExpiresAndIsNeverSpoken() {
        start(); send(DirectorResult(Action.PREPARE, "a", prepareHint = "目标 A"))
        send(DirectorResult(Action.PREPARE, "b", prepareHint = "目标 B")); assertEquals("b", journey.prepared?.topic)
        journey.location(Fix(30.8, 104.2, time + 1)); assertNull(journey.prepared); assertTrue(voice.speech.isEmpty())
        send(DirectorResult(Action.PREPARE, "c", prepareHint = "目标 C")); time += 300001; journey.tick(); assertNull(journey.prepared)
    }
    @Test fun failuresAutoSilentActiveBrieflyReports() {
        start(); journey.deliver(journey.ticket(false), "garbage"); assertTrue(voice.speech.isEmpty())
        journey.deliver(journey.ticket(true), null); assertEquals(listOf("刚才没连上，稍后再试。"), voice.speech)
    }
    @Test fun skipSuppressesOnlyTopicAndDoesNotInferPreference() {
        start(); val result = DirectorResult(Action.SPEAK_NOW, "工程", "内容")
        send(result); journey.skip(); send(result); assertEquals(1, voice.speech.size); assertEquals("", journey.instructions)
        assertFalse(journey.speaking)
    }
    @Test fun intentPreferencesAndQuietCommands() {
        start(); journey.requestInput("去绵阳"); assertEquals("去绵阳", journey.destination)
        journey.requestInput("今天多讲工程"); assertTrue(journey.instructions.contains("多讲工程"))
        assertFalse(journey.requestInput("安静半小时")); assertEquals(1800000L, journey.quietRemaining)
        journey.requestInput("结束安静"); assertFalse(journey.isQuiet)
        assertEquals(Long.MAX_VALUE, quietCommand("先别讲，等我叫你"))
    }
    @Test fun backgroundDoesNotAskAndNoGpsDoesNotCall() {
        journey.start(); assertFalse(journey.shouldCheck()); journey.foreground = false
        journey.location(Fix(30.6, 104.0, time)); send(DirectorResult(Action.ASK_USER, question = "去哪？"))
        assertTrue(voice.speech.isEmpty())
    }
    @Test fun sensorsHighFrequencyAiLowFrequency() {
        start(); assertTrue(journey.shouldCheck()); journey.ticket(false)
        repeat(100) { time += 1000; journey.location(Fix(30.67, 104.06, time)); assertFalse(journey.shouldCheck()) }
        journey.location(Fix(30.70, 104.08, time)); assertTrue(journey.shouldCheck())
    }
    @Test fun contextBoundedDoesNotDumpTrajectoryOrInventMissingAltitude() {
        start(); val context = ContextPipeline()
        repeat(3000) { context.accept(Fix(30.67, 104.06, time + it * 1000)) }
        val card = context.card(journey, time); assertTrue(card.length < 2000); assertTrue(card.contains("海拔趋势未知"))
        assertFalse(card.contains("[Fix"))
    }
    @Test fun activeQuestionCanTemporarilyTakeOverQuietWithoutCancellingTimer() {
        start(); journey.quiet(); val until = journey.quietUntil
        send(DirectorResult(Action.ASK_USER, question = "你想从哪个角度展开？"), active = true)
        voice.finish(); assertEquals(JourneyState.LISTENING, journey.state)
        voice.answer?.invoke(null); assertEquals(JourneyState.QUIET, journey.state)
        assertEquals(until, journey.quietUntil)
    }
}
