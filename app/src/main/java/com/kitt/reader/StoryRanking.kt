package com.kitt.reader

/**
 * Interest bias applied after every qualification gate has already run. Callers must pass only
 * eligible candidates: ranking can reorder them, never admit or remove one. A stable sort keeps the
 * previous deterministic order when the whole map is NORMAL.
 */
object StoryRanking {
    private fun weight(interest: StoryInterest) = when (interest) {
        StoryInterest.MORE -> 0
        StoryInterest.NORMAL -> 1
        StoryInterest.LESS -> 2
    }
    fun order(stories: List<LocalStory>, interests: Map<StoryCategory, StoryInterest>): List<LocalStory> =
        stories.sortedBy { story -> weight(interests[story.category] ?: StoryInterest.NORMAL) }
    fun first(stories: List<LocalStory>, interests: Map<StoryCategory, StoryInterest>): LocalStory? =
        order(stories, interests).firstOrNull()
}
