package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

/** Structured-path fake: it records stops, hands out the one ASR callback and can be answered by voice or text. */
private class ReplyVoice : VoicePort {
    var stops = 0
    val spoken = mutableListOf<String>()
    var answer: ((ListeningResult) -> Unit)? = null
    var done: ((Boolean) -> Unit)? = null
    override fun stop() {
        val pending = answer; answer = null; stops++
        pending?.invoke(ListeningResult(ListeningOutcome.CANCELLED))
    }
    override fun speak(text: String, complete: (Boolean) -> Unit) { spoken.add(text); done = complete }
    override fun listen(result: (String?) -> Unit) = error("structured path required")
    override fun listenOutcome(result: (ListeningResult) -> Unit) { answer = result }
    fun say(text: String) { done?.invoke(true); answer?.invoke(ListeningResult.recognized(text)) }
}

class ReplyExchangeTest {
    private var time = 1_000_000L
    private val voice = ReplyVoice()
    private val journey = Journey({ time }, voice)
    private val answers = mutableListOf<String>()

    private fun openExchange() {
        journey.start(); journey.location(Fix(30.67, 104.06, time, 80.0, 35.0))
        journey.beginListening(answers::add)
    }

    @Test fun typedAnswerTravelsTheSameRequestInputPathAsSpeech() {
        journey.start(); journey.location(Fix(30.67, 104.06, time, 80.0, 35.0))
        // Identical to DirectorLoop.speak(): the handler it passes on is DirectorLoop::user, which is
        // requestInput + dispatch. Typing goes through that same handler, so there is no second grammar.
        journey.beginListening { text -> answers.add(text); journey.requestInput(text) }
        assertTrue(journey.awaitingReply)
        assertTrue(journey.submitReply("安静十分钟"))
        assertEquals(listOf("安静十分钟"), answers)
        assertFalse(journey.awaitingReply)
        assertEquals(JourneyState.QUIET, journey.state)
        assertEquals(600000L, journey.quietRemaining)
        assertEquals("", journey.notice)
    }

    @Test fun speechAnswerSubmitsOnceAndRejectsASecondTranscript() {
        openExchange()
        voice.answer!!(ListeningResult.recognized("再讲一点"))
        voice.answer?.invoke(ListeningResult.recognized("再讲一点"))
        assertEquals(listOf("再讲一点"), answers)
        assertFalse(journey.awaitingReply)
    }

    @Test fun aLateTranscriptAfterTypedAnswerCannotOverwriteOrDuplicate() {
        openExchange(); val pending = voice.answer!!
        assertTrue(journey.submitReply("跳过"))
        // The recogniser answers after the typed submit, exactly as a slow OEM service would.
        pending(ListeningResult.recognized("过期识别"))
        pending(ListeningResult(ListeningOutcome.NO_MATCH))
        assertEquals(listOf("跳过"), answers)
        assertEquals(1, answers.size)
    }

    @Test fun anUnavailableRecogniserStillLeavesTheExchangeOpenForTyping() {
        openExchange()
        voice.answer!!(ListeningResult(ListeningOutcome.UNAVAILABLE))
        assertEquals("系统语音识别暂不可用。", journey.notice)
        assertFalse(journey.listening)
        assertTrue("typing must survive a dead backend", journey.awaitingReply)
        assertTrue(journey.submitReply("详细一点"))
        assertEquals(listOf("详细一点"), answers)
    }

    @Test fun aStartupErrorFromEveryBackendIsNotAMissingAnswer() {
        openExchange()
        voice.answer!!(ListeningResult.error(5))
        assertTrue(journey.awaitingReply)
        assertFalse(journey.notice.contains("没听清"))
        assertTrue(journey.submitReply("三星堆为什么这么有名"))
        assertEquals(listOf("三星堆为什么这么有名"), answers)
    }

    @Test fun silenceClosesTheExchangeWithoutPretendingTypingIsStillPending() {
        openExchange()
        voice.answer!!(ListeningResult(ListeningOutcome.NO_MATCH))
        assertEquals("没听清，想说时再点一下。", journey.notice)
        assertFalse(journey.awaitingReply)
        assertFalse(journey.submitReply("再讲一点"))
        assertTrue(answers.isEmpty())
    }

