# TASK CARD — KITT V0 Autonomous Overnight Build

## Mission

Autonomously build **KITT / 路上读山河 V0** to the furthest complete, testable state possible before user review.

You are expected to work **without routine user intervention**. Read the repository documents first and treat them as the source of truth:

1. `AGENTS.md`
2. `docs/PRODUCT_SPEC.md`
3. `docs/AI_CONTRACT.md`
4. `docs/ROADMAP.md`

Then execute the roadmap as a Destination Chain.

## Core objective

Deliver a runnable native Android V0 whose golden path is:

> Start journey → obtain location/context → AI Director decides SILENT/SPEAK_NOW/PREPARE/ASK_USER → TTS output → user can interrupt and speak → same AI responds → journey can enter/exit 10-minute quiet mode → trip can end with summary + rating.

The required simulation target is:

> **成都 → 绵阳，约 80 km/h**

If the exact user-provided AMap route screenshot is not available, do **not** stop. Use a clearly labeled coarse fixture and make route data replaceable later.

## Working style

This is an **AI Native autonomous build**.

Do not ask the user for ordinary implementation choices.

You may independently:
- choose package/module structure;
- select Android libraries that are justified by current requirements;
- refactor as needed;
- write tests;
- create milestone commits;
- change implementation path when a chosen path fails;
- use fakes/mocks/fallbacks to keep moving;
- discard unnecessary complexity.

Before adding new architecture, dependency, service, database, agent, or subsystem, ask:

> Does this solve a problem that has already appeared?

If not, do not add it.

## Decision order

Always prefer:

> **Local deterministic logic → ask the user naturally → AI reasoning**

Do not build complicated prediction systems for information that can be obtained from one natural-language question.

## Hard product boundaries

Do not expand V0 into:
- navigation;
- route planning;
- RAG / vector DB;
- large geographic knowledge base;
- local LLM;
- multi-agent pipeline;
- complex model router;
- Android Auto;
- vehicle control;
- wake word / always-on microphone;
- camera scene recognition;
- hotel/restaurant booking;
- music/audio-focus integration with the car head unit;
- analytics backend;
- server infrastructure;
- long-term full GPS or conversation storage.

GPS is the hard dependency.
AMap/map enrichment is optional. If it is awkward or unavailable, leave it out and continue.

## AI Provider strategy

The product must not depend on one provider.

Priority:
1. Try the currently viable ChatGPT/Codex-related auth/call path.
2. If it is not practical for V0, switch to a normal API Provider.
3. If user auth/API credentials are unavailable during this run, implement the Provider Adapter + Fake Provider and continue the rest of the roadmap.

Do not let provider authentication stop unrelated work.

The Settings surface may expose:
- Provider
- Model
- Reasoning effort when supported

Do not hard-code model-specific behavior into Director logic.

## Blocker policy

The user is going to sleep and does not want to act as a dispatcher.

Only treat these as true external blockers:
- OAuth/login that only the user can approve;
- API key unavailable anywhere in the environment;
- physical-device behavior that cannot be validated in the current environment;
- the promised AMap route screenshot for final fixture refinement.

When blocked:
1. isolate the blocker;
2. complete all work that can proceed without it;
3. create a fake/fallback path where appropriate;
4. record the exact remaining action in `HANDOFF.md`;
5. continue with every other destination.

Do **not** stop the entire project waiting for the user.

## Milestone discipline

For each roadmap destination:
- implement;
- build;
- run relevant tests;
- fix failures;
- commit with a clear milestone message;
- keep the tree clean when practical;
- proceed automatically to the next destination.

Do not create “approval gates” that require the sleeping user to respond.

## Quality requirements

At minimum verify:
- app builds;
- start/end journey;
- 10-minute quiet mode and early exit;
- user interruption immediately stops TTS;
- ASK_USER is one-shot and does not nag;
- PREPARE invalidates when stale;
- stale narration is never replayed merely because it was generated;
- auto-provider failure degrades to silence;
- active-request failure gives a short user-facing message;
- structured Director response validation;
- simulation location source flows through the same downstream pipeline as real GPS.

## Final overnight deliverable

Before stopping, update/create `HANDOFF.md` with:

- current status;
- what is fully implemented;
- build command and result;
- tests and results;
- simulation status;
- default AI Provider/model/effort if real provider works;
- any auth/key/device step that only the user can perform;
- known limitations;
- exact morning acceptance steps;
- milestone commit list;
- whether the working tree is clean.

The user should be able to wake up and **perform a total acceptance test**, not reconstruct your work.

## Definition of done for tonight

Best case:
> A runnable V0 with the 成都→绵阳 simulated golden path working end to end.

Acceptable fallback:
> Everything except unavoidable user-only auth/device steps is complete, with those remaining steps minimized and documented precisely.

Do not stop merely because the first implementation path fails. Change path, simplify, fake, or defer optional enrichment and keep moving.

**Run to completion.**
