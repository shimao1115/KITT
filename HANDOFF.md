# KITT V0 — morning acceptance handoff

Status as of **2026-10-03 (Asia/Shanghai)**: runnable native Android V0, roadmap D0–D11 completed to the credential/device boundary. Technical fallback acceptance passes. Real-AI content quality and physical-device behavior are intentionally unclaimed until the final phone test. No routine implementation decisions remain.

## Install and run

- Ready APK: **`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`**, approximately 9 MB, debug signed and verified. Android **8.0+**.
- App: **路上读山河**, package `com.kitt.reader`, version `0.1.0`, target SDK 35.
- Transfer that APK to the phone and open/install it. If using an authorized USB-debugging device instead:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r H:\CODEX\KITT\artifacts\kitt-v0-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.kitt.reader/.MainActivity
```

No connected Android device was available during this build; the APK was launched through the Android/Robolectric test environment, not installed on a physical phone.

## Default Provider and the one remaining credential action

Default: **Fake Provider / fixed offline demonstration**, prominently labeled. Model: none; effort: none. No login, server, map key or AI key is needed to test the interaction loop.

For a **real-AI** acceptance, before starting the trip go to 设置, select OpenAI Responses or 兼容 API, enter a working HTTPS API address, supported model ID and your API key, then 保存. The OpenAI form starts with `https://api.openai.com/v1` and the editable model hint `gpt-4.1-mini`; no account/model access is assumed or live-verified. Reasoning controls appear only for Adapter-recognized reasoning families; unknown/compatible models omit that parameter.

Local Codex metadata had ChatGPT login tokens but no Platform API key. Separate KITT ChatGPT-plan registration/consent would still require the user; existing desktop tokens were not copied. The ordinary API fallback is implemented and transport/contract tested. See [Provider spike](docs/PROVIDER_SPIKE.md). This is the smallest remaining credential action; OAuth work is not required for V0.

Keys are encrypted with Android Keystore in private on-device settings and excluded from backups. No real keys are compiled into the APK or committed. Actual device Keystore behavior remains part of phone acceptance.

## One final acceptance run

1. Open the app. Leave Fake selected for a credential-free technical test, or configure the real Provider first as above for content-quality acceptance.
2. Tap the top **路上读山河** title **five times**. Select **模拟位置**, **80 km/h** and **16×** (roughly five minutes for the coarse route; 60× is a faster smoke test). The only fixture is 成都→德阳→绵阳. Click 开始. Grant precise location and notification permissions when requested. Location permission is used for the foreground journey even with simulated input.
3. After the first location arrives, hear **今天准备去哪儿？**. Allow the microphone when the one listening window requests it; say **去绵阳**. Expect a short acknowledgement, then quiet until a worthwhile narration. No response should produce repeated nagging. If system voice recognition is unavailable, the same hidden developer entry accepts one typed user utterance into the same Director loop; use that fallback to continue the technical test.
4. During narration, tap **说点什么**: audio must stop immediately. Say **再讲一点**, then **今天多讲工程**. Expect the same Director to respond and keep the temporary preference for this trip. Fake uses fixed replies; only the real Provider can prove semantic/content quality.
5. Tap **安静一会儿**: show **10:00** and the large **结束安静** control. Resume early. Try **跳过** during speech: stop without resuming the old narration. Natural-language **安静半小时**, **安静一个小时** and **先别讲，等我叫你** are implemented. Ten-minute expiry without forced speech is already covered by clock tests; leaving it to expire on the phone is optional.
6. Lock the screen or switch apps. The journey should continue; notification actions should be only quiet/resume and end. Return to the app: state should match, with no stale narration replay. The coarse simulation reaching Mianyang does **not** auto-end the journey.
7. Tap **结束旅程**. Confirm location/simulation stops, see the lightweight summary, select the five 1–5 scores and optional feedback, then **保存评分** → **已保存在本机**. Return to the departure page.

Optional recovery check in that acceptance session: start another trip, force-stop the app from Android App Info, reopen, and choose **继续** or **结束**. Restore intent/preferences/recent themes/remaining quiet and simulation progress; never restore old speech, PREPARE or a microphone window.

If Chinese TTS is unavailable, select/install a **zh-CN system TTS voice** in Android settings. If recognition is unavailable, enable a supported Chinese system speech-recognition service or use the developer text fallback. These are phone/system components, not missing app implementation.

## Fully implemented

