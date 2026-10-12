package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowGeocoder

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAreaResolverTest {
    private val street = AreaIdentity("成都市", "新都区", "新都街道", "四川省")
    private val app get() = RuntimeEnvironment.getApplication()
    private fun fix(time: Long = System.currentTimeMillis(), source: FixSource = FixSource.NETWORK) =
        Fix(30.0, 104.0, time, accuracy = 30.0, source = source)

    @Test @Config(sdk = [26, 34]) fun absentPlatformGeocoderCompletesUnresolvedWithoutCrash() = runBlocking {
        ShadowGeocoder.setIsPresent(false)
        val logs = mutableListOf<String>(); val resolver = AndroidAreaResolver(app, this, logs::add)
        val done = CompletableDeferred<Unit>(); val request = fix()
        try {
            resolver.resolve(request) { done.complete(Unit) }
            withTimeout(5000) { done.await() }
            assertNull(resolver.cached(request)); assertNull(resolver.pendingSince)
            assertEquals(listOf("geo_started", "geo_empty"), logs)
        } finally { resolver.stop() }
    }

    @Test fun delayedResponseCannotUndoProviderSwitchOrApplyAfterStop() = runTest {
        val logs = mutableListOf<String>(); var callbacks = 0
        val now = { 1_000_000L + testScheduler.currentTime }
        val resolver = AndroidAreaResolver(app, backgroundScope, logs::add, { delay(5000); street }, now)
        try {
            resolver.resolve(fix(now())) { callbacks++ }; runCurrent()
            advanceTimeBy(1000); resolver.cached(fix(now(), FixSource.GPS))
            advanceTimeBy(1000); resolver.cached(fix(now()))
            advanceTimeBy(3000); runCurrent()
            assertEquals(1, callbacks); assertNull(resolver.cached(fix(now())))
            assertTrue(logs.contains("geo_discarded"))
        } finally { resolver.stop() }
        val stopped = AndroidAreaResolver(app, backgroundScope, {}, { delay(5000); street }, now)
        stopped.resolve(fix(now())) { callbacks++ }; runCurrent(); stopped.stop()
        advanceTimeBy(6000); runCurrent()
        assertEquals(1, callbacks); assertNull(stopped.cached(fix(now()))); assertNull(stopped.pendingSince)
    }

    @Test fun timeoutAndErrorAreDistinguishableAndNeverLeakNamesCoordinatesOrExceptionText() = runTest {
        for (timeout in listOf(true, false)) {
            val logs = mutableListOf<String>(); val now = { 1_000_000L + testScheduler.currentTime }
            val resolver = AndroidAreaResolver(app, backgroundScope, logs::add, {
                if (timeout) delay(9000) else throw IllegalStateException("private service detail")
                street
            }, now)
            try {
                resolver.resolve(fix(now())) { fail("Failure must not deliver a result") }; runCurrent()
                advanceTimeBy(8000); runCurrent()
                assertNull(resolver.pendingSince); assertNull(resolver.cached(fix(now())))
                assertEquals(listOf("geo_started", if (timeout) "geo_timeout" else "geo_error"), logs)
            } finally { resolver.stop() }
        }
    }

    @Test fun staleMissingOrBridgePositionsDoNotStartLookups() = runTest {
        var calls = 0; val now = { 1_000_000L }
        val resolver = AndroidAreaResolver(app, backgroundScope, {}, { calls++; street }, now)
        try {
            resolver.cached(null)
            resolver.resolve(fix(now() - 30001)) { fail() }
            resolver.resolve(fix(now(), FixSource.LAST_KNOWN)) { fail() }
            resolver.resolve(fix(now()).copy(accuracy = 1501.0)) { fail() }
            runCurrent(); assertEquals(0, calls); assertNull(resolver.pendingSince)
        } finally { resolver.stop() }
    }
}
