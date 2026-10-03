package com.kitt.reader

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Safe diagnostics preserve status/code/request ID/shape, never response messages or credentials. */
class ChatGptFailure(val status: Int = 0, val code: String = "", val requestId: String = "", val shape: String = "") :
    Exception("ChatGPT request failed ($status)") {
    val terminalRefresh get() = code in setOf("invalid_grant", "invalid_refresh_token", "token_expired",
        "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
    val pausesRequests get() = status in setOf(401, 403, 429) || code in setOf("subscription_sharing_usage_limit_exceeded",
        "subscription_sharing_user_not_eligible", "subscription_sharing_invalid_user", "subscription_sharing_route_not_supported",
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context", "invalid_client")
    val userMessage get() = when {
        terminalRefresh || status == 401 || code == "subscription_sharing_invalid_user" -> "ChatGPT 授权已失效，请重新连接。"
        status == 403 || code in setOf("subscription_sharing_user_not_eligible", "subscription_sharing_route_not_supported",
            "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context") -> "当前账号、地区或权限不允许使用 ChatGPT 计划，请检查授权或手动切换 Provider。"
        status == 429 || code == "subscription_sharing_usage_limit_exceeded" -> "ChatGPT 计划用量受限，请查看 ChatGPT 用量设置或手动切换 Provider。"
        code == "subscription_sharing_usage_unavailable" -> "暂时无法确认 ChatGPT 计划用量，请稍后重试。"
        code == "invalid_client" -> "ChatGPT 注册配置未被接受，请检查连接。"
        else -> "ChatGPT 暂时无法完成请求，请稍后重试或重新连接。"
    }
    override fun toString() = "ChatGptFailure(status=$status, code=$code, requestId=$requestId, shape=$shape)"
    companion object {
        fun from(status: Int, body: String, id: String = ""): ChatGptFailure {
            val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val error = obj?.get("error")
            val code = when (error) {
                is JsonPrimitive -> error.content
                is JsonObject -> error["code"]?.jsonPrimitive?.content.orEmpty()
                else -> ""
            }
            fun safe(value: String) = value.take(160).filter { it.isLetterOrDigit() || it in "_-" }
            return ChatGptFailure(status, safe(code), safe(id), when {
                error is JsonObject -> "error_object"
                error is JsonPrimitive -> "oauth_error"
                obj?.containsKey("detail") == true -> "detail"
                else -> "other"
            })
        }
    }
}

interface ChatGptTransport {
    fun get(url: String, bearer: String = ""): String
    fun form(url: String, body: String): String
    fun stream(url: String, bearer: String, body: String): String
    fun research(url: String, bearer: String, body: String): String = throw ResearchUnavailable("当前账号 Transport 未提供研究能力。")
}

class ChatGptHttpsTransport : ChatGptTransport {
    private fun connection(url: String, bearer: String = "", type: String? = null): HttpURLConnection {
        require(URL(url).protocol == "https")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 12000; readTimeout = 25000
            if (bearer.isNotBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            if (type != null) { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", type) }
        }
    }
    private fun checked(c: HttpURLConnection): BufferedReader {
        if (c.responseCode !in 200..299) {
            val body = c.errorStream?.bufferedReader()?.use { bounded(it, 32000) }.orEmpty()
            throw ChatGptFailure.from(c.responseCode, body, c.getHeaderField("x-request-id").orEmpty())
        }
        return c.inputStream.bufferedReader(Charsets.UTF_8)
    }
    override fun get(url: String, bearer: String): String = connection(url, bearer).let { c ->
        // The live account catalog includes substantial per-model metadata (>256 KB on the acceptance account).
        val limit = if (url == "${ChatGptProtocol.RESOURCE}/models") 4_000_000 else 256000
        try { checked(c).use { bounded(it, limit) } } finally { c.disconnect() }
    }
    override fun form(url: String, body: String): String = connection(url, type = "application/x-www-form-urlencoded").let { c ->
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checked(c).use { bounded(it, 64000) }
        } finally { c.disconnect() }
    }
    override fun stream(url: String, bearer: String, body: String): String = connection(url, bearer, "application/json").let { c ->
        try {
            c.setRequestProperty("Accept", "text/event-stream")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checked(c).use { ChatGptStream.read(it, c.getHeaderField("x-request-id").orEmpty()) }
        } finally { c.disconnect() }
    }
    override fun research(url: String, bearer: String, body: String): String = connection(url, bearer, "application/json").let { c ->
        try {
            c.readTimeout = 80000
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checked(c).use { ChatGptStream.read(it, c.getHeaderField("x-request-id").orEmpty(), research = true) }
        } finally { c.disconnect() }
    }
    private fun bounded(reader: BufferedReader, max: Int): String {
        val out = StringBuilder(); val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer); if (count < 0) return out.toString()
            require(out.length + count <= max) { "ChatGPT 响应过大。" }; out.append(buffer, 0, count)
        }
    }
}

