# CURRENT TASK CARD — KITT V0.4.1 / Issue #24 本地原生讲述热修

**授权时间：2026-10-14 下午；用户已到办公室并明确要求“开始吧”。当前状态：独立分支存在候选代码、Draft PR #25，尚未在办公室电脑完成构建和真机验收。**

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
