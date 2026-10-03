# KITT V0 + M1.1 — acceptance handoff

Status as of **2026-10-03 (Asia/Shanghai)**: **M1.1 PASS on the connected vivo V2405A (Android API 36)**; D0–D11 preserved. KITT signed in through the official system-browser flow, validated identity/plan permission, loaded account models and completed a real structured Director request with **no Platform API key configured**. Disconnect/remote revocation and reconnect with the retained registration also passed. No desktop Codex/ChatGPT tokens were inspected or reused. No user-only authorization step remains on this phone.

## M1.1 phone acceptance

Verified: APK update and launch; old settings survive; Settings lists **ChatGPT 账号（推荐）**, the three existing providers and **Continue with ChatGPT**. The official OpenAI authorization page opens in system **Firefox**. The user personally completed authorization. Lifecycle diagnostics establish listener bound → browser opened → callback state/client validated → code exchanged → signed ID token/subject/scopes validated and encrypted → account catalog loaded. The exact ephemeral `http://127.0.0.1:<port>/auth/callback` is reused for authorization and exchange, with no custom-scheme substitution.

The account returned **five listed models**: GPT-6-Astra, GPT-5.6-Sol, GPT-5.6-Terra, GPT-5.6-Luna and GPT-5.5. GPT-6.1 was not listed for this account; the cause is unclaimed. Only `visibility == "list"` choices are displayed. Current saved phone configuration: **ChatGPT / `gpt-5.6-sol` / `medium`** (advertised by the account catalog). Fresh installations still default to Fake; ChatGPT is the preferred real-AI option.

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

The final M1.1 APK has been installed and launched on the connected phone. Original D0–D11 audio/GPS/OEM background behavior still requires the acceptance run below; installing and opening Settings alone does not establish those behaviors.

## Default Provider and real-AI setup

Default: **Fake Provider / fixed offline demonstration**, prominently labeled. Model: none; effort: none. No login, server, map key or AI key is needed to test the interaction loop.

Preferred **real-AI** path: **ChatGPT 账号 / Continue with ChatGPT**, as above. Model choices come from the authorized account; no fixed ChatGPT-plan model is assumed. Reasoning controls appear only when the catalog explicitly advertises levels, otherwise the adapter omits reasoning.

Alternative: select OpenAI Responses or 兼容 API, enter your own HTTPS API address, supported model and API key, then 保存. The existing OpenAI form still starts with `https://api.openai.com/v1` and `gpt-4.1-mini`; model access is unclaimed. Ordinary API adapters are preserved and tested. See [Provider spike](docs/PROVIDER_SPIKE.md). Fake remains usable without any authorization.

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
- ChatGPT OAuth plan provider, Fake, OpenAI Responses and compatible API adapters; Provider/model/effort controls, encrypted credentials, TTS rate and notification status. M1.1 adds dynamic registration, RS256 OIDC validation, rotating-token renewal, revocation, account models and completion-gated SSE.
- Hidden simulation controls: one fixture, physical speed 40/80/100 km/h, time acceleration 1/16/60×, start/stop, typed voice fallback. Acceleration changes travel distance, not the ten-minute quiet timer.

## Build/test evidence

Final executable verification command:

```powershell
cd H:\CODEX\KITT
.\scripts\verify.ps1 -Offline
```

Result: **PASS**. Equivalent Gradle gate: `./gradlew.bat assembleDebug testDebugUnitTest lintDebug --offline --console=plain`.

- **52 tests**, **0 failures, 0 errors, 0 skips**: all original 34 tests unchanged, plus 18 focused M1.1 tests including real local sockets, signed-token sign-in, cancellation/protection lifetime, refresh concurrency/rotation, encrypted records and terminal-stream handling.
- Lint: **0 errors**, four informational newer-dependency warnings. Pinned working toolchain: JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler 2.0.21, SDK 35.
- APK signature: **PASS**, v2 scheme. SHA-256: `artifacts/SHA256.txt`; machine-readable gate result: `artifacts/verification.json`.
- Full route: **110,452 m**, **80 km/h**, **82 simulated minutes**, **64 Director checks**, **4 voice outputs**. Production Context/Director/Journey with Fake Provider and fake Voice. No subjective real-AI product PASS is implied.
- Detailed evidence: [Acceptance](docs/ACCEPTANCE.md), `artifacts/full-simulation.txt`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

This machine already has `JAVA_HOME=C:\Users\Church\AppData\Local\CodexToolchains\jdk-17` and `ANDROID_HOME=C:\Users\Church\AppData\Local\Android\Sdk`. On a fresh machine omit `-Offline` to fetch dependencies. No runtime environment setup is needed on the phone beyond installation, permissions and the system voice components.

## Remaining external checks and limitations

M1.1's phone authorization, account models, real Director and disconnect/reconnect gates passed. Remaining original V0 checks: physical GPS/Chinese audio/recognition, full driving content quality and lock-screen/OEM journey-service behavior. A Platform API key is optional. Those broader D0–D11 phone behaviors are not implied by the focused M1.1 Settings acceptance.

- Fixture is **coarse, non-navigation-grade**, with no promised AMap screenshot supplied. Replacing `app/src/main/assets/chengdu-mianyang.json` later refines the fixture; the screenshot is optional for the current technical acceptance.
- Map enrichment/search are not connected. The constitution explicitly limits unverified local specifics, exact figures, records and real-time claims. Fake explains general mechanisms and labels demonstration content. Real factual/narrative quality remains to be judged after credentials are supplied.
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
| M1.1 handoff | final `HEAD` | Final phone evidence and this handoff |

The final tracked working tree is clean. `artifacts/`, Gradle/Kotlin caches and build/test outputs are intentionally ignored and remain available locally. `git log --oneline -13` shows the exact final commit hashes, including D11.
