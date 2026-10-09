package com.kitt.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class RuntimeRequestOwner(val epoch: Long) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<RuntimeRequestOwner>
}

enum class RuntimeTask(val label: String) {
    DECIDING("AI 正在决策与生成讲述"), SEARCH_DECISION("AI 正在判断是否需要搜索"),
    OVERVIEW("正在搜索本地概况"), TOPIC("正在研究当地专题"), QUESTION("正在搜索你的问题")
}
enum class RequestOutcome { SUCCESS, FAILED, TIMEOUT, INVALID_RESPONSE, UNAVAILABLE }
data class RequestObservation(val outcome: RequestOutcome, val at: Long)
data class RuntimeWork(val id: Long, val task: RuntimeTask, val since: Long, val epoch: Long,
    val active: Boolean, val areaKey: String? = null, val job: Job? = null)

/** Ephemeral read-only projection of calls already made. No payloads, event history or new requests. */
class RuntimeRequests(private val clock: () -> Long, private val epoch: () -> Long) {
    var work by mutableStateOf<List<RuntimeWork>>(emptyList()); private set
    var ai by mutableStateOf<RequestObservation?>(null); private set
    var research by mutableStateOf<RequestObservation?>(null); private set
    private var aiConfig: ProviderConfig? = null
    private var researchConfig: ProviderConfig? = null
    private var serial = 0L
    private var generation = 0L
    @Synchronized fun select(config: ProviderConfig, searching: Boolean) {
        if (searching && researchConfig != config) { researchConfig = config; research = null }
        if (!searching && aiConfig != config) { aiConfig = config; ai = null }
    }
    @Synchronized fun clearWork() { generation++; work = emptyList() }
    @Synchronized private fun begin(task: RuntimeTask, active: Boolean, areaKey: String?, owner: Long, job: Job?): Pair<RuntimeWork, Long> {
        val item = RuntimeWork(++serial, task, clock(), owner, active, areaKey, job)
        work = work + item
        return item to generation
    }
    @Synchronized private fun finish(item: RuntimeWork, token: Long, config: ProviderConfig,
        searching: Boolean, outcome: RequestOutcome?) {
        work = work.filterNot { it.id == item.id }
        if (outcome == null || token != generation) return
        val value = RequestObservation(outcome, clock())
        if (searching && config == researchConfig) research = value
        if (!searching && config == aiConfig) ai = value
    }
    suspend fun <T> observe(config: ProviderConfig, task: RuntimeTask, active: Boolean,
        searching: Boolean = false, areaKey: String? = null, realService: Boolean = true,
        valid: (T) -> Boolean = { true }, call: suspend () -> T): T {
        select(config, searching)
        val (item, token) = begin(task, active, areaKey, currentCoroutineContext()[RuntimeRequestOwner]?.epoch ?: epoch(), currentCoroutineContext()[Job])
        var outcome: RequestOutcome? = null
        try {
            val value = call()
            currentCoroutineContext().ensureActive()
            if (realService) outcome = if (valid(value)) RequestOutcome.SUCCESS else RequestOutcome.INVALID_RESPONSE
            return value
        } catch (e: TimeoutCancellationException) {
            if (realService) outcome = RequestOutcome.TIMEOUT
            throw e
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            outcome = if (e is ResearchUnavailable) RequestOutcome.UNAVAILABLE else if (realService) RequestOutcome.FAILED else null
            throw e
        } finally { finish(item, token, config, searching, outcome) }
    }
}

class ObservedDirector(private val delegate: DirectorProvider, private val config: ProviderConfig,
    private val status: RuntimeRequests) : DirectorProvider {
    override val acceptsImages get() = delegate.acceptsImages
    override suspend fun researchNeed(request: DirectorRequest) = status.observe(config, RuntimeTask.SEARCH_DECISION,
        active = true, realService = config.kind != ProviderKind.FAKE) { delegate.researchNeed(request) }
    override suspend fun direct(request: DirectorRequest) = status.observe(config, RuntimeTask.DECIDING,
        active = request.userUtterance != null, realService = config.kind != ProviderKind.FAKE,
        valid = { runCatching { DirectorContract.parse(it) }.isSuccess }) { delegate.direct(request) }
}

class ObservedResearch(private val delegate: LocalResearchProvider, private val config: ProviderConfig,
    private val status: RuntimeRequests) : LocalResearchProvider {
    override val supportsTopics get() = delegate.supportsTopics
    private suspend fun <T> call(task: RuntimeTask, area: AreaIdentity, block: suspend () -> T): T =
        status.observe(config, task, active = task == RuntimeTask.QUESTION, searching = true,
            areaKey = area.key, realService = delegate !is UnavailableResearch && config.kind != ProviderKind.FAKE, call = block)
    override suspend fun research(area: AreaIdentity, at: Long) = call(RuntimeTask.OVERVIEW, area) { delegate.research(area, at) }
    override suspend fun overview(area: AreaIdentity, at: Long) = call(RuntimeTask.OVERVIEW, area) { delegate.overview(area, at) }
    override suspend fun topic(area: AreaIdentity, objectToResearch: OverviewObject, at: Long) =
        call(RuntimeTask.TOPIC, area) { delegate.topic(area, objectToResearch, at) }
    override suspend fun researchQuestion(query: LocalQuestion, at: Long) =
        call(RuntimeTask.QUESTION, query.area) { delegate.researchQuestion(query, at) }
}

