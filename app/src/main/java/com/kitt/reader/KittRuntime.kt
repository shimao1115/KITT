package com.kitt.reader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.*

class KittRuntime(private val context: Context, injectedVoice: VoicePort? = null) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val revision = mutableIntStateOf(0)
    val voice: VoicePort = injectedVoice ?: AndroidVoice(context)
    val store = TripStore(context)
    val settings = SettingsStore(context)
    var recovery = store.recovery(); private set
    var resumeRequested = false
    val journey = Journey(System::currentTimeMillis, voice) {
        revision.intValue++
        checkpointIfChanged()
    }
    val pipeline = ContextPipeline()
    var config = settings.read()
    val loop = DirectorLoop(journey, pipeline, scope, System::currentTimeMillis, {
        if (config.kind == ProviderKind.FAKE) FakeProvider() else ApiProvider(config)
    }) { Log.w("KITT", it) }
    var simulation = settings.simulation
    var acceleration = settings.acceleration
    var source: LocationSource? = null; private set
    var sourceNotice = ""; private set
    var summary: TripSummary? = null; private set
    private var ticker: Job? = null
    private var lastCheckpoint = ""
    private var travelCheckpointMs = 0L
    var notificationChanged: (() -> Unit)? = null
    init {
        (voice as? AndroidVoice)?.speechRate = settings.speechRate
        journey.diagnostic = { Log.w("KITT", it) }
    }
    val sourceLabel: String get() = if (simulation) "成都→绵阳 · 粗粒度模拟 / 80 km/h / ${acceleration.toInt()}×（非导航级）${sourceNotice}" else "手机 GPS · ${sourceNotice.ifBlank { "无地图增强" }}"
    fun start() {
        val saved = if (resumeRequested) recovery else null
        resumeRequested = false
        stopSources(); travelCheckpointMs = saved?.travelMs ?: 0L; summary = null; sourceNotice = ""; recovery = null
        if (saved != null) {
            simulation = saved.simulation; acceleration = saved.acceleration
            journey.restore(saved.started, saved.destination, saved.instructions, saved.topics, saved.quietUntil)
        } else journey.start()
        loop.reset()
        source = if (simulation) {
            val fixture = context.assets.open("chengdu-mianyang.json").bufferedReader().use { RouteFixture.parse(it.readText()) }
            SimulatedLocationSource(fixture, scope, System::currentTimeMillis, acceleration = acceleration, initialTravelMs = saved?.travelMs ?: 0L) {
                sourceNotice = "模拟已到终点"; revision.intValue++
            }
        } else RealLocationSource(context) { sourceNotice = it; revision.intValue++ }
        source?.start(loop::location)
        ticker = scope.launch {
            var quiet = journey.isQuiet
            while (isActive && journey.running) {
                delay(1000); loop.check()
                if (System.currentTimeMillis() / 1000 % 10 == 0L) checkpoint()
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
        if (!journey.running && recovery == null) { stopSources(); return }
        if (journey.running) summary = journey.end()
        else recovery?.let { summary = TripSummary(it.started, System.currentTimeMillis(), it.destination, it.topics, 0) }
        stopSources(); recovery = null; resumeRequested = false
        summary?.let { store.finish(it) }; store.clearRecovery(); revision.intValue++
    }
    fun dismissSummary() { summary = null; revision.intValue++ }
    fun developer(simulated: Boolean = simulation, speed: Double = acceleration) {
        if (journey.running) return
        simulation = simulated; acceleration = speed; settings.developer(simulation, acceleration); revision.intValue++
    }
    fun checkpoint() { store.checkpoint(journey, simulation, acceleration, (source as? SimulatedLocationSource)?.travelMs ?: travelCheckpointMs) }
    private fun checkpointIfChanged() {
        // Ticks/GPS do not write a raw track. Only intent, quiet and topic summaries change this key.
        val key = "${journey.started}|${journey.destination}|${journey.instructions}|${journey.quietUntil}|${journey.recentTopics}"
        if (journey.running && key != lastCheckpoint) { lastCheckpoint = key; checkpoint() }
    }
    fun serviceLost() {
        checkpoint(); stopSources()
        if (journey.running) {
            val saved = store.recovery(); journey.end(); recovery = saved; revision.intValue++
        }
    }
    fun stopSources() {
        travelCheckpointMs = (source as? SimulatedLocationSource)?.travelMs ?: travelCheckpointMs
        ticker?.cancel(); ticker = null; source?.stop(); source = null; loop.reset()
    }
    fun locationUnavailable(message: String) { sourceNotice = message; revision.intValue++ }
    fun foreground(value: Boolean, permissionDialog: Boolean = false) {
        journey.foreground = value
        if (!value && journey.listening && !permissionDialog) { loop.cancel(); journey.invalidateProvider() }
    }
}
