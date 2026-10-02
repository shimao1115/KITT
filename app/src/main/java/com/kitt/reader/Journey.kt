package com.kitt.reader

enum class JourneyState { IDLE, READING, SPEAKING, LISTENING, QUIET }
interface VoicePort {
    fun stop()
    fun speak(text: String, complete: (Boolean) -> Unit)
    fun listen(result: (String?) -> Unit)
}
data class Prepared(val topic: String, val hint: String, val at: Long, val anchor: Fix)
data class Ticket(val epoch: Long, val at: Long, val fix: Fix?, val active: Boolean)
data class TripSummary(val started: Long, val ended: Long, val destination: String, val topics: List<String>, val skipped: Int)

/** All transitions run on the main thread in Android; fake clock/voice make races testable on JVM. */
class Journey(private val now: () -> Long, private val voice: VoicePort, private val changed: () -> Unit = {}) {
    var running = false; private set
    var speaking = false; private set
    var listening = false; private set
    var quietUntil = 0L; private set
    var started = 0L; private set
    var destination = "未询问"; private set
    var instructions = ""; private set
    var topic = ""; private set
    var notice = ""; private set
    var prepared: Prepared? = null; private set
    var fix: Fix? = null; private set
    var foreground = true
    var epoch = 0L; private set
    var lastSpeech = 0L; private set
    var cooldownUntil = 0L; private set
    val recentTopics = ArrayDeque<String>()
    private val skippedTopics = mutableMapOf<String, Long>()
    private val asked = mutableSetOf<String>()
    private var lastQuestion = Long.MIN_VALUE / 2
    private var destinationQuestion = false
    private var skippedCount = 0
    private var lastCheckAt = Long.MIN_VALUE / 2
    private var lastCheckFix: Fix? = null
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
        epoch++; speaking = false; listening = false; voice.stop()
    }
    fun start() {
        invalidate(); running = true; started = now(); quietUntil = 0; destination = "未询问"
        instructions = ""; topic = ""; notice = ""; fix = null; prepared = null
        recentTopics.clear(); skippedTopics.clear(); asked.clear(); skippedCount = 0
        lastSpeech = 0; cooldownUntil = 0; lastCheckAt = Long.MIN_VALUE / 2; lastCheckFix = null
        lastQuestion = Long.MIN_VALUE / 2; destinationQuestion = false; changed()
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
        destination = "未询问"; topic = ""; recentTopics.clear(); asked.clear(); skippedTopics.clear(); changed()
        return summary
    }
    fun quiet(durationMs: Long = 600000) {
        if (!running) return
        invalidate(); prepared = null; quietUntil = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE else now() + durationMs
        notice = ""; changed()
    }
    fun resume() {
        if (!running) return
        invalidate(); quietUntil = 0; prepared = null; cooldownUntil = now() + 10000
        lastCheckAt = now(); lastCheckFix = fix; changed()
    }
    fun tick() {
        if (quietUntil != 0L && quietUntil != Long.MAX_VALUE && now() >= quietUntil) resume()
        prepared?.let { if (now() - it.at > 300000) prepared = null }
        changed()
    }
    fun skip() {
        if (!running) return
        if (topic.isNotBlank()) { skippedTopics[topic] = now() + 1800000; skippedCount++ }
        invalidate(); prepared = null; topic = ""; cooldownUntil = now() + 45000; changed()
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
    fun shouldCheck(): Boolean {
        val position = fix ?: return false
        if (!running || isQuiet || speaking || listening || now() < cooldownUntil || now() - position.timeMs > 60000) return false
        if (lastCheckFix == null) return true
        val elapsed = now() - lastCheckAt
        val distance = lastCheckFix!!.distanceTo(position)
        val sinceSpeech = if (lastSpeech == 0L) Long.MAX_VALUE else now() - lastSpeech
        val threshold = if (sinceSpeech < 180000) 3000.0 else 1500.0
        return elapsed >= 45000 && (distance >= threshold || (elapsed >= 300000 && distance >= 300))
    }
    fun ticket(active: Boolean): Ticket {
        if (active) invalidate()
        else { lastCheckAt = now(); lastCheckFix = fix }
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
        cooldownUntil = now() + 30000; changed(); return true
    }
    fun beginListening(onResult: (String) -> Unit) {
        if (!running) return
        invalidate(); prepared = null; listening = true; notice = ""; changed()
        val token = epoch
        voice.listen { value ->
            if (token == epoch && running && listening) {
                listening = false
                if (value.isNullOrBlank()) {
                    if (destinationQuestion) { destination = "未提供"; destinationQuestion = false }
                    notice = "没听清，想说时再点一下。"; cooldownUntil = now() + 30000; changed()
                } else { changed(); onResult(value) }
            }
        }
    }
    fun deliver(ticket: Ticket, raw: String?, onAnswer: (String) -> Unit = {}) {
        if (!running || ticket.epoch != epoch || now() - ticket.at > 45000) return
        if (!ticket.active && (isQuiet || speaking || listening || ticket.fix == null || fix == null ||
                    now() - fix!!.timeMs > 60000 || ticket.fix.distanceTo(fix!!) > 1500 ||
                    angleDifference(ticket.fix.bearing, fix!!.bearing) > 65)) return
        val response = runCatching { DirectorContract.parse(raw ?: error("Provider unavailable")) }
        if (response.isFailure) {
            if (ticket.active) say("刚才没连上，稍后再试。", false, true, onAnswer)
            changed(); return
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
                if (foreground && !isQuiet && normalized !in asked && now() - lastQuestion >= 600000) {
                    asked.add(normalized); lastQuestion = now()
                    if (destination == "未询问") { destinationQuestion = true; destination = "等待回答" }
                    say(result.question, true, ticket.active, onAnswer)
                }
            }
            Action.SPEAK_NOW -> {
                if (!ticket.active && (skippedTopics[result.topic] ?: 0) > now()) return
                prepared = null; topic = result.topic
                if (result.topic !in recentTopics) {
                    recentTopics.addLast(result.memoryUpdate.ifBlank { result.topic })
                    while (recentTopics.size > 8) recentTopics.removeFirst()
                }
                say(result.narration, false, ticket.active, onAnswer)
            }
        }
        changed()
    }
    private fun say(text: String, ask: Boolean, active: Boolean, onAnswer: (String) -> Unit) {
        speaking = true; val token = epoch; changed()
        voice.speak(text) { success ->
            if (epoch == token && running && speaking) {
                speaking = false; lastSpeech = now(); cooldownUntil = now() + if (active) 30000 else 60000
                if (!success) {
                    notice = "语音播放不可用，请检查系统中文语音。"
                    if (destinationQuestion) { destination = "未提供"; destinationQuestion = false }
                }
                changed()
                if (ask && success && foreground && !isQuiet) beginListening(onAnswer)
            }
        }
    }
    fun invalidateProvider() { if (running) { invalidate(); prepared = null; cooldownUntil = now() + 10000; changed() } }
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
