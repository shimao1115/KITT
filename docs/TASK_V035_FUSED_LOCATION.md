# V0.3.5 — 原生融合定位与定位连续性试验

**Status: AUTHORIZED for autonomous implementation (2026-10-09)**  
**Scope: small location-only milestone; Android LocationManager native FUSED_PROVIDER evaluation.**  
**Baseline:** current `main`, V0.3.4 engineering handoff. Existing user field acceptance of V0.3.4 / #15 and V0.3.3 / #14 remains independent and unresolved until actually done.

## 0. 用户问题与真实目标

2026-10-09 办公室，vivo 上“沿途 0.3.4 验收”显示蜂窝/系统联网已验证、VPN已检测，但定位已过时且等待可靠位置（约40秒）。这证明 **网络已连接并不意味着 Android NETWORK_PROVIDER 会提供新位置**。目前尚未确定究竟是系统没有回调，还是来源有效性/跳跃/时效过滤拒绝了结果。

**用户目标：** 室内、城市遮挡、弱 GPS 时更可能持续获得真实且可靠的位置，而不是“仅为代码加上 FUSED 字符串”。

**首要原则：** *先测量，再接入，不能以不可信坐标换取看似不断线的 UI。*

## 1. 接口路线与约束

- 优先使用 Android 平台原生 `LocationManager.FUSED_PROVIDER`（Android 12 / API 31 起，**设备存在时才可用**）。官方接口文档：https://developer.android.com/reference/android/location/LocationManager#FUSED_PROVIDER
- Android 8.0+ / minSdk26 **必须继续正常运行**。API31 以下保持原 GPS + NETWORK 行为；需要版本判断、存在性与权限检测，不得因接口不可用崩溃。
- **不引入** Google Play services FusedLocationProviderClient、Play SDK 强依赖、Google 地图、第三方商业 LBS SDK、地图 UI、基站或 Wi-Fi BSSID 数据库。
- 物理定位 **只来自 Android 设备 LocationManager 返回的有效位置**。不得使用 IP、GeoIP、VPN出口、代理、DNS、模型/搜索推断位置；继续保持已有源/精度/年龄/跳跃与粗位置地标护栏。
- V0.3.4 状态透明度保留，新增来源在展示上有明确 FUSED/融合标识，不能说“卫星GPS”除非实际来源是 GPS；LAST_KNOWN 应显示原始来源。
- 不随意放宽 GPS 15s / NETWORK 30s / LAST_KNOWN 最多60s 的基准时效，不通过延长过期时间掩盖无更新问题。FUSED 活跃窗口必须明确记录理由并通过回归验证。
- 低版本和没有原生 FUSED 的手机按原方案工作；缺权限/缺Provider/注册失败分别降级，不让定位全链断掉。

## 2. Stage A：定位来源诊断和真实比较（必须先做）

在保留 GPS 与 NETWORK 的基础上，对 API31+ 可用 FUSED 进行**短时间并行测量/对照**。可使用内部仪表化探针或开发者实验开关；不要把 A/B 开关做成正式驾驶 UI，也不依赖用户反复手工输入复杂命令。

在同一设备同类条件下，对照 FUSED 未启用 vs 启用：
- 室内静止（办公室场景），Wi-Fi + VPN；
- 室内静止，蜂窝 + VPN（条件允许时）；
- 室外/开阔区域；
- 位置随真实移动更新，弱 GPS →恢复（如果可安全观察）；
- 开始冷启动、停止重启、后台/切回。

针对各来源 **单独记汇总**（不记录完整坐标轨迹）：
1. 设备 SDK、FUSED 是否列于 `allProviders` / `hasProvider`，是否启用、注册结果与权限；
2. 首次有效回调时间、回调次数、最近回调年龄、精度范围/分位数（可先用简单统计）；
3. 原始 Location 收到 vs `deviceFix` 拒绝 vs `PhysicalLocationSelector.offer` 拒绝，分原因计数（缺测量时钟、无精度、过期、乱序、不合理跳跃、精度超限等）；
4. 最终被选为 Journey 位置的来源、实际年龄、最长连续无有效 fix 时间与由已知→未知的次数；
5. FUSED 是否带来 **额外新鲜有效位置**，而非只转发与 GPS/NETWORK 相同的旧测量；
6. 如可合理观察，记录明显定位/CPU/电量开销变化；不能凭短测得出“更省电”的结论。

重要：细节诊断是**短时本机测试证据**；不引入无限增长的生产日志。只记录来源、时间/年龄、精度和原因的汇总，不把真实经纬度、OAuth、原始请求体、VPN节点写进报告。

若办公室场景复现不了，必须写明“未复现/待复现”，不能把模拟回归冒充同一现场。

## 3. Stage B：有价值才成为生产第三来源

