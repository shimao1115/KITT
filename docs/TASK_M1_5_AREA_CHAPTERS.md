# M1.5 — Area Chapters & Local Narrative (PRIORITY NEXT)

## Priority

**Execute this milestone before M1.4 Voice Experience.**

The user changed priority after the first real ChatGPT simulation run. M1.4's task card remains valid, but voice work is temporarily deferred until this content problem is fixed.

## Why this is urgent

The first real ChatGPT accelerated Chengdu→Deyang→Mianyang run technically worked, but the two automatic narrations were both generic mechanism explanations:
- why the road bends;
- how roadside villages grow.

The user explicitly judged this too narrow. KITT must also read the **history, culture, historic towns, heritage, monuments, famous sites, local stories, industry, food and everyday life** of the places it passes.

A concrete benchmark: a Chengdu→Deyang→Mianyang experience that passes the Guanghan corridor but never makes **Sanxingdui** eligible for narration is editorially incomplete.

The user now wants to return to a previously discussed structure:

> **区 / 镇 / 乡为内容单元。**

Refined product form:

> **区县/县级市提供背景；镇/乡/街道作为基本章节；GPS推动章节切换；AI决定这一章讲什么。**

Administrative units organize content. They do **not** automatically trigger encyclopedic playback.

## Core product model

### 1. Area hierarchy

Represent current spatial chapter with a lightweight structure such as:

- prefecture/city (optional context)
- district/county/county-level city
- town/township/street/subdistrict
- current broad place label
- route direction / current fix
- recent area/topic history

Use Chinese administrative naming pragmatically. The important product levels are:
- **区县级 background**
- **镇 / 乡 / 街道 basic chapter**

Do not over-model China's administrative hierarchy.

### 2. Area boundary != mandatory narration

Entering a new district/town/township:
- refreshes the current **Area Card**;
- refreshes candidate topics;
- gives the Director a new opportunity.

It does **not** mean:
- instantly read a local encyclopedia;
- say the administrative name every time;
- narrate every boundary crossing.

The Director still chooses SILENT / SPEAK_NOW / PREPARE / ASK_USER.

### 3. Area Card

Add a compact session-local Area Card. Suggested fields:

- current district/county-level background
- current town/township/street chapter
- one-sentence place orientation
- a small set of **high-value narrative candidates**
- candidate topic family
- confidence / grounding status
- recent topic families already used

Candidate families should include:

- natural geography / landform
- roads / engineering / transport
- settlement / everyday life
- local history / historical change
- ancient towns / historic settlements
- heritage / ruins / monuments / museums / temples
- famous scenic or cultural places
- cultural geography / place names / customs
- industry / agriculture / food / local economy
- people / stories when grounded

Do not rotate mechanically. The Area Card is a candidate pool, not a playlist.

### 4. Topic-family diversity

Track recent **topic family** in addition to recent topic text.

Goal:
- prevent several consecutive narrations from all being road / settlement mechanism explanations;
- prefer a different high-value family when it is well grounded and relevant.

Do not impose a rigid quota.

### 5. Major-node salience

Allow very high-salience cultural/history candidates to outrank generic mechanisms.

Example acceptance principle:

> If the current Area Card is Guanghan-related and Sanxingdui is a reliable candidate, another generic “why roads bend” explanation should usually not outrank it unless context clearly makes the road story more relevant.

Do not hard-code a Sanxingdui narration script. Sanxingdui is an acceptance benchmark for candidate discovery and editorial priority.

## Resolving the current administrative unit

### Real GPS

Implement a small **AreaResolver** boundary around the location layer.

Preferred first step:
- evaluate Android platform reverse geocoding / `Geocoder` on the actual vivo phone;
- normalize available fields into district/county + town/township/street when present;
- rate-limit lookups: do not reverse-geocode every 2-second GPS fix;
- refresh on meaningful movement / area change / stale interval.

If platform geocoding is unavailable or incomplete:
- degrade gracefully;
- do not block journey;
- keep coordinates/current GPS;
- make the limitation explicit in diagnostics.

Do **not** add a full map SDK solely for this milestone unless the platform path proves unusable and a very small alternative is clearly justified.

### Simulation

Refine the coarse Chengdu→Deyang→Mianyang fixture enough to exercise area chapters.

It is acceptable to attach explicit broad administrative/chapter metadata to fixture points because the fixture is already a test asset.

Ensure the route includes a **Guanghan-related chapter** so Sanxingdui candidate discovery can be tested.

Do not claim the fixture is navigation-grade.

## Local narrative candidate generation

This milestone needs a practical first version, not a giant knowledge system.

### Base behavior

