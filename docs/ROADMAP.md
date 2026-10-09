# KITT V0 Roadmap — Destination Chain

> **当前授权任务：V0.3.5 Android 原生融合定位与连续性实测 / Issue #16**。按 [TASK_V035_FUSED_LOCATION.md](TASK_V035_FUSED_LOCATION.md) 先做 GPS/NETWORK 与 FUSED 的自然设备对照；只有证据支持时才启用生产第三来源。保留 Android 8.0+、VPN/IP定位隔离、旧定位时效与状态显示；不要顺带开发其他内容。Issue #14 的外场验收与 Issue #15 的用户验收仍独立待处理。

> **V0.3.4 运行状态透明化第一阶段 / Issue #15 已完成工程交付，等待用户总验收**。Debug314/Release301、构建/lint/签名PASS；独立手机包完成当前条件冒烟，原包保留账号覆盖升级等待原签名密钥。详见 [TASK_V034_RUNTIME_STATUS.md](TASK_V034_RUNTIME_STATUS.md)、[运行状态验收](ACCEPTANCE_RUNTIME_STATUS.md) 和根 HANDOFF。到此停止，不做展开式诊断/日志、不修 Issue #13；Issue #14 仍等待实车验收。

> 上一轮 V0.3.3 定位可靠性热修复 / Issue #14 的规格、自动回归及待外场验收详见 [HOTFIX_LOCATION_FALLBACK.md](HOTFIX_LOCATION_FALLBACK.md) 与 [ACCEPTANCE_LOCATION_FALLBACK.md](ACCEPTANCE_LOCATION_FALLBACK.md)。

> 当前轮次只收口 **Overview → Topic 研究链**，真机与回归结果见 [ACCEPTANCE_STAGED_RESEARCH.md](ACCEPTANCE_STAGED_RESEARCH.md)。
> 验收完成后停止，不启动下一轮功能、架构或 Provider 扩展。此前静音热修复与真实搜索证据规则继续有效。

> 当前紧急施工优先 [HOTFIX_V03_TOTAL_SILENCE.md](HOTFIX_V03_TOTAL_SILENCE.md)，结果以 [ACCEPTANCE_V03_SILENCE_HOTFIX.md](ACCEPTANCE_V03_SILENCE_HOTFIX.md) 与根 HANDOFF.md 为准。
> 沿途 V0.3 原任务仍为 [TASK_POST_ASR_YANTU_V03.md](TASK_POST_ASR_YANTU_V03.md)（含主动按需搜索）。
> 中文 ASR 已完成并由用户验收，保持其后端与交互。V0.3 的工程实现与真实 hosted-search／新都黑盒内容验收分开记录；
> 下文历史 Stop Rules 不重开已通过的 Gate，当前外部缺口与最短验收以根 HANDOFF.md 为准。

本路线图不是逐行施工脚本，而是一组必须到达的 Destination + Acceptance Gates。Codex 可以自主选择最短实现路径。

总目标：

> 用户醒来后能安装/运行一个 Android V0，用模拟驾驶跑“成都→绵阳约80km/h”，看到完整的 GPS/Context → AI Director → TTS → 用户语音接管链路，并做一次旅程结束评分。

---

## Destination 0 — Repository Bootstrap

### 目标
建立最小可构建 Android 工程和自主施工基础。

### 必须产物
- Kotlin + Jetpack Compose 原生 Android 工程
- 可编译的 app
- 基本 package / application id 自主选择并记录
- `HANDOFF.md` 初始文件
- secrets 不入库
- 本地开发说明

### Gate
- Debug build PASS
- 至少一个最小测试 PASS
- App 可启动到占位主驾驶页

### 不要做
不要先搭后端、数据库、DI 大体系、复杂 Clean Architecture。

---

## Destination 1 — AI Provider Spike

### 目标
尽早验证“AI 能力能否真正接通”，但不让认证阻塞其他施工。

### 路径优先级
1. 调研并验证当前可行的 ChatGPT/Codex 相关授权/调用方式；
2. 如果技术限制、授权限制或环境不适合 V0，切普通 API Provider；
3. 若当前环境缺用户授权/密钥，用 Fake Provider 先跑完整上层主链，并把真实 Provider 接口和最小待办留好。

### Provider 必须支持的上层契约
- model 可配置
- reasoning effort 在支持时可配置
- structured DirectorResult
- 可选 web search；无原生搜索时允许后续 Search Adapter

### Gate
满足其一即可继续：
- 真实 Provider 返回一次合法 `SILENT/SPEAK_NOW/PREPARE/ASK_USER`；
- 或 Fake Provider 完整通过契约测试，且 HANDOFF 精确记录真实 Provider 只差哪个外部授权动作。

### 失败策略
首选 Provider 失败 ≠ 项目失败。
不要卡在认证上整夜不动。

---

## Destination 2 — Journey State + Driving UI

### 目标
先把产品状态做对，不接复杂地图。

### 实现
五个用户可感知状态：
- IDLE
- READING
- SPEAKING
- LISTENING
- QUIET

