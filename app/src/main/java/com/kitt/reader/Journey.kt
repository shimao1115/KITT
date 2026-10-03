package com.kitt.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
enum class JourneyState { IDLE, READING, SPEAKING, LISTENING, QUIET }
interface VoicePort {
    fun stop()
    fun speak(text: String, complete: (Boolean) -> Unit)
    fun listen(result: (String?) -> Unit)
    fun listenOutcome(result: (ListeningResult) -> Unit) { listen { result(ListeningResult.recognized(it)) } }
}
data class Prepared(val topic: String, val hint: String, val at: Long, val anchor: Fix)
data class Ticket(val epoch: Long, val at: Long, val fix: Fix?, val active: Boolean)
enum class DeliveryOutcome { SILENT, SPEAK_NOW, PREPARE, ASK_USER, STALE, CANCELLED, FAILURE, SUPPRESSED }
data class TripSummary(val started: Long, val ended: Long, val destination: String, val topics: List<String>, val skipped: Int)

/** All transitions run on the main thread in Android; fake clock/voice make races testable on JVM. */
class Journey(private val now: () -> Long, private val voice: VoicePort, private val changed: () -> Unit = {}) {
    // Compose observes the authoritative flags directly, including same-instance transitions.
    var running by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    var listening by mutableStateOf(false); private set
    var imageInteraction by mutableStateOf(false); private set
    var quietUntil by mutableLongStateOf(0L); private set
    var started = 0L; private set
    var destination = "未询问"; private set
    var instructions = ""; private set
    var topic by mutableStateOf(""); private set
    var notice by mutableStateOf(""); private set
    var prepared: Prepared? = null; private set
    var fix by mutableStateOf<Fix?>(null); private set
    var foreground = true
    var diagnostic: (String) -> Unit = {}
    var epoch = 0L; private set
    var lastSpeech = 0L; private set
    var cooldownUntil = 0L; private set
    val recentTopics = ArrayDeque<String>()
    val recentFamilies = ArrayDeque<TopicFamily>()
    private val skippedTopics = mutableMapOf<String, Long>()
    private val asked = mutableSetOf<String>()
    private var lastQuestion = Long.MIN_VALUE / 2
    private var destinationQuestion = false
    private var skippedCount = 0
    private var lastCheckAt = Long.MIN_VALUE / 2
    private var lastCheckFix: Fix? = null
    private var chapterEntry = false
    private var simulationClock: (() -> Long)? = null
    private var simulationProgress: (() -> Double)? = null
    private var lastCheckProgress = 0.0
    private var lastSpeechTravelMs: Long? = null
    val simulatedTravelMs get() = simulationClock?.invoke()
    val simulatedMeters get() = simulationProgress?.invoke()
    val simulatedSinceSpeechMs get() = lastSpeechTravelMs?.let { simulatedTravelMs?.minus(it) }
    private fun cadenceNow() = simulationClock?.invoke() ?: now()
    /** Explicit developer sessions only. Quiet, freshness, ticket and PREPARE expiry stay on wall time. */
    fun simulationCadence(clock: () -> Long, progress: () -> Double) {
        simulationClock = clock; simulationProgress = progress
    }
    val state: JourneyState get() = when {
        !running -> JourneyState.IDLE
        listening -> JourneyState.LISTENING
        speaking -> JourneyState.SPEAKING
        isQuiet -> JourneyState.QUIET
        else -> JourneyState.READING
    }
    val isQuiet get() = running && quietUntil > now()
    val quietRemaining get() = if (quietUntil == Long.MAX_VALUE) Long.MAX_VALUE else (quietUntil - now()).coerceAtLeast(0)

