# Future Task — Runtime Transparency & Diagnostics

Status: **Stage 1 AUTHORIZED (V0.3.4 / Issue #15); Stage 2 remains BACKLOG**

Active stage-one scope and acceptance: [TASK_V034_RUNTIME_STATUS.md](TASK_V034_RUNTIME_STATUS.md).
Origin: real-world field-use feedback (2026-10-09).

## Problem

When 沿途 goes silent, the user cannot tell whether the app is:
- working normally and waiting for something worth saying;
- still acquiring/recovering location;
- unable to connect through the VPN / mobile network;
- researching an area;
- waiting for hosted search completion;
- synthesizing/deciding narration;
- preparing to play audio;
- in a legitimate cooldown/quiet mode;
- stalled or failed.

Silence is currently indistinguishable from failure, eroding trust.

## Product principle

> At a glance: is it working? At a tap: what is it doing? In diagnostics: why is it taking so long?

Do **not** turn the driver screen into a debug console.
Keep the existing five user-facing Journey states (IDLE/READING/SPEAKING/LISTENING/QUIET).
Add a read-only **health / activity / progress** overlay orthogonal to Journey state, not more JourneyState values.

## Three display layers

### A. Driving page: minimal status strip + one human sentence

Stable indicators should cover:
1. **Location** — source GPS/NETWORK/LAST_KNOWN/unknown, accuracy, age, area if resolved; coarse vs precise clearly separated.
2. **Connectivity** — Android connectivity, VPN/TUN visible if detectable, and **AI service reachability separately** (based on actual recent attempts; network-connected alone is not "ChatGPT working").
3. **Current work** — e.g. 正在识别区域 / 搜索资料 / 整理证据 / AI 决策中 / 准备播报 / 正在讲述 / 等待合适时机.
4. **Degradation/error** — only when actionable or material, e.g. 位置暂不可用 / AI 请求失败 / 本次资料不充分.

One prominent text line should answer “why it is silent.” Examples:
- 正常运行 · 正在等待值得讲的新内容
- 正在查询新繁东湖资料 · 已等待 35 秒
- 位置约 ±300 米 · 目前只进行区域级讲述
- 网络可用，但最近一次 AI 请求失败
- 已备好介绍，等待合适的讲述时机
- 安静模式 · 剩余 8 分钟

Avoid a spinner that can remain forever without explanation. No fake exact percentages or speculative completion ETA.

### B. Tap to expand: work / dependency detail

Show a compact expandable diagnostics panel only when desired:

**Position**
- source, horizontal accuracy, fix age, resolved admin area
- GPS/NETWORK transitions, permission/provider availability

**Network**
- system connected or not, Wi-Fi/cellular, VPN active if detectable
- last known model-service attempt result/time/latency and hosted research attempt
- explicit not-yet-tested / unknown rather than invented green

**Director/knowledge pipeline**
- area recognized;
- Overview: not started / running / READY / failed / cancelled / insufficient evidence;
- Topic: absent / running / READY / failed / cancelled;
- Director: idle / deciding / awaiting model / prepared / waiting opportunity / failed;
- active user question: independent higher-priority branch.

**Audio/interaction**
- idle / TTS preparing / speaking / finished / error;
- listening / processing recognition / awaiting user answer;
- app quiet mode / suppressed by user / cooldown or deferred by policy.

These are independent, potentially simultaneous tracks; do not force them into one linear loading bar.
Label only stages actually observable from the corresponding component.

### C. Developer/diagnostics view: bounded event timeline

Expose recent significant events with local timestamps or elapsed time:
- location source changed / fix stale / resumed;
- chapter entered / area resolution changed;
- Overview and Topic start, completion, cancellation, failure;
- Director request start, finish, cancelled, timeout;
- TTS request start, playback started, finished, stopped, error;
- network/provider recent failure category.

Keep a bounded, lightweight ring buffer, with no infinite growth. For any user-triggered retry or failure, surface useful plain-language explanation and recovery (e.g. “请检查网络/VPN” vs generic “Error”).

Privacy:
- no full coordinate trail by default;
- no OAuth tokens, authorization headers, private prompts, full model payloads, raw audio, or unredacted secrets in logs;
- any user export must be explicit and redacted.

## Health model

Do not infer service health from Android network state alone.
Distinct signals:
- device connectivity and VPN/TUN presence;
- successful/recent request to actual ChatGPT provider;
- hosted search success/failure and recency;
- location-fix quality and recency;
- actual state of current jobs;
- speech pipeline state.

Use **unknown** if untested; **working** only if supported by active/recent evidence; **failed/degraded** only with concrete error or timeout. A slow model request is not automatically “VPN failed”.

Prefer passive events already in production, no continual high-frequency ping/active probes. Optional explicit one-shot connectivity check if justified.

## Interaction / concurrency

- Do not block location or Journey while Overview/Topic is pending.
- User talk interrupts prior background Topic work; diagnostics must reflect cancellation, not show stale “searching”.
- Do not show 'AI 决策中' after the request has completed, timed out, or been cancelled.
- When speech is queued/preparing, distinguish it from started audible playback.
- On quiet/cooldown/waiting, explicitly say it is intentionally not speaking.
- When GPS goes stale, use NETWORK fallback if present; diagnostics should show source + precision and never use VPN exit/GeoIP to determine physical location.
- Show time since current stage actually started; don't promise completion times.
- Avoid creating a second orchestration state machine solely for display; derive from existing component-owned events/counters.

## UX / driving safety

- One glance status on driver screen, not dense technical rows.
- All detail behind one optional tap or for passenger/parked exploration.
- No modal alerts, forced reading, vibration, or narration for routine transitions.
- Status should not cause layout jumps or move the big voice-control button.
- Design for phone landscape and low-height screens.
- Respect existing product spec: five top-level Journey states remain.

## Implementation sequencing

1. Inventory actual telemetry signals available in Journey, RealLocationSource/PhysicalLocationSelector, ChapterResearch, DirectorLoop, Provider request path, VoiceEngine/TTS/ASR.
2. Implement one small read-only snapshot/aggregator updated by existing lifecycle hooks.
3. Add the minimal dashboard strip and clear 'why silent' sentence.
4. Add expandable detail and bounded event timeline only after minimal layer verifies in real phone.
5. Test under real VPN/TUN + Wi-Fi/cellular + weak GPS + network timeout.
6. Avoid adding a new backend, external analytics service, map SDK, global event bus, or large logging framework.

## Acceptance matrix

- healthy idle: clearly "running, waiting", not falsely "stuck";
- startup location acquisition / coarse network location / GPS recovery shown accurately;
- VPN active and Android internet exists but provider request fails: no false AI-green status, no jump to VPN exit country;
- Overview / Topic search running >30s shows actual stage + elapsed duration;
- research pending does not freeze Director/Journey;
- cancellation after user interruption immediately updates;
- research failure / thin evidence has clear state;
- Director waiting, PREPARE held, TTS preparing and actual speaking are distinguishable;
- quiet mode and intentional cooldown are plainly explained;
- network/API timeout cannot leave perpetual spinner;
- app background/foreground transition does not show misleading old state;
- checks do not leak sensitive payloads or store continuous GPS tracks.

## Priority relative to other backlog

User reported that unclear silence is a major real-driving friction, so prioritize this near the front of UX improvements after V0.3.3 field verification, together with the repeated-narration problem (#13). Stage 1 implementation is authorized by the user; Stage 2 remains future work, and Issue #14 still awaits field acceptance.