When a new area chapter becomes active:
- generate a small candidate set once;
- cache it for the session/area;
- reuse it across Director checks;
- do not regenerate on every GPS fix.

### Knowledge policy

Permit the model to use **high-confidence, stable, widely known local associations** as candidates with conservative wording.

Examples:
- a famous site strongly associated with the current area;
- a well-known historic town/heritage node;
- a stable local cultural identity.

Still require verification for:
- exact dates;
- exact figures;
- rankings/records;
- current status/opening/closures;
- controversial or uncertain local claims.

Avoid turning “no web search” into “never mention any local history”.

### Optional web verification probe

OpenAI's Responses API supports the hosted `web_search` tool, and the current Sign in with ChatGPT open-source preview says web search is supported subject to model/account/workspace policy. Official docs: 
- https://developers.openai.com/api/docs/guides/tools-web-search
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations

As part of this milestone:
- **probe** whether the signed-in ChatGPT plan/model route used by KITT accepts a small web-search verification request;
- do not assume support;
- do not make the whole milestone depend on it.

If web search is used for user-facing factual narration, follow OpenAI citation requirements and expose minimal clickable source attribution in the UI. Do not silently consume web search output with invisible citations.

If this complicates the first usable version, keep M1.5 on high-confidence stable knowledge + conservative wording and record web verification as the next enhancement.

## Director editorial update

Revise the constitution carefully.

Current behavior over-rewards:
- generic terrain/road/settlement mechanisms;
- silence under sparse local context.

New principle:

> **Roads are one lens, not the subject of the whole journey.**

The Director should:
- consider local history/culture/sites as first-class candidates;
- avoid repeating the same narrative family;
- use the Area Card before falling back to generic mechanisms;
- remain quiet when there truly is nothing new worth saying;
- never invent precise local facts.

Do not simply add “talk more about history” without changing Context/candidates.

## Main-screen place display

This milestone may also satisfy the existing backlog item to show current place while running.

If the AreaResolver yields a human-readable place:
- show a concise current chapter label on the driving screen, e.g. district + town/street;
- keep it glanceable;
- fall back to GPS/coordinates when unavailable.

Do not do broad visual polish here.

## Data / cache boundaries

- Area Cards are session-local by default.
- Cache one Area Card per visited area within the current trip.
- Do not build a server, vector DB, permanent POI database, or RAG stack.
- Do not persist a huge travel knowledge corpus.
- Trip summary may retain only concise topic/area summaries under existing product rules.

## Acceptance

### Simulation

Run Chengdu→Deyang→Mianyang with real ChatGPT, 100 km/h + 16×.

Expected:
- area/chapter transitions are visible in diagnostics;
- Guanghan-related chapter is reached;
- candidate pool contains **Sanxingdui** as a high-salience culture/history candidate;
- automatic narration shows meaningfully broader topic-family variety than M1.3;
- at least one automatic narration is human/history/culture/site-oriented when relevant;
- do not force a fixed narration count or category quota;
- generic road/settlement explanations should not dominate the entire run.

### Real GPS

On the connected vivo phone:
- start an ordinary real-GPS journey;
- confirm best-effort human-readable area resolution works or record the exact platform limitation;
- main screen shows current area when available;
- failure to resolve an area never blocks GPS/Director/voice.

### Regression

Preserve:
- M1.1 ChatGPT OAuth/inference;
- M1.2 real vs simulated source isolation;
- M1.3 accelerated simulation cadence/pause/stale safety;
- wall-clock quiet timing;
- all existing D0–D11 behavior.

## Tests

Add focused tests for:

1. area identity normalization;
2. geocode refresh throttling;
3. area change detection;
4. area-card session caching;
5. topic-family history/dedup;
6. major-node candidate can outrank repeated generic mechanism;
7. Sanxingdui appears as candidate in Guanghan simulation chapter without hard-coded narration;
8. boundary change does not force speech;
9. unresolved area degrades safely;
10. real-GPS cadence remains unchanged;
11. M1.3 accelerated simulation remains green.

## Deliverable

- AreaResolver + Area Card implemented.
- District/county background + town/township/street chapter behavior implemented.
- Simulation fixture refined for chapter testing.
- Director editorial policy broadened.
- Human-readable current-place display implemented when resolver data exists.
- Real ChatGPT simulation demonstrates culture/history/site content variety.
- Debug/Release build + lint + all tests PASS.
- APK installed and phone acceptance recorded.
- Commit + push `main`.
- Update `HANDOFF.md`.
- Working tree clean.

Do not implement route screenshot upload, camera/image chat, full UI beautification, or M1.4 voice improvements in this milestone.