    private fun invalidate() {
        epoch++; speaking = false; listening = false; imageInteraction = false; voice.stop()
    }
    fun start() {
        simulationClock = null; simulationProgress = null; lastCheckProgress = 0.0; lastSpeechTravelMs = null
        invalidate(); running = true; started = now(); quietUntil = 0; destination = "未询问"
        instructions = ""; topic = ""; notice = ""; fix = null; prepared = null
        recentTopics.clear(); recentFamilies.clear(); skippedTopics.clear(); asked.clear(); skippedCount = 0
        lastSpeech = 0; cooldownUntil = 0; lastCheckAt = Long.MIN_VALUE / 2; lastCheckFix = null
        lastQuestion = Long.MIN_VALUE / 2; destinationQuestion = false; chapterEntry = false; changed()
    }
    fun restore(startedAt: Long, intent: String, session: String, topics: List<String>, quiet: Long) {
        start(); started = startedAt; destination = intent; instructions = session
        recentTopics.addAll(topics.takeLast(8)); quietUntil = quiet.takeIf { it > now() } ?: 0
        // Recovery never restores audio, requests, PREPARE, or a microphone window.
        changed()
    }
    fun end(): TripSummary {
        val summary = TripSummary(started, now(), destination, recentTopics.toList(), skippedCount)
        invalidate(); running = false; prepared = null; fix = null; quietUntil = 0; instructions = ""
        destination = "未询问"; topic = ""; recentTopics.clear(); recentFamilies.clear(); asked.clear(); skippedTopics.clear(); chapterEntry = false; changed()
        return summary
    }
    fun quiet(durationMs: Long = 600000) {
        if (!running) return
        invalidate(); prepared = null; quietUntil = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE else now() + durationMs
        notice = ""; changed()
    }
    fun resume() {
        if (!running) return
        invalidate(); quietUntil = 0; prepared = null; cooldownUntil = cadenceNow() + 10000
        lastCheckAt = cadenceNow(); lastCheckFix = fix; lastCheckProgress = simulationProgress?.invoke() ?: 0.0; changed()
    }
    fun tick() {
        if (quietUntil != 0L && quietUntil != Long.MAX_VALUE && now() >= quietUntil) resume()
        prepared?.let { if (now() - it.at > 300000) prepared = null }
        changed()
    }
    fun skip() {
        if (!running) return
        if (topic.isNotBlank()) { skippedTopics[topic] = now() + 1800000; skippedCount++ }
        invalidate(); prepared = null; topic = ""; cooldownUntil = cadenceNow() + 45000; changed()
    }
    fun location(next: Fix) {
        if (!running || !next.valid() || now() - next.timeMs > 60000 || next.timeMs > now() + 10000) return
        fix?.let { if (next.timeMs < it.timeMs) return }
        fix = next
        prepared?.let {
            if (now() - it.at > 300000 || it.anchor.distanceTo(next) > 2000 ||
                angleDifference(it.anchor.bearing, next.bearing) > 65 ||
                (it.anchor.area.isNotEmpty() && next.area != it.anchor.area)) prepared = null
        }
        changed()
    }
    /** A new town/township/street chapter is worth one look; it grants an opportunity, never forced audio. */
    fun noteChapterEntry() { chapterEntry = true }

