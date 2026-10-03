package com.kitt.reader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.*

/** Explicit adb-only live probe. Uses the phone's saved provider; never exports credentials or changes settings. */
class HotfixPhoneProbe : Instrumentation() {
    private var mode = "search"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode") ?: "search"; start() }
    override fun onStart() {
        val result = Bundle()
        val startedAt = System.currentTimeMillis()
        try {
            lateinit var runtime: KittRuntime
            runOnMainSync { runtime = (targetContext.applicationContext as KittApp).runtime }
            if (mode in setOf("research-transport", "research-transport-sse")) {
                startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish(0, researchPhoneProbe(runtime, targetContext, streamOnly = mode == "research-transport-sse")); return
            }
            check(runtime.config.kind == ProviderKind.CHATGPT) { "Use the saved ChatGPT account narration provider" }
            check(runtime.settings.readResearch() == null) { "Independent OpenAI research must be unchecked for this probe" }
            result.putString("provider", "CHATGPT/${runtime.config.model}/${runtime.config.effort.ifBlank { "default" }}")
            runBlocking {
                if (mode == "search") {
                    val dossier = withTimeout(90000) { runtime.researchProvider().research(
                        AreaIdentity("成都市", "新都区", "新都街道", "四川省"), System.currentTimeMillis()) }
                    result.putString("search", "READY: ${dossier.facts.size} facts / ${dossier.sources.size} sources")
                    result.putString("evidence", dossier.text())
                } else {
                    val response = withTimeout(35000) { runtime.provider().direct(DirectorRequest(
                        contextCard = "【旅程意图】目的地：未询问\n【本地研究状态】RESEARCHING；尚未获得证据，不得编造当地事实。",
                        userUtterance = if (mode == "active") "河流为什么会弯曲？请只解释一般机制。" else null)) }
                    result.putString("director", DirectorContract.parse(response).action.name)
                }
            }
            result.putString("outcome", "PASS")
        } catch (e: Exception) {
            result.putString("outcome", "FAILED")
            result.putString("failure", e.javaClass.simpleName)
            if (e is ResearchUnavailable) result.putString("research", e.reason)
            if (e is ChatGptFailure) result.putString("http", "status=${e.status} code=${e.code} request_id=${e.requestId} shape=${e.shape}")
        }
        result.putLong("elapsed_ms", System.currentTimeMillis() - startedAt)
        finish(0, result)
    }
}
