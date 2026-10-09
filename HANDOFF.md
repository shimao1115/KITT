# 沿途 V0.3.4 — 运行状态透明化第一阶段交接

截至 **2026-10-09（Asia/Shanghai）**，Issue #15 第一阶段实现与自动 Gate 完成，交付 Android8.0+、**0.3.4 / code9**。只增加主驾驶页真实状态摘要；不做展开式诊断、事件时间线或日志平台，**不修 Issue #13，不关闭待外场验收的 Issue #14**。下一步为用户总验收，不继续 Stage 2。

实现 commit：`d841c2e` — `feat: expose truthful driving runtime status and wait reasons (#15)`。
完整矩阵、手机结果及签名恢复操作：[运行状态验收](docs/ACCEPTANCE_RUNTIME_STATUS.md)。V0.3.3 原交接存档至 [定位交接](docs/HANDOFF_V033_LOCATION_FALLBACK.md)，原定位 Gate/阈值继续有效。

## 当前可运行能力

- 主驾驶页显示真实定位来源、±精度、测量年龄、精确/粗略；短桥接显示“沿用 GPS/NETWORK”，过期为未知。地区名未解析时不显示坐标。物理定位仍来自 GPS/Android NETWORK，VPN/IP/搜索不参与定位。
- Android 默认网络连接、Wi-Fi/蜂窝、系统联网验证与可见 VPN 单独显示。AI和研究分别显示最近真实请求结果与年龄，未调用/未知明确标明；Fake不冒充真实服务成功。VPN已检测不代表出口、联网或路由健康。
- 主提示显示定位、地区解析、概况/专题/主动问题搜索、AI判断/生成、准备语音、实际讲述、安静、冷却或正常等待。使用真实任务开始时间，没有百分比或预计完成时间；取消/超时/失败/跳过/驶离/结束清理或过滤旧状态。
- TTS入队显示准备语音，onStart才显示讲述；PREPARE显示已有线索等待新现场确认。用户交互与安静优先，相关后台任务可作次提示。定位/网络/服务仍独立可见。
- 横竖屏状态区域稳定，紧凑打字接管不需要驾驶页滚动，大按钮保持可用。可见且运行时仅1秒刷新elapsed；网络被动callback，后台Journey保持、Activity恢复重读，没有额外ping。
- Journey五态、Director八字段、Overview→Topic、地标/过时保护、定位兜底、图片、文字/语音接管、Vosk和Android TTS沿用原行为；未修改反重复策略。

## Build / test / 完整回归

最终 `scripts/verify.ps1 -Offline`：**Debug 314 / Release 301 tests**，失败/错误/跳过均 **0**；两种assemble、lint、签名均PASS。新增29项：18状态/协程、5网络、4布局、2Voice；网络callback生命周期覆盖最低API26。

回归涵盖start/end、安静10分钟/提前恢复、打断TTS、ASK_USER one-shot、PREPARE失效、旧内容不播放、自动失败静默降级/主动失败短报、schema、Overview→Topic、真实等待38秒/90秒超时、Topic取消、旧ticket/地区结果、配置切换/结束恢复、TTS准备/onStart/watchdog与网络交接。Compose覆盖手机竖屏/横屏及16:10平板比例，真平板仍待验收。

修复全量lint发现的API30调用：改用API21兼容VPN订阅，随后重跑完整Gate通过；也修复断网桥接标签过长及权限提示覆盖用户状态边界。没有遗留已知生产崩溃。

六组模拟报告PASS：成都新都→绵阳安州雎水 **29点 / 90912m**；80km/h完整链57次Director/4次TTS，Context Card1489字符；100km/h/16×章节研究 **13章节 /19次研究 /13段旁白**，零stale/cancel/failure。独立四地标、加速、编辑自由度与行政节流保留。这是Fake/确定性Provider和fake Voice＋生产Context/Journey/Director回归，不代表真实路线/AI内容/真人听感验收。

本地证据：`artifacts/runtime-delivery-gates.log`、`verification.json`、六份simulation报告、`runtime-phone-report.json`（初轮Fake）、`runtime-phone-delivery-report.json`（最终真实配置）、APK和截图。`assembleDebugAndroidTest`也PASS；instrumentation仅在测试APK中。

## 手机实际结果与未完成项

vivo **V2405A / Android16**，使用生产源码独立包。初轮Fake验证自然NETWORK定位、蜂窝系统联网验证、实际VPN、AI未验证/研究不可用、安静/恢复、监听/取消/打字、TTS生命周期和结束无旧loading。横竖屏截图确认按钮/状态/输入可见；后台切回刷新正常。临时断Wi-Fi/蜂窝显示未连接，随后恢复原wifi/data=1、旋转=0。

