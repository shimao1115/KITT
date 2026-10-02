# KITT V0 Roadmap — Destination Chain

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

# Stop Rules

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
