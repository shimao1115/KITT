# Task Card — 沿途 V0.3：品牌重命名 + 搜索驱动内容 + 触发增强

Status: **ACTIVE — Chinese ASR recovery is complete; execute from current `main`**
Source of truth after ASR: this file
Absorbs: `docs/TASK_MANDATORY_LOCAL_SEARCH.md` + the latest product decisions in Issue #10
Internal codename: KITT may remain

## Product decision

The user-facing product name is now:

# **沿途**

Slogan:

> **读懂沿途的世界**

Product definition:

> **一个会读懂你正在经过的地方的 AI 副驾驶。**

The old name **“路上读山河”** is no longer the product name. It is too narrow because mountains/rivers are now only one part of the content.

Chinese ASR recovery is now complete on `main`. Inherit the latest state and implement this batch as one coherent update.

---

# 1. Brand rename: 路上读山河 → 沿途

Rename user-facing product strings to **沿途**.

Cover at least:
- Android app label / launcher name;
- home/driving UI title;
- settings/about copy;
- foreground notification;
- TTS preview sentence;
- user-facing error/help text where the old name appears;
- documentation current-state/product-positioning text;
- Director persona wording where it names the product.

Recommended user-facing wording:
- **沿途**
- **读懂沿途的世界**
- **一个会读懂沿途世界的 AI 副驾驶**

Keep internal package/class/repository codename **KITT** unless changing it is actually necessary. Do not create risk by renaming package IDs or internal architecture for branding alone.

The historical phrase “路上读山河” may remain inside archived/historical acceptance documents when it is clearly historical evidence.

---

# 2. Core content philosophy

Along the journey, KITT should not merely “read mountains and rivers”.

It should help a first-time visitor understand the **whole local world**:
- place background / historical origins;
- scenic areas and notable places;
- human geography;
- local history and major change;
- specialties, food, crafts and representative products;
- tangible cultural heritage;
- intangible cultural heritage;
- nationally protected major cultural relic sites;
- provincial-level protected cultural relic sites;
- important local historic buildings/sites;
- museums, ruins, archaeology, temples and old towns;
- historical/local/literary/artistic figures;
- reliable anecdotes, curiosities and local stories;
- customs, festivals, folk arts, dialect/place-name stories;
- agriculture, industry, trade and contemporary livelihoods;
- bridges, railways, dams, tunnels, canals, ancient roads and other engineering;
- mountains, peaks, rivers, lakes, reservoirs, passes, canyons, basins and other physical geography;
- any other grounded subject that is genuinely worth hearing.

This list is **open-ended research coverage**, not an editorial whitelist and not a playback checklist.

---

# 3. Mandatory local search

For every newly resolved local chapter, **search is mandatory** before relying on chapter-specific facts.

Core chain:

> **搜索找证据 → Local Dossier 整理事实 → Director 自由讲述**

Generic category labels such as “非遗 / 文保 / 特产 / 名人” are not enough.

The research layer must actually find what exists in this specific place.

Use the full place identity for disambiguation, e.g.:

> 四川省 / 绵阳市 / 安州区 / 雎水镇

Prefer authoritative sources:
1. national/provincial official cultural-heritage/culture/tourism authorities;
2. State Council / national cultural-relics authority / official protected-site lists;
3. provincial/municipal/county governments and culture/tourism bureaus;
4. official museums, memorials, universities, research institutions;
5. strong institutional/encyclopedic sources;
6. reputable media for background/anecdotes when primary sources are unavailable.

Official designations such as **全国重点文物保护单位、省级文物保护单位、非遗项目** must not be inferred from travel blogs or unsourced aggregators.

The search result becomes a compact, source-preserving **Local Dossier** for the active trip.

Do not store raw pages/history long-term.

Use `docs/TASK_MANDATORY_LOCAL_SEARCH.md` as the detailed search-provider/provenance sub-spec.

---

# 4. Search-provider requirement

Current Director requests do not have web search.

Implement a small search/research boundary separate from normal narration.

Probe the current ChatGPT-plan OAuth/direct route rather than assuming it supports hosted search.

If supported:
- use a dedicated search-capable research request;
- force/require search for Local Dossier research;
- preserve source URLs/provenance.

If that route does not support hosted search:
- document the exact limitation;
- use another explicitly configured search-capable research path only if available;
- do not pretend model memory is search;
- do not silently scrape public search-engine result pages as a hidden fallback.

Normal Director narration remains separate from research.

---


# 4A. User-initiated search branch

Mandatory chapter research is not the only place where search may be needed.

When the user explicitly asks a question during the journey, the Director must be able to obtain fresh/search-grounded information when the existing Context + Local Dossier are not sufficient.

Examples:
- “附近还有什么值得去的地方？”
- “这个寺庙今天开放吗？”
- “前面这个地方有什么来历？”
- “附近今天有什么活动？”
- “帮我查一下这附近有什么博物馆。”
- “这里最近有什么新闻？”
- “这条路为什么堵？”
- “刚才你说的那个人再查详细一点。”

Required behavior:

