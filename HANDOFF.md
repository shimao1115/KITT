# KITT V0.2 — 当前交接

截至 **2026-10-03（Asia/Shanghai）**：**中文语音输入已在实机上真正可用**（`docs/TASK_CHINESE_ASR_RECOVERY.md`），
方式是保留原有「点一下→听一次→把交回原 Director」交互不变，在系统识别被证实不可用后回落到**打包进 APK 的离线中文模型**。
任务卡随后追加的**识别文字上屏 + 打字入口**也已实现并在 0.2.2 上验收通过：中间结果实时显示、只有 final 或打字会提交、
`ASK_USER` 不再只能靠语音回答、打字与语音共用同一个 Director 入口。
同时修掉一个用户实测报出的旧缺陷：**安静倒计时数字一直不走**（只是显示，时长逻辑原本就是对的）。
**Debug/Release 各 177 测试、两个构建、两个 lint 全部 PASS**；四句验收语由真人发声逐字识别正确。
编辑自由度／章节机会／地标／路线图／看图／OAuth／模拟 GPS 等既有能力**本批次未改动逻辑**。

## 直接安装与验收

最新可安装 APK：`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`，**0.2.2 / versionCode 4 / Android 8.0+ / 73.6 MB**。
本地 `adb install -r artifacts/kitt-v0-debug.apk`。APK v2 签名验证 PASS；SHA-256：
`8BBF70421AD4E63342F8EF55B370A290CD76F0E6D12DBEDE8F5A291AA8F138BF`。
（0.2.1／versionCode 3／SHA `D0C1634E…` 是真人语音四句验收那一轮的包，那份证据仍然有效。）
**体积从 9.5 MB 涨到 73.9 MB**：离线中文模型解压 68 MB（随包发布，不走下载）＋只保留 arm64／armeabi-v7a 的原生库。
**第一次使用语音**时模型会从包内解到应用私有目录（实测约 8 秒，之后每次开听只需 76–91 ms）；不语音输入则完全不付出这个代价。

最短合并顺序：TTS声音试听／保存 → 普通开始验证真实GPS → 结束 → 添加可选路线截图
→ 标题点五次，**100km/h / 16× / 开始** → 全程观察章节素材架、讲述机会与独立地标，期间**用语音**追问、安静、旅途看图
→ 后台／锁屏 → 结束纪要 → 普通GPS重启 → 横竖屏。逐项标准见[11项清单](docs/COMBINED_PHONE_ACCEPTANCE.md)。
语音部分现在**必须真声验收**，键盘文本输入不再算作 ASR 成功。完整语音验收记录：[中文语音输入恢复](docs/ACCEPTANCE_CHINESE_ASR.md)。

## 当前可运行能力

- **中文语音输入**：点「说点什么」→ 立刻停止朗读 → 听一次 → 中文转写 → 交回原 Director／安静／跳过路径。
  优先手机系统识别，坏掉时自动回落本地离线模型；RMS 监听条、结构化错误文案、TTS 声音选择器全部保留。
- **识别文字上屏 + 打字并存**：说话时实时显示中间结果（「在听：」），最终结果变「你说：」并持续显示；
  只有 final 或打字会提交，中间结果绝不提交。同一个交互面上还有「或打字回答」+ 发送 + 取消，
  `ASK_USER` 提问后既能说也能打字；打字走的是语音本来会走的**同一个** handler（`DirectorLoop.user` → `requestInput`），
  不是第二套语义。聚焦输入框就交还麦克风，提交先作废 epoch 让迟到识别无法覆盖或重复提交，
  90 秒无人回答干净作废；系统识别全坏了时打字照旧可用。识别文字与错误提示分节点分样式，不会混。
- Journey 开始／结束、10分钟真实时间安静与提前退出、跳过、最新用户意图、一次 ASK_USER、一个失效型 PREPARE、简短纪要。
- **安静倒计时现在真的会走**：过去停在第一次画出的数字上，因为绝对截止时间不产生任何可观察变化、Compose 不重组这一行。
  现在在组合里按秒重读剩余时间；时长、到点恢复、墙钟计算逻辑一行都没改。
- GPS 压缩 Context；区县背景＋镇乡街道章节，本旅程缓存；现实位置 best-effort 系统逆地理编码，限频且不阻塞 GPS。
- **本地素材架**：每个章节交出一整面中性主题／实体清单（地方史与建置、古镇老街、各级文物保护单位与历史建筑、
  遗址考古与博物馆、寺庙信仰、风景名胜、文学艺术与地方人物、传说轶闻、非遗与民间工艺、民俗节庆、方言地名、
  水系山川地貌、桥隧水利铁路工程、地方道路、特产饮食、农业工业贸易、街巷当代生活，以及 `OTHER` 兜底条目）。
  标题**不预设论点、切入方式、结构或结论**；已核实的当地对象另附来源注记。清单是开放的，类别不是允许范围。
