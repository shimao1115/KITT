package com.kitt.reader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.*

class KittRuntime(private val context: Context, injectedVoice: VoicePort? = null) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val revision = mutableIntStateOf(0)
    val voice: VoicePort = injectedVoice ?: AndroidVoice(context)
    val store = TripStore(context)
    val settings = SettingsStore(context)
    private val diagnostics = DebugDiagnostics(context)
    val chatGpt = ChatGptAccount(settings, transport = ChatGptHttpsTransport { diagnostics.log("KITTAuth", it) },
        diagnostic = { diagnostics.log("KITTAuth", it) }, keepAlive = { active ->
        val intent = android.content.Intent(context, ChatGptAuthService::class.java)
        if (active) androidx.core.content.ContextCompat.startForegroundService(context, intent)
        else context.stopService(intent)
    })
    var recovery = store.recovery(); private set
    var resumeRequested = false
    val journey = Journey(System::currentTimeMillis, voice) {
        revision.intValue++
        checkpointIfChanged()
    }
    val pipeline = ContextPipeline(context.assets.open("landmarks.json").bufferedReader().use { LandmarkCatalog.parse(it.readText()) })
    var config = settings.read()
        set(value) { field = value; requests.select(value, false); requests.select(settings.readResearch() ?: value, true) }
    val requests = RuntimeRequests(android.os.SystemClock::elapsedRealtime) { journey.epoch }
    val network = AndroidNetworkStatus(context)
    var foregroundVisible by androidx.compose.runtime.mutableStateOf(true); private set
    fun provider(): DirectorProvider {
        requests.select(config, false)
        val selected = when (config.kind) {
        ProviderKind.CHATGPT -> ChatGptProvider(chatGpt, config)
        ProviderKind.FAKE -> FakeProvider()
        else -> ApiProvider(config)
        }
        return ObservedDirector(selected, config, requests)
    }
    private var researchConfig: ProviderConfig? = null
    private var selectedResearch: LocalResearchProvider = UnavailableResearch()
    fun resetResearchProvider() { researchConfig = null }
    fun researchProvider(): LocalResearchProvider {
        val configured = settings.readResearch() ?: config
        if (researchConfig != configured) {
            researchConfig = configured
            val selected = when (configured.kind) {
                ProviderKind.CHATGPT -> ChatGptLocalResearch(chatGpt, configured)
                ProviderKind.OPENAI -> ApiLocalResearch(configured)
                ProviderKind.FAKE -> UnavailableResearch("离线演示没有实际搜索；当地事实研究不可用，演示内容不是研究验收。")
                ProviderKind.COMPATIBLE -> UnavailableResearch("兼容聊天 API 未声明搜索能力；本地研究不可用。可在设置中显式配置独立的研究通路。")
            }
            selectedResearch = ObservedResearch(selected, configured, requests)
        }
        requests.select(configured, true)
        return selectedResearch
    }
    val routeReference = RouteReference(scope, ::provider)
    val loop = DirectorLoop(journey, pipeline, scope, System::currentTimeMillis, {
        provider()
    }, failure = { Log.w("KITT", it) }, diagnostic = {
        diagnostics.log("KITTResearch", it)
        if (simulation) diagnostics.log("KITTSim", "$it progressMeters=${(source as? SimulatedLocationSource)?.traveledMeters?.toInt() ?: 0}")
    }, researchProvider = ::researchProvider, researchChanged = { revision.intValue++ })
    val visualTalk = VisualTalk(journey, loop, scope, ::provider)
    var simulation = false; private set
    var acceleration = settings.acceleration
    var simulationSpeed = settings.simulationSpeed
    var source: LocationSource? = null; private set
    var sourceNotice = ""; private set
    var summary: TripSummary? = null; private set
    private var ticker: Job? = null
    private var lastCheckpoint = ""
    private var travelCheckpointMs = 0L
    var notificationChanged: (() -> Unit)? = null
    init {
        java.io.File(context.cacheDir, "visual-talk/capture.jpg").delete()
        (voice as? AndroidVoice)?.speechRate = settings.speechRate
        (voice as? AndroidVoice)?.voiceName = settings.voiceName
        journey.diagnostic = { diagnostics.log(if (simulation) "KITTSim" else "KITT", it) }
    }
    val sourceLabel: String get() = if (simulation) "新都→安州雎水 · 粗粒度模拟 / ${simulationSpeed.toInt()} km/h / ${acceleration.toInt()}×（非导航级）${sourceNotice}" else {
        val fix = journey.currentFix
        if (!journey.running) "手机定位 · GPS / 系统网络辅助" else if (fix == null) "定位未知${sourceNotice.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}" else
            "${fix.source} · ±${fix.accuracy.toInt()} m · ${fix.ageMs(System.currentTimeMillis()) / 1000} 秒${sourceNotice.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
    }
    fun statusSnapshot(elapsed: Long): DrivingRuntimeSnapshot {
        val wall = System.currentTimeMillis()
        val (primary, hint) = runtimeActivity(journey, requests.work,
            (voice as? AndroidVoice)?.detail ?: VoiceDetail(), elapsed, wall,
            pipeline.areas.active?.area?.key, (source as? RealLocationSource)?.areaLookupSince,
            requests.ai, requests.research, (voice as? AndroidVoice)?.speechQueuedSince, (source as? RealLocationSource)?.waitingSince,
            locationPermissionUnavailable = sourceNotice.contains("权限"))
        val services = (if (config.kind == ProviderKind.FAKE) "AI 离线演示 · 未验证" else "AI ${observationLabel(requests.ai, elapsed)}") +
            " / 研究 ${observationLabel(requests.research, elapsed)}"
        return DrivingRuntimeSnapshot(positionLabel(journey.currentFix, wall, elapsed), network.snapshot.label, services,
            primary, hint.ifBlank { sourceNotice.takeIf { journey.running }.orEmpty() })
    }
    fun start(simulated: Boolean = false) {
        visualTalk.clear()
        val routeHint = routeReference.beginJourney()
        val saved = if (resumeRequested) recovery else null
        resumeRequested = false
        stopSources(); travelCheckpointMs = saved?.travelMs ?: 0L; summary = null; sourceNotice = ""; recovery = null
        simulation = saved?.simulation ?: simulated
        if (saved != null) {
            acceleration = saved.acceleration
            journey.restore(saved.started, saved.destination, saved.instructions, saved.topics, saved.quietUntil)
        } else journey.start()
        loop.reset(); loop.counters.clear()
        pipeline.routeHint = routeHint
        source = if (simulation) {
            val fixture = context.assets.open("chengdu-mianyang.json").bufferedReader().use { RouteFixture.parse(it.readText()) }
            SimulatedLocationSource(fixture, scope, System::currentTimeMillis, speedKmh = simulationSpeed, acceleration = acceleration,
                initialTravelMs = saved?.travelMs ?: 0L, paused = { loop.simulationPaused }) {
                diagnostics.log("KITTSim", "route completed ${loop.counters.summary()}")
                sourceNotice = "模拟已到终点"; revision.intValue++
            }
        } else RealLocationSource(context) { sourceNotice = it; revision.intValue++ }
        (source as? SimulatedLocationSource)?.let { simulatedSource ->
            journey.simulationCadence({ simulatedSource.simulatedTravelMs }, { simulatedSource.traveledMeters })
        }
        network.start()
        source?.start(loop::location)
        ticker = scope.launch {
            var quiet = journey.isQuiet
            while (isActive && journey.running) {
                delay(1000); visualTalk.sync(); loop.check()
                if (System.currentTimeMillis() / 1000 % 10 == 0L) {
                    checkpoint()
                    if (simulation) diagnostics.log("KITTSim", "progress meters=${(source as? SimulatedLocationSource)?.traveledMeters?.toInt()} paused=${loop.simulationPaused} research_pending=${loop.research.pending}")
                }
                if (quiet != journey.isQuiet) { quiet = journey.isQuiet; notificationChanged?.invoke() }
            }
        }
        revision.intValue++
    }
    fun quietToggle() {
        loop.cancel(); if (journey.isQuiet) journey.resume() else journey.quiet()
        notificationChanged?.invoke()
    }
    fun end() {
        visualTalk.clear(); java.io.File(context.cacheDir, "visual-talk/capture.jpg").delete()
        routeReference.clear()
        if (!journey.running && recovery == null) { stopSources(); return }
        if (journey.running) summary = journey.end()
        else recovery?.let { summary = TripSummary(it.started, System.currentTimeMillis(), it.destination, it.topics, 0) }
        stopSources(); recovery = null; resumeRequested = false
        summary?.let { store.finish(it) }; store.clearRecovery(); revision.intValue++
    }
    fun dismissSummary() { summary = null; revision.intValue++ }
    fun developer(speed: Double = acceleration, speedKmh: Double = simulationSpeed) {
        if (journey.running) return
        acceleration = speed; simulationSpeed = speedKmh
        settings.developer(acceleration, simulationSpeed); revision.intValue++
    }
    fun checkpoint() { store.checkpoint(journey, simulation, acceleration, (source as? SimulatedLocationSource)?.travelMs ?: travelCheckpointMs) }
    private fun checkpointIfChanged() {
        // Ticks/GPS do not write a raw track. Only intent, quiet and topic summaries change this key.
        val key = "${journey.started}|${journey.destination}|${journey.instructions}|${journey.quietUntil}|${journey.recentTopics}"
        if (journey.running && key != lastCheckpoint) { lastCheckpoint = key; checkpoint() }
    }
    fun serviceLost() {
        visualTalk.clear(); java.io.File(context.cacheDir, "visual-talk/capture.jpg").delete()
        routeReference.clear()
        checkpoint(); stopSources()
        if (journey.running) {
            val saved = store.recovery(); journey.end(); recovery = saved; revision.intValue++
        }
    }
    fun stopSources() {
        travelCheckpointMs = (source as? SimulatedLocationSource)?.travelMs ?: travelCheckpointMs
        ticker?.cancel(); ticker = null; source?.stop(); source = null; loop.reset()
        requests.clearWork()
        if (!foregroundVisible) network.stop()
        simulation = false; sourceNotice = ""; revision.intValue++
    }
    fun locationUnavailable(message: String) { sourceNotice = message; revision.intValue++ }
    fun foreground(value: Boolean, permissionDialog: Boolean = false) {
        foregroundVisible = value
        if (value || journey.running) network.start() else network.stop()
        journey.foreground = value
        if (!value && journey.listening && !permissionDialog) { loop.cancel(); journey.invalidateProvider() }
    }
}
