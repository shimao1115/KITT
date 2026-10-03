# KITT V0 + M1.4 — acceptance handoff

Status as of **2026-10-03 (Asia/Shanghai)**: **M1.4 implemented; Debug/Release build, lint and 89 tests per variant PASS; final APK installed on vivo V2405A (API 36).** RMS-driven listening bars, a distinct animated speaking scanner, Android recognition outcome diagnostics, and selectable/previewable/persistent TTS voices are delivered. All original 70 M1.1–M1.3 / D0–D11 tests remain. **Real-phone Chinese ASR/RMS acceptance is blocked by vivo's selected public recognizer; no successful spoken transcription is claimed.**

## M1.4 phone diagnosis and acceptance

**Root cause:** selected `com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService` (Copilot 6.9.3.0) returns **ERROR_CLIENT(5)** in about **15–26 ms**, before any ready, RMS or results callback. Inspection of the installed public APK's service method establishes that `onStartListening` unconditionally sends error 5 and returns, without reading the intent or starting recognition. This is an OEM public-API stub, not “没听清,” permission denial or KITT's timeout. Android exposes no on-device recognizer or recognize-speech Activity on this phone. Its Google recognition component is not enabled/resolvable; Android rejected component enable with SecurityException and made no change. No private vivo API, silent alternate-app routing, paid ASR or saved recording was introduced. The app reports the accurate startup failure and offers **系统语音识别设置**. Full bounded evidence: [M1.4 acceptance](docs/ACCEPTANCE_M1_4.md).

**Verified on the phone:** both exposed local voices (**zh / zh-Hant**, **yue / yue-Hans**) previewed with successful TTS completion callbacks; non-default **yue** saved, survived force-stop/relaunch and APK reinstall, and was applied to journey narration. Real ChatGPT question and automatic narration displayed the scanner; paired screenshots show different scanner positions in one utterance. A physical Speak-button action stopped real TTS and started the one-shot recognizer **64 ms after TTS start**; the scanner disappeared and the accurate client error replaced listening. Quiet counted down in real time and resumed early, explicit End opened a populated summary, ordinary Start returned to phone GPS, Settings Back stayed in-app, and the final phone is idle with **no active JourneyService**. Sustained real listening/RMS, spoken Chinese → Director and genuine silence/no-match cannot pass until the system recognizer works; their lifecycle/error/state gates pass in Robolectric.

**成都→绵阳 live simulation:** **80 km/h / 16×**, **110,452 m**, completed **10:20:48**. **14 automatic opportunities = 7 SILENT + 3 SPEAK_NOW + 1 ASK_USER + 3 FAILURE**, zero stale or automatic cancellation. One extra developer typed request was deliberately cancelled during interruption testing. One narration completed, another was stopped by typed input and the third by Speak. At the end ChatGPT returned **HTTP 503**; two subsequent opportunities were silently blocked by the existing connection safeguard. There was no error narration, stale replay, crash or retry storm. No quota/auth-revocation cause is inferred; after final APK reinstall, Settings refreshed the account's five models without authorization. The deterministic Fake full route and M1.3 delayed-provider simulation also pass unchanged.

**Final configuration:** fresh installations remain **Fake / offline demonstration**. This existing phone retains **ChatGPT / gpt-5.6-luna / high**, **yue system TTS voice / 1.0× rate**. No API key or OAuth action is presently required for this phone. An ordinary Start uses real GPS; only explicit developer Start/recovery uses simulation.

**Smallest remaining user/system action:** enable/select a working Chinese Android speech-recognition service using supported phone setup, then tap **说点什么** and actually say **再讲一点**, followed by a silence attempt. Confirm live level motion and genuine recognized text reaching the Director. The selected Copilot stub cannot be fixed by waiting/retrying or changing language extras. Any new recognizer's account, permissions and data-processing setup must be completed through its normal user flow. This limitation is separate from the working TTS/AI loop. Outdoor GPS accuracy, subjective content/voice quality and OEM lock-screen endurance remain broader V0 checks.

**Final gates:** `scripts/verify.ps1 -Offline` and `gradlew.bat build lintRelease --offline --console=plain` PASS. **89 Debug + 89 Release tests, zero failures/errors/skips**; lint **zero errors**, only four existing dependency warnings in Debug and one in Release; v2 APK signature PASS. Ready APK: `H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`; SHA-256 **B5F0997EE6253615C74B86B015892478BB41F382E5864BC0864BF58E76DC21F8**. Installed with `adb install -r`. No source/build dependency or new permission was added.