- Kotlin/Compose native driving UI, system light/dark scheme, fixed large portrait/landscape controls; no scrolling on the driving page.
- Start/end, 10-minute quiet/early exit, natural-language quiet durations, skip, interruption, one-shot ASK_USER and soft dialogue cooldown.
- Real GPS and continuous replaceable simulated GPS through one bounded Context pipeline; no map API dependency.
- Three-layer Director prompts, strict four-action schema, low-frequency checks, one PREPARE hint, conservative expiry/deviation/replacement, latest-user-intent wins and no stale audio queue.
- Automatic failures become silence; active failures get a short message. Local development diagnostics omit request/response content and credentials.
- Local Chinese TTS in complete chunks and on-demand recognition, bounded listening windows; no wake word or saved recordings.
- Explicitly started location foreground service, quiet/end notification actions, progressive permissions, foreground screen-on and no automatic screen wake.
- Lightweight JSON recovery and last 20 summaries, at most eight recent themes, five local ratings and optional feedback. No full GPS/transcript/search history storage.
- Fake, OpenAI Responses and compatible API adapters; Provider/model/effort controls, encrypted credentials, TTS rate and notification status.
- Hidden simulation controls: one fixture, physical speed 40/80/100 km/h, time acceleration 1/16/60×, start/stop, typed voice fallback. Acceleration changes travel distance, not the ten-minute quiet timer.

## Build/test evidence

Final executable verification command:

```powershell
cd H:\CODEX\KITT
.\scripts\verify.ps1 -Offline
```

Result: **PASS**. Equivalent Gradle gate: `./gradlew.bat assembleDebug testDebugUnitTest lintDebug --offline --console=plain`.

- **34 tests**, **0 failures, 0 errors, 0 skips**: core, Provider/transport, full simulation, voice callback races, Android lifecycle/service, persistence, encrypted-settings layout and Compose UI interactions.
- Lint: **0 errors**, four informational newer-dependency warnings. Pinned working toolchain: JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler 2.0.21, SDK 35.
- APK signature: **PASS**, v2 scheme. SHA-256: `artifacts/SHA256.txt`; machine-readable gate result: `artifacts/verification.json`.
- Full route: **110,452 m**, **80 km/h**, **82 simulated minutes**, **64 Director checks**, **4 voice outputs**. Production Context/Director/Journey with Fake Provider and fake Voice. No subjective real-AI product PASS is implied.
- Detailed evidence: [Acceptance](docs/ACCEPTANCE.md), `artifacts/full-simulation.txt`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

This machine already has `JAVA_HOME=C:\Users\Church\AppData\Local\CodexToolchains\jdk-17` and `ANDROID_HOME=C:\Users\Church\AppData\Local\Android\Sdk`. On a fresh machine omit `-Offline` to fetch dependencies. No runtime environment setup is needed on the phone beyond installation, permissions and the system voice components.

## Remaining external checks and limitations

Only remaining required external checks: physical GPS/Chinese audio/recognition, on-device key storage and lock-screen/OEM foreground-service behavior; a working API key for real-AI content acceptance. The app already exposes all controls needed for these checks.

- Fixture is **coarse, non-navigation-grade**, with no promised AMap screenshot supplied. Replacing `app/src/main/assets/chengdu-mianyang.json` later refines the fixture; the screenshot is optional for the current technical acceptance.
- Map enrichment/search are not connected. The constitution explicitly limits unverified local specifics, exact figures, records and real-time claims. Fake explains general mechanisms and labels demonstration content. Real factual/narrative quality remains to be judged after credentials are supplied.
- PREPARE is conservatively bound to the current position/heading and a short lifetime; it is always revalidated, never cached speech or an inferred navigation target.
- Last 20 summaries are saved locally; there is no historical-trip browser, navigation, backend, car integration or continuous microphone. These are outside V0 or explicitly allowed lightweight fallbacks.
- The ready artifact is a debug acceptance build, not a store release.

## Milestones and repository state

Local commits on `main` (not pushed; remote source was cloned at `40054ff`):

| Destination | Commit | Result |
|---|---|---|
| D0 | `6c6da0f` | Native Android bootstrap |
| D1 | `8c2bcb4` | Provider contracts and auth isolation |
| D2 | `0ab7d06` | Journey transitions and driving UI |
| D3 | `2219dc2` | Real GPS and bounded Context |
| D4 | `5fd9392` | Chengdu–Mianyang fixture |
| D5 | `2211470` | Director loop and stale protection |
| D6 | `9e09606` | TTS/recognition voice loop |
| D7 | `7119472` | Foreground service and permissions |
| D8 | `1b83808` | Recovery, summary and ratings |
| D9 | `b912cd4` | Settings and encrypted credentials |
| D10 | `64c34a5` | Full simulation/UI acceptance |
| D10 follow-up | `21af3cc` | Persist developer simulation speed |
| D11 | final `HEAD` | This handoff |

The final tracked working tree is clean. `artifacts/`, Gradle/Kotlin caches and build/test outputs are intentionally ignored and remain available locally. `git log --oneline -13` shows the exact final commit hashes, including D11.