**条件式交付：**
- 若 vivo 的原生 FUSED 存在，且测量证明具有新鲜可靠的增益：以兼容、可回退的方式纳入 `RealLocationSource` 及 `PhysicalLocationSelector`；UI 显示真实来源。GPS/NETWORK 不得被无条件替代。
- 若 FUSED 不存在、总是不回调、仅镜像旧位置，或会降低有效位置连续性：不得硬加“FUSED优先”。保留原有生产双来源，并交付实测诊断与下一步建议；此结局可算作**诚实的试验交付**，但不能宣称连续性得到改善。
- 如果时间允许先做可关闭的生产候选路径，默认行为必须与对照和 Gate 结果一致。不要默认为所有型号推广实验中的设备特殊处理。

可靠性注意事项：
- `FUSED` 与 `GPS/NETWORK` 可能重复或测量顺序不同。不能让较旧 FUSED 重播/压住较新的可信 GPS/NETWORK，也不能因融合源的微小差异误判为远距离跳跃或反复抖动。
- 继续坚持测量时间单调、独立 provider 注册/移除、生命周期世代隔离、跳跃检查、质量评分与短桥接上限；保留完整 GPS→NETWORK→GPS 行为。
- 不能只因标签为 FUSED 就断言卫星级精度。精确地标仍应受有效的精度、年龄、位置来源及到达可信度约束。避免未确定底层融合来源时错误地显示“来自GPS”。
- 如果同时开启三个活跃 Provider 可能导致显著能耗/重复定位，请评估是否有必要长期开启，优先最小负担且可回退的方案。
- 保留网络/VPN状态和定位状态的独立显示；无法定位时如实显示未知。

## 4. 回归与验收矩阵

**自动 / Robolectric**
- API 26 / 30 / 31+：不存在/不可用 FUSED 时不崩溃，旧双来源完整工作；
- FUSED 正常、仅粗略权限、GPS禁用、NETWORK不回调、FUSED也不回调等状态；
- FUSED 与GPS/NETWORK 同测量重传、乱序、切换与恢复、不合理跳跃、缓存过期；
- 60s 内短暂桥接、全部来源过期后未知；不能伪造位置、擅自提升地标精度；
- UI来源与年龄真实刷新、重启/后台无残留回调；
- V0.3.4 状态/网络/AI可见性保持；Overview→Topic/Director/Voice/模拟路线回归不变。

**真实 Android / vivo（条件允许时）**
- FUSED 是否存在、注册成功、实际回调与最终被选中（或没有被选中）；
- 同一办公室场景：网络/VPN正常但此前定位过时，对比测试；
- Wi-Fi、蜂窝、VPN、室外、切换条件下来源连续性；真实情况缺测注明；
- 至少一次能说明新来源有/无增益的自然设备数据，不将 fake location/模拟测试冒充真机；
- 外场驾驶 GPS→NETWORK/FUSED→GPS、粗定位安全与长期锁屏**如未观察仍标待验收**，Issue #14 不因此自动关闭。

**构建 Gate**
- 完整 `scripts/verify.ps1 -Offline`，Debug/Release 全量测试、assemble、lint、APK 签名；
- 保留目前所有定位、位置事实、状态透明、章节研究、TTS/ASR和模拟行驶行为；
- 真机探针成功/失败的证据分开列出，绝不写“真机全部通过”但实际上只通过 Robolectric。

## 5. 安装与双电脑开发注意事项

- 办公室仓库：`H:\CODEX\KITT`。开始时确认 `main` 最新、工作树和提交关系，不覆盖未提交改动。
- 手机已使用并登录独立包 **`com.kitt.reader.runtimecheck` / “沿途 0.3.4 验收”**。本轮优先交付可由**办公室电脑现有同一签名身份覆盖升级**的 V0.3.5 验收APK，以保存现有 OAuth、设置和旅程数据；未验证可覆盖前不要卸载或清除它。
- 标准包 `com.kitt.reader` 与 `runtimecheck` 不共享数据；本轮**不顺便迁移包名、更换签名或账号**，也不要求找回旧0.3.3的签名密钥。若与原签名冲突，安全保留原数据，报告阻塞。
- Keystore/OAuth/API凭据/真实GPS轨迹/签名私钥不得入库。

## 6. Stop Rules / 交付格式

Codex 在一个小版本内自主完成：检查→短测/探针→判断是否值得生产启用→必要的最小代码改动→全量回归→真机证据→打包→更新 `HANDOFF.md` 与 `docs/ACCEPTANCE_FUSED_LOCATION.md`→提交并推送。

交付需写清：
1. FUSED可用性及实测价值：**有实质提升 / 无提升 / 无法确定**；
2. 什么代码或架构改变、什么没有改变；FUSED是否在生产启用；
3. Debug/Release 测试、模拟与真机测试各自范围与失败/未测；
4. 三来源的对照汇总、原始回调与拒绝统计（脱敏）；
5. APK、版本/包名/签名与安装升级说明，Git commit/Release链接；
6. 尚需用户现场确认的场景及最短步骤。

不扩大为导航、地图SDK、Google服务、完整日志平台、重复播报修复、知识包或播报频率调节。Issue #13/#14/#15 不因本轮开工而擅自关闭。
