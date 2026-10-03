package com.kitt.reader

import kotlinx.serialization.json.*
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

object ChatGptProtocol {
    const val ISSUER = "https://auth.openai.com"
    const val AUTHORIZE = "$ISSUER/api/accounts/authorize"
    const val TOKEN = "$ISSUER/api/accounts/oauth/token"
    const val RESOURCE = "https://api.openai.com/v1"
    const val DIRECT_SCOPE = "chatgpt.tokens.use.direct"
    const val SCOPES = "openid profile email offline_access resource.invoke $DIRECT_SCOPE"
    const val DYNAMIC = "dynamic_agent_client"
    const val AUTH_TIMEOUT_MS = 150000
    fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

/** Never expose the URL, PKCE verifier or retained ID-token hint to diagnostics. */
class ChatGptAttempt(
    val redirectUri: String, val hostId: String, val selected: ChatGptRecord,
    val consent: Boolean = false, val state: String = ChatGptProtocol.random(),
    val nonce: String = ChatGptProtocol.random(), val verifier: String = ChatGptProtocol.random()
) {
    private var consumed = false
    val url: String get() {
        val params = linkedMapOf("client_id" to selected.clientId.ifBlank { ChatGptProtocol.DYNAMIC },
            "ext_agent_host_id" to hostId, "response_type" to "code", "redirect_uri" to redirectUri,
            "scope" to ChatGptProtocol.SCOPES, "resource" to ChatGptProtocol.RESOURCE,
            "state" to state, "nonce" to nonce, "code_challenge_method" to "S256",
            "code_challenge" to ChatGptProtocol.challenge(verifier))
        if (selected.clientId.isBlank()) params["agent_name_hint"] = "沿途"
        else {
            if (selected.idToken.isNotBlank()) params["id_token_hint"] = selected.idToken
            if (selected.email.isNotBlank()) params["login_hint"] = selected.email
        }
        // Deployment of force_reconsent is not confirmed; official prompt=consent is supported.
        if (consent) params["prompt"] = "consent"
        return ChatGptProtocol.AUTHORIZE + "?" + ChatGptProtocol.form(params)
    }
    @Synchronized fun callback(target: String): Pair<String, String> {
        check(!consumed) { "授权回调已使用。" }
        val uri = URI(target)
        require(uri.scheme == null && uri.rawAuthority == null && uri.path == "/auth/callback" && uri.fragment == null) { "授权回调地址不符。" }
        val params = linkedMapOf<String, String>()
        uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.forEach { part ->
            val pair = part.split('=', limit = 2)
            val name = URLDecoder.decode(pair[0], "UTF-8")
            require(!params.containsKey(name)) { "授权回调参数重复。" }
            params[name] = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        require(ChatGptProtocol.same(params["state"].orEmpty(), state)) { "授权状态不符，请重新连接。" }
        consumed = true
        require(!params.containsKey("error")) { "授权未完成，请重连或选择其他 Provider。" }
        val client = params["client_id"] ?: selected.clientId
        require(client.isNotBlank() && client != ChatGptProtocol.DYNAMIC) { "注册未返回 client ID，请重新连接。" }
        require(selected.clientId.isBlank() || client == selected.clientId) { "注册身份不符，请重新连接。" }
        val code = params["code"].orEmpty()
        require(code.isNotBlank()) { "授权未返回 code，请重新连接。" }
        return code to client
    }
    fun exchange(code: String, client: String) = ChatGptProtocol.form(linkedMapOf(
        "grant_type" to "authorization_code", "client_id" to client, "code" to code,
        "code_verifier" to verifier, "redirect_uri" to redirectUri, "resource" to ChatGptProtocol.RESOURCE))
    override fun toString() = "ChatGptAttempt(redacted)"
}

/** Bound exclusively to IPv4 loopback; exact official path, ephemeral port, no Android deep link. */
class ChatGptLoopback : AutoCloseable {
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = ChatGptProtocol.AUTH_TIMEOUT_MS }
    val redirectUri = "http://127.0.0.1:${server.localPort}/auth/callback"
    fun receive(attempt: ChatGptAttempt): Pair<String, String> {
        val deadline = System.currentTimeMillis() + ChatGptProtocol.AUTH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            server.soTimeout = (deadline - System.currentTimeMillis()).coerceIn(1, ChatGptProtocol.AUTH_TIMEOUT_MS.toLong()).toInt()
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream()
                val bytes = ArrayList<Byte>()
                while (bytes.size < 8192) {
                    val value = input.read(); require(value >= 0) { "授权回调中断。" }
                    bytes.add(value.toByte())
                    if (bytes.size >= 4 && bytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
                }
                require(bytes.size < 8192) { "授权回调过大。" }
                val line = bytes.toByteArray().toString(Charsets.US_ASCII).substringBefore("\r\n").split(' ')
                val target = line.getOrNull(1).orEmpty()
                if (line.firstOrNull() != "GET" || target.substringBefore('?') != "/auth/callback") {
                    reply(socket, "404 Not Found", "Not found"); return@use
                }
                val result = runCatching { attempt.callback(target) }
                reply(socket, if (result.isSuccess) "200 OK" else "400 Bad Request",
                    if (result.isSuccess) "Authorization received. Return to Yantu to finish connecting." else "Authorization rejected. Return to Yantu and reconnect.")
                return result.getOrThrow()
            }
        }
        error("授权超时，请重新连接。")
    }
    private fun reply(socket: java.net.Socket, status: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
    }
    override fun close() = server.close()
}

