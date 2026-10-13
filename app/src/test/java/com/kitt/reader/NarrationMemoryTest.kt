package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

class NarrationMemoryTest {
    private var time = 1_000_000L
    private val voice = TestVoice()
    private val journey = Journey({ time }, voice)
    private val area = AreaIdentity("测试市", "甲区", "甲街道", "测试省")
    private fun start() { journey.start(); position(area) }
    private fun position(identity: AreaIdentity) {
        journey.location(Fix(30.0, 104.0, time, administrative = identity))
    }
    private fun result(topic: String = "古桥", facts: String = "石拱承重", text: String = "石拱把荷载传向两端。") =
        DirectorResult(Action.SPEAK_NOW, topic, text, memoryUpdate = facts)
    private fun send(value: DirectorResult = result(), active: Boolean = false) = journey.deliver(journey.ticket(active), value.json())

    @Test fun queuedNarrationIsNotCompletedMemory() {
        start(); send()
        assertTrue(journey.recentTopics.isEmpty())
        voice.finish(); assertEquals(listOf("石拱承重"), journey.recentTopics.toList())
    }

    @Test fun topicDifferentFromSummaryStillSuppressesRepeatedFact() {
        start(); send(); voice.finish()
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(text = "荷载经石拱传到两头。")))
        assertEquals(1, voice.speech.size)
    }

    @Test fun sameObjectWithNewFactRemainsEligible() {
        start(); send(); voice.finish()
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(result(facts = "拱脚水平推力", text = "拱脚需抵抗水平推力。")))
        voice.finish(); assertEquals(2, journey.narrationMemory.count { it.phase == NarrationPhase.COMPLETED })
    }

    @Test fun subsetAndReorderedFactsDoNotRepeatButDifferentObjectCanShareMechanism() {
        start(); send(result(facts = "石拱承重；拱脚水平推力")); voice.finish()
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(facts = "拱脚水平推力； 石拱承重。")))
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(facts = "石拱承重")))
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(result(topic = "另一座桥")))
    }

    @Test fun priorFactsAccumulateWithoutBlacklistingObject() {
        start(); send(); voice.finish(); send(result(facts = "拱脚水平推力")); voice.finish()
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(facts = "石拱承重；拱脚水平推力")))
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(result(facts = "桥面排水")))
    }

    @Test fun leaveAndReturnRetainsCoverageButDifferentDistrictSameNameSpeaks() {
        start(); send(); voice.finish()
        position(area.copy(district = "乙区"))
        assertEquals(DeliveryOutcome.SPEAK_NOW, send()); voice.finish()
        position(area)
        assertEquals(DeliveryOutcome.SUPPRESSED, send())
    }

    @Test fun prepareRecheckStillFiltersDeliveredCoverage() {
        start(); send(); voice.finish()
        send(DirectorResult(Action.PREPARE, "古桥", prepareHint = "新现场重新确认"))
        assertNotNull(journey.prepared)
        assertEquals(DeliveryOutcome.SUPPRESSED, send()); assertNull(journey.prepared)
        assertEquals(1, voice.speech.size)
    }

    @Test fun failedVoiceDoesNotClaimCompletedOrAutomaticallyReplay() {
        start(); send(); voice.done!!(false)
        assertTrue(journey.recentTopics.isEmpty())
        assertEquals(NarrationPhase.FAILED, journey.narrationMemory.single().phase)
        assertEquals(DeliveryOutcome.SUPPRESSED, send())
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(result(facts = "可靠新事实")))
    }

    @Test fun cancelledBeforeStartIsNotCompletedAndLateCompletionCannotChangeIt() {
        start(); send(); val late = voice.done!!; journey.beginListening {}
        assertEquals(NarrationPhase.CANCELLED, journey.narrationMemory.single().phase)
        late(true); assertTrue(journey.recentTopics.isEmpty()); assertTrue(journey.listening)
        voice.answer!!(null); assertEquals(DeliveryOutcome.SUPPRESSED, send())
    }

    @Test fun startedThenInterruptedNeverClaimsFullCoverageOrResumesAutomatically() {
        var began: (() -> Unit)? = null; var completed: ((Boolean) -> Unit)? = null
        val tracked = object : VoicePort {
            override fun stop() = Unit
            override fun listen(result: (String?) -> Unit) = Unit
            override fun speak(text: String, complete: (Boolean) -> Unit) { completed = complete }
            override fun speakTracked(text: String, started: () -> Unit, complete: (Boolean) -> Unit) {
                began = started; completed = complete
            }
        }
        val j = Journey({ time }, tracked); j.start(); j.location(Fix(30.0, 104.0, time))
        j.deliver(j.ticket(false), result().json()); assertEquals(NarrationPhase.QUEUED, j.narrationMemory.single().phase)
        began!!(); assertEquals(NarrationPhase.STARTED, j.narrationMemory.single().phase)
        j.quiet(); completed!!(true)
        assertEquals(NarrationPhase.INTERRUPTED, j.narrationMemory.single().phase); assertTrue(j.recentTopics.isEmpty())
        j.resume(); j.location(Fix(30.0, 104.0, time))
        assertEquals(DeliveryOutcome.SUPPRESSED, j.deliver(j.ticket(false), result().json()))
    }

    @Test fun obsoleteStartAndCompletionCannotMarkANewJourney() {
        start(); send(); val old = voice.done!!
        journey.end(); start(); send(result(facts = "新材料")); old(true)
        assertEquals(NarrationPhase.QUEUED, journey.narrationMemory.single().phase)
        assertTrue(journey.recentTopics.isEmpty()); voice.finish(); assertEquals(listOf("新材料"), journey.recentTopics.toList())
    }
    @Test fun duplicatedCompletionCannotFinishNextAutomaticSpeechInSameEpoch() {
        start(); send(); val old = voice.done!!; voice.finish()
        send(result(facts = "独立新事实")); old(true)
        assertTrue(journey.speaking); assertEquals(NarrationPhase.QUEUED, journey.narrationMemory.last().phase)
        assertEquals(1, journey.recentTopics.size); voice.finish(); assertEquals(2, journey.recentTopics.size)
    }

    @Test fun restartAndLegacyRecoveryDoNotImportCompletedClaimsOrReplayText() {
        start(); send(); voice.finish(); val summary = journey.end()
        journey.restore(summary.started, summary.destination, "", summary.topics, 0); position(area)
        assertTrue(journey.recentTopics.isEmpty()); assertTrue(journey.narrationMemory.isEmpty()); assertNull(journey.replay)
        assertTrue("未验证播放完成" in journey.narrationCard())
        assertEquals(DeliveryOutcome.SPEAK_NOW, send())
    }

    @Test fun ordinaryActiveQuestionAndImageTakeoverBypassAutoCoverage() {
        start(); send(); voice.finish(); journey.beginImageInteraction()
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(active = true)); voice.finish()
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(active = true))
    }

    @Test fun moreWithNoNewCoverageReportsBrieflyAndKeepsMemoryHonest() {
        start(); send(); voice.finish()
        assertEquals(DeliveryOutcome.SUPPRESSED, journey.deliver(journey.ticket(true), result().json(), followUp = FollowUp.MORE))
        assertEquals("目前没有可靠的新内容。", voice.speech.last()); voice.finish()
        assertEquals(1, journey.narrationMemory.size)
        journey.deliver(journey.ticket(true), DirectorResult(Action.SILENT).json(), followUp = FollowUp.MORE)
        assertEquals("目前没有可靠的新内容。", voice.speech.last())
    }

    @Test fun moreWithNewCoverageAndExplicitRepeatAreEligible() {
        start(); send(); voice.finish()
        assertEquals(DeliveryOutcome.SPEAK_NOW, journey.deliver(journey.ticket(true), result(facts = "拱脚水平推力").json(), followUp = FollowUp.MORE))
        voice.finish()
        assertEquals(DeliveryOutcome.SPEAK_NOW, journey.deliver(journey.ticket(true), result().json(), followUp = FollowUp.REPEAT))
    }

    @Test fun legacyBlankSummaryCannotPermanentlyBlacklistTopicWithNewFacts() {
        start(); send(result(facts = "")); voice.finish()
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(facts = "")))
        assertEquals(DeliveryOutcome.SPEAK_NOW, send())
    }

    @Test fun memoryIsBoundedAndContainsNoNarrationOrCoordinates() {
        start(); repeat(45) { send(result(topic = "对象$it", facts = "事实$it", text = "敏感长正文$it"), true); voice.finish() }
        assertEquals(32, journey.narrationMemory.size); assertEquals(8, journey.recentTopics.size)
        assertFalse("敏感长正文" in journey.narrationCard()); assertFalse("104.0" in journey.narrationCard())
        journey.end(); assertTrue(journey.narrationMemory.isEmpty()); assertNull(journey.replay)
    }
    @Test fun landmarkIdentitySurvivesRegionAndTitleChange() {
        start(); send(result().copy(landmarkId = "bridge")); voice.finish()
        position(area.copy(chapter = "乙街道"))
        assertEquals(DeliveryOutcome.SUPPRESSED, send(result(topic = "别名").copy(landmarkId = "bridge")))
        assertEquals(DeliveryOutcome.SPEAK_NOW, send(result(topic = "另一地标").copy(landmarkId = "other")))
    }
}
