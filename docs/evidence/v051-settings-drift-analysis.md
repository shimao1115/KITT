# V0.5.1/code18 设置漂移：本机证据诊断（2026-10-16）

> **最终收口状态**：用户后续[亲手体验PASS](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212597341)并[批准工程合并](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212644800)。本报告保留调查当时的HOLD及事实边界；工程合并不改变设置字节FAIL、根因UNKNOWN、账号在线PENDING或正式故事0。最新综合判定见[HANDOFF](../../HANDOFF.md)。

> **后续独立授权已完成一次当前状态只读核查**：见[17:27当前设置检查](v051-current-settings-readonly.md)。六项普通设置匹配较早code17记录，账号密文非空；仍不能重建紧邻升级前字段或确认账号在线可用。下文保留PC离线调查当时的结论与范围，漂移根因仍UNKNOWN。

**判定：`INSUFFICIENT_BASELINE`；设置语义 `UNKNOWN`。整体维持 HOLD / `FAIL_SETTINGS_BYTE_PRESERVATION_SEMANTICS_UNDETERMINED`。** 不能判为 `VERIFIED_BENIGN_SERIALIZATION_OR_MIGRATION`，也不能判为 `VERIFIED_MEANINGFUL_PREFERENCE_CHANGE`。只有整体文件哈希，无法重建升级前字段；新增手机数据也不能补成升级前证据。

本轮遵从用户16:43报告后的最新指令，仅离线读取办公室PC已有文件与源码。起始工作树干净，分支 `feat/v051-explore-places`、HEAD `00fb499498f9a1cf6ee55dd719fa4d53f31754bf`。未访问手机、运行设备工具、执行已有采集/安装脚本、刷新真实账号、联网取证或修改生产代码、设置、APK、包、原证据、提交历史。

## 1. 已有基线及 confirmed 事实

- 本机 `artifacts/v051-phone/before.json`、`after-install.json`、`after-ui.json` 均存在。离线重新比较其字段，结果与[原手机证据](v051-phone-acceptance.json)吻合：16:22:34升级前 → 16:24:35升级后/UI前，全部受检文件一致；至16:38:44最终记录，仅 `shared_prefs/settings.xml` 改变。
- **20/20真实历史逐文件哈希三阶段一致**，三阶段均记录20份必要JSON字段校验通过；首次安装时间、数据目录标识一致，恢复文件均不存在。本轮复核的是已存记录，没有重新读取手机原文件。
- 三阶段均记录非空 `chatgpt` 密文存在；没有逐字段值、类型或密文独立指纹。整体设置文件改变不能确定其中哪个字段改变。
- 静态阅读 `artifacts/v051-phone/snapshot.py`：原始设置XML只在进程内解析；持久化JSON仅含整体文件哈希、账号密文存在布尔值等摘要，**没有写出原始设置XML**。本轮没有导入或执行此脚本。
- 检查已有 `artifacts/` 下277份XML的根元素，未找到SharedPreferences的 `map` 根；再检查755份XML/JSON/TXT/LOG/BAK及无扩展名候选文本的偏好XML标记，匹配仅来自构建lint的 `incidents` 报告，未发现原始设置载荷。1份构建lint XML本身不能完整解析，不是设置快照；未发现常见备份容器。现有手机XML是UI层级，不能当作原始偏好XML。结论范围限本次检查的已知本机目录，不声称搜索了整台电脑。

下表的类型仅来自源码契约，**并非设备快照记录的实际类型**；无原始基线，不输出或猜测字段值。