**Shortest next total acceptance:** Settings → choose voice → **试听 → 保存 → 系统返回**; title five taps → **80 km/h / 16× / 开始**; observe auto question/narration and scanner, interrupt with Speak, quiet/resume, End/summary/Return, then verify ordinary Start says **手机 GPS**. Use the existing typed developer fallback only to continue non-ASR checks while the recognizer is blocked. After system setup, repeat with actual spoken Chinese; never count typed text as an ASR pass.

**Recent milestone commits:** `6944fc5` M1.4 voice implementation, 19 regressions and phone diagnosis; `179dbf7` M1.4 task; `c7e8116` M1.3 cadence/voice delivery; `1d54588` M1.2 handoff; `3ff905d` M1.1 live catalog/SSE. Final tracked working tree is clean after the handoff commit; artifacts/build/cache outputs are ignored and kept locally.

## Preserved M1.3 handoff

Status as of **2026-10-03 (Asia/Shanghai)**: **M1.3 accelerated-simulation delivery gate verified on vivo V2405A (API 36)**. Explicit simulation now compresses driving between interactions: route progress drives roughly 14 automatic opportunities, and travel pauses during Director requests, TTS and one-shot listening. Real-GPS cadence, the 1,500 m stale threshold, wall-clock quiet durations and ChatGPT M1.1 adapters remain unchanged. Debug/Release builds, lint and **70 tests per variant** pass, preserving all original 60. The final APK is installed; the human user confirmed hearing an automatic real-ChatGPT narration. No authorization/key step is required on this phone.

## M1.3 phone acceptance

Reproduced the old **100 km/h + 16×** silent route on the installed M1.2 phone build. A retained regression reproduces six delayed valid auto responses all becoming spatially stale, with zero voice outputs. After the cadence/pause fix, a complete live route returned **14 genuine SILENT, zero stale/failure**; this established a Context-quality issue separately from the acceleration bug. Only then, a minimal simulation-only Context adjustment clarified fixture area stages, simulated versus wall elapsed time, and the not-yet-asked destination. The system constitution, real-GPS Context and ChatGPT provider implementation are unchanged; no narration is forced or replaced with Fake text.

The final live ChatGPT run automatically asked the destination and delivered **SPEAK_NOW** at **15,590 m** after **12.53 seconds** of provider latency. Request/terminal progress was identical; TTS then completed successfully after **33.3 seconds**, and accelerated movement resumed. The user explicitly confirmed hearing the question and automatic narration. A second automatic SPEAK_NOW also survived about 15 seconds of latency at **73,076 m**. Full route: **14 requests = 11 SILENT + 2 SPEAK_NOW + 1 ASK_USER; 0 stale/cancel/failure**. Both automatic narrations completed TTS successfully. The installed APK hash matches the packaged artifact. Settings system Back and the next ordinary Start were rechecked: main returned correctly, the real GPS listener registered at 2 seconds / 5 m, and ending cleared the route label. The phone is left at real-GPS idle with no active trip. See [M1.3 acceptance](docs/ACCEPTANCE_M1_3.md).

The one system-recognition window returned no recognized destination; successful ASR transcription is not claimed. The phone uses vivo Copilot recognition, and the existing developer text fallback remains available. This is separate from the now verified automatic ChatGPT/TTS gate. Remaining broader checks are outdoor physical GPS reception, reliable Chinese ASR, subjective content quality and OEM lock-screen endurance. The fixture remains coarse and non-navigation-grade.

Shortest M1.3 recheck: title five taps → **100 km/h / 16× / 开始** → allow automatic decisions and listen. During thinking/voice/listening, movement pauses; between interactions it accelerates. View counters through the same hidden entry or `adb logcat -s KITTSim`. SILENT is valid and reported distinctly from stale/cancel/failure. **安静一会儿** still means 10 real minutes. End explicitly; ordinary Start must return to **手机 GPS**. Existing phone configuration remains **ChatGPT / gpt-5.6-luna / high**; fresh installations still default to Fake.

M1.2's completed acceptance is preserved below as historical evidence.

