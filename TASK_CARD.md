# CURRENT TASK CARD — KITT V0.3.5.2 / Issue #18

**执行状态（2026-10-13）：#18 工程已交付，V0.3.5.2/code12，commit `f7cbd6b`；最终 Debug365/Release352、构建/lint/签名与六组模拟/驾驶解析回归 PASS，同签名 runtimecheck APK已产出。手机升级/杀进程/真模型内容验收 PENDING。当前停止等待一次人工验收，不再施工下一 Destination；#19家庭端仍 OPEN/PENDING。** 本版实测与最短升级见 [HANDOFF.md](HANDOFF.md) 和 [验收](docs/ACCEPTANCE_RESEARCH_CACHE.md)。下方保留本次施工授权与边界，不能把其中施工目标当作尚未开始的新任务。

**用户最新授权（2026-10-13）：立即恢复 V0.3.5.2 的施工；#19 办公室签名 PASS，另一台电脑的密钥复制/验签 PENDING，但不再阻塞业务代码与办公室端构建。**
**当前唯一代码施工 Destination：** [Issue #18 — 持久化有来源的地区研究证据](https://github.com/shimao1115/KITT/issues/18)。
**开发方式：** AI-native 自主完成理解 → 实现 → 测试 → 修复 → 交付，用户只做小版本验收。先继承 `AGENTS.md`、`HANDOFF.md`、`docs/ROADMAP.md`、#18 和最新 commits。不要重做已通过的 Golden Path。

## 先继承基线与平行待办

- 当前业务基线 V0.3.5.1 / code11；真实设备 `com.kitt.reader.runtimecheck` 已保留 ChatGPT 账号/设置。#17 行政区稳定修复工程已交付但连续镇街真机验收尚未全 PASS；#14 位置新鲜度/室外 GPS↔NETWORK 是独立事项，**不要改 Fix 老化策略、定位来源、FUSED 或 VPN/IP 地理边界**。
- #19 办公室端已用原私钥验证：`%USERPROFILE%\.kitt\signing\acceptance.keystore` 证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，runtimecheck 与标准 Debug/Release 构建验签 PASS；另一台电脑尚未移交密钥且 PENDING。**可以在办公室用已验签环境施工和打包；若当前机器无密钥，先做功能测试与未签名/隔离构建，不得伪称手机同签名包可升级。无需等待另一台电脑验签。**
- 本轮不要操作手机账号/凭据，不要卸载/清数据。新可安装 runtimecheck 只有在同签名、同包名、合法递增 versionCode 时才可 `adb install -r`。标准 `com.kitt.reader` 不是原账号数据空间。

## 产品问题与目标

用户常在新都区重复点击「开始沿途」，现有 `ChapterResearch.cache` / topics 仅存在于单次 Journey 的内存里，`KittRuntime.start() -> loop.reset() -> research.clear()` 清空它们，导致新都区已研究过仍重新花 Tokens 搜索。目标：**首次有真实来源的 Overview 和 Topic 研究成功后保存稳定证据，结束旅程/重新启动应用仍可复用；可信缓存命中后相同身份、相同层级研究请求数为 0。** Director 仍重新判断何时开口/讲什么，不保存固定旁白。

## 最小施工目标与边界

1. **复用现有模型与入口。** 保留 `AreaIdentity`、`OverviewDossier`、`LocalDossier`、`ResearchSource`、`searchedAt` 及 Overview → Topic → Director 流程，不新造知识格式/服务层。增加很轻量的应用私有 `filesDir` 版本化 JSON 持久化（或更简单的可靠等价机制），原子写入，给单条/总体定上限与损坏恢复。不得引入 Room/SQLite、RAG/向量库、NAS、爬虫或离线包。
2. **命中身份准确。** 区县 Overview 与镇/乡/街道 Overview 严格按完整 `AreaIdentity.key` 区分；Topic 按地区+规范主题区分。不能靠相似地名、VPN、公共IP匹配；位置不新鲜时不误绑定旧镇街。跨进程重新进入会有新的 Director 机会，但不能继承上次的 `examined`、pending、已播去重等运行态。
3. **只缓存有依据的成功研究。** 必须有真实已核验来源与可用事实；空/失败/伪来源/Fake、无来源或错误地区不能写进生产缓存。保留 URL/标题、事实、时间戳与必要缺口。落盘失败不影响正常讲述；旧版本/损坏文件不能崩溃，安全回退现场研究。并发同身份请求应避免重复研究和竞态覆写。
4. **时效与证据诚实。** 本地缓存代表“此前有来源的研究”，不能让 Director 对用户谎称“本次刚联网查过”；如现有证据卡文案 `已实际搜索` 容易误解，按最小修改明确历史来源。明确用户要求“查最新/重新研究”、今日开放/票价/天气/路况/临时展览/时事，仍按现有独立实时研究能力处理或诚实说无法实时查证。历史稳定事实不要每次 start 强行 TTL 失效联网。
5. **隐私。** 不缓存原始网页/模型请求/对话/讲述录音/GPS 轨迹/API 密钥或账户信息；必要诊断只保留 hit/miss/write/rejected、研究调用次数和原因摘要。

## 自动与设备验收

- 第一次有可信来源的地区概况研究 → 保存；同一旅程和**结束 Journey → 杀进程 → 重启 App → 同地开始**都命中，区县及镇街分别证明 **0 次重复 Overview 调用**。Topic 也可跨重启复用。
- 关闭/拒绝 Provider 网络后，已缓存稳定资料仍能供 Director 使用；从未研究的地区不能编造；新一趟仍会重新做一次 Director 判断。
- 当前时效信息和显式重新搜索必须走实时路径；坏/空/伪证据、断电原子写入失败、同名不同区域、并发、容量边界、粗位置等负例不污染缓存。
- 运行相关新单测、完整 Debug/Release tests、assemble、lint、签名检查及成都新都→绵阳安州雎水六组模拟。#17、#14 安全护栏和用户打断/安静/ASR/TTS 不回归；记录实际运行/UP-TO-DATE/未测范围，不能复用旧版本数字冒称本版 PASS。
- 如使用真实 ChatGPT Provider 进行新都区内容采样，仅在得到合法本机授权和用户已有可用账号的环境下小规模实施；不得导出凭据，不为证明缓存而反复触发昂贵搜索。未实测就写明未实测。

## 交付与 Stop

发布独立 V0.3.5.2 工程提交及精简验收记录，更新 `HANDOFF.md`、`docs/ROADMAP.md`、#18。能在已验签的办公室环境实际产出同签名 `com.kitt.reader.runtimecheck` 新版 APK时，附包名、versionCode、SHA256、证书指纹、最短原位升级步骤；未完成真实手机升级则标 PENDING。**另一台电脑签名移交属于 #19，保留 OPEN/PENDING，但不作为 #18 代码/单测施工阻塞。**

**只完成 V0.3.5.2，停止等待一次小版本人工验收。不得顺手启动 #13 语义去重、离线包、定位系统重构或下一轮 UI。**