data class ChatGptIdentity(val subject: String, val email: String)

/** RS256 is the only algorithm in OpenAI production discovery. Keys come only from its trusted JWKS. */
object ChatGptIdToken {
    fun verify(token: String, jwks: String, client: String, nonce: String?, nowMs: Long): ChatGptIdentity {
        require(token.length <= 32000)
        val parts = token.split('.'); require(parts.size == 3)
        fun obj(part: String) = Json.parseToJsonElement(Base64.getUrlDecoder().decode(part).toString(Charsets.UTF_8)).jsonObject
        val header = obj(parts[0]); require(header["alg"]?.jsonPrimitive?.content == "RS256")
        val kid = header.getValue("kid").jsonPrimitive.content
        val key = Json.parseToJsonElement(jwks).jsonObject.getValue("keys").jsonArray.map { it.jsonObject }.single {
            it["kid"]?.jsonPrimitive?.content == kid && it["kty"]?.jsonPrimitive?.content == "RSA" &&
                (it["use"] == null || it["use"]?.jsonPrimitive?.content == "sig") &&
                (it["alg"] == null || it["alg"]?.jsonPrimitive?.content == "RS256")
        }
        fun number(name: String) = BigInteger(1, Base64.getUrlDecoder().decode(key.getValue(name).jsonPrimitive.content))
        val modulus = number("n"); require(modulus.bitLength() >= 2048)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, number("e")))
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            verify(Base64.getUrlDecoder().decode(parts[2]))
        }) { "ID token 签名不符。" }
        val claims = obj(parts[1])
        require(claims["iss"]?.jsonPrimitive?.content == ChatGptProtocol.ISSUER)
        val aud = claims.getValue("aud")
        val audiences = if (aud is JsonArray) aud.map { it.jsonPrimitive.content } else listOf(aud.jsonPrimitive.content)
        require(client in audiences && (audiences.size == 1 || claims["azp"]?.jsonPrimitive?.content == client))
        claims["azp"]?.let { require(it.jsonPrimitive.content == client) }
        require(claims.getValue("exp").jsonPrimitive.long > nowMs / 1000)
        claims["nbf"]?.let { require(it.jsonPrimitive.long <= nowMs / 1000) }
        if (nonce != null) require(ChatGptProtocol.same(claims["nonce"]?.jsonPrimitive?.content.orEmpty(), nonce))
        val subject = claims.getValue("sub").jsonPrimitive.content; require(subject.isNotBlank())
        return ChatGptIdentity(subject, claims["email"]?.jsonPrimitive?.content.orEmpty())
    }
}