- **进入新的镇／乡／街道章节即创造一次导演机会**：跳过普通距离／时间门槛，但不越过安静、语音冷却、用户接管、
  看图与位置新鲜度门槛；被压制的那次唤醒直接丢弃，不排队。有依据充足的材料时倾向于开口，SILENT 仍合法，
  不设篇数或题材配额。区县变化同样可触发背景机会。
- 最近题材只作为弱反重复提示（“不是黑名单，也不要求轮换题材”）；不再有按题材降权的候选排序。
- `topic_family` 增加 `OTHER`；严格八字段 JSON 契约不变。
- 有依据的地标接近仍可独立唤起检查，优先于泛泛道路／聚落解释，**规则与几何完全保留**：14类支持、
  每节点每趟一次机会、成功交付节点去重、主动深入不受自动去重限制、驶离后晚到内容直接死亡。
- 可选路线图由当前多模态 Provider **只分析一次**，保存≤240字会话 RouteHint；取消／结束／服务丢失清空，不落恢复文件。
- 乘客／停车时“旅途看图”→ 系统相机／选图＋可选短问题，经主动 Director／本地TTS回答。不长期保存，不新增权限。
- 黑红夜间／高对比日间界面，大按钮与简洁地点；竖屏／横屏驾驶页不滚动。
- ChatGPT账号隔离授权／目录／SSE，Fake／Responses／兼容API；实时位置与开发模拟独立，普通开始始终手机GPS。

## 语音识别的后端选择规则（这次新增的唯一一层）

```
SpeechEngine { id, watchdogMs, start(sink), cancel() }   RecognitionSink { onReady/onRms/onEndOfSpeech/onFinish }
  ├─ system    原 Android 框架识别（代码搬家，语义不变）
  └─ vosk-cn   AudioRecord 16 kHz + 包内离线中文模型 + 内置端点检测
```

- 顺序：系统识别 → 本地模型。**只有「从未进入 ready」且失败属于后端损坏**才回落。
- `NO_MATCH`／`TIMEOUT` 是声学结果，**永不**触发回落，也不会被写成后端故障。
- `UNAVAILABLE`／`CLIENT` 会被记住，之后不再重复探测坏掉的后端（本地模型失败同样记住）；
  `BUSY`／`NETWORK`／`SERVER` 视为瞬时，下次仍给系统识别机会。全部坏掉时保留准确文案 `系统语音识别暂不可用。`
- 每次点击只交付一个结果：`serial` 之外再加 per-attempt `closed`，被放弃尝试的 watchdog 不可能再触发第二次回落。
  这条是实机日志里抓出来的真实缺陷（旧版会在 15 s 后打出一条过期 watchdog），已修并有测试。
- `Journey.awaitingReply` 表示一次未结束的回答窗口（点「说点什么」或 KITT 提问）。它跟着 epoch 作废，
  任何 skip／安静／结束／新意图都会关闭它；`shouldCheck` 把它与 `listening` 同等看待，避免讲述打断正在打字的人。
- **隐私**：麦克风只在一次「说点什么」期间打开；音频只进一个 100 ms 短数组，用完立刻 `stop/release`，
  不落盘、无录音历史、无常驻监听、无唤醒词。模型文件是应用私有目录，卸载即清。
- 没有引入：付费服务、需要 API key 的 ASR、订阅、按分钟计费、云端 TTS、后台识别、RAG／向量库、多 Agent。

## 默认 Provider／外部步骤

**新安装默认 Fake；模型／effort 无实际用途**，无需账号、AI key 或地图 key 即可跑完整技术闭环。
真实AI首选设置中的 ChatGPT 账号及账号实际返回模型／effort；不支持或服务报错会简短解释，不隐藏换 Provider 或计费 fallback。
普通 API 是可选替代，需自己的 key。本地 `OPENAI_API_KEY` 存在性检查为否，未读任何手机或桌面账号 token。
**语音识别不需要任何 key、账号或网络**——这条是本次刻意守住的边界。

历史 M1.4 手机最后已验证配置为 **ChatGPT / gpt-5.6-luna / high**，TTS **yue**（本次实机朗读日志同样是 `voice=yue`）。
本批次**没有重验当前会话／模型可用性**；已有有效授权可直接使用，新装或过期时由用户本人完成系统浏览器授权。

