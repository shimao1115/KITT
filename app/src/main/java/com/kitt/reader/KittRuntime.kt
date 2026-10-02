package com.kitt.reader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.*

class KittRuntime(private val context: Context, injectedVoice: VoicePort? = null) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val revision = mutableIntStateOf(0)
    val voice: VoicePort = injectedVoice ?: AndroidVoice(context)
    val journey = Journey(System::currentTimeMillis, voice) { revision.intValue++ }
    val pipeline = ContextPipeline()
    var config = ProviderConfig()
    val loop = DirectorLoop(journey, pipeline, scope, System::currentTimeMillis, {
        if (config.kind == ProviderKind.FAKE) FakeProvider() else ApiProvider(config)
    }) { Log.w("KITT", it) }
    var simulation = false
    var acceleration = 1.0
    var source: LocationSource? = null; private set
    var sourceNotice = ""; private set
    var summary: TripSummary? = null; private set
    private var ticker: Job? = null
    var notificationChanged: (() -> Unit)? = null
    val sourceLabel: String get() = if (simulation) "成都→绵阳 · 粗粒度模拟 / 80 km/h / ${acceleration.toInt()}×（非导航级）${sourceNotice}" else "手机 GPS · ${sourceNotice.ifBlank { "无地图增强" }}"
    fun start() {
        stopSources(); summary = null; sourceNotice = ""; journey.start(); loop.reset()
        source = if (simulation) {
            val fixture = context.assets.open("chengdu-mianyang.json").bufferedReader().use { RouteFixture.parse(it.readText()) }
            SimulatedLocationSource(fixture, scope, System::currentTimeMillis, acceleration = acceleration) {
                sourceNotice = "模拟已到终点"; revision.intValue++
            }
        } else RealLocationSource(context) { sourceNotice = it; revision.intValue++ }
        source?.start(loop::location)
        ticker = scope.launch {
            var quiet = journey.isQuiet
            while (isActive && journey.running) {
                delay(1000); loop.check()
                if (quiet != journey.isQuiet) { quiet = journey.isQuiet; notificationChanged?.invoke() }
            }
        }
        revision.intValue++
    }
    fun quietToggle() {
        loop.cancel(); if (journey.isQuiet) journey.resume() else journey.quiet()
        notificationChanged?.invoke()
    }
    fun end() { stopSources(); if (journey.running) summary = journey.end(); revision.intValue++ }
    fun stopSources() { ticker?.cancel(); ticker = null; source?.stop(); source = null; loop.reset() }
    fun foreground(value: Boolean) {
        journey.foreground = value
        if (!value && journey.listening) { loop.cancel(); journey.invalidateProvider() }
    }
}
