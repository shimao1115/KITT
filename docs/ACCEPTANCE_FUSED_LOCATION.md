# 沿途 V0.3.5 — 原生 FUSED 实验验收

2026-10-09（Asia/Shanghai），Issue #16。结论：**在已观察的室内蜂窝 + VPN 条件下无实质增益；生产不启用 FUSED，继续 GPS + NETWORK。** 这是任务卡允许的负结果交付，不能宣称定位连续性已经改善，也不能推广成“所有设备的 FUSED 都无用”。

## 现有主链审计

`RealLocationSource` 原来只注册 GPS、NETWORK，共享一个 listener，以 `Location.provider` 判断原始来源。精确权限缺失时只订阅 NETWORK；各 provider 分别注册，禁用时仍保持订阅以便恢复。stop 移除 listener、取消计时/地区解析，世代检查挡住旧回调。缓存按测量时钟从新到旧进入选择器。

`deviceFix` 要求 Android 测量时钟和 accuracy；`PhysicalLocationSelector` 继续拒绝无效数据、精度超过 1500m、未来/超过 60s 的测量、同来源重复或乱序、比可信锚点旧超过 15s 的测量、不合理跳跃。实际选择使用年龄/精度/速度评分、切换滞回和测量单调性。**GPS 活跃 15s、NETWORK 活跃 30s、短桥接最多 60s 不变**，桥接不刷新时间，精度随年龄下降，过期为未知。VPN/IP/搜索没有物理定位输入端口。

本轮生产代码只把合并的拒绝日志细分为原因，并补 deviceFix 缺精度/缺时钟诊断；接受条件和调度不变。原有 `FixSource.FUSED`、选择器及状态标签已经存在于 V0.3.4，本轮没有把预留类型误当成已接入来源。

## 实验方法与有效范围