- If the answer is already well-supported by the current Local Dossier/context, answer directly without redundant search.
- If the question requires **current/fresh information** (opening status, events, traffic, recent news, closures, current conditions, etc.), search is required.
- If the user explicitly says “查一下 / 搜一下 / 帮我找”, search is required unless the request is impossible/unavailable.
- If the question asks for a local fact that is missing or weakly grounded in the dossier, perform an on-demand research/search call before answering.
- Stable general knowledge that does not need freshness may be answered without search when confidence is high.
- Preserve source/provenance for searched facts and do not claim “查到” when no search actually ran.

This should reuse the same search/research capability boundary rather than creating a second unrelated web stack.

The user-initiated search is a **temporary branch from the journey**, not a new journey mode:
- current GPS/chapter remains available as context;
- the answer returns through the normal Director/TTS path;
- after answering, KITT returns to the ongoing journey;
- do not replace or reset the current chapter Local Dossier unless the new search produces useful local facts worth merging;
- do not queue unrelated autonomous narration behind the user answer.

For “nearby” queries, use the current location/area as grounding, but do not pretend precise visibility or travel time without an appropriate data source.


# 5. Every town/township/street is a chapter opportunity

Spatial model:

> **区县提供背景；镇 / 乡 / 街道作为基本章节；GPS 推动章节切换。**

Every newly entered **town / township / street** should create a real Director opportunity.

If the Local Dossier contains at least one worthwhile, grounded subject, KITT should **usually speak**.

SILENT is appropriate when:
- research truly found no worthwhile reliable material;
- the worthwhile material was just covered;
- the user is speaking/interacting;
- quiet mode is active;
- the position is stale;
- delivery would otherwise be unsafe/incorrect.

Do not turn chapter entry into a fixed “XX镇简介”.

The Director chooses the most interesting local thread.

---

# 6. Fix lost chapter opportunities

Current V0.3 behavior consumes `chapterEntry` before checking cooldown/speaking/listening/image/quiet gates.

This can make an entire town disappear if the vehicle crosses the boundary while KITT is speaking or cooling down.

Change the lifecycle:

- entering a new chapter records a pending chapter/research state;
- research starts once;
- after research is READY, retain **READY_UNCHECKED** until an actual Director check occurs while still in that chapter;
- TTS/listening/image interaction/cooldown may **delay**, but must not erase, that check;
- quiet may defer it while still in the chapter;
- if the vehicle clearly leaves before the check/research completes, cache the dossier but drop the stale delivery opportunity;
- never queue generated narration.

This is a queue of **unexamined context state**, not a narration queue.

---

# 7. Chapter detection must be responsive enough for driving

Inspect the current real-GPS AreaResolver behavior.

At the time this task was written:
- reverse geocode attempts were at least ~60 s apart;
- small movement could reuse lookup state for up to several minutes;
- resolved administrative area could be reused within ~3 km.

That can miss short towns/streets at road speed.

Improve chapter resolution for real driving without creating a network storm.

Requirements:
- speed/distance/time-aware refresh is acceptable;
- do not blindly retain a previous town label across a likely chapter change;
- avoid one request per GPS fix;
- preserve GPS as authoritative and reverse-geocode failure as non-blocking;
- add diagnostics/tests showing chapter transitions are not systematically swallowed at 80–100 km/h.

Do not build a navigation engine merely to solve administrative detection.

---

# 8. Landmark/geographic triggers must also be harder to miss

Important mountains/ranges, peaks, rivers/crossings, lakes/reservoirs, distinctive landforms, major bridges/dams/tunnels, landmark buildings, museums/ruins/heritage sites remain independent triggers.

Review the current cadence rule that can require ~45 s since the previous Director check.

High-salience landmark entry should not be silently lost simply because it occurs during ordinary cooldown.

Use a retained live opportunity while the vehicle remains in the relevant approach zone.

Still preserve:
- user/quiet priority;
- stale-position protection;
- dedup;
- no late narration after clearly leaving;
- no forced narration of every minor POI.

---

# 9. Audience model: assume a first-time outsider

KITT should assume the listener is:

> **第一次来到这里、对当地历史文化几乎没有背景知识的外地人。**

Narration must stand on its own.

Do not assume the listener knows:
- who a local historical figure is;
- what “古蜀” means;
- why a site is famous;
- what a specific non-heritage craft/custom actually is;
- what a protected-cultural-relic designation means;
- how local places/people/events are related.

When using unfamiliar proper nouns or concepts, give enough plain-language context for a newcomer.

Product acceptance sentence:

> **第一次来、第一次听，也能听明白；听完还能记住一点东西。**

---

# 10. Narration should be detailed, concrete and self-contained

Current real experience is too terse.

Do not compress a substantial local subject into one or two thin sentences merely to be concise.

The Director should give enough context to answer likely outsider questions:
- 这是什么？
- 为什么有名？
- 有什么特别？
- 发生过什么？
- 为什么值得记住？
- 它和这个地方有什么关系？

These are **coverage goals**, not a mandatory prose template.

No fixed word count/minute quota in code.

