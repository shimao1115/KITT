package com.kitt.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

private class SurfaceVoice : VoicePort {
    var stops = 0
    var answer: ((ListeningResult) -> Unit)? = null
    override fun stop() { stops++ }
    override fun speak(text: String, complete: (Boolean) -> Unit) { complete(true) }
    override fun listen(result: (String?) -> Unit) = error("structured path required")
    override fun listenOutcome(result: (ListeningResult) -> Unit) { answer = result }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ReplySurfaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun scene(journey: Journey, voice: SurfaceVoice, transcript: TranscriptText, onCancel: () -> Unit = {}) {
        compose.runOnUiThread { compose.activity.setContent {
            MaterialTheme { DrivingScreen(journey, "手机 GPS", {}, { journey.beginListening {} }, {}, {}, {},
                VoiceDetail(VoicePhase.LISTENING, 0.4f), transcript = transcript, onCancelReply = onCancel) }
        } }
    }

    @Test fun theOpenExchangeOffersTypingAlongsideTheMicrophone() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        var answered = ""
        compose.runOnUiThread {
            journey.start(); journey.location(Fix(30.67, 104.06, 1_000_000L, 80.0, 35.0))
            journey.beginListening { answered = it }
        }
        scene(journey, voice, TranscriptText())
        assertTrue(journey.awaitingReply)
        compose.onNodeWithTag("typed-reply").assertIsDisplayed()
        compose.onNodeWithTag("send-reply").assertIsNotEnabled()
        // Speaking is still possible: the tap control remains reachable in the same surface.
        compose.onNodeWithText("说点什么").assertIsDisplayed()
        compose.onNodeWithTag("typed-reply").performTextInput("安静十分钟")
        compose.onNodeWithTag("send-reply").performClick()
        assertEquals("安静十分钟", answered)
        assertFalse(journey.awaitingReply)
        // Driving screens must never scroll.
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    @Test fun interimTextIsShownAsStillBeingHeardAndNeverLooksCommitted() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        compose.runOnUiThread { journey.start(); journey.beginListening {} }
        scene(journey, voice, TranscriptText("再讲", final = false))
        compose.onNodeWithText("在听：再讲").assertIsDisplayed()
        compose.onAllNodesWithText("你说：再讲").assertCountEquals(0)
    }

    @Test fun finalTextIsShownAsWhatTheUserActuallySaid() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        compose.runOnUiThread { journey.start(); journey.beginListening {} }
        scene(journey, voice, TranscriptText("三星堆为什么这么有名", final = true))
        compose.onNodeWithText("你说：三星堆为什么这么有名").assertIsDisplayed()
    }

    @Test fun recognitionTroubleIsShownAsANoticeNotAsTranscriptText() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        compose.runOnUiThread {
            journey.start(); journey.location(Fix(30.67, 104.06, 1_000_000L, 80.0, 35.0))
            journey.beginListening {}
            voice.answer!!(ListeningResult(ListeningOutcome.UNAVAILABLE))
        }
        scene(journey, voice, TranscriptText())
        // The failure wording lives in its own node, and never in the transcript slot.
        compose.onNodeWithTag("journey-notice").assertIsDisplayed()
        compose.onNodeWithText("系统语音识别暂不可用。").assertIsDisplayed()
        compose.onAllNodesWithText("你说：系统语音识别暂不可用。").assertCountEquals(0)
        compose.onAllNodesWithText("在听：系统语音识别暂不可用。").assertCountEquals(0)
        // A dead backend must not strand the user: typing is still the way to answer.
        assertTrue(journey.awaitingReply)
        var answered = ""
        compose.runOnUiThread { journey.beginListening { answered = it } }
        compose.onNodeWithTag("typed-reply").performTextInput("跳过")
        compose.onNodeWithTag("send-reply").performClick()
        assertEquals("跳过", answered)
    }

    @Test fun cancelClosesTheSurfaceAndReportsBackToTheCaller() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        var cancelled = 0
        compose.runOnUiThread { journey.start(); journey.beginListening {} }
        scene(journey, voice, TranscriptText("半句"), onCancel = { journey.cancelReply(); cancelled++ })
        compose.onNodeWithTag("cancel-reply").performClick()
        assertEquals(1, cancelled)
        assertFalse(journey.awaitingReply)
        compose.onNodeWithTag("typed-reply").assertDoesNotExist()
        compose.onNodeWithTag("spoken-transcript").assertDoesNotExist()
    }

    @Test fun reachingForTheKeyboardHandsTheMicrophoneBackButKeepsTypingOpen() {
        val voice = SurfaceVoice(); val journey = Journey({ 1_000_000L }, voice)
        var answered = ""
        compose.runOnUiThread { journey.start(); journey.beginListening { answered = it } }
        scene(journey, voice, TranscriptText())
        val stopsBefore = voice.stops
        compose.onNodeWithTag("typed-reply").performClick()
        assertTrue("focusing the field must release the mic", voice.stops > stopsBefore)
        assertFalse(journey.listening)
        assertTrue(journey.awaitingReply)
        // A late transcript from the abandoned attempt cannot answer instead of the user.
        compose.runOnUiThread { voice.answer?.invoke(ListeningResult.recognized("迟到的识别")) }
        compose.onNodeWithTag("typed-reply").performTextInput("跳过")
        compose.onNodeWithTag("send-reply").performClick()
        assertEquals("跳过", answered)
    }
}
