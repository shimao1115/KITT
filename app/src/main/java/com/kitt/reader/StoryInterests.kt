package com.kitt.reader

import android.content.Context

/** Soft selection bias. LESS never bans a category and MORE never crosses a content gate. */
enum class StoryInterest { MORE, NORMAL, LESS }

/**
 * A separate preferences file: an interest edit must not re-touch provider, model, TTS or the
 * encrypted account keys, because the code18 settings.xml byte drift is still undetermined.
 */
class StoryInterestStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun read(): Map<StoryCategory, StoryInterest> = StoryCategory.entries.associateWith { category ->
        runCatching { StoryInterest.valueOf(prefs.getString(category.name, StoryInterest.NORMAL.name)!!) }
            .getOrDefault(StoryInterest.NORMAL)
    }
    fun save(interests: Map<StoryCategory, StoryInterest>): Result<Unit> = runCatching {
        val edit = prefs.edit()
        StoryCategory.entries.forEach { edit.putString(it.name, (interests[it] ?: StoryInterest.NORMAL).name) }
        check(edit.commit()) { "暂时无法保存讲述兴趣。" }
    }
    companion object { const val FILE = "story_interests" }
}
