# CURRENT TASK CARD — KITT V0.4.2 / Issue #23 结束旅程页面精简

**2026-10-14 当前工程结果：V0.4.2/code16 已完成，STOP等待用户明确批准真机升级。** 独立分支 `feat/v042-simplify-end-summary`，唯一目标 [#23 删除五项评分](https://github.com/shimao1115/KITT/issues/23)，实现 `d64adf8`；评分/评语/保存接口已移除，自动纪要/旧JSON/直接返回保留。最终Debug446/Release433共879项、failure/error/skipped0，六组模拟、构建/lint、原签名及runtimecheck APK PASS。首轮Debug1个旧超时测试调度失败已修订并两Variant完整重跑。见 [HANDOFF](HANDOFF.md)、[证据](docs/evidence/v042-verification.json)、[真机步骤](docs/ACCEPTANCE_END_SUMMARY.md)。本轮未操作手机/发送真实云请求；code16真人全部PENDING，Issue23保持OPEN，Draft PR不合并，#24外场PENDING不改写。**不据下方开发授权重新施工或自动安装；工程验收已结束，等待新指令。**

> **STOP 边界**：本轮只做 UI/旧评分接口的必要清理，完成工程构建与签名产物后 STOP 请求用户确认。**本句“继续”仅授权开发、测试及准备验收 APK，不视为已经授权对手机执行 `adb install -r`**。用户当前手机上是已验收的 `com.kitt.reader.runtimecheck` V0.4.1/code15；不可卸载、清数据、换签名、擅自替换标准包、合并 PR、关闭 Issue 或进入 V0.5.0。V0.4.1 [Issue #24](https://github.com/shimao1115/KITT/issues/24) 真实断网和恢复外场 PENDING **继续 OPEN**，不得改写。

## 开发起点与最小需求

先读本卡、`AGENTS.md`、`HANDOFF.md`、[路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md) 和 [Issue #23](https://github.com/shimao1115/KITT/issues/23)；以分支当前实际代码为准。最小黄金路径：**点击“结束旅程” → `KittRuntime.end()` 自动将轻量 `TripSummary` 写入 `TripStore.finish()` → 展示简洁 `EndScreen` → 点击“回到出发页”**。现有完整链路无需任何评分按钮即可保存。不得因此修改写入时序、旅程恢复与持久化语义。

1. 在 `app/src/main/java/com/kitt/reader/EndScreen.kt` 删除五项 1—5 分问卷及其默认全 3 分状态、`想说的话（可选）` 输入框、`保存评分` 按钮、保存成功/失败提示及专用状态与交互。**不要把五项替换成六类满意度评分**。
2. 保留 **“这一程，读过的山河”**、时长、跳过次数、已经讲过的主题、无主题时的安静文案及 **“回到出发页”**。已有目的地**若由用户明确提供则可显示**；`未询问`、`未提供`、`等待回答` 这类技术状态不应作为旅程目的地展示；不从 GPS 推断或伪造目的地。界面在手机竖屏、小横屏/平板上不需额外复杂滚动。
3. 让 `EndScreen` 无需依赖评分 `TripStore` 实例（若删除 `store` 参数则同步更改 `MainActivity.kt` 和 Compose 测试），不影响 `KittRuntime.end()` 的 `store.finish()` 自动保存。**本次不增加保存纪要按钮或数据库迁移**。
4. 可以在全仓确认调用只剩旧测试后清理 `TripStore.feedback()` 死接口，**但严禁擦除、迁移或改写用户本机已有 `trip-*.json`**；历史文件可能有 `ratings`/`feedback` 可选字段，`TripStore.latest()`、恢复/读取必须继续忽略额外键并正确返回 `TripSummary`。优先小改，必要的遗留接口保留不影响最终 UX。
5. **不动主核心功能**：离线优先自动讲述、联网零自动 Director/Research 调用、目的地不主动追问、GPS 可信门槛、Android 中文离线 TTS、ASR、主动在线回答、静音/打断/后台服务/已播记忆，及六类偏好/双层知识包/离线更新/探索地点均不属于本版。

## 必须做的最小回归

- `DrivingUiTest.kt` 中 `EndUiTest.endScreenSavesRatingAndReturns` 是**旧评分设计的正向断言**；替换为**简洁结束页显示 + 评分元素确实不存在 + 返回生效**，并验证已有 `TripSummary` 内容仍完整且保存自动完成。验证显式目的地与 `未询问` 时的不同展示、无已讲主题的安静文案。
- `PersistenceTest.kt` 中 `summaryRatingRetentionAndCorruptRecovery` 正在直接调用旧 `store.feedback()`。**绝不能删掉这条历史兼容性验证**：改为手工构造/读取**带遗留 `ratings`/`feedback` JSON**，确认旧记录依然可被读取且不会因新版 EndScreen 自动改写/抹去；单独验证现在不依赖评分也会 `store.finish(summary)` 并保存纪要。仍验证损坏 `current.json` 恢复安全。
- 其余导航、竖横屏、Journey-end、旧设置、自动播报本地优先、安全签名和原 V0.4.1 869 项测试都应全套回归。不能删除失败测试、改生产定位/冷却阈值换 PASS；如果新增测试，报告新旧真实计数。
- `app/build.gradle.kts` 计划递增为 **V0.4.2/versionCode16**，先核对当前实际 `main`/手机 code15；如版本已更高，先报告并合法递增，不可覆盖更高版本或复用旧 code。
- 办公室 JDK17 / Android SDK35：运行 `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Offline`（Debug＋Release 单测、assemble、lint、签名）与 `powershell -ExecutionPolicy Bypass -File scripts/build-runtime-smoke.ps1 -Offline`、`scripts/test-kitt-signing.ps1` 等现成原签名核验脚本。完成既有六组模拟回归。不能把 UP-TO-DATE 的旧单测充当当轮新执行测试。
- **唯一能覆盖现有手机账号数据空间的候选是**同原证书 `com.kitt.reader.runtimecheck` code16 验收 APK，非普通 `com.kitt.reader` 标准包。原证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；校验候选 APK 的包名、versionCode、唯一证书、SHA256、字节数并存脱敏结果。不能生成替换密钥、导出或提交私钥/账号凭据。
- 如果工程绿灯：构建 Android instrumentation APK（若现有工具链适用），补一页最短真人验收：原位升级后“结束旅程”不出现评分、正常返回、自动纪要/旧记录/账号设置仍在；V0.4.1 联网本地讲述零云调用不退步。**这里只需工程候选，先不要 ADB 安装手机**。

## Git / 汇报 / STOP

Codex 可以基于此分支自主施工、运行测试、修复回归、提交推送，并在有代码更改后开启 **Draft PR → main**，更新 [Issue #23](https://github.com/shimao1115/KITT/issues/23)、`HANDOFF.md`、相关简要验收记录以及路线图，不改写 V0.4.1 外场 PENDING。若本地已有未提交文件，先保存/解释而不是强行覆盖；同步远端 `main` 时注意当前分支已包含合并后的 code15 基线。

报告精确 **版本/commit、Debug/Release 新实际测试计数与 skipped/errors、六组模拟、build/lint、原签名检查、APK 包名/大小/SHA256、待真人项目**。然后 STOP。**需要用户后续明确批准才对手机执行安装，之后真实验收通过再讨论合并**。

---

## 已归档的上轮施工上下文（勿重复执行）

# ARCHIVED TASK CARD — V0.4.1/code15 阶段性交付已合并（2026-10-14）

**状态：STOP / 已阶段性接受。** 用户确认“剩余外场测试以后补做”；[PR #25](https://github.com/shimao1115/KITT/pull/25) 已合并进 `main`，merge [`65ec2726`](https://github.com/shimao1115/KITT/commit/65ec2726e0fe173ff431db8ecfebade7c645d179)。工程 441 Debug＋428 Release = 869，零失败；真机 code15 原位升级、联网新都可信 NETWORK 自动本地朗读零云调用、不主动问目的地及中文耳听通过。**Issue #24 继续 OPEN 以追踪真实断网新鲜定位下地方讲述、重播/本地问答及云失败后的恢复等外场 PENDING。** 不以本轮测试/合并取代外场验收。

**当前手机已安装 `com.kitt.reader.runtimecheck` code15，不能重装/卸载/清数据。** 下文原先“等待批准/禁止合并”是本轮施工前及验收中的历史约束，合并行为已按用户后续明确授权执行。新版本必须重新授权、另立单一任务；此卡不再是活跃施工指令。下一个候选才是 [#23 去五项评分](https://github.com/shimao1115/KITT/issues/23)，**尚未启动**。

# CURRENT TASK CARD — KITT V0.4.1 / Issue #24 本地原生讲述热修

**2026-10-14最新执行状态：code14→15原签名真机原位升级成功，本轮验收结束，STOP等待用户决定。** 联网真实NETWORK→新都本地讲述、0自动云调用、不问目的地及首次真人耳听PASS；120秒联网109秒可信NETWORK/自动0云、主动问题单次FAILED已短句结束；断网120秒没有新鲜物理位置，完整地方链PENDING但正确未知/安静/0云；独立断网中文试听、实际播报按钮打断、默认10分钟静音/提前退出、结束/账号设置保留分别PASS。手机最终出发页，服务停止，原Wi-Fi关/流量开已恢复。不卸载/清数据、不合并PR/关闭Issue或开始下一版本；**已是code15，不要重复安装**。详见 [HANDOFF](HANDOFF.md)、[真机证据](docs/evidence/v041-phone-acceptance.json)。工程Debug441/Release428等结果仍是安装前实际执行证据。

## 2026-10-14 下午：用户已明确批准真机原位升级与验收（当前生效）

用户刚刚明确答复**“允许”**：授权办公室 Codex **将原手机已安装的 `com.kitt.reader.runtimecheck` V0.4.0/code14 原位更新为 V0.4.1/code15，并完成 [docs/ACCEPTANCE_LOCAL_FIRST.md](docs/ACCEPTANCE_LOCAL_FIRST.md) 的真机验收**。此许可替代本卡上方“STOP 等待手机安装确认”的**安装与验收**部分；**不解除**不卸载、不清数据、不泄露凭据、不合并 Draft PR #25、不关闭 Issue #24、不开始 #23 或 V0.5.0 的其他 STOP 约束。

**以下授权已执行完成，保留作审计，不要据此重启安装或验收。** 本轮后续需等用户新指令。执行时的安全技术门禁如下：

1. 在办公室 Windows 仓库最新 `fix/offline-native-no-destination-question` 上确认工作树清洁、原 APK 路径确实存在：`artifacts/kitt-v0.4.1-runtimecheck.apk`（本机 `H:\CODEX\KITT\artifacts\kitt-v0.4.1-runtimecheck.apk`）。重新核查 SHA256 **`8beac9f79e4adcf8a31f95b742562ea393d996c337be3e1ee87cc8c2e58ce43f`**、长度 **74,369,318 字节**、包名 **`com.kitt.reader.runtimecheck`**、版本 **0.4.1/code15**、唯一原证书 SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**。用 `scripts/check-kitt-signing.ps1 -ApkPath artifacts\kitt-v0.4.1-runtimecheck.apk -ExpectedApplicationId com.kitt.reader.runtimecheck` 等现有检查；**不能使用 `com.kitt.reader` 标准包代替**。
2. 取得手机的**实际当前状态**：ADB 仅在设备授权、安全连接后查询；核对真实安装包名、现有 versionCode **应小于15（预期14）**、签名兼容、设备可通信；不凭 0.4.0 历史记录猜当前版本。若已是15或更高、设备/证书不匹配、APK 路径/哈希不符，**停止安装并报告**，不要覆盖、降级、重签或重建密钥。
3. 只有以上均满足才执行**单次** `adb install -r artifacts\kitt-v0.4.1-runtimecheck.apk`；若 vivo 弹出安装或 USB 授权，等用户在手机上亲自批准；失败时**不进行** `adb uninstall`、`pm clear`、降级、解锁/提取账号或任何绕过安装保护的操作。安装后独立回读实际包名/code15、必要时验证装机 APK SHA256、首次安装时间/用户设置不变，记录脱敏结果。
4. 真机验收严格分开：正常真实 GPS/NETWORK 新鲜且新都可信几何内时，**有移动网络/ChatGPT 不可达**和**双无线断网**分别确认无需先联网即可本地中文讲述、开始不问目的地、自动云 Director/Research **调用增量0**；另验本地有限问法/重播、skip、打断、安静、后台/恢复、在线主动问题失败后的本地恢复、账户及 TTS 设置保留。不要为验收破坏账户/代理配置或授权状态；不存原始 GPS 轨迹、凭据、聊天正文。
5. 若办公室得不到真实新鲜 GPS：不猜坐标、不注入模拟位置、不降低 15 秒/50 米与范围几何边界门槛。可验证安装、语音引擎、设置和 UI，但**真实位置→自动离线讲述及有网零云调用真人听感仍记 PENDING**，等待安全的室外/行车后验。
6. 用新**手机实测**更新 HANDOFF、Issue #24、PR #25 与脱敏证据（不能用旧 Robolectric 的 0 云计数顶替），完整列明 PASS、PENDING、BLOCKED，并推送提交。**完成验收后立即 STOP**；PR 仍 Draft，未经新授权不合并、不进入后续版本。

> **本任务替代本分支中的旧 V0.4.0 #20 任务卡；主干的 V0.4.0 历史工程交接仍保持原样。** 当前唯一可施工目标：[Issue #24](https://github.com/shimao1115/KITT/issues/24) 和 [Draft PR #25](https://github.com/shimao1115/KITT/pull/25)。请先读本卡、AGENTS.md、HANDOFF.md、docs/ROADMAP_OFFLINE_FIRST_NEXT.md、docs/KITT_KNOWLEDGE_STANDARD.md。旧 #20 的工程已交付，真实物理 GPS 等待验收部分仍 PENDING，不重新做或改写其结论。

## 本轮目标与严格范围

**联网也本地直接开口，不先等待 ChatGPT；“开始沿途”也不先问目的地。** 作为下一小版本 **V0.4.1 / versionCode 15** 的候选计划；如实际主干已经有更高版本号，先按当前代码校验再选用合法递增版本，不可降级/复用 versionCode。

1. 普通**真实物理定位**旅程：先判可信新鲜 GPS/NETWORK、当前已安装新都地区包与范围，再用本地 OfflineNarrator/后续 Story Selector 决定是否出声，Android **已安装、无需互联网的中文 TTS**直接播放。Wi-Fi、流量、代理、ChatGPT 连接或模型列表**均不是自动播报的启动条件**。即使网络可用也不调用在线 Director/Research/ChatGPT 的自动路径。当前本地故事较浅属于 V0.5.0 的下一任务，本轮不扩内容包。
2. 有可信物理位置但未覆盖本地地区知识：可以安静、如实标识“当地暂无资料”，**不能偷偷转入自动云端旁白**；未知/不新鲜 GPS 更不能猜地址。模拟模式仍保留既有 Fake Provider / 专用六组回归，不拿模拟替代真机。
3. **主动提问**：“这里有什么”“再讲一点”“再说一遍”在有本地资料时应直接从本地响应；主动问没有本地证据的开放式问题、图片或今天路况/天气时按现有在线可用条件走 AI/实际可信来源，无法在线则短句说明，但**不能阻断下次本地开口**。当前未实施“即时优先” UI，**不在此热修捆绑新增模式或六类设置**。
4. **取消目的地追问**：普通开始不主动问“今天准备去哪儿？”；如用户主动提“去绵阳”等，继续把目的地作为可选背景；“添加路线参考图（可选）”不删除。保留真正需要答复的其他澄清问题，但后台或驾车安全门禁不松。
5. 可信已安装**离线行政区几何优先于在线 Geocoder**（即使移动数据打开）；覆盖不到的地区仅按旧有安全地理规则降级，不允许从 IP/代理或旧街道推断现位置。
6. 常规本地叙述时 TTS 优先真正离线中文声音；云端上次失败/超时状态不能覆盖本地运行健康提示或留下全局阻塞。保留 skip/quiet/replay/打断/前台服务/已播记忆和恢复逻辑。

## 当前候选改动供检查，不是 PASS

分支 `fix/offline-native-no-destination-question` 已包含 DirectorLoop、KittRuntime、RealLocationSource、Journey、DirectorProvider、LocationContext、RuntimeStatus 和相应 Unit/Robolectric/模拟测试的最小改动。**先完整 Review 候选，不假设此前远端文档级修改一定编译、不会回归。** 重点检查：

- `preferOffline` 只用于生产真实物理旅程，模拟 provider 回归依然可控；不应从网络状态决定是否讲本地知识。
- 在线情况下 `RealLocationSource` 的本地几何优先、边界退让、LAST_KNOWN 不误认，以及离开新都时不会保留过期地区。
- `OfflineNarrator` 的有限已支持问法与明确开放问题的在线分支不被误合并；离线条件下不使用云端 TTS。
- `ASK_USER` 禁目的地在所有实际入口/提示词/旧测试中都一致；用户主动告知目的地并没有损失。
- `app/src/main/java/com/kitt/reader/RuntimeStatus.kt` 的失败状态、`DirectorLoop` 取消/并发和章内研究机会不会复活旧云请求；假 TTS 完成/旧无网语音试听不能冒充真人沿途 Gate。
- PR 相关测试和其余未改的历史测试都必须实际执行，任何失败按原因修复并重跑，不删除测试/放松可信定位阈值求绿。

## 办公室 Codex 的执行顺序

1. **检出 Draft PR #25 的分支**（不是重启 main 的旧 V0.4.0 任务）并确认办公室 Git 工作区没有未保存的个人变更：`git fetch origin && git switch fix/offline-native-no-destination-question && git pull --ff-only origin fix/offline-native-no-destination-question`。若本地已有未提交改动，停止危险覆盖，仅报告冲突处理计划。运行 `git status --short`、核对编译 JDK17 和 Android SDK35。
2. 先 Review 候选，视回归结果只做必要代码/测试修订；调整 `app/build.gradle.kts` 到 **V0.4.1/versionCode15**（真实主干版本高则递增到更高），不能修改 applicationId、签名文件路径、私钥/密码，也**绝不能把私钥、账号令牌、GPS轨迹、私人聊天记录写入 GitHub/日志**。
3. **真实运行**：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Offline`。包括 Debug/Release Unit、assemble、lint、签名；再运行六组项目模拟脚本/测试及必要的地理/线上超时隔离专项测试。若已有本地依赖可以使用非 Offline 构建但必须说明原因，不能隐藏 skipped/failed。重要断言：**真实运行路线在线 / 无网 / 在线但 ChatGPT 不可达**，地方包命中时自动讲述不调用云端 AI（计数为0），非覆盖地区安静；主动联网问题按需调云且失败不阻塞本地。
4. 运行 `powershell -ExecutionPolicy Bypass -File scripts/build-runtime-smoke.ps1 -Offline`，生成**同签名**独立验收包 `com.kitt.reader.runtimecheck`，而不是错误地把普通 `com.kitt.reader` APK 安装到已经有账号/设置的数据空间。运行 `scripts/check-kitt-signing.ps1` 验证 APK 唯一证书 SHA256 必须是 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，且 Android `versionCode > 14`。
5. **安装前先报告** APK 包名、code、哈希、真实全套测试数与失败项及旧手机 APK 信息。**未获得本轮手机原位升级确认前，不自动 adb install、uninstall、clear data 或替换手机 App**。用户确认后，只用原签名 `adb install -r`，手动真机断网/联网+不可用 ChatGPT、GPS、免目的地、主动问答/静音/打断及本地中文发声分开验收。
6. 工程全绿与真机结果分别记到 Issue #24、PR #25、HANDOFF 和脱敏 evidence；必要时先同步主干新增文档避免 PR 失效。**没有真机 GPS 或 AI 观测证据则明确 PENDING，不伪造 PASS**。质量合格才申请 PR Review/Merge；本轮完成 STOP，暂不做 #23 评分删除、V0.5.0 故事、Explore、离线包下载器。

## 完成定义与 STOP

交付候选同签名可覆盖 APK + 可复核构建日志/测试计数 + 真实改动/未验证分项 + 一页最短真机验收操作。工程与手机分开判定。**没有完整验证前不得说“手机上已经修好了”。**
