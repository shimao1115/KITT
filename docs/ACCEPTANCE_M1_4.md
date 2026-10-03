# M1.4 voice experience acceptance — 2026-10-03 (Asia/Shanghai)

Status: implementation and automated gates PASS; real-phone TTS, voice persistence, speaking animation and interruption verified. **Chinese ASR/RMS acceptance is blocked by the selected OEM recognition service. No spoken Chinese transcription is claimed.**

## Actual vivo failure diagnosis

Device: vivo V2405A / Android API 36, USB-authorized device `10AF352AFP003DH`. Microphone permission was already granted. The selected public Android recognizer is `com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService`, Copilot **6.9.3.0**.

Reproduction using the original recognizer creation and `zh-CN` free-form intent, with added callback-only diagnostics:

```text
09:58:32.892 start service=com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService language=zh-CN
09:58:32.917 error code=5
09:58:32.917 finish outcome=CLIENT code=5 rmsCallbacks=0 textLength=0
09:59:15.786 start ...
09:59:15.811 error code=5
```

There were **no ready/beginning/RMS/end/results callbacks**. A later automatic ASK_USER produced the identical failure, with `onDeviceAvailable=false`. This is `SpeechRecognizer.ERROR_CLIENT`, not a no-match, network diagnosis, silence timeout, or microphone-permission denial.

To distinguish an app integration mistake from an OEM service failure, the installed **public APK code only** was pulled and inspected with the SDK's `dexdump`. `classes10.dex` contains `CopilotRecognitionService`, a final subclass of `android.speech.RecognitionService`. Its `onStartListening(Intent, Callback)` performs only: if callback exists, send `callback.error(5)`, then return. It does not examine the language/intent, open a microphone, or start recognition. `onStopListening` and `onCancel` also return error 5. The installed APK SHA-256 is `4A4B12E900540E0E3EBD0AB2B6A76829B163E6EA6E83BF9222E032E3A7844DE1`. No app-private files, account credentials, or speech buffers were read.

Platform alternatives checked:

- `query-services -a android.speech.RecognitionService` exposes only Copilot and Messenger's `HatchRecognitionService`. Messenger is not established as a compatible Chinese recognizer and KITT does not silently send voice to another app.
- `query-activities -a android.speech.action.RECOGNIZE_SPEECH` returns no activities.
- `SpeechRecognizer.isOnDeviceRecognitionAvailable()` returns false on this phone.
- Google's installed public package contains a recognition component in its resolver table, but the component is not currently enabled/resolvable. Android rejected the attempted component enable with `SecurityException: Shell cannot change component state`; the attempt made no change. No bypass or private vivo ASR interface was used.

The smallest supported app fix is to preserve the actual client failure, reset listening immediately, show **语音识别没启动成功，请再试一次。**, and provide **系统语音识别设置** in Settings. Additional waits, audio permission prompts, intent language tweaks or blind retries cannot make this unconditional-error service transcribe. The existing developer typed-input fallback remains available for the rest of the journey acceptance.

**Remaining user-only/system step:** enable/select a working Chinese Android speech-recognition service through the phone's supported system setup (or use an OEM update that implements this public API), then speak **再讲一点** and repeat once with silence. Confirm real RMS motion and actual transcription reaching the Director. Installing a new recognizer, its permissions/account setup, and its data-processing choices are not silently performed by KITT. No AI/API-key authorization is needed for the already-connected ChatGPT account.

## Implementation and regression boundaries

- `ListeningResult` preserves success, no-match, timeout, permission, unavailable/language support, busy, network, server, client/startup and cancellation. The Android numeric code is retained only in diagnostics. Journey submits only success and never produces a false error for cancellation or late callbacks.
- Session tokens protect all callbacks, including queued RMS/start/end/error/results. Stop/result/error/watchdog clear the watchdog, destroy the recognizer, and reset the visual level. Permission grants after cancellation cannot reopen the microphone. Listening remains one-shot, bounded to 15 seconds after starting recognition.
- Real RMS callbacks feed finite, clamped `[-2, 10] dB` normalization and separate attack/release smoothing. The seven large listening bars use this level; they never invent microphone amplitude. Preparing and final-recognition detail are exposed separately. Raw buffers are discarded.
- A restrained horizontal scanner is composed only while Journey is SPEAKING. Listening uses vertical bars, a different color and shape. Portrait and landscape retain fixed large controls and no scrolling. Original Journey flags remain authoritative; the fields read by Compose now use observable state so same-instance changes cannot be skipped.
- Android TTS voices retain their exact system names, locales, installation and network metadata. Chinese voices are ordered zh-CN, other zh, then yue, with usable/local voices preferred within each group. No gender is inferred. Save atomically persists voice and rate beside existing encrypted settings. Missing/uninstalled voices fall back to the system Chinese voice or another usable Chinese voice; engines with only a default Chinese language can still preview without an enumerated voice list.
- Preview uses the fixed Chinese sentence and draft voice/rate directly in AndroidVoice; it changes no Journey state, Provider configuration or Director request. Leaving Settings stops preview. Subsequent journey speech reapplies the saved voice/rate.
- ChatGPT OAuth/inference, Director prompts/cadence, provider adapters, simulation clocks/pause/stale safeguards, source selection, quiet semantics and recovery algorithms retain their previous behavior. No new dependencies, permissions, provider, recording storage or background microphone were added.