object ChatGptStream {
    fun read(reader: BufferedReader, requestId: String = "", research: Boolean = false): String {
        var total = 0; val event = StringBuilder()
        val deltas = StringBuilder(); val doneTexts = linkedMapOf<String, String>()
        val deadline = System.nanoTime() + if (research) 85_000_000_000L else 25_000_000_000L
        val textLimit = if (research) 40000 else 16000
        val items = linkedMapOf<String, JsonObject>()
        val annotations = mutableListOf<JsonElement>()
        while (true) {
            require(System.nanoTime() < deadline) { "ChatGPT stream timed out" }
            // Bound each line before allocation; a malformed stream cannot exhaust the app heap.
            val line = StringBuilder()
            while (true) {
                val c = reader.read()
                if (c < 0) throw ChatGptFailure(code = "interrupted_stream", requestId = requestId)
                total++; require(total <= (if (research) 1_000_000 else 512000) && line.length <= 128000) { "ChatGPT stream too large" }
                if (c == 10) break
                if (c != 13) line.append(c.toChar())
            }
            if (line.isEmpty()) {
                if (event.isEmpty()) continue
                val raw = event.toString().trimEnd('\n'); event.clear()
                if (raw == "[DONE]") throw ChatGptFailure(code = "interrupted_stream", requestId = requestId)
                val obj = Json.parseToJsonElement(raw).jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "response.output_item.done" -> if (research) {
                        val item = obj.getValue("item").jsonObject
                        items[obj["output_index"].toString()] = item
                    }
                    "response.output_text.annotation.added" -> if (research) annotations += obj.getValue("annotation")
                    "response.output_text.delta" -> {
                        deltas.append(obj.getValue("delta").jsonPrimitive.content)
                        require(deltas.length <= textLimit) { "Response too large" }
                    }
                    "response.output_text.done" -> {
                        val key = "${obj["output_index"]}:${obj["content_index"]}"
                        doneTexts[key] = obj.getValue("text").jsonPrimitive.content
                        require(doneTexts.values.sumOf { it.length } <= textLimit) { "Response too large" }
                    }
                    "response.failed", "error" -> {
                        val source = obj["response"] as? JsonObject ?: obj
                        throw ChatGptFailure.from(0, source.toString(), requestId)
                    }
                    "response.incomplete" -> throw ChatGptFailure(code = "incomplete_response", requestId = requestId)
                    "response.completed" -> {
                        val response = obj.getValue("response").jsonObject
                        require(response["status"]?.jsonPrimitive?.content == "completed")
                        if (research) {
                            val snapshot = response["output"]?.jsonArray ?: JsonArray(emptyList())
                            // Direct-plan snapshots can omit tool items while retaining the final message.
                            val extraTools = items.values.filter { item ->
                                item["type"]?.jsonPrimitive?.content == "web_search_call" && snapshot.none { saved ->
                                    val s = saved.jsonObject
                                    s == item || (item["id"] != null && s["id"] == item["id"])
                                }
                            }
                            val output = if (snapshot.isEmpty()) JsonArray(items.values.toList()) else JsonArray(snapshot + extraTools)
                            val hasText = output.any { item -> (item.jsonObject["content"] as? JsonArray)?.any {
                                it.jsonObject["type"]?.jsonPrimitive?.content == "output_text" && !it.jsonObject["text"]?.jsonPrimitive?.content.isNullOrBlank()
                            } == true }
                            val assembled = if (hasText) output else JsonArray(output + buildJsonObject {
                                put("type", "message"); put("content", buildJsonArray { add(buildJsonObject {
                                    put("type", "output_text"); put("text", deltas.toString().ifBlank { doneTexts.values.joinToString("") })
                                    put("annotations", JsonArray(annotations))
                                }) })
                            })
                            return JsonObject(response + ("output" to assembled)).toString()
                        }
                        val snapshot = response["output"]?.jsonArray?.flatMap { item ->
                            item.jsonObject["content"]?.jsonArray?.mapNotNull { part ->
                                val p = part.jsonObject
                                if (p["type"]?.jsonPrimitive?.content == "output_text") p["text"]?.jsonPrimitive?.content else null
                            } ?: emptyList()
                        }?.joinToString("").orEmpty()
                        // The live direct route can complete with an empty output snapshot.
                        // Stream text remains untrusted until this terminal event and strict schema validation.
                        val text = snapshot.ifBlank { deltas.toString().ifBlank { doneTexts.values.joinToString("") } }
                        require(text.isNotBlank()) { "Completed response contained no Director text" }
                        DirectorContract.parse(text)
                        return text
                    }
                }
            } else if (line.startsWith("data:")) {
                event.append(line.substring(5).removePrefix(" ")).append('\n')
            }
        }
    }
}

