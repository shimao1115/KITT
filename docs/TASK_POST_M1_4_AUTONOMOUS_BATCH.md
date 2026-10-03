# Task Card — Post-M1.4 Autonomous Batch

Status: **IMPLEMENTED — phone-only gates DEFERRED TO COMBINED PHONE ACCEPTANCE**
Owner: Codex / construction agent
Source of truth: this file + existing milestone docs + HANDOFF.md

2026-10-03 user refinements executed: the coarse simulation now starts in **成都市新都区** and ends in
**绵阳市安州区雎水镇**, with 29 points / 13 chapters. Area Chapters remain the main content container;
grounded high-salience geographic/landmark proximity also creates independent Director opportunities inside
the same chapter, preserving quiet/user/stale/dedup safeguards. See `docs/LANDMARK_PROXIMITY.md` for coverage.
Phase A–E results: `docs/ACCEPTANCE_POST_M1_4.md`; final phone pass: `docs/COMBINED_PHONE_ACCEPTANCE.md`.

## User decision

The user is temporarily offline and wants the remaining KITT work completed autonomously.

**Chinese ASR is explicitly out of scope for this batch.**
Do not spend time on vivo Copilot ASR, third-party recognizers, iFlytek/OpenAI transcription, or recognition-service replacement. That topic will be handled in a separate later milestone.

M1.4 is considered complete for now.

---

## Goal

Finish the remaining KITT V0.2 work that does not require the user's phone, then leave one combined real-phone acceptance pass for the user's return.

The batch should make KITT:
- understand the route as a sequence of meaningful local places;
- talk about history/culture/sites as well as roads/geography;
- accept an optional route screenshot;
- support in-trip image discussion;
- show richer but still driving-safe state/UI;
- remain technically clean and regression-safe.

Do not stop simply because the phone is offline.

---

# Phase A — M1.5 Area Chapters + Local Narrative

Use `docs/TASK_M1_5_AREA_CHAPTERS.md` as the detailed contract.

## Product model

> **区县/县级市提供背景；镇/乡/街道作为基本章节；GPS推动章节切换；AI决定这一章讲什么。**

Implement:

### A1. AreaResolver
- Resolve a practical administrative hierarchy:
  - city/prefecture if useful;
  - district/county/county-level city;
  - town/township/street/subdistrict.
- Do not over-model administrative law.
- Real GPS path: best-effort reverse geocoding, rate-limited.
- Simulation path: explicit area metadata is allowed in fixture data.
- Area lookup failure must never block Journey.

### A2. Area Card
Create a compact session-local Area Card per active chapter.

Suggested content:
- district/county background;
- current town/township/street chapter;
- one-sentence orientation;
- current direction/fix;
- small candidate set;
- candidate topic family;
- grounding/confidence;
- recent topic families.

Cache per visited area for the current trip.

### A3. Narrative breadth
Candidate families must include, when relevant:
- natural geography / landform;
- roads / engineering / transport;
- settlements / everyday life;
- local history / historical change;
- ancient towns / historic settlements;
- heritage / ruins / monuments / museums / temples;
- famous cultural/scenic places;
- place names / customs / cultural geography;
- industry / agriculture / food / local economy;
- people / stories when grounded.

Roads are one lens, not the subject of the whole journey.

### A4. Topic-family diversity
Track recent topic family in addition to topic text.

Avoid repeated sequences such as:
- road mechanism;
- road mechanism;
- settlement mechanism;
- settlement mechanism.

Prefer another grounded, high-value family when available.

Do not enforce a rigid quota.

### A5. Major-node salience
High-salience cultural/history nodes may outrank generic explanations.

**Acceptance benchmark: Guanghan / Sanxingdui.**

When the simulated Chengdu→Deyang→Mianyang run reaches the Guanghan-related chapter:
- Sanxingdui must appear in the candidate set as a high-value culture/history/site candidate;
- do not hard-code a narration script;
- do not force it to speak if Director legitimately chooses silence, but the system must be capable of noticing it.

### A6. Current-place display
If AreaResolver provides a human-readable place:
- show a concise district + town/street label on the driving screen;
- keep it glanceable;
- fall back safely when unavailable.

No broad UI redesign in Phase A.

---

# Phase B — Route Reference Image

Add an optional route-reference image flow for trip setup.

## UX
Idle/start surface:
- secondary action: **添加路线参考图（可选）**
- use Android photo picker;
- route screenshot is optional.

## Semantics
- image is a route hint, not navigation truth;
- GPS remains authoritative;
- analyze the image once;
- compress result into a short session-local `RouteHint`;
- reuse the text hint in later Director calls;
- do not resend the image on every request.

## Provider behavior
- use selected multimodal provider when supported;
- unsupported provider must return a clear explanation;
- do not silently drop the image;
- do not add a backend or map scraping.

## Lifecycle
- route image/raw bytes should not become permanent trip memory;
- clear session image/hint when appropriate at trip end;
- keep only the compact RouteHint during the active trip.

---

# Phase C — In-trip Visual Talk

Add a small secondary image control during an active journey.