## 模拟结果与工程证据

路线 fixture 不变：**成都市新都区 → 青白江／广汉／德阳／绵竹 → 绵阳市安州区雎水镇**，
**29点、13个镇街章节、90,912m 测试折线**。

- 最终门禁：`scripts/verify.ps1` 完成 **assembleDebug/Release、testDebug/ReleaseUnitTest、lintDebug/Release**，
  **177＋177 tests，0 failures/errors/skips**；lint **0 errors**；APK v2 签名 PASS。
  本批次保留原有 136 项，累计新增 41 项：识别后端选择／回落／取消／静音／权限 12 项、安静倒计时 3 项、
  中文 ASR 其余 3 项，以及交互层 23 项（打字与语音共用一条路径 12 项、文字上屏 5 项、Compose 界面 6 项）。
- **章节素材架回归**：13 章节缓存／13 次章节进入机会、14 段素材、6 个题材家族、三星堆可选且被选两次、零 stale/cancel/failure。
- **独立地标回归**：四节点各选一次、18 机会＝14 SILENT＋4 SPEAK_NOW、零 stale/cancel/failure（规则未动）。
- **M1.2 晚到再现／M1.3 对话节奏／Fake 黄金路径**：与上一批一致，见下档。

完整证据：[中文语音输入恢复](docs/ACCEPTANCE_CHINESE_ASR.md)、[编辑自由度批次](docs/ACCEPTANCE_EDITORIAL_FREEDOM.md)、
[Post-M1.4 批次](docs/ACCEPTANCE_POST_M1_4.md)、空间规则：[独立地标](docs/LANDMARK_PROXIMITY.md)。
本地报告：`artifacts/final-verification.log`、`verification.json`、`SHA256.txt`、`verify-run.txt`、
`phone-asr-session-A.txt`（真人发声原始日志）、`phaseA-reprobe.txt`、`ASR_PHASE_A_FINDINGS.txt`。
构建／APK／本地日志忽略入Git，可通过验证脚本重建；**离线中文模型本身随 `app/src/main/assets/` 提交进仓库**，
因为打包发布比新增下载／校验／断点子系统更简单，且最大单文件 26 MB 远低于 GitHub 限制。

## 已知限制及延后的 Gate

**交互层（文字上屏＋打字并存）已在 0.2.2／versionCode 4／SHA `8BBF7042…` 上由用户自行验收通过**。
我另外在同一台机器上直接抓到：中间结果实时逐字上屏、监听条与打字行同屏共存、页面不滚动、
不出声得到 `TIMEOUT` 且不留编造文字、取消时麦克风释放。
**证据缺口**：这一轮的 `logcat` 没能归档（用户测完我已读不到设备），所以写在档上的逐字文本仍来自 0.2.1 那轮；
下次接上手机跑 `adb logcat -d -s KITTVoice > artifacts/phone-asr-session-D.txt` 即可补齐。

**需要用户拍板的一次升级**：0.2.2 上同一句疑似含「马尔康」的口语被抓成四种结果（`尔康藏匿是`／`起码尔康类似`／
`四马尔康县惨烈是`／`四马尔康县长劣势`），而短指令与「三星堆为什么这么有名」在 0.2.1 全部逐字正确。
合起来说明**短指令没问题、较长口语和地名会飘**，与模型自报 CER 一致。若实际使用要口述地名／长句，
建议换成 sherpa-onnx + Paraformer/Zipformer 中文模型（仍免费、无 key、纯本地，`SpeechEngine` 接缝已在，
不需要动 Journey／Director；代价是新增一个 AAR 与更大模型体积）。本批次没有擅自做这个决定。

**真人语音验收已在 0.2.1 发布包上完成**（versionCode 3／SHA `D0C1634E…`，vivo V2405A）：
`再讲一点`／`跳过`／`安静十分钟`／`三星堆为什么这么有名` 四句全部逐字正确；不出声得到 `TIMEOUT` 而非编造文字；
讲述中打断后仍能听；第一次系统识别失败之后整个窗口都不再重复探测它；安静倒计时按真实秒速下降。
完整表格与原始日志见 [中文语音输入恢复](docs/ACCEPTANCE_CHINESE_ASR.md)。

其余限制：

- **离线中文模型的准确率是有代价的**：Vosk `small-cn` 自报 CER 23.5 %（speechio_02）／38.3 %（speechio_06）。
  四句验收语（含命名实体“三星堆”）逐字正确，但**这不等于长句、口音、自由口述同样好**。
  实机已观察到一次较长句子被端点检测提前截断、只剩尾部两个字（`EndpointerMode.LONG` 已是较宽档）。
  若真路上自由口述质量不够，升级路径是 sherpa-onnx（Apache-2.0，paraformer-zh-small-int8 82 MB 或 SenseVoice-int8 237 MB），
  代价是要在库里提交 38–50 MB AAR 或引入 JitPack；已有 `SpeechEngine` 接缝，换引擎不需要再动 Journey／Director。
