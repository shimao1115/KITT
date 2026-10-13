# KITT 签名统一：办公室 PASS / 家庭 PENDING（2026-10-13）

**当前唯一工作项 [#19](https://github.com/shimao1115/KITT/issues/19)，办公室端已完成；家庭端密钥传输、构建验签与双机原位升级均未执行，PENDING，不能声称双机统一成功。** 本次交付后停止，等待家庭端实际验证；不开展 V0.3.5.2/#18、#13 或离线包。工程提交 [`8753240`](https://github.com/shimao1115/KITT/commit/8753240ed184a8ee15c1d7420691b11184e1a30f)；脱敏证据 [signing-office-verification.json](docs/evidence/signing-office-verification.json)。

## 办公室真实结果

- **原密钥 PASS**：JDK17 `keytool` 确认 `%USERPROFILE%\.android\debug.keystore` 的 `androiddebugkey` 是 PrivateKeyEntry，SHA256 为唯一标准 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，与已安装手机证书的已有记录一致。
- **专用密钥 PASS**：原样复制至 `%USERPROFILE%\.kitt\signing\acceptance.keystore`，内部文件哈希比对相同，不输出私钥文件摘要；原全局文件保留、未覆盖或生成密钥。专用文件与目录 ACL 仅当前用户和 SYSTEM。迁移源已准备在此路径，文件未进入仓库、聊天或 GitHub。
- **预检/构建 PASS**：实际运行 `scripts/check-kitt-signing.ps1`、`scripts/build-runtime-smoke.ps1 -Offline`（assembleDebug、assembleDebugAndroidTest、lintDebug）和 `scripts/verify.ps1 -Offline`（标准 Debug/Release assemble、lint、验签）。三个生产验收 APK 的单一 signer 均为上述证书，包名/版本已由 aapt 核对；runtimecheck 测试 APK 也同证书。
- **测试 PASS**：签名回归实际执行 2 个正例 + 6 个拒绝案例：缺密钥、缺 APK、包名约束但无 APK、错误包名、未签名 APK、指纹相同但只有公开证书无私钥。测试仅临时导出公开证书并建立证书容器，已清理，不生成私钥。Debug349/Release336 测试报告失败/错误/跳过为0；两种单测任务本轮是 **UP-TO-DATE，复用有效结果，未重新执行全部单测**。
- **修复范围**：现有 `java.io.File` 在 Gradle Kotlin DSL 中编译失败，改用显式 import；Gradle 独立校验原私钥条目和证书。PowerShell 固定 keytool 输出语言、拒绝非私钥/多个 signer，检查包名并报告版本；runtimecheck 先验签再复制交付文件。构建提示不再把标准包误作手机升级包。不改业务、版本号、包名或 Provider。
- **手机数据未操作**：本轮无 adb、安装、降级、卸载、清数据、账户或设置读取/修改；没有通过验签要求卸载现有 App，也未重新验活 ChatGPT。仍为 0.3.5.1/code11，此次仅构建验证；未来正式升版时才以同包名/同证书 `adb install -r` 原位升级。既有手机 ChatGPT/gpt-6.1-sol/low 配置是下方历史证据，当前未访问；新安装默认 Fake 的行为保持。

| 办公室 APK（均0.3.5.1/code11） | applicationId | 文件 SHA256 |
| --- | --- | --- |
| [runtimecheck](artifacts/kitt-v0.3.5.1-runtimecheck.apk) | `com.kitt.reader.runtimecheck` | `2302cd463836a14e7636ef281278c1175f4e7a7412baeb23707d2e71a33ded75` |
| [标准 Debug](artifacts/kitt-v0.3.5.1-debug.apk) | `com.kitt.reader` | `183ba0fbca2fc5740d8c1a2698d56ee98b076bfb744653f48338c87a2a241d6e` |
| [标准 Release 验收](artifacts/kitt-v0.3.5.1-release.apk) | `com.kitt.reader` | `7fa9507d22e35065329a3ffc772c05fe4779f45a811db53f7ab31ac7ea354a09` |

runtimecheck 文件哈希与历史0.3.5.1交付相同；Release 重新签名后的文件哈希不同，证书相同。标准包不属于手机 runtimecheck 数据空间。APK/原始日志均在本地忽略的 `artifacts/`，不提交二进制或密钥。

## 家庭端剩余最小动作 / 停止规则

1. 本人将办公室 `%USERPROFILE%\.kitt\signing\acceptance.keystore` 通过本人控制的加密 U 盘等安全介质携带到家庭电脑；不要上传/发送该文件。办公室原件保留。
2. 家庭仓库拉最新 main，先用 `scripts/check-kitt-signing.ps1 -KeystorePath <介质源文件>` 核对，再复制到家庭 `%USERPROFILE%\.kitt\signing\acceptance.keystore` 并限权。已有不同目标时保留双方，不强制覆盖。**家庭端 PENDING**。
3. 执行预检、`scripts/build-runtime-smoke.ps1`、runtimecheck 的 `-ApkPath ... -ExpectedApplicationId com.kitt.reader.runtimecheck`、`scripts/verify.ps1` 及签名回归。完整可复制命令见 [SIGNING_UNIFICATION.md](docs/SIGNING_UNIFICATION.md)。收集家庭 PASS/FAIL 才更新 #19；现保持 open。

两机均维持 code11，此轮不安装到手机。没有新增 OAuth/API key 步骤，唯一人工交接是安全传输密钥及家庭端执行验证。家庭端依赖、环境和构建尚未实测；本轮无跨机升级数据保留新证据。#18 仍暂缓，停止等待家庭端验证。

## 成都→绵阳 / 保留基线 / 最近提交

本轮没有路线或业务变更；`verify.ps1` 复核/复制既有成都新都→绵阳安州雎水29点、90912m等确定性/Fake模拟报告，来自 UP-TO-DATE 单测结果，不当作新真机/实车验收。当前可运行的 V0.3.5.1 能力、Provider、模拟和已知实机限制保持下方历史交接。#17 工程通过、真机部分待验收（600秒中305秒新鲜、期间名称消失0），定位间断仍属 #14。

- `8753240` — #19办公室原签名构建、Gradle编译修复/独立验签、包名保护、签名正负例回归。
- `13ba91d` — 本次继承的最新 main；前置签名门禁与暂缓缓存任务说明。
- `129191f` / `65748a0` / `3d120ce` / `befd5d8` — 原项目签名配置与预检（此前未实测，本次已修复并验证）。
- `e87c6be` / `74161cf` — V0.3.5.1账号保留/自然位置证据及地区稳定工程基线。

---

# KITT V0.3.5.2 原授权状态存档（现被 #19 签名前置门禁暂缓）

**用户决定：V0.3.5.1 地区名称修复按“工程通过、真机部分待验收”阶段性收口，允许推进下一小版本 V0.3.5.2 / [Issue #18](https://github.com/shimao1115/KITT/issues/18)。** 这只是任务授权与派发，**0.3.5.2 尚无工程交付、测试结果或可安装 APK**。原先计划以 [TASK_CARD.md](TASK_CARD.md) 作为 #18 施工入口；但用户随后要求先完成 #19 双机签名统一，当前请以本文最顶部 #19 说明及最新任务卡为准。完成 #19 后才允许施工 Agent 更新本 HANDOFF 为 0.3.5.2 正式交接并停止等待验收。

- **保留的 V0.3.5.1 基线：** 0.3.5.1/code11 已原位升级、ChatGPT 账号配置与设置保留。Debug349/Release336、构建/lint/签名/模拟是上一版本的工程报告，不能作为下一版测试结果。
- **真机未签 PASS：** 600秒自然采样仅305秒新鲜定位；这些新鲜条件下名称消失0，但仍未完成连续镇街及实车跨区域/来源切换验收。#17 保持 open 跟踪剩余验收；定位新鲜度/连续性归 #14。不得通过延长过期定位有效性伪装解决。
- **本次目标：** 有来源且已验证的地区概况和专题，跨 Journey 和 App 重启本地命中并复用，命中时0次重复 Overview/Topic 搜索；保留 Director 的新旅程判断及当前性问题实时搜索。
- **禁止混做：** 本轮不修 #14、#17、不实施 #13 语义去重、不启动离线包/本地大模型。完成 #18 后必须停止并留下复验方法、实际测试数据、提交和安装包情况。
- **历史交接说明：** 以下 V0.3.5.1 的“停止、不进入 #18”仅是其当时的里程碑 stop rule，现已被用户对 #18 的新授权取代；其实际测量与安全限制依旧有效。

---

# 沿途 V0.3.5.1 — 地区名称稳定性修复交接

截至 **2026-10-12（Asia/Shanghai）**，**0.3.5.1 / code11**，Issue #17工程交付完成；设备上线后已原位升级并确认保存的账号/设置保留。**室内十分钟自然采样仅305/600秒满足新鲜位置条件，不能签完整静止稳定PASS；新鲜位置下名称消失为0，镇街连续显示尚未取得证据。到此停止，不进入#18、#13或离线包。**

工程commit：[`74161cf`](https://github.com/shimao1115/KITT/commit/74161cf3f4b263cbddaefd96c9fa45b5305c2e0a)。独立说明：[地区稳定性验收](docs/ACCEPTANCE_AREA_STABILITY.md)；上一版本：[V0.3.5交接存档](docs/HANDOFF_V035_FUSED_LOCATION.md)。

## 设备上线后的实际结果

- vivo V2405A / Android16，用户确认室内原地可保持前台。`adb install -r` 成功：0.3.5/code10 → 0.3.5.1/code11；手机拉回base.apk的SHA256与交付runtimecheck包完全一致。没有卸载原App、清数据或导出凭据。
- 安装前、安装后、探针结束的settings.xml摘要均为 `8250181a64bc67deae3f02e95789057bc80fdb23552eb56162ece5fac15c7056`。保存的ChatGPT记录可解密，connected=true、credentialUnavailable=false；仍为ChatGPT / gpt-6.1-sol / low，语速1、独立研究关闭。本轮不调用账号网络服务，不声称登录有效期已验活。当前无未结束旅程恢复记录，未伪造或清除旧旅程。
- 600次每秒观察，实际613309ms；600/600前台、屏幕交互且解锁。生产RealLocationSource、生产DrivingScreen组件与临时Journey/AreaCards，未启动真实旅程/Director/研究、未注入坐标或切换网络/VPN。92次发布，首次成功1507ms；新鲜有效NETWORK仅305秒，精度30–45m、年龄最大29654ms、距首次锚点最大17.54m。
- 新鲜位置下名称消失 **0**；所有状态合计235次名称缺失，均非新鲜位置，不可把它们包装成全程稳定。40次界面抽查24次匹配地区标签、0次“地区名称未解析”；16次未匹配，没有逐次记录等待文案。3次章节进入不能称“只有一次”：失去现场/重新解析后再次进入是允许的失效路径，未收集逐次转移因果日志。截图只确认区县“新都区”，没有完整镇街稳定证据。
- 判定 **POSITION_GAPS_REQUIRE_REVIEW**，而非全程PASS。最新回调来源计数不等于持续有效位置；过期LAST_KNOWN可留在最后回调，fresh计数另校验等待状态/年龄/精度。设备未暴露KITTArea日志，报告geo事件0表示不可观测，不能当作0次查询/失效；真实120秒复查次数未建立。

[脱敏真机报告](docs/evidence/v0351-area-stability-phone.json)。探针首次活动复用等待被终止，未作为验收；修正后的十分钟窗口才计入以上数据。最后仅补充日志不可观测说明，完成测试APK构建/lint，未重新真机运行该文字补充；采样逻辑未变。测试APK已卸载、KEEP_SCREEN_ON已清理、无测试JourneyService，普通应用入口已恢复；App仍0.3.5.1，账号设置摘要不变。

## 原因、修复与当前能力

修复前新增确定性测试在第31秒失败，证明30秒geocode锚点寿命与静止120秒复查产生名称空窗；空结果覆盖成功值也是确定代码路径。没有用真机事件日志证明每一次闪烁都由此导致。

地区缓存仅含本次运行的一个成功锚点：当前持续新鲜、可信、同来源、距成功锚点≤450m时保留名称，空/异常/超时不覆盖；可靠不同区域或仅区县结果直接替换。复查按原时间/距离节流，间隔使用单调时钟。位置过时/缺失/观测中断、来源变化、越距撤销缓存；回原地或回切不能复活已失效名称，晚结果受失效版本和当前位置校验。粗定位撤销镇街，LAST_KNOWN只带不确定区县背景，不查询、不保留镇街。缓存不持久化，应用/旅程重启不能携带旧名称。

GPS活跃15秒、NETWORK活跃30秒、LAST_KNOWN原测量最多60秒，物理定位选择、跳跃/乱序、行政越界护栏保持。生产只注册LocationManager GPS+NETWORK，FUSED不接入。经纬度不受IP/VPN/DNS/搜索影响。新增日志仅事件/失效原因，无坐标、地区名、轨迹或认证信息。

Vosk/本地TTS、主动说话立即打断、打字/看图、安静/提前恢复、研究Overview→Topic、Director、模拟路线、运行状态与结束评分保持；没有扩展知识缓存、语义去重、地图栈或新UI配置。

## Build / tests / 成都→绵阳

实际执行 `scripts/verify.ps1 -Offline`：**Debug349 / Release336 tests，失败、错误、跳过均0；两种assemble、lint、Debug/Release验收签名全部PASS**。新增20项执行覆盖生命周期、刷新失败、连续性与负例；旧驾驶解析测试修改为验证当前位置新鲜度，450m与过时负例保留。runtimecheck独立工作区的assembleDebug、assembleDebugAndroidTest、lintDebug也PASS；首次离线交付只构建测试APK，接续仅执行area-stability模式，未重跑其它手机模式。

- GPS/NETWORK各600秒新鲜同地缓存测试，AreaCards只进入一次；生产RealLocationSource链的合成NETWORK ±30m测试连续600秒，**302次发布 / 6次查询 / 首次成功后0次null或镇街消失 / 1次章节进入**，跨过空、异常、8秒超时及恢复。
- 从未成功300秒保持未解析，仅3次查询；可靠不同区县即时替换。100km/h越450m、旧位置过时、观测缺口、GPS↔NETWORK切换、粗定位/粗权限、LAST_KNOWN、停止/重启及晚结果均覆盖。Geocoder缺失在API26/34不崩溃。
- 六组模拟PASS：成都新都→绵阳安州雎水 **29点 / 90912m**；80km/h主链57次Director/4次TTS、Context Card1489字符；100km/h/16×研究 **13章节 / 19次研究 / 13段旁白**，零stale/cancel/failure。快进、素材架、独立地标与驾驶解析回归通过；GPS↔NETWORK另由确定性设备回调覆盖。这是Fake/确定性Provider、fake Voice与生产Context/Journey/Director，不能替代真实路线、搜索、自然GPS或听感。

入Git的脱敏证据：[验证摘要](docs/evidence/v0351-verification.json)、[十分钟发布报告](docs/evidence/v0351-area-stability.txt)、[SHA256](docs/evidence/v0351-SHA256.txt)。本地全量日志：`artifacts/area-stability-delivery-gates.log`、`area-stability-runtime-build.log`、`area-stability-red.log`与六份模拟报告。私钥、二进制和原始账户数据不入Git。

## APK / 安全升级 / Provider

[V0.3.5.1预发布与校验文件](https://github.com/shimao1115/KITT/releases/tag/v0.3.5.1)。已有手机账号使用 **runtimecheck** 包；标准Debug/Release是另一应用数据空间。不要卸载旧App或清数据。

| APK | bytes | SHA256 |
| --- | ---: | --- |
| [runtimecheck原位升级包](artifacts/kitt-v0.3.5.1-runtimecheck.apk) | 74279072 | `2302CD463836A14E7636EF281278C1175F4E7A7412BAEB23707D2E71A33DED75` |
| [标准Debug](artifacts/kitt-v0.3.5.1-debug.apk) | 74279048 | `183BA0FBCA2FC5740D8C1A2698D56EE98B076BFB744653F48338C87A2A241D6E` |
| [Release验收包](artifacts/kitt-v0.3.5.1-release.apk) | 70642112 | `5EFC4913185CEE39F652552BC5027A3F2ABB5B22E581EDFBBF61193EB0AF3B89` |

aapt已核对runtimecheck包名 **`com.kitt.reader.runtimecheck`**、code11、version0.3.5.1、标签“沿途0.3.5.1验收”。三包apksigner验证成功，证书SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**，与手机升级前APK和本机密钥匹配。**设备上线后已执行adb install -r并成功，已核对账号记录可解密和设置保留；无需再卸载/重装。**

当前真机保存配置为 **ChatGPT / gpt-6.1-sol / low**、语速1、独立研究关闭；只在设备内读取连接布尔状态，不导出账户记录/凭据，未验活网络登录或调用真实AI。新安装仍默认Fake（配置字段gpt-4.1-mini/default，不调用该模型）。没有新增授权/API key步骤；若手机原账号后续过期，重新授权须本人完成。

## 最短人工验收与限制

1. 手机已升级，直接打开“沿途0.3.5.1验收”；账号/设置已保留，无需再安装。使用普通“开始沿途”进入真实Journey/前台服务。
2. 室内静止、保持前台与新鲜NETWORK/GPS ±30m，首次解析成功后连续5–10分钟看地区名称，跨30秒和120秒复查不消失、不出现章节假切换。无首次成功时“未解析”仍是正确降级。
3. 停车或乘客操作，验证临时断网/服务失败保留可信同地名称；真正移动、失去新鲜定位、切换来源时不能保留旧街道冒充新现场，可靠新解析应更新地区。切换/失效后可能暂时未解析，复查仍受原节流约束。
4. 主动说话/打字打断、安静/提前恢复、100km/h/16×模拟与结束冒烟。普通真实Journey/前台服务完整运行、持续新鲜位置≥5分钟且曾成功取得镇街、室外/真实边界、GPS/NETWORK恢复、真实失败注入、真人内容/ASR/TTS与长期锁屏仍待验收。#14/#15独立开放。本次设备自然位置间断不能通过延长旧标签信任或换FUSED在#17中绕过。

450m是沿用的保守距离护栏，并非行政边界数据库。Android26–32同步Geocoder可能不能被协程立即终止，期间只保留一个pending，GPS持续，晚结果仍校验；8秒超时自动证据来自可取消注入查询，vivo Android16走平台异步接口。无已知新增生产崩溃；自动测试不能替代真实设备总验收。

## 最近 milestone commits

- 本次 `test: record V0.3.5.1 safe phone upgrade and natural location gaps` — 自然位置/生产屏幕临时采样探针、同签名升级与账号设置保留、305秒有效现场及剩余验收边界；生产APK/hash不变。
- `74161cf` — V0.3.5.1 / #17地区缓存连续性、失败保留、失效/晚结果防护、20项新增执行与打包门禁。
- 随后的 `docs: hand off V0.3.5.1 area stability for offline-phone acceptance` — 当前交接、脱敏证据、安装/待验收与Stop rule。
- `83c2275` — V0.3.5.1当前唯一任务卡。
- `1329bb1` / `7644286` — V0.3.5 FUSED负结果实验与账号保留交接。
- `d841c2e` / `9fbc88e` — V0.3.4运行状态与交接。
