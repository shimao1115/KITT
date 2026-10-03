package com.kitt.reader

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import java.io.File

internal fun searchCapabilityPhoneProbe(runtime: KittRuntime, context: Context): Bundle {
    val result = Bundle()
    try {
        check(runtime.config.kind == ProviderKind.CHATGPT && runtime.config.model == "gpt-5.6-luna")
        check(runtime.settings.readResearch() == null && runtime.config.effort.isBlank())
        val saved = runtime.chatGpt.record
        check(saved.planEnabled && saved.expiresAt > System.currentTimeMillis()) { "Saved access token expired" }
        val timeline = File(context.cacheDir, "search-capability-timeline.jsonl")
        timeline.writeText("")
        val report = runBlocking { SearchCapabilityProbe().run(saved.accessToken) { row ->
            timeline.appendText(row.toString() + "\n")
        } }
        File(context.cacheDir, "search-capability-report.json").writeText(report.toString())
        result.putString("provider", "CHATGPT/gpt-5.6-luna/default")
        result.putString("report", report.toString())
    } catch (e: Exception) {
        result.putString("outcome", "SETUP_FAILED"); result.putString("exception", e.javaClass.simpleName)
    }
    return result
}
