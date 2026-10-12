package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

class AreaResolutionCacheTest {
    private val start = 1_000_000L
    private val street = AreaIdentity("成都市", "新都区", "新都街道", "四川省")
    private fun fix(time: Long = start, source: FixSource = FixSource.NETWORK) =
        Fix(30.0, 104.0, time, accuracy = 30.0, source = source)
    private fun seed(cache: AreaResolutionCache, fix: Fix = fix()) {
        cache.cached(fix, fix.timeMs)
        assertTrue(cache.success(fix, street, cache.revision, fix.timeMs))
    }

    @Test fun tenMinutesOfFreshGpsAndNetworkKeepOneChapterAcrossOldThirtySecondExpiry() {
        for (source in listOf(FixSource.GPS, FixSource.NETWORK)) {
            val cache = AreaResolutionCache(); val cards = AreaCards(); seed(cache, fix(source = source))
            for (second in 0..600) {
                val now = start + second * 1000
                val area = cache.cached(fix(now, source), now)
                assertEquals("$source second=$second", street, area)
                cards.accept(area)
            }
            assertEquals(1, cards.transitions); assertEquals(street, cards.active!!.area)
        }
    }

    @Test fun emptyDoesNotReplaceSuccessButReliableDifferentOrDistrictOnlyResultDoes() {
        val cache = AreaResolutionCache(); seed(cache)
        for (second in 1..120) cache.cached(fix(start + second * 1000), start + second * 1000)
        val current = fix(start + 120000); cache.cached(current, current.timeMs)
        assertFalse(cache.success(current, null, cache.revision, current.timeMs))
        assertEquals(street, cache.cached(current, current.timeMs))
        val other = street.copy(district = "金牛区", chapter = "天回镇街道")
        assertTrue(cache.success(current, other, cache.revision, current.timeMs))
        assertEquals(other, cache.cached(current, current.timeMs))
        assertTrue(cache.success(current, other.copy(chapter = ""), cache.revision, current.timeMs))
        assertEquals("", cache.cached(current, current.timeMs)!!.chapter)
        val empty = AreaResolutionCache(); empty.cached(current, current.timeMs)
        assertFalse(empty.success(current, null, empty.revision, current.timeMs))
        assertNull(empty.cached(current, current.timeMs))
    }

    @Test fun hundredKmhMovementInvalidatesBy450MetersAndNeverResurrectsOnReturn() {
        val cache = AreaResolutionCache(); seed(cache)
        for (second in 1..17) {
            val now = start + second * 1000
            val moving = fix(now).copy(latitude = 30.0 + second * (100.0 / 3.6) / 111195, speedKmh = 100.0)
            val area = cache.cached(moving, now)
            if (second <= 16) assertEquals(street, area) else assertNull(area)
        }
        assertNull(cache.cached(fix(start + 18000), start + 18000))
    }

    @Test fun staleOrMissingPositionInvalidatesWithoutResurrection() {
        for (source in listOf(FixSource.GPS, FixSource.NETWORK)) {
            val cache = AreaResolutionCache(); val first = fix(source = source); seed(cache, first)
            val expiry = if (source == FixSource.GPS) 15001 else 30001
            assertNull(cache.cached(first, start + expiry))
            assertNull(cache.cached(fix(start + expiry + 1, source), start + expiry + 1))
        }
        val cache = AreaResolutionCache(); seed(cache)
        assertNull(cache.cached(null, start + 1000))
        assertNull(cache.cached(fix(start + 2000), start + 2000))
    }

    @Test fun unobservedGapAlsoBreaksContinuityWithFreshReturningFix() {
        val cache = AreaResolutionCache(); seed(cache)
        assertNull(cache.cached(fix(start + 120000), start + 120000))
    }

    @Test fun providerSwitchAndLastKnownCannotCarryOldStreetOrRestoreItOnSwitchBack() {
        for (source in listOf(FixSource.GPS, FixSource.LAST_KNOWN)) {
            val cache = AreaResolutionCache(); seed(cache)
            assertNull(cache.cached(fix(start + 1000, source), start + 1000))
            assertNull(cache.cached(fix(start + 2000), start + 2000))
        }
    }

    @Test fun coarsePositionWithdrawsStreetAndAccuracyRecoveryNeedsNewGeocode() {
        val cache = AreaResolutionCache(); seed(cache)
        val coarse = fix(start + 1000).copy(accuracy = 1000.0)
        assertEquals(street.copy(chapter = ""), cache.cached(coarse, coarse.timeMs))
        assertEquals("", cache.cached(fix(start + 2000), start + 2000)!!.chapter)
        assertNull(cache.cached(fix(start + 3000).copy(accuracy = 1501.0), start + 3000))
    }

    @Test fun lateResultCannotCrossInvalidationOrRelabelDistantCurrentPosition() {
        val cache = AreaResolutionCache(); val request = fix(); cache.cached(request, start)
        val revision = cache.revision
        cache.cached(fix(start + 1000, FixSource.GPS), start + 1000)
        cache.cached(fix(start + 2000), start + 2000)
        assertFalse(cache.success(request, street, revision, start + 2000))
        val freshRevision = cache.revision
        cache.cached(fix(start + 3000).copy(latitude = 31.0), start + 3000)
        assertFalse(cache.success(request, street, freshRevision, start + 3000))
    }

    @Test fun stopClearsLabelsAndInvalidatesPendingResult() {
        val cache = AreaResolutionCache(); seed(cache); val revision = cache.revision
        cache.clear(); cache.cached(fix(start + 1000), start + 1000)
        assertFalse(cache.success(fix(), street, revision, start + 1000))
        assertNull(cache.cached(fix(start + 1000), start + 1000))
    }
}
