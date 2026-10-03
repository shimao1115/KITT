# M1.3 accelerated simulation acceptance — 2026-10-03 (Asia/Shanghai)

## Reproduction and cause

The installed M1.2 build on vivo V2405A (API 36), with the existing ChatGPT account and **100 km/h + 16×**, reached Mianyang without asking the destination or narrating. The end page showed `旅程意图：未询问` and `这一程以安静相伴`. No credentials were extracted or changed.

The retained regression `reproduceLegacy100Kmh16xDropsEveryFiveSecondAutoResponse` reproduces the old configuration with the production Journey/Director/continuous source and a provider returning a valid SPEAK_NOW after five seconds: **6 requests, 6 spatially stale results, 0 voice outputs** over 110,452 m. It deliberately leaves the simulation cadence and pause hooks disabled. At 16×, five seconds represents about 2,222 m, exceeding the unchanged 1,500 m response safeguard; the wall-clock 45-second floor also limits opportunities.

## Implementation boundaries

Only explicit developer simulation/recovery installs a simulated cadence clock and route-progress reader. Ordinary Start still uses RealLocationSource. The original real-GPS 45-second floor, 1,500/3,000 m thresholds, five-minute fallback, speech/dialogue cooldowns, freshness and stale-response checks retain their original behavior. ChatGPT OAuth, catalog, provider payload, SSE and inference code are unchanged.

Simulation checks become eligible after 9 km of new route progress, or a changed fixture area after at least 5 km, with a 45-second **simulated driving** floor. Each request consumes its opportunity, including failures/SILENT; active dialogue also establishes a new simulated baseline. This fixture produces about 14 automatic opportunities rather than a request every second. These are opportunities to decide, never instructions to narrate.

The continuous source omits time while a Director request, TTS or one-shot listening is active. It still publishes fresh wall-clock fixes at the frozen position. Transition intervals are conservatively omitted at one-second sampling resolution; resuming never catches up the paused travel. Recovery checkpoints contain only travel actually advanced, using the existing travelMs representation. Simulation soft cooldowns use simulated driving time. Explicit quiet durations, request timeout/age, GPS freshness, PREPARE expiry and question/skip suppression stay on wall clock; **10 minutes remains 600 real seconds** at both 16× and 60×.

Diagnostics are local: hidden developer counters and `KITTSim` action/request/voice events. Automatic and active counters are separate, including SILENT/SPEAK_NOW/PREPARE/ASK_USER and stale/cancel/failure/suppressed outcomes. Dispatch/terminal events include coarse fixture progress in meters. No tokens, keys, prompts, raw responses, narration, user input, recordings or GPS arrays are logged.

## Evidence-driven Context adjustment

The first cadence/pause fix was installed and run through the entire real-ChatGPT route **before** adjusting Context. From **09:31:29 to 09:36:50**, it produced **14 automatic requests, 14 valid SILENT, 0 SPEAK_NOW/PREPARE/ASK_USER, 0 stale/cancel/failure**. This establishes genuine model decisions rather than lost responses. The original fixture clue chiefly denied real scene knowledge; the constitution already permitted stable general mechanisms. Twelve consecutive accepted SILENT decisions were observed before making the small Context change; the complete distribution above was retained.

The final Context describes only the current coordinate-backed fixture stage (existing area A→area B), explains compressed travel between interactions and distinguishes elapsed simulated driving from elapsed wall time since speech. At the initial simulated fix it clarifies that `未询问` means the destination has not yet been asked. It permits explaining worthwhile stable general mechanisms from the area/movement relationship, retaining SILENT as valid. It invents no bridge, road, tunnel, local history, terrain observation, record or knowledge base. The system constitution and real-GPS Context are unchanged; real ChatGPT still receives the live Context Card, not Fake text.

## Final phone run

The same phone/account retained **ChatGPT / gpt-5.6-luna / high**, with no Platform key or new OAuth step. The final APK was installed with `adb install -r`. At **09:37:37** the hidden developer dialog started **100 km/h + 16×**.