Status as of **2026-10-03 (Asia/Shanghai)**: **M1.2 navigation/source fixes PASS on the connected vivo V2405A (Android API 36)**. Settings system Back now returns to the main screen; main idle Back retains Android's exit behavior. Ordinary Start always uses phone GPS, for every Provider. Only an explicit hidden developer Start uses the fixture. Ending/stopping simulation clears its source and route/completion label. Legacy persisted `simulation=true` is removed on upgrade; speed/acceleration preferences and explicit unfinished-trip recovery remain available. M1.1 and D0–D11 regression scenarios pass.

## M1.2 phone acceptance

Before upgrade, the phone contained `simulation=true`, and Settings system Back returned to the launcher. After `adb install -r`, verified Settings → Android Back → main without exiting; ordinary Start → **手机 GPS · 无地图增强**, with a real GPS listener registered in Android's location service; End → summary → clean idle; hidden developer Start → **成都→绵阳 · 粗粒度模拟** and **成都方向（模拟）**; hidden **停止模拟 / 结束** → summary → clean idle; next ordinary Start → phone GPS again. Main idle Back still exits, and relaunch remains in real mode. No simulation-route residue remains. Current phone configuration was already **ChatGPT / `gpt-5.6-luna` / `high`** before upgrade and is preserved; it supersedes the older M1.1 configuration below. ChatGPT plan permission remains enabled; five account models loaded and a fresh phone Director test completed with **SPEAK_NOW（ChatGPT 计划）**. No credentials were extracted or manually changed, and no new authorization/key step is required. The phone is left at the real-GPS idle main screen with no active journey.

The eight new regressions cover Settings Back and in-page Return, normal idle Back, actual developer/ordinary UI service intents, legacy upgrade migration, source selection across all four Providers, end/completion cleanup, service start defaults, and explicit simulation recovery after service loss. Source selection is a session start argument carried through the existing permission/service path; Provider adapters, OAuth/inference code, Director, Journey and Voice behavior are preserved. See [M1.2 acceptance evidence](docs/ACCEPTANCE_M1_2.md).

Shortest M1.2 recheck: **设置 → 系统返回 → 开始读山河 → 结束旅程 → 回到出发页**; then title five taps → developer **开始 → 停止模拟 / 结束 → 回到出发页**; ordinary Start must again show **手机 GPS**. The broader original voice/GPS/content-quality acceptance remains below. This focused phone run verifies source selection and registration; reception of a fresh physical GPS fix was not established indoors.

## Preserved M1.1 acceptance

Earlier M1.1 acceptance on **2026-10-03**: **PASS on the connected vivo V2405A (Android API 36)**; D0–D11 preserved. KITT signed in through the official system-browser flow, validated identity/plan permission, loaded account models and completed a real structured Director request with **no Platform API key configured**. Disconnect/remote revocation and reconnect with the retained registration also passed. No desktop Codex/ChatGPT tokens were inspected or reused. No user-only authorization step remains on this phone.

## M1.1 phone acceptance

Verified: APK update and launch; old settings survive; Settings lists **ChatGPT 账号（推荐）**, the three existing providers and **Continue with ChatGPT**. The official OpenAI authorization page opens in system **Firefox**. The user personally completed authorization. Lifecycle diagnostics establish listener bound → browser opened → callback state/client validated → code exchanged → signed ID token/subject/scopes validated and encrypted → account catalog loaded. The exact ephemeral `http://127.0.0.1:<port>/auth/callback` is reused for authorization and exchange, with no custom-scheme substitution.

The account returned **five listed models**: GPT-6-Astra, GPT-5.6-Sol, GPT-5.6-Terra, GPT-5.6-Luna and GPT-5.5. GPT-6.1 was not listed for this account; the cause is unclaimed. Only `visibility == "list"` choices are displayed. At M1.1 acceptance, saved phone configuration was **ChatGPT / `gpt-5.6-sol` / `medium`** (advertised by the account catalog); current M1.2 configuration is recorded above. Fresh installations still default to Fake; ChatGPT is the preferred real-AI option.

The real **测试 Director 连接** result was **Director 请求已完成：SPEAK_NOW（ChatGPT 计划）**, both before and after disconnect/reconnect. This requires public Responses SSE reaching `response.completed` and strict local Director JSON validation. A boolean on-device settings check confirmed no Platform API-key credential configured; no token values were extracted. **断开 ChatGPT** confirmed remote revocation and local token clearing while retaining registration. Reconnect reused the issued client/host and revalidated the same subject; plan access and the five-model catalog returned. The phone remains configured for ChatGPT.

