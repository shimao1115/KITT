# AGENTS.md — KITT Autonomous Build Contract

> **最高优先未来排序（2026-10-14 用户确认）**：[本地知识优先下一阶段路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)。先 V0.4.1 #23 结束页去评分，再 V0.5.0 新都高质量双包、V0.5.1 探索地点、V0.5.2 六类兴趣，其后 Github 分发/一键更新/在线 AI 检索。**不把 ChatGPT 账号不响应、在线模型列表或代理 VPN 不稳定视为当前施工 blocker**；云请求超时/失败不能阻碍本地知识使用，不许为查在线故障越权重构登录/定位/语音。无可信 GPS 时自动沿途仍不得推断位置；主动探索异地不需要 GPS。每一小版本须另行下发任务卡验收。**目前只有规划更新，V0.4.0/#20 的施工 STOP 与未验状态仍有效**。

> **2026-10-14 后续知识包接班规范（不扩当前施工）**：凡涉及离线事实包、成品文字讲述包、多模型资料生产、GitHub 存储、下载和发行，应先读 [正式内容标准 V2.4](docs/KITT_KNOWLEDGE_STANDARD.md)、[知识工厂 V2.4](docs/KITT_KNOWLEDGE_FACTORY.md) 与 [长期离线蓝图 V2.4](docs/FUTURE_OFFLINE_KNOWLEDGE_PACKS.md)、[离线包管理与更新规范 V1.0](docs/KITT_OFFLINE_PACK_MANAGEMENT.md)。用户已确定 **Fact Pack + Narrative Pack 两层纯文字知识、手机本地 TTS、基础功能不需要 API**；旧 V0 单层事实/在线 Director/不准预制旁白的说明只作为历史实现证据，不得用来否定未来按主题制作审核过的完整文字故事。**更新决定：仅办公室电脑/家庭电脑生产，GitHub 存储协作和 Releases 分发，NAS 不参加。讲述默认离线优先；即时优先由 AI 现场组织，但**已选方案 B**：无论哪种讲述模式，AI 的地方知识问答均可先查相关本地 Fact/Narrative，缺证据或临时问题再联网查证，失败提示并回退本地故事；真正实时问题需要可靠在线新数据。** **知识包管理参考已定：按地区一键准备事实＋讲述、默认仅 Wi-Fi 自动更新已装包（可关闭）、显示真实覆盖/更新摘要、校验后原子安装/失败保留旧包；行前提示及签名本地导入列后续，差分优化暂缓。**新增正式交互“探索地点”**：手机上按当前位置或输入任何地区查已有主题，点主题直接看已审稿**完整文字讲述**，正文下方“播放”才让本地 TTS 朗读同一篇文字；无包、有网有 AI 才允许标明为临时在线探查。浏览别处不得覆盖真实 GPS 状态；不做音频包。模式属于未来任务而非已实现。**只有已授权任务卡才能改代码或发布包；V0.4.0/code14 已工程交付，不能因此自行施工 V0.5.0 或调整 #20 历史验收。

> **后续用户确定（2026-10-14）**：主题分类第一版严格六类，具体内容类别依[知识标准 V2.4](docs/KITT_KNOWLEDGE_STANDARD.md)；新成品 Narrative 每篇一主类及可选辅类，兴趣设置每类“多讲/正常/少讲”默认正常，**不可根据旧 `TopicFamily`/对象 `family` 直接推断主题分类**，也不可固定比例强制讲述。用户决定**取消旧结束页五项评分、意见框和保存评分**；保留轻量纪要和返回，旧本机 JSON 不得被破坏。**不得以此擅自修改现有 code14；由单独下一小版本施工任务与实测验收。**

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
