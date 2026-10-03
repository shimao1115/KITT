package com.kitt.reader

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

/** Live production contracts/scheduler/Director/Journey/native TTS, with a stationary fresh test fix.
 * Benchmark answers are checked after discovery, never passed into research or Director prompts. */
internal fun stagedResearchPhoneProbe(runtime: KittRuntime, context: Context): Bundle {
    val report = linkedMapOf<String, JsonElement>()
    val file = File(context.cacheDir, "staged-research-report.json")
    val timeline = File(context.cacheDir, "staged-research-timeline.jsonl")
    timeline.writeText("")
    fun save() = file.writeText(JsonObject(report).toString())
    val result = Bundle()
    runBlocking {
        var loop: DirectorLoop? = null
        try {
            check(runtime.config.kind == ProviderKind.CHATGPT && runtime.config.model == "gpt-5.6-luna")
            check(runtime.settings.readResearch() == null)
            withTimeout(35000) { runtime.chatGpt.refreshModels() }
            check(runtime.chatGpt.models.any { it.slug == runtime.config.model })
            val area = AreaIdentity("成都市", "新都区", "", "四川省")
            val started = System.currentTimeMillis()
            var readyAt = 0L
            var topicStarted = 0L
            var firstSpeech = ""
            var followSpeech = ""
            var ttsStarted = false
            val selected = runtime.provider()
            val director = object : DirectorProvider {
                override val acceptsImages get() = selected.acceptsImages
                override suspend fun researchNeed(request: DirectorRequest) = selected.researchNeed(request)
                override suspend fun direct(request: DirectorRequest): String {
                    val raw = selected.direct(request)
                    val parsed = DirectorContract.parse(raw)
                    if (parsed.action == Action.SPEAK_NOW) {
                        if (request.userUtterance == null && firstSpeech.isBlank()) firstSpeech = parsed.narration
                        if (request.userUtterance == "再讲一点") followSpeech = parsed.narration
                    }
                    return raw
                }
            }
            withContext(Dispatchers.Main) {
                runtime.stopSources(); runtime.loop.reset()
                runtime.journey.restore(started, "去绵阳，沿途了解地方。", "直接简短讲一个已查证的地方对象，不询问偏好。", emptyList(), 0)
                loop = DirectorLoop(runtime.journey, runtime.pipeline, runtime.scope, System::currentTimeMillis,
                    { director }, diagnostic = { message ->
                        val elapsed = System.currentTimeMillis() - started
                        timeline.appendText(buildJsonObject { put("elapsed_ms", elapsed); put("event", message) }.toString() + "\n")
                        if (message.startsWith("research started stage=topic")) topicStarted = System.currentTimeMillis()
                    }, researchProvider = runtime::researchProvider, researchChanged = {
                        if (readyAt == 0L && loop?.research?.overview(area) != null) readyAt = System.currentTimeMillis()
                    })
                loop!!.location(Fix(30.823, 104.160, started, accuracy = 10.0, administrative = area))
            }
            suspend fun tick() = withContext(Dispatchers.Main) {
                loop!!.location(Fix(30.823, 104.160, System.currentTimeMillis(), accuracy = 10.0, administrative = area))
                if ((runtime.voice as? AndroidVoice)?.detail?.phase == VoicePhase.SPEAKING) ttsStarted = true
            }
            withTimeout(95000) {
                while (withContext(Dispatchers.Main) { loop!!.research.overview(area) == null }) {
                    check(withContext(Dispatchers.Main) { loop!!.research.status(area) != ResearchStatus.FAILED }) { "Overview failed" }
                    delay(250); tick()
                }
            }
            val overview = withContext(Dispatchers.Main) { loop!!.research.overview(area)!! }
            report["overview_ready_ms"] = JsonPrimitive(readyAt - started)
            report["overview_sources"] = JsonPrimitive(overview.sources.size)
            report["overview_objects"] = JsonArray(overview.objects.map { JsonPrimitive(it.title) })
            report["overview_evidence"] = JsonPrimitive(overview.evidence().text())
            save()
            withTimeout(65000) { while (firstSpeech.isBlank() || !ttsStarted) { delay(250); tick() } }
            report["overview_narration"] = JsonPrimitive(firstSpeech)
            report["overview_tts_started"] = JsonPrimitive(ttsStarted)
            save()
            // Production delivery selects one discovered lead for background deepening.
            withTimeout(95000) { while (withContext(Dispatchers.Main) { loop!!.research.completedTopics(area).isEmpty() }) { delay(250); tick() } }
            val topic = withContext(Dispatchers.Main) { loop!!.research.completedTopics(area).first() }
            report["topic_elapsed_ms"] = JsonPrimitive(System.currentTimeMillis() - topicStarted)
            report["topic_facts"] = JsonPrimitive(topic.facts.size)
            report["topic_sources"] = JsonPrimitive(topic.sources.size)
            report["topic_evidence"] = JsonPrimitive(topic.text())
            save()
            withContext(Dispatchers.Main) { loop!!.user("再讲一点") }
            withTimeout(140000) { while (followSpeech.isBlank()) { delay(250); tick() } }
            report["follow_up_narration"] = JsonPrimitive(followSpeech)
            report["follow_up_differs"] = JsonPrimitive(followSpeech != firstSpeech)
            report["follow_up_speak_now"] = JsonPrimitive(withContext(Dispatchers.Main) { (loop!!.counters.active[DeliveryOutcome.SPEAK_NOW] ?: 0) > 0 })
            var followTts = false
            withTimeout(65000) {
                while (!followTts || withContext(Dispatchers.Main) { runtime.journey.speaking }) {
                    delay(100)
                    withContext(Dispatchers.Main) {
                        if ((runtime.voice as? AndroidVoice)?.detail?.phase == VoicePhase.SPEAKING) followTts = true
                    }
                }
            }
            report["follow_up_tts_started"] = JsonPrimitive(followTts)
            report["follow_up_tts_completed"] = JsonPrimitive(withContext(Dispatchers.Main) { !runtime.journey.notice.contains("不可用") })
            report["outcome"] = JsonPrimitive("PASS")
        } catch (e: Exception) {
            report["outcome"] = JsonPrimitive("FAILED")
            report["exception"] = JsonPrimitive(e.javaClass.simpleName)
            if (e is ChatGptFailure) report["http"] = JsonPrimitive(e.toString())
        } finally {
            withContext(Dispatchers.Main) { loop?.reset(); if (runtime.journey.running) runtime.journey.end() }
            save()
        }
    }
    result.putString("report", JsonObject(report).toString())
    return result
}
