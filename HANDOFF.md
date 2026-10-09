# 沿途 V0.3.5 — 原生 FUSED 实验交接

截至 **2026-10-09（Asia/Shanghai）**，Issue #16 条件式实验交付完成，Android8.0+，**0.3.5 / code10**。结论：**当前室内蜂窝 + VPN 条件下无实质增益，生产继续 GPS + NETWORK，不启用 FUSED。** 没有证明办公室定位过时问题已解决；到此停止，不开展其他功能。

工程 commit：`1329bb1`。完整三来源数据、拒绝统计、工具校正和未测范围见 [验收报告](docs/ACCEPTANCE_FUSED_LOCATION.md)；V0.3.4能力/限制存档见 [原交接](docs/HANDOFF_V034_RUNTIME_STATUS.md)。

## 当前能力与实测

V0.3.4真实定位/网络/VPN/AI/研究状态及等待原因保持；Journey、安静/接管、Overview→Topic、Director、图片、文字/语音、Vosk、本地TTS、模拟路线及评分保持。GPS活跃15s、NETWORK活跃30s、LAST_KNOWN最多60s，测量单调、跳跃、粗位置地标和过时护栏均不变。生产代码只细分定位拒绝日志；FUSED仅用于显式短时测试。没有修改播报策略或引入Play定位/地图依赖。

vivo V2405A / Android16 / API36：原生FUSED存在、启用且注册成功。两轮完整前台B阶段分别观察8和7次FUSED回调，**15/15镜像NETWORK（同测量时钟、相距<1m），额外测量0、额外fresh覆盖秒数0**。基线与加FUSED选择器均连续65个一秒观察点使用NETWORK；停止重启双来源的A2也各65点有效。GPS在这些室内窗口无回调。两轮FUSED旧缓存各因超过60s拒绝一次；有效B的deviceFix/活跃offer拒绝均0。

Wi-Fi开关开启后默认网络仍为蜂窝，不能算实际Wi-Fi联网验收。A1受后台/锁屏影响，不用其TTFF/UNKNOWN采样评价前台连续性。原办公室40s过时症状未复现，室外、真实移动/弱GPS恢复、驾驶切换与长期锁屏未测，没有定量CPU/电量结论。

## 安全升级 / Provider / 授权

已成功 `adb install -r` 将 **`com.kitt.reader.runtimecheck`** 从0.3.4/code9覆盖升级，保留账号/设置/原旅程恢复提示。手机拉回base.apk与交付包SHA256完全一致；升级前后及实验结束后设置文件摘要不变，没有导出凭据/设置内容。测试APK已卸载，无测试Journey或定位前台服务；wifi恢复为0、mobile_data为1，VPN未改。

保存配置仍为 **ChatGPT / gpt-6.1-sol / low**，语速1，独立研究关闭；账号记录可解密且connected=true。本轮未重新验证真实AI服务或刷新登录。**无需新增密钥/登录动作**。新安装默认Fake（配置字段gpt-4.1-mini/default，不调用该模型）。vivo安装可能要求本人指纹。

## Build / test / 成都→绵阳

完整 `scripts/verify.ps1 -Offline`：**Debug329 / Release316 tests，失败/错误/跳过均0；assemble、lint、Debug/Release签名全部PASS**。新增15项执行覆盖候选FUSED重传/乱序/切换/GPS恢复/跳跃/缓存/桥接/粗定位与状态，以及API26/30/31/34的原双来源注册/清理。最终探针APK也完成assemble/lint；最后工具启动/汇总改进未重新真机执行，自然数据来自同一统计逻辑的前版，不冒充最新探针真机全通过。

六组模拟PASS：成都新都→绵阳安州雎水 **29点 /90912m**；80km/h主链57次Director/4次TTS、Context Card1489字符；100km/h/16×章节研究 **13章节 /19次研究 /13段旁白**，零stale/cancel/failure。确定性/Fake Provider、fake Voice与生产Context/Journey/Director，不替代真路线、真人内容或听感。

本地证据：`artifacts/fused-delivery-gates.log`（全量重跑）、`fused-final-packaging.log`、`verification.json`、六份simulation报告、自然phone JSON与APK。脱敏自然汇总已入Git。

## APK交付

[V0.3.5预发布及校验文件](https://github.com/shimao1115/KITT/releases/tag/v0.3.5)。**当前手机已升级，无需卸载重装。** 保留现有账号使用runtimecheck；标准包属于另一应用数据空间。

| APK | bytes | SHA256 |
| --- | ---: | --- |
| [同签名runtimecheck](artifacts/kitt-v0.3.5-runtimecheck.apk) | 74279068 | `F4914802647FD97271E4B4C30FD005F71A2D8CB07BF13D4F15BF17963B65A752` |
| [标准Debug](artifacts/kitt-v0.3.5-debug.apk) | 74279044 | `500BD750087FF1C5F85D218B9A047280B09708B3F4BD37506EC324BAEAB2F004` |
| [Release验收包](artifacts/kitt-v0.3.5-release.apk) | 70642112 | `B848EF2132A3E0DEF0A8603642C6930CE3E06EC656908B14ACEF41C1CBDF379B` |

统一沿用办公室验收密钥，证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。密钥/完整日志/二进制不入Git。可选location-probe APK只用于复测，哈希见预发布SHA256.txt；普通验收无需安装。

## 最短人工总验收与限制

1. 打开“沿途0.3.5验收”，按保留的原旅程恢复提示继续/结束；停车或乘客操作，真实旅程中核对来源、精度、年龄和独立网络/AI状态。
2. 安静/提前恢复、说话/取消/打字、打断、后台切回、结束；真人听感、平板、真实AI内容和长期锁屏仍沿用V0.3.4人工边界。
3. 再现前台联网正常但位置过时的现场时，按验收报告运行短时探针，保持解锁/页面可见并确认真实网络；只有观察到FUSED独立可靠新测量才重新评估接入。实际Wi-Fi、室外/移动仍需现场条件。

Issue #14外场与#15用户验收继续独立开放；#13未施工。无已知新增生产崩溃。本轮是负结果实验交付，不宣称已经改善弱GPS连续性。

## 最近 milestone commits

- `1329bb1` — V0.3.5原生FUSED自然对照、拒绝原因、兼容/安全回归与负结果验收。
- 随后的 `docs: hand off V0.3.5 negative FUSED trial and preserved runtime account` — 当前交接与安装/缺测范围。
- `b7e5ed9` / `ab43a25` — 授权V0.3.5实验。
- `d841c2e` / `9fbc88e` — V0.3.4状态透明化与交接。
- `a61a565` / `39afed5` — V0.3.3定位回退与外场交接。
