# KITT V0.5.2 / code19 — 唯一施工任务卡：六类讲述偏好 + 柔性排序

> **2026-10-16 工程交付 / STOP**：本卡第 1—3 节的六类×三档设置、独立持久化与门禁后柔性排序已实现（`d9d9b41`，[Draft PR #35](https://github.com/shimao1115/KITT/pull/35)）；本轮重跑 Debug511 + Release498 = 1009 项零失败/错误/跳过、Python7、六组模拟、构建/lint、原签名2接受+6拒绝，并产出原签名 code19 **本地**候选（74444882 字节，SHA256 `3af6f91a0aba318d4be58d53f0d15c7eef25632a14f2ae7caf98ecf6e08b2a4b`）。第 2C 节来源双模式按停止门槛判定 **DEFERRED**，未实现、未测试、无假开关。第 1 节黄金路径中的“真实多类正式故事被偏好改变次序”受限于**正式故事0**，如实记 PENDING。详见 [HANDOFF](HANDOFF.md)、[证据](docs/evidence/v052-verification.json)、[验收说明](docs/ACCEPTANCE_STORY_INTERESTS.md)。手机未访问/未安装，PR 未合并，APK 未上传；下方原文仍是本版施工与安全的唯一依据，不因交付而放宽。
> **2026-10-16 用户授权启动下一小版本**。V0.5.1/code18 [PR #31](https://github.com/shimao1115/KITT/pull/31) 已合并 main（merge `9319c271f878000f79a71be92df7bee6ec92605f`）。本版唯一工单：[Issue #34](https://github.com/shimao1115/KITT/issues/34)，施工分支 `feat/v052-story-preferences`，验收出口为此分支独立 **Draft PR**。该授权包括在办公室电脑开发、全量测试、构建有签名的**本地候选**和提交 PR；**不包括手机操作/安装、合并 PR、正式发布故事、上传/发行 APK、启动 V0.6.x**。
>
> **绝对事实基线**：code18 在 vivo X200 Pro 原签名原位升级成功，用户亲手检查探索 UI 后说“我没有发现什么问题，都是正常的”；20/20真实历史哈希不变。原 Debug489+Release476=965 工程通过，但这是 code18 的旧结果。原 `settings.xml` 整体 SHA256 漂移根因仍 `UNKNOWN / INSUFFICIENT_BASELINE`；六项普通设置匹配旧证据不等于账号在线Token已经验证。标准**正式完整故事0**；杨慎两篇 `candidate / fact_gate=passed / visitor_gate=pending`，独立试听人工PASS不构成Visitor正式批准。真实GPS外场#23/#24/#27继续PENDING。最新已合并 [HANDOFF](HANDOFF.md)、[路线图](docs/KITT_CURRENT_ROADMAP.md) 为真相来源；本文件以下授权高于旧历史STOP文字，不覆盖其安全约束。

## 1. Golden Path（可直接跑通的最短验收）

**旧用户正常升级且资料不丢 → 打开设置 → 六类兴趣每类显示“正常” → 将“历史与人物”选为“多讲”、“交通与工程”选为“少讲” → 点击清楚的保存动作 → 重新打开应用设置，两个选择仍在 → 其余四类正常 → 合法且同等条件的已审核故事候选出现时，柔性排序优先考虑“多讲”而非“少讲” → 不匹配区域/位置不可信/门禁未过或已播则仍不能进入候选集。**

对现在的**标准正式故事0**如实显示「兴趣偏好将在有合格的多类故事时影响自动讲述；当前没有已批准的完整故事」等简短说明，不虚构“偏好已使杨慎讲更多”。所有偏好与探索阅读、手动点播解耦，**用户明确主动请求/原文重播/独立延伸入口不按自动选题偏好过滤**。

### 六类固定主类别（严格枚举，非旧 TopicFamily）

| `StoryCategory` | 游客标签 |
| --- | --- |
| `NATURE` | 自然地理生态 |
| `ENGINEERING` | 交通与工程 |
| `CULTURE` | 人文文化社会 |
| `LOCAL_LIFE` | 物产产业生活 |
| `HISTORY` | 历史与人物 |
| `TRAVEL` | 旅行现场与常识 |

每类三个选择：`MORE`（多讲）、`NORMAL`（正常）、`LESS`（少讲），**六类初值全部 NORMAL**。先用已审 Narrative 的单一 `primary_category`；只有模型/内容 Schema 已安全支持且不会破坏门禁时才考虑 secondary_categories，不能拿 `TopicFamily` 猜或映射到六类。**偏好是软权重/选择倾向，不是硬配额、强制轮询或筛除：少讲≠禁讲、多讲≠超越事实/版权/位置/节奏门禁**。

## 2. 有界工程实现

### A. 设置与持久化
1. 复用 `SettingsScreen.kt`/现有 Android Compose 样式：可滚动、清楚的“讲述兴趣”分组，六个并列类别，各自三档选项，中文标签直观；避免重做整个设置页、不要在驾驶页增加六个控制或引入复杂导航。保留最主要驾驶按钮、登录、模型选择与TTS行为。
2. 新增**独立、命名明确**的兴趣存储键/值（可用已有 SharedPreferences，但最好专用偏好持久化入口），缺项→NORMAL；容错非法值→NORMAL且不崩溃；明确的保存/取消语义，返回不意外覆盖用户账号或旧偏好。新偏好保存**不能复用 `SettingsStore.save(config,rate,voice)` 触发旧账号 `credential` 重新加密或其他无关字段写入**。不得修改已存在 `chatgpt`、`provider`、`model`、`effort`、`tts_voice`、`speech_rate`、`research_credential` 等键，不尝试解决历史 XML 漂移的 UNKNOWN。
3. 无新存储 Migration 要求，兼容 code18 设备升级保留旧配置；20份历史以及 `TripStore` 旧 rating 字段仅兼容、不迁移、不清除。修改偏好不要启动 Journey、改当前 GPS 或重置已有语音生命周期。

### B. 柔性候选排序
1. 先阅读 `StoryPacks.kt`、`OfflinePack.kt`、`OfflineNarrator`、`DirectorLoop`、`Journey` 的真实入口。当前 `StoryPacks.select` 仍对杨慎单主篇+延伸带特殊限制，**不要为做六类演示提前发明一整套多县通用包加载器或放开这条身份限制**（V0.6.0再重构）。
2. 仅在**当前合法自动故事选择入口**加入清晰、可注入/测试的轻量兴趣排序函数：先过 **来源/内容 approval + Fact/Visitor Gate/版权/地区/可信位置/已播/节奏/独立延伸** 等既有资格约束，再做类别软排序。排序完全不凭偏好让原本不合格的候选入场。
3. 本版已发布0篇，生产链无法现场真实比较多类别：允许纯内存 **TEST_ONLY** 合法批准、多类别、同区域/合格位置/相近价值的候选集，通过单测验证 `MORE > NORMAL > LESS` 的相对排序、完全 NORMAL 的稳定旧顺序；未过门禁/不匹配地区/已播/被用户明确指定的单篇独立处理。**不要修改 `content/yangshen` 审批、Claim、正文、现有标准资产、manifest，也不准注入手机APK**。不要为了多类别测试随意去掉 `stories.count(main) <= 1` 的现行数据门禁。
4. 若当前自动链根本没有任何可供偏好影响的多个候选，本版只应如实交付**可靠保存和已接入可复用候选排序的基础设施**，把“正式多类内容真实播报受偏好影响”列 PENDING，不能冒充真人真机已验证。

### C. 来源模式（本版可选，带清楚停止门槛）
- 先核查 `DirectorLoop.preferOffline`（真机默认离线优先、模拟例外）、`online()`、现有 Research/Provider 的证据路由以及 `OfflineNarrator` 的失败回退。
- **只有**在不动 ChatGPT 授权、刷新、研究缓存大结构、不改变位置门禁/内容 Gate、且可用现有小范围接线完成“离线优先默认／即时优先用户主动选择／无网络或失败时有界回退本地”的可测试闭环时，才实施来源模式的两档开关。
- 如果需要全新「从本地Fact进入AI提示词→查网补充→动态组织」管道、多县资料索引、失败重试调度/大重构或真账号联网验收，则此版**不实施**，在 HANDOFF/PR 明确记为 `DEFERRED`，建议独立 V0.5.3 或既定 V0.7.x；禁止做只有 UI 切换但实际失效/绕过本地证据的伪功能。不要让设置改动触发 ChatGPT 自动刷新模型/Token 的额外写入。
- 即时优先 **不是实时准确**，不能用大模型记忆替代已审证据；缺可靠位置不自动讲；网络失败不能阻断离线知识/朗读。搜索用户明确询问的实时问题仍要求实在的新来源。

## 3. 测试和交付

**必须本次真实执行**：Debug/Release 全量测试（分别记录tests/failures/errors/skips，严格零失败/错误/跳过，不能沿用965）、六组确定性/Fake模拟、Python内容门禁、标准 `--require-playable` 正式0预期 BLOCKED、构建/lint、原签名2接受+6拒绝和版本 code18→code19 **静态**同包同证书兼容校验。若双模式未实施，则不要宣称测试了该模式的真实网络行为。

专项至少覆盖：
- 六类×三档的默认、持久化、异常值、旧设置无新键回退；设置屏幕在手机竖屏/横屏、Tab S9横屏、小屏/大字号可滚动、按钮可点击。
- 更改一类不会改其它五类、更不会改 Provider/模型/声音/加密账号字节、20份**合成**历史/实际GPS/Journey/已播计数（不得直接去改真实手机）。
- 仅真正正式审核并合格的故事参与排位，软排序不跨 Gate；全正常保留确定性旧序；多讲/少讲不硬排除；用户明确手动请求不被偏好屏蔽；候选杨慎两篇仍 BLOCKED。
- 由在内存中构造的6类已审核候选和不合格候选演练，不更改 production APK 标准故事=0。
- 若加入来源双模式，再测默认离线、不配置云/网络不可用/服务失败限次及明示回退，不新增假GPS或假联网检索。

`versionCode=19`，`versionName=0.5.2`；构建**仅本机**原签名 `com.kitt.reader.runtimecheck` code19候选及标准包的可复现证据，报告真实大小、SHA256/证书、已装code18→候选code19静态兼容。保留 `docs/evidence/v051-settings-drift-analysis.md`、`v051-current-settings-readonly.md` 与旧 HANDOFF 的历史记录，不改旧证据；更新本版 HANDOFF / 新证据 / Draft PR / 本次测试真实计数；自检工作树干净与无密钥/截图/用户历史敏感内容意外入Git。

## 4. 执行纪律和 STOP

先在办公室电脑 `git fetch origin`，核 main HEAD 已含 PR31 merge，查看所有当前 worktree、`git status` 并保护未提交修改；只在 `feat/v052-story-preferences` 正确施工分支开展，遵守 [AGENTS.md](AGENTS.md)、[HANDOFF.md](HANDOFF.md)、[Issue #34](https://github.com/shimao1115/KITT/issues/34)、新 Draft PR 的唯一范围；不触碰 [Draft PR #29](https://github.com/shimao1115/KITT/pull/29) 的县域工厂分支。

**到工程交付即可 STOP**：不调用 adb/scrcpy、不操作/卸载/更新手机 code18，不合并PR、不发Release，不宣布故事正式发布/开启 Visitor Gate，不关闭 #23/#24/#27/#30，不创建 KITT-PACKS、不做区县下载器、不开展六区县采集，也不启动下一小版本。向用户交付一次可核对的工程验收报告，用户另行决定代码合并和真机升级。
