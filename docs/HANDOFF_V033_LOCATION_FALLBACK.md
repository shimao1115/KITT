# 沿途 V0.3.3 — 定位可靠性修复交接

截至 **2026-10-08（Asia/Shanghai）**，Issue #14 的实现、自动回归、APK交付及部分自然真机定位验证完成。完整外场验收仍待人工确认，**不宣称真机全部通过**；Issue 保留待外场验收。

实现 commit：`a61a565 fix: keep physical location reliable across GPS and network handoff (#14)`。
规格：[HOTFIX_LOCATION_FALLBACK.md](docs/HOTFIX_LOCATION_FALLBACK.md)。详细结果、阈值与操作：[ACCEPTANCE_LOCATION_FALLBACK.md](docs/ACCEPTANCE_LOCATION_FALLBACK.md)。V0.3.2历史研究/真机记录已存档至 [原交接](docs/HANDOFF_V032_STAGED_RESEARCH.md)。

## 当前可运行能力

- 保留GPS，增加Android NETWORK_PROVIDER双监听；缺Provider/权限独立降级，粗权限也可使用系统网络定位。GPS失效有可用网络fix时自动选择，GPS恢复后自动提升，不需要手动切换。
- Fix含来源、原始来源、精度、测量时间/单调时间年龄。拒绝过期、无精度、乱序及不合理跳跃；按新鲜度/不确定性选择，来源切换有稳定期与质量门槛，不平滑真实车辆移动。
- GPS live 15s，NETWORK live 30s，LAST_KNOWN最多原测量年龄60s；短桥接增加不确定性且不刷新timestamp，之后未知。现场精确地标要求≤50m/≤15s且不是LAST_KNOWN，粗定位与晚地标结果不能触发精确到达。
- VPN/IP/代理/DNS/搜索推测地理位置没有进入物理定位的接口。reverse-geocoder只增强已给定坐标，远离请求坐标的标签拒绝，不改实际位置。
- Journey、Director八字段、Overview→Topic、用户主动研究、Vosk ASR与Android TTS沿用原实现；仅增加位置质量/过时保护。没有新地图SDK、付费定位服务、Wi-Fi/基站库、新地图或其他功能。

## Build / test / 模拟

最终完整 `scripts/verify.ps1 -Offline`：**Debug 285 / Release 272 tests**，失败/错误/跳过均0；两种assemble、lint与签名通过。新增26项定位回归（17 selector/链路 + 9 Robolectric Android34）；既有Journey、安静/接管、ASK_USER、PREPARE、晚内容、失败降级、研究/语音回归保留。脚本拒绝低于上述全量数量的报告。

`assembleDebugAndroidTest`通过，新增自然定位探针可复现；它不在生产APK内。
本地证据：`artifacts/location-full-gates.log`、`location-phone-build.log`、`verification.json`、`location-phone-report.json`。

成都新都→绵阳安州雎水粗模拟：**29点 / 90912m**；80km/h完整状态链PASS，Context Card1489字符；100km/h /16×章节模拟 **13章节 /19研究 /13旁白**，零stale/cancel/failure。
这是Fake研究/Director/Voice与生产状态机的确定性回归，不冒充真实GPS路线、模型内容或本轮真机研究/TTS验收。

## 本轮实际真机验证

vivo **V2405A / Android16**，最终Debug包覆盖安装；拉回base.apk与交付包同一SHA256，版本0.3.3 / code8。每轮45秒，直接运行生产RealLocationSource，不注入坐标，不启动Journey或联网研究。探针轮次之间应用重启；每轮内来源实例不重启。

| 条件 | 实际来源交付 | 窗口末次 |
| --- | --- | --- |
| Wi-Fi开，连接快照Wi-Fi/VPN | LAST_KNOWN 1 / NETWORK 7 | NETWORK ±30m，年龄3835ms |
| Wi-Fi关，蜂窝/VPN | LAST_KNOWN 1 / NETWORK 7 | NETWORK ±100m，年龄4603ms |
| Wi-Fi恢复，Wi-Fi/VPN | LAST_KNOWN 1 / NETWORK 3 / GPS 14 | GPS ±1.7m，年龄2282ms |

第三轮同一实例约19秒 **NETWORK→GPS**，日志22:05:37 NETWORK→22:05:56 GPS，随后GPS约2秒持续更新；自动恢复真实观察完成。前两轮GPS也一直开启但没有选中GPS。次数包含行政元数据更新，不等于原始传感器采样次数。

