# M1.4 — Voice Experience: reliable listening, live voice states, selectable TTS

## Goal

Turn KITT's current basic voice loop into a trustworthy real-phone voice experience.

M1.3 proved automatic ChatGPT → TTS works. The remaining real-phone voice problems are now explicit:

1. Tapping **“说点什么”** currently ends almost immediately with **“没听清”** on the vivo phone.
2. The UI does not make it obvious whether KITT is actually listening.
3. While KITT speaks, the screen only shows static text; the user wants a visible dynamic speaking state.
4. Local Android TTS voice is not selectable; Settings only exposes speech rate.

This milestone fixes only voice input/output UX. Do not add route-image or camera features here.

## Current implementation facts

### ASR
`AndroidVoice.startRecognition()` currently:
- checks `SpeechRecognizer.isRecognitionAvailable()`;
- creates a recognizer;
- starts one-shot `ACTION_RECOGNIZE_SPEECH` for `zh-CN`;
- ignores `onReadyForSpeech`, `onBeginningOfSpeech`, `onRmsChanged`, `onEndOfSpeech`;
- collapses **all recognition errors** into `result(null)`;
- uses a 15-second watchdog.

At the Journey layer, any null/blank result becomes:

> 没听清，想说时再点一下。

Therefore service unavailable, recognizer busy, client error, network error, timeout, permission failure, and genuine no-match are indistinguishable.

### TTS
`AndroidVoice` currently:
- initializes Android `TextToSpeech`;
- sets language to Simplified Chinese;
- supports speech-rate selection;
- does not enumerate or persist a specific `Voice`;
- has no preview flow.

### Driving UI
`DrivingScreen` has static state labels:
- 正在讲述
- 正在听

There is no live listening level or speaking animation.

## Product behavior

### A. Reliable one-shot listening

Keep the existing interaction model:

> tap “说点什么” → KITT stops speaking → listens once → submits recognized text automatically → returns to normal Director flow.

Do **not** add:
- always-on microphone;
- wake word;
- saved raw audio;
- cloud audio recording owned by KITT.

But make the one-shot interaction reliable and diagnosable.

### B. Preserve recognition outcome semantics

Introduce an internal structured listening outcome instead of reducing everything to nullable text.

At minimum distinguish:
- SUCCESS(text)
- NO_MATCH / no intelligible speech
- TIMEOUT
- PERMISSION_DENIED
- RECOGNIZER_UNAVAILABLE
- BUSY
- NETWORK / SERVER
- CLIENT / STARTUP_ERROR
- CANCELLED

Exact enum naming is up to the implementation.

Map Android `SpeechRecognizer` error codes explicitly and preserve a non-secret diagnostic event.

User-facing behavior should be concise:
- genuine no-match → **“没听清，想说时再点一下。”**
- unavailable recognizer → **“系统语音识别暂不可用。”**
- busy/start failure → **“语音识别没启动成功，请再试一次。”**
- permission denied → **“需要麦克风权限才能听你说话。”**
- network/service error → brief accurate wording, not “没听清”.
- cancelled/interrupted because user changed state → no false error toast/message.

Do not expose Android numeric codes in the normal driving UI; they may be included in developer diagnostics/logs.

### C. Live listening state

While one-shot recognition is active:
- main screen clearly shows **“正在听，请说话”**;
- display a large, glanceable dynamic listening visualization;
- drive it from real `onRmsChanged(rmsdB)` callbacks when available;
- smooth/clamp noisy RMS data so the UI does not flicker violently;
- visualization returns to idle immediately on result/error/cancel/timeout.

The indicator can be waveform / vertical bars / pulse, but it must:
- be visible at a glance;
- not require reading tiny UI;
- not flash rapidly;
- work in portrait and landscape;
- remain secondary to safe driving.

Do not retain microphone buffers. `onBufferReceived` remains discard-only.

### D. Dynamic AI-speaking state

When `JourneyState.SPEAKING`:
- show a distinct animated visual state instead of static **“正在讲述”** only;
- it should be visually different from listening;
- use a restrained pulse/scanner/breathing-bars style compatible with future KITT visual identity;
- state-driven animation is sufficient; no need to analyze actual TTS audio amplitude;
- animation must stop immediately when speech finishes, user interrupts, skip is pressed, quiet starts, or journey ends.

Avoid a full UI redesign in M1.4. This is a state visualization, not the later visual-polish milestone.

