# Task Card — Editorial Freedom / Prompt Reset

Status: **NEXT CONTENT FIX**
Scope: Director prompt + Area/Landmark candidate semantics
Chinese ASR: out of scope

## Why

Current KITT content is technically able to discover areas/landmarks, but the editorial prompt is over-constrained.

Examples of the current problem:
- candidate titles such as **“三星堆与古蜀文化：遗物怎样改变对这片土地的理解”** already prescribe a thesis;
- the constitution requires **“眼前切入 → 一个问题 → 解释一层 → 落回眼前 → 停”**;
- many topics are implicitly forced back into “这片土地说明了什么 / 塑造了什么 / 改变了什么理解”.

This makes KITT sound like a命题作文 or standardized documentary template instead of a knowledgeable, spontaneous co-pilot.

## Product principle

> **给 AI 素材、边界和当前现场，不给它作文题。**

Content layer decides:
- what entities / facts / local associations are worth knowing;
- what is grounded;
- what must not be invented.

Director decides:
- whether to speak;
- which angle is interesting now;
- how to structure the narration;
- how deep to go;
- whether to tell a story, explain a mechanism, compare, describe, connect history, or simply point something out.

## Required changes

### 1. Rewrite Director Constitution

Remove or soften rules that prescribe a single rhetorical structure.

In particular, do not require every narration to:
- start from the visible scene;
- pose one question;
- explain one layer;
- return to “the land”;
- end with a takeaway;
- produce a “改变对这片土地的理解” thesis.

Replace with broad editorial goals:
- be interesting, accurate, relevant to the current journey;
- speak when there is something worth hearing;
- history, culture, people, artifacts, archaeology, architecture, engineering, geography, food, industry, customs, place names, stories and contemporary life are all first-class; protected cultural relics, tangible/intangible heritage, local specialties, scenic places, folk arts, literature, religion, anecdotes and any other grounded worthwhile material are equally eligible;
- use the form that best fits the material;
- avoid repetition and generic filler;
- do not force a moral, lesson, summary, or grand conclusion.

Keep hard safety/quality boundaries:
- user intent wins;
- quiet/skip/stale/foreground rules;
- no fake visibility (“你眼前就是…” unless grounded);
- exact figures/dates/current status require grounding;
- no fabricated local facts;
- no repeated narration of the same topic;
- no narration queue.

### 2. Candidate semantics: facts/topics, not thesis prompts

Change AreaCard / Landmark candidate titles from pre-written essay questions into neutral topic/entity labels.

Bad:
- “三星堆与古蜀文化：遗物怎样改变对这片土地的理解”
- “地形水系怎样塑造土地使用”

Better:
- “三星堆 / 古蜀文明”
- “三星堆代表性器物与考古发现”
- “广汉与成都平原的古代文明背景”
- “本地水系与聚落”
- “地方道路与交通”
- “当地产业与生活”

A candidate may include:
- entity/topic;
- family;
- salience;
- compact grounded fact hints;
- source/grounding notes.

It should **not** prescribe the narration’s thesis or rhetorical question.



### 2A. Area arrival should open a **local dossier**, not a narrow thesis

When KITT enters a district/county/town/township/street chapter, the content layer should expose a broad **local dossier / material shelf**. It is not a checklist that must be read out, and it is not a pre-written essay outline.

Useful material may include, when grounded and relevant:

- place background / local character / historical depth;
- scenic areas, notable places, museums, ruins, temples, old streets, historic towns and local landmarks;
- human geography and settlement patterns;
- historical origins and major periods of change;
- local specialties, foods, crafts, products and everyday material culture;
- tangible cultural heritage;
- intangible cultural heritage;
- nationally protected major cultural relic sites;
- provincial-level protected cultural relic sites;
- other locally important protected sites or historic buildings;
- customs, festivals, folk arts, dialect/place-name stories and local traditions;
- archaeology and notable discoveries;
- historical figures and people strongly connected with the place;
- anecdotes, local stories, curious facts and memorable episodes when reliable;
- literature, art, religion and public culture;
- agriculture, industry, trade and contemporary livelihoods;
- bridges, dams, tunnels, railways, canals and other engineering stories;
- mountains, rivers, lakes, passes and other physical geography;
- any other genuinely interesting, reliable angle the model discovers.

