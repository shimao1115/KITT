# CURRENT TASK CARD — Signing preflight / Issue #19

**状态：2026-10-12 用户决定先统一办公室/家用电脑的 KITT 签名，再启动已授权但尚未开工的 V0.3.5.2（#18）。当前唯一执行目标是 [Issue #19](https://github.com/shimao1115/KITT/issues/19)。**
**本卡已提交的是构建代码与签名文档配置，不代表电脑上的私钥已迁移或双机验证 PASS。**

## 先继承事实

- 当前手机装的是 `com.kitt.reader.runtimecheck` V0.3.5.1 / code11，已同签名升级并保留 ChatGPT 账号和设置；标准 `com.kitt.reader` Debug/Release 不是该数据空间。
- 手机已安装证书 SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。**不能换签名、不能卸载旧包、不能清数据。**
- 先读 `AGENTS.md`、`HANDOFF.md`、`docs/ROADMAP.md`、`docs/SIGNING_UNIFICATION.md`、#19 以及本卡。当前代码在 `app/build.gradle.kts`、`scripts/check-kitt-signing.ps1`、`scripts/verify.ps1`、`scripts/build-runtime-smoke.ps1` 已有统一签名设计，施工端首先审计、真实执行验证而不是重复改造。

## 唯一工作范围

1. 检查已安装手机 runtimecheck 证书、办公室原验收密钥与家中目标文件的对应关系。仅用可信的本机 `keytool` / `apksigner` 获取证书指纹，不碰 ChatGPT 凭据。证书指纹绝不能替代私钥。
2. 原办公电脑密钥先经指纹匹配，确认是旧版真正签名源，再由用户在两个电脑之间通过个人控制的安全方式复制**相同的完整原密钥文件**到各自 `%USERPROFILE%\.kitt\signing\acceptance.keystore`。不更改各自全局 `~/.android/debug.keystore`，不上传 keystore、密码到 GitHub、Issue、聊天或云端构建服务。若办公室电脑暂不可操作，先自检脚本，剩余仅留一个人工复制步骤。
3. 检查项目专用 Debug 签名配置与 runtimecheck 子工作区、Release 验收签名脚本是否严格使用同一证书。先调用 `scripts/check-kitt-signing.ps1`，不一致时应立即失败而不是签出错误验收 APK。若现有脚本有缺陷，作最小修复并保留文档。
4. 在**办公室与家用电脑分别**构建至少一个同应用包名 `com.kitt.reader.runtimecheck` 的验收 APK，并校验证书 SHA256。只有实际执行并收集脱敏验证证据后才能写双机 PASS。APK 文件字节不必一致，证书必须一致。版本号必须上升才能测试原位升级：本轮不为签名测试擅自伪造新版本或降级安装已有 0.3.5.1。不得误用标准包名覆盖现有 runtimecheck。
5. 本轮工程变更只限工具链、签名检查、安全复制指导以及必要的基础构建/回归。**严禁先实现 #18 缓存代码，也不做 #13、离线包、定位改造。**

## 交付 Gate

- A: 办公室密钥证书正确 PASS；
- B: 家庭密钥证书正确 PASS（实际完成安全复制）；
- C: 两机 runtimecheck APK 的证书均是手机目前的 SHA256，且包名、versionCode 规则已验证；
- D: 无私钥泄漏，旧 Android 工程全局 Debug key 未被覆盖；
- E: 构建 / 脚本完整执行情况与未测部分、提交、最短安装说明已写 HANDOFF。

**缺少 B/C 的任何一步，只能标记“配置已就绪，实际双机签名统一待验”；绝不能提前宣称完成。** 
确认 #19 后，将 [#18 原任务](https://github.com/shimao1115/KITT/issues/18) 恢复为当前施工卡。#18 的需求与验收已由用户先前明确授权，勿因为签名前置工作丢失内容。施工里程碑之间要明确止步界限。

---

## 已授权的下一任务（暂不执行）

# CURRENT TASK CARD — KITT V0.3.5.2 / Issue #18

