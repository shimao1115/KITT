# KITT V0.3.5.2 用户阶段性验收决定（2026-10-13）

**用户接受阶段性结果：工程 PASS；手机安全升级、账号设置保留、真实新都区 Overview 跨进程缓存 PASS。#18 仍 OPEN，以补齐镇街/Topic、持续在线讲述和真人语音的现场证据；这不是“手机总验收 PASS”。** [用户验收决定已记录在 Issue #18](https://github.com/shimao1115/KITT/issues/18#issuecomment-6137058814)。

- 真正手机链路：自然完整区县身份，首次研究成功 `write=1` → 结束并 force-stop → 新进程相同区县 `hit=1` / 重复 Overview 研究调用 `0`；Director 新旅程机会重建。
- 今日天气问题走实时搜索，但资料校验失败后明确无法确认：**实时路由和诚实降级 PASS，天气答案质量及持续在线能力不在 PASS 范围内**。网络异常与合规材料校验失败根因尚未确定。
- 镇街身份未建立、Topic 未自然研究，二者跨重启性能仅有合成回归，真实验收依旧 PENDING；真人 ASR/TTS 听感、播放中打断和持续在线讲述 PENDING。
- **施工状态：停下，不重新开发已通过区县缓存，不自动开启 #13、离线包或额外 Hotfix。** #14 / #17 外场定位、#19 家庭签名继续独立跟踪。本文件下方的“等待用户决定”是作决定前的历史记载。

---

# 当前验收任务：V0.3.5.2 / #18 真实手机原位升级与缓存复用（2026-10-13）

**实际验收：2026-10-13 10:28–10:37（Asia/Shanghai），vivo V2405A / Android16。安全原位升级 PASS，账号/设置保留 PASS，真实新都区 Overview 跨进程缓存 PASS；镇街、Topic、真人语音与手机总验收 PENDING。#18 保持 OPEN，等待用户最终决定。** 脱敏实测见 [验收记录](docs/ACCEPTANCE_RESEARCH_CACHE.md#2026-10-13-真实手机验收) 和 [手机计数摘要](docs/evidence/v0352-phone-acceptance.json)。

- 办公室现成 APK 的摘要、唯一签名、包名/code12 和原手机 code11/同证书全部实核；`check-kitt-signing.ps1` PASS。仅执行 `adb install -r`，返回 Success；安装后手机 base.apk 摘要一致，数据目录标识和首次安装时间不变。未卸载、清数据、降级或替换标准包。
- 常规设置界面升级前后账号显示在内存中比对一致；ChatGPT、GPT-6.1-Sol/low、语速1.0×、系统默认中文声音、通知允许、独立 Responses 研究未启用均保留。没有读取或导出凭据/原始账号文件。首趟真实研究成功证明当次在线能力；不保证后续网络恒定可用。
- **区县实测链**：自然新鲜 NETWORK、完整四川省/成都市/新都区身份；首趟 Overview研究调用=1、搜索开始/完成=1/1、来源校验完成、facts=4、write=1 → 正常结束 → `am force-stop` 后进程不存在 → 新进程普通开始、新鲜同区县 → overview hit=1、重复 Overview研究调用=0；Director自动启动2次。没有套用合成0/0/0。镇街身份未建立，Topic未自然完成，各自PENDING。
- 新进程自动Director先出现 STALE、随后 `UnknownHostException`/FAILURE；缓存仍成功载入并无重复研究。主动打字问一次今日天气，Director判 SEARCH_REQUIRED，实际搜索开始/完成=1/1，但结果校验 `IllegalArgumentException`，App明确显示“刚才没查到可靠资料，暂时无法确认。”，语音完成回调=1；没有写稳定缓存。实时路径/诚实失败 PASS，今日天气内容 PENDING；不自动重试或修复别的issue。
- 启动/结束页/回到出发页、安静10分钟/提前结束、打字提交短测PASS；TTS有实际开始和完成回调，但真人听感、实际说话识别和播放中打断未签PASS。观察窗口无 AndroidRuntime FATAL EXCEPTION；不是无限期无崩溃保证。手机已正常结束旅程并停在出发页。
- 本轮仅验收与文档，未重建或重跑既有工程测试、模拟。下面保留安装前工程交付历史；Debug365/Release352和成都→绵阳六组模拟仍仅代表确定性/Fake结果。没有新密钥或OAuth待办；最小用户动作是审阅本次阶段性结果并决定接受与否，真人语音/连续镇街按现场可用性补验。#14/#17外场与#19家庭签名仍PENDING，不开发#13或离线包。

---

# 安装前工程交付历史：沿途 V0.3.5.2 — 地区研究证据缓存交接

