# Editorial Freedom / Prompt Reset — batch evidence

Date: 2026-10-03 (Asia/Shanghai). Task card: `docs/TASK_EDITORIAL_FREEDOM.md`.
Chinese ASR and every phone-only gate are out of scope for this batch and stay deferred.

## What changed

| Layer | Before | After |
| --- | --- | --- |
| Director constitution | `只选此刻最值得理解的一件事：眼前切入→一个问题→解释一层→落回眼前→停。` | `当此刻有值得听的东西，就用最合适的方式讲给同行的人；不必每次提问，不必每次升华，不必每次总结…` 形式按素材选择，无强制模板 |
| Director constitution | `道路只是一个视角，不是整段旅程的主角。` ＋ 点名“三星堆通常胜过重复的道路机制” | 开放类别清单（历史／考古／文物／博物馆／名胜／信仰／人物／传说／非遗／民俗／地名／饮食／产业／工程／当代生活…），`OTHER` 兜底，不规定谁胜过谁 |
| Director constitution | `进入镇乡街道只刷新背景，仍可 SILENT` | `每进入一个新的镇乡街道都值得检查一次…有依据充足且值得听的材料时倾向于开口`，SILENT 仍是合法选择 |
| Candidate wording | `三星堆与古蜀文化：遗物怎样改变对这片土地的理解` | `三星堆 / 古蜀文明`；另有 `三星堆代表性器物与考古发现`、`广汉与成都平原的古代文明背景` |
| Candidate wording | `地形水系怎样塑造土地使用`、`老街古镇与聚落留下的空间痕迹` | `本地水系、山川与地貌`、`古镇、老街与传统聚落` |
| Candidate set | 11 个视角，多数标题自带论点 | 18 条通用中性素材，已核实区县另加 2–3 条带来源主题（广汉最多 21 条），含 `各级文物保护单位与历史建筑`、`非遗项目与民间工艺`、`传说、轶闻与有依据的趣闻`、`本章其他有依据而有趣的线索`(OTHER) |
| Candidate selection | `ranked() = 价值×3 − 最近题材出现次数×2`，同题材被降权 | 不排序、不降权；`最近题材` 只作为 Context 里的弱反重复提示，并注明“不是黑名单，也不要求轮换题材” |
| Chapter entry | 只刷新 Context／候选，不产生机会 | 立即产生一次导演机会，跳过普通距离／时间门槛，但不越过安静、冷却、用户接管与位置新鲜度门槛；被压制的唤醒直接丢弃，不排队 |
| Fake Provider 文案 | “地形不只是坡度，它也影响土地怎么被使用”式结论句 | 一般机制＋明确“演示内容不代表真实 AI 质量”，并注明 Fake 只演示交互链，不代表编辑策略 |

## Preserved without change

Strict eight-field JSON contract (plus legacy six/seven-field acceptance), `landmark_id` eligibility rules,
user-intent priority, quiet/skip, ASK_USER one-shot window, PREPARE single-slot and expiry, 1500 m / 45 s / bearing
staleness, epoch cancellation, no narration queue, no background questions, grounded-fact rules
(exact figures/dates/current status require evidence, otherwise delete), image ephemerality and RouteHint session scope.
Independent landmark proximity keeps its own geometry, per-node per-journey opportunity, delivered-node dedup
and recency guard; `landmarks.json` stays a four-node coarse source-backed reference set — no POI service, no new
coordinates, no backend, no search, no new permissions.

## Simulation evidence (100 km/h / 16×, Xindu → Jushui, deterministic Provider + fake Voice)

- **Editorial chapter batch**: 13 cached chapters, 13 chapter-entry opportunities, 14 subjects selected from the
  shelf across 6 families (HISTORY / HISTORIC_SETTLEMENT / HERITAGE / CULTURAL_SITE / CULTURAL_GEOGRAPHY / PEOPLE),
  **zero** stale / cancelled / failures. Sanxingdui stayed eligible and was chosen twice (entity + artefacts angle)
  without any rule forcing it. Report: `artifacts/batch-simulation.txt`.
- **Silence stays legal**: providers scripted SILENT still complete 8–15 cadence opportunities per route with zero
  speech; a chapter wake-up blocked by quiet or cooldown is dropped rather than queued
  (`AreaChapterTest.quietAndCooldownSwallowChapterWakeUpWithoutLeavingAQueue`).
- **Independent landmark regression unchanged**: all four catalog nodes selected exactly once,
  18 opportunities = 14 SILENT + 4 SPEAK_NOW, zero stale / cancel / failure. Report: `artifacts/landmark-simulation.txt`.
- **M1.2 stale reproduction**: chapter wake-ups raise the request count from six to ten on the legacy 110 km fixture;
  nine late replies still die as STALE, nothing plays while the car keeps moving, and the only delivery happens at the
  terminus where the position stops changing (so it is not stale).
- **M1.3 dialogue cadence**: 14 automatic opportunities + 1 active request, 13 automatic narrations, zero stale.
- **Golden path (Fake, 80 km/h)**: 68 simulated minutes, 55 Director calls, 4 TTS outputs, Context Card 1166 characters.
  Reports: `artifacts/full-simulation.txt`, `artifacts/accelerated-simulation.txt`.

These deterministic reports are separate scenarios, not content-quality proof and not a per-chapter audio quota.

## Gates

`scripts/verify.ps1 -Offline` — assembleDebug/Release, testDebug/ReleaseUnitTest, lintDebug/Release: **PASS**.
136 + 136 tests, 0 failures / errors / skipped; lint 0 errors (Debug 6 / Release 3 informational warnings:
dependency-update hints and the framework ExifInterface suggestion); APK v2 signature verified.
Log: `artifacts/final-verification.log`; machine-readable summary: `artifacts/verification.json`.

Subject-side quality — whether real-model narration now feels broader and less template-like, and whether most
chapters actually get something said on a real drive — is part of
[combined phone acceptance](COMBINED_PHONE_ACCEPTANCE.md), items 3, 4 and 8.
