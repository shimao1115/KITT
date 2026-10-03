# Task Card — Chinese Voice Input Recovery

Status: **ACTIVE — Qoder can implement while user performs content acceptance**
Scope: one-shot Chinese ASR only
Base: current `main` after Editorial Freedom (`d201a38` or newer)

## Goal

Make KITT's existing **tap “说点什么” → listen once → submit recognized Chinese text → Director** path actually work on the user's vivo phone if a practical supported solution exists.

The previous M1.4 diagnosis is authoritative historical evidence:
- device: vivo V2405A / Android API 36;
- selected public recognizer:
  `com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService`;
- it returned `ERROR_CLIENT(5)` in ~15–26 ms before ready/RMS/results;
- inspection showed its public `onStartListening()` path is effectively a stub;
- no successful Chinese transcription was claimed.

Do **not** paper over that failure as “没听清”.

## Product priority

Use this order:

1. **Phone/system-supported recognizer first.**
2. If the OEM public recognizer still cannot work, try the **smallest free solution** that can make one-shot Chinese ASR work reliably.
3. Do not introduce a paid ASR service, required API key, subscription, or per-minute cloud cost without a new user decision.

The user prefers the phone's own capability when possible; free fallback comes second.

## Preserve the interaction model

Keep:
> tap “说点什么” → stop TTS immediately → listen once → recognize → send text to the same Director path.

Do not add:
- wake word;
- always-on microphone;
- continuous background listening;
- raw-audio history;
- long-term audio storage;
- voice command grammar that bypasses the normal Director unless already supported elsewhere.

Existing listening UI, RMS visualization, structured error outcomes and TTS voice selector should remain.

## Phase A — Re-probe the phone before changing architecture

If the phone is available through adb, inspect current reality again instead of assuming M1.4 state is unchanged.

Record:
- `Settings.Secure voice_recognition_service`;
- all installed/exported `RecognitionService` implementations;
- whether Android reports on-device recognition available;
- whether any recognizer activity/service is enabled but not selected;
- package/component/version for each plausible recognizer;
- actual callback sequence and error code for a Chinese utterance;
- whether explicit `SpeechRecognizer.createSpeechRecognizer(context, ComponentName)` can use another valid installed service;
- whether a supported system setting/user-selectable recognizer can solve it without private APIs.

Do **not**:
- enable protected components through unsupported shell/private APIs and call that a product solution;
- reverse-engineer and call vivo private hidden APIs from KITT;
- claim PASS from an activity UI that does not return recognized text to KITT.

If a supported installed recognizer works, use it with the smallest code change and stop there.

## Phase B — Free fallback if system ASR is genuinely blocked

If no supported system recognizer can transcribe Chinese, investigate current practical **free** fallbacks.

You may consider:
- a compatible Android `RecognitionService` the user can select;
- a small on-device ASR library/model embedded behind KITT's voice abstraction;
- another free/offline approach appropriate for modern Android.

Do not assume a specific engine in advance. Compare candidates on:
- Simplified Chinese accuracy for short conversational commands/questions;
- first-use setup burden;
- APK/model size;
- startup and recognition latency on this vivo device;
- offline capability;
- arm64 Android support;
- license compatibility;
- maintenance status;
- privacy (prefer local processing);
- ability to provide final text cleanly into the existing one-shot flow.

Avoid adding a huge model/dependency unless the accuracy benefit clearly justifies it.

If installation of a third-party recognizer/app is required, document exactly what the user must install/select and why. Do not silently install unrelated apps or change system defaults without explicit user action.

## Phase C — Minimal architecture

Prefer a tiny boundary such as:
- `SpeechInputEngine` / `RecognizerBackend`;
- existing Android system backend;
- optional free fallback backend only if needed.

Keep `AndroidVoice` / Journey / Director semantics stable.

Selection behavior should be simple:
- use a known working system backend when available;
- otherwise use the configured/available free fallback;
- otherwise preserve the current accurate “系统语音识别暂不可用” failure.

Do not create a general audio platform or provider marketplace.

## Chinese acceptance phrases

Real-phone success requires actual spoken Chinese transcription, not typed developer input.

At minimum test:
- “再讲一点”
- “跳过”
- “安静十分钟”
- one open question, e.g. “三星堆为什么这么有名”

Acceptance requires:
- actual recognized text reaches the existing Director/user-intent path;
- listening UI remains active long enough to speak;
- RMS/live state works when backend can provide it, or degrades honestly when it cannot;
- TTS interruption → listening still works;
- silence/no-match is distinct from backend failure;
- cancel/end/quiet does not leak a late result;
- no stale audio/result is replayed after state change.


