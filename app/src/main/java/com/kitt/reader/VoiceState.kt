package com.kitt.reader

import android.speech.SpeechRecognizer
import java.util.Locale

enum class ListeningOutcome { SUCCESS, NO_MATCH, TIMEOUT, PERMISSION_DENIED, UNAVAILABLE, BUSY, NETWORK, SERVER, CLIENT, CANCELLED }
data class ListeningResult(val outcome: ListeningOutcome, val text: String = "", val androidCode: Int? = null) {
    val notice: String get() = when (outcome) {
        ListeningOutcome.SUCCESS, ListeningOutcome.CANCELLED -> ""
        ListeningOutcome.NO_MATCH -> "没听清，想说时再点一下。"
        ListeningOutcome.TIMEOUT -> "没等到语音，想说时再点一下。"
        ListeningOutcome.PERMISSION_DENIED -> "需要麦克风权限才能听你说话。"
        ListeningOutcome.UNAVAILABLE -> "系统语音识别暂不可用。"
        ListeningOutcome.BUSY, ListeningOutcome.CLIENT -> "语音识别没启动成功，请再试一次。"
        ListeningOutcome.NETWORK -> "语音识别网络未连通，请稍后再试。"
        ListeningOutcome.SERVER -> "系统语音识别服务暂时出错，请稍后再试。"
    }
    companion object {
        fun recognized(text: String?) = text?.trim()?.takeIf { it.isNotEmpty() }?.let {
            ListeningResult(ListeningOutcome.SUCCESS, it)
        } ?: ListeningResult(ListeningOutcome.NO_MATCH)
        fun error(code: Int) = ListeningResult(when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> ListeningOutcome.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListeningOutcome.TIMEOUT
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListeningOutcome.PERMISSION_DENIED
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> ListeningOutcome.BUSY
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ListeningOutcome.NETWORK
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> ListeningOutcome.SERVER
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> ListeningOutcome.UNAVAILABLE
            else -> ListeningOutcome.CLIENT
        }, androidCode = code)
    }
}

enum class VoicePhase { IDLE, PREPARING_LISTEN, LISTENING, PROCESSING, PREPARING_SPEECH, SPEAKING }
data class VoiceDetail(val phase: VoicePhase = VoicePhase.IDLE, val level: Float = 0f)

/**
 * Recognition text shown while the one-shot interaction is open. Transient UI state only:
 * it is never persisted, never replayed into a later interaction, and never becomes voice history.
 * `final=false` means an interim hypothesis, which must never be submitted as an answer.
 */
data class TranscriptText(val text: String = "", val final: Boolean = false) {
    val present get() = text.isNotBlank()
}

/** Callback-driven only; no microphone buffers, timers or simulated sound levels. */
class ListeningFeedback {
    var detail = VoiceDetail(); private set
    fun preparing() { detail = VoiceDetail(VoicePhase.PREPARING_LISTEN) }
    fun ready() { detail = VoiceDetail(VoicePhase.LISTENING) }
    fun rms(db: Float) {
        if (detail.phase != VoicePhase.LISTENING || !db.isFinite()) return
        val target = ((db + 2f) / 12f).coerceIn(0f, 1f)
        val weight = if (target > detail.level) 0.35f else 0.18f
        detail = detail.copy(level = (detail.level + weight * (target - detail.level)).coerceIn(0f, 1f))
    }
    fun endSpeech() { detail = VoiceDetail(VoicePhase.PROCESSING) }
    fun reset() { detail = VoiceDetail() }
}

data class TtsVoiceOption(val name: String, val locale: Locale, val networkRequired: Boolean, val installed: Boolean = true) {
    val label get() = "$name · ${locale.toLanguageTag()} · ${if (networkRequired) "需联网" else "本地"}${if (installed) "" else " · 需下载语音数据"}"
}
fun chineseVoices(voices: List<TtsVoiceOption>): List<TtsVoiceOption> = voices
    .filter { it.locale.language in setOf("zh", "yue") }
    .distinctBy { it.name }
    .sortedWith(compareBy<TtsVoiceOption> { when {
        it.locale.language == "zh" && it.locale.country == "CN" -> 0
        it.locale.language == "zh" -> 1
        else -> 2
    } }
        .thenBy { !it.installed }.thenBy { it.networkRequired }.thenBy { it.locale.toLanguageTag() }.thenBy { it.name })

fun selectedChineseVoice(saved: String, voices: List<TtsVoiceOption>, systemDefault: String?): TtsVoiceOption? {
    val chinese = chineseVoices(voices)
    return chinese.find { it.name == saved && it.installed }
        ?: chinese.find { it.name == systemDefault && it.installed }
        ?: chinese.firstOrNull { it.installed }
}

enum class VoiceVisual { IDLE, LISTENING, SPEAKING }
fun voiceVisual(state: JourneyState) = when (state) {
    JourneyState.LISTENING -> VoiceVisual.LISTENING
    JourneyState.SPEAKING -> VoiceVisual.SPEAKING
    else -> VoiceVisual.IDLE
}
