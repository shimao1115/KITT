# AGENTS.md — KITT Autonomous Build Contract

本文件是 KITT 仓库中对开发 Agent 的最高优先级工程说明。任何实现必须同时满足本文件与 `docs/PRODUCT_SPEC.md`、`docs/AI_CONTRACT.md`、`docs/ROADMAP.md`。

## 1. 任务

自主完成“路上读山河”V0，使它成为一个可运行、可模拟验收的原生 Android AI 副驾驶。

不要把用户当项目经理或搬运工。除非遇到以下真正阻塞，否则不要中途索要决策：

- 需要用户本人完成 OAuth / 登录 / 授权；
- 必须输入外部密钥且仓库与环境中均不存在；
- 必须由用户提供现实世界素材才能完成最终替换；
- 外部服务明确不可用，且没有文档允许的替代路径。

遇到上述阻塞时：
1. 把阻塞压缩成最小范围；
2. 先使用接口、fake/mock 或 fallback 完成其他所有可完成工作；
3. 在 `HANDOFF.md` 记录准确阻塞、已验证事实和用户只需完成的最小动作；
4. 继续后续不依赖该阻塞的里程碑。

## 2. AI Native 原则

不要把 Roadmap 当逐行脚本。它是一条 Destination Chain。

你应当：
- 先理解目标与验收；
- 自主选择最短实现路径；
- 先跑通黄金路径；
- 只有真实失败或质量不足时，才增加中间层；
- 优先删除复杂度，而不是用更多抽象修复复杂度。

三层决策顺序：

> **本地确定 → 问用户 → AI 推理**

含义：
- 本地代码能廉价、确定地判断的，不调用 AI。
- 一个自然问题就能得到可靠答案的，不构造复杂算法长期猜测。
- AI 用在真正有价值的判断：选题、解释、对话、事实核验决策。

### Agent 接班原则

KITT 允许 Codex、WorkBuddy、Qoder、OpenCode 或其他施工 Agent 接力开发。任何新 Agent 接班时：

> **先继承现状，再继续第一个未完成的 Destination；除非现有实现明确阻塞验收，否则不要为了个人偏好重构已经通过 Gate 的部分。**

接班前先阅读 `AGENTS.md`、`docs/ROADMAP.md`、最近 milestone commits 和 `HANDOFF.md`（若已存在），以 GitHub 当前状态作为唯一施工真相源。不同 Agent 的角色是轮班施工队，而不是重新设计项目。

## 3. 禁止擅自扩张 V0

除非规格明确要求，不要新增：

- 自建导航或路线规划器
- 强制目的地/途经点管理
- 复杂路线预测
- 大型地图数据栈、DEM/OSM/地质数据库
- RAG、向量数据库、知识库
- Room/复杂数据库（除非实测证明轻量存储无法满足）
- 多 Agent / Director+Writer+Reviewer 链
- 大小模型自动路由
- 本地大模型
- 云端高级 TTS
- 常驻唤醒词
- 后台持续录音
- 摄像头识别
- Android Auto / 车机控制
- 音乐焦点联动、自动 ducking、车机音源控制
- 酒店/餐馆预订、支付、消息、日历等执行工具
- 行为埋点平台、远程分析后台
- 完整 GPS 轨迹长期留存
- 完整对话/录音长期留存
- 自动写入 GitHub Memory Hub
- 为模拟测试建设生产级路线 API
- “以后可能需要”的服务器与后台

如果一个功能不能直接帮助：
1. 更准确地知道当前现场；
2. 更好地判断该不该开口；
3. 把一件值得理解的事讲清；
4. 让用户更自然地接管 AI；
则 V0 默认不做。

## 4. 架构责任边界

产品层只保留五个核心责任域，实际包结构可以更少，不要为了架构图创建空层：

### Journey
开始/结束旅程、10 分钟安静模式、目的地意图、Session Instructions、最近主题、一个 PREPARE、旅程纪要。

### Context
真实 GPS 或模拟 GPS → 压缩后的 Context Card。地图 Provider 只能做 enrichment，不得成为主链硬依赖。

### AI Director
同一个 AI 能力负责自动导演、主动对话、是否查证、是否 ASK_USER。V0 不拆多 Agent。

### Voice
Android 本地 TTS + 按需语音识别。用户主动说话立即停止当前 TTS。

### UI
驾驶大按钮界面和旅程结束页。UI 不自行判断导演逻辑。

## 5. Provider 解耦

AI Director 不得依赖某个具体模型名或厂商业务逻辑。

定义薄 Provider Adapter，使上层只关心：
- system constitution
- session instructions
- context card
- optional user utterance
- structured director response

首选技术 Spike：当前可行的 ChatGPT/Codex 相关授权路径。
如果不可行或限制不适合 V0，直接使用普通 API Provider。不要让首选 Provider 阻塞整个项目。

设置中允许：
- Provider
- Model
- Reasoning effort（仅在 Provider/模型支持时）

不要在业务代码散落 `if model == ...`。

## 6. 质量规则

每个里程碑：
- 编译必须通过；
- 与该里程碑相关的自动测试必须通过；
- 不得遗留已知崩溃；
- 不得把密钥提交到仓库；
- 对无法自动验证的能力留下最小人工验收说明；
- commit message 清晰描述 Destination；
- 工作树尽量保持干净。

至少覆盖这些状态测试：
- journey start/end
- silence 10min / early exit
- user interruption stops TTS
- ASK_USER one-shot listening
- PREPARE invalidation
- stale content never replays
- provider failure silent-degrades for auto mode
- active request failure reports briefly
- director response parser/schema validation

## 7. 施工完成后的交接

最终创建/更新根目录 `HANDOFF.md`，至少包含：
- 当前可运行能力
- build/test 结果
- 最终默认 Provider/模型配置（若可用）
- 尚需用户本人完成的授权/密钥步骤
- 成都→绵阳模拟验收结果
- 已知限制
- 下一次人工总验收最短步骤
- 最近 milestone commit 列表

目标不是“代码很多”，而是用户醒来后可以直接做总验收。
