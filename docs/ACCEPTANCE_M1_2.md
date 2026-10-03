# M1.2 acceptance — 2026-10-03 (Asia/Shanghai)

**PASS**: Settings Back navigation and session-only simulation source selection. No Provider/OAuth/inference, Director, Journey, Voice, fixture or production dependency changes.

## Build and regression gates

```powershell
.\scripts\verify.ps1 -Offline
.\gradlew.bat build lintRelease --offline --console=plain
```

Both commands passed. Debug and Release each ran **60 tests**, with **zero failures, errors or skips**. This includes all 18 M1.1 OAuth/inference tests and the original D0–D11 scenarios. Eight new tests exercise real Compose Back dispatch, in-page Return, normal platform Back, UI start intents, migration from legacy `simulation=true`, real/explicit simulated starts across all four Providers, cleanup, service start defaults, and continued simulation recovery without making it the next journey's default.

Two existing tests now request simulation explicitly / verify only persistent speed preferences. Three existing Compose UI tests now host their content in the declared MainActivity, so they also work in Release; the previous test host depended on Debug's UI-test manifest. Their behavioral assertions are preserved. No test Activity is added to the Release APK.

Debug and Release lint both have **zero errors**. Debug has four existing newer-dependency warnings; Release has one warning. Both APK variants build; the installed acceptance artifact is the debug-signed APK (v2 signature PASS). SHA-256:

```text
16428D6AD3F1423E8F62BED83C93EF2AC694FBBD8705878D65543F7500D90F6D
```

Full Chengdu→Deyang→Mianyang production-core simulation remains PASS with Fake Provider/fake Voice: **110,452 m**, **80 km/h**, **82 simulated minutes**, **64 Director checks**, **4 voice outputs**. This covers start, one-shot destination question/reply, narration, interruption/follow-up, cooldown, quiet expiry/early exit, and explicit end/summary.

## Phone gate

Connected device: **vivo V2405A, Android API 36**. Updated using `adb install -r`, preserving app data and the existing authorized account.

| Check | Observed result |
|---|---|
| Upgrade precondition | Legacy settings contained `simulation=true`; Settings system Back reproduced return to the launcher before upgrade. |
| Legacy migration | Removed only the global simulation flag. Speed preferences and saved Provider/model/effort remain. |
| Settings system Back | Returns to KITT main screen, with Start visible. |
| Idle main system Back | Leaves the Activity normally; reopening shows real-GPS idle mode. |
| Ordinary Start | Shows phone GPS and running journey; Android location service has KITT's GPS listener requesting updates every 2 seconds / 5 m. |
| Ordinary End | Summary opens; returning to departure shows phone GPS without a fixture label. |
| Explicit developer Start | Shows Chengdu→Mianyang simulation label and Chengdu simulated area, through the production pipeline. |
| Developer Stop / End | Stops the simulated journey; returning to departure clears the fixture label. |
| Next ordinary Start | Returns to phone GPS; no sticky simulation state. |
| Existing ChatGPT connection | Plan permission enabled, five account models loaded; Director test completed with `SPEAK_NOW（ChatGPT 计划）`. |
| Saved account settings | ChatGPT / `gpt-5.6-luna` / `high`, unchanged from the pre-upgrade phone configuration. |

The phone is left on the idle main screen in real-GPS mode, with no active journey. No new user authorization/key step is required. Credentials were not extracted or manually changed. Raw UI snapshots and helper commands remain locally under ignored `artifacts/m1.2-*`; credential values are absent from tracked evidence.

This gate establishes correct source selection and GPS registration, not reception of a fresh physical GPS fix indoors. Original outdoor GPS/audio/ASR, OEM background behavior and subjective narration quality remain the broader manual acceptance boundaries described in HANDOFF.md. The fixture remains coarse and non-navigation-grade. Its coordinates are fixed; location samples advance continuously at the chosen speed. Fake narration is fixed demonstration content; a real Provider generates Director responses from current Context and user input.