data class DrivingRuntimeSnapshot(val position: String, val network: String, val services: String,
    val primary: String, val hint: String = "")

internal fun ageLabel(ms: Long): String = when {
    ms < 60_000 -> "${ms.coerceAtLeast(0) / 1000} 秒"
    ms < 3_600_000 -> "${ms / 60_000} 分钟"
    ms < 86_400_000 -> "${ms / 3_600_000} 小时"
    else -> "${ms / 86_400_000} 天"
}
internal fun observationLabel(value: RequestObservation?, elapsed: Long): String = if (value == null) "未验证" else {
    val result = when (value.outcome) {
        RequestOutcome.SUCCESS -> "最近成功"
        RequestOutcome.FAILED -> "最近失败"
        RequestOutcome.TIMEOUT -> "最近超时"
        RequestOutcome.INVALID_RESPONSE -> "回复无效"
        RequestOutcome.UNAVAILABLE -> "不可用"
    }
    "$result ${ageLabel(elapsed - value.at)}前"
}
internal fun positionLabel(fix: Fix?, wall: Long, elapsed: Long): String {
    if (fix == null || fix.ageMs(wall, elapsed) !in 0..60_000) return "定位未知 · 等待可靠位置"
    val quality = when {
        fix.source == FixSource.SIMULATED -> "模拟"
        fix.source == FixSource.LAST_KNOWN -> "粗略"
        fix.precise(wall, elapsed) -> "精确"
        else -> "粗略"
    }
    val source = if (fix.source == FixSource.LAST_KNOWN) "沿用 ${fix.originSource}" else fix.source.name
    return "$source · ±${kotlin.math.ceil(fix.accuracy).toInt()}m · ${fix.ageMs(wall, elapsed) / 1000}秒 · $quality"
}

/** Priority derives from Journey and currently owned work; it never schedules or cancels anything. */
internal fun runtimeActivity(journey: Journey, works: List<RuntimeWork>, voice: VoiceDetail,
    elapsed: Long, wall: Long, areaKey: String?, areaPendingSince: Long?,
    ai: RequestObservation?, research: RequestObservation?, speechSince: Long? = null, locationSince: Long? = null,
    locationPermissionUnavailable: Boolean = false): Pair<String, String> {
    val relevant = works.filter { it.job?.isActive != false }.filter { if (it.active || it.task in setOf(RuntimeTask.DECIDING, RuntimeTask.SEARCH_DECISION))
        it.epoch == journey.epoch else areaKey != null && (it.areaKey == areaKey || it.areaKey == journey.currentFix?.administrative?.copy(chapter = "")?.key) }
    fun label(work: RuntimeWork) = "${work.task.label} · 已等待 ${ageLabel(elapsed - work.since)}"
    val active = relevant.firstOrNull { it.active }
    val background = relevant.firstOrNull { !it.active && it.task == RuntimeTask.DECIDING }
        ?: relevant.firstOrNull { !it.active }
    val primary = when {
        !journey.running -> "开车上路后点一下开始"
        journey.isQuiet -> if (journey.quietRemaining == Long.MAX_VALUE) "安静模式 · 等你叫我" else
            "安静模式 · 剩余 ${ageLabel(journey.quietRemaining)}"
        journey.listening -> when (voice.phase) {
            VoicePhase.PREPARING_LISTEN -> "正在准备听你说话"
            VoicePhase.PROCESSING -> "正在识别你的话"
            else -> "正在听，请说话"
        }
        journey.awaitingReply -> "等待你的回答 · 可以打字"
        journey.imageInteraction -> "等待你的看图问题"
        active != null -> label(active)
        journey.speaking -> if (voice.phase == VoicePhase.SPEAKING) "正在讲述" else "正在准备语音" +
            (speechSince?.let { " · 已等待 ${ageLabel(elapsed - it)}" } ?: "")
        background != null -> label(background)
        journey.currentFix == null && locationPermissionUnavailable -> "定位权限不可用"
        journey.currentFix == null -> (if (journey.fix == null) "正在获取位置" else "位置已过时，等待可靠定位") +
            (locationSince?.let { " · 已等待 ${ageLabel(elapsed - it)}" } ?: "")
        areaPendingSince != null -> "正在识别所在地区 · 已等待 ${ageLabel(elapsed - areaPendingSince)}"
        journey.prepared != null -> "已有准备线索，等待新现场确认"
        journey.checkDelayReason() == "cooldown" -> "刚刚讲过或已接管，暂时等待新内容"
        listOfNotNull(ai, research).maxByOrNull { it.at }?.outcome in
            setOf(RequestOutcome.FAILED, RequestOutcome.TIMEOUT, RequestOutcome.INVALID_RESPONSE) -> "上次请求失败，等待下次触发"
        research?.outcome == RequestOutcome.UNAVAILABLE -> "本地研究不可用，等待新内容"
        else -> "正常运行，等待值得讲的新内容"
    }
    val hint = when {
        !journey.running -> ""
        (active != null || journey.speaking || journey.listening || journey.awaitingReply) && background != null -> label(background)
        journey.currentFix == null && primary != "正在获取位置" -> "定位暂不可用"
        journey.currentFix != null && journey.currentFix?.administrative == null -> "地区名称未解析；物理定位独立运行"
        else -> ""
    }
    return primary to hint
}
