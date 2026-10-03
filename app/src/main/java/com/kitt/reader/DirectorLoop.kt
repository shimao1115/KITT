package com.kitt.reader

import kotlinx.coroutines.*

class DirectorCounters {
    var opportunities = 0; private set
    var dispatched = 0; private set
    var activeDispatched = 0; private set
    val automatic = mutableMapOf<DeliveryOutcome, Int>()
    val active = mutableMapOf<DeliveryOutcome, Int>()
    fun opportunity() { opportunities++ }
    fun dispatch(isActive: Boolean) { dispatched++; if (isActive) activeDispatched++ }
    fun terminal(isActive: Boolean, outcome: DeliveryOutcome) {
        val counts = if (isActive) active else automatic
        counts[outcome] = (counts[outcome] ?: 0) + 1
    }
    fun clear() { opportunities = 0; dispatched = 0; activeDispatched = 0; automatic.clear(); active.clear() }
    fun summary() = "opportunities=$opportunities dispatched=$dispatched activeDispatched=$activeDispatched " +
        "auto=${DeliveryOutcome.entries.joinToString(",") { "$it:${automatic[it] ?: 0}" }} " +
        "active=${DeliveryOutcome.entries.joinToString(",") { "$it:${active[it] ?: 0}" }}"
}

class DirectorLoop(private val journey: Journey, private val context: ContextPipeline,
    private val scope: CoroutineScope, private val now: () -> Long,
    private val provider: () -> DirectorProvider, private val failure: (String) -> Unit = {},
    private val diagnostic: (String) -> Unit = {}) {
    private var request: Job? = null
    private var requestActive = false
    val pending get() = request?.isActive == true
    val counters = DirectorCounters()
    fun location(fix: Fix) {
        journey.location(fix)
        if (journey.fix == fix) context.accept(fix)
        check()
    }
    fun check() {
        journey.tick()
        if (pending || !journey.shouldCheck()) return
        counters.opportunity()
        dispatch(null)
    }
    fun speak() { cancel(); journey.beginListening(::user) }
    fun user(text: String, image: ImageInput? = null) {
        cancel()
        if (journey.requestInput(text)) dispatch(text, image)
    }
    private fun dispatch(utterance: String?, image: ImageInput? = null) {
        requestActive = utterance != null
        val ticket = journey.ticket(utterance != null)
        val input = DirectorRequest(sessionInstructions = journey.instructions,
            contextCard = context.card(journey, now()), userUtterance = utterance, image = image)
        counters.dispatch(ticket.active)
        diagnostic("dispatch active=${ticket.active} ${counters.summary()}")
        request = scope.launch {
            var activeFailure: String? = null
            val raw = try { withTimeout(35000) {
                val selected = provider()
                if (image != null && !selected.acceptsImages) throw UnsupportedImage()
                selected.direct(input)
            } }
            catch (e: TimeoutCancellationException) { failure("Provider timeout"); null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failure("Provider failure: ${e.javaClass.simpleName}")
                if (image != null) activeFailure = if (e is UnsupportedImage) e.message
                    else "看图暂时没完成，请确认所选模型支持图片，并检查连接。"
                null
            }
            val outcome = journey.deliver(ticket, raw, activeFailure) { user(it) }
            counters.terminal(ticket.active, outcome)
            diagnostic("terminal active=${ticket.active} outcome=$outcome ${counters.summary()}")
        }
    }
    fun cancel() {
        if (pending) {
            counters.terminal(requestActive, DeliveryOutcome.CANCELLED)
            diagnostic("cancel ${counters.summary()}")
        }
        request?.cancel(); request = null
    }
    fun reset() { cancel(); context.reset() }
}
