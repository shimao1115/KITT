package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ResearchTransportProbeTest {
    private val area = AreaIdentity("测试市", "测试区", province = "测试省")
    private val payload = LocalResearchContract.payload(area, ProviderConfig(ProviderKind.CHATGPT, model = "saved-model"), stream = false)
    private val dossierText = """{"orientation":"测试背景","sources":[{"id":"s1","title":"测试机构","url":"https://example.test/fact","kind":"INSTITUTIONAL"}],"facts":[{"title":"测试对象","summary":"有来源的测试事实","family":"OTHER","salience":4,"confidence":"high","source_ids":["s1"],"designation":""}],"gaps":[]}"""
    private fun envelope(text: String = dossierText, search: Boolean = true, provenance: Boolean = true) = buildJsonObject {
        put("status", "completed"); put("id", "redact_response_id"); put("output", buildJsonArray {
            if (search) add(buildJsonObject {
                put("type", "web_search_call"); put("status", "completed")
                put("action", buildJsonObject { put("sources", buildJsonArray {
                    if (provenance) add(buildJsonObject { put("url", "https://example.test/fact") })
                }) })
            })
            add(buildJsonObject { put("type", "message"); put("content", buildJsonArray {
                add(buildJsonObject { put("type", "output_text"); put("text", text) })
            }) })
        })
    }.toString()

    @Test fun streamIsTheOnlyPayloadDifferenceAndNoBenchmarkNamesAreSeeded() {
        val production = LocalResearchContract.payload(area, ProviderConfig(ProviderKind.CHATGPT, model = "saved-model"), stream = true)
        assertEquals(production - "stream", payload - "stream")
        listOf("杨升庵", "杨慎", "桂湖", "宝光寺", "新繁东湖").forEach { assertFalse(it in payload.toString()) }
        val client = ResearchTransportProbe.defaultClient()
        assertEquals(90000, client.callTimeoutMillis)
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects); assertFalse(client.retryOnConnectionFailure)
    }
    @Test fun boundedReaderChecksActualBytesAndDoesNotUseSse() {
        val bytes = "{\"中文\":true}".toByteArray()
        var first = 0
        assertEquals("{\"中文\":true}", ResearchTransportProbe.boundedJson(ByteArrayInputStream(bytes), bytes.size) { first++ })
        assertEquals(1, first)
        assertTrue(runCatching { ResearchTransportProbe.boundedJson(ByteArrayInputStream(bytes), bytes.size - 1) }.isFailure)
        assertEquals("sse_completion_timeout", probeFailureReason(IllegalArgumentException("ChatGPT stream timed out")))
        assertEquals("response_reader_or_framing_failure", probeFailureReason(IllegalArgumentException("private details")))
    }
    @Test fun searchSuccessIsPreservedAcrossEachParserFailureAndShapeIsRedacted() {
        val ready = inspect(envelope(), area, 1)
        assertEquals(1, ready.dossier!!.facts.size)
        assertEquals("READY", ready.report["classification"]!!.jsonPrimitive.content)
        for ((raw, stage) in listOf(envelope(provenance = false) to "source_url_extraction",
            envelope(text = "not structured JSON secret_token") to "structured_output",
            envelope(text = dossierText.replace("source_ids\":[\"s1", "source_ids\":[\"missing")) to "local_dossier_contract")) {
            val inspected = inspect(raw, area, 1)
            assertNull(inspected.dossier)
            assertTrue(inspected.report["completed_web_search_call"]!!.jsonPrimitive.boolean)
            assertEquals("SEARCH_COMPLETED_PARSE_FAILURE", inspected.report["classification"]!!.jsonPrimitive.content)
            assertEquals(stage, inspected.report["parse_stage"]!!.jsonPrimitive.content)
            val shape = inspected.report["response_shape"].toString()
            listOf("secret_token", "redact_response_id", "https://example.test", "测试背景").forEach { assertFalse(it in shape) }
        }
        assertEquals("RESPONSE_UNRESOLVED", inspect(envelope(search = false), area, 1).report["classification"]!!.jsonPrimitive.content)
    }
    private fun client(status: Int, body: String, capture: (Request) -> Unit = {}) = ResearchTransportProbe.defaultClient().newBuilder()
        .addInterceptor { chain -> capture(chain.request()); Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(status).message("test").header("x-request-id", "req_test")
            .header("Content-Type", "application/json").body(body.toResponseBody()).build() }.build()

    @Test fun jsonProbeUsesSavedBearerAndRetainsExactRejectionWithoutLoggingCredentials() = runBlocking {
        val probe = ResearchTransportProbe(client(403, """{"error":{"code":"unsupported_tool","message":"saved-token"}}""") {
            assertEquals("Bearer saved-token", it.header("Authorization"))
            assertEquals("application/json", it.header("Accept"))
        })
        val result = probe.run("https://example.test/responses", "saved-token", payload, area, 1)
        assertEquals(403, result.report["http_status"]!!.jsonPrimitive.int)
        assertEquals("unsupported_tool", result.report["error_code"]!!.jsonPrimitive.content)
        assertEquals("req_test", result.report["request_id"]!!.jsonPrimitive.content)
        assertEquals("HTTP_REJECTION", result.report["classification"]!!.jsonPrimitive.content)
        listOf("saved-token", "Authorization").forEach { assertFalse(it in result.report.toString()) }
        assertTrue(runCatching { probe.run("http://example.test/", "saved-token", payload, area, 1) }.isFailure)
        val ready = ResearchTransportProbe(client(200, envelope())).run("https://example.test/", "saved-token", payload, area, 1)
        assertNotNull(ready.dossier); assertEquals(200, ready.report["http_status"]!!.jsonPrimitive.int)
        val parameter = ResearchTransportProbe(client(400, """{"detail":"Stream must be set to true"}"""))
            .run("https://example.test/", "saved-token", payload, area, 1)
        assertEquals("Stream must be set to true", parameter.report["rejection_detail"]!!.jsonPrimitive.content)
        assertEquals("detail", parameter.report["error_shape"]!!.jsonPrimitive.content)
        assertEquals("{\"detail\":\"string\"}", parameter.report["response_shape"].toString())
        assertFalse("secret-token" in safeRejectionDetail("Bearer secret-token", "saved-token"))
    }
    @Test fun cancellationReleasesCallerAndCancelsNativeCallWhileBodyIsBlocked() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        lateinit var nativeCall: Call
        val blocked = ResearchTransportProbe.defaultClient().newBuilder().addInterceptor { chain ->
            nativeCall = chain.call(); entered.countDown(); release.await(3, TimeUnit.SECONDS)
            throw java.io.IOException("test_cancelled")
        }.build()
        val job = async { ResearchTransportProbe(blocked).run("https://example.test/", "saved-token", payload, area, 1) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            job.cancel(); withTimeout(1000) { job.join() }
            withTimeout(1000) { while (!nativeCall.isCanceled()) delay(5) }
            assertTrue(job.isCancelled)
        } finally { release.countDown() }
    }
    @Test fun bodySocketFailureAfter200IsTransportFailureWithNoCapabilityRejection() = runBlocking {
        val brokenBody = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long = throw java.net.SocketException("private socket details")
                override fun timeout() = Timeout.NONE
                override fun close() {}
            }.buffer()
        }
        val client = ResearchTransportProbe.defaultClient().newBuilder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .header("x-request-id", "req_socket").body(brokenBody).build()
        }.build()
        val result = ResearchTransportProbe(client).run("https://example.test/", "saved-token", payload, area, 1)
        assertEquals(200, result.report["http_status"]!!.jsonPrimitive.int)
        assertEquals("body read", result.report["stage"]!!.jsonPrimitive.content)
        assertEquals("SocketException", result.report["transport_outcome"]!!.jsonPrimitive.content)
        assertEquals("io_failure", result.report["failure_reason"]!!.jsonPrimitive.content)
        assertEquals("TRANSPORT_UNRESOLVED", result.report["classification"]!!.jsonPrimitive.content)
        assertFalse(result.report["local_dossier_ready"]!!.jsonPrimitive.boolean)
        assertFalse(result.report.containsKey("error_code"))
        assertFalse("private socket details" in result.report.toString())
    }
    @Test fun totalDeadlineReleasesCallerEvenWhenNativeCallbackIsStillBlocked() = runBlocking {
        val release = CountDownLatch(1)
        lateinit var nativeCall: Call
        val blocked = ResearchTransportProbe.defaultClient().newBuilder().addInterceptor { chain ->
            nativeCall = chain.call(); release.await(3, TimeUnit.SECONDS)
            throw java.io.IOException("late cancelled call")
        }.build()
        try {
            val report = withTimeout(1000) { ResearchTransportProbe(blocked, timeoutMs = 150)
                .run("https://example.test/", "saved-token", payload, area, 1).report }
            assertEquals("TimeoutCancellationException", report["transport_outcome"]!!.jsonPrimitive.content)
            assertEquals("TRANSPORT_UNRESOLVED", report["classification"]!!.jsonPrimitive.content)
            withTimeout(1000) { while (!nativeCall.isCanceled()) delay(5) }
        } finally { release.countDown() }
    }
}
