package com.kitt.reader

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

class ResearchSocketCancellationTest {
    @Test fun nativeCallTimeoutBoundsARealSocketWaitingForResponseHeaders() = runBlocking {
        ServerSocket(0).use { server ->
            val input = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val call = OkHttpClient.Builder().retryOnConnectionFailure(false)
                .callTimeout(250, TimeUnit.MILLISECONDS).build().newCall(input)
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                try { responseFromCall(call) { "unexpected response" }; false }
                catch (_: IOException) { true }
            }
            withContext(Dispatchers.IO) { server.accept() }.use {
                // A real connected server never sends headers. The call deadline must still fire.
                assertTrue(withTimeout(2000) { result.await() })
                assertTrue(call.isCanceled())
            }
        }
    }

    @Test fun cancelledResearchClosesBlockedTransportAndReleasesItsSlot() = runBlocking {
        val input = Request.Builder().url("https://example.test/").build()
        lateinit var callback: Callback
        val closing = CountDownLatch(1); val release = CountDownLatch(1)
        var cancelled = false; var parsed = 0
        val call = object : Call {
            override fun request() = input
            override fun execute(): Response = error("asynchronous transport only")
            override fun enqueue(responseCallback: Callback) { callback = responseCallback }
            override fun cancel() { cancelled = true; closing.countDown(); release.await(3, TimeUnit.SECONDS) }
            override fun isExecuted() = true
            override fun isCanceled() = cancelled
            override fun timeout() = Timeout()
            override fun clone(): Call = error("no retries")
        }
        val request = async(start = CoroutineStart.UNDISPATCHED) { responseFromCall(call) {
            parsed++; "late response must never become evidence"
        } }
        try {
            request.cancel()
            withTimeout(2000) { request.join() }
            assertTrue(withContext(Dispatchers.IO) { closing.await(2, TimeUnit.SECONDS) })
            assertTrue(request.isCancelled); assertTrue(cancelled)
            callback.onResponse(call, Response.Builder().request(input).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("late".toResponseBody()).build())
            assertEquals(0, parsed)
        } finally { release.countDown() }
    }
}