最终安装复验时独立包已保存 **ChatGPT / gpt-6.1-sol / low**，独立研究关闭；本轮没有修改/导出该配置。实际观测到DECIDING、OVERVIEW、SEARCH_DECISION，NETWORK/沿用NETWORK、真实AI成功与研究成功，随后主动链Provider抛`IllegalArgumentException`并正常短报/TTS结束。主提示成为“上次请求失败，等待下次触发”，网络仍显示蜂窝已验证/VPN已检测，AI失败与研究成功分别保留。未把这次Provider失败算作内容验收通过，也没有遗留loading。两轮结果都仅 **PASS_CURRENT_CONDITIONS_ONLY**。

手机原 **0.3.3 / code8** 和账号保留，另安装 **“沿途 0.3.4 验收”**。最终独立APK已拉回base核对同一SHA256（见下表）；测试runner卸载，验收主包保留，无遗留测试Journey/定位前台服务。

仍待真人/外场：真实慢Overview/Topic超过30秒及真实超时/不可达、真人确认内容质量、连续驾驶GPS→NETWORK→GPS和无fix桥接→未知、本人确认的国外VPN出口、粗定位地标抑制、真人ASR/声音质量/蓝牙、长时间后台/锁屏、Tab S9硬件/大字体/键盘遮挡。自动模拟不能替代这些条件。Issue #14继续开放，#13仍为backlog。

## Provider / 授权 / 签名阻塞

新安装默认 **Fake / 配置字段gpt-4.1-mini / default effort**，Fake不调用该模型。当前独立包真实配置如上；原手机 **ChatGPT OAuth / gpt-5.6-sol / low** 是V0.3.3历史快照，本轮保留数据但未重新验活原包账号。没有新API密钥要求，独立包不共享原包账号。

唯一覆盖升级阻塞：原手机0.3.3由另一电脑debug密钥签名，本机覆盖被Android拒绝 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。用户提供的 `D:\QSYNC\peanut-pim-dev.keystore` 已核对，证书与本机相同，**不是原应用密钥**。未卸载原应用、清数据或导出OAuth。

- 原手机证书SHA256：`6bc696a7310db472b6481bd8fd63103bb56590fc90573815ff2ce33dc4dff92a`。
- 本机/该文件证书：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

最小动作：取得**原构建电脑** `%USERPROFILE%\.android\debug.keystore`，证书应匹配前者。然后 `scripts/verify.ps1 -Offline -SigningKeystore <原密钥路径>` 重签，再 `adb install -r` 保留OAuth/设置升级。密钥不入Git；已有debug alias/password约定详见验收文档。现独立包已可直接验收，无需等待原密钥才能看新状态。

## APK交付

预发布：[V0.3.4](https://github.com/shimao1115/KITT/releases/tag/v0.3.4)。标准Debug/Release为`com.kitt.reader`；当前手机先用独立包，不卸载旧包。

| APK | 大小（bytes） | SHA256 |
| --- | ---: | --- |
| [标准 Debug](artifacts/kitt-v0.3.4-debug.apk) | 74726163 | `505E994D5B42D79FF5DC14FE1D686B6194F82361710AA6A961489EA2C317AFD9` |
| [Release验收包](artifacts/kitt-v0.3.4-release.apk) | 70642112 | `30B9D9954DBE78634CD439382D7C9922FAE37586F3D50AE3DA3CF54EEC0BB686` |
| [独立手机验收包](artifacts/kitt-v0.3.4-runtimecheck.apk) | 74726076 | `BACA47DD3326339EC9395A5698478231F0AE1B91473BAD0EF6B6BD403A0472F1` |

三者沿用本机已有debug验收密钥；Debug v2、Release v2/v3签名通过，无Play新身份。标准包可新安装/同签名升级，**不能覆盖当前手机旧0.3.3**；独立包不共享原账号，当前已独立配置ChatGPT。二进制/完整日志/密钥不入仓库，预发布附SHA256和验证报告。

## 最短总验收

1. 打开“沿途 0.3.4 验收”，开始后核对定位来源/精度/年龄、真实网络/VPN、AI/研究最近结果和等待原因；横竖屏看清大按钮。
2. 安静/提前恢复、说点什么/取消/打字、打断讲述、后台切回、结束旅程；确认不会永远停在搜索/准备。真人听感/事实质量由你确认。
3. 匹配原密钥后覆盖升级原包保留原账号；按 [定位外场步骤](docs/ACCEPTANCE_LOCATION_FALLBACK.md) 完成GPS失效恢复、无可靠fix和国外VPN条件，并补慢真实研究/超时与平板硬件。

## 最近 milestone commits

- `d841c2e` — V0.3.4真实状态/任务elapsed、网络VPN、语音准备、完整回归和独立验收包构建。
- 紧随其后的 `docs: hand off V0.3.4 runtime status gates and phone limits` — 最终结果、签名阻塞、APK和下一次验收。
- `7d1be5a` — GitHub同期新增future旁白频率文档，安全同步保留，本轮不施工。
- `a61a565` / `39afed5` — V0.3.3定位修复与外场交接。
- `c81f641` / `51cdbc4` — V0.3.2 Overview→Topic与原真机交接。
