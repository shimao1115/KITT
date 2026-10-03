package com.kitt.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Explicit instrumentation only. No production caller, credentials, response text or auth headers in reports. */
internal class ResearchTransportProbe(private val client: OkHttpClient = defaultClient(), private val timeoutMs: Long = 90_000) {
    init { require(timeoutMs in 1..90_000) }
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(12, TimeUnit.SECONDS).writeTimeout(25, TimeUnit.SECONDS)
            .readTimeout(80, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()

        fun boundedJson(input: InputStream, limit: Int = 1_000_000, firstByte: () -> Unit = {}): String {
            val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return out.toString(Charsets.UTF_8.name())
                if (count == 0) continue
                if (out.size() == 0) firstByte()
                require(out.size() + count <= limit) { "probe_response_size_limit" }
                out.write(buffer, 0, count)
            }
        }
    }

    internal class Trace : EventListener() {
        private val started = System.nanoTime()
        fun elapsed() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        @Volatile var stage = "DNS"
        @Volatile var headersMs = -1L
        @Volatile var bodyFirstByteMs = -1L
        var status = 0
        var requestId = ""
        var contentType = ""
        override fun dnsStart(call: Call, domainName: String) { stage = "DNS" }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { stage = "connect" }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { stage = "connect" }
        override fun secureConnectStart(call: Call) { stage = "TLS" }
        override fun secureConnectEnd(call: Call, handshake: Handshake?) { stage = "headers" }
        override fun requestHeadersStart(call: Call) { stage = "headers" }
        override fun responseHeadersStart(call: Call) { headersMs = elapsed() }
        override fun responseHeadersEnd(call: Call, response: Response) {
            record(response)
        }
        fun record(response: Response) {
            status = response.code
            requestId = safeId(response.header("x-request-id").orEmpty())
            contentType = response.header("Content-Type").orEmpty().substringBefore(';').take(80)
            stage = "body read"
        }
        fun firstByte() { if (bodyFirstByteMs < 0) bodyFirstByteMs = elapsed() }
    }

    suspend fun run(url: String, bearer: String, payload: JsonObject, area: AreaIdentity, at: Long): ProbeResult {
        val trace = Trace()
        val streaming = payload.getValue("stream").jsonPrimitive.boolean
        val request = Request.Builder().url(url).apply { require(build().url.isHttps) }
            .header("Authorization", "Bearer $bearer")
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        var raw: String? = null
        var failure: Exception? = null
        var rejectionShape: JsonElement? = null
        var rejectionDetail = ""
        try {
            raw = withTimeout(timeoutMs) { responseFromCall(client.newBuilder().eventListener(trace).build().newCall(request)) { response ->
                trace.record(response)
                val body = requireNotNull(response.body)
                if (!response.isSuccessful) {
                    val errorBody = boundedJson(body.byteStream(), 32000, trace::firstByte)
                    val error = runCatching { Json.parseToJsonElement(errorBody).jsonObject }.getOrNull()
                    rejectionShape = error?.let(::safeResponseShape)
                    val detail = (error?.get("detail") as? JsonPrimitive)?.contentOrNull
                        ?: ((error?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    rejectionDetail = safeRejectionDetail(detail, bearer)
                    throw ChatGptFailure.from(response.code, errorBody, trace.requestId)
                }
                if (streaming) {
                    val observed = object : java.io.FilterInputStream(body.byteStream()) {
                        override fun read(): Int = super.read().also { if (it >= 0) trace.firstByte() }
                        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) trace.firstByte() }
                    }
                    observed.bufferedReader().use { ChatGptStream.read(it, trace.requestId, research = true) }
                } else boundedJson(body.byteStream(), firstByte = trace::firstByte)
            } }
        } catch (e: TimeoutCancellationException) { failure = e
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = e }
        val elapsed = trace.elapsed()
        val assessment = raw?.let { inspect(it, area, at) }
        return ProbeResult(buildJsonObject {
            put("stream", streaming); put("http_status", trace.status); put("request_id", trace.requestId)
            put("content_type", trace.contentType); put("headers_first_byte_ms", trace.headersMs)
            put("body_first_byte_ms", trace.bodyFirstByteMs); put("elapsed_ms", elapsed)
            put("stage", if (failure != null) trace.stage else "complete")
            put("transport_outcome", if (failure == null) "complete" else failure.javaClass.simpleName)
            failure?.let { e ->
                // Fixed diagnostic labels only; never log arbitrary exception messages.
                put("failure_reason", probeFailureReason(e))
                e.stackTrace.firstOrNull { it.className.startsWith("com.kitt.reader.") }?.let { site ->
                    put("failure_site", "${site.className}.${site.methodName}:${site.lineNumber}")
                }
            }
            if (failure is ChatGptFailure) {
                put("error_code", failure.code); put("error_shape", failure.shape)
                put("rejection_detail", rejectionDetail)
                rejectionShape?.let { put("response_shape", it) }
            }
            assessment?.report?.forEach { (k, v) -> put(k, v) }
            if (assessment == null) {
                // No complete response: zero OBSERVED evidence, not evidence of no search execution.
                put("completed_web_search_call", false); put("sources_count", 0); put("facts_count", 0)
                put("local_dossier_ready", false); put("evidence_complete", false)
                put("classification", if (failure is ChatGptFailure && trace.status in 400..499) "HTTP_REJECTION" else "TRANSPORT_UNRESOLVED")
            }
        }, assessment?.dossier)
    }
}

