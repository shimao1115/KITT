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
    private val diagnostic: (String) -> Unit = {},
    private val researchProvider: () -> LocalResearchProvider = { UnavailableResearch() },
    researchChanged: () -> Unit = {}) {
    private var request: Job? = null
    private var requestActive = false
    val pending get() = request?.isActive == true
    val counters = DirectorCounters()
    private var lastDelay = ""
    val research = ChapterResearch(scope, now, researchProvider, diagnostic) { researchChanged(); check() }
    init { context.researchCard = research::card }
    fun location(fix: Fix) {
        journey.location(fix)
        if (journey.fix == fix) {
            val previous = context.areas.active?.area?.key
            context.accept(fix)
            val entered = context.areas.active?.area?.key != previous
            if (entered) {
                diagnostic("area chapter=${context.areas.active?.area?.label ?: "unresolved"} cache=${context.areas.size}")
                journey.clearChapterEntry()
                research.enter(context.areas.active?.area)
            }
        }
        check()
    }
    fun check() {
        journey.tick()
        if (journey.fix?.precise(now()) != true) context.proximity.expire()
        if (!journey.running) { research.clear(); return }
        if (research.opportunity) journey.noteChapterEntry()
        val delay = if (pending) "director_in_flight" else journey.checkDelayReason(context.proximity.opportunity)
        if (delay != null) {
            val state = "$delay research_pending=${research.pending} chapter_retained=${research.opportunity}"
            if (state != lastDelay) { diagnostic("director delayed reason=$state"); lastDelay = state }
            return
        }
        lastDelay = ""
        counters.opportunity()
        dispatch(null)
    }
    fun speak() { research.cancelTopic(); cancel(); journey.beginListening(::user) }
    fun user(text: String, image: ImageInput? = null) {
        research.cancelTopic()
        cancel()
        if (journey.requestInput(text)) dispatch(text, image)
    }
    private fun dispatch(utterance: String?, image: ImageInput? = null) {
        requestActive = utterance != null
        val ticket = journey.ticket(utterance != null)
        var deliveryTicket = ticket
        val landmarkIds = context.proximity.ids
        val chapterKey = context.areas.active?.area?.key
        if (!ticket.active) {
            if (context.proximity.opportunity) diagnostic("landmark opportunity ids=${landmarkIds.joinToString()}")
        }
        counters.dispatch(ticket.active)
        diagnostic("dispatch active=${ticket.active} ${counters.summary()}")
        request = scope.launch {
            // A dispatch cancelled before this coroutine runs has never examined the context.
            // Snapshot evidence at the actual check, including research completed before this coroutine ran.
            val continuation = if (utterance?.trim() in setOf("再讲一点", "再讲点", "继续讲", "详细一点", "多讲一点") && journey.topic.isNotBlank())
                "\n【当前主题追问】用户要继续刚才的“${journey.topic}”。优先使用该对象已完成专题中的新事实，补充未讲角度，不重复基础介绍，也不另换对象；没有新依据则按原主动搜索规则处理。" else ""
            val input = DirectorRequest(sessionInstructions = journey.instructions,
                contextCard = context.card(journey, now()) + continuation, userUtterance = utterance, image = image)
            if (!ticket.active) { research.checked(); context.proximity.consumeOpportunity(landmarkIds) }
            diagnostic("director started active=${ticket.active} research_pending=${research.pending} chapter=$chapterKey")
            var activeFailure: String? = null
            val raw = try { withTimeout(if (ticket.active) 140000 else 35000) { withContext(RuntimeRequestOwner(ticket.epoch)) {
                val selected = provider()
                if (image != null && !selected.acceptsImages) throw UnsupportedImage()
                var groundedInput = input
                if (ticket.active && (image == null || ActiveResearchPolicy.forced(utterance.orEmpty()))) {
                    val needs = if (ActiveResearchPolicy.forced(utterance.orEmpty())) ResearchNeed.SEARCH_REQUIRED
                        else withTimeout(35000) { selected.researchNeed(input) }
                    diagnostic("active research decision=$needs")
                    if (needs == ResearchNeed.SEARCH_REQUIRED) {
                        deliveryTicket = ticket.copy(researching = true)
                        val area = ticket.fix?.administrative ?: AreaIdentity()
                        try {
                            val dossier = withTimeout(90000) { researchProvider().researchQuestion(
                                LocalQuestion(area, utterance.orEmpty(), input.contextCard), now()) }
                            ensureActive()
                            // Active questions remain about their original place if GPS changes; label it explicitly.
                            groundedInput = input.copy(contextCard = input.contextCard + "\n【当前主动问题的搜索证据】\n" + dossier.text() +
                                "\n以上是提问时现场，不证明回答时仍在那里；附近只按区域关联，不伪造精确距离或方向。")
                            diagnostic("active research ready sources=${dossier.sources.joinToString { it.url }} facts=${dossier.facts.size}")
                        } catch (_: TimeoutCancellationException) {
                            diagnostic("active research failed reason=timeout")
                            throw ActiveResearchFailure()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            diagnostic("active research failed reason=${e.javaClass.simpleName}")
                            // Local failure semantics: never let model memory impersonate failed search.
                            throw ActiveResearchFailure()
                        }
                    } else groundedInput = input.copy(contextCard = input.contextCard +
                        "\n【本次未联网】现有证据或一般常识足够直接回答；不能声称‘我刚查到’。")
                }
                withTimeout(35000) { selected.direct(groundedInput) }
            } } }
            catch (e: TimeoutCancellationException) { failure("Provider timeout"); null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failure("Provider failure: ${e.javaClass.simpleName}")
                if (e is ActiveResearchFailure) activeFailure = "刚才没查到可靠资料，暂时无法确认。"
                if (image != null && e !is ActiveResearchFailure) activeFailure = if (e is UnsupportedImage) e.message
                    else "看图暂时没完成，请确认所选模型支持图片，并检查连接。"
                null
            }
            val parsed = raw?.let { runCatching { DirectorContract.parse(it) }.getOrNull() }
            if (journey.fix?.precise(now()) != true) context.proximity.expire()
            val chapterBlock = if (!ticket.active && chapterKey != context.areas.active?.area?.key) DeliveryOutcome.STALE else null
            val outcome = journey.deliver(deliveryTicket, raw, activeFailure, chapterBlock ?: context.proximity.guard(parsed, ticket.active, landmarkIds)) { user(it) }
            if (outcome == DeliveryOutcome.SPEAK_NOW && parsed != null) {
                context.proximity.delivered(parsed)
                // A narrated object (or the highest salient remaining lead) may now be researched in the background.
                // This does not delay delivery, consume GPS opportunities, or pause simulation.
                if (!ticket.active && chapterBlock == null) research.deepen(parsed.topic + "\n" + parsed.narration)
            }
            counters.terminal(ticket.active, outcome)
            diagnostic("terminal active=${ticket.active} chapter=$chapterKey outcome=$outcome topic=${parsed?.topic.orEmpty()} reason=${parsed?.memoryUpdate.orEmpty()} ${counters.summary()}")
        }
    }
    fun cancel() {
        if (pending) {
            counters.terminal(requestActive, DeliveryOutcome.CANCELLED)
            diagnostic("cancel ${counters.summary()}")
        }
        request?.cancel(); request = null
    }
    val simulationPaused get() = pending || journey.speaking || journey.listening || journey.awaitingReply || journey.imageInteraction
    fun reset() { cancel(); research.clear(); journey.clearChapterEntry(); context.reset(); lastDelay = "" }
}
