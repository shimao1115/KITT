# 沿途 V0.3.3 定位可靠性验收 — Issue #14

日期：2026-10-08（Asia/Shanghai）。范围仅为 [定位热修复](HOTFIX_LOCATION_FALLBACK.md)。
V0.3.2 研究与真机历史记录保存在 [原交接](HANDOFF_V032_STAGED_RESEARCH.md)，不能代替本轮现场验收。

## 实现与边界

- 保留 GPS，增加 Android `LocationManager.NETWORK_PROVIDER` 双监听；每个 Provider 不存在、关闭或无权限时单独降级，结束/重启移除监听并隔离晚回调。
- 没有引入 Google fused 依赖、新地图 SDK、付费定位服务、基站/Wi-Fi 数据库、扫描器或网络地理服务。`FUSED` 只作为 fix 来源契约预留值，本轮实际监听 GPS/NETWORK。
- 每个 fix 含坐标、GPS/NETWORK/LAST_KNOWN 来源、原始来源、水平精度、测量 timestamp 和 elapsedRealtime；真实年龄/排序采用单调时钟，不受手机墙上时间调整影响。模拟仍使用原注入时钟。
- 原始 fix 要求有效坐标、速度、正精度 ≤1500m、年龄 0–60s；缺精度、未来、过期、同源乱序均拒绝。
- GPS live 窗口 15s，NETWORK live 窗口 30s。按 `精度 + 年龄秒数 × max(15m/s, 已报告速度)` 比较有效不确定性；来源稳定期 10s、挑战者至少改善30%。有效 GPS（≤50m、≤8s且评分最优）可立即恢复；当前来源失效可立即切换。
- 独立 Provider 最近15s的回调乱序可以保存，但不向 Journey 发送测量时间倒退的位置；等待新测量后恢复。跳跃过滤比较测量间隔、距离、双方精度及速度上界，不平均坐标，不把正常移动磨成静止。
- LAST_KNOWN 仅短暂桥接，最长原测量年龄60s；不刷新 timestamp，精度不确定性持续增加，不新发起地理查询。保留上一章节作为不确定背景；过期 UI/Context 显示未知，自动导演原新鲜度 Gate 拒绝现场旁白。
- 精确地标机会要求精度≤50m、年龄≤15s且不是 LAST_KNOWN。粗定位清空 live proximity、作废精确 PREPARE；延迟到达的地标结果重新过 Guard，不能补播。正常章节背景与主动对话保留，Context 明示不能说已到门口、精确距离、方向或可见性。
- 精度>500m的 reverse-geocode 标签只保留区县背景；reverse-geocoder 必须返回与请求坐标近邻的坐标，否则不接受其章节标签。地理增强仍可缺省，不改变实际坐标。
- 定位没有 IP、VPN出口、代理、DNS或搜索引擎地理输入通道；Context 也明确禁止用它们改写物理现场。