### E. Selectable Android TTS voice

Add a local TTS voice selector in Settings.

Requirements:
- enumerate available Android `TextToSpeech.voices`;
- prioritize/filter Chinese voices, especially `zh-CN`, while allowing useful Chinese variants if present;
- display the system-provided voice name and locale;
- if metadata indicates network-required vs embedded/local, surface that briefly where reliable;
- do not invent male/female labels when Android metadata does not provide them;
- add one-tap **试听** using a short fixed Chinese sentence;
- selecting a voice + Save persists it;
- restore the voice on app launch;
- if the saved voice no longer exists, safely fall back to the system/default Chinese voice and keep KITT usable;
- current speech-rate setting remains.

Suggested preview sentence:
> “你好，我是路上读山河。前面的风景，值得慢慢听。”

Preview must not start or alter a Journey and must not send anything to an AI Provider.

### F. Voice state plumbing

Keep platform-specific mechanics inside the voice layer as much as practical.

A small observable voice-state model is appropriate, e.g.:
- IDLE
- PREPARING_LISTEN
- LISTENING(level)
- SPEAKING
- ERROR(summary)

Do not create a new app-wide architecture or navigation framework solely for this milestone.

Journey remains authoritative for product interaction state; AndroidVoice may expose device-level voice detail such as live RMS/error.

## Real-phone investigation requirement

The current vivo ASR failure must be actually diagnosed, not merely cosmetically hidden.

On the connected phone:
- capture which `SpeechRecognizer` callbacks and error code occur after tapping **说点什么**;
- identify whether the recognizer is unavailable, busy, client-error, network/server failure, no-match, or another documented condition;
- implement the smallest robust fix available on the platform.

If the device default recognizer itself is not capable of returning Chinese text in this environment, document that honestly. Do not claim successful ASR unless a spoken Chinese utterance is actually transcribed on-device.

Do not silently introduce a paid/cloud ASR provider in this milestone.

## Tests

Preserve the current 70 tests per variant and add focused coverage for at least:

1. Android recognition error-code mapping.
2. no-match vs unavailable/busy/network/permission are distinct outcomes.
3. cancellation does not surface a false “没听清”.
4. RMS updates are clamped/smoothed and reset on finish/cancel.
5. listening visual state follows one-shot recognition lifecycle.
6. speaking visual state follows Journey speaking lifecycle and stops on interrupt/skip/quiet/end.
7. TTS voice list filters/orders Chinese voices sensibly.
8. selected voice is persisted and restored.
9. missing saved voice falls back safely.
10. preview does not mutate Journey/provider state.
11. speech rate still applies with selected voice.
12. M1.1 OAuth/inference tests remain green.
13. M1.2 navigation/source tests remain green.
14. M1.3 accelerated simulation tests remain green.

Use interfaces/fakes where Android framework classes make JVM tests difficult; do not weaken production behavior for testability.

## Phone acceptance

On the existing vivo phone:

### Listening
1. Start a journey.
2. Tap **说点什么**.
3. KITT immediately enters a clearly visible **正在听，请说话** state.
4. Speak a simple Chinese phrase such as **“再讲一点”**.
5. Confirm the live indicator moves with speech level.
6. Confirm actual recognized text reaches the Director and produces an appropriate response.
7. Repeat once with silence/no speech and confirm only that case says “没听清”.
8. Trigger/observe any device-service error if reproducible and confirm it is no longer mislabeled as no-match.

### Speaking
1. Let KITT speak.
2. Confirm a distinct visible animation remains active for the whole TTS utterance.
3. Tap **说点什么** during speech.
4. Speaking animation stops immediately and listening animation begins.

### TTS voice
1. Open Settings.
2. See available Chinese voices.
3. Preview at least two available voices if the device exposes them.
4. Save a non-default voice.
5. Return to journey and verify KITT uses it.
6. Relaunch app and verify selection persists.

## Deliverable

- Current vivo ASR behavior diagnosed and documented.
- Live listening indicator implemented.
- Dynamic AI-speaking indicator implemented.
- TTS voice selector + preview + persistence implemented.
- Build/lint/all tests PASS for Debug/Release.
- Real-phone acceptance recorded truthfully.
- APK installed.
- Commit + push to `main`.
- Update `HANDOFF.md`.
- Working tree clean.

Do not implement route screenshots, camera/image chat, current-place reverse geocoding, or broad UI beautification in M1.4.