**状态：用户已授权本小版本自主施工；当前唯一 Destination 是“跨旅程的地方研究证据缓存”。这只是任务下发，不代表施工 Agent 已运行或交付。**
**正式需求 / 完整验收：** [Issue #18](https://github.com/shimao1115/KITT/issues/18)。
**施工顺序：** V0.3.5.1 已按“工程通过、真机部分待验收”阶段性收口 → **现在做 V0.3.5.2** → 小版本用户验收 → #13 语义去重 → 新都区离线包试点。不得自动进入下一个 issue。

## 0. 施工交接和已完成边界

先读 `AGENTS.md`、`HANDOFF.md`、`docs/ROADMAP.md`、Issue #18、最近 commits 和 `docs/ACCEPTANCE_AREA_STABILITY.md`；继承当前 main（已交付 0.3.5.1 / code11），勿重新开发 V0、勿拆改经过测试的 Golden Path。历史根文档中“0.3.5.1停止、不开展#18”的文字是上一个里程碑的 stop rule；**本张已由用户明确授权的新任务卡替换那个停止条件，但其保留的缺测/安全约束仍适用。**

V0.3.5.1 工程提交 `74161cf`、设备交接 `e87c6be`；室内自然600秒观测只有305秒定位新鲜、此期间名称消失0，尚无连续镇街确认。**不能称完整真机 PASS**。#17 保留待补充手机验收、#14 保留定位新鲜度/回切外场问题。本轮不得擅自延长过时位置有效期、改FUSED、重新定义区域来源，不能“修好”位置问题作为开始缓存的前提。

## 1. 用户的痛点与唯一目标

用户反复在新都区点击「开始沿途」，App 每次重新联网研究同一地区，重复花搜索时间和 Tokens。现有 `ChapterResearch` 的 `cache/topics` 是会话内存，`KittRuntime.start() → loop.reset() → research.clear()` 会清空；已经拥有 `AreaIdentity`、`OverviewDossier`、`LocalDossier`、`ResearchSource` 和 `searchedAt`。

**目标：同一个经过真实搜索、有有效来源的地方 Overview/Topic，研究成功一次后能在之后的 Journey 和 App 重启中复用，命中时发起 0 次同范围重复研究网络请求；Director仍然独立决定本次讲什么、如何讲、是否安静。** 不缓存语音播报，不把“本地已有证据”冒充“本趟刚联网搜索”。

## 2. 实施边界——黄金路径先跑通

1. **最小、持久、可核验的证据存储。** 优先使用已有应用私有 `filesDir` 下原子写入的版本化小 JSON（或等价现有机制），按完整 `AreaIdentity.key` 分开储存区县和镇乡街道 Overview、按区域+专题规范键存 Topic。保留必要类型字段、原始可信来源URL、标题、搜集时间、事实摘要、缺口；设置单项和总容量上限、损坏/旧版本容错。不要先上Room/SQLite、服务器、NAS、地图包或新中间层。
2. **在现有研究链入口先读缓存**，命中有效数据则恢复合规 `OverviewDossier/LocalDossier` 及相关 Director 机会；未命中才调用 `LocalResearchProvider`，并在成功通过当前事实/来源验证后保存。会话结束只重置运行态，不删除长期研究证据。启动同一区县和同一街道不应形成重复请求风暴，并发请求应按现有节流/去重保护。
3. **严格区分证据来源与时效。** 仅保存可溯源的成功研究；不信任空结果、失败结果、缺来源、Fake/模拟伪来源；测试可用受控可信 fixture 验证落盘和复用，但不污染真机真实知识缓存。用户主动要求“最新/重新查”，或询问天气、交通管制、票价、开放时间、临时活动等，必须走适当实时渠道或明确不能实时确认。缓存只为稳定地方知识；不靠固定短 TTL 让每次启动再做同样研究。避免说出“本趟已经查阅网站”之类误导表述。
4. **缓存是证据，不是旁白。** 启动新 Journey，正常走一次本次 Director 判断；不能继承上一趟的 `checked`、PREPARE、去重记录、未完成后台任务等运行状态，也不能跳过新旅程应有的讲述机会。#13 同一旅程的语义重复讲述另案解决。
5. **行政区真实性优先。** 当前位置仅来自设备 Android GPS+NETWORK，地区名称仅来自受约束的逆地理编码结果。缓存键不能使用 IP/VPN/代理地理位置；相同区名但不同行政实体不得误命中。面对仅区县识别、定位过时、GPS↔NETWORK变化时遵守现有失效/精度边界。
6. **不暴露私人资料。** 不持久化GPS轨迹、用户对话、原始 prompt/网页正文、账号/密钥、音频、上次讲述全文。日志统计命中/未命中/网络调用/跳过的计数与状态即可，不泄露坐标或凭据。

**允许实现位置：** `LocalResearch.kt` 的 `ChapterResearch`、必要的 `KittRuntime/DirectorLoop` 接线、一个极轻量持久化工具与相应测试；如现有序列化足够直接使用，不创“知识服务分层”。请自行采用最短可测路径，不要反复向用户征询实现细节。

## 3. 必须实际完成的验收门槛

- **首次真实查证：** 区县与街道 Overview 成功且来源有效才写入；二者严格分开；专题也可独立持久化。有证据的对象与 sources 完整可回读。
- **重复开始：** 结束旅程 → 进程终止/重启 → 同地区开始。命中区县/街道的已完成概况，**0 次重复 Overview 研究请求**；Topic同理；但 Director 的新旅程判断仍执行。
- **离线复用：** 关闭研究 Provider/无网络，先前成功保存的稳定证据仍能被读取；无缓存时不能编造地方事实；当前性问题不能拿缓存当实时证据。
- **负例：** 不同行政区同名、损坏/旧 Schema 文件、应用进程意外终止、写入失败、空/无来源/低质量/FAKE研究、章节变化、反复 start/stop、并发查询以及容量限制均安全回退，不崩溃、不泄露账号。
- **回归：** #17 静止地名与安全失效、#14 真实定位新鲜度边界、#15 状态、Director/Overview→Topic、安静/用户接管、成都新都→绵阳雎水六组模拟，不因缓存新增错误章节或失去应讲机会。
- **工程门禁：** 实际执行 `scripts/verify.ps1 -Offline` 和必要的Debug/Release tests、assemble、lint、签名检查，提供可重跑的“首次搜索→结束→重启→命中→零重复请求”计数测试。无真机/在线账号时明确“尚未实机/真实模型验收”，严禁虚报省 Tokens 百分比。此前报告的Debug349/Release336只是基线，不能直接当本版本结果。

## 4. 交付与停止

完成后提交 clean working tree 的 commit，更新 `HANDOFF.md`、`docs/ROADMAP.md`、独立简短验收记录、Issue #18 进度、APK/下载位置与 SHA256（若可交付），给出新版安装和最短手机验收步骤。

保留已安装的 `com.kitt.reader.runtimecheck` 和 ChatGPT 登录配置：有且仅有原证书时才声称能够 `adb install -r` 原位覆盖，不能要求用户卸载 App、清数据；拿不到签名凭证要明说缺口。

**Stop Rule：只施工 V0.3.5.2，完成后停止等待用户做一次小版本验收。严禁顺带施工 #13、离线包、位置/Geocoder大改或其它 UI 功能。**

