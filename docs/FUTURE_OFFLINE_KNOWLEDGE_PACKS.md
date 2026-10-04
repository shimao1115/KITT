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


## Runtime distribution principle — NAS is not a trip-time dependency

The preferred product shape is **download-to-phone**, analogous to offline map packages.

NAS is the builder / publisher / update source, not the runtime database that the phone must query on every trip.

Desired flow:

> NAS builds / refreshes province or route packs
> → phone downloads the selected pack before travel
> → phone reads local SQLite / FTS data directly during the trip
> → live network is used only for missing or time-sensitive information.

This avoids:
- NAS round-trip latency;
- home-network/VPN dependency while driving;
- failures when NAS is asleep or unreachable;
- extra mobile-data traffic for stable knowledge;
- repeated hosted-search cost for content already curated.

The phone should be able to use a downloaded pack with no NAS connection at all.

### Package UX

Long-term UX should resemble offline maps:

> 离线地方资料
> - 四川省 · 已下载 · 版本 2026.10
> - 湖北省 · 未下载
> - 阿坝州路线包 · 有更新

User can:
- download a full province;
- download only a route / selected prefectures/counties;
- update an existing pack;
- delete a pack to reclaim storage.

### Package manifest

Each pack should have a small manifest with:
- package id;
- region / administrative scope;
- version;
- schema version;
- created_at / updated_at;
- byte size;
- record counts;
- checksum;
- minimum app version;
- optional delta/update metadata.

Phone verifies checksum before activating the new pack and keeps the previous known-good version until replacement succeeds.

### Storage expectation

Because the core material is structured text + source metadata, not map tiles, imagery, audio, or full mirrored webpages, package size should usually be modest relative to offline maps.

Do not optimize prematurely. Measure a real Sichuan pilot first. If needed:
- use SQLite page compression / compact schemas;
- deduplicate URLs, aliases, source titles and repeated strings;
- compress package transport;
- keep only concise evidence snippets rather than raw pages.


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


## Design priority — schema first, crawler second

The hardest and highest-leverage part of this project is **not crawling**. Crawling can be replaced, retried, parallelized, or delegated to low-cost models.

The durable core is the **knowledge-pack contract**: what a place/topic record looks like, which fields are required, how provenance is represented, how uncertainty/conflicts are stored, how updates work, and how the runtime AI can consume the data with very little prompt/context overhead.

Before building a large crawler, design and validate the pack schema against real examples.

### Schema design goals

The schema should be:

- **AI-friendly**: easy for the Director to read and reason over without a large adapter prompt;
- **human-auditable**: facts and source provenance are inspectable;
- **compact**: optimized for local phone storage and fast lookup;
- **stable**: supports future models without rewriting the database each time;
- **updateable**: individual facts/topics/sources can be refreshed without rebuilding a whole province;
- **uncertainty-aware**: conflicting or weak evidence is represented explicitly rather than flattened into false certainty;
- **location-aware**: facts distinguish administrative association from precise physical proximity;
- **time-aware**: stable historical facts are separated from time-sensitive state;
- **narration-neutral**: store evidence/material, not prewritten scripts.

### Fields to prototype first

Do not freeze these names yet; validate them on real records first.

Candidate core entities:

**Area**
- area_id / administrative_code
- parent_area_id
- level
- canonical_name
- aliases
- centroid / optional coarse geometry
- overview_id
- pack_version

**Overview**
- area_id
- orientation
- highlights[]
- source_refs[]
- updated_at
- confidence / coverage notes

**Topic**
- topic_id
- area_id(s)
- canonical_name
- aliases
- topic_family
- salience
- why_it_matters
- spatial_scope / location relation
- facts[]
- source_refs[]
- uncertainty_notes
- updated_at

**Fact**
- fact_id
- statement / neutral summary
- source_refs[]
- confidence
- date_scope / temporal validity
- official_designation (if verified)
- geographic_scope
- conflict_group / supersedes (optional)
- verified_at

**Source**
- source_id
- url
- title
- publisher / organization
- source_class
- retrieved_at
- published_at (if known)
- fingerprint / version hint
- authority flags

The phone runtime should be able to fetch an Area Overview plus a small number of relevant Topic records in one fast local query.

### First design exercise

When this work begins, do **not** start with a province-wide crawl.

First hand-design or AI-generate 20–50 representative records from diverse Sichuan examples:
- historical person;
- temple;
- garden;
- archaeological site;
- intangible heritage craft;
- food/specialty;
- mountain/river;
- railway/bridge/dam;
- industrial/agricultural topic;
- ordinary township with sparse sources;
- official heritage designation;
- disputed/uncertain historical claim.

Then test:
1. Can the same schema represent all of them cleanly?
2. Can Director answer “what is this / why interesting / tell me more” from it?
3. Can the same data support Overview and Topic without duplication?
4. Can one fact be updated without rewriting unrelated records?
5. Can the runtime distinguish “in this district” from “near the car”?
6. Can provenance be traced without shipping raw webpages?
7. Is the prompt/context payload small enough for low latency?

Only after this contract is stable should the NAS crawler/production pipeline scale up.