| key | 源码类型 | 三阶段存在性证据 | 值/密文是否一致 |
| --- | --- | --- | --- |
| `provider`, `endpoint`, `model`, `effort`, `tts_voice` | string | 未记录，UNKNOWN | UNKNOWN |
| `speech_rate`, `acceleration`, `simulation_speed` | float | 未记录，UNKNOWN | UNKNOWN |
| `separate_research` | boolean | 未记录，UNKNOWN | UNKNOWN |
| `research_endpoint`, `research_model` | string | 未记录，UNKNOWN | UNKNOWN |
| `simulation` | 历史测试为boolean | 未记录，UNKNOWN | UNKNOWN |
| `chatgpt` | 加密string | 三阶段均非空 | 密文是否一致UNKNOWN；账号语义前后一致UNKNOWN |
| `credential`, `research_credential` | 加密string | 未记录，UNKNOWN | 密文是否一致及可解密性UNKNOWN |

对已有私密 `settings-private-top.xml` 只计算标签布尔值：账号行可见、计划已启用、未连接标签不可见、等待账号模型列表标签可见；未输出账号文字。结合下述源码，这支持**该画面时进程内曾读出/解码出已连接且有计划权限的记录**；不证明最终落盘密文可解密、网络可用、刷新成功或升级前后为同一账号。模型等待标签也不证明未发起请求或刷新失败。

## 2. 启动迁移路径：源码 confirmed，真机归因 hypothesis