三轮结果仅为 `PASS_CURRENT_CONDITIONS_ONLY`。没有完成一个连续驾驶旅程的GPS→NETWORK→GPS外场循环；没有证明室外/山路/隧道条件、国外VPN出口国家、真实粗定位旁白听感或长时间背景可靠性。VPN传输可见，**出口国家没有确认**。外国坐标/IP隔离与地标抑制有自动回归证据，不能代替这些现实场景。

宿主为第二轮临时关闭Wi-Fi，已恢复；测试APK已卸载，没有遗留测试Journey/定位前台服务。主APK保留0.3.3供直接验收。

## Provider / 授权

本轮未改设置/账号；手机实际保留 **ChatGPT OAuth / gpt-5.6-sol / low**，独立研究关闭。这是本轮手机配置快照，区别于V0.3.2历史luna配置；没有迁移或切换模型。
新安装仍默认 **Fake / 配置字段gpt-4.1-mini / 默认effort**，Fake不会调用该模型。使用真实AI仍通过原设置连接账号。本轮定位不需要新密钥、OAuth或外部服务授权；没有重新验活OAuth或真人语音/研究内容。

## APK / 签名

交付 **0.3.3 / versionCode8 / Android8.0+**；推荐Debug覆盖现有安装，保留数据与定位诊断。

- [Debug APK](artifacts/kitt-v0.3.3-debug.apk)：74665375 bytes，v2签名通过，已安装并核对相同哈希。
  SHA256 `FF793B140B121E02E43351467721CE0A9713DF86F9E06E1D46C35A8EBF6DAC69`。
- [Release验收APK](artifacts/kitt-v0.3.3-release.apk)：70625728 bytes，v2/v3签名通过。
  SHA256 `D210CA1131FA6D887E56960B77F0D86A476798B392D61EDB4721079D1E1C32D5`。

Release沿用已有Android debug验收密钥，无新签名身份或Play发布配置。二进制和完整日志不入Git；预发布下载地址：[V0.3.3](https://github.com/shimao1115/KITT/releases/tag/v0.3.3)，附SHA256、自动结果与无轨迹手机报告。

## 已知限制与用户最小动作

Android网络定位是否可用、精度、频率由手机系统/蜂窝/Wi-Fi条件决定；两者均无有效fix时仅短桥接后未知，不以IP代替。没有额外fused/Google依赖。
地理增强可以缺省：本次初期解析为四川/成都/新都，末次标签为空；原静止缓存/节流可使章节标签短暂未知，原始位置不停。GPS/网络精度不是可见性证明。
已有研究托管搜索延迟、手机网络波动与系统ASR限制仍见历史交接；本轮没有扩大修复范围。

无代码/授权/密钥阻塞。用户只需做现实外场总验收（停车/乘客观察）：

1. 打开已安装0.3.3，保留原Provider与正常VPN/TUN；开启精确定位，开始旅程，核对本地现场与来源/±精度/年龄。
2. 室外GPS稳定→进入弱GPS区域但Android有网络定位→回室外，确认同一Journey的GPS→NETWORK→GPS、无瞬移、不中止；没有网络fix记“系统未提供”。
3. 使用本人确认的国外/美国VPN节点，核对实际本地位置不变；本轮没有代替用户确认出口国家。
4. 粗NETWORK（≥100m）不说已到地标门口；说点什么立即打断、安静/恢复、研究/旁白、后台/锁屏、结束旅程短验；这些现实听感/硬件场景仍需人工。

逐项标准与ADB复现见 [定位验收](docs/ACCEPTANCE_LOCATION_FALLBACK.md)。未测到的场景保持待确认，再据结果收口Issue #14；无需用户搬运密钥或重配账号。

## 最近 milestone commits

- `a61a565` — V0.3.3 GPS/NETWORK选择、位置质量与跳跃/粗地标保护、全量自动Gate、自然真机探针。
- 本轮紧随其后的 `docs: hand off V0.3.3 location results and field acceptance limits` — 最终交接/自然手机结果与待外场项（具体SHA见git log）。
- `14a7432` — 定位兜底紧急规格与Issue #14施工依据。
- `c81f641` / `51cdbc4` — V0.3.2 Overview→Topic与原真机交接。

本轮到此定位任务收口；其余future文档不施工。完整外场通过前不宣称Issue所有真机Gate已通过。
