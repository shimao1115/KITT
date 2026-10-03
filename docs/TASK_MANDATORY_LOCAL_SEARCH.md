# Task Card — Mandatory Local Search + Grounded Dossier

Status: **ACTIVE — content infrastructure**
Scope: search/enrichment + chapter trigger semantics
Base: current `main` after Editorial Freedom (`d201a38` or newer)

## Product decision

**Local search is mandatory for a new place chapter.**

The open “material shelf” is only an editorial menu. It is not enough to hand the Director empty category labels such as “非遗”“特产”“文保单位” and expect the model to know the local facts from memory.

When KITT enters a district/county/town/township/street chapter, it must actively research that place before relying on chapter-specific facts.

Core principle:

> **搜索负责找证据；素材包负责整理事实；Director 负责自由讲述。**

Do not turn search results into a fixed narration script.

## Why

Current behavior can still go silent because:
- generic AreaCard slots are only directions, not facts;
- current Director has no web-search tool;
- prompt correctly forbids inventing local details;
- many ordinary towns are not reliably covered by model memory.

The result is “有栏目，没内容”.

This task fixes the missing evidence layer.

---

## A. Mandatory chapter research

For every newly resolved chapter:
- city/prefecture if useful for disambiguation;
- district/county/county-level city;
- town/township/street when available;

start a **Local Dossier research job**.

Research should happen:
- once per unique chapter per active trip;
- asynchronously; GPS/Journey must not block;
- without repeated searches on every Director check;
- without route prediction.

If the same chapter is revisited in the same trip, reuse the cached dossier unless it was incomplete/failed and an explicit retry policy allows one bounded retry.

### Search completion creates an opportunity

A chapter must not be lost merely because it was entered during TTS, listening, image interaction or cooldown.

Maintain a small state such as:
- UNSEEN
- RESEARCHING
- READY_UNCHECKED
- CHECKED
- FAILED

When research reaches READY_UNCHECKED, create/retain one Director opportunity until it is actually checked under normal safety gates.

This is **not a narration queue**:
- do not queue generated audio/text;
- only retain that the newly researched chapter has not yet had its Director check;
- quiet/user interaction/stale position still win;
- if the vehicle has clearly left the chapter before research completes, do not narrate it late as if still present. Cache the dossier for later/revisit, but drop the stale delivery opportunity.

---

## B. What the search must investigate

The research prompt/query must actively look for useful, grounded material across these directions:

- 地方背景、历史沿革、地名/建置由来;
- 景区、古镇、老街、博物馆、遗址、寺庙、地标及其他值得介绍的地点;
- 人文地理、聚落与地方生活;
- 地区特产、饮食、手工艺、代表性物产;
- 物质文化遗产;
- 非物质文化遗产;
- **全国重点文物保护单位**;
- **省级文物保护单位**;
- 其他重要历史建筑/地方文保;
- 考古发现;
- 历史人物、地方人物、文学艺术人物;
- 可靠的趣闻、轶事、地方故事;
- 民俗、节庆、民间艺术、方言与地名故事;
- 农业、工业、商贸与当代地方特色;
- 桥梁、水利、铁路、隧道、古道等工程故事;
- 山脉、河流、湖泊、峡谷、垭口、盆地等自然地理;
- any other reliable local subject that is genuinely worth hearing.

This list is **research coverage**, not a playback checklist.

Do not require one result in every category. “未找到可靠资料” is better than invented completeness.

---

## C. Source quality

Prefer sources in roughly this order when applicable:

1. national/provincial official heritage/culture/tourism authorities;
2. State Council / national cultural-relics authority or official protected-site lists;
3. provincial/municipal/county government and culture/tourism bureaus;
4. official museums, memorials, universities, research institutions;
5. authoritative encyclopedic/institutional sources;
6. reputable media for anecdotes/current background when primary sources are unavailable.

For national/provincial protected cultural relics and official heritage status, prefer **official lists or government/institutional sources**, not travel blogs.

Do not treat:
- SEO travel pages;
- copied aggregation pages;
- user-generated posts;
- unsourced short-video captions

as sufficient proof for an official designation.

A lower-quality source may suggest a lead, but important factual claims should be corroborated before entering the dossier.

---

## D. Local Dossier contract

Create a compact, structured, session-local dossier per chapter.

Suggested shape (exact implementation may differ):

- area identity;
- research status / searched_at;
- orientation/background;
- facts/items:
  - neutral title/entity;
  - short fact summary;
  - optional family/tag;
  - salience;
  - confidence;
  - source title/domain/url;
  - source class (official / institutional / reputable media / other);
- notable protected heritage;
- notable intangible heritage;
- notable places/people/products/engineering/nature;
- research gaps / uncertain claims to avoid.

The dossier should hold **facts and evidence**, not narration prose and not a pre-written thesis.

Keep it compact enough to pass into the Director without swamping the live GPS context.

Do not persist raw search pages/history long-term.

---

## E. Search-provider architecture

Add a small search/research boundary separate from the normal Director request, e.g.:
- `LocalResearchProvider`;
- `ChapterResearch`;
- equivalent minimal abstraction.

Do not couple search logic directly into `Journey`.

### ChatGPT account path

The current ChatGPT direct Responses request does not include a search tool.

Probe whether the current ChatGPT-plan/OAuth direct route supports hosted web search. Do **not** assume Platform API support automatically means this account route supports it.

