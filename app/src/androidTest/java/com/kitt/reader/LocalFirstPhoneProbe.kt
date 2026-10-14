package com.kitt.reader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Only natural physical measurements and the saved provider. No coordinates, credentials or text retained. */
internal fun Instrumentation.localFirstPhoneProbe(runtime: KittRuntime, args: Bundle?): Bundle {
    val expectedOnline = args?.getString("network") != "offline"
    val seconds = (args?.getString("seconds")?.toIntOrNull() ?: 120).coerceIn(15, 180)
    val report = JSONObject().put("scenario", if (expectedOnline) "online" else "offline")
        .put("measurement", "REAL_DEVICE; natural LocationManager, original installed target APK, no injected fixes")
    fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    fun stage(value: String) { sendStatus(1, Bundle().apply { putString("stage", value) }) }
    fun awaitSpeech(limit: Int = 35) = runBlocking {
        delay(500)
        var waited = 0
        while (waited < limit) {
            var busy = false
            main { busy = runtime.loop.pending || runtime.journey.speaking }
            if (!busy) break
            delay(1000); waited++
        }
    }
    val before = runtime.config
    val rate = runtime.settings.speechRate; val voice = runtime.settings.voiceName
    var director = 0; var research = 0; var observed = 0
    var freshGps = 0; var freshNetwork = 0; var trusted = 0; var owned = false
    val sources = linkedSetOf<String>(); val phases = linkedSetOf<String>()
    try {
        main {
            val target = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)
            report.put("version", target.versionName).put("version_code", target.longVersionCode)
                .put("provider", before.kind.name).put("model", before.model).put("effort", before.effort)
            check(target.longVersionCode == BuildConfig.VERSION_CODE.toLong())
            check(!runtime.journey.running && runtime.recovery == null)
            runtime.network.refresh()
            check(runtime.network.snapshot.physicalInternet == expectedOnline) { "Wrong physical Internet state" }
            report.put("physical_internet_before", runtime.network.snapshot.physicalInternet)
                .put("system_validated", runtime.network.snapshot.validated).put("vpn_detected", runtime.network.snapshot.vpn)
            director = runtime.requests.directorCalls; research = runtime.requests.researchCalls
            targetContext.startForegroundService(Intent(targetContext, JourneyService::class.java).setAction(JourneyService.START))
            owned = true
        }
        stage("automatic_observation_started")
        runBlocking {
            repeat(seconds) {
                delay(1000)
                main {
                    check(runtime.journey.running && !runtime.simulation)
                    check(runtime.network.snapshot.physicalInternet == expectedOnline) { "Network state changed" }
                    observed++
                    val fix = runtime.journey.fix
                    fix?.let { sources += it.source.name }
                    if (fix?.precise(System.currentTimeMillis()) == true) {
                        if (fix.source == FixSource.GPS) freshGps++
                        if (fix.source == FixSource.NETWORK) freshNetwork++
                    }
                    if (runtime.offlinePack?.resolve(fix, System.currentTimeMillis()) != null) trusted++
                    runtime.journey.narrationMemory.forEach { phases += it.phase.name }
                }
            }
        }
        main {
            report.put("automatic_director_calls_delta", runtime.requests.directorCalls - director)
                .put("automatic_research_calls_delta", runtime.requests.researchCalls - research)
                .put("automatic_ask_user", runtime.loop.counters.automatic[DeliveryOutcome.ASK_USER] ?: 0)
                .put("automatic_completed", runtime.journey.narrationMemory.count { it.phase == NarrationPhase.COMPLETED })
                .put("destination_untouched", runtime.journey.destination == "未询问")
                .put("offline_voice_ready", (runtime.voice as? AndroidVoice)?.offlineReady == true)
                .put("golden_chain", if (trusted > 0 && runtime.journey.narrationMemory.any { it.phase == NarrationPhase.COMPLETED }) "PASS_ENGINE_ONLY" else "PENDING_NO_TRUSTED_POSITION_OR_COMPLETION")
            check(runtime.requests.directorCalls == director && runtime.requests.researchCalls == research)
            check(runtime.journey.destination == "未询问")
        }
        stage("automatic_observation_finished")
        var localQuestions = 0
        for (question in listOf("再讲一点", "再说一遍", "这里有什么")) {
            var eligible = false
            main { eligible = runtime.offlinePack?.resolve(runtime.journey.fix, System.currentTimeMillis()) != null }
            if (!eligible) break
            stage("finite_local_question_${localQuestions + 1}")
            main { runtime.loop.user(question) }
            awaitSpeech()
            main {
                check(runtime.requests.directorCalls == director && runtime.requests.researchCalls == research)
                check(!runtime.loop.pending && !runtime.journey.speaking)
                check(!runtime.journey.notice.contains("语音播放不可用"))
                localQuestions++
            }
        }
        report.put("finite_local_questions_completed", localQuestions)
            .put("finite_local_questions", if (localQuestions == 3) "PASS_ENGINE_ONLY_ZERO_CLOUD" else "PENDING_NO_TRUSTED_POSITION")
        main {
            runtime.quietToggle()
            report.put("quiet_10min", runtime.journey.isQuiet && runtime.journey.quietRemaining in 599000..600000)
            runtime.quietToggle(); report.put("quiet_early_exit", !runtime.journey.isQuiet)
        }
        if (expectedOnline && args?.getString("active") == "true") {
            stage("one_explicit_online_question_started")
            main { runtime.loop.user("为什么天空是蓝色的？只解释一般原理。") }
            runBlocking {
                var waited = 0
                while (waited < 145) {
                    var busy = false
                    main { busy = runtime.loop.pending || runtime.journey.speaking }
                    if (!busy) break
                    delay(1000); waited++
                }
            }
            main {
                report.put("explicit_director_calls_delta", runtime.requests.directorCalls - director)
                    .put("explicit_research_calls_delta", runtime.requests.researchCalls - research)
                    .put("explicit_ai_outcome", runtime.requests.ai?.outcome?.name ?: "NEVER_ATTEMPTED")
                    .put("explicit_failure_delivered", (runtime.loop.counters.active[DeliveryOutcome.FAILURE] ?: 0) > 0)
                    .put("explicit_pending_after_wait", runtime.loop.pending)
                // Finite local questions can only demonstrate recovery if there is a trusted covered position.
                report.put("post_explicit_local_recovery", if (runtime.offlinePack?.resolve(runtime.journey.fix, System.currentTimeMillis()) == null)
                    "PENDING_NO_TRUSTED_POSITION" else "PENDING_REQUIRES_LISTENING_AND_FOLLOWUP")
            }
            var eligible = false
            var calls = 0; var searches = 0
            main {
                eligible = runtime.offlinePack?.resolve(runtime.journey.fix, System.currentTimeMillis()) != null
                calls = runtime.requests.directorCalls; searches = runtime.requests.researchCalls
                if (eligible) runtime.loop.user("这里有什么")
            }
            if (eligible) {
                awaitSpeech()
                main {
                    check(runtime.requests.directorCalls == calls && runtime.requests.researchCalls == searches)
                    check(!runtime.loop.pending && !runtime.journey.speaking)
                    check(!runtime.journey.notice.contains("语音播放不可用"))
                    report.put("post_explicit_local_recovery", "PASS_ENGINE_ONLY_ZERO_ADDITIONAL_CLOUD")
                }
            }
        }
    } catch (e: Exception) {
        report.put("failure_class", e.javaClass.simpleName).put("golden_chain", "PENDING")
    } finally {
        main {
            report.put("settings_unchanged", runtime.config == before && runtime.settings.speechRate == rate && runtime.settings.voiceName == voice)
            if (owned) { runtime.end(); runtime.dismissSummary(); targetContext.stopService(Intent(targetContext, JourneyService::class.java)) }
            report.put("journey_ended", !runtime.journey.running)
        }
    }
    report.put("observed_seconds", observed).put("fresh_gps_seconds", freshGps).put("fresh_network_seconds", freshNetwork)
        .put("trusted_county_seconds", trusted).put("sources", sources.joinToString(",")).put("phases", phases.joinToString(","))
        .put("human_hearing", "PENDING_EXPLICIT_LISTENER_FEEDBACK")
    return Bundle().apply { putString("report", report.toString()) }
}