## Live transcript + typed-input fallback

The listening experience should behave more like a modern voice input method.

### Show recognized speech on screen

While one-shot ASR is active:
- if the backend provides partial/interim hypotheses, show the current recognized text live on screen as it changes;
- when a final result arrives, show the final recognized text clearly before/while it is submitted into the existing Director path;
- do not fabricate partial text for engines that only support final results;
- if a backend cannot provide interim hypotheses, keep the listening visualization and show text as soon as a real final result exists;
- recognition errors/notices must remain visually distinct from actual recognized text.

The transcript is transient UI state only. Do not persist it as raw voice history beyond the existing user-text/Director interaction semantics.

### Always preserve a typing path

Whenever the user has entered the **“说点什么”** interaction, and whenever KITT has asked the user a question via `ASK_USER`, the UI must also expose a **typed text input** path.

Required behavior:
- microphone listening and text entry belong to the same one-shot interaction surface;
- the user may choose to speak or type;
- selecting/starting text entry should safely stop/cancel the active recognizer so a late ASR result cannot overwrite or duplicate the typed submission;
- typed text must go through the same existing `Journey.requestInput` / Director path as recognized speech;
- do not create separate command semantics for typed vs spoken input;
- when KITT asks a question, the user must not be forced to answer by voice only;
- if ASR is unavailable, typed input still remains usable.

The exact UI may be a compact text field, keyboard action, or equivalent, but it must be obvious and reachable during the active interaction without navigating away from the journey screen.

Do not require the user to type while driving; this is an available input method for a passenger/stopped user, not a driving requirement.

### Interaction correctness

Verify:
- partial ASR text updates do not submit prematurely;
- final ASR result submits once;
- typed submit cancels ASR and submits once;
- a late recognizer callback after typed submit is ignored;
- cancel/back clears transient transcript and input state;
- `ASK_USER` still abandons cleanly if neither voice nor text response is given;
- TTS → ASK_USER → listening + typing entry works;
- ASR-unavailable state still allows typed response.

## Error and privacy requirements

Keep structured outcomes at least equivalent to M1.4:
- SUCCESS
- NO_MATCH
- TIMEOUT
- PERMISSION_DENIED
- UNAVAILABLE
- BUSY
- NETWORK/SERVER where applicable
- CLIENT/STARTUP_ERROR
- CANCELLED

Never relabel backend startup failure as “没听清”.

Raw microphone audio must not be persisted by KITT. If a fallback engine temporarily buffers PCM in memory, release it immediately after the one-shot recognition lifecycle.

## Regression boundaries

Do not change:
- Editorial Freedom / local dossier behavior;
- area/town narration opportunities;
- landmark proximity triggers;
- route image / Visual Talk;
- ChatGPT OAuth/provider behavior;
- real GPS vs simulation semantics;
- wall-clock quiet timing;
- stale protection;
- strict Director JSON contract.

## Tests

Add focused tests for:
- backend selection/fallback;
- system recognizer failure does not masquerade as no-match;
- success text reaches existing `VoicePort`/Director flow;
- cancellation wins over late result;
- timeout cleanup;
- permission handling;
- backend unavailable fallback;
- no fallback retry storm;
- TTS → listen interruption remains correct;
- partial transcript rendering when the backend supports it;
- final transcript rendering;
- typed submit through the same Director path;
- typed submit cancels/invalidates late ASR results;
- ASK_USER exposes both voice and typed response paths;
- ASR unavailable still leaves typed input usable.

Run full:
- Debug unit tests;
- Release unit tests;
- assembleDebug / assembleRelease;
- lintDebug / lintRelease;
- existing simulation/regression suite.

## Phone acceptance / truthfulness

If a real working solution is found:
1. install the built APK;
2. start a journey;
3. tap “说点什么”;
4. speak the four Chinese acceptance phrases above;
5. record actual recognized text and resulting Director behavior;
6. test silence and TTS interruption;
7. record backend/component/model actually used.

If no practical free solution is achieved:
- document the exact blocker and experiments;
- leave the app in an accurate failure state;
- do not claim ASR PASS;
- do not weaken existing behavior merely to make a test green.

## Deliverable

- diagnosis/research record;
- smallest working implementation, if feasible;
- explicit backend choice and why;
- full tests/build/lint evidence;
- real-phone result if phone available;
- updated `HANDOFF.md`;
- latest APK;
- milestone commit(s), push `main`;
- clean tracked working tree.

This task **reopens Chinese ASR intentionally**. It does not reopen other completed milestones.
