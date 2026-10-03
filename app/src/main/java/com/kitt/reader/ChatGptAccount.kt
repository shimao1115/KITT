package com.kitt.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

/** Single active registration for V0; identity is issuer + subject + issued client ID, never email. */
data class ChatGptRecord(
    val hostId: String = "", val clientId: String = "", val subject: String = "", val email: String = "",
    val accessToken: String = "", val refreshToken: String = "", val idToken: String = "",
    val scopes: Set<String> = emptySet(), val expiresAt: Long = 0, val earliestRefreshAt: Long = 0
) {
    val connected get() = subject.isNotBlank() && accessToken.isNotBlank()
    val planEnabled get() = connected && ChatGptProtocol.DIRECT_SCOPE in scopes
    fun withoutTokens() = copy(accessToken = "", refreshToken = "", idToken = "", scopes = emptySet(), expiresAt = 0, earliestRefreshAt = 0)
    override fun toString() = "ChatGptRecord(redacted)"
    fun encode(): String = buildJsonObject {
        put("host_id", hostId); put("client_id", clientId); put("issuer", ChatGptProtocol.ISSUER)
        put("subject", subject); put("email", email); put("access_token", accessToken)
        put("refresh_token", refreshToken); put("id_token", idToken)
        put("scopes", JsonArray(scopes.map(::JsonPrimitive))); put("expires_at", expiresAt); put("earliest_refresh_at", earliestRefreshAt)
    }.toString()
    companion object {
        fun decode(raw: String): ChatGptRecord {
            val obj = Json.parseToJsonElement(raw).jsonObject
            require(obj["issuer"]?.jsonPrimitive?.content == ChatGptProtocol.ISSUER)
            fun text(name: String) = obj[name]?.jsonPrimitive?.content.orEmpty()
            require(text("client_id") != ChatGptProtocol.DYNAMIC)
            return ChatGptRecord(text("host_id"), text("client_id"), text("subject"), text("email"), text("access_token"),
                text("refresh_token"), text("id_token"), obj.getValue("scopes").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                obj.getValue("expires_at").jsonPrimitive.long, obj.getValue("earliest_refresh_at").jsonPrimitive.long)
        }
    }
}