主驾驶页：
- 大按钮
- “开始读山河”
- “说点什么”
- “跳过”
- “安静一会儿”
- 安静状态巨大“结束安静”
- “结束旅程”

安静默认 10 分钟，允许提前退出。

### Gate
自动测试覆盖：
- start/end
- quiet 10min
- early resume
- skip stops current content
- state transition 不出现冲突

UI 人工验收：
- 核心按钮一次点击完成
- 不滚动
- 触控目标明显偏大
- 深浅色跟随系统

---

## Destination 3 — Location Source + Context Pipeline

### 目标
让真实 GPS 和模拟 GPS 共享同一后续链路。

### 实现
定义 LocationSource 概念：
- RealLocationSource
- SimulatedLocationSource

Context 层把原始位置压缩成 Context Card：
- current location
- speed/bearing
- altitude/trend（可得时）
- recent movement summary
- destination intent
- recent topics
- interaction state
- optional map enrichment

高德/地图信息是可选 enrichment。拿不到就空，不阻塞 Gate。

### Gate
- 可以切换 real/simulated source
- 下游 Director 不知道位置来自哪里
- Context Card 不包含原始巨量 GPS 数组
- 无地图 Provider 时仍可工作

---

## Destination 4 — 成都→绵阳模拟驾驶

### 目标
建立可重复主链测试场。

### 实现
开发者隐藏入口：
- fixture 选择
- 模拟速度
- start/stop

首个 fixture：
- 成都→德阳→绵阳方向
- 默认约 80 km/h

若用户后续提供高德截图：
- 用截图替换/校正 fixture
- 不建设路线 API

若截图尚未提供：
- 使用清晰标注的 coarse test fixture
- 仅服务主链测试，不宣称导航级精度

### Gate
- 模拟位置连续推进
- Context Card 随之变化
- 可加速测试（若实现成本低）
- 整个 App 不需要知道这是“假 GPS”，只知道 LocationSource

---

## Destination 5 — Director Core

### 目标
把 `docs/AI_CONTRACT.md` 变成真实可运行核心。

### 实现
Prompt 三层：
- System Constitution
- Session Instructions
- Context Card

结构化动作：
- SILENT
- SPEAK_NOW
- PREPARE
- ASK_USER

策略：
- 传感器高频、AI 低频
- 本地新信息触发导演检查
- 软冷却
- 同时最多一个 PREPARE
- stale PREPARE 丢弃
- 不做旁白队列

### Gate
测试至少覆盖：
- 合法响应解析
- 非法响应降级
- auto failure => SILENT
- PREPARE replace/invalidate
- 用户意图覆盖自动动作
- stale content 不补播

真实/Fake Provider 均可用于 Gate；有真实 Provider 时优先真跑一遍。

---

## Destination 6 — Voice Loop

### 目标
实现“自动时它讲，你开口时它听”的魔法闭环。

### 实现
- Android 本地 TTS
- 系统/可用 Speech Recognition
- “说点什么”单击开始
- TTS 被用户主动输入立即打断
- 用户说完自动提交
- AI 主动 ASK_USER 后自动开启一次监听
- 无回答自动放弃，不反复催
- “再讲一点”走同一主动对话链
- 主动回答结束后短暂软冷却

### Gate
人工/自动混合验收：
- 正在播时点击“说点什么”立即停止
- 用户一次输入可到 Director 并得到回答
- ASK_USER 可形成一轮自然问答
- 无回答不会死循环
- 不保存原始录音

---

## Destination 7 — Background, Permissions, Notification

### 目标
符合真实驾驶的 Android 生命周期。

### 实现
- 旅程由用户显式开始
- location foreground service
- 权限按场景渐进申请
- 前台驾驶页可保持常亮
- 锁屏/后台旅程继续
- 不主动点亮屏幕
- 锁屏时减少需要即时回应的主动提问
- 常驻通知只放：
  - 安静/结束安静
  - 结束旅程

### Gate
- App 切后台后旅程不立即丢失
- 返回 App 状态一致
- 通知不骚扰
- 没有“始终监听麦克风”
- 无不必要权限

---

## Destination 8 — Persistence + End-of-Trip

### 目标
旅程可以自然结束和恢复，不建设数据工程。

### 实现
轻量本地持久化：
- settings/current state → DataStore 或同等级简单方案
- trip summaries → JSON 或同等级轻量方案

保存：
- current trip recovery
- recent topics / session instruction summary
- destination intent
- lightweight final summary

不长期保存：
- full GPS
- recordings
- full conversation
- full search history

结束页：
- 旅程纪要
- 1–5 分简短评分（≤5项）
- optional feedback text

未正常结束：
- 下次启动问“继续/结束”

### Gate
- abnormal reopen 可恢复/结束
- trip end 清理 runtime
- summary 可回看或至少落盘验证
- feedback 可保存本地
- 不需要数据库服务器

---

## Destination 9 — Settings + Provider Control