data class ChatGptModel(val slug: String, val displayName: String, val efforts: List<String>)
object ChatGptModels {
    fun parse(raw: String): List<ChatGptModel> = Json.parseToJsonElement(raw).jsonObject.getValue("models").jsonArray
        .map { it.jsonObject }.filter { it["visibility"]?.jsonPrimitive?.content == "list" }.map { item ->
            val slug = item.getValue("slug").jsonPrimitive.content; require(slug.isNotBlank())
            // Only server-advertised levels. Missing capability metadata means omit reasoning.
            val efforts = (item["supported_reasoning_levels"] as? JsonArray)?.mapNotNull { level ->
                (level as? JsonObject)?.get("effort")?.jsonPrimitive?.content
            } ?: emptyList()
            ChatGptModel(slug, item["display_name"]?.jsonPrimitive?.content ?: slug, efforts.distinct())
        }.distinctBy { it.slug }
}

class ChatGptProvider(private val account: ChatGptAccount, private val config: ProviderConfig) : DirectorProvider {
    override suspend fun researchNeed(request: DirectorRequest): ResearchNeed = ActiveResearchPolicy.parse(
        direct(request.copy(systemConstitution = ActiveResearchPolicy.decisionInstructions, image = null)))
    override val acceptsImages get() = true
    fun payload(request: DirectorRequest, model: ChatGptModel): JsonObject = buildJsonObject {
        put("model", model.slug); put("store", false); put("stream", true)
        put("instructions", request.systemConstitution)
        put("input", buildJsonArray { add(buildJsonObject {
            put("role", "user")
            val input = "本次 Session Instructions：${request.sessionInstructions}\n${request.contextCard}\n" +
                (request.userUtterance?.let { "用户当前明确输入：$it" } ?: "自动导演检查。可以保持安静。")
            if (request.image == null) put("content", input)
            else put("content", imageContent(input, request.image, true))
        }) })
        put("text", buildJsonObject { put("format", buildJsonObject {
            put("type", "json_schema"); put("name", "director"); put("strict", true); put("schema", DirectorContract.schema)
        }) })
        if (config.effort.isNotBlank() && config.effort in model.efforts)
            put("reasoning", buildJsonObject { put("effort", config.effort) })
    }
    override suspend fun direct(request: DirectorRequest): String = account.infer(config.model) { payload(request, it).toString() }
}
