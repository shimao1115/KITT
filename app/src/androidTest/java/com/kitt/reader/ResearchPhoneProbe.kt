package com.kitt.reader

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

/** adb-only; same saved account/model, exact production payload except stream=false. */
internal fun researchPhoneProbe(runtime: KittRuntime, targetContext: Context, streamOnly: Boolean = false): Bundle {
        val result = Bundle()
        try {
            check(runtime.config.kind == ProviderKind.CHATGPT)
            check(runtime.settings.readResearch() == null)
            val config = runtime.config
            val area = AreaIdentity("成都市", "新都区", province = "四川省")
            val at = System.currentTimeMillis()
            val reports = mutableListOf<ProbeResult>()
            val probe = ResearchTransportProbe()
            // This is a transport probe, not an auth/catalog probe. Never refresh or export credentials.
            val saved = runtime.chatGpt.record
            check(saved.planEnabled && saved.expiresAt > at) { "Saved OAuth access token must still be valid" }
            val model = runtime.chatGpt.models.singleOrNull { it.slug == config.model }
            check(config.effort.isBlank() || model != null) { "Nondefault effort requires already loaded model metadata" }
            val production = LocalResearchContract.payload(area,
                config.copy(effort = config.effort.takeIf { it in model?.efforts.orEmpty() }.orEmpty()), stream = true, at = at)
            runBlocking {
                    suspend fun trial(stream: Boolean): ProbeResult {
                        val payload = JsonObject(production + ("stream" to JsonPrimitive(stream)))
                        return probe.run("${ChatGptProtocol.RESOURCE}/responses", saved.accessToken, payload, area, at).also {
                            reports += it
                            // Incremental bounded structural evidence survives an interrupted instrumentation run.
                            File(targetContext.cacheDir, if (streamOnly) "research-transport-sse-probe.json" else "research-transport-probe.json")
                                .writeText(JsonArray(reports.map { r -> r.report }).toString())
                        }
                    }
                    val first = trial(streamOnly)
                    // Initial JSON success is the gate for two samples per arm. No retry of failed JSON.
                    if (!streamOnly && first.dossier != null) { trial(true); trial(false); trial(true) }
            }
            result.putString("provider", "CHATGPT/${config.model}/${config.effort.ifBlank { "default" }}")
            result.putString("area", area.fullName)
            result.putString("same_access_token_and_payload", "yes: saved valid token, no refresh/catalog request, only stream differs")
            result.putString("report", JsonArray(reports.map { it.report }).toString())
            result.putString("outcome", reports.firstOrNull()?.report?.get("classification")?.jsonPrimitive?.content ?: "NO_REQUEST")
        } catch (e: Exception) {
            result.putString("outcome", "PROBE_SETUP_FAILED")
            result.putString("failure", e.javaClass.simpleName)
            if (e is ChatGptFailure) result.putString("http", "status=${e.status} code=${e.code} request_id=${e.requestId} shape=${e.shape}")
        }
        return result
}