API依据：[Android LocationManager](https://developer.android.com/reference/android/location/LocationManager) 与 [Location 测量时间](https://developer.android.com/reference/android/location/Location)。应用信任 Android 设备定位契约，OEM网络定位底层能力须以实际现场验证为准。

## 自动验证

最终统计与 APK SHA256 见根 [HANDOFF.md](../HANDOFF.md) 与本地 `artifacts/verification.json`。

新增26项回归：`LocationFallbackTest` 17项、`RealLocationSourceTest` 9项（Robolectric Android 34）。包括：

- GPS→NETWORK→GPS、GPS关闭/恢复、弱GPS与较新网络优选、防来源抖动；
- 年龄、单调时钟、缓存桥接/过期、缺Provider/权限、仅粗权限、停止/重启隔离；
- 长距离/美国坐标跳跃拒绝且不污染恢复、精度圈内合理噪声、连续移动车不冻结；
- 乱序回调不重播、未知Provider/无精度/无测量时间拒绝；
- 粗定位保留章节和原 Director/Journey、精确 PREPARE 与地标机会失效、晚地标旁白丢弃；
- reverse-geocoder 返回外国坐标不能更改现场/章节。

这些测试不模拟真实基站、Wi-Fi数据库、GNSS天空条件或VPN出口，不等价于以下现场项目全部通过。

复现：JDK17、Android SDK35配置后在 PowerShell 运行 `scripts/verify.ps1 -Offline`。脚本覆盖两种 assemble、全量单测、lint与签名；Debug至少285、Release至少272且失败/跳过为0才交付包，拒绝用筛选测试报告代替全量。

## 真机验证记录

本轮手机自然定位探针与现场项目结果以最终 HANDOFF 为准。探针只直接运行生产 RealLocationSource，45s内接受自然 Android 回调；不注入坐标、不切换 GPS/Wi-Fi/VPN、不启动旅程/联网研究、不修改 Provider/账号设置。只记录来源/精度/年龄/标签，不保存坐标轨迹。

开发入口可复现（先结束现有旅程）：

```powershell
.\gradlew.bat assembleDebugAndroidTest --offline --console=plain
adb install -r artifacts/kitt-v0.3.3-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode location com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
adb shell run-as com.kitt.reader cat cache/location-fallback-phone.json
adb uninstall com.kitt.reader.test
```

`PASS_CURRENT_CONDITIONS_ONLY` 仅表示窗口中收到有效自然设备测量；GPS、NETWORK 各出现多少次可直接检查报告。它没有证明室外GPS健康/失联/恢复，更没有证明国外VPN出口国家。手机输出、包核对及清理记录存于本地 `artifacts/location-phone-*.log/json`。

## 最短现场验收

用 Debug APK 覆盖安装，保留原 OAuth、模型和语音配置；开启系统定位与精确权限，使用正常 VPN/TUN。乘客/停车时观察来源行（来源/±精度/年龄），驾驶员无需操作。界面坐标/区域必须符合实际所在地，不能只凭“有定位”通过。

| 必需现场场景 | 可判定标准 | 本轮证据边界 |
| --- | --- | --- |
| 室外 GPS 健康 | GPS、低精度数值与新鲜年龄 | 自然探针若收到GPS，只证明当前条件；室外现场仍需确认 |
| GPS 弱/丢失 | Android提供网络fix时自动NETWORK，Journey不中止 | 自动/Robolectric证明切换；真实弱GNSS现场需确认 |
| GPS 恢复 | 无重启回GPS且无异常跳跃 | 自动/Robolectric证明；自然真实恢复需确认 |
| 国外VPN/TUN出口（含美国） | 开启已确认国外节点后，现场仍为手机实际本地位置 | 架构隔离/外国跳跃与标签测试完成；国外出口现场需确认 |
| Wi-Fi关、蜂窝开 | NETWORK仍有有效系统测量（若OEM提供） | 需人工确认此组合 |
| Wi-Fi开 | 网络辅助fix有效 | 自然探针以当时网络状态为准；与蜂窝对照需确认 |
| 粗网络精度 | ≥100m不触发精确地标到达叙述 | 自动Guard完成；真实内容/听感需确认 |
| 来源切换无远距离跳跃 | 连续观察本地坐标，不瞬移外地/美国 | 异常跳跃回归完成；行车现场需确认 |
| 原链路 | 开始/安静/接管/研究/讲话/结束正常 | 全量确定性回归；本轮真机另记，不复用历史PASS |

至少观察一次 GPS→NETWORK→GPS；无法让 Android 输出 NETWORK 时记为“系统未提供”，不要伪造成功或用 IP替代。隧道中GPS与网络都无fix是预期边界：短暂LAST_KNOWN后未知，等待恢复。

可辅助记录：`adb logcat -v time -s KITTLocation KITTArea KITTResearch KITT AndroidRuntime`。不提交凭据、完整轨迹或原始录音。手动验收完成后更新本表实际结果与 Issue #14；任何尚未测到的行仍为待确认。