internal data class ProbeResult(val report: JsonObject, val dossier: LocalDossier?)
private fun safeId(value: String) = value.take(160).filter { it.isLetterOrDigit() || it in "_-" }

internal fun probeFailureReason(error: Exception): String = when {
    error is TimeoutCancellationException -> "total_deadline"
    error.message == "ChatGPT stream timed out" -> "sse_completion_timeout"
    error.message in setOf("ChatGPT stream too large", "Response too large", "probe_response_size_limit") -> "response_size_limit"
    error is ChatGptFailure -> "http_or_response_rejection"
    error is java.io.IOException -> "io_failure"
    else -> "response_reader_or_framing_failure"
}

internal fun safeRejectionDetail(value: String, bearer: String): String = value
    .let { if (bearer.isNotBlank()) it.replace(bearer, "[redacted]") else it }
    .replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "[redacted]")
    .replace(Regex("[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]+"), "[redacted]")
    .filter { !it.isISOControl() }.take(320)

/** Preserve topology and known structural enums, redact all response values and IDs. */
internal fun safeResponseShape(element: JsonElement, depth: Int = 0): JsonElement = when {
    depth > 8 -> JsonPrimitive("depth_limit")
    element is JsonObject -> buildJsonObject { element.entries.take(80).forEach { (k, v) ->
        put(safeId(k), if (k in setOf("type", "status") && v is JsonPrimitive && v.content in
            setOf("completed", "failed", "incomplete", "in_progress", "web_search_call", "message", "reasoning", "output_text", "url_citation", "search", "open_page", "find_in_page")) v else safeResponseShape(v, depth + 1))
    } }
    element is JsonArray -> buildJsonObject { put("count", element.size); put("items", JsonArray(element.take(24).map { safeResponseShape(it, depth + 1) })) }
    element is JsonNull -> JsonPrimitive("null")
    element is JsonPrimitive -> JsonPrimitive(if (element.isString) "string" else "primitive")
    else -> JsonPrimitive("unknown")
}

/** Evidence inspection precedes strict parsing, so a dossier parse failure cannot erase tool success. */
internal fun inspect(raw: String, area: AreaIdentity, at: Long): ProbeResult {
    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
    val output = (root?.get("output") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    val completed = output.any { it.text("type") == "web_search_call" && it.text("status") == "completed" }
    val urls = linkedSetOf<String>(); val texts = mutableListOf<String>()
    output.forEach { item ->
        ((item["action"] as? JsonObject)?.get("sources") as? JsonArray)?.forEach { source ->
            (source as? JsonObject)?.text("url")?.takeIf(String::isNotBlank)?.let(urls::add)
        }
        (item["content"] as? JsonArray)?.forEach { part -> (part as? JsonObject)?.let { p ->
            if (p.text("type") == "output_text") texts += p.text("text")
            (p["annotations"] as? JsonArray)?.forEach { ann -> (ann as? JsonObject)?.let { a ->
                if (a.text("type") == "url_citation") a.text("url").takeIf(String::isNotBlank)?.let(urls::add)
            } }
        } }
    }
    val text = texts.joinToString("")
    val dossierJson = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
    val parsed = runCatching { LocalResearchContract.response(raw, area, at) }
    val dossier = parsed.getOrNull()
    val parseStage = when {
        root == null || root.text("status") != "completed" || root["output"] !is JsonArray -> "response_shape"
        !completed -> "completed_search_missing"
        urls.isEmpty() -> "source_url_extraction"
        dossierJson == null -> "structured_output"
        dossier == null -> "local_dossier_contract"
        else -> "none"
    }
    return ProbeResult(buildJsonObject {
        put("completed_web_search_call", completed); put("sources_count", urls.size)
        put("facts_count", dossier?.facts?.size ?: 0); put("raw_facts_count", (dossierJson?.get("facts") as? JsonArray)?.size ?: 0)
        put("dossier_sources_count", dossier?.sources?.size ?: 0); put("local_dossier_ready", dossier != null)
        put("evidence_complete", true); put("parse_stage", parseStage)
        put("classification", when { dossier != null -> "READY"; completed -> "SEARCH_COMPLETED_PARSE_FAILURE"; else -> "RESPONSE_UNRESOLVED" })
        parsed.exceptionOrNull()?.let { put("parser_exception", it.javaClass.simpleName) }
        put("response_shape", root?.let(::safeResponseShape) ?: JsonPrimitive("not_json"))
        if (dossier != null) {
            val evidence = dossier.orientation + dossier.facts.joinToString { it.title + it.summary }
            put("black_box_discovery", buildJsonObject {
                put("杨升庵", "杨升庵" in evidence || "杨慎" in evidence)
                listOf("桂湖", "宝光寺", "新繁东湖").forEach { put(it, it in evidence) }
            })
        }
    }, dossier)
}
