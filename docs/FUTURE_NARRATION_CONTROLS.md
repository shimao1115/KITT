# Future Task — Narration Granularity + Detail + Initiative Controls

Status: **BACKLOG — design after current field use**
Priority: useful UX control, especially once offline regional knowledge packs exist.

## Core idea

Narration should eventually expose three independent user controls:

1. **Spatial / chapter granularity** — how fine a geographic layer is allowed to become a narration chapter.
2. **Narration detail** — how deeply the Director should explain a chosen topic.
3. **Narration initiative / frequency** — how proactively and often the app seeks appropriate opportunities to speak.

These are orthogonal and should not be collapsed into one “more/less” setting.

## 1. Granularity control

Purpose: decide what geographic level is worth automatically treating as a new content chapter.

Suggested product levels:

### Coarse
Focus on:
- prefecture / city;
- district / county;
- major high-salience landmarks.

Ordinary township / street transitions do not automatically create chapter opportunities.

Good for:
- long highway drives;
- users who want fewer interruptions.

### Standard
Focus on:
- district / county;
- meaningful township / town / street chapters;
- important landmarks.

Default candidate.

### Fine
Allow:
- township / street;
- important village / historic settlement when data quality supports it;
- smaller local landmarks / geographic features.

Good for:
- slow travel;
- cultural / exploratory driving.

Important:
- finer granularity does **not** mean every boundary must trigger speech;
- it only expands the set of locations that are eligible for Director inspection;
- salience, evidence quality, repetition and current context still decide whether to speak.

## 2. Detail control

Purpose: decide how much depth a selected topic receives.

Suggested levels:

### Brief
- identify the object;
- explain why it matters;
- 1–2 memorable facts;
- stop before deep background.

### Standard
- self-contained explanation for a first-time outsider;
- enough historical / cultural / geographic context to understand the topic;
- a few concrete details;
- natural stopping point.

Default candidate.

### Deep
- multiple layers / angles when evidence exists;
- richer background, chronology, people, material culture, mechanisms or local connections;
- suitable for major nodes and “tell me more” behavior;
- avoid padding when evidence is thin.

Do not implement detail as a rigid word/time quota. It is a target depth, not a fixed duration.

## 3. Interaction matrix

The first two controls create distinct listening styles:

- **Coarse + Brief** → few interruptions, quick overview.
- **Coarse + Deep** → fewer topics, but substantial explanations.
- **Fine + Brief** → many local observations, each short.
- **Fine + Deep** → dense cultural exploration; potentially very talkative.

All three controls must remain separate. Frequency adds a third dimension rather than replacing these combinations.

## 4. Runtime semantics

Granularity should affect:
- which Area levels can create chapter opportunities;
- which offline-pack levels are loaded by default;
- which local entities are considered for automatic narration;
- fallback behavior when location accuracy is coarse.

Detail should affect:
- Director session instructions;
- how much Topic material is selected;
- whether additional Topic Dossier material is pulled before narration;
- “再讲一点” depth.

No control should:
- override user explicit commands;
- force narration when evidence is weak;
- weaken provenance requirements;
- turn topic families into a playlist.

## 5. UI direction

Prefer simple segmented controls over fine-grained sliders, especially for in-car use.

Example:

> 播报粒度：粗 / 标准 / 细
>
> 讲述详细度：简短 / 标准 / 深入
>
> 播报主动性：安静 / 均衡 / 活跃

Optional later:
- “自动” can adapt to speed, route type, trip duration, passenger mode, and content density.
- Do not add automatic mode until manual levels are stable and understandable.

Settings must be usable before departure; avoid requiring interaction while driving.

## 6. Offline-pack interaction

The future offline knowledge-pack schema should support these controls directly.

Example:
- coarse mode may only load county Overview + high-salience Topics;
- fine mode may additionally load township/street Overview and smaller Topics;
- brief mode can stop at Overview/short Topic facts;
- deep mode may retrieve deeper Topic facts or local source excerpts.

This should be a query / selection policy over the same knowledge base, not separate databases.

## 7. Acceptance

Test with the same route under representative combinations (including these four granularity/detail examples and each initiative level):
- Coarse + Brief
- Coarse + Deep
- Fine + Brief
- Fine + Deep

Verify:
- clearly different behavior;
- no hardcoded topic playlist;
- no duplicate narration;
- user can still interrupt and ask questions;
- changing detail does not change geographic triggering;
- changing granularity does not silently force longer narration;
- settings persist between trips if the user chooses.


## 8. Narration initiative / frequency (recorded 2026-10-09)

**Status: BACKLOG ONLY.** Do not implement as part of V0.3.4 status transparency work, and do not adjust the existing scheduling thresholds without first using real driving observations.

Why this is independent:

- **Granularity** asks `which geographic levels and entities are eligible?`
- **Detail** asks `how deeply should one chosen subject be explained?`
- **Initiative** asks `how actively should 沿途 look for the next worthwhile chance to speak?`

Suggested three-position control:

- **安静 / Quiet** — fewer discretionary checks, more tolerance for silence, significant places and user requests still served.
- **均衡 / Balanced** — sensible default, keep the current event-driven spirit.
- **活跃 / Active** — more proactive checks in slow urban/township travel and dense cultural areas, subject to evidence quality, cooldown, duplication protection and current user interaction.

Examples:
- 活跃 + 简短 = frequent but short local discoveries.
- 安静 + 深入 = fewer subjects, but substantial narration when something merits it.

### Current baseline to observe, not a proposed hard schedule

In the V0.3.3 `Journey.checkDelayReason` production **real-location** path:
- routine check requires ≥45s since prior check **and** ≥3 km if less than 3 min since last speech; otherwise ≥1.5 km;
- slower movement escape: ≥5 min and ≥300 m;
- new area chapter and valuable grounded landmark proximity may grant independent checks;
- post-auto-speaking soft cooldown ~60s; user replies and explicit quiet modes retain priority;
- actual playback remains decided by Director (SILENT / PREPARE / SPEAK_NOW / ASK_USER), availability of evidence, freshness and TTS readiness.

These are *Director inspection opportunities*, **not a guaranteed time interval between audio clips**.
Search latency, poor signal, insufficient material and explicit silence can create longer gaps. A permanent periodic “speak every N minutes” requirement is not wanted.

### Future tuning and acceptance

1. Use V0.3.4 observable current-work/why-silent statuses and real-road evidence to separate **cadence gate**, **research latency**, **Director SILENT**, **failed request**, **intentionally quiet**, and **speech playback**.
2. Test highway vs slow-town/city/cultural trip scenarios at all initiative levels; avoid lots of low-value or repetitive narration.
3. Initiative may tune thresholds/opportunity frequency, but must not force speech or create a content queue; Director can always choose SILENT.
4. Existing interruption, user-question, explicit quiet, GPS accuracy and source-provenance constraints remain non-negotiable.
5. A more active mode must not cause request storms, runaway model/search cost, battery drain, or repeated facts.
6. Verify changing initiative does not silently change geographic detail level or length of each narration.
7. Prefer only a few understandable presets; consider adaptive behavior after real measurements, not before.

**Sequencing:** keep V0.3.4 scope on status visibility. After observation, revisit initiative/frequency tuning alongside (not inside) granularity/detail and Issue #13 repetition quality.