设备为 vivo V2405A / Android 16 / API36，现有独立包 `com.kitt.reader.runtimecheck`，精确/粗略权限均已授予。原生 FUSED 同时列于 `allProviders` 且 `hasProvider=true`，GPS/NETWORK/FUSED 均启用，三种注册均成功。官方平台接口及 API31 边界：[Android LocationManager](https://developer.android.com/reference/android/location/LocationManager#FUSED_PROVIDER)。没有引入 Play 定位依赖或地图 SDK。

显式 instrumentation 探针按 A1（GPS+NETWORK）→ B（再注册原生 FUSED）→ A2（停止后重启双来源）采样，各阶段目标 65 次一秒观察。B 阶段同时运行两份**生产选择器**：一份只接 GPS/NETWORK，另一份再接 FUSED 流，用同一时刻比较有无额外新鲜 fix。没有注入位置、模拟路线、研究请求或 TTS，也没有启动 Journey；表中的最终来源是选择器的 Journey 输入候选，不冒充实际驾驶 Journey 验收。

自然位置只在最多 256 个/来源的内存样本中用于比较；报告只写来源、精度、年龄、计数、拒绝原因及网络 capability，**没有真实经纬度、轨迹、账号标识、凭据或 VPN 节点**。每阶段独立 listener，结束移除；测试 APK 已卸载，验收主包保留，没有测试 Journey/定位前台服务。

两轮 A1 分别有后台切换/锁屏，前台只覆盖 59/65、49/65 个观察点，首次回调等待与观察间隔因此受到污染。**A1 不用于可靠的冷启动 TTFF/连续缺定位时长比较**，其 UNKNOWN 计数不能当作原办公室 40s 症状复现。以下结论取两轮各自完整前台的 B、A2（均 65/65）。原先“已联网但定位过时约40s”的前台办公室症状**未复现**。

首个工具版本错误地把 FUSED 返回 `Location.provider=network` 当成来源不匹配；修正后允许原生 FUSED 流保留底层 GPS/NETWORK 标签并比较测量本身。该初轮错误统计未用于下表。最后一版工具另外补 shell 前台启动与实际窗口时长；它编译通过，但最后测试 APK 替换被 vivo 拒绝，手机数据来自同一统计/过滤逻辑的上一版。没有把工具安装失败算作定位服务失败。

## 自然三来源对照

第二轮只打开 Wi-Fi 开关，**默认网络在实验前后仍为已验证蜂窝**，VPN 可见；没有建立实际 Wi-Fi 联网对照。报告中的请求标签 `indoor-stationary-wifi-vpn` 不能覆盖 `network_before/after` 的事实。结束恢复原 wifi=0、mobile_data=1，VPN配置未改。

| 有效 B 阶段 | 来源 | 原始回调 / 接受 offer | 首个有效回调 | 精度 min / p50 / max | 测量年龄 min–max |
| --- | --- | ---: | ---: | --- | --- |
| R1：蜂窝 + VPN | GPS | 0 / 0 | 未收到 | 无测量 | 无测量 |
| R1 | NETWORK | 8 / 8 | 44ms | 30 / 33 / 58m | 2–4190ms |
| R1 | FUSED 流（raw label=network） | 8 / 8 | 81ms | 30 / 33 / 58m | 12–4191ms |
| R2：Wi-Fi开关开，默认仍蜂窝 + VPN | GPS | 0 / 0 | 未收到 | 无测量 | 无测量 |
| R2 | NETWORK | 7 / 7 | 39ms | 30 / 53 / 58m | 2–12ms |
| R2 | FUSED 流（raw label=network） | 7 / 7 | 73ms | 30 / 53 / 58m | 21–52ms |

GPS 是“存在/启用/注册成功但观察窗内无回调”，不是接口不可用或应用过滤掉 GPS。两轮 B 的 deviceFix 拒绝均0，活跃 selector 拒绝均0。两轮各收到一个 FUSED last-known 缓存，均因超过60s而拒绝（`cache_expired:1`），没有延长时效来使用它。

| 连续性与额外价值 | R1 B | R2 B | 两轮 A2 |
| --- | ---: | ---: | --- |
| FUSED 与 NETWORK 同测量时钟且位置相距<1m | 8/8 | 7/7 | 未订阅 FUSED |
| FUSED 未匹配的额外测量 | 0 | 0 | — |
| FUSED 在基线无新鲜 fix 时补足的观察秒数 | 0 | 0 | — |
| 基线 / 加 FUSED 的 fresh 秒数 | 65 / 65 | 65 / 65 | 各65 |
| 最终选择来源 | NETWORK / NETWORK | NETWORK / NETWORK | 各 NETWORK |
| 有效阶段最长采样 UNKNOWN / no-fresh 秒数 | 0 / 0 | 0 / 0 | 各0 / 0 |
| 已知→未知次数 | 0 | 0 | 各0 |
| 最后测量年龄（实际单调时钟） | 7271ms | 5089ms | R1 4550ms / R2 4502ms |

FUSED 本轮只增加了重复的 callback，没有显示精度、时效或连续性提升。65s、65个采样点的“无空档”只描述有效前台短窗口；不能覆盖失去前台时未观测的墙钟时间，也不能证明长期/移动连续性。没有定量 CPU/电量测量，不能得出更省电或显著耗电结论。

脱敏原始汇总已入 Git：[R1](evidence/v035-fused-cellular.json)、[R2实际仍蜂窝](evidence/v035-fused-wifi-enabled-cellular.json)。更早的错误探针结果只在忽略的本地 artifacts 中保留。

## 条件式生产决定

**不接入、不默认开启第三来源，不设置 FUSED 优先级。** 现有双来源、V0.3.4 状态显示、地区解析、粗位置地标护栏、章节研究、Director、播报、TTS/ASR 均保持。实验 FUSED 使用与 NETWORK 相同的 30s 活跃窗口，理由是未知底层组合不应获得更长信任；这是选择器已有候选行为的回归，不是本轮新启用的生产窗口。

下一次只有在未复现的问题现场自然测量确实出现“基线缺更新但 FUSED 有独立可靠新测量”时，才重新评估第三来源。不能仅凭 NETWORK/FUSED 叫法不同或较晚到达的同一测量开启它。

## 自动 Gate 与打包

最终完整 `scripts/verify.ps1 -Offline`：**Debug329 / Release316 tests，失败/错误/跳过均0；assemble、lint、签名全部PASS**。新增15个测试执行：7个候选来源/选择器与状态测试，2个兼容性测试在 API26/30/31/34 各执行一次（8）。覆盖三来源切换/GPS恢复、同测量重传、乱序、跳跃、缓存过期、30s→60s桥接→未知、FUSED/沿用FUSED诚实显示、粗位置上下文及 deviceFix 拒绝原因；生产 API26+ 保持双 listener 与清理。

六组成都新都→绵阳安州雎水模拟：29点 / 90912m；80km/h 完整链57次 Director /4次 TTS，Context Card1489字符；100km/h /16× 13章节 /19次研究 /13段旁白，零 stale/cancel/failure。Fake/确定性 Provider、fake Voice 和生产 Context/Journey/Director 的回归不等于真路线/真人听感。原 start/end、安静/提前恢复、打断TTS、ASK_USER一次监听、PREPARE失效、旧内容不补播、自动失败静默/主动失败短报、schema、Overview→Topic、状态/网络/布局 Gate 一并保留。

APK 为 **0.3.5 / code10 / minSdk26**，Release为现有 debug 验收证书签名的验收包。[V0.3.5预发布](https://github.com/shimao1115/KITT/releases/tag/v0.3.5) 包含下列主包、可选测试探针、SHA256、验证与脱敏实验报告。

| 主包 | 包名 | bytes | SHA256 |
| --- | --- | ---: | --- |
| `kitt-v0.3.5-runtimecheck.apk`（现有账号优先） | `com.kitt.reader.runtimecheck` | 74279068 | `F4914802647FD97271E4B4C30FD005F71A2D8CB07BF13D4F15BF17963B65A752` |
| `kitt-v0.3.5-debug.apk` | `com.kitt.reader` | 74279044 | `500BD750087FF1C5F85D218B9A047280B09708B3F4BD37506EC324BAEAB2F004` |
| `kitt-v0.3.5-release.apk` | `com.kitt.reader` | 70642112 | `B848EF2132A3E0DEF0A8603642C6930CE3E06EC656908B14ACEF41C1CBDF379B` |

`runtimecheck` 已用 `adb install -r` 成功从0.3.4/code9升级，没有卸载/清数据。手机 base.apk 与交付APK哈希一致：`F4914802647FD97271E4B4C30FD005F71A2D8CB07BF13D4F15BF17963B65A752`，74279068 bytes。

签名证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，升级前后相同。设置文件摘要始终 `2925e5f64907b314c02ccc971f2cae1f927b572b1c329492502bad5603970f42`；只取摘要，没有导出文件。已保存 ChatGPT / gpt-6.1-sol / low、语速1、独立研究关闭，账号记录可解密且 connected=true，原未结束旅程恢复提示也保留。本轮未重新验证真实 AI 服务/刷新授权；新安装仍默认 Fake / 配置字段gpt-4.1-mini / default。V0.3.4历史交接见 [存档](HANDOFF_V034_RUNTIME_STATUS.md)。

## 未测与最短后续验收

- 实际 Wi-Fi 默认联网、室外/开阔、真实移动、弱GPS→恢复、驾驶 GPS→NETWORK/FUSED→GPS、粗位置地标安全与长期锁屏均未建立。Issue #14 外场、#15 用户状态验收仍独立开放；#13不施工。
- 在停车/乘客条件下打开已升级的“沿途0.3.5验收”，按原旅程恢复提示继续/结束，再核对真实来源/精度/测量年龄、网络/AI状态、安静/恢复、接管和结束。
- 若再次出现前台联网正常但位置过时，保持现场条件，安装同签名测试APK后运行 `scripts/probe-fused-location.ps1 -Condition indoor-stationary-cellular-vpn`。65s×3，保持解锁和沿途可见；没有坐标注入。vivo安装指纹只能本人完成。报告看 raw回调/拒绝原因、前台覆盖和实际 network capability，不能用请求标签代替环境确认。
- 无新的密钥或登录阻塞；现有账号保留。实际Wi-Fi/外场缺测是验收范围限制，不据此强行启用 FUSED。工程交付后停止，不开展其他功能。
