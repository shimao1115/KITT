# CURRENT TASK CARD — KITT V0.3.5.1 / Issue #17

**状态：已授权施工，等待施工 Agent 实际领取与提交；非“已完成”或“已启动 Agent”的声明。**
**当前唯一施工 Destination：修复真实定位场景中行政区/街道名称闪烁。**
**任务真相源：** [Issue #17](https://github.com/shimao1115/KITT/issues/17)（现场证据、代码致因、边界与验收），本文为施工入口。
**顺序：** 完成本卡并停下人工验收 → #18 跨旅程知识缓存 → #13 语义去重 → 后续离线新都区试点。不要自行进入下一项。

## 0. 先继承现状

阅读 `AGENTS.md`、`HANDOFF.md`、`docs/ROADMAP.md`、Issue #17、最近 milestone commits；在当前 `main` V0.3.5 基础上做最小修复。旧版 TASK_CARD 的通宵 V0 全量自主构建目标已经完成历史使命；本卡取代其作为**当前**指令，禁止重做已通过的 Golden Path。

当前生产物理位置源是 Android GPS + NETWORK，FUSED 0.3.5 仅做过负结果实验，不得把其接入生产；保留已验证的稳定行为、Vosk/TTS、OAuth/Provider、模拟路线、运行状态与行政区越界护栏。

## 1. 真实痛点（不是 GPS 丢失）

用户在 vivo X200 Pro（Android 16）上原地不动，出现：

1. NETWORK 定位新鲜、精度约 ±30m；
2. 短暂显示正确的新都区街道名；
3. 一会变成“地区名称未解析”，随后又恢复，循环反复。

静态审计发现：
- `AreaChapter.kt::areaCacheValid` 只允许先前 geocode 锚点年龄 <=30s；
- `GeocodeThrottle` 在静止时可能 120s 才进行下一次请求；
- `RealLocationSource.publish()` 每秒从 `lookup.cached(selected)` 生成 `administrative`，命中空缓存即给 UI null；
- `AndroidAreaResolver.resolve` 空地址结果也会把 `resolved` 覆盖成 null；
- `DrivingScreen.placeLabel` 会切换到“地区名称未解析”，`AreaCards.accept(null)` 清空活动章节。

以上是代码存在的明确路径，**尚未用真机事件日志证明每次闪烁的单一原因**。先以确定性测试证伪/证实并修复，不需要先索要更多截图。

## 2. 本次唯一交付

在有**持续新鲜、可信物理位置**且与已验证区域锚点地理上相容时，保留最近一次成功的地区名称；刷新搜索可以后台继续，但空/错误/超时不能令可信地区闪烁。真实离开、位置过时、精度不足、提供者切换/跳点、地区新解析结果不一致时按已有安全边界正确失效/降级。

**允许的最小修改域**：`AndroidAreaResolver.kt`、`AreaChapter.kt`、`RealLocationSource.kt`、相关单元/Android测试和必要的轻量日志，视失败路径扩展一点但不能换定位架构。保持经纬度只来自设备 LocationManager，不能从 IP、VPN、DNS、搜索结果推定位置。

**禁止**：扩大为 #18 知识持久缓存、#13 重复讲述、离线包、FUSED/Play Services 定位集成、地图 SDK、数据库/后台服务/复杂状态机、新 UI 配置或大范围重构。

## 3. 必须证明的行为

- A. 静止、定位每秒新鲜、NETWORK/GPS ±30m，首次正确识别后持续 5–10 分钟地区名称不消失；跨过原30s缓存寿命及120s下一次逆地理编码时不发生 UI null / `AreaCards` 假切换。
- B. 成功 → Geocoder 空/异常/超时 → 成功：可信位置相容时继续保留经核验名称；从未成功时仍然“未解析”。新一次**可靠、不同区域**的响应必须取代旧值。
- C. 真移动到另一行政区（含100km/h场景）、距离超门槛、旧位置已过时、GPS↔NETWORK切换、粗略定位、LAST_KNOWN、应用停止/重启：不能将旧街道当新现场；必要时仅保留区县并撤销过度精确的镇街身份。
- D. 研究、行政区章节、Director、主动接管/安静、网络/VPN隔离与既有 0.3.5 定位源优先规则不回退；不要把区域名当作物理定位凭据。
- E. 无位置、无 Geocoder、无网络或系统地理编码服务临时失败时不崩溃、不请求风暴；日志简短、脱敏，不包含轨迹或认证信息。

**验收必须覆盖过时位置与实际移动的负例**，不可只测“固定坐标稳定”而导致旧区县长期挂在屏幕上。

## 4. 自主施工与质量门禁

1. 自行选择最小代码变更，提交针对问题的确定性测试。要区分本地复用、正常复查、超时/空结果与越区失效；至少构造5分钟固定位置、120s重新 geocode、移动与各种失败。
2. 在可用环境执行 `scripts/verify.ps1 -Offline`（或等价逐步执行 Debug/Release tests、assemble、lint、签名）；成都→绵阳模拟回归含快进及 GPS/NETWORK 切换。若环境不支持，明确列出未验证项，不能凭推测写 PASS。
3. 更新 `HANDOFF.md`（当前版本、原因/修复、测试结果、限制、最短人工验收、commit、APK/安装入口）和独立简短验收说明。
4. 产出 V0.3.5.1 可安装 APK 与 SHA256；**优先复用现有 `com.kitt.reader.runtimecheck` 包名与签名**进行 `adb install -r` 原位升级，保留已有 ChatGPT 登录及设置；拿不到原签名不要让用户卸载旧 App，不要声称可覆盖升级。不得提交任何私钥、凭据或原始账户数据。
5. Push 清晰 commit，反馈验收要点和仍需用户实机验证的部分。实机 vivo 室内静止5–10分钟，若无设备就明确待验收。

## 5. Stop rule

**只做 V0.3.5.1。** 修复、自测、提交、交接后停止。严禁因为已有下一张 issue 就顺手开发 #18、#13 或离线包；用户只做这一个小版本的人工验收。