Real phone failures fixed before completion: cached-process freezing during browser login (bounded `shortService`, 150 seconds, no new permissions), a model catalog larger than 256 KB (separate 4 MB bounded catalog read; nullable capability metadata is also tolerated), and a completed SSE event with an empty final output snapshot (collect text deltas/done events and validate only after completion). Live login/reconnect completed with the short foreground service; its notification stops after the attempt. Unused listener sockets close immediately after callback validation.

Implementation, lifecycle boundaries and official sources: [M1.1](docs/CHATGPT_SIWC.md). Refresh rotation and failure paths use focused tests; waiting an hour/30 days on the phone is not part of the gate. A new installation requires its own explicit browser authorization. If a login attempt expires or Android kills it, start a fresh attempt; do not import desktop credentials. Identity-only grants stay connected with plan use disabled. Admission/region/usage errors appear in Settings, with no automatic API-key billing fallback.

## Install and run

- Ready APK: **`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`**, approximately 9 MB, debug signed and verified. Android **8.0+**.
- App: **路上读山河**, package `com.kitt.reader`, version `0.1.0`, target SDK 35.
- Transfer that APK to the phone and open/install it. If using an authorized USB-debugging device instead:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r H:\CODEX\KITT\artifacts\kitt-v0-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.kitt.reader/.MainActivity
```

The final M1.4 APK has been installed and launched on the connected phone. Automatic real-ChatGPT Chinese TTS is now heard and verified; broader D0–D11 GPS/ASR/OEM background and subjective quality checks remain as described below.

## Default Provider and real-AI setup

Default: **Fake Provider / fixed offline demonstration**, prominently labeled. Model: none; effort: none. No login, server, map key or AI key is needed to test the interaction loop.

Preferred **real-AI** path: **ChatGPT 账号 / Continue with ChatGPT**, as above. Model choices come from the authorized account; no fixed ChatGPT-plan model is assumed. Reasoning controls appear only when the catalog explicitly advertises levels, otherwise the adapter omits reasoning.

Alternative: select OpenAI Responses or 兼容 API, enter your own HTTPS API address, supported model and API key, then 保存. The existing OpenAI form still starts with `https://api.openai.com/v1` and `gpt-4.1-mini`; model access is unclaimed. Ordinary API adapters are preserved and tested. See [Provider spike](docs/PROVIDER_SPIKE.md). Fake remains usable without any authorization.

Keys are encrypted with Android Keystore in private on-device settings and excluded from backups. No real keys are compiled into the APK or committed. Actual device Keystore behavior remains part of phone acceptance.

## One final acceptance run

1. Open the app. Leave Fake selected for a credential-free technical test, or configure the real Provider first as above for content-quality acceptance.
2. Tap the top **路上读山河** title **five times**. Select **80 km/h** and **16×** (roughly five minutes of compressed driving, plus time spent thinking/speaking/listening; 60× is a faster smoke test). Use **100 km/h + 16×** for the M1.3 regression. The only fixture is 成都→德阳→绵阳. Click the developer dialog's **开始** to explicitly start this simulated trip. Ordinary main-screen Start always uses phone GPS. Grant precise location and notification permissions when requested. Location permission is used for the foreground journey even with simulated input.
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
- ChatGPT OAuth plan provider, Fake, OpenAI Responses and compatible API adapters; Provider/model/effort controls, encrypted credentials, TTS rate and notification status. M1.1 adds dynamic registration, RS256 OIDC validation, rotating-token renewal, revocation, account models and completion-gated SSE.
- Hidden simulation controls: one fixture, physical speed 40/80/100 km/h, time acceleration 1/16/60×, explicit session start/stop, typed voice fallback and action counters. Only speed preferences persist; ordinary starts use real GPS. Explicit recovery may continue an unfinished simulation. M1.3 pauses travel during requests/TTS/listening and uses route progress for opportunities; quiet durations remain real time.

## Preserved M1.3 build/test evidence

M1.3 verification command (also used by M1.4):

```powershell
cd H:\CODEX\KITT
.\scripts\verify.ps1 -Offline
.\gradlew.bat build lintRelease --offline --console=plain
```

Result: **PASS**. The packaging command verifies Debug build/tests/lint/signature; the second command also builds Release and runs all unit-test variants plus Release lint.

