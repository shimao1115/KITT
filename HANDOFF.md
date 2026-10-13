# KITT V0.3.6 / #13 工程交付 — 停止等待验收（2026-10-13）

**工程施工、全量测试、六组模拟与原签名打包完成，代码提交 [`0cc7e68`](https://github.com/shimao1115/KITT/commit/0cc7e68a867742852c46e000fdc506386eab3862)。用户恢复手机连接后，本版真实ChatGPT→自动旁白→TTS引擎完成已取得一次证据；正常设置已核对。用户反馈“没听到或当时不在旁边”，真人听感、同义去重、补充/重播仍PENDING。停止手机操作，#13保持OPEN等待本小版本人工验收，不自动开启下一版本。** [精简验收](docs/ACCEPTANCE_NARRATION_MEMORY.md)、[本版实际验证](docs/evidence/v036-verification.json)、[手机阶段性事实](docs/evidence/v036-phone-acceptance.json)。下方旧授权/旧交接只作历史。

## 当前可运行能力

同趟最近32条覆盖分开记录地区、对象、事实/角度标签、landmark_id与播放生命周期，不再将topic与memory_update混用。相同对象已覆盖标签的子集/重排不自动重讲，同对象新事实、不同对象、跨区同名与独立地标仍有机会。单一Director在原请求中判断换措辞/别名/Overview→Topic回流的语义重复，沿用对象title和事实标签；没有新Schema、额外语义模型、Reviewer、向量库或数据库。

排队、真正onStart、最终成功回调、失败、未开始取消、开始后打断分别记QUEUED/STARTED/COMPLETED/FAILED/CANCELLED/INTERRUPTED；仅成功完成进入已讲摘要，epoch和语音序号拒绝旧/重复回调。失败/半截不冒称完整听过，也不自动重放。**引擎完成不证明真人听见；模型将同义事实改成全新对象/标签仍可能逃过本地防线，真实语义/听感待验。**

“再讲一点”复用原主动查证链，只补未覆盖材料，重复/无可靠新料简短说明；普通问题/看图正常接管。“再说一遍”本地重播本进程最新一段，0额外Director/研究调用；只留这一段内存正文，不写恢复文件，结束/新Journey/进程重建清空。旧恢复摘要仍兼容，但标为未验证背景，不变成完成覆盖。PREPARE/安静/用户中断/过时与来源规则保持；#18持久来源缓存不作为已讲记录。独立地标只消费额外唤醒，空间有效时新事实仍可用。

## 本轮实际 build / tests / 回归

- 最终 `scripts/verify.ps1 -Offline`：**Debug395 / Release382，失败、错误、跳过全0**；新增30项两种Variant均实际执行。Debug/Release的tests、assemble、lint均EXECUTED PASS，生产包签名PASS；115任务33执行/82复用。没有复制旧版365/352成绩。编译器既有deprecated警告不影响通过。
- 工程交付时 `scripts/build-runtime-smoke.ps1 -Offline`：assembleDebug、compileDebugAndroidTestKotlin、lintDebug均EXECUTED PASS，75任务13执行/62复用；assembleDebugAndroidTest首轮EXECUTED PASS、工程复核UP-TO-DATE。生产/测试APK唯一原证书，40个生产Kotlin文件与最终施工源码逐一一致。恢复手机后仅修正测试Probe文案及断言，再构建12s：compileDebugAndroidTestKotlin、assembleDebugAndroidTest、lintDebug EXECUTED PASS，assembleDebug UP-TO-DATE，75任务7执行/68复用；测试包安装并实际instrumentation返回code0。生产APK摘要不变，本次接续未重跑上述全量单测/模拟。
- `scripts/test-kitt-signing.ps1`：2接受+6拒绝实际PASS，未生成/导出/提交私钥。旧实现两条红测确实失败；迁移播放前题材历史/整节点去重旧断言后最终全量通过。
- 同一粗粒度路线13章刻意3次检查：**26新对象/事实输出、13重复SUPPRESSED、SILENT0、STALE/FAILURE0**。这只是标签/状态确定性证据，不是真模型语义。
- 成都新都→广汉→德阳→绵竹→绵阳安州雎水六组前后均本轮实际执行：full57调用/4声音；accelerated22/22；batch13章/23主题/23调用；landmark4节点/28调用（SPEAK4/SILENT24）；dossier13讲述/33调用/19唯一研究（SPEAK13/SILENT20）；driving-area-resolution合成700m章节80/100km/h含报速0为12/10查询、最短16s。前后计数保持，正常组无STALE/cancel/failure；Fake/假Voice、29点90912m，非实车/真人内容验收。详表与报告时间/摘要见验证JSON。

## V0.3.6/code13 本地原签名产物

三个生产APK均0.3.6/code13，唯一证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。APK及原始构建日志只在本地忽略的artifacts，未提交到GitHub。

| 文件 | 包名 | APK SHA256 |
| --- | --- | --- |
| [手机runtimecheck](H:/CODEX/KITT/artifacts/kitt-v0.3.6-runtimecheck.apk) | com.kitt.reader.runtimecheck | 28795780faeff52b394255cd4c73aa540d64572d4ce72304638f084f48626a81 |
| [标准Debug](H:/CODEX/KITT/artifacts/kitt-v0.3.6-debug.apk) | com.kitt.reader | 7a536b523c9832a2a53b60d1bdc57ac632ff088275dd7bccd52f015794f451af |
| [标准Release验收](H:/CODEX/KITT/artifacts/kitt-v0.3.6-release.apk) | com.kitt.reader | 5bf3df44ccef5f59413aa4ee616ac7bf751158579a464b2f4934aa7c89aa161f |

标准包不是原手机账号的数据空间，不能替换runtimecheck。若在其他未升级设备安装，先核对原包名/证书和现有code<13，再用 `adb install -r artifacts\kitt-v0.3.6-runtimecheck.apk`；不卸载、不清数据、不降级。原手机已code13，无需重复安装。

## 手机事实 / Provider / 用户最小待办

- 原手机code12/base.apk已核同证书、同包名。用户本人允许vivo“超级守护”安装后，`adb install -r`返回**Success**；已核0.3.6/code13，更新时点11:34:03，首次安装时间2026-10-09 09:42:45不变。没有卸载、清数据、降级或标准包替换。
- 恢复手机后的正常UI核对：已连接账号1个、ChatGPT/GPT-6.1-Sol/low、语速1.0×、系统默认中文声音、通知允许、独立Responses关闭，均符合既有设置；Probe内配置前后相同。账号文字与暂存的**升级后**UI在内存中比对一致；code12→13精确账号文字比较未留存，不能补造。原UI脚本检查过早：暂存界面仍是“等待账号模型列表”，模型/effort控件不存在，足以令原检查失败；不据此推断数据丢失。未读取/导出凭据或原始账号文件，临时手机UI XML已删除。
- **真实自动讲述链 PASS_ENGINE_CALLBACK_ONLY**：修正后Probe观察98s、新鲜位置98s，ChatGPT/gpt-6.1-sol/low自然地区自动SPEAK_NOW1，STARTED→COMPLETED1，STALE/FAILURE/SUPPRESSED均0。简短主题为“杨慎（杨升庵）与升庵祠、桂湖”。没有注入坐标/移动，测试临时旅程偏好不问目的地；不是连续行驶或真人语义验收。终端研究字段NEVER_ATTEMPTED不代表无研究：当次诊断见后台Topic started，未证明Topic完成或网络调用数，#18不改PASS。
- 首轮Probe测试文案“无资料可安静”误触安静命令，180s观察/170s新鲜位置但无讲述，该次无效，不算去重成功。仅测试文案改为“无资料则SILENT”并断言非安静；生产代码及APK未变。另一次普通UI开始自然ASK_USER/TTS完成，并有地区就绪前STALE，单独记录，不混入上述旁白计数。有效Probe正常结束并回出发页；进程重启清除了临时诊断日志，只留脱敏结果，不把空日志计数冒充0。
- **真人听感/同路段语义/补充/重播PENDING**：用户回答“没听到或当时不在旁边”；既不能证明听见，也不能判定无声故障。没有为补听继续调用模型。新安装默认仍 **Fake / gpt-4.1-mini / effort空**；原手机既有 **ChatGPT / gpt-6.1-sol / low** 已实际运行，本版无新增OAuth/密钥步骤，生产包与测试包vivo安装授权均由用户完成。
- 用户随后明确要求“再次调用一次我再听听看”，另运行一次有界Probe：180s观察/171s新鲜位置，自动FAILURE1、SPEAK_NOW/COMPLETED均0，Provider返回`server_is_overloaded`，未触发TTS；instrumentation code0、配置不变，正常结束。该次是请求失败，不是无声或去重证据；不覆盖上一轮成功链，未继续手动重试。[本次结果](docs/evidence/v036-phone-listening-retry.json)。
- 下一次最小动作：停车/乘客状态普通开始，实际听完一段后，在**同一Journey、结束之前**试“再说一遍”和“再讲一点”，检查同段不换词重复、新事实仍开口及打断/安静；再结束/新Journey。当前Probe已结束，上一段重播正文按设计清空。不要为刷日志重复付费搜索。**本轮到此停止。**
- #18真实区县缓存的阶段性接受仍有效，镇街/Topic及真人语音未全验收；#14/#17外场定位、#19家庭签名仍独立PENDING。未做定位、网络/天气校验修复或V0.4.0离线包。

## 最近milestone commits

- 本次接续 — 仅修正测试Probe安静误触、归档code13真实讲述和设置核对；生产APK保持不变。对应提交见Git历史。
- `5d48b31` — 原工程交接及用户暂停手机验收的历史记录。
- `0cc7e68` — V0.3.6/#13覆盖记忆、诚实播放生命周期、补充/本地重播、地标新事实、30项新回归、code13与本版证据。
- `1e17de1` — 当前任务卡/授权基线，只开V0.3.6，保留V0.3.5.2阶段性结果。
- `519743f` — code12真机原位升级及真实区县跨进程缓存验证。
- `f7cbd6b` — V0.3.5.2/#18持久有来源研究、code12。

**STOP：工程工作及一次真实自动讲述引擎链已完成，等待用户实际聆听与本小版本验收决定。Issue #13保留OPEN，不自动进入下一Destination。**

---

# 历史：V0.3.6 讲述语义去重授权入口（2026-10-13）

**用户已授权下一小版本 V0.3.6 / [Issue #13](https://github.com/shimao1115/KITT/issues/13)，当前唯一任务 [TASK_CARD.md](TASK_CARD.md)。这是施工入口和授权，不表示 Agent 已运行、功能已实现或 APK 已产出。** 前一版本关于“不得自动开 #13”的 stop rule 已在小版本阶段性验收后由本次**明确授权**取代；前一版未完成的验收和安全约束依旧有效。

- **正式目标**：纠正 Journey `topic` 与 `memory_update` 混用，避免重复讲同一对象/事实/角度，只有确有证据时记录播报完成；“再讲一点”提供新证据、“再说一遍”允许主动重播。禁止压成整趟 SILENT，不添加额外语义模型判重、向量库或大规模架构。
- **基线**：手机当前 `com.kitt.reader.runtimecheck` V0.3.5.2/code12；区县新都区真实 `write=1 → force-stop → hit=1 / 重复Overview研究=0`、账号/设置保留已获用户阶段性 PASS；#18 镇街/Topic、持续在线讲述/真人语音未全验收，不因升级改成 PASS。
- **要先区分真实音频与网络故障**：先看当下真实 Provider 是否能完成至少一段自动 Director→TTS 讲述；上版真机出现 `UnknownHostException` / `STALE` 与天气资料校验 `IllegalArgumentException`，这些不是证明语义去重有效或无效的依据。若当时网络无法可靠使用，不阻塞单测和模拟开发，但真机听感必须标 PENDING。
- **同签名原位升级边界**：建议V0.3.6/code13；证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；只允许同包名 `com.kitt.reader.runtimecheck`、版本号合法递增，不卸载、清数据或导出登录信息。办公室签名通过，#19 家庭端仍 PENDING。
- **施工 stop**：只完成 #13，测试/交付并更新HANDOFF、ROADMAP、Issue后停止。#14/#17定位、#18剩余现场、#19家庭签名、V0.4.0离线知识包均不自动施工。

---

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