## Phone evidence

The phone exposes exactly two installed local Chinese-family voices: **zh · zh-Hant · 本地** and **yue · yue-Hans · 本地**. These are the engine's own locale labels; no zh-CN voice or gender is invented.

- Preview **zh**: TTS configured/started at **10:05:17**, successful completion at **10:05:22**.
- Preview **yue**: TTS configured/started at **10:06:14**, successful completion at **10:06:19**.
- Save non-default **yue**, force-stop/relaunch, reopen Settings: yue still selected. Reinstall/relaunch at **10:11:26** configured `voice=yue fallback=false rate=1.0`. The actual journey also configured yue before its question and narrations.
- Real ChatGPT ASK_USER at **10:13:10**, TTS completed at **10:13:12**, then exactly one listen attempt failed with client code 5 in 25 ms. It did not repeat the question/listening window.
- Real ChatGPT automatic SPEAK_NOW at **15,683 m**, **10:14:16**, with matching dispatch/terminal progress. TTS completed successfully at **10:14:48**. Two phone screenshots show the scanner at different horizontal positions while this same utterance remains active, with all controls visible.
- Automated physical-button interruption at **10:18:11**: real auto TTS started at **10:18:11.206**; **说点什么** caused recognition start at **10:18:11.270**, only 64 ms later. The old utterance had no success-completion event and was stopped; the scanner was removed. The one-shot recognizer then returned client code 5 at **10:18:11.285**. The after-screen shows reading state and accurate startup wording, with no speaking/listening indicator left behind.
- A separate developer typed `more` request was intentionally cancelled by a later Speak action; cancellation was counted distinctly and did not replay its delayed response. This is typed fallback evidence, **not spoken ASR evidence**.

RMS movement, real spoken Chinese → Director, genuine silence/no-match, and sustained listening visualization **cannot be verified on this selected stub recognizer**. Their platform callback/state behavior is tested with Robolectric, not misrepresented as phone success. TTS completion callbacks and screenshots establish playback/state behavior; subjective voice quality remains a human judgment.

Live full-route result: **80 km/h / 16×**, completed at **10:20:48**, **110,452 m**, **14 automatic opportunities**: 7 SILENT + 3 SPEAK_NOW + 1 ASK_USER + 3 FAILURE, zero stale/automatic cancellation. One extra typed active request was deliberately cancelled. The first narration completed; the second was stopped by the typed request; the third was interrupted by Speak in 64 ms. The first of the final three failures was a genuine ChatGPT **HTTP 503** at 10:19:41; the other two were blocked by the existing connection cooldown/guard. All silently degraded; no error narration, crash or retry storm occurred. No usage-limit or revoked-auth cause is inferred. After the final APK reinstall, Settings successfully refreshed the five-model account catalog without reauthorization. Quiet showed 9:58 after about two real seconds and resumed early; End opened the populated summary, Return cleared simulation, and an ordinary Start used phone GPS again. The final phone is left idle with no active journey.

Local evidence: `artifacts/voice-investigation/phone-log.txt`, `acceptance-live-log.txt`, `immediate-interruption-log.txt`, `copilot-recognition-dex.txt`, and `speaking-*.png`. These are intentionally ignored; this document contains the relevant bounded facts.

## Automated gates and shortest recheck

Commands:

```powershell
.\scripts\verify.ps1 -Offline
.\gradlew.bat build lintRelease --offline --console=plain
```

Results: **89 tests in each Debug and Release, zero failures/errors/skips; both builds and both lints PASS**. Lint has only the four existing Debug dependency warnings and one Release dependency warning. APK v2 signature PASS; final APK SHA-256 `B5F0997EE6253615C74B86B015892478BB41F382E5864BC0864BF58E76DC21F8`. Final package was installed with `adb install -r`; saved yue remained selected, preview/Settings Back and ordinary GPS Start/end were rechecked. Machine-readable packaging results are in `artifacts/verification.json`. The original 70 tests remain; M1.4 adds 19 tests covering Android code mapping, accurate notices, cancellation/races, RMS lifecycle/normalization, recognizer watchdog/destruction, permission races, Chinese voice ordering/fallback, persistence/runtime restoration, actual preview voice/rate/default fallback, isolated preview, and portrait/landscape indicators with real Journey transitions. The original M1.1, M1.2, M1.3 and D0–D11 suites remain included.

Shortest phone recheck: Settings → select voice → 试听 → 保存 → Back; title five taps → 80 km/h / 16× / 开始; observe automatic question and narration, animated scanner and Speak interruption; quiet/resume; explicit End → summary → return; ordinary Start must show 手机 GPS. For Chinese ASR acceptance, first complete the system recognizer step above, then tap Speak and say 再讲一点; inspect `adb logcat -s KITTVoice KITTSim` without recording speech text.

Platform API references: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice).
