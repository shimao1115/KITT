# V0 acceptance evidence — 2026-10-03

This records the original D0–D11 gate. Current Debug/Release and phone evidence: [M1.2 acceptance](ACCEPTANCE_M1_2.md), with the preserved M1.1 phone record in [HANDOFF.md](../HANDOFF.md).

`assembleDebug testDebugUnitTest lintDebug`: PASS. 34 tests, zero failures/errors/skips. APK signature verification: PASS (v2), package `com.kitt.reader`, version `0.1.0`, min SDK 26, target SDK 35. Lint: zero errors; four newer-dependency notices. Versions were deliberately pinned to the available proven toolchain.

Full production-core simulation: PASS with Fake Provider and fake Voice. Fixture distance 110,452 m, speed 80 km/h, about 82 simulated minutes. 64 Director checks, four TTS outputs: destination question, acknowledgement, one general mechanism narration, and an active deeper reply. GPS samples run through the same Fix → Context → DirectorLoop → Journey → VoicePort path as real GPS; there is no simulation branch in Director/Journey.

Verified events: start → reliable location → ASK_USER → one listening window → destination reply → same Director → speech; field change → narration; interruption → immediate stop → active follow-up → soft cooldown; quiet 10 minutes → expiry without forced speech; early quiet exit; complete fixture → explicit trip end → summary. Separate tests cover PREPARE replacement/expiry/deviation, invalid response/transport failure, late response rejection, skipped-topic suppression, and background question suppression.

Android/Robolectric checks: Activity launch; foreground location service; quiet/end notification actions; background/return consistency; abnormal recovery; simulation progress recovery; JSON summary/ratings; ciphertext settings and fail-closed lost credentials. Compose interaction tests verify portrait and landscape controls without scrolling, single-tap quiet/resume/skip/end, and rating save/return. UI classes run in separate JVM workers to prevent a cached Compose main dispatcher from retaining another Robolectric sandbox's Looper.

This is **technical fallback acceptance**, not a subjective real-AI product PASS. Fake narration is explicitly labeled fixed demonstration content. Physical GPS, actual Chinese TTS/recognition, Android Keystore hardware behavior, lock-screen/OEM lifecycle and real Provider content remain final device acceptance. No connected Android device was available. No relevant API-key environment variable or Platform key in the local Codex auth metadata was found. AMap screenshot was not supplied; the fixture is explicitly coarse and replaceable.

Reproduce and package: `./scripts/verify.ps1 -Offline` after dependencies have been cached, or omit `-Offline` on a new machine. Generated detailed evidence lives in `app/build/acceptance/full-simulation.txt`, JUnit XML/HTML and the lint report. `artifacts/` contains the installable APK, SHA-256, machine-readable verification and simulation report, and is intentionally ignored by Git.
