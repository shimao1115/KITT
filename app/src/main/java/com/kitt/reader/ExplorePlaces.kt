package com.kitt.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Read-only installed content. No position, Journey, accounts, network or stores are involved. */
data class ExploreRegion(val pack: OfflinePack, val stories: List<LocalStory>, val storyStatus: String) {
    val key get() = pack.area.key
    val factCount get() = pack.objects.sumOf { it.facts.size }
    fun storiesFor(title: String) = stories.filter { it.entityTitle == title }
    fun search(query: String): List<PackObject> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return pack.objects.filter { obj ->
            val text = obj.title + " " + pack.area.fullName + " " + obj.facts.joinToString(" ") { it.summary } +
                storiesFor(obj.title).joinToString(" ") { it.title + " " + it.text }
            terms.all { text.contains(it, ignoreCase = true) }
        }
    }
}

class ExploreCatalog(val regions: List<ExploreRegion>) {
    companion object {
        fun from(pack: OfflinePack?, stories: StoryPacks?) = ExploreCatalog(if (pack == null) emptyList() else listOf(
            ExploreRegion(pack, stories?.stories.orEmpty(), when {
                stories == null -> "完整故事资料缺失或校验失败；基础事实仍可阅读。"
                stories.blocked.isNotEmpty() -> "部分完整故事尚未通过审核或依赖校验，暂不可阅读和播放。"
                stories.stories.isEmpty() -> "目前没有审核通过的完整故事。"
                else -> "完整故事已通过内容校验，点击文章后可手动播放。"
            })))
        // Even runtimecheck exploration is strict. Its separate story-preview audition is unchanged.
        fun load(context: Context) = from(OfflinePack.load(context), StoryPacks.load(context, acceptanceCandidate = false))
    }
}

enum class ExploreSpeech { IDLE, PREPARING, PLAYING, ERROR }

/** Owns a dedicated VoicePort; late callbacks can never revive a stopped or replaced article. */
class ExplorePlayback(private val voice: VoicePort, private val offlineReady: () -> Boolean) {
    var state by mutableStateOf(ExploreSpeech.IDLE); private set
    var message by mutableStateOf(""); private set
    private var serial = 0L
    fun stop() { serial++; voice.stop(); state = ExploreSpeech.IDLE; message = "" }
    fun play(story: LocalStory) {
        stop()
        if (!offlineReady()) {
            state = ExploreSpeech.ERROR; message = "离线中文语音尚不可用，请在系统 TTS 设置安装或选择本地中文声音。"; return
        }
        state = ExploreSpeech.PREPARING
        val token = serial
        try {
            voice.speakTracked(story.text, {
                if (token == serial) state = ExploreSpeech.PLAYING
            }) { ok ->
                if (token == serial) {
                    state = if (ok) ExploreSpeech.IDLE else ExploreSpeech.ERROR
                    message = if (ok) "朗读完成" else "朗读未完成，请检查设备离线中文语音后重试。"
                }
            }
        } catch (_: Exception) {
            stop(); state = ExploreSpeech.ERROR; message = "朗读未能启动，请检查设备离线中文语音。"
        }
    }
}