Editorial expectation:
- a normal worthwhile local topic gets a reasonably complete explanation;
- important nodes can run for multiple minutes;
- major nodes such as Sanxingdui, major mountain/river systems, nationally important heritage or major engineering may be substantially deeper when material supports it;
- “再讲一点” should add a new layer/angle, not repeat.

---

# 11. Preserve editorial freedom

Do not reintroduce:
- “眼前切入 → 一个问题 → 解释一层 → 落回眼前 → 停” as a mandatory pattern;
- “改变对这片土地的理解” as a required thesis;
- category rotation;
- one-topic-per-category quotas;
- pre-written narration scripts.

Core principle remains:

> **给 AI 素材、证据、边界和现场，不给它作文题。**

The Director chooses:
- whether to speak;
- subject;
- angle;
- structure;
- depth;
- narrative form.

---

# 12. Sanxingdui benchmark

Sanxingdui remains a major acceptance benchmark.

The finished system should:
- actively search it when the relevant chapter is entered;
- build a source-grounded dossier with multiple useful angles;
- be able to explain it to someone who has never heard of ancient Shu;
- not be limited to “三星堆改变了我们对这片土地的理解”;
- not be limited to a one-paragraph summary;
- support follow-up “再讲一点” with genuinely new information.

Possible angles may include archaeology, representative objects, discovery/excavation history, ancient Shu, Chengdu Plain context, museum/public culture, unresolved questions, etc.

No requirement to cover all angles in one narration.

---


# 12A. Black-box Xindu discovery benchmark

**New acceptance benchmark: Chengdu Xindu District.**

This benchmark is intentionally **black-box**:

- do **not** seed the runtime prompt/query with the expected answers;
- do **not** hard-code these names into production AreaCard/fixtures/search prompts merely to pass the test;
- do **not** add special Xindu-only training or heuristics;
- the normal mandatory local-research pipeline should discover them from the place identity and open research coverage alone.

Given only the ordinary place context **四川省 / 成都市 / 新都区** (plus live town/street when available), a competent Local Dossier should independently discover at least these three high-salience Xindu subjects:

1. **杨升庵（杨慎）** — who he was and why he is strongly associated with Xindu;
2. **桂湖 / 新都桂湖（杨升庵博物馆相关）** — what the place is and its historical/cultural connection;
3. **宝光寺** — what it is and why it is a major historical/cultural landmark of Xindu.
4. **新繁东湖（东湖）** — identify it as a notable Xindu/Xinfan scenic-cultural site and explain why it is locally worth knowing.

These are **acceptance expectations, not injected hints**.

The research layer should also be free to discover other important Xindu subjects beyond these three.

### Pass condition

For a fresh black-box Xindu research run:
- actual web/local research is invoked;
- sources are preserved;
- all four subjects above appear in the resulting dossier with enough context for a first-time outsider;
- the Director has real opportunities to narrate them during the Xindu portion of the journey, subject only to user/quiet/stale/safety rules;
- at least the district-level content plan is rich enough that Xindu is not reduced to a generic “成都北部城区” introduction.

A test that passes because the expected names were inserted into the production prompt, fixture, candidate list or training examples **does not count**.


# 13. Acceptance on the existing route

Use the existing Xindu → Jushui fixture plus real-phone testing when available. Run the Xindu benchmark as a fresh black-box research case without seeding 杨升庵、桂湖、宝光寺、新繁东湖 into the production query/prompt.

For each chapter, evidence should make it possible to inspect:
1. resolved chapter identity;
2. whether Local Dossier research started;
3. research sources;
4. research status;
5. whether a Director opportunity was retained;
6. whether it was checked;
7. SPEAK_NOW/SILENT and why;
8. whether narration used grounded dossier facts.

A long drive through many towns should not pass most of them in silence when reliable local material exists.

Also verify:
- cooldown no longer erases a town;
- TTS/listening/image interaction no longer erases a town;
- stale/left-behind chapters are not narrated late;
- landmark opportunities survive ordinary cadence while still relevant;
- no retry/search storm;
- no duplicate search for the same chapter in one trip.

---

# 14. Regression boundaries

Preserve:
- Chinese ASR result from the preceding task;
- ChatGPT OAuth/auth lifecycle;
- Provider separation;
- route image / RouteHint;
- Visual Talk;
- real GPS vs simulation isolation;
- wall-clock quiet semantics;
- stale response protection;
- strict Director JSON contract;
- no narration queue;
- no long-term raw GPS/audio/photo/search-history storage.

---

# 15. Deliverable

With `TASK_CHINESE_ASR_RECOVERY` complete:

- rebase/inherit latest `main`;
- implement this batch autonomously;
- update all current user-facing branding to **沿途**;
- implement mandatory Local Dossier search;
- fix chapter opportunity lifecycle;
- improve real-driving chapter resolution;
- strengthen high-salience landmark opportunity retention;
- update outsider-friendly narration-depth prompt/contract;
- add/adjust tests;
- run Debug/Release tests/build/lint;
- update `HANDOFF.md`;
- produce latest APK;
- commit/push `main`;
- leave tracked working tree clean.

The ASR prerequisite is satisfied. This is now the next active Destination.