## Entry
- visually secondary to voice;
- tap → **拍照 / 从相册选择**;
- suitable for passenger/stopped use.

## Behavior
Treat image as an active user message.

Support:
- default question: **“帮我看看这个”**;
- optional short spoken/typed follow-up;
- include current location/journey/session context;
- return answer through normal KITT response flow where practical.

## Boundaries
- do not attach the previous photo to later automatic Director checks;
- do not build an image queue;
- do not store raw trip photos long-term by default;
- a tiny session-local textual summary is allowed if useful;
- route-reference image and Visual Talk may share plumbing but remain separate product intents.

---

# Phase D — Content Tuning + Simulation Gates

After A–C, tune the Director experience.

## Desired behavior
- SILENT remains valid.
- Do not force a fixed narration count.
- Do not rotate topic categories mechanically.
- Avoid the M1.3 failure mode where almost all useful content collapses into road/settlement mechanisms.
- Prefer high-value local culture/history/site content when grounded.
- Preserve “再讲一点” as deepening the current topic.
- Preserve skip/quiet/user-intent priority.

## Simulation acceptance
Run the coarse Chengdu→Deyang→Mianyang route with:
- 100 km/h;
- 16× acceleration;
- real pipeline where available;
- fake/deterministic regression where needed.

Verify:
- chapter transitions;
- Area Card refresh/caching;
- topic-family diversity;
- Sanxingdui candidate in Guanghan chapter;
- no stale/cancel regression;
- no forced speech from boundary crossing;
- no retry storm/provider failure cascade.

If real ChatGPT cannot be exercised without phone/account state, record this gate as deferred instead of claiming PASS.

---

# Phase E — KITT Visual Identity / UI Polish

Do this only after behavior above is stable.

## Direction
- restrained dark/black base;
- red scanner/pulse/glow language;
- large driving-safe hierarchy;
- speaking/listening/quiet states visually distinct;
- current location readable;
- camera/image controls secondary;
- portrait + landscape both usable;
- avoid fast flashing and decorative complexity.

Preserve M1.4 voice-state animation semantics.

Do not rewrite navigation/state architecture merely for visual polish.

---

# Explicit Non-Goals

Do not implement in this batch:
- Chinese ASR replacement/integration;
- iFlytek/OpenAI/Vosk speech recognition;
- wake word;
- always-on microphone;
- backend/server;
- vector DB/RAG;
- full navigation engine;
- route prediction;
- map scraping;
- Android Auto;
- vehicle control;
- long-term raw GPS/audio/photo storage;
- large user-profile system.

---

# Engineering Rules

Preserve:
- Provider/LocationSource separation;
- ChatGPT OAuth isolation;
- real GPS vs simulation isolation;
- M1.3 accelerated-cadence semantics;
- 1500m stale protection;
- wall-clock quiet timing;
- user-intent priority;
- no narration queue;
- no secrets in repo.

Prefer:
- local deterministic logic first;
- ask user only when needed;
- AI reasoning last;
- small milestone commits;
- smallest sufficient implementation.

Do not refactor working code purely for style.

---

# Tests / Gates

After each phase:
- Debug unit tests PASS;
- Release unit tests PASS;
- Debug/Release build PASS;
- lint PASS;
- existing M1.1–M1.4 regressions remain green;
- add focused tests for new behavior.

At minimum add tests for:
- area normalization;
- area change detection;
- reverse-geocode throttling;
- Area Card cache;
- topic-family dedup/diversity;
- Sanxingdui candidate discovery;
- boundary change does not force speech;
- RouteHint lifecycle;
- unsupported multimodal provider behavior;
- Visual Talk image lifecycle;
- trip-end cleanup;
- portrait/landscape state rendering where practical.

---

# Phone-unavailable rule

The phone may be offline for the duration of this batch.

Therefore:
- do not stop for phone-only checks;
- do not invent phone PASS results;
- mark them **DEFERRED TO COMBINED PHONE ACCEPTANCE**;
- complete all non-phone work first.

---

# Final deliverable before user returns

Repository must contain:
- Phase A–E implementation, or an exact blocker record for any unfinished phase;
- small milestone commits;
- updated `HANDOFF.md`;
- a concise **Combined Phone Acceptance** checklist;
- latest installable APK artifact;
- Debug/Release tests + build + lint results;
- explicit list of deferred phone-only gates;
- clean tracked working tree.

## Combined Phone Acceptance checklist must cover

1. M1.4 speaking/listening visuals and TTS voice persistence.
2. Real GPS AreaResolver/current-place display.
3. Real ChatGPT narrative variety.
4. Guanghan/Sanxingdui behavior.
5. Route screenshot → RouteHint → later Director use.
6. In-trip camera/gallery → multimodal discussion.
7. Real GPS vs developer simulation isolation.
8. 100 km/h + 16× accelerated simulation.
9. background/lock-screen endurance.
10. end-trip cleanup/summary.
11. portrait/landscape final UI usability.

Chinese ASR is **not part of this combined acceptance** unless the user explicitly reopens that milestone.
