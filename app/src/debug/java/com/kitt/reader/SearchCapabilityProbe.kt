package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.FilterInputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/** Diagnostic only: one short required search, no dossier/Director contract, no production caller. */
internal class SearchCapabilityProbe(
    private val client: OkHttpClient = diagnosticClient(), private val deadlineMs: Long = 180_000
) {
    init { require(deadlineMs in 1..180_000) }
    companion object {
        const val QUESTION = "请联网搜索四川省成都市新都区，找出一个具体、值得了解的历史文化地点。只用一句简短中文回答，并保留搜索来源。"
        fun payload() = buildJsonObject {
            put("model", "gpt-5.6-luna"); put("store", false); put("stream", true)
            put("instructions", "请实际联网搜索，简短回答并保留来源。")
            put("input", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", QUESTION) }) })
            put("tools", buildJsonArray { add(buildJsonObject { put("type", "web_search") }) })
            put("tool_choice", "required"); put("include", buildJsonArray { add("web_search_call.action.sources") })
        }
        fun diagnosticClient() = ResearchTransportProbe.defaultClient().newBuilder()
            .readTimeout(180, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()
    }

    suspend fun run(bearer: String, emit: (JsonObject) -> Unit = {}): JsonObject {
        val trace = ResearchTransportProbe.Trace()
        val events = SearchCapabilityEvents(trace::elapsed, emit, bearer)
        val request = Request.Builder().url("${ChatGptProtocol.RESOURCE}/responses")
            .header("Authorization", "Bearer $bearer").header("Accept", "text/event-stream")
            .post(payload().toString().toRequestBody("application/json".toMediaType())).build()
        var terminal = "not_started"
        var failure: Exception? = null
        try {
            terminal = withTimeout(deadlineMs) {
                responseFromCall(client.newBuilder().eventListener(trace).build().newCall(request)) { response ->
                    trace.record(response)
                    events.note("http_headers", buildJsonObject {
                        put("http_status", response.code); put("headers_first_byte_ms", trace.headersMs)
                    })
                    val body = requireNotNull(response.body)
                    if (!response.isSuccessful) {
                        val raw = ResearchTransportProbe.boundedJson(body.byteStream(), 32000, trace::firstByte)
                        events.errorBody(raw)
                        return@responseFromCall "http_error"
                    }
                    val input = object : FilterInputStream(body.byteStream()) {
                        private fun observed(count: Int) { if (count > 0 && trace.bodyFirstByteMs < 0) {
                            trace.firstByte(); events.note("body_first_byte")
                        } }
                        override fun read(): Int = super.read().also { observed(if (it >= 0) 1 else 0) }
                        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also(::observed)
                    }
                    // This reader has no production 85s deadline or dossier parser.
                    events.read(input.bufferedReader())
                }
            }
        } catch (e: TimeoutCancellationException) { terminal = "total_timeout"; failure = e }
        catch (e: CancellationException) { events.finish("cancelled", trace.stage); throw e }
        catch (e: Exception) { terminal = "exception"; failure = e }
        events.finish(terminal, trace.stage, failure?.javaClass?.simpleName.orEmpty())
        return buildJsonObject {
            put("http_status", trace.status); put("request_id", trace.requestId)
            put("headers_first_byte_ms", trace.headersMs); put("body_first_byte_ms", trace.bodyFirstByteMs)
            put("elapsed_ms", trace.elapsed()); put("diagnostic_deadline_ms", deadlineMs)
            put("terminal", terminal); put("phase", trace.stage)
            failure?.let { put("exception", it.javaClass.simpleName) }
            events.summary().forEach { (k, v) -> put(k, v) }
        }
    }
}

/** Persist metadata for EVERY dispatched SSE frame, including unknown types; never text/IDs/URLs. */
internal class SearchCapabilityEvents(
    private val elapsed: () -> Long, private val emit: (JsonObject) -> Unit = {}, private val bearer: String = ""
) {
    private val timeline = mutableListOf<JsonObject>()
    private val sources = linkedSetOf<String>()
    private var frozen = false
    private var toolSeen = false
    private var toolItemSeen = false
    private val lifecycleTypes = linkedSetOf<String>()
    private var searchCompleted = false
    private var textStarted = false
    private var responseCompleted = false
    private var capabilityRejected = false
    private var errorCode = ""
    private var errorShape = ""
    private var lastType = ""
    private var lastEventMs = -1L
    private var malformed = 0
    private fun label(value: String): String = if (bearer.isNotBlank() && bearer in value) "redacted" else
        value.take(160).filter { it.isLetterOrDigit() || it in "._-" }
    private fun JsonObject.word(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    @Synchronized fun note(kind: String, details: JsonObject = JsonObject(emptyMap())) {
        if (frozen) return
        require(timeline.size < 5000) { "probe_event_limit" }
        val row = buildJsonObject { put("kind", kind); put("elapsed_ms", elapsed()); details.forEach { (k, v) -> put(k, v) } }
        timeline += row; emit(row)
    }
    private fun inspect(element: JsonElement, parent: String = "", depth: Int = 0) {
        if (depth > 16) return
        when (element) {
            is JsonArray -> element.forEach { inspect(it, parent, depth + 1) }
            is JsonObject -> {
                val type = element.word("type")
                if (type == "web_search_call") {
                    toolSeen = true; toolItemSeen = true
                    if (element.word("status") == "completed") searchCompleted = true
                }
                if (type == "output_text" && element.word("text").isNotBlank()) textStarted = true
                if (parent == "sources" || type == "url_citation") {
                    val url = element.word("url")
                    if (runCatching { URI(url).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)) {
                        require(sources.size < 1024 || url in sources) { "probe_source_limit" }; sources += url
                    }
                }
                element.forEach { (key, value) -> inspect(value, key, depth + 1) }
            }
            else -> Unit
        }
    }
    private fun error(root: JsonObject) {
        val nested = root["error"]
        val e = nested as? JsonObject ?: root
        errorCode = label(e.word("code").ifBlank { (nested as? JsonPrimitive)?.contentOrNull.orEmpty() })
        errorShape = safeResponseShape(root).toString().take(4000)
        // Decide using the error internally; persist code/shape only, never the message/body.
        val detail = (e.word("message") + " " + root.word("detail")).lowercase()
        val code = errorCode.lowercase()
        capabilityRejected = capabilityRejected || code.contains("unsupported_tool") || code.contains("tool_not_supported") ||
            code.contains("scope_not_authorized") || code.contains("route_not_supported") ||
            (("web_search" in code || "permission" in code || "capability" in code) &&
                listOf("unsupported", "denied", "not_allowed", "not_authorized", "insufficient", "disabled").any { it in code }) ||
            (listOf("web_search", "web search", "hosted search").any { it in detail } &&
                listOf("not supported", "unsupported", "not allowed", "permission", "not authorized").any { it in detail })
    }
    @Synchronized fun errorBody(raw: String) {
        if (frozen) return
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (root != null) error(root) else errorShape = "not_json"
        note("http_error", buildJsonObject { put("error_code", errorCode); put("capability_rejected", capabilityRejected) })
    }
    @Synchronized fun event(raw: String, sseName: String): String? {
        if (frozen) return "cancelled"
        if (raw == "[DONE]") { note("sse_done"); return "sse_done" }
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val type = root?.word("type").orEmpty()
        val actualType = type.ifBlank { sseName }
        if (root == null) malformed++
        if (actualType.contains("web_search_call")) {
            toolSeen = true; lifecycleTypes += label(actualType)
            if (actualType.endsWith(".completed") && root != null) searchCompleted = true
        }
        if (actualType.contains("output_text") && root != null &&
            (root.word("delta").isNotBlank() || root.word("text").isNotBlank())) textStarted = true
        root?.let {
            inspect(it)
            if (it.containsKey("error") || actualType in setOf("error", "response.failed", "response.incomplete")) {
                error((it["response"] as? JsonObject) ?: it)
            }
        }
        if (actualType == "response.completed" && root != null) responseCompleted = true
        lastType = label(type).ifBlank { label(sseName).ifBlank { "missing_type" } }; lastEventMs = elapsed()
        note("sse_event", buildJsonObject {
            put("type", label(type).ifBlank { "missing_type" }); put("sse_event_name", label(sseName))
            (root?.get("item") as? JsonObject)?.let { item ->
                put("item_type", label(item.word("type"))); put("item_status", label(item.word("status")))
            }
            put("web_search_seen", toolSeen); put("search_completed", searchCompleted)
            put("source_count", sources.size); put("output_text_started", textStarted)
        })
        return actualType.takeIf { root != null && it in setOf("response.completed", "response.failed", "response.incomplete", "error") }
    }
    fun read(reader: BufferedReader): String {
        var total = 0; var sseName = ""; val data = StringBuilder(); var comment = false
        fun dispatch(): String? {
            val terminal = if (data.isNotEmpty()) event(data.toString().trimEnd('\n'), sseName)
                else { if (comment) note("sse_comment"); if (sseName.isNotEmpty()) note("sse_event", buildJsonObject {
                    put("type", "missing_type"); put("sse_event_name", label(sseName))
                }); null }
            data.clear(); sseName = ""; comment = false; return terminal
        }
        while (true) {
            val line = StringBuilder(); var eof = false
            while (true) {
                val c = reader.read()
                if (c < 0) { eof = true; break }
                require(++total <= 2_000_000 && line.length < 128000) { "probe_sse_size_limit" }
                if (c == 10) break
                if (c != 13) line.append(c.toChar())
            }
            if (line.isEmpty()) dispatch()?.let { return it }
            else when {
                line.startsWith("data:") -> {
                    require(data.length + line.length < 256000) { "probe_sse_frame_limit" }
                    data.append(line.substring(5).removePrefix(" ")).append('\n')
                }
                line.startsWith("event:") -> sseName = line.substring(6).trim()
                line.startsWith(":") -> comment = true
            }
            if (eof) { dispatch()?.let { return it }; return "socket_eof" }
        }
    }
    @Synchronized fun finish(terminal: String, phase: String, exception: String = "") {
        if (frozen) return
        note("terminal", buildJsonObject {
            put("outcome", terminal); put("phase", phase); if (exception.isNotBlank()) put("exception", exception)
        }); frozen = true
    }
    @Synchronized fun summary() = buildJsonObject {
        put("classification", when {
            capabilityRejected -> "D"
            searchCompleted && sources.isNotEmpty() && responseCompleted -> "A"
            toolSeen -> "B"
            else -> "C"
        })
        put("web_search_item_or_lifecycle_seen", toolSeen); put("search_completed", searchCompleted)
        put("web_search_item_seen", toolItemSeen)
        put("web_search_lifecycle_event_types", JsonArray(lifecycleTypes.map(::JsonPrimitive)))
        put("sources_count", sources.size); put("output_text_started", textStarted)
        put("response_completed", responseCompleted); put("last_sse_event_type", lastType)
        put("last_sse_event_elapsed_ms", lastEventMs); put("unparseable_events", malformed)
        put("error_code", errorCode); put("error_shape", errorShape); put("timeline", JsonArray(timeline.toList()))
    }
}