**2026-10-13（Asia/Shanghai），0.3.5.2 / code12，#18 工程交付 PASS；手机原位升级、杀进程重启与真实内容验收 PENDING。到此停止，等待一次小版本人工验收。** 工程提交 [`f7cbd6b`](https://github.com/shimao1115/KITT/commit/f7cbd6bd21bfc33994a3d35fb2c23b4ddac5bbd3)，[精简验收](docs/ACCEPTANCE_RESEARCH_CACHE.md)、[本版验证摘要](docs/evidence/v0352-verification.json)、[零重复研究对照](docs/evidence/v0352-research-cache.txt)。#18 保留 OPEN 等待人工验收，未启动 #13、离线包、定位重构或下一轮 UI。

## 当前可运行能力

有来源并经既有 completed-hosted-search/provenance 契约核验的 Overview/Topic 保存至私有 filesDir/research-evidence-v1；完整 AreaIdentity.key 区分区县/镇街，Topic 按地区与格式规范后的主题取。版本化 JSON、内容摘要校验、同目录原子替换；单项40000 UTF-8 bytes、128项、2MiB。坏/旧/空/假/无依据或错误地区资料不写入，存储失败不阻止成功研究用于本次讲述。模拟/Fake 不读写生产证据。

结束或重新开始只清运行态；下趟从磁盘载入证据、重新产生 Director 机会，不恢复 examined、pending、已播去重或固定旁白。历史证据明确“此前有来源的研究（本次未联网复查）”与原 searched_at；稳定事实不按 start/TTL 强制重查。明确查最新/重新研究以及开放、票价、天气、路况、临时展览和时事仍走实时研究，失败简短报告，动态答案不进入稳定缓存。未增加离线 Director：远程 AI 全断网时自动模式安静降级。

既有 GPS+NETWORK、行政区连续性和新鲜度、模拟路线、语音/打字/看图接管、安静/提前恢复、ASK_USER、PREPARE、评分保持。没有修改 Fix 老化、定位来源、FUSED、VPN/IP 边界或语义去重。没有原网页、请求、对话、录音、轨迹或凭据落入证据缓存。

## 本版实际 Build / tests / 签名

- 最终 `scripts/verify.ps1 -Offline` 实际执行：**Debug365 / Release352**，失败/错误/跳过均0；新增16项缓存测试在两种 Variant 均执行。单独相关 Debug29项（缓存16 + 主动研究13）也实际通过。
- 最终 Debug/Release 的 tests、assemble、lint 均 EXECUTED PASS，标准包验签 PASS。Gradle最终115任务：33 executed / 82 up-to-date；未变资源/依赖任务复用，本版单测任务没有复用0.3.5.1结果。首轮全量也通过；对齐旧系统提示/设置说明后以上最终源再次完整运行。
- `scripts/build-runtime-smoke.ps1 -Offline` 实际执行 assembleDebug、assembleDebugAndroidTest、lintDebug并验签：75任务，25 executed / 50 up-to-date。runtimecheck生产与测试APK证书检查 PASS；**仅构建测试APK，没有执行手机 instrumentation**。
- `scripts/test-kitt-signing.ps1` 实际执行2正例 + 6拒绝例 PASS（缺密钥/缺APK/仅包名约束/错包名/未签名/只有匹配公钥无私钥）。仅临时公开证书样本，没有导出/提交原私钥。
- 缓存合成对照：首次区县 Overview=1、镇街 Overview=1、Topic=1，write=3；clear/end后新管理器+新磁盘存储对象、研究 Provider拒绝网络：同三类 fetch=0/0/0，hit=3，新旅程 Director实际再次判断。**这是磁盘重建测试，不是手机杀进程或真实 Tokens 采样。**

## 成都新都 → 绵阳安州雎水：六组本版回归

全部由最终完整单测重新执行，确定性/Fake Provider与fake Voice，非实车/真模型；路线29点、90912m，为粗粒度测试折线。当前报告头部、时间与摘要在验证JSON，原始报告在本地 artifacts。

| 报告 | 本版结果 |
| --- | --- |
| full-simulation | 80km/h、68模拟分钟；57 Director调用、4 TTS；接管/安静/结束 PASS |
| accelerated-simulation | 100km/h、16×；22调用、22声音输出；零 stale/cancel/failure |
| batch-simulation | 13章节，23选题/23调用；零 stale/cancel/failure（不沿用历史14段数字） |
| landmark-simulation | 13章节，4独立地标，28调用；零 stale/cancel/failure |
| dossier-simulation | 13章节、19唯一研究、13讲述、33调用；零 stale/cancel/failure |
| driving-area-resolution | 700m合成章节；80/100km/h与报速0条件12/10查询，最短16s；非真实Geocoder路线 |

