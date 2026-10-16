package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

/** Ranking is a pure post-gate reorder: it can never add, remove or ban a candidate. */
class StoryRankingTest {
    private fun story(id: String, category: StoryCategory, type: StoryType = StoryType.followup) =
        LocalStory(id, type, "person-yang-shen", "标题 $id", "正文".repeat(40), category, listOf("claim:$id"))
    private fun ids(stories: List<LocalStory>) = stories.map { it.id }

    @Test fun aMissingEntryIsTreatedAsNormalAndNeverAsLess() {
        val candidates = listOf(story("his", StoryCategory.HISTORY), story("nat", StoryCategory.NATURE))
        // NATURE has no stored entry: it must tie with an explicit NORMAL, not sink below it.
        assertEquals(ids(candidates), ids(StoryRanking.order(candidates, mapOf(StoryCategory.HISTORY to StoryInterest.NORMAL))))
        assertEquals(listOf("nat", "his"), ids(StoryRanking.order(candidates, mapOf(StoryCategory.HISTORY to StoryInterest.LESS))))
        assertEquals("nat", StoryRanking.first(candidates, mapOf(StoryCategory.NATURE to StoryInterest.MORE))!!.id)
    }
    @Test fun allNormalKeepsTheExistingDeterministicOrder() {
        val candidates = StoryCategory.entries.map { story("s-${it.name}", it) }
        val balanced = StoryCategory.entries.associateWith { StoryInterest.NORMAL }
        assertEquals(ids(candidates), ids(StoryRanking.order(candidates, balanced)))
        assertEquals(ids(candidates), ids(StoryRanking.order(candidates, emptyMap())))
    }
    @Test fun moreBeatsNormalBeatsLessWithoutReorderingTies() {
        val candidates = listOf(story("nat", StoryCategory.NATURE), story("his", StoryCategory.HISTORY),
            story("eng", StoryCategory.ENGINEERING), story("his2", StoryCategory.HISTORY))
        val ranked = ids(StoryRanking.order(candidates, mapOf(StoryCategory.HISTORY to StoryInterest.MORE,
            StoryCategory.ENGINEERING to StoryInterest.LESS)))
        assertEquals(listOf("his", "his2", "nat", "eng"), ranked)
        assertEquals("his", StoryRanking.first(candidates, mapOf(StoryCategory.HISTORY to StoryInterest.MORE))!!.id)
    }
    @Test fun lessNeverBansAndMoreNeverAdmitsAnUngatedStory() {
        val candidates = listOf(story("his", StoryCategory.HISTORY), story("eng", StoryCategory.ENGINEERING))
        val hostile = StoryCategory.entries.associateWith { StoryInterest.LESS }
        assertEquals(candidates.toSet(), StoryRanking.order(candidates, hostile).toSet())
        assertEquals("his", StoryRanking.first(candidates, mapOf(StoryCategory.TRAVEL to StoryInterest.MORE))!!.id)
        assertTrue(StoryRanking.order(emptyList(), hostile).isEmpty())
        assertNull(StoryRanking.first(emptyList(), hostile))
    }
}
