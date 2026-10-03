# M1.2 — Navigation + simulation-state isolation

## Goal

Fix two real-phone V0 bugs discovered after M1.1, without changing AI Provider behavior or redesigning KITT.

### Bug A — Android Back from Settings exits the app
Current behavior:
- Open Settings from the idle main/driving screen.
- Press the Android system Back button.
- The Activity exits instead of returning to the KITT main screen.

Expected behavior:
- If Settings is visible, Android Back returns to the main KITT screen.
- Only when already on the main idle screen should the normal Android Back behavior be allowed to leave the Activity.
- The existing in-page “返回” control should keep working.

Known implementation clue:
- `MainActivity.kt` currently keeps Settings as local Compose boolean state (`settings`) but does not intercept system Back.
- Use the normal Compose/Android back handling path appropriate for this state; do not introduce a navigation framework solely for this fix.

### Bug B — ChatGPT is selected, but Start Journey still starts the Chengdu→Mianyang simulation
Current behavior:
- User previously enabled developer simulated location.
- User later signs in to ChatGPT and saves ChatGPT as Provider.
- Tapping “开始读山河” still uses the simulated Chengdu→Mianyang LocationSource.

Root cause to verify:
- Provider selection and LocationSource selection are independent.
- `SettingsStore.simulation` persists developer simulation in SharedPreferences.
- `KittRuntime.start()` chooses `SimulatedLocationSource` whenever persisted `simulation == true`, regardless of Provider.
- Therefore a past developer test leaks into later normal journeys.

Expected product behavior:
- **Normal “开始读山河” must use real phone GPS by default, regardless of Provider.**
- ChatGPT/Fake/OpenAI/Compatible are AI-engine choices; they must not implicitly choose simulated vs real location.
- Simulation is a hidden developer test action, not a sticky production-mode setting.

## Required behavior

### Normal start
From the ordinary main screen:
- “开始读山河” starts a normal journey with **RealLocationSource**.
- It must not silently inherit a prior developer simulation session.
- The main screen source label must show phone GPS / real mode, not 成都→绵阳 simulation.

### Developer simulation
From the hidden developer dialog:
- User can explicitly start the 成都→德阳→绵阳 simulation.
- The developer choice may persist speed/acceleration preferences if useful.
- **The fact that simulation is active should not persist as the default source for the next ordinary journey.**
- Ending/stopping simulation must return the runtime to normal real-GPS mode.

### Cleanup
When a simulated journey ends or is stopped:
- clear active simulated-source state;
- clear/normalize the main-screen simulation label;
- do not leave “离线演示 · 成都→绵阳” or “成都→绵阳 · 粗粒度模拟...” on the idle main screen;
- starting the next ordinary journey must use RealLocationSource.

This should also close the previously observed residual-label bug.

## Design constraint

Do not couple Provider to LocationSource.

Correct conceptual separation:

```
AI Provider:
  ChatGPT | Fake | OpenAI API | Compatible API

Location Source:
  Real GPS (normal default)
  Simulated fixture (explicit developer-run only)
```

Fake Provider can run against real GPS if desired.
ChatGPT Provider can run against simulated GPS when explicitly launched from developer mode.
These are orthogonal axes.

## Suggested implementation direction

Prefer a small runtime/session-level source selection rather than a persisted global “simulation mode”.

Possible shape:
- persisted developer preferences:
  - simulation speed
  - acceleration
- transient next/start mode:
  - NORMAL_REAL
  - DEVELOPER_SIMULATION

The exact implementation is up to the Agent.

Do not add a navigation framework, database, or new architecture for these fixes.

## Tests

Add regression coverage for:

1. Settings visible + system Back => main screen, Activity not finished.
2. Main idle screen + system Back => existing/normal platform behavior remains acceptable.
3. Persisted/legacy `simulation=true` cannot force a normal Start Journey into simulated mode after upgrade.
4. Explicit developer “开始” => simulated source.
5. End developer simulation => idle main screen no simulation route label.
6. Next ordinary Start => RealLocationSource.
7. Provider kind ChatGPT + normal Start => RealLocationSource.
8. Provider kind ChatGPT + explicit developer simulation => SimulatedLocationSource.
9. Existing M1.1 ChatGPT OAuth/inference tests remain green.
10. Existing full simulation tests remain green.

If there are old installs containing `simulation=true`, migrate/normalize safely on first use or otherwise guarantee normal Start is real GPS.

## Acceptance gate

On the user's phone:

- Open Settings, press Android Back => returns to KITT main screen.
- ChatGPT remains selected (`gpt-5.6-sol / medium` unless user changes it).
- Tap ordinary “开始读山河” => uses phone GPS, not 成都→绵阳 fixture.
- End journey => idle screen contains no simulation-route residue.
- Open hidden developer menu and explicitly start simulation => simulation still works.
- End simulation => ordinary Start again returns to phone GPS.
- Build/lint/all tests pass.
- Commit, push to `main`, update `HANDOFF.md`.

Do not change unrelated UI/content behavior in this milestone.