[SettingsStore.kt:35–38](../../app/src/main/java/com/kitt/reader/SettingsStore.kt#L35) 每次构造都提交 `remove("simulation").apply()`。`KittApp` 按进程惰性持有Runtime，[KittRuntime.kt:20](../../app/src/main/java/com/kitt/reader/KittRuntime.kt#L20) 构造Store；首次首页/探索前就可能触发。不是每次返回页面都重建Store，进程重建则可能再构造。

若旧键存在，此路径会删除它并申请异步落盘，不需要保存按钮。键缺失时不能仅凭调用 `apply()` 就断言发生了实际磁盘改写或XML重排。本轮没有设备初始键集合、迁移日志或启动后/进设置前快照，不能确认此机曾有该键、迁移是否写盘、是否足以解释漂移。即使只删旧模拟键，也须有字段证据才能判为已验证迁移。

## 3. 设置页/凭据路径：源码 confirmed，真机归因 hypothesis

- [SettingsScreen.kt:54–62](../../app/src/main/java/com/kitt/reader/SettingsScreen.kt#L54)：所选Provider为CHATGPT时，`LaunchedEffect(clientId, connected)` 在计划已启用、模型列表空、非授权忙碌状态下自动 `refreshModels()`，无需点击刷新或保存。Account构造时模型列表为空，仅在内存维护。
- [ChatGptAccount.kt:142–179](../../app/src/main/java/com/kitt/reader/ChatGptAccount.kt#L142)：刷新列表先调用 `accessLocked()`；剩余有效期≤60秒且达到允许刷新时刻，并有refresh token时，先进行令牌刷新。成功解析/必要身份校验后，`save()` → `saveChatGpt()` 同步 `commit()` 写入整个加密记录，随后才请求模型目录。**即使后面的模型目录请求失败，先前成功刷新保存仍可能已经发生。** 未临期时，单纯取模型列表/更新内存模型本身不写偏好。
- 终止性令牌刷新错误另有 `save(base.withoutTokens())` 写盘路径；普通失败处理只改内存消息/暂停/退避状态。记录非空无法排除“仍有注册信息但令牌已被清空”。目前没有证据证明这些成功或失败分支在该手机发生。
- [SettingsStore.kt:24–31、88–92](../../app/src/main/java/com/kitt/reader/SettingsStore.kt#L24) 每次加密新建AES-GCM Cipher，让加密实现生成新IV，并存储IV与密文。**相同明文重新加密也可产生不同密文；密文变化不等于身份变化，更不等于解密失败。** 实际成功刷新还可能正常改变令牌与有效期；没有独立密文或语义基线，本轮均无法确认。
- 模型列表返回后，设置页可能自动选择第一个模型、清空effort，但此处只改Compose草稿；持久化provider/model/effort/语速/voice需走[保存按钮](../../app/src/main/java/com/kitt/reader/SettingsScreen.kt#L192)。不能把草稿变化写成已保存偏好变化。

原验收记录的顺序为：升级后/UI前一致 → 断网探索 → 恢复网络/字号/方向 → 打开设置查看账号 → 返回 → 最终发现漂移。网络先恢复，因此设置页自动刷新具备尝试联网的条件；源码没有在此Effect前做网络门禁。**恢复网络本身不等于刷新已发生**：Runtime网络回调只在Journey运行时检查loop，原验收没有Journey。没有中间设置快照、该时段账号刷新结果或令牌期限，不能把变化精确定位到设置页，也不能确认刷新/迁移/纯序列化中的任一路径。

## 4. 其它不依赖保存按钮的写入边界

- `ChatGptAccount.signIn()` 可保存主机注册、client注册和验证后的授权；`disconnect()` 可清除令牌。原验收记录没有点击重连/断连，缺少证据将其作为本次原因。
- `infer()`、`research()`、手动刷新/连接测试也可经 `accessLocked()` 触发上述令牌保存；本轮没有调用它们。设置页自动刷新是无需点击的额外入口。
- [SettingsStore.kt:96–98](../../app/src/main/java/com/kitt/reader/SettingsStore.kt#L96)、[KittRuntime.kt:184–187](../../app/src/main/java/com/kitt/reader/KittRuntime.kt#L184)：开发模拟速度/倍率控件调用 `developer()` 即 `apply()`，无需设置页保存。它需要显式开发控件操作，没有本次被操作证据。
- `read()`、`readResearch()`、`readChatGpt()` 的解密失败只标记内存状态/返回空值，不自动删除偏好。探索页仅读取语速/voice，未发现它直接写settings的路径。一般设置保存与研究设置保存均在保存按钮下调用，不是进入设置页即保存。

四个重点生产文件与已合并code17基线 `465d5828` 内容一致（只归一化Git检出换行）。这些路径早已存在；此事实不能证明本轮漂移无害，也不能证明code18新增了这些写入。

## 5. 验证范围、独立结论与 STOP

本轮实跑：本地文件清点、XML结构/载荷候选检查、三阶段JSON安全比较、原设备报告交叉核对、既有UI标签布尔检查、四源码文件与code17基线对照。均未读取设备或调用账号。现成 `SourceIsolationTest.upgradeDiscardsLegacySimulationButPreservesProviderAndSpeedPreferences`、`SettingsTest`、`ChatGptTest.refreshRotationSerializedAndPersistedWithIssuedClientAndResource` / `earliestRefreshHonoredAndTerminalRefreshClearsOnlyTokens` 仅静态阅读；**本轮未执行这些测试、Gradle、构建或新的动态复现，不称新增测试PASS**。Debug489+Release476=965等原工程数字及原设备FAIL/PENDING记录不改。

| 独立事项 | 判定 |
| --- | --- |
| 20/20真实历史不变 | confirmed：已有三阶段逐文件记录再次离线比较一致 |
| 已装code18可用 | confirmed（既有设备证据范围）：单次升级成功且探索辅助检查PASS；本轮未重测手机 |
| 设置逐字节保留 | 已有最终FAIL继续保留 |
| 设置语义、漂移字段及根因 | UNKNOWN / INSUFFICIENT_BASELINE |
| 账号可用性、最终密文可解密性、用户亲手操作反馈 | 尚未验证/PENDING；账号记录存在不代替可用性 |

**STOP。** 本轮只新增此无敏感分析与HANDOFF入口，不提交/推送或写PR评论。若用户决定新增一次手机只读语义检查，最小理由是现有基线不足，无法确认当前落盘账号及偏好状态；检查也只能补充当前状态，不能恢复过去。进入设置页会自动刷新，构造Store会执行迁移，所以“打开页面看看”不是可靠的无副作用方案；未来须先明确批准能避开这两条写入路径的检查方法，再访问设备。本轮不实施或申请扩大设备操作，不安装、不改设置、不修代码、不合并/发布/关闭Issue、不启动下一版本。