- **70 tests in each of Debug and Release**, **0 failures, 0 errors, 0 skips**: the original 34 D0–D11, 18 M1.1 and eight M1.2 scenarios, plus ten M1.3 regressions. No original test was removed. The M1.3 tests cover delayed 100 km/h + 16× delivery, route opportunities, 60× smoke behavior, interaction pauses, real quiet timing, unchanged real-GPS behavior, cancellation/PREPARE/stale safety, and simulation Context reset. All 18 OAuth/inference tests remain green.
- Lint: **0 errors** in Debug and Release; four existing newer-dependency warnings in Debug, one warning in Release. Pinned working toolchain: JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler 2.0.21, SDK 35.
- APK signature: **PASS**, v2 scheme. SHA-256: `artifacts/SHA256.txt`; machine-readable gate result: `artifacts/verification.json`.
- Full route: **110,452 m**, **80 km/h**, **82 simulated minutes**, **64 Director checks**, **4 voice outputs**. Production Context/Director/Journey with Fake Provider and fake Voice. No subjective real-AI product PASS is implied.
- Accelerated delayed-provider route: **100 km/h + 16×**, **14 automatic opportunities**, **15 total requests**, **13 automatic SPEAK_NOW + one ASK_USER + one active reply**, **0 stale/cancel/failure**; five-second provider latency and 20-second fake TTS. Evidence: `artifacts/accelerated-simulation.txt` and [M1.3](docs/ACCEPTANCE_M1_3.md).
- Detailed evidence: [Acceptance](docs/ACCEPTANCE.md), `artifacts/full-simulation.txt`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

This machine already has `JAVA_HOME=C:\Users\Church\AppData\Local\CodexToolchains\jdk-17` and `ANDROID_HOME=C:\Users\Church\AppData\Local\Android\Sdk`. On a fresh machine omit `-Offline` to fetch dependencies. No runtime environment setup is needed on the phone beyond installation, permissions and the system voice components.

## Remaining external checks and limitations

M1.1's phone authorization, account models, real Director and disconnect/reconnect gates passed. M1.3 adds an audible automatic real-ChatGPT narration gate. Remaining original V0 checks: physical GPS, reliable Chinese recognition, full driving content quality and lock-screen/OEM journey-service behavior. A Platform API key is optional.

- Fixture is **coarse, non-navigation-grade**, with no promised AMap screenshot supplied. Replacing `app/src/main/assets/chengdu-mianyang.json` later refines the fixture; the screenshot is optional for the current technical acceptance.
- Map enrichment/search are not connected. The constitution explicitly limits unverified local specifics, exact figures, records and real-time claims. Fake explains general mechanisms and labels demonstration content. Real factual/narrative quality still requires broader judgment; the connected phone already has ChatGPT credentials.
- PREPARE is conservatively bound to the current position/heading and a short lifetime; it is always revalidated, never cached speech or an inferred navigation target.
- Last 20 summaries are saved locally; there is no historical-trip browser, navigation, backend, car integration or continuous microphone. These are outside V0 or explicitly allowed lightweight fallbacks.
- The ready artifact is a debug acceptance build, not a store release.

## Milestones and repository state

Milestones on `main` (including the original D0–D11 history and the M1.1 task at `627a98e`):

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
| D11 | `fef5d70` | Original handoff |
| M1.1 | `afc3217` | Isolated ChatGPT OAuth provider and bounded Android login |
| M1.1 live gate | `3ff905d` | Live catalog/SSE fixes; phone Director and reconnect PASS |
| M1.1 handoff | `bc41ff3` | Final M1.1 phone evidence |
| M1.2 task | `464f8e3` | Navigation and simulation isolation acceptance contract |
| M1.2 | `3f4d994` | Navigation/source isolation fixes, regression tests and phone acceptance |
| M1.2 handoff | `1d54588` | Phone-verified source/navigation and all-variant gates |
| M1.3 task | `87097c4` | Accelerated simulation cadence and live delivery contract |
| M1.3 | `c7e8116` | Simulation progress/pause fix, ten regressions, real ChatGPT audible acceptance |
| M1.4 task | `179dbf7` | Voice reliability, visualization and TTS acceptance contract |
| M1.4 | `6944fc5` | OEM recognizer diagnosis, live indicators, persistent voice/preview and 19 regressions |

The final tracked working tree is clean. `artifacts/`, Gradle/Kotlin caches and build/test outputs are intentionally ignored and remain available locally. `git log --oneline -13` shows the exact final commit hashes, including D11.