This list is deliberately **open-ended**. Do not reject a worthwhile topic just because it does not fit one of the named families.

The purpose of the area chapter is simply:
> “We have entered a new place. Here is a shelf of reliable things worth knowing.”

The Director remains free to choose one thread, several connected threads, or silence.

Do not require:
- one item from every category;
- a fixed order such as history → culture → geography → specialty;
- a standard “background introduction” paragraph;
- a concluding thesis about land, identity, civilization, or modernization.


### 3. Allow multiple narrative forms

Director may choose, depending on material:
- short factual introduction;
- historical story;
- archaeological discovery story;
- person-centered anecdote;
- object-centered explanation;
- timeline;
- comparison;
- “why it looks like this” mechanism;
- place-name story;
- engineering explanation;
- cultural custom;
- food/industry/everyday-life observation;
- a concise “you’re passing X, here’s the one thing worth knowing” note;
- a longer documentary-style passage for major nodes.

No single structure is mandatory.

### 4. Major nodes can be deeper

Important nodes such as Sanxingdui should not be artificially compressed to one shallow paragraph.

For major high-salience topics:
- allow a fuller automatic narration when context permits;
- “再讲一点” should explore a new angle rather than repeat;
- do not set a rigid word/time quota in code;
- do not force every topic to be long.

### 5. Sanxingdui as benchmark

Sanxingdui should be used to test **editorial freedom**, not just discovery.

Acceptance:
- the candidate layer exposes neutral grounded material, not a pre-written thesis;
- Director can choose different valid angles across runs;
- examples could include archaeology, bronze imagery, ancient Shu, excavation/discovery, museum/public culture, relation to Chengdu Plain, or unanswered questions;
- no requirement to mention all angles;
- no requirement to conclude with “对这片土地的理解”.

### 6. Fully open the editorial field

The topic-family enum is an implementation aid for weak deduplication, **not the ontology of what KITT is allowed to talk about**.

If the model finds a grounded worthwhile subject that does not map neatly to a current family, do not suppress it merely because the taxonomy is incomplete. Prefer graceful mapping or an expandable representation over editorial exclusion.

KITT is not a curriculum and not an exam answer. It should feel like travelling with someone widely read, locally curious and able to notice what is interesting.

### 7. Keep diversity without mechanical category rotation

Recent topic-family history remains useful as a weak anti-repetition signal.

It must not become:
- “last was geography, next must be history”;
- a category quota;
- a scripted playlist.

### 8. Preserve AI-native principle

The prompt should state destination and boundaries, not micromanage prose.

Desired spirit:
> 你是坐在副驾驶位置、对沿途世界很有见识的人。  
> 当此刻有值得说的东西，就用最合适的方式讲给同行的人听。  
> 不必每次提问，不必每次升华，不必每次总结，也不必把一切都解释成“土地如何塑造人”。  
> 准确、具体、有趣，比统一格式重要。

This is guidance, not required verbatim wording.

## Tests / acceptance

Add/update tests so they verify constraints, not exact prose templates.

Must verify:
- entering a chapter can expose broad local-dossier material such as history, places, specialties, heritage/protected relics, people, stories and engineering without forcing a scripted order;
- candidate titles are neutral topic/entity labels for benchmark items;
- no constitution text requires the fixed “一个问题→解释一层→落回眼前” template;
- no constitution/candidate requires “改变对这片土地的理解” or equivalent thesis;
- existing strict JSON contract remains;
- user intent/quiet/stale/dedup safeguards remain;
- Sanxingdui remains discoverable/high-salience;
- M1.1–V0.2 regressions remain green.

## Deliverable

- revised Director Constitution;
- neutralized AreaCard/Landmark candidate wording;
- updated tests;
- Debug/Release tests/build/lint PASS;
- short before/after examples in HANDOFF or acceptance doc;
- commit and push main;
- clean working tree.

Do not add new backend/search/ASR architecture in this task.