### 目标
给开发与高级用户必要的可调能力，不把设置变成参数垃圾桶。

### 允许
- Provider / auth status
- Model
- Reasoning effort（支持时）
- TTS speech rate
- Notification state
- version/about
- hidden developer mode

### 禁止
- content weights
- narration interval sliders
- search depth
- route preferences
- user profile controls

这些由自然语言 Session Instructions 解决。

### Gate
- 换 Provider/模型不需要改 Director 业务代码
- 不支持 reasoning effort 时 UI 不应伪装支持
- secrets 不写入 repo

---

## Destination 10 — Full Simulation Acceptance

### 目标
从成都到绵阳跑完整条模拟黄金路径。

### 验收问题
1. 它是不是太爱说话？
2. 每次开口是否真的值得听？
3. 现场是否明显对不上？
4. 用户主动说话时是否自然接管？
5. ASK_USER 是否有价值而不烦人？
6. 安静10分钟、跳过、再讲一点是否符合预期？
7. provider/search 失败时是否只是“少说一段”，而不是炸 UI？
8. 结束页评分是否可用？

### 产品 PASS
不是“所有功能按钮都能点”，而是：

> 能明显感到这是一个“懂山河、懂分寸、听得懂人”的副驾驶雏形。

如果内容质量不足：
- 首先调 System Constitution / Context Card；
- 其次调 AI 唤醒条件；
- 再考虑模型/effort；
- 最后才考虑增加工程复杂度。

不要反过来先堆系统。

---

## Destination 11 — Handoff

### 目标
用户醒来即可总验收，不需要先理解开发过程。

更新 `HANDOFF.md`：

```text
状态：
安装/运行方式：
默认 Provider：
默认 Model/Effort：
Build：
Tests：
模拟路线：
完整模拟结果：
需要用户本人完成的动作：
已知限制：
建议总验收步骤：
Milestone commits：
```

工作树保持干净或明确解释残留。

---

## Destination 12 — Editorial Freedom / Prompt Reset

### 目标
把内容层从“命题作文模板”改回“素材＋边界＋现场”，让 KITT 像一个有见识的同行者而不是标准化纪录片。
任务卡：`docs/TASK_EDITORIAL_FREEDOM.md`。

### 实现
- Director Constitution 去掉“眼前切入→一个问题→解释一层→落回眼前→停”和“改变对这片土地的理解”式硬规则；
- 区域章节改为开放的**本地素材架**：候选标题是中性主题／实体标签，可带依据与来源，不预设论点；
- 进入新的镇乡街道章节直接创造一次导演机会（跳过普通距离/时间门槛，但不越过安静、冷却、用户接管与新鲜度门槛）；
- `TopicFamily` 增加 `OTHER`：分类是标注辅助，不是允许讲话的清单；
- 最近题材只作为弱化的反重复信号，不再对候选降权或排序；
- 三星堆等已核实节点保留为高价值素材，可用但不强制，也不再由规则规定“通常胜过”其它题材。

### Gate
- Debug/Release 编译、单测、lint 全绿；
- 新都→雎水 100km/h / 16× 模拟：每个章节都有讲述机会，确定性 Provider 下 13 章节 14 段内容、零 stale/cancel/failure；
- 独立地标回归保持原状（4 节点各一次、无待播队列、晚到即死）；
- 严格 JSON 契约、用户接管、安静、过时与去重保护全部保留。

主观内容质量（真实模型下是否真的更爱讲、更好听）属于手机联合验收，见 `docs/COMBINED_PHONE_ACCEPTANCE.md`。

---

# Stop Rules

> 2026-10-03 编辑自由度批次（Destination 12）已完成非手机施工：Director 提示词重置、本地素材架、
> 章节进入机会、`OTHER` 分类与相应回归全部落地。它不重开已通过的路线截图、旅途看图、界面打磨或 ASR 工作。
> 2026-10-03 Post-M1.4 用户授权优先：本批次手机离线不构成施工阻塞；全部手机专属 Gate 合并延后。
> 中文 ASR 不在本批次范围。当前完成 A–E 的实现、详细新都→雎水模拟及独立地标接近行为后，
> 首个剩余 Destination 是 `docs/COMBINED_PHONE_ACCEPTANCE.md`，不是重新施工已通过的历史阶段。

以下情况允许开发 Agent停下并等待用户，但必须先完成所有可绕过工作：

- OAuth/账号授权只能由用户本人点击；
- API key 完全缺失且没有 Fake/其他 Provider 可以继续；
- Android 真机权限/硬件行为必须由用户本人验证；
- 用户承诺提供的高德截图尚未提供，而最终路线精修只能靠它完成。

即便如此，仍应留下一个可编译、可 Fake-run、可继续施工的仓库，而不是半截工程。

# Anti-Overengineering Gate

任何新依赖、抽象层、后台服务、数据系统或工具接入，在添加前先问：

> **它是否解决了一个已经真实出现的问题？**

如果答案只是“以后可能有用”，不要加。