If supported:
- use a dedicated research request with web search enabled;
- require/force search for this research call rather than leaving it optional;
- preserve source URLs/citations into the Local Dossier;
- keep the normal Director structured-output path separate.

### OpenAI API path

Current official Responses API supports the hosted `web_search` tool. For mandatory research calls, use a required/specific web-search tool choice rather than an auto choice that may skip search.

Official reference:
- https://developers.openai.com/api/docs/guides/tools-web-search

### Compatible providers

Do not pretend generic OpenAI-compatible chat endpoints support web search.

Each provider/adapter must declare research capability.

If selected provider cannot search:
- prefer a separately configured search-capable research provider if one already exists/was explicitly configured;
- otherwise show an accurate diagnostic that local research is unavailable;
- do not silently fall back to model memory and call it “searched”.

Do not introduce scraping of Google/Baidu/Bing result pages as a hidden fallback.

---

## F. Search result → Director

After a dossier becomes READY:
- append a compact grounded dossier section to Context;
- trigger/retain one chapter Director opportunity;
- Director may choose any worthwhile angle;
- Director may connect several related facts;
- Director may still SILENT only when there is genuinely no worthwhile/relevant material or safety/state suppression applies.

For chapter-specific facts, the Director should prefer dossier evidence over ungrounded model memory.

Existing independent landmark proximity triggers remain.

A landmark already covered by the dossier should participate in normal dedup so KITT does not immediately repeat it.

---

## G. Trigger semantics fix

Current code consumes `chapterEntry` before checking cooldown/speaking/listening/image/quiet gates.

Change this behavior.

Required:
- entering a new chapter marks it pending/unresearched;
- research can proceed while appropriate;
- the Director opportunity is consumed only after an actual Director check for that current chapter;
- cooldown may delay the check but must not erase it;
- speaking/listening/image interaction may delay it but must not erase it;
- quiet mode may defer while still in the chapter; if the vehicle leaves before quiet ends, do not late-narrate the old chapter;
- stale position never produces late narration.

This is a pending **check**, not queued content.

---

## H. Search query strategy

Do not make 10–20 serial network calls per town.

Prefer:
- one research request that can issue several targeted searches;
- or a small bounded set of parallel/agentic queries.

Use the full place identity to avoid ambiguity, e.g.:
`四川省 绵阳市 安州区 雎水镇`.

Research should explicitly include official-designation terms where relevant:
- 全国重点文物保护单位
- 省级文物保护单位
- 非物质文化遗产
- 景区 / 遗址 / 博物馆 / 历史人物 / 特产 / 工程

Bound latency and cost. No retry storm.

---

## I. Search freshness and lifecycle

Most dossier facts are slow-changing, but:
- opening hours, closures, ticketing, road status and events are dynamic;
- do not narrate them as current unless the source is fresh enough and the claim is needed.

For V0:
- session cache is mandatory;
- a short-lived persistent cache is optional only if it stays simple and provenance-aware;
- raw search output is not permanent trip memory.

Trip end clears ephemeral research state unless an explicit cache design is implemented and tested.

---

## J. Tests

Add focused tests for:
- chapter entry starts research once;
- same chapter does not repeatedly search;
- full area identity is used for disambiguation;
- research READY creates a retained Director opportunity;
- cooldown does not erase a chapter opportunity;
- TTS/listening/image interaction does not erase it;
- leaving the chapter before research completion prevents late narration;
- search failure is not mislabeled as “no local content”;
- source URLs/provenance survive dossier compression;
- official protected-heritage claims require appropriate grounding;
- duplicate landmark/dossier topics do not immediately repeat;
- provider without search does not pretend to have searched;
- Director still receives strict JSON contract unchanged;
- all V0.2/V0.3 regressions remain green.

Run Debug/Release unit tests, builds and lint.

---

## K. Acceptance benchmark

Use at least:
- 广汉 / 三星堆;
- 雎水镇;
- several ordinary chapters on the existing Xindu → Jushui fixture.

For each researched chapter, acceptance evidence should show:
1. chapter identity;
2. actual search/research was invoked;
3. sources returned;
4. compact Local Dossier;
5. Director opportunity;
6. chosen topic or truthful SILENT reason.

For Sanxingdui, search should produce enough grounded material that the Director is not limited to one shallow sentence or one pre-written thesis.

For ordinary towns, demonstrate that the search finds concrete local material where reliable material exists.

---

## Non-goals

Do not add:
- a backend/server;
- vector DB/RAG;
- route prediction/navigation;
- map scraping;
- a giant permanent local knowledge base;
- pre-written narration scripts;
- mandatory category rotation;
- long-term raw browsing history.

Chinese ASR work is separate and may proceed independently.

---

## Deliverable

- mandatory Local Dossier research pipeline;
- search capability probing for ChatGPT-plan direct route;
- search-capable provider boundary;
- source-preserving dossier;
- fixed chapter-opportunity lifecycle;
- deterministic tests plus real-search evidence where credentials/account access permit;
- updated HANDOFF.md;
- latest APK;
- milestone commit(s), push main;
- clean tracked working tree.

If the ChatGPT-plan direct route rejects hosted web search, document the exact response/limitation and stop short of pretending success. Implement the architecture so a search-capable provider can be used once available, and report the smallest remaining decision needed.
