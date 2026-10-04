# Priority Future Task — Offline Regional Knowledge Packs

Status: **PRIORITY BACKLOG — do not implement during the current field-use window**

Priority note:
This should be treated as one of the **highest-value next product investments** because it directly improves:
- startup/content latency;
- network resilience;
- research cost;
- content cleanliness and depth;
- repeatability of narration quality.

## Product idea

Build an “offline knowledge map” for 沿途, analogous to offline map packages.

NAS / home infrastructure acts as the slow knowledge-production side.
The phone consumes compact, structured, pre-cleaned regional knowledge packs.

Core principle:

> NAS 慢慢生产，手机快速读取；稳定知识本地优先，时效信息实时联网。

## Why this matters

Current live research proves useful but can be slow:
- Overview can take tens of seconds;
- Topic deep dives can take around a minute or more;
- mobile DNS / socket / SSE transport may fail intermittently.

For stable local knowledge, this work does not need to happen while driving.

Precomputing it off-device can turn:
- 40–90 second live research
into:
- local database lookup in milliseconds,
while preserving source provenance.

## Architecture

### 1. Knowledge production side — NAS

NAS runs a slow background pipeline:

> discover → fetch/read → extract → deduplicate → reconcile → verify → structure → QA → publish pack

Low-cost models should do most routine work:
- entity discovery;
- fact extraction;
- category tagging;
- duplicate merging;
- source classification;
- simple conflict detection;
- overview/topic organization.

More capable models are reserved for:
- ambiguous historical claims;
- conflicting sources;
- cultural heritage designation verification;
- difficult provenance reconciliation;
- quality sampling and final review.

Time can be exchanged for cost. Batch jobs may take minutes per place; realtime speed is not required.

### 2. Data hierarchy

Suggested hierarchy:

> Province
> → Prefecture / city
> → District / county
> → Township / street
> → Overview
> → Topic Dossiers
> → Sources / provenance

Use stable administrative identifiers where available, not display names alone.

### 3. Overview package

Each administrative area may contain:
- short orientation;
- 4–10 important concrete objects;
- salience;
- topic family;
- why_it_matters;
- source references;
- update metadata.

Overview is an index for “what is worth knowing here”, not a prewritten tour script.

### 4. Topic packages

Each high-value object/topic contains:
- normalized title / aliases;
- entity/place relationship;
- structured facts;
- source references;
- confidence;
- official designations only when verified;
- relevant dates;
- update timestamp;
- optional uncertainty/conflict notes.

Examples:
- 杨慎 / 杨升庵
- 桂湖
- 宝光寺
- 新繁东湖
- a local craft;
- a historic road;
- a dam/bridge/railway;
- a geographic feature.

Store evidence and clean facts, not fixed narration.

Director remains free to decide what to say and how to say it.

## Stable vs realtime information

### Suitable for offline packs

Stable or slow-changing knowledge:
- local history;
- historical people;
- archaeology;
- museums and cultural institutions background;
- temples;
- historic settlements;
- cultural relics and official heritage designations;
- intangible cultural heritage;
- geography and landforms;
- rivers/lakes background;
- place-name origins;
- crafts;
- food/specialties background;
- agriculture / industry history;
- infrastructure history;
- local customs and cultural context.

### Keep realtime

Do not rely on cached knowledge packs for fast-changing state:
- today’s opening hours;
- live ticket availability;
- current road closures;
- live traffic;
- weather;
- current events;
- recent news;
- temporary exhibitions;
- one-off activities;
- emergency conditions.

These still use online search when needed.

## Runtime lookup strategy

When the phone resolves current administrative area:

1. check local pack first;
2. load local Overview immediately;
3. load available Topic Dossiers on demand;
4. only use live hosted search if:
   - local pack is missing;
   - local evidence is too thin;
   - user asks for current information;
   - user explicitly asks to search;
   - a high-value object needs deeper fresh research.

This should reuse the current Overview → Topic semantics rather than replace them.

Desired future runtime:

> GPS / area
> → local Overview (milliseconds)
> → Director can speak
> → local Topic on demand
> → web search only for gaps/freshness

## Phone package format

Start simple.

Prefer:
- SQLite;
- compact JSON blobs;
- FTS where helpful;
- straightforward keyed lookup by area + topic.

Do **not** start with:
- vector DB;
- embeddings;
- RAG framework;
- knowledge graph;
- multi-agent runtime.

Add those only if simple structured lookup proves insufficient.

## Pack distribution

Long-term UX may resemble offline maps:

> 离线地方资料
> - 四川省 已下载
> - 湖北省 已下载
> - 云南省 下载

Support:
- versioned province packs;
- incremental updates;
- partial/route packs;
- pre-trip download;
- local storage cleanup.

Avoid forcing full re-download for small changes.

## Update model

Knowledge production should be incremental.

Each source/object can retain:
- source URL;
- title;
- retrieved_at;
- source class;
- content fingerprint / version hint;
- verification timestamp;
- confidence;
- dependent facts/topics.

Update rules may include:
- unchanged source → skip;
- changed source → re-extract/reconcile;
- new official list → re-check affected designations;
- new entity discovery → add;
- conflicting evidence → queue for review;
- stale low-confidence fact → refresh or drop.

Historical/stable topics can refresh infrequently.
Administrative / institutional content may refresh more often.
Realtime data does not belong in the pack.

## Storage and copyright discipline

Do not build a giant mirrored web archive.

Prefer storing:
- structured facts;
- source metadata;
- provenance URLs;
- timestamps;
- concise evidence snippets where appropriate;
- normalized entity relationships.

Avoid retaining full raw pages unless genuinely needed for internal processing and legally appropriate.

## First implementation scope

Do **not** begin with all of China.

Recommended first experiment:
- a real frequently traveled corridor, such as Chengdu → Wenchuan → Lixian → Maerkang → Jinchuan/Xiaojin;
or
- a small Sichuan pilot covering selected districts/counties and townships.

Goal:
- validate hit rate;
- validate pack size;
- compare latency vs live hosted search;
- compare narration quality;
- learn what granularity is actually worth storing;
- confirm update workflow.

Only after this pilot works should the NAS expand gradually across Sichuan.

## Product acceptance

A successful pilot should demonstrate:
- local Overview lookup in near-instant time;
- materially fewer live searches during a trip;
- consistent sourced content;
- better content depth/cleanliness than rushed live search;
- phone can keep narrating stable local knowledge during poor network periods;
- current information still routes to live search;
- no loss of provenance;
- no prewritten fixed-tour behavior;
- straightforward pack update workflow.

## Priority

This is not merely a storage optimization.

It is intended to improve the core experience:
- **faster**
- **cheaper**
- **more reliable**
- **cleaner**
- **more detailed**

Treat this as a higher priority than cosmetic polish and most optional future features once the current field-use version has been observed in real travel.
