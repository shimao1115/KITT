# Standalone public RecognitionService probe

This diagnostic APK has a separate package (`com.kitt.asrprobe`). It does not build, install,
link to, or configure Yantu. No production backend or UI changes are involved.

Requirements: Android SDK 35/build-tools 35.0.0, JDK 17, existing Android debug signing key.
Set `ANDROID_HOME` and `JAVA_HOME`, then run `./scripts/asr-reprobe/build.ps1` from PowerShell.

Install `artifacts/google-asr-reprobe/probe.apk` and launch `com.kitt.asrprobe/.ProbeActivity`.
Grant the probe microphone permission yourself. Each button starts one `zh-CN` request;
speak after ready and remain on screen until a terminal callback or the 30-second probe limit.
Only enabled/exported Google services discovered by the public action query receive test buttons.
The default-service button uses the framework's default selection without changing it.

Collect `adb logcat -d -s GoogleASRProbe:I '*:S'` before uninstalling the temporary package.
The APK is not debuggable; `run-as` is not used for collection. Logcat includes process IDs;
attempt numbers reset when the Activity is recreated, so identify a run using its wall time and PID.
All callback events (including each RMS) are logged as JSON; audio buffers are never retained.
The private JSONL file is diagnostic data and is removed when this temporary app is uninstalled.

`probeTimeout` and `cancel` are probe lifecycle events, not fabricated SpeechRecognizer error codes.
Package-manager visibility and ready are evidence of access, not evidence of successful transcription.
See `docs/GOOGLE_ASR_REPROBE.md` for the actual device results.