    @Test fun choosingTheKeyboardReleasesTheMicrophoneButKeepsTheExchangeOpen() {
        openExchange(); val stopsBefore = voice.stops
        journey.stopListeningForTyping()
        assertFalse(journey.listening)
        assertTrue(voice.stops > stopsBefore)
        assertTrue(journey.awaitingReply)
        // A transcript that lands afterwards must not submit over the typed answer being composed.
        voice.answer?.invoke(ListeningResult.recognized("迟到的识别"))
        assertTrue(answers.isEmpty())
        assertTrue(journey.submitReply("安静一会儿就好"))
        assertEquals(listOf("安静一会儿就好"), answers)
    }

    @Test fun cancelClosesTheExchangeAndHandsTheMicrophoneBack() {
        openExchange()
        journey.cancelReply()
        assertFalse(journey.awaitingReply)
        assertFalse(journey.listening)
        assertEquals("", journey.notice)
        assertFalse(journey.submitReply("再讲一点"))
        assertTrue(answers.isEmpty())
    }

    @Test fun anUnansweredExchangeExpiresAndStopsCapturing() {
        openExchange()
        time += Journey.REPLY_WINDOW_MS - 1; journey.tick()
        assertTrue(journey.awaitingReply)
        time += 2; journey.tick()
        assertFalse(journey.awaitingReply)
        assertFalse(journey.listening)
        assertFalse(journey.submitReply("太晚了"))
        assertTrue(answers.isEmpty())
    }

    @Test fun skippingQuietingOrEndingAbandonsThePendingExchange() {
        listOf<Pair<String, Journey.() -> Unit>>(
            "skip" to { skip() }, "quiet" to { quiet() }, "end" to { end() },
        ).forEach { (name, action) ->
            val time = 1_000_000L
            val journey = Journey({ time }, ReplyVoice())
            journey.start(); journey.beginListening { }
            assertTrue("$name should have started open", journey.awaitingReply)
            journey.action()
            assertFalse("$name must close the exchange", journey.awaitingReply)
            assertFalse(journey.submitReply("迟到的回答"))
        }
    }

    @Test fun anOpenExchangeIsNotTalkedOverByAutomaticNarration() {
        openExchange()
        assertFalse("narration must wait while the user is answering", journey.shouldCheck())
        voice.answer!!(ListeningResult.recognized("跳过"))
        assertFalse(journey.awaitingReply)
        assertTrue(journey.shouldCheck())
    }

    @Test fun askedQuestionAcceptsEitherSpokenOrTypedReply() {
        val time = 1_000_000L; val voice = ReplyVoice()
        val journey = Journey({ time }, voice); val replies = mutableListOf<String>()
        journey.start(); journey.location(Fix(30.67, 104.06, time, 80.0, 35.0))
        val outcome = journey.deliver(journey.ticket(true),
            DirectorResult(Action.ASK_USER, question = "今天想走快点还是看看风景？").json()) { replies.add(it) }
        assertEquals(DeliveryOutcome.ASK_USER, outcome)
        assertEquals(listOf("今天想走快点还是看看风景？"), voice.spoken)
        voice.done!!(true)
        assertTrue("ASK_USER must keep a typed way to answer", journey.awaitingReply)
        assertTrue(journey.submitReply("看看风景"))
        assertEquals(listOf("看看风景"), replies)
    }

    @Test fun askedQuestionCanBeAnsweredByVoiceAndClosesAfterOneResult() {
        val time = 1_000_000L; val voice = ReplyVoice()
        val journey = Journey({ time }, voice); val replies = mutableListOf<String>()
        journey.start(); journey.location(Fix(30.67, 104.06, time, 80.0, 35.0))
        journey.deliver(journey.ticket(true), DirectorResult(Action.ASK_USER, question = "要不要绕广汉？").json()) { replies.add(it) }
        voice.done!!(true)
        assertTrue(journey.awaitingReply)
        voice.answer?.invoke(ListeningResult.recognized("绕过去"))
        assertEquals(listOf("绕过去"), replies)
        // A second transcript from the same attempt must not answer the question twice.
        voice.answer?.invoke(ListeningResult.recognized("绕过去"))
        assertEquals(listOf("绕过去"), replies)
        assertFalse(journey.awaitingReply)
    }
}