class ChatGptAccount(
    private val store: SettingsStore,
    private val transport: ChatGptTransport = ChatGptHttpsTransport(),
    private val keepAlive: (Boolean) -> Unit = {},
    private val diagnostic: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    var record by mutableStateOf(runCatching { store.readChatGpt()?.let(ChatGptRecord::decode) }.getOrNull() ?: ChatGptRecord()); private set
    var models by mutableStateOf<List<ChatGptModel>>(emptyList()); private set
    var message by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var catalogLoading by mutableStateOf(false); private set
    var lastFailure: ChatGptFailure? = null; private set
    private var paused = false
    private var retryAfter = 0L
    private var authJob: Job? = null
    @Volatile private var listener: ChatGptLoopback? = null
    @Volatile private var generation = 0

    private fun save(value: ChatGptRecord) { store.saveChatGpt(value.encode()); record = value }
    private fun failure(error: Exception) {
        lastFailure = error as? ChatGptFailure
        val location = error.stackTrace.firstOrNull { it.className.startsWith("com.kitt.reader.") }
        diagnostic("failure: ${error.javaClass.simpleName} location=$location" + (lastFailure?.let { " status=${it.status} code=${it.code} request_id=${it.requestId} shape=${it.shape}" } ?: ""))
        message = if (error is ChatGptFailure) error.userMessage else "连接未完成，请重试。请确保浏览器和沿途 未被系统关闭。"
        if (error is ChatGptFailure && error.pausesRequests) paused = true
        retryAfter = clock() + 60000 // No retry storm; a later explicit Settings action may reset this.
    }
    fun cancelSignIn() {
        generation++; listener?.close(); listener = null; authJob?.cancel(); authJob = null
        busy = false
        diagnostic("authorization cancelled")
        keepAlive(false)
    }
    fun signIn(scope: CoroutineScope, consent: Boolean = false, openBrowser: (String) -> Unit) {
        if (busy) return
        busy = true; message = "请在系统浏览器完成登录和授权，再返回沿途。"
        val attemptGeneration = ++generation
        authJob = scope.launch {
            try {
                keepAlive(true)
                withTimeout(ChatGptProtocol.AUTH_TIMEOUT_MS.toLong()) { withContext(Dispatchers.IO) {
                    val selected = mutex.withLock {
                        if (record.hostId.isBlank()) save(record.copy(hostId = "urn:uuid:${UUID.randomUUID()}"))
                        record
                    }
                    ChatGptLoopback().use { local ->
                        listener = local
                        diagnostic("loopback listener bound")
                        val attempt = ChatGptAttempt(local.redirectUri, selected.hostId, selected, consent)
                        withContext(Dispatchers.Main) { openBrowser(attempt.url); diagnostic("system browser opened") }
                        val (code, client) = local.receive(attempt)
                        local.close(); listener = null
                        diagnostic("callback state and registration validated")
                        ensureActive()
                        mutex.withLock {
                            check(attemptGeneration == generation)
                            // Retain issued registration even if code exchange fails or code expires.
                            if (selected.clientId.isBlank()) save(selected.copy(clientId = client))
                            diagnostic("issued client retained; exchanging code")
                            val tokens = Json.parseToJsonElement(transport.form(ChatGptProtocol.TOKEN, attempt.exchange(code, client))).jsonObject
                            diagnostic("code exchange completed; validating ID token")
                            val identity = ChatGptIdToken.verify(tokens.getValue("id_token").jsonPrimitive.content,
                                transport.get("${ChatGptProtocol.ISSUER}/.well-known/jwks.json"), client, attempt.nonce, clock())
                            require(selected.subject.isBlank() || selected.subject == identity.subject) { "所选账号身份不符。" }
                            ensureActive(); check(attemptGeneration == generation)
                            save(tokenRecord(record.copy(clientId = client, subject = identity.subject, email = identity.email), tokens))
                            diagnostic("verified identity and granted scopes saved")
                            models = emptyList(); paused = false; retryAfter = 0
                            message = if (record.planEnabled) "ChatGPT 已连接，计划使用已启用。" else "身份已登录；ChatGPT 计划使用未启用。可授权计划使用或手动切换 Provider。"
                            if (record.planEnabled) loadModelsLocked()
                            diagnostic("authorization completed")
                        }
                    }
                } }
            } catch (e: TimeoutCancellationException) { if (attemptGeneration == generation) { message = "授权等待超时，请重新连接。"; diagnostic("authorization timed out") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attemptGeneration == generation) failure(e) }
            finally {
                if (attemptGeneration == generation) { listener = null; busy = false; authJob = null; keepAlive(false) }
            }
        }
    }
    private fun tokenRecord(base: ChatGptRecord, obj: JsonObject, refresh: Boolean = false): ChatGptRecord {
        require(obj["token_type"]?.jsonPrimitive?.content?.equals("Bearer", ignoreCase = true) == true)
        val access = obj.getValue("access_token").jsonPrimitive.content; require(access.isNotBlank())
        val replacement = obj["refresh_token"]?.jsonPrimitive?.content.orEmpty()
        if (refresh) require(replacement.isNotBlank()) { "未返回轮换 refresh token。" }
        val expiry = obj.getValue("expires_in").jsonPrimitive.long; require(expiry in 1..86400)
        val scopes = obj["scope"]?.jsonPrimitive?.content?.split(' ')?.filter { it.isNotBlank() }?.toSet()
            ?: if (refresh) base.scopes else emptySet()
        if ("offline_access" in scopes) require(replacement.isNotBlank())
        val earliest = obj["earliest_refresh_at"]?.jsonPrimitive?.content?.let {
            it.toLongOrNull()?.let { seconds -> Math.multiplyExact(seconds, 1000) } ?: Instant.parse(it).toEpochMilli()
        } ?: 0
        return base.copy(accessToken = access, refreshToken = replacement,
            idToken = obj["id_token"]?.jsonPrimitive?.content ?: base.idToken,
            scopes = scopes, expiresAt = clock() + expiry * 1000, earliestRefreshAt = earliest)
    }
    private fun accessLocked(): String {
        check(record.planEnabled) { "ChatGPT 计划使用未启用。" }
        if (record.expiresAt - clock() <= 60000 && clock() >= record.earliestRefreshAt) {
            check(record.refreshToken.isNotBlank()) { "请重新登录 ChatGPT。" }
            val base = record
            try {
                val tokens = Json.parseToJsonElement(transport.form(ChatGptProtocol.TOKEN, ChatGptProtocol.form(linkedMapOf(
                    "grant_type" to "refresh_token", "client_id" to base.clientId,
                    "refresh_token" to base.refreshToken, "resource" to ChatGptProtocol.RESOURCE)))).jsonObject
                tokens["id_token"]?.jsonPrimitive?.content?.let { token ->
                    val identity = ChatGptIdToken.verify(token, transport.get("${ChatGptProtocol.ISSUER}/.well-known/jwks.json"), base.clientId, null, clock())
                    require(identity.subject == base.subject)
                }
                save(tokenRecord(base, tokens, refresh = true))
            } catch (e: ChatGptFailure) {
                if (e.terminalRefresh) { save(base.withoutTokens()); models = emptyList(); paused = true }
                throw e
            }
        }
        check(record.planEnabled && record.expiresAt > clock()) { "ChatGPT 授权已过期，请重新连接。" }
        return record.accessToken
    }
    private fun loadModelsLocked() {
        diagnostic("model catalog request started")
        val raw = transport.get("${ChatGptProtocol.RESOURCE}/models", accessLocked())
        diagnostic("model catalog received; parsing")
        models = ChatGptModels.parse(raw)
        diagnostic("model catalog parsed: ${models.size} listed models")
    }
    suspend fun refreshModels() {
        if (catalogLoading) return
        catalogLoading = true
        try { withContext(Dispatchers.IO) {
            mutex.withLock {
                try { paused = false; retryAfter = 0; loadModelsLocked(); message = "已更新账号模型。" }
                catch (e: Exception) { if (e is CancellationException) throw e; failure(e) }
            }
        } } finally { catalogLoading = false }
    }
    suspend fun infer(model: String, body: (ChatGptModel) -> String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                check(!paused && clock() >= retryAfter) { "ChatGPT 连接暂不可用，请查看设置。" }
                val token = accessLocked()
                if (models.isEmpty()) loadModelsLocked()
                val selected = models.singleOrNull { it.slug == model } ?: error("请选择当前账号可用的模型。")
                val raw = transport.stream("${ChatGptProtocol.RESOURCE}/responses", token, body(selected))
                ensureActive(); DirectorContract.parse(raw); raw
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e); throw e }
        }
    }
    suspend fun disconnect() {
        cancelSignIn()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var confirmed = record.refreshToken.isBlank()
                try {
                    if (!confirmed) {
                        val discovery = Json.parseToJsonElement(transport.get("${ChatGptProtocol.ISSUER}/.well-known/openid-configuration")).jsonObject
                        val endpoint = discovery.getValue("revocation_endpoint").jsonPrimitive.content
                        require(endpoint.startsWith("${ChatGptProtocol.ISSUER}/"))
                        transport.form(endpoint, ChatGptProtocol.form(linkedMapOf("token" to record.refreshToken,
                            "token_type_hint" to "refresh_token", "client_id" to record.clientId)))
                        confirmed = true
                    }
                } catch (_: Exception) { /* Clear locally even when remote revocation is unavailable. */ }
                save(record.withoutTokens()); models = emptyList(); paused = true
                message = if (confirmed) "已断开连接。注册保留，可重新登录。" else "已在本机断开；远程撤销未确认，请在 ChatGPT 设置中断开沿途。"
            }
        }
    }

    /** Uses the same verified registration and refresh rules, but never holds the auth lock during research.
     * A search capability rejection must not pause the already accepted narration connection. */
    suspend fun research(model: String, body: (ChatGptModel) -> String): String = withContext(Dispatchers.IO) {
        val (token, selected, stamp) = mutex.withLock {
            check(!paused && clock() >= retryAfter) { "ChatGPT 连接暂不可用，请查看设置。" }
            val access = accessLocked()
            if (models.isEmpty()) loadModelsLocked()
            Triple(access, models.singleOrNull { it.slug == model } ?: error("请选择当前账号可用的模型。"), generation)
        }
        try {
            val raw = transport.research("${ChatGptProtocol.RESOURCE}/responses", token, body(selected))
            ensureActive()
            check(generation == stamp && record.planEnabled) { "研究期间账号连接已改变。" }
            raw
        } catch (e: CancellationException) { throw e }
        catch (e: ChatGptFailure) {
            diagnostic("research rejected status=${e.status} code=${e.code} request_id=${e.requestId} shape=${e.shape}")
            throw e
        }
    }
}
