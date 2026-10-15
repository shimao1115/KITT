package com.kitt.reader

import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit instrumentation-only audition. No synthetic fix, Journey, cloud, or saved-setting writes.
 * Drafts cannot be selected here. A successful engine callback is not human listening approval. */
fun storyPreviewPhoneProbe(instrumentation: Instrumentation, arguments: Bundle?): Bundle {
    val result = Bundle()
    val context = instrumentation.targetContext
    val id = arguments?.getString("narrative_id") ?: "yangshen-main"
    val story = StoryPacks.load(context)?.stories?.find { it.id == id }
    if (story == null) {
        result.putString("outcome", "BLOCKED: requested story absent or not approved")
        return result
    }
    var runtime: KittRuntime? = null
    instrumentation.runOnMainSync { runtime = (context.applicationContext as KittApp).runtime }
    if (runtime!!.journey.running) {
        result.putString("outcome", "BLOCKED: end current Journey before independent audition")
        return result
    }
    val complete = CountDownLatch(1)
    var voice: AndroidVoice? = null
    var success = false
    val start = android.os.SystemClock.elapsedRealtime()
    instrumentation.runOnMainSync {
        voice = AndroidVoice(context).apply {
            offlineRequired = { true }
            voiceName = runtime!!.settings.voiceName
            speechRate = runtime!!.settings.speechRate
        }
        voice!!.speak(story.text) { success = it; complete.countDown() }
    }
    try {
        val finished = complete.await(610, TimeUnit.SECONDS)
        result.putString("outcome", if (finished && success) "PASS_ENGINE_ONLY" else "FAILED_OR_TIMEOUT")
        result.putString("narrative_id", id)
        result.putString("text_sha256", OfflinePack.sha256(story.text.encodeToByteArray()))
        result.putLong("elapsed_ms", android.os.SystemClock.elapsedRealtime() - start)
        result.putString("human_listening", "PENDING: engine callback cannot certify hearing or content acceptance")
    } finally { instrumentation.runOnMainSync { voice?.close() } }
    return result
}
