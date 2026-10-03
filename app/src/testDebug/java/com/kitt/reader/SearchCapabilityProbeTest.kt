package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SearchCapabilityProbeTest {
    private fun frame(raw: String, name: String = "") = (if (name.isBlank()) "" else "event: $name\r\n") + "data: $raw\r\n\r\n"
    private val added = """{"type":"response.output_item.added","item":{"type":"web_search_call","status":"in_progress","id":"private_item_id"}}"""
    private val searchDone = """{"type":"response.output_item.done","item":{"type":"web_search_call","status":"completed","action":{"sources":[{"url":"https://example.test/evidence","title":"private title"}]}}}"""
    private fun events() = SearchCapabilityEvents({ 12L })
    @Test fun payloadIsExactlyOneUnseededSearchWithNoSchemaAndDiagnosticOnlyDeadline() {
        val payload = SearchCapabilityProbe.payload()
        assertEquals("gpt-5.6-luna", payload["model"]!!.jsonPrimitive.content)
        assertTrue(payload["stream"]!!.jsonPrimitive.boolean)
        assertEquals("required", payload["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("web_search", payload["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(SearchCapabilityProbe.QUESTION, payload["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content)
        listOf("local_dossier", "json_schema", "杨升庵", "杨慎", "桂湖", "宝光寺", "新繁东湖").forEach { assertFalse(it in payload.toString()) }
        val client = SearchCapabilityProbe.diagnosticClient()
        assertEquals(180000, client.callTimeoutMillis); assertEquals(180000, client.readTimeoutMillis)
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects); assertFalse(client.retryOnConnectionFailure)
        assertEquals(90000, ResearchTransportProbe.defaultClient().callTimeoutMillis)
    }
    @Test fun allActualTypesIncludingAddedSearchingAndUnknownEventsAreRecordedWithoutBody() {
        var elapsed = 0L
        val observer = SearchCapabilityEvents({ elapsed++ })
        val raws = listOf("""{"type":"response.created"}""", added,
            """{"type":"response.web_search_call.searching"}""", """{"type":"new.unknown.event","content":"private body"}""",
            searchDone, """{"type":"response.output_text.delta","delta":"private body"}""",
            """{"type":"response.completed","response":{"status":"completed"}}""")
        assertEquals("response.completed", observer.read(raws.joinToString("") { frame(it) }.reader().buffered()))
        val report = observer.summary()
        assertEquals("A", report["classification"]!!.jsonPrimitive.content)
        assertTrue(report["web_search_item_seen"]!!.jsonPrimitive.boolean)
        assertTrue(report["output_text_started"]!!.jsonPrimitive.boolean)
        assertEquals(raws.map { Json.parseToJsonElement(it).jsonObject["type"]!!.jsonPrimitive.content },
            report["timeline"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        listOf("private body", "private_item_id", "private title", "https://example.test").forEach { assertFalse(it in report.toString()) }
    }
    @Test fun completedToolWithoutResponseCompletedIsBAndDoneMarkerCannotFakeA() {
        for (ending in listOf("", frame("[DONE]"))) {
            val observer = events()
            val terminal = observer.read((frame(added) + frame(searchDone) + ending).reader().buffered())
            assertEquals(if (ending.isEmpty()) "socket_eof" else "sse_done", terminal)
            assertEquals("B", observer.summary()["classification"]!!.jsonPrimitive.content)
            assertFalse(observer.summary()["response_completed"]!!.jsonPrimitive.boolean)
        }
        val started = events(); started.event(added, "")
        assertEquals("B", started.summary()["classification"]!!.jsonPrimitive.content)
    }
    @Test fun noToolIsCAndOnlyExplicitToolPermissionErrorsAreD() {
        val none = events(); none.read((": keepalive\n\n" + frame("""{"type":"response.created"}""")).reader().buffered())
        assertEquals("C", none.summary()["classification"]!!.jsonPrimitive.content)
        val rejected = events()
        rejected.event("""{"type":"error","error":{"code":"unsupported_tool","message":"web_search not supported private body"}}""", "")
        assertEquals("D", rejected.summary()["classification"]!!.jsonPrimitive.content)
        assertFalse("private body" in rejected.summary().toString())
        val auth = events(); auth.errorBody("""{"error":{"code":"invalid_token","message":"private token"}}""")
        assertEquals("C", auth.summary()["classification"]!!.jsonPrimitive.content)
    }
    @Test fun namedEventsAndCitationsAreAcceptedAndLateEventsCannotMutateFrozenEvidence() {
        val observer = events()
        observer.event(added, "")
        observer.event("""{"type":"response.web_search_call.completed"}""", "")
        observer.event("""{"annotation":{"type":"url_citation","url":"https://example.test/evidence"}}""", "response.output_text.annotation.added")
        observer.event("""{"text":"private body"}""", "response.output_text.done")
        assertEquals("response.completed", observer.event("{}", "response.completed"))
        assertEquals("A", observer.summary()["classification"]!!.jsonPrimitive.content)
        observer.finish("response.completed", "body read")
        val snapshot = observer.summary()
        observer.event("""{"type":"error","error":{"code":"unsupported_tool"}}""", "")
        assertEquals(snapshot, observer.summary())
        assertTrue(runCatching { events().read(("data: " + "x".repeat(128001)).reader().buffered()) }.isFailure)
    }
    @Test fun totalDeadlinePreservesLastEventAndCancelsNativeCallWithoutWaitingForClose() = runBlocking {
        val release = CountDownLatch(1)
        lateinit var call: Call
        val blocked = SearchCapabilityProbe.diagnosticClient().newBuilder().addInterceptor { chain ->
            call = chain.call(); release.await(3, TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).code(200).message("test").protocol(Protocol.HTTP_1_1)
                .body(frame(added).toResponseBody()).build()
        }.build()
        try {
            val report = withTimeout(1500) { SearchCapabilityProbe(blocked, 200).run("private-token") }
            assertEquals("total_timeout", report["terminal"]!!.jsonPrimitive.content)
            withTimeout(1000) { while (!call.isCanceled()) delay(5) }
            assertFalse("private-token" in report.toString())
        } finally { release.countDown() }
    }
}
