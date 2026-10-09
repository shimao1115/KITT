# V0.3.4 — Runtime Status Transparency (Stage 1)

Status: **AUTHORIZED FOR CODE IMPLEMENTATION** (2026-10-09)
Scope: **one small milestone**, driver-screen transparency, NOT a full diagnostics platform.

Product goal:
> 沿途不说话时，用户应当一眼看出它是在正常等待、定位不足、网络/服务出问题，还是搜索、整理、决策、准备播放中。

## Baseline and reference

- Begin from current main + `HANDOFF.md` (V0.3.3 positioning).
- Parent design: `docs/FUTURE_RUNTIME_TRANSPARENCY.md`.
- Preserve five Journey states: IDLE / READING / SPEAKING / LISTENING / QUIET.
- V0.3.3 Issue #14 remains open until *field* acceptance; don't silently close.
- Issue #13 duplicate narration is separate. Don't alter anti-repeat behavior in this milestone.

## P0 — one-glance driving status

Add compact, stable, glanceable runtime indicators to the existing driver page, in normal and compact landscape layouts:

1. **Position**
   - existing GPS / NETWORK / LAST_KNOWN / unknown from accepted physical fix only;
   - approximate accuracy and last-measurement age;
   - concise coarse/precise indication when applicable;
   - resolved area only if actually available.
   - Never use IP/VPN/search geography for physical position.

2. **Network + AI provider availability as distinct signals**
   - Android's connectivity state (connected / unavailable / unknown; Wi-Fi vs cellular as OS actually exposes), plus VPN/TUN status if detectable through normal permissions;
   - separately display *actual recent AI-service request outcome* (last success/error/never attempted), with age as appropriate;
   - connectivity alone must not appear as “AI 服务正常”;
   - uncertain or untested must say “未验证/未知”, not green.
   - Avoid active background ping loops and extra unnecessary permissions; request `ACCESS_NETWORK_STATE` only if needed.

3. **Current task / why silent** (one clear primary sentence, one optional secondary terse hint)
   - examples: “正在获取位置”, “正在识别所在地区”, “正在搜索本地资料 · 已等待 38 秒”, “正在整理搜索证据”, “AI 正在生成讲述”, “内容已备好，等待讲述时机”, “正常运行，等待值得讲的新内容”, “正在准备语音”, “正在讲述”, “安静模式 · 剩余 8 分钟”, “上次请求失败，等待下次触发”.
   - The phrase must reflect a *true observable runtime stage*, not a guessed pipeline step.
   - If a stage cannot be observed reliably, present the coarser truthful state instead.
   - Show elapsed time for in-flight jobs, never pretend to have percent complete or a deterministic ETA.
   - A pending/failed/cancelled request must clear its in-flight label promptly; no eternal “搜索中” spinner.

4. **Not speaking is not the same as broken**
   - distinguish user quiet/cooldown/normal wait from location unavailable/service failure.
   - If several tasks run in parallel, primary status should favor user's active interaction, then current speech/listening, then relevant in-flight action; keep independent location/network indicators visible.
   - Preserve late-topic-cancel, stale-result and interruption semantics.

## Technical boundaries

Use current Journey / LocationSource / PhysicalLocationSelector / ChapterResearch / DirectorLoop / provider transport / VoicePort lifecycle truth where observable.

Implement a **small read-only status snapshot** or similarly light projection. This is a view of existing production events, NOT a second workflow engine or broad mutable state machine.

- Updates must be reliable on activity re-entry/resume and when background Journey continues.
- Any timer for elapsed stage time must be cheap, bounded, and tied to real pending state.
- Minimize Compose recomposition; do not run high-frequency polling of network/location.
- Keep “说点什么”, “跳过”, “安静一会儿” controls fixed and accessible.
- Fits vivo X200 Pro landscape (~20:9) and Galaxy Tab S9 (~16:10) screen proportions; no scrolling on driving screen, no cramped status chips and no forced reading.
- Do not output raw prompts, tokens, OAuth headers, actual coordinates or continuous tracks to UI/events.

## Out of scope (Stage 2, future only)

- Expandable full diagnostics panel;
- persistent diagnostic or event timeline;
- log export/share;
- manual ping/repair action;
- new map rendering/map SDK;
- new TTS/ASR, knowledge packs, vector DB;
- background analytics, backend service, broad refactoring;
- fixes for narration duplication (#13).

## Test / acceptance

Both automated tests and actual device smoke tests when a phone is available:

1. Initial idle/start location acquisition -> no false ready.
2. GPS precise -> NETWORK coarse fallback -> GPS recovery; source and precision follow truth.
3. Android Wi-Fi/cellular+VPN connection active but provider unreachable -> show network vs AI separately; no GeoIP effects.
4. Before any request, AI service state “未验证” (never optimistic).
5. Overview pending >30s -> honest stage + elapsed time; Director and Journey are not globally frozen.
6. Topic in background + Director/user interruption -> cancellation clears stale status; active path is primary.
7. Provider/research error/timeout -> stops “searching”, shows recent failure or normal subsequent wait accurately.
8. Director PREPARE waiting opportunity / cooldown vs truly idle -> meaningful honest message.
9. TTS queued/initializing vs actually audible speaking if engine exposes it; if unavailable say “准备讲述” without claiming actual playback.
10. QUIET/LISTENING have clear priority; no UI state flicker on quick transitions.
11. Lock/background/resume does not re-show stale status; no unbounded timers, logs or polling.
12. Verify current V0.3.3 GPS fallback regressions, Overview/Topic, Director, user interruption, ASR/TTS and sim route remain passing.
13. Screenshots or clear visual evidence for portrait + landscape/compact UI if available.

Do not claim tests or field scenarios have passed if only simulated or lacking the corresponding hardware/network conditions.

## Autonomous delivery contract

Agent should read HANDOFF and existing classes/tests, choose a minimal implementation, build and run full verification, repair failures, then update HANDOFF/acceptance notes and push the milestone commit(s). Deliver installable debug APK (release artifact if existing practice requires), exact commit IDs, test totals, pass/fail and any real-phone limitations.

No approval is required for routine implementation choices; ask user only for an unavoidable external credential/irreversible decision. Do not require user to shuttle individual files between agents.

Stop after Stage 1 passes. **Do not silently start Stage 2 or fix Issue #13.**