    fun shouldCheck(landmarkOpportunity: Boolean = false): Boolean {
        val position = fix ?: return false
        val freshChapter = chapterEntry
        // Consumed even when blocked: a suppressed chapter wake-up does not become a queued opportunity.
        chapterEntry = false
        if (!running || isQuiet || speaking || listening || imageInteraction || cadenceNow() < cooldownUntil || now() - position.timeMs > 60000) return false
        if (lastCheckFix == null) return true
        if (freshChapter) return true
        val elapsed = cadenceNow() - lastCheckAt
        if (landmarkOpportunity && elapsed >= 45000) return true
        simulationProgress?.let {
            val traveled = it() - lastCheckProgress
            val areaChanged = position.area != lastCheckFix!!.area
            // About 13–15 opportunities on this 110 km fixture, without per-second requests.
            return elapsed >= 45000 && (traveled >= 9000 || (areaChanged && traveled >= 5000))
        }
        val distance = lastCheckFix!!.distanceTo(position)
        val sinceSpeech = if (lastSpeech == 0L) Long.MAX_VALUE else now() - lastSpeech
        val threshold = if (sinceSpeech < 180000) 3000.0 else 1500.0
        return elapsed >= 45000 && (distance >= threshold || (elapsed >= 300000 && distance >= 300))
    }
    fun ticket(active: Boolean): Ticket {
        if (active) invalidate()
        if (!active || simulationClock != null) {
            lastCheckAt = cadenceNow(); lastCheckFix = fix; lastCheckProgress = simulationProgress?.invoke() ?: 0.0
        }
        return Ticket(epoch, now(), fix, active)
    }
    fun requestInput(text: String): Boolean {
        if (!running || text.isBlank()) return false
        invalidate(); prepared = null; notice = ""
        val clean = text.trim().take(800)
        val duration = quietCommand(clean)
        if (duration != null) { quiet(duration); return false }
        if (clean.contains("结束安静") || clean.contains("恢复讲")) { resume(); return false }
        if (destinationQuestion || clean.startsWith("去") || clean.contains("目的地") || clean.contains("计划变")) {
            destination = clean.take(120); destinationQuestion = false
        }
        if (listOf("多讲", "少讲", "详细一点", "简单一点", "孩子", "偏好").any { it in clean })
            instructions = (instructions.lines().filter(String::isNotBlank).takeLast(3) + clean).joinToString("\n").takeLast(600)
        cooldownUntil = cadenceNow() + 30000; changed(); return true
    }
    fun beginListening(onResult: (String) -> Unit) {
        if (!running) return
        invalidate(); prepared = null; listening = true; notice = ""; changed()
        val token = epoch
        voice.listenOutcome { result ->
            if (token == epoch && running && listening) {
                listening = false
                if (result.outcome != ListeningOutcome.SUCCESS || result.text.isBlank()) {
                    if (destinationQuestion) { destination = "未提供"; destinationQuestion = false }
                    notice = result.notice; cooldownUntil = cadenceNow() + 30000; changed()
                } else { changed(); onResult(result.text) }
            }
        }
    }
    fun beginImageInteraction() {
        if (!running) return
        invalidate(); prepared = null; imageInteraction = true; notice = ""; changed()
    }
    fun finishImageInteraction() {
        imageInteraction = false; cooldownUntil = cadenceNow() + 30000; changed()
    }
    fun deliver(ticket: Ticket, raw: String?, activeFailure: String? = null, proximityBlock: DeliveryOutcome? = null,
        onAnswer: (String) -> Unit = {}): DeliveryOutcome {
        if (!running || ticket.epoch != epoch) return DeliveryOutcome.CANCELLED
        if (now() - ticket.at > 45000) return DeliveryOutcome.STALE
        if (!ticket.active && (isQuiet || speaking || listening || ticket.fix == null || fix == null ||
                    now() - fix!!.timeMs > 60000 || ticket.fix.distanceTo(fix!!) > 1500 ||
                    angleDifference(ticket.fix.bearing, fix!!.bearing) > 65)) return DeliveryOutcome.STALE
        if (proximityBlock != null) return proximityBlock
        val response = runCatching { DirectorContract.parse(raw ?: error("Provider unavailable")) }
        if (response.isFailure) {
            diagnostic("Director response unavailable or failed schema validation")
            if (ticket.active) say(activeFailure ?: "刚才没连上，稍后再试。", false, true, onAnswer)
            changed(); return DeliveryOutcome.FAILURE
        }
        val result = response.getOrThrow()
        when (result.action) {
            Action.SILENT -> Unit
            Action.PREPARE -> {
                // This is a revalidation hint bound to this live fix, never a narration cache.
                prepared = fix?.let { Prepared(result.topic, result.prepareHint, now(), it) }
            }
            Action.ASK_USER -> {
                val normalized = result.question.replace(Regex("[\\s？?。！!]"), "")
                if (foreground && (ticket.active || !isQuiet) && normalized !in asked && now() - lastQuestion >= 600000) {
                    asked.add(normalized); lastQuestion = now()
                    if (destination == "未询问") { destinationQuestion = true; destination = "等待回答" }
                    say(result.question, true, ticket.active, onAnswer)
                } else return DeliveryOutcome.SUPPRESSED
            }
            Action.SPEAK_NOW -> {
                if (!ticket.active && (skippedTopics[result.topic] ?: 0) > now()) return DeliveryOutcome.SUPPRESSED
                prepared = null; topic = result.topic
                result.topicFamily?.let {
                    recentFamilies.addLast(it)
                    while (recentFamilies.size > 8) recentFamilies.removeFirst()
                }
                if (result.topic !in recentTopics) {
                    recentTopics.addLast(result.memoryUpdate.ifBlank { result.topic })
                    while (recentTopics.size > 8) recentTopics.removeFirst()
                }
                say(result.narration, false, ticket.active, onAnswer)
            }
        }
        changed()
        return DeliveryOutcome.valueOf(result.action.name)
    }
    private fun say(text: String, ask: Boolean, active: Boolean, onAnswer: (String) -> Unit) {
        speaking = true; val token = epoch; changed()
        voice.speak(text) { success ->
            if (epoch == token && running && speaking) {
                speaking = false; lastSpeech = now()
                lastSpeechTravelMs = simulatedTravelMs
                cooldownUntil = cadenceNow() + if (active) 30000 else 60000
                diagnostic("Voice completed success=$success active=$active ask=$ask")
                if (!success) {
                    notice = "语音播放不可用，请检查系统中文语音。"
                    if (destinationQuestion) { destination = "未提供"; destinationQuestion = false }
                }
                changed()
                if (ask && success && foreground && (active || !isQuiet)) beginListening(onAnswer)
            }
        }
    }
    fun invalidateProvider() { if (running) { invalidate(); prepared = null; cooldownUntil = cadenceNow() + 10000; changed() } }
}

fun quietCommand(text: String): Long? {
    if (!listOf("安静", "先别讲", "别说", "等我叫").any { it in text }) return null
    if ("结束安静" in text) return null
    if ("等我叫" in text) return Long.MAX_VALUE
    if ("半小时" in text) return 1800000
    if ("一小时" in text || "一个小时" in text) return 3600000
    val digits = Regex("(\\d{1,3})\\s*(分钟|小时)").find(text)
    if (digits != null) return digits.groupValues[1].toLong().coerceIn(1, 180) *
        if (digits.groupValues[2] == "小时") 3600000 else 60000
    val chinese = mapOf("十分钟" to 10, "二十分钟" to 20, "三十分钟" to 30, "五分钟" to 5)
    chinese.entries.sortedByDescending { it.key.length }.firstOrNull { it.key in text }?.let { return it.value * 60000L }
    return 600000
}