#17 的600秒新鲜合成定位/120秒刷新失败等回归及 #14 的 GPS/NETWORK、粗/旧位置、用户打断、ASK_USER、安静和过时护栏均包含在本版全量通过结果中；真机外场仍各自未全验收。

## 办公室同签名交付 APK

三个生产APK均 **versionName=0.3.5.2、versionCode=12、单一 signer**，证书 SHA256：
`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

| APK | applicationId | APK SHA256 |
| --- | --- | --- |
| [手机 runtimecheck](artifacts/kitt-v0.3.5.2-runtimecheck.apk) | com.kitt.reader.runtimecheck | 4e9526b932801ca000c00ee552e086d74820023ccf48580b31d328da92fa7b88 |
| [标准 Debug](artifacts/kitt-v0.3.5.2-debug.apk) | com.kitt.reader | 6e48fec824f372a122f29faa6bbdfad92cdd35f6b2e4f33e1244401a9dec87ad |
| [标准 Release验收](artifacts/kitt-v0.3.5.2-release.apk) | com.kitt.reader | 4adb512bc09b9d542a16c49673d8d9d628ac0c94d33687e3f4f425cfd31400c2 |

标准包不属于原手机账号空间；交付APK和原始构建日志在本地忽略的 artifacts，未提交二进制或密钥。办公室通过不等于家庭端通过。

## 最短原位升级与人工验收

本轮**没有 adb、连接手机、安装、卸载、清数据、账号/设置/凭据读取或真实 Provider请求**。手机仍以已有0.3.5.1/code11历史记录为基线，本版实际升级 PENDING。

1. 在办公室核对 runtimecheck 文件摘要和上述证书、包名，确认手机已有版本仍低于code12，再于仓库根目录运行：
   `adb install -r artifacts\kitt-v0.3.5.2-runtimecheck.apk`
   保留原App，不卸载、不清数据，不使用标准包替代，也不降级。
2. 用现有账号普通“开始沿途”，在新鲜完整新都区/镇街身份下首次成功研究概况与一个专题后结束；观察 write，再杀进程重启、同地开始：区县/镇街/已有专题 hit，重复研究调用=0；Director仍做本趟判断。已研究对象再次讲述不等于缓存失败，#13本轮未做。
3. 问查最新/今天开放/票价/天气等，确认实时研究或明确失败；安静/提前恢复、说话/打字打断、ASR/TTS与结束正常。不要为证明命中反复触发真实昂贵搜索。详见精简验收文档的5步检查。

## Provider / 用户最小待办 / 已知限制

- 新安装默认仍为 Fake / gpt-4.1-mini / effort空；未改设置默认或 Provider架构。原手机已保存 ChatGPT / gpt-6.1-sol / low 是0.3.5.1历史证据，本轮未读取或验活；同包名同签名递增升级应保留数据，**本版保留结果尚未实测**。
- #18无需新增密钥/登录步骤。剩余最小动作是上述原位升级与小版本人工验收。真实新都内容/来源质量、手机杀进程重启、OEM文件系统断电行为、真人ASR/TTS及Tokens节省比例均未测；已有来源URL核验不等于独立语义事实审计。
- #17 真机连续镇街仍待验；既有600秒自然现场仅305秒新鲜、名称缺失与定位间断结论不重写。#14室外GPS↔NETWORK独立待验，不用延长旧Fix掩盖。
- **#19保持 OPEN/PENDING：办公室签名已 PASS，家庭端本人安全携带密钥、复制和验签未做，非本轮阻塞。** 最小家庭动作见 [SIGNING_UNIFICATION](docs/SIGNING_UNIFICATION.md)；不要发送或上传私钥。

## 最近 milestone commits

- `4b1f061` — 本轮安全code12原位升级/真实区县缓存验收任务交接，随后实测结果见本文件顶部。
- `32fd345` — V0.3.5.2工程交付、签名APK与合成验收交接；手机结果当时PENDING。
- `f7cbd6b` — V0.3.5.2/#18有来源地区研究持久化、16项新测试、历史证据/实时语义与code12。
- `8faf246` — 最新恢复#18授权，家庭签名后续不再阻塞。
- `8753240` — #19办公室原密钥构建/验签与签名正负例。
- `74161cf` — V0.3.5.1行政地区稳定工程基线，实机部分仍待验。

旧交接原文可查 [施工前HANDOFF存档](https://github.com/shimao1115/KITT/blob/8faf246/HANDOFF.md)。当前只等待V0.3.5.2人工验收，不继续下一Destination。
