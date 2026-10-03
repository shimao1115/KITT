# M1.3 — Make accelerated simulation actually exercise the AI Director

## Real-phone bug

Observed on the current M1.2 phone build:

- Provider: ChatGPT account
- Simulation: 成都→德阳→绵阳
- Physical speed: **100 km/h**
- Acceleration: **16×**
- Result: the simulated car reached Mianyang and **KITT never spoke once**.

This makes the accelerated simulator poor for real-AI acceptance even though the production real-GPS path may be fine.

## Root causes to verify

Current code strongly suggests two independent acceleration mismatches.

### 1. Director observation cadence is wall-clock based

`Journey.shouldCheck()` currently requires roughly:
- at least 45 seconds of wall time between checks, plus distance conditions;
- wall-clock speech/dialogue cooldowns.

At 100 km/h × 16, the ~110 km fixture finishes in only a few wall-clock minutes, so the Director receives far fewer opportunities than it would during the represented journey.

### 2. A real provider response can become spatially stale before it returns

Automatic tickets are rejected when current position has moved more than ~1500 m from the request fix.

At 100 km/h × 16:
- virtual speed is ~444 m/s;
- a response taking only 4–5 real seconds can be >1.5–2.2 km behind;
- therefore an otherwise valid real ChatGPT result can be discarded before delivery.

Also inspect movement during TTS/listening: at accelerated speed the virtual car may travel tens of kilometers while a normal-length narration is spoken, reducing the simulator's usefulness as an interactive acceptance tool.

Do not assume these are the only causes; reproduce and instrument non-secret lifecycle/action events.

## Goal

Make accelerated developer simulation represent **compressed driving between interactions**, while preserving normal production behavior.

The simulator should allow the user to evaluate:

> simulated moving GPS → repeated Director opportunities → real ChatGPT actions → TTS → user interruption/dialogue

without waiting 80+ real minutes and without accelerated travel invalidating every response.

## Hard constraints

- **Do not change normal real-GPS Director cadence.**
- **Do not change ChatGPT OAuth/provider implementation.**
- **Do not force the AI to speak.** SILENT remains valid.
- **Do not turn simulation into scripted narration.**
- **Do not accelerate user-facing quiet durations.** “安静 10 分钟” is still 10 real minutes.
- Do not weaken stale-response protection globally just to make the simulator pass.
- No new backend/map/RAG system.

## Required simulation semantics

### A. Simulation-aware Director opportunities

For explicit developer simulation, Director checks must be driven by **simulated route progress / simulated travel time**, not only wall-clock elapsed time.

Target behavior for the 成都→绵阳 fixture:
- at 16×, give roughly **8–15 meaningful automatic Director opportunities** over the full route when not blocked by speech/listening/quiet/provider activity;
- do not spam requests every second;
- route/area transitions are natural opportunities;
- 60× remains a smoke-test mode and may produce fewer completed calls if provider latency dominates, but must not be limited to the old wall-clock 45-second cadence.

Implementation can use simulated travel time, distance since last check, route progress, or a small simulation-specific policy. Keep it simple.

### B. Accelerated movement must not invalidate normal provider latency

Do not globally increase the 1500 m stale threshold.

For developer simulation only, choose the simplest safe behavior so a normal 2–15 second provider response can still be evaluated against the intended simulated scene.

Preferred product semantics:

> **Acceleration compresses uneventful driving time; it should not make the virtual car race tens of kilometers while KITT is thinking, speaking, or listening.**

Therefore strongly consider pausing or effectively freezing simulated travel progression while:
- an automatic/active Director request is in flight;
- TTS is speaking;
- one-shot listening is active.

Resume accelerated movement after the interaction completes.

If a different small implementation achieves the same acceptance properties without weakening production safeguards, it is acceptable. Document it.

### C. Keep quiet timing real

The existing 10-minute quiet timer and natural-language quiet durations continue to use wall clock.
Simulation acceleration must not make 10 minutes expire in seconds.

### D. Context remains real AI input

ChatGPT simulation must still use the live simulated Context Card. Do not substitute Fake Provider text.

The route is still a coarse, non-navigation-grade fixture. It is okay to improve the fixture/context label with safe broad stage information already represented by the coordinates/areas, but:
- do not invent specific bridges, roads, tunnels, history, or records;
- do not add a fake knowledge base merely to provoke narration.

## Developer diagnostics

Add only lightweight, non-secret diagnostics sufficient to understand a silent run. For developer mode, expose or log at minimum:
- Director opportunity count
- request dispatched count
- terminal action count by SILENT / SPEAK_NOW / PREPARE / ASK_USER
- stale/cancel/failure count

Do **not** log OAuth tokens, full private prompts, API keys, or full user conversation.

A compact developer display is useful if cheap, but logs/test evidence are sufficient.

## Tests

Preserve all current 60 tests and add focused regression coverage.

At minimum:

1. Real-GPS `Journey.shouldCheck()` behavior remains unchanged.
2. 16× simulation gets multiple Director opportunities based on simulated progress.
3. 100 km/h × 16 with a realistically delayed fake provider does not drop every auto result as spatially stale.
4. Simulated progression pauses/is safely bounded while Director request is pending (if pause design is used).
5. Simulated progression pauses/is safely bounded during TTS/listening (if pause design is used).
6. Quiet 10 minutes is still wall-clock 10 minutes under 16×/60×.
7. User interruption still cancels speech/request correctly.
8. PREPARE/stale-content guarantees remain intact.
9. ChatGPT M1.1 auth/inference tests remain green.
10. M1.2 normal Start still uses RealLocationSource and system Back fix remains green.
11. Full-route Fake simulation still passes.
12. Add a new accelerated-simulation test representing **100 km/h + 16×** with delayed provider behavior.

## Phone acceptance

Use the actual connected phone and existing ChatGPT account.

1. Keep ChatGPT Provider selected.
2. Enter hidden developer simulation.
3. Set **100 km/h + 16×**.
4. Start 成都→绵阳.
5. Answer the destination question normally.
6. Let the route run without manually forcing narration.
7. Capture the Director action counters/log summary.
8. Expected:
   - multiple automatic Director requests occur across route progress;
   - normal provider latency no longer causes every response to be discarded;
   - if ChatGPT returns SPEAK_NOW, it is actually heard before being made stale by acceleration;
   - if ChatGPT chooses SILENT repeatedly, the evidence clearly shows genuine SILENT decisions rather than cadence/staleness bugs.

For acceptance, run at least one route where real ChatGPT produces and audibly delivers an automatic `SPEAK_NOW`. If the model genuinely returns only SILENT after adequate opportunities, inspect the Context/System prompt and document the exact returned action distribution before making a minimal prompt/context adjustment. Do not hard-code a forced narration.

## Deliverable

- Build/lint/all tests PASS.
- Phone acceptance recorded.
- APK installed.
- Commit + push to `main`.
- Update `HANDOFF.md`.
- Working tree clean.

This milestone is about making the accelerated simulator a trustworthy KITT acceptance tool, not adding new product features.