- **噪声幻觉已挡住但门限是实测调出来的**：模型会把接近静音的噪声编成看着合理的句子（实机出现过 `我要去绵羊`），
  现在没有 voice energy（RMS>300）就不接受转写。极端安静的车厢可能把轻声判成 `没等到语音`，这时应该重说而不是怀疑后端。
- 两次安静在我采样约 20 秒后自行结束，时间点与用户结束测试、按屏幕上「结束安静」重合；
  **放置 60 秒不动则稳定保持**，所以没有证据表明存在自发作废，但留作观察项，不宣布已排除。
- 模型常驻进程：`AndroidVoice.close()` 在生产路径**没有调用者**，所以模型一旦用过就驻留（实机 TOTAL PSS ≈ 212 MB／RSS ≈ 250 MB）。
  现代手机可接受，但这是明确的资源代价；低内存设备的表现属人工验收范围。
- 首次使用语音要等模型解包（实测约 8 s），期间监听条处于“准备中”。
- **主观内容质量尚未在真实模型下验证**：素材架更宽、模板已拆，但真实 ChatGPT 是否讲得更杂更自然，
  以及“经过多数乡镇不应大面积沉默”是否在真机成立，属[联合手机验收](docs/COMBINED_PHONE_ACCEPTANCE.md)第3／4／8项。
- 当前只有 **三星堆、绵竹年画村、龙门山山前地带、雎水太平桥** 四个带来源的空间区域参考，不承诺全国 POI 自动发现。
  通用素材条目只是方向提示，模型不得据此编造当地事实；Adapter 未启用搜索。
- 系统Geocoder可用性依设备；API33+超时8s。Windows Robolectric 不能替代实体麦克风／FileProvider URI／硬件表现。
- 手机专属能力（真实 GPS／地点／地标时机、OEM 锁屏长运行、TTS 主观表现、后台清理）仍 **DEFERRED TO COMBINED PHONE ACCEPTANCE**。
- ASR 完成后，上游新增的 `docs/TASK_POST_ASR_YANTU_V03.md`（沿途 V0.3）与 `docs/TASK_MANDATORY_LOCAL_SEARCH.md`
  两张任务卡的前置条件已经解除，是下一个 Destination 的候选；本批次没有开始它们。

## 最短人工总验收（语音部分，本批次已在最终包上跑过）

```
adb install -r artifacts/kitt-v0-debug.apk
adb shell am start -n com.kitt.reader/.MainActivity     # 开始读山河 → 点“说点什么”
```
说 `再讲一点`／`跳过`／`安静十分钟`／`三星堆为什么这么有名`，然后
`adb logcat -d -s KITTVoice` 应当看到 **进程内第一次** `engine=system error code=5` → `falling back to vosk-cn`
→ `outcome=SUCCESS … text=<你说的原话>`。之后各次监听会**直接用 `vosk-cn`，不再出现 `engine=system`**
（坏后端已被记住，这正是“不重复探测”的期望行为，不是日志缺失）。首次点语音先等模型解包（约 8 s，状态为“准备中”）。
不出声应得到 `TIMEOUT` 而不是编出文字；安静时屏幕上的“剩余 M:SS”应逐秒减少。

## 最近 milestone commits

| Commit | Destination |
| --- | --- |
| `3c1d93b` | V0.2：识别文字上屏 + 打字与语音共用同一个 Director 入口 |
| `dcdeafd` | V0.2：中文语音输入在 vivo 实机上真正可用（离线模型回落） |
| `0a8c397` | fix：安静倒计时真的在倒数 |
| `2b156ce` | docs：中文 ASR 恢复任务卡 |
| `d201a38` | V0.2：把编辑自由度交回 Director |
| `bc92395` | 独立地理／地标机会，节点去重和接近过时保护 |
| `8cf0e58` | Phase E：黑红状态／横竖屏、打包与合并验收 |

已完成的历史 M1.1–M1.4 证据原文保留在[历史交接](docs/HANDOFF_PRE_V0_2.md)。
以本文件顶部状态为当前真相；**历史 vivo ASR 失败记录已被本次实机复现确认仍然存在**，
所以任何“语音能用”都必须同时记录 `recognitionSource`（`system` 或 `vosk-cn`），不能只说“能听”。
