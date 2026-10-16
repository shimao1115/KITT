# 2026-10-17 用户最新明确授权：Qoder 一次 code18→code19 原签名原位升级 + 有界真机验收（当前唯一任务）

> **优先级最高**：用户对 [PR #35 独立工程评审](https://github.com/shimao1115/KITT/pull/35#issuecomment-6219618938) 中“是否批准 Qoder 将 vivo X200 Pro 上 code18 原签名原位升级至 code19，并执行有限真机验收”明确回复 **“批准”**。本授权**仅解除本段精确范围内**下方历史“禁止设备操作”的STOP，**不授权再次开发/修复业务代码、二次安装、改变账号、合并 PR、发布 APK、解除故事门禁或启动后续版本**。本轮仍由 Qoder 在用户办公室的 `H:/CODEX/KITT/` 本地执行；GitHub 的指令写入不代表设备已自动操作。

## 0. 唯一设备和验签前置（所有项目 PASS 才准一次升级）

1. 先保护电脑真实 worktree：`git status`／`git worktree list`，检查 `main` 与 `feat/v052-story-preferences`，`git fetch origin` 同步**最新**任务卡。不得 reset、clean、覆盖未提交更改或盲目重建 APK。阅读 `AGENTS.md`、本卡、`HANDOFF.md`、`docs/ACCEPTANCE_STORY_INTERESTS.md`、`docs/evidence/v052-verification.json` 和 PR #35 最新评审评论。已审业务实现 commit `d9d9b41b17961c8ed848c12218d3cfb06694ce9e`，交付 HEAD `2a331d999c4cd369ed3ae809b02031915f1a995c`，本授权之后的纯文档提交不能冒充新 APK 工程基线。若本地候选缺失/不匹配 STOP，不现场改代码、重签或重建代替。
2. 只在用户允许使用的**唯一 USB/ADB** 目标确认是 vivo X200 Pro/V2405A、包 `com.kitt.reader.runtimecheck` 当前 **0.5.1/code18**、同原签名、**无正在运行 Journey/前台播报/待恢复状态**且非驾车环境才继续；存在 Journey 或设备不唯一则直接 STOP 报告，请用户自主处理，不强制结束或清历史。**别碰标准生产包 `com.kitt.reader`**。
3. 安装前只读记录必要证据：code18 的包/版本/证书，20份真实旅程历史的**文件数量和本机保密逐文件哈希指纹**，当前普通设置的非敏感摘要与账号加密记录“存在/非空”布尔值；**不输出/上传**账号、邮箱、Token/API Key、密文、整份 settings.xml、旅行原文、设备序列号。可复用之前证明可靠的 `run-as` 只读通道；若无安全通道或发现敏感数据可能泄露，STOP。原 code18 APK 回读记录 SHA256 为 `8fb8b57040787ea82ffd3fb026d230698ac7ab8bde84ab29868b670b32413fea`（这是历史证据，若只查包信息不能把它写成此轮新测）。
4. **仅批准的 code19 原签名本地候选**：`artifacts/kitt-v0.5.2-runtimecheck.apk`，恰好 **74444882 bytes**；SHA256 **`3af6f91a0aba318d4be58d53f0d15c7eef25632a14f2ae7caf98ecf6e08b2a4b`**；包 `com.kitt.reader.runtimecheck`；版本 `0.5.2/code19`；原证书SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**。核验 APK 实际文件大小、SHA、包头、证书与手机旧包的签名一致且版本18→19递增。任何不符立即 STOP，不重签、换包或绕过检查。

## 1. 仅准的一次原位安装

- 全部前置 PASS 后，在仓库根对唯一USB设备运行**恰好一次**：`adb -d install -r artifacts/kitt-v0.5.2-runtimecheck.apk`。这是保数据升级，不执行 `adb uninstall`、`pm clear`、`install -d/-g/-t`、签名替换、包名切换、标准Debug/Release安装或升级失败后重复尝试。失败只记录确切返回并 STOP。
- 安装完成先核 `runtimecheck` 已变 **code19**、签名相同、包/数据目录未重置、20份历史仍逐文件不变、普通设置未见重置、加密账号记录仍存在。任何明显历史丢失、账号记录缺失或非敏感设置意外变化，立即 STOP，不做修复/重新授权。不要将“记录存在/界面显示登录”当做 ChatGPT token 联网可用证明。

## 2. 有界真机 UI/偏好保存验收（只在升级前置及升级结果通过后）

1. 不开启 Journey、不模拟 GPS、不故意触发线上 Director/研究/ChatGPT 请求；正常打开现有 App，检查首页、设置中六类“讲述兴趣”：`NATURE/ENGINEERING/CULTURE/LOCAL_LIFE/HISTORY/TRAVEL`、每类**多讲/正常/少讲**、初次全部**正常**，可滚动看见并点击，设置页同时保留原 Provider/模型/账号状态/音色/语速。检查**实机**竖屏、横屏、长页滚动/返回（若需临时改方向，记录旧状态并结束恢复）；不是平板实机，也不是物理GPS外场验收。
2. 只做一轮**明确保存**：设置 **历史与人物=多讲、交通与工程=少讲**，其余四类正常；点**独立的「保存讲述兴趣」**，退出并重新进入 App/设置，确认两项持久保留；对独立新文件 `shared_prefs/story_interests.xml` 只记录六类键/档位，不能包含密钥。更换选项**但不再点保存**再返回，确认未保存改动不落盘（若可能造成用户误解，只记录 UX 观察）。每项只做必要次数，不反复尝试。
3. 充分区分**保存兴趣对旧文件的隔离**与**打开原设置页可能自动刷新模型/轮换令牌**：本版独立兴趣写入设计不应修改 `settings.xml`，但 code18 曾观察到打开设置页后整份 XML 哈希漂移、根因 **UNKNOWN**。尽可能记录“打开设置页前/兴趣保存前/兴趣保存后/完成后”不同窗口的脱敏哈希对比，**不将整文件变化未经分析直接判账号损坏，也不可凭单次哈希判断已保留全部历史字段**。若观察到非预期漂移，立即 STOP 并只报告事实，不读取密文/账号原文、不主动刷新模型/点旧「保存」。
4. 完成后核实**20份真实历史**逐文件SHA与安装前相同、普通设置六项与升级前有来源的基准一致、原用户账号仍显示原状态；手机原网络、方向/系统设置与测试前相同，没有活跃 Journey。记录每一项 PASS/FAIL/PENDING。**设备自动 UI 点击与截图辅助观察≠用户本人亲手验收**，人手交互、舒适性和账号联网可用性须由用户后续反馈或如实保持 PENDING。
5. 现实正式故事为 **0篇**；杨慎主/延伸 `candidate / visitor_gate=pending` 正式发布仍 BLOCKED。**多类偏好“改变真实已审核故事实际播报优先级”必然 PENDING**，之前只有纯内存 TEST_ONLY 多候选测试。不得注入候选、把手机设置改为调试发布、解锁故事 Gate 或因多讲历史强制播报。

## 3. 证据、权限边界、STOP

- 创建**独立**本次真机证据 `docs/evidence/v052-phone-acceptance.json`，在 `HANDOFF.md` 最顶部追加实机验收结果，Draft PR #35 留下逐项事实、设备/签名/version/候选hash、20份历史/原设置的安全对照、未完成事项及重要限制；原工程1009测试仍是此前 Qoder 实跑的**工程证据**，本次未重跑不得冒称重跑。不公开原始 XML、设备截图中的邮箱、旅程原文、账号凭据、设备唯一标识或任何可用于关联个人的指纹。
- 只允许提交真实设备**脱敏验收文档与交接**，不新增或修改生产 Kotlin、Story/Claim/approval/assets、Gradle构建/证书、原工程证据；工作树或 Git 冲突先 STOP。若有真实体验阻断，提出复现与最小修复建议，本轮不得擅自打新APK或二次安装。
- **执行一次后 STOP**，即便全部 PASS 也不自动合并 PR #35，PR 保持 **Draft/OPEN**，不发布 APK、不修改正式故事审核、不启动 V0.5.3/V0.6.x/六县采集；#23/#24/#27/#30 原外场状态维持 OPEN/PENDING。用户下一步亲手体验、批准合并、正式故事发布分别另行决策。

> 下方为旧“工程交付/尚未授权手机”的历史文本；只有本次用户明确批准的**单次原签名保数据升级+有界验收**覆盖其相关 STOP，其余限制全部保留。

---

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
