package com.kitt.reader

import kotlinx.coroutines.*

class DirectorLoop(private val journey: Journey, private val context: ContextPipeline,
    private val scope: CoroutineScope, private val now: () -> Long,
    private val provider: () -> DirectorProvider, private val failure: (String) -> Unit = {}) {
    private var request: Job? = null
    fun location(fix: Fix) {
        journey.location(fix)
        if (journey.fix == fix) context.accept(fix)
        check()
    }
    fun check() {
        journey.tick()
        if (request?.isActive == true || !journey.shouldCheck()) return
        dispatch(null)
    }
    fun speak() { cancel(); journey.beginListening(::user) }
    fun user(text: String) {
        cancel()
        if (journey.requestInput(text)) dispatch(text)
    }
    private fun dispatch(utterance: String?) {
        val ticket = journey.ticket(utterance != null)
        val input = DirectorRequest(sessionInstructions = journey.instructions,
            contextCard = context.card(journey, now()), userUtterance = utterance)
        request = scope.launch {
            val raw = try { withTimeout(35000) { provider().direct(input) } }
            catch (e: TimeoutCancellationException) { failure("Provider timeout"); null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure("Provider failure: ${e.javaClass.simpleName}"); null }
            journey.deliver(ticket, raw, ::user)
        }
    }
    fun cancel() { request?.cancel(); request = null }
    fun reset() { cancel(); context.reset() }
}
