package com.kitt.reader

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class ChatGptProtocolTest {
    private fun query(url: String) = URL(url).query.split('&').associate {
        val p = it.split('=', limit = 2); URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
    }
    @Test fun freshPkceNonceStateAndDynamicRegistration() {
        val first = ChatGptAttempt("http://127.0.0.1:54321/auth/callback", "urn:uuid:host", ChatGptRecord())
        val second = ChatGptAttempt(first.redirectUri, first.hostId, ChatGptRecord())
        val params = query(first.url)
        assertEquals(ChatGptProtocol.DYNAMIC, params["client_id"])
        assertEquals("沿途", params["agent_name_hint"])
        assertEquals(ChatGptProtocol.SCOPES, params["scope"])
        assertEquals(ChatGptProtocol.RESOURCE, params["resource"])
        assertEquals(first.redirectUri, params["redirect_uri"])
        assertEquals("S256", params["code_challenge_method"])
        assertNotEquals(first.state, second.state); assertNotEquals(first.nonce, second.nonce); assertNotEquals(first.verifier, second.verifier)
        assertEquals(43, first.verifier.length)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            ChatGptProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val (code, client) = first.callback("/auth/callback?code=code&state=${first.state}&client_id=oaiapp_kitt")
        val form = query("https://example.test/?" + first.exchange(code, client))
        assertEquals("oaiapp_kitt", form["client_id"]); assertEquals(first.redirectUri, form["redirect_uri"])
        assertEquals(first.verifier, form["code_verifier"]); assertFalse(form.containsKey("client_secret"))
        assertTrue(runCatching { first.callback("/auth/callback?code=code&state=${first.state}&client_id=oaiapp_kitt") }.isFailure)
    }
    @Test fun returningRegistrationUsesSavedIdentityHintsAndNeverDynamic() {
        val saved = ChatGptRecord(clientId = "issued", email = "user@example.test", idToken = "retained-sensitive")
        val attempt = ChatGptAttempt("http://127.0.0.1:1234/auth/callback", "host", saved)
        val p = query(attempt.url)
        assertEquals("issued", p["client_id"]); assertEquals("retained-sensitive", p["id_token_hint"])
        assertEquals(saved.email, p["login_hint"]); assertFalse(p.containsKey("agent_name_hint")); assertFalse(p.containsKey("prompt"))
        assertEquals("issued", attempt.callback("/auth/callback?state=${attempt.state}&code=abc").second)
        assertEquals("consent", query(ChatGptAttempt(attempt.redirectUri, "host", saved, consent = true).url)["prompt"])
        assertFalse(attempt.toString().contains("retained-sensitive"))
    }
    @Test fun rejectMissingMismatchedStateClientErrorsAndDuplicateParameters() {
        fun bad(build: (ChatGptAttempt) -> String, returning: Boolean = false) {
            val a = ChatGptAttempt("http://127.0.0.1:1/auth/callback", "host", ChatGptRecord(clientId = if (returning) "issued" else ""))
            assertTrue(runCatching { a.callback(build(a)) }.isFailure)
        }
        bad({ "/auth/callback?state=wrong&code=x&client_id=issued" })
        bad({ "/auth/callback?state=${it.state}&code=x" })
        bad({ "/auth/callback?state=${it.state}&code=x&client_id=dynamic_agent_client" })
        bad({ "/auth/callback?state=${it.state}&code=x&client_id=other" }, true)
        bad({ "/auth/callback?state=${it.state}&error=access_denied" })
        bad({ "/auth/callback?state=${it.state}&state=${it.state}&code=x&client_id=issued" })
        bad({ "/callback?state=${it.state}&code=x&client_id=issued" })
        bad({ "http://evil.test/auth/callback?state=${it.state}&code=x&client_id=issued" })
    }
    @Test fun realLoopbackListenerBindsBeforeCallbackAndDoesNotEchoCredentials() = runBlocking {
        ChatGptLoopback().use { listener ->
            assertTrue(listener.redirectUri.startsWith("http://127.0.0.1:")); assertTrue(listener.redirectUri.endsWith("/auth/callback"))
            val attempt = ChatGptAttempt(listener.redirectUri, "host", ChatGptRecord())
            val receive = async(Dispatchers.IO) { listener.receive(attempt) }
            val response = withContext(Dispatchers.IO) {
                (URL(listener.redirectUri + "?state=${attempt.state}&code=sensitive-code&client_id=issued").openConnection() as HttpURLConnection).let { c ->
                    try { assertEquals(200, c.responseCode); c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
                }
            }
            assertFalse(response.contains("sensitive-code")); assertEquals("sensitive-code" to "issued", receive.await())
        }
    }
    @Test fun validatesJwksSignatureIssuerAudienceExpiryNonceAndSubject() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = key.public as RSAPublicKey
        fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        fun integer(value: java.math.BigInteger) = b64(value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
        val jwks = """{"keys":[{"kty":"RSA","kid":"test","n":"${integer(publicKey.modulus)}","e":"${integer(publicKey.publicExponent)}"}]}"""
        fun token(issuer: String = ChatGptProtocol.ISSUER, aud: String = "issued", exp: Long = 2000, nonce: String = "nonce", alg: String = "RS256", sub: String = "subject"): String {
            val header = b64("""{"alg":"$alg","kid":"test"}""".toByteArray())
            val payload = b64("""{"iss":"$issuer","aud":"$aud","exp":$exp,"nonce":"$nonce","sub":"$sub","email":"u@example.test"}""".toByteArray())
            val unsigned = "$header.$payload"
            return unsigned + "." + b64(Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(unsigned.toByteArray()); sign() })
        }
        val identity = ChatGptIdToken.verify(token(), jwks, "issued", "nonce", 1000000)
        assertEquals("subject", identity.subject)
        listOf(token(issuer = "https://evil.test"), token(aud = "other"), token(exp = 1000), token(nonce = "wrong"), token(alg = "none"), token(sub = ""),
            token().substringBeforeLast('.') + "." + b64(ByteArray(256))).forEach {
            assertTrue(runCatching { ChatGptIdToken.verify(it, jwks, "issued", "nonce", 1000000) }.isFailure)
        }
    }
    @Test fun modelCatalogUsesOnlyVisibleSlugsAndExplicitCapabilityMetadata() {
        val models = ChatGptModels.parse("""{"models":[{"slug":"a","display_name":"First","visibility":"list","supported_reasoning_levels":[{"effort":"low"}]},{"slug":"hidden","visibility":"hide"},{"slug":"b","visibility":"list","supported_reasoning_levels":null},{"slug":"not-listed"}]}""")
        assertEquals(listOf("a", "b"), models.map { it.slug }); assertEquals("First", models.first().displayName)
        assertEquals(listOf("low"), models.first().efforts); assertTrue(models.last().efforts.isEmpty())
    }
    private val result = DirectorResult(Action.SILENT).json()
    private fun completed() = "data: " + """{"type":"response.completed","response":{"status":"completed","output":[{"content":[{"type":"output_text","text":${JsonPrimitive(result)}}]}]}}""" + "\n\n"
    @Test fun sseOnlyCompletedValidatedDirectorTextSucceeds() {
        val stream = ": comment\n\nevent: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n" + completed()
        assertEquals(result, ChatGptStream.read(stream.reader().buffered()))
        assertTrue(runCatching { ChatGptStream.read(completed().replace(result.replace("\"", "\\\""), "bad").reader().buffered()) }.isFailure)
    }
    @Test fun sseRejectsFailedIncompleteInterruptedAndDoneWithoutCompletion() {
        listOf("data: {\"type\":\"response.incomplete\"}\n\n", "data: [DONE]\n\n", "data: {\"type\":\"response.output_text.delta\",\"delta\":\"${result.replace("\"", "\\\"")}\"}\n\n", "data: {\"type\":\"response.completed\"}").forEach {
            assertTrue(runCatching { ChatGptStream.read(it.reader().buffered()) }.isFailure)
        }
        val failed = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n"
        val error = runCatching { ChatGptStream.read(failed.reader().buffered(), "req_id") }.exceptionOrNull() as ChatGptFailure
        assertEquals("subscription_sharing_usage_limit_exceeded", error.code); assertTrue(error.pausesRequests)
    }
    @Test fun streamedTextIsValidatedOnlyAfterCompletionEvenWhenFinalSnapshotIsEmpty() {
        val delta = "data: {\"type\":\"response.output_text.delta\",\"delta\":${JsonPrimitive(result)}}\n\n"
        val terminal = "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n"
        assertEquals(result, ChatGptStream.read((delta + terminal).reader().buffered()))
        assertTrue(runCatching { ChatGptStream.read(delta.reader().buffered()) }.isFailure)
        val done = "data: {\"type\":\"response.output_text.done\",\"text\":${JsonPrimitive(result)}}\n\n"
        assertEquals(result, ChatGptStream.read((done + terminal).reader().buffered()))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatGptAccountTest {
    private fun store(): SettingsStore {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SettingsStore(RuntimeEnvironment.getApplication(), object : SecretCipher {
            override fun encrypt(text: String): String {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
                return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()))
            }
            override fun decrypt(text: String): String {
                val data = Base64.getDecoder().decode(text)
                return Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
                }.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
            }
        })
    }
    private fun record(expiry: Long = 2000000, scopes: Set<String> = setOf(ChatGptProtocol.DIRECT_SCOPE)) = ChatGptRecord(
        "urn:uuid:installation", "issued-kitt", "subject", "u@example.test", "own-access", "own-refresh", "own-id-token", scopes, expiry)
    private class Transport : ChatGptTransport {
        var refreshes = 0; var streams = 0; var failure: ChatGptFailure? = null; var body = ""; var bearer = ""; var formBody = ""
        override fun get(url: String, bearer: String): String = if (url.endsWith("/models"))
            """{"models":[{"slug":"account-model","display_name":"Account model","visibility":"list"}]}""" else
            """{"revocation_endpoint":"https://auth.openai.com/api/accounts/oauth/revoke"}"""
        override fun form(url: String, body: String): String {
            formBody = body; failure?.let { throw it }
            if (url.endsWith("/revoke")) return ""
            refreshes++
            return """{"access_token":"rotated-access","refresh_token":"rotated-refresh","token_type":"Bearer","expires_in":3600,"earliest_refresh_at":1001,"scope":"chatgpt.tokens.use.direct"}"""
        }
        override suspend fun stream(url: String, bearer: String, body: String): String {
            assertEquals("https://api.openai.com/v1/responses", url)
            streams++; this.body = body; this.bearer = bearer; failure?.let { throw it }
            return DirectorResult(Action.SILENT).json()
        }
    }
    @Test fun encryptedRecordKeepsAllIdentityAndTokenValuesOutOfPreferencesAndDiagnostics() {
        val store = store(); val original = record(); store.saveChatGpt(original.encode())
        assertEquals(original, ChatGptRecord.decode(store.readChatGpt()!!))
        val raw = RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString()
        listOf(original.hostId, original.clientId, original.subject, original.email, original.accessToken, original.refreshToken, original.idToken).forEach {
            assertFalse(raw.contains(it)); assertFalse(original.toString().contains(it))
        }
        val failure = ChatGptFailure.from(401, """{"detail":"own-access own-refresh own-id-token"}""", "req_123")
        assertEquals("detail", failure.shape); assertEquals("req_123", failure.requestId)
        assertFalse(failure.toString().contains("own-access")); assertFalse(failure.message!!.contains("own-refresh"))
    }
    @Test fun identityOnlyGrantCannotInvokeModelsOrResponsesOrPaidFallback() = runTest {
        val store = store(); store.saveChatGpt(record(scopes = setOf("openid")).encode())
        val transport = Transport(); val account = ChatGptAccount(store, transport) { 1000000 }
        assertTrue(account.record.connected); assertFalse(account.record.planEnabled)
        assertTrue(runCatching { ChatGptProvider(account, ProviderConfig(ProviderKind.CHATGPT, apiKey = "paid-key")).direct(DirectorRequest(contextCard = "")) }.isFailure)
        assertEquals(0, transport.streams); assertEquals(0, transport.refreshes)
    }
    @Test fun refreshRotationSerializedAndPersistedWithIssuedClientAndResource() = runTest {
        val store = store(); store.saveChatGpt(record(expiry = 1000100).encode())
        val transport = Transport(); val account = ChatGptAccount(store, transport) { 1000000 }
        val provider = ChatGptProvider(account, ProviderConfig(ProviderKind.CHATGPT, model = "account-model", apiKey = "must-not-use", effort = "high"))
        coroutineScope { listOf(async { provider.direct(DirectorRequest(contextCard = "现场")) }, async { provider.direct(DirectorRequest(contextCard = "现场")) }).awaitAll() }
        assertEquals(1, transport.refreshes); assertEquals(2, transport.streams)
        assertEquals("rotated-refresh", ChatGptRecord.decode(store.readChatGpt()!!).refreshToken)
        assertEquals("rotated-access", transport.bearer)
        assertTrue(transport.formBody.contains("client_id=issued-kitt")); assertTrue(transport.formBody.contains("grant_type=refresh_token"))
        assertFalse(transport.formBody.contains("scope=")); assertFalse(transport.formBody.contains(ChatGptProtocol.DYNAMIC))
        val payload = Json.parseToJsonElement(transport.body).jsonObject
        assertFalse(payload.getValue("store").jsonPrimitive.boolean); assertTrue(payload.getValue("stream").jsonPrimitive.boolean)
        assertTrue(payload.getValue("input") is JsonArray); assertTrue(payload.containsKey("instructions"))
        assertFalse(payload.containsKey("reasoning")); assertFalse(transport.body.contains("must-not-use"))
        assertTrue(payload.keys.none { it in setOf("previous_response_id", "temperature", "max_output_tokens", "prompt", "metadata", "background") })
    }
    @Test fun earliestRefreshHonoredAndTerminalRefreshClearsOnlyTokens() = runTest {
        val store = store(); store.saveChatGpt(record(expiry = 1000100).copy(earliestRefreshAt = 1000050).encode())
        val transport = Transport(); val account = ChatGptAccount(store, transport) { 1000000 }
        account.infer("account-model") { "{}" }; assertEquals(0, transport.refreshes)
        store.saveChatGpt(record(expiry = 999999).encode())
        val expired = ChatGptAccount(store, transport) { 1000000 }; transport.failure = ChatGptFailure(400, "invalid_grant")
        assertTrue(runCatching { expired.infer("account-model") { "{}" } }.isFailure)
        assertFalse(expired.record.connected); assertEquals("issued-kitt", expired.record.clientId)
        assertEquals("urn:uuid:installation", expired.record.hostId); assertEquals("subject", expired.record.subject)
    }
    @Test fun transientFailurePreservesCredentialsAndAdmissionOrLimitStopsNewRequests() = runTest {
        for (status in listOf(401, 403, 429, 503)) {
            val store = store(); store.saveChatGpt(record().encode())
            val transport = Transport(); transport.failure = ChatGptFailure(status)
            val account = ChatGptAccount(store, transport) { 1000000 }
            assertTrue(runCatching { account.infer("account-model") { "{}" } }.isFailure)
            assertEquals(record(), account.record)
            assertTrue(runCatching { account.infer("account-model") { "{}" } }.isFailure)
            assertEquals(1, transport.streams)
        }
    }
    @Test fun rejectedChecksDoNotExtendTransientNetworkBackoffForever() = runTest {
        var time = 1000000L
        val store = store(); store.saveChatGpt(record().encode())
        val transport = Transport(); transport.failure = ChatGptFailure(503)
        val account = ChatGptAccount(store, transport) { time }
        assertTrue(runCatching { account.infer("account-model") { "{}" } }.isFailure)
        transport.failure = null
        repeat(5) {
            time += 10000
            assertTrue(runCatching { account.infer("account-model") { "{}" } }.isFailure)
            assertEquals(503, account.lastFailure?.status)
            assertEquals(1, transport.streams)
        }
        time += 10000
        assertEquals(DirectorResult(Action.SILENT).json(), account.infer("account-model") { "{}" })
        assertEquals(2, transport.streams)
    }
    @Test fun signOutRevokesAndRetainsRegistrationWithoutHintsEvenIfRemoteFails() = runTest {
        for (fails in listOf(false, true)) {
            val store = store(); store.saveChatGpt(record().encode())
            val transport = Transport(); if (fails) transport.failure = ChatGptFailure(503)
            val account = ChatGptAccount(store, transport); account.disconnect()
            assertFalse(account.record.connected); assertEquals("", account.record.idToken)
            assertEquals("issued-kitt", account.record.clientId); assertEquals("subject", account.record.subject)
            assertTrue(transport.formBody.contains("token=own-refresh")); assertTrue(transport.formBody.contains("token_type_hint=refresh_token"))
            assertEquals(fails, account.message.contains("未确认"))
        }
    }
    @Test fun compatibleAndOpenaiKeyProvidersRemainSeparateFromChatGpt() = runTest {
        for (kind in listOf(ProviderKind.OPENAI, ProviderKind.COMPATIBLE)) {
            val provider = ApiProvider(ProviderConfig(kind, model = "test", apiKey = "test-key"), JsonTransport { url, key, _ ->
                assertEquals("test-key", key)
                val text = JsonPrimitive(DirectorResult(Action.SILENT).json())
                if (kind == ProviderKind.OPENAI) { assertTrue(url.endsWith("/responses")); """{"status":"completed","output":[{"content":[{"type":"output_text","text":$text}]}]}""" }
                else { assertTrue(url.endsWith("/chat/completions")); """{"choices":[{"finish_reason":"stop","message":{"content":$text}}]}""" }
            })
            assertEquals(Action.SILENT, DirectorContract.parse(provider.direct(DirectorRequest(contextCard = ""))).action)
        }
        assertTrue(runCatching { ApiProvider(ProviderConfig(ProviderKind.CHATGPT, apiKey = "paid-key")).direct(DirectorRequest(contextCard = "")) }.isFailure)
    }
    @Test fun researchRejectionPreservesAcceptedNarrationAuthorizationAndHasNoPaidFallback() = runTest {
        val store = store(); store.saveChatGpt(record().encode())
        val delegate = Transport(); var searches = 0
        val transport = object : ChatGptTransport by delegate {
            override suspend fun research(url: String, bearer: String, body: String): String {
                searches++; assertEquals("own-access", bearer)
                val payload = Json.parseToJsonElement(body).jsonObject
                assertEquals("required", payload["tool_choice"]!!.jsonPrimitive.content)
                throw ChatGptFailure(400, "unsupported_tool", "req_test", "error_object")
            }
        }
        val events = mutableListOf<String>(); val account = ChatGptAccount(store, transport, diagnostic = { events += it }) { 1000000 }
        val research = ChatGptLocalResearch(account, ProviderConfig(ProviderKind.CHATGPT, model = "account-model", apiKey = "must-not-use"))
        repeat(5) { assertTrue(runCatching { research.research(AreaIdentity("测试市", "测试区", "测试镇"), 1) }.exceptionOrNull() is ResearchUnavailable) }
        assertEquals(1, searches); assertEquals(record(), account.record)
        assertEquals(DirectorResult(Action.SILENT).json(), account.infer("account-model") { "{}" })
        assertEquals(1, delegate.streams)
        assertTrue(events.any { "research rejected status=400 code=unsupported_tool" in it })
        assertFalse(events.joinToString().contains("own-access"))
    }
    @Test fun researchCannotBlockActiveDirectorAndDisconnectedResearchCannotReturn() = runBlocking {
        val store = store(); store.saveChatGpt(record().encode())
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val transport = object : ChatGptTransport by Transport() {
            override suspend fun research(url: String, bearer: String, body: String): String {
                entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); return "{}"
            }
        }
        val account = ChatGptAccount(store, transport) { 1000000 }
        val search = async { runCatching { account.research("account-model") { "{}" } } }
        try {
            withContext(Dispatchers.IO) { check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(DirectorResult(Action.SILENT).json(), withTimeout(2000) { account.infer("account-model") { "{}" } })
            account.disconnect(); release.countDown(); assertTrue(withTimeout(5000) { search.await() }.isFailure)
        } finally { release.countDown(); search.cancelAndJoin() }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancelAuthorizationClosesLoopbackAndEndsProtectionWithoutReplacingRegistration() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = store(); store.saveChatGpt(record().encode())
            val protection = mutableListOf<Boolean>(); val opened = CompletableDeferred<String>()
            val account = ChatGptAccount(store, Transport(), keepAlive = { protection.add(it) }) { 1000000 }
            account.signIn(jobs) { opened.complete(it) }
            val url = withTimeout(10000) { opened.await() }; assertTrue(url.startsWith(ChatGptProtocol.AUTHORIZE))
            account.cancelSignIn()
            withTimeout(10000) { jobs.coroutineContext[Job]!!.children.toList().joinAll() }
            assertFalse(account.busy); assertEquals(record(), account.record)
            assertEquals(listOf(true, false), protection)
        } finally { jobs.cancel(); Dispatchers.resetMain() }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun completeSignInValidatesBeforeReplacingAccountAndPreservesIdentityOnlyGrant() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = store()
            val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val publicKey = key.public as RSAPublicKey
            fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            fun integer(value: java.math.BigInteger) = b64(value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
            var nonce = ""; var mismatch = false; var browserClient = ""
            val transport = object : ChatGptTransport {
                override fun get(url: String, bearer: String) = """{"keys":[{"kty":"RSA","kid":"test","n":"${integer(publicKey.modulus)}","e":"${integer(publicKey.publicExponent)}"}]}"""
                override fun form(url: String, body: String): String {
                    assertTrue(body.contains("client_id=issued-kitt")); assertFalse(body.contains("client_secret"))
                    val unsigned = b64("""{"alg":"RS256","kid":"test"}""".toByteArray()) + "." +
                        b64("""{"iss":"https://auth.openai.com","aud":"issued-kitt","exp":2000,"sub":"${if (mismatch) "other" else "subject"}","nonce":"$nonce","email":"u@example.test"}""".toByteArray())
                    val token = unsigned + "." + b64(Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(unsigned.toByteArray()); sign() })
                    return """{"access_token":"own-access","id_token":"$token","token_type":"Bearer","expires_in":3600,"scope":"openid profile email"}"""
                }
                override suspend fun stream(url: String, bearer: String, body: String): String = error("Identity-only grant cannot infer")
            }
            val events = mutableListOf<String>(); val protection = mutableListOf<Boolean>()
            val account = ChatGptAccount(store, transport, keepAlive = { protection.add(it) }, diagnostic = { events.add(it) }) { 1000000 }
            suspend fun signIn() {
                account.signIn(jobs) { url ->
                    val query = URL(url).query.split('&').associate {
                        val pair = it.split('=', limit = 2); pair[0] to URLDecoder.decode(pair[1], "UTF-8")
                    }
                    nonce = query.getValue("nonce"); browserClient = query.getValue("client_id")
                    jobs.launch(Dispatchers.IO) {
                        (URL(query.getValue("redirect_uri") + "?state=${query.getValue("state")}&code=auth-code&client_id=issued-kitt").openConnection() as HttpURLConnection).let { c ->
                            try { assertEquals(200, c.responseCode); c.inputStream.close() } finally { c.disconnect() }
                        }
                    }
                }
                withTimeout(10000) { while (account.busy) delay(10) }
            }
            signIn()
            assertEquals(ChatGptProtocol.DYNAMIC, browserClient)
            assertTrue(account.record.connected); assertFalse(account.record.planEnabled)
            assertEquals("subject", account.record.subject); assertEquals("issued-kitt", account.record.clientId)
            assertTrue(account.record.hostId.startsWith("urn:uuid:")); assertEquals(account.record, ChatGptRecord.decode(store.readChatGpt()!!))
            val original = account.record; mismatch = true; signIn()
            assertEquals("issued-kitt", browserClient); assertEquals(original, account.record)
            assertTrue(events.contains("loopback listener bound")); assertTrue(events.contains("verified identity and granted scopes saved"))
            val diagnostics = events.joinToString(" ")
            listOf("own-access", original.idToken, "auth-code", nonce, original.hostId).forEach { assertFalse(diagnostics.contains(it)) }
            assertEquals(listOf(true, false, true, false), protection)
        } finally { jobs.cancel(); Dispatchers.resetMain() }
    }
}