- Initial automatic ASK_USER completed in about 6.6 seconds at **0 m**; Chinese TTS completed successfully and opened one recognition window. That window returned no recognized answer; it was abandoned without repeated destination questions. The configured recognition service is vivo Copilot. Successful destination transcription is not claimed.
- At **15,590 m**, an automatic request from **09:38:25.601 to 09:38:38.131** returned **SPEAK_NOW** after **12.53 seconds**. Its terminal progress remained **15,590 m**. The UI showed `正在讲述` and `平原道路为何不总是笔直`. TTS completed successfully at **09:39:11.398**, about **33.3 seconds** later, then simulated driving resumed.
- The human user explicitly confirmed **hearing both the destination question and automatic narration**. No manual prompt forced this narration.
- A second automatic SPEAK_NOW at **73,076 m** survived **14.96 seconds** of provider latency and completed TTS successfully at **09:42:43.744**. No manual prompt forced either narration.
- The route completed at **09:44:30.598**: **14 opportunities/requests; SILENT 11, SPEAK_NOW 2, ASK_USER 1, PREPARE 0; stale 0, cancelled 0, failure 0, suppressed 0; active requests 0**. The end page retained the two short theme summaries. An unanswered destination remained `未提供`, without nagging.
- After explicit End → departure, the simulation/completion label cleared. Settings → Android Back returned to main. Ordinary Start showed **手机 GPS**, and Android registered KITT's real GPS listener at **2 seconds / 5 m**. End → departure again left a clean real-GPS idle screen, with no active journey. A fresh physical GPS fix was not established indoors.
- The installed `base.apk` was pulled and its SHA-256 matched the final packaged artifact exactly. No new login, model/provider change or credential manipulation was required.

Local non-secret logs/UI captures are retained under ignored `artifacts/m1.3-*`. This run proves the simulator/real-ChatGPT/TTS delivery gate. It does not claim successful physical GPS reception, successful ASR transcription, subjective accuracy of every future narration, or OEM lock-screen endurance. The existing developer text fallback remains available when the system recognizer cannot provide a result.

The 11 final SILENT decisions passed schema/delivery validation. The six-field contract has no decision-reason field, and full responses/private prompts are not retained, so their individual reasons cannot be established retrospectively. Sparse coarse scene information, no verified local nodes/search and avoidance of recently explained themes are plausible explanations from the supplied Context/constitution, not recorded model reasons. Accepted SILENT proves a functioning decision path, not the subjective quality of each decision.

## Build and regression gates

```powershell
.\scripts\verify.ps1 -Offline
.\gradlew.bat build lintRelease --offline --console=plain
```

Both pass. **70 tests in each of Debug and Release**, **0 failures/errors/skips**, preserving all original 60 tests, including 18 M1.1 OAuth/inference tests and eight M1.2 regressions. Ten new regressions cover the legacy reproduction, unchanged real-GPS boundaries, delayed SILENT/voice full routes at 16×, 60× smoke cadence, request/TTS/listening pause and fresh fixes, wall-clock quiet, cancellation/interruption, PREPARE/epoch/stale protection, and simulation Context/ordinary-session reset.

The new **100 km/h + 16×** full-route delayed-provider test has **14 auto opportunities, 15 requests (one active reply), 13 automatic SPEAK_NOW, one ASK_USER, 15 voice outputs, 0 stale/cancel/failure** with five-second provider delays and 20-second TTS. The original Fake golden path remains **64 calls, 4 outputs, 110,452 m, 82 simulated minutes at 80 km/h**. Reports: `artifacts/accelerated-simulation.txt`, `artifacts/full-simulation.txt`, both variant test reports.

Debug/Release lint: **0 errors**, four/one existing dependency-version warnings. Debug APK v2 signature passes. SHA-256: **A627B7BF80B1CA1DB7B359C92F692AC3B02DAB9CE556FE80BF6FCA6F66552F23** (`artifacts/kitt-v0-debug.apk`).

An intermediate overlapping Gradle invocation failed to delete an open test binary output file. It was a build-process collision, not a failed test. After the first invocation completed, the full build/lint/test command was rerun serially and passed; final reports above come from completed serial verification.
