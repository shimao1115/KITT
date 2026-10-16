# V0.5.1/code18：一次当前设备设置只读语义核查

> **最终收口状态**：用户后续[亲手体验PASS](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212597341)并[批准工程合并](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212644800)。下文的Draft/HOLD及人工PENDING是17:27检查当时的状态，原检查记录保留；当前账号在线PENDING、升级前语义等价未证实、根因UNKNOWN均不变。最新综合判定见[HANDOFF](../../HANDOFF.md)。

**2026-10-16 17:27:27（北京时间）完成。** 判定：`CURRENT_NON_SENSITIVE_SETTINGS_READABLE`、`ACCOUNT_ENCRYPTED_RECORD_PRESENT`、`PRE_UPGRADE_SEMANTIC_EQUIVALENCE_UNPROVEN`。设置漂移根因仍 `UNKNOWN / INSUFFICIENT_BASELINE`；PR31继续Draft/HOLD。

## 范围与方式

本轮依据用户新增的一次当前状态只读授权执行。先确认工作树仍为 `feat/v051-explore-places`、HEAD `00fb499`，原有 `HANDOFF.md` 修改及未跟踪的[PC调查报告](v051-settings-drift-analysis.md)均保留，并逐字节备份到本机忽略目录 `artifacts/v051-current-settings-readonly/local-documents-backup/`。没有reset、stash、rebase、提交或推送。

前置通过：设备列表唯一，ADB USB选择器确认同一目标，厂商vivo、型号V2405A（X200 Pro）；已装 `com.kitt.reader.runtimecheck` code18；包过滤的服务查询无运行服务，`files/trips/current.json` / `current.json.new` 均不存在，未发现活动Journey/待恢复旅程。

只使用项目原验收已采用的非root `run-as` 文件读取路径：ADB设备/USB目标查询、只读getprop、包与服务dumpsys、恢复文件存在性查询；然后**仅一次** `exec-out run-as com.kitt.reader.runtimecheck cat shared_prefs/settings.xml`。XML仅在本机进程内存解析；没有启动App、实例化SettingsStore、打开UI、调用仪器、提取密钥、解密或发起账号网络请求。没有改变网络、权限、字号、方向或其他设备设置，没有执行任何安装/强制停止/输入/保存动作。未把原始XML、账号值、密文、密文长度/哈希或设备唯一标识写入输出/文件。

## 当前普通设置

| key | 存在 | 实际XML类型 | 当前值 | 有来源的较早参考 |
| --- | --- | --- | --- | --- |
| `provider` | 是 | string | `CHATGPT` | 一致 |
| `model` | 是 | string | `gpt-6.1-sol` | 一致 |
| `effort` | 是 | string | `low` | 一致 |
| `speech_rate` | 是 | float | `1.0` | 一致 |
| `tts_voice` | 是 | string | 空字符串（按源码表示系统默认声音） | 一致 |
| `separate_research` | 是 | boolean | `false` | 一致 |
| `simulation` | 否 | 无 | 未存储；不推断历史上是否存在 | 无逐字段基准 |
| `acceleration` | 否 | 无 | 未存储；不把源码默认值当成设备存储值 | 无逐字段基准 |
| `simulation_speed` | 否 | 无 | 未存储；不把源码默认值当成设备存储值 | 无逐字段基准 |

六项“一致”的参考明确来自[code17既有验收证据的persistence段](v050-phone-acceptance.json)：该记录保存了provider/model/effort/speech_rate/stored_voice/separate_research，voice写为 `empty / system default`。这是**较早code17验收时的实际记录**，不是本次code17→18紧邻升级前的字段快照；不得将其等同于完整升级前语义基线。当前六项存在且类型符合源码，未见这些检查项的明显异常。三个开发键缺失不能证明意外重置，也不能证明启动删除旧simulation键就是原漂移根因。

## 敏感项仅到存储结构

| key | 存在 | 实际XML类型 | 是否非空 | 解密/在线可用性 |
| --- | --- | --- | --- | --- |
| `chatgpt` | 是 | string | 是 | 未测试；在线PENDING |
| `credential` | 是 | string | 否 | 未测试；不据此判断ChatGPT失效 |
| `research_credential` | 否 | 无 | 不适用 | 未测试 |

现有读取路径只返回XML，不提供无秘密的解密成功信号。本轮没有引入解密代码、启动App或用凭据验证服务端，所以**不能宣布真实账号正常登录、密文可解密或当前授权有效**。仅确认账号加密记录存在；没有敏感字段的前后相等结论。

## 独立结论与 STOP

- **当前普通设置可读，六项匹配较早code17记录**；这是当前快照和有限历史参考的比较，不是升级前后全字段等价证明。
- **20/20历史不变**沿用已保存三阶段逐文件证据；本轮没有重新读取历史内容或重跑历史校验。
- **已装code18**本轮前置确认；探索可用沿用此前辅助UI证据，本轮未操作UI。用户亲手体验仍PENDING。
- **原最终设置保字节FAIL、漂移字段/根因UNKNOWN、完整升级前语义等价未证实**继续保留。账号在线使用PENDING。
- 本轮无工程构建/测试，原965项结果未重跑或改写。没有修改生产Kotlin、APK、包、审批、原手机证据或提交历史。

本机可复核的脱敏记录为 `artifacts/v051-current-settings-readonly/sanitized-result.json`；受一次标记保护的本机读取脚本仅用于本次已完成核查，不代表再次运行许可，未保存原始设备数据。

**STOP：本次当前状态检查许可已使用完毕，后续不再访问设备。** 原唯一安装许可也已用尽；不安装/回滚/卸载/清数据、不刷新或编辑账号、不合并/发布/关闭#23/#24/#27/#30、不启动未来版本。等待用户亲手体验及是否合并的单独决定。
