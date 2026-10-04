# Future Task — Narration Granularity + Detail Controls

Status: **BACKLOG — design after current field use**
Priority: useful UX control, especially once offline regional knowledge packs exist.

## Core idea

Narration should expose two independent user controls:

1. **Spatial / chapter granularity** — how fine a geographic layer is allowed to become a narration chapter.
2. **Narration detail** — how deeply the Director should explain a chosen topic.

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

The two controls create distinct listening styles:

- **Coarse + Brief** → few interruptions, quick overview.
- **Coarse + Deep** → fewer topics, but substantial explanations.
- **Fine + Brief** → many local observations, each short.
- **Fine + Deep** → dense cultural exploration; potentially very talkative.

This is why the controls must remain separate.

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

Neither control should:
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

Test with the same route under the four combinations:
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

