# 验收记录 — 中文语音输入恢复

任务卡：`docs/TASK_CHINESE_ASR_RECOVERY.md`
设备：**vivo V2405A，Android 16（API 36）**，本次全程通过 adb 实机操作。
日期：2026-10-03（Asia/Shanghai）

结论先说：**手机自带的公开识别服务仍然不能用**；KITT 现在在实机上用**打包进 APK 的离线中文模型**真正听懂了真人说的话，
并把文字交回原来的 Director 链路。四句验收语全部识别正确，静音不再被编成文字。

---

## Phase A — 重新探测手机（不沿用 M1.4 结论）

| 检查项 | 实测结果 |
| --- | --- |
| `Settings.Secure voice_recognition_service` | `com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService`（与 M1.4 相同） |
| `Settings.Global voice_recognition_service` | `null` |
| `Settings.Secure speech_recognition_service` | `null` |
| `query-services -a android.speech.RecognitionService` | 只有 2 个：vivo copilot、`com.facebook.aura/.assist.HatchRecognitionService` |
| `SpeechRecognizer.isOnDeviceRecognitionAvailable()` | **false**（KITT 日志实测） |
| Google 语音服务 | 未安装（无 `com.google.android.apps.speechservices`） |
| `com.vivo.voicerecognition` / `com.vivo.voicewakeup` | 有包，但 Service Resolver Table 为空——没有对外 `RecognitionService`（它们是无障碍语音控制与唤醒词） |
| Aura 的识别服务 | 需要 `BIND_SPEECH_RECOGNITION_SERVICE`（系统权限），是 Meta 内部辅助能力，用户无法选择，普通应用也无法绑定 |

实机复现（直接从 KITT 自己的日志读，两次独立点击）：

```
engine=system start service=com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService language=zh-CN onDeviceAvailable=false
engine=system error code=5
asr backend=system elapsedMs=24 finish-sink outcome=CLIENT code=5
```

与 M1.4 完全一致：**在 ready／RMS／results 之前 24–25 ms 就 ERROR_CLIENT(5) 失败**。没有成功的中文转写，
因此这条路径确认不可用，不是偶发。

一个值得记录的发现：`com.google.android.googlequicksearchbox` 在清单里确实声明了
`com.google.android.voicesearch.serviceapi.GoogleRecognitionService`（action `android.speech.RecognitionService`），
但它**不可解析**：`query-services -a android.speech.RecognitionService -p com.google.android.googlequicksearchbox`
返回 `No services found`，且该包的 Component Enabled State 为空。普通应用没有 `CHANGE_COMPONENT_ENABLED_STATE`，
无法替别的包启用组件——所以这条只能作为诊断事实记录，**不是可交付方案**，本次没有用它，也没有动系统设置。

按任务卡要求：**没有**调用 vivo 私有隐藏 API，**没有**用 shell 打开受保护组件冒充产品能力，
**没有**把某个界面能说话但没有回传文字的活动算作 PASS。

## Phase B — 免费回退方案比较

约束：不引入付费、需要 API key／账号激活、按分钟计费的能力；优先本地处理。

| 引擎 | 许可 | 维护 | 最小可用中文模型 | 中文质量 | 离线 | arm64 预编译 | 集成成本 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **Vosk** `com.alphacephei:vosk-android:0.3.75` | Apache-2.0 | 2025-12 发布 | `vosk-model-small-cn-0.22` 43 MB（解压 68 MB） | 官方 README 自报 CER 23.5 %（speechio_02）／38.3 %（speechio_06） | 是 | Maven Central 直接可用，`libvosk.so` arm64 10.0 MB | 低：一个依赖＋`Recognizer`＋内置端点检测 |
| sherpa-onnx 1.13.8 | Apache-2.0（模型另有 FunASR MODEL_LICENSE） | 2026-09 | zipformer-small-ctc-zh-int8 26 MB／paraformer-zh-small-int8 82 MB／SenseVoice-int8 237 MB | 同尺寸里最好，尤其命名实体 | 是 | **不在 Maven Central**，需 JitPack 或在库中提交 38–50 MB AAR | 中—高：流式配置、端点规则、字节级模型选择 |
| whisper.cpp 1.9.4 | MIT | 活跃 | base 148 MB／small 488 MB | 短中文指令弱，静音易幻觉 | 是 | 无官方 AAR，需 NDK 或第三方 JNI 封装 | 高 |
| FunASR 直接上端 | MIT＋自定义模型许可 | 活跃 | — | 好 | 是 | 无 Android 运行库 | 高 |
| Moonshine | — | 活跃 | — | **不支持中文** | 是 | — | — |
| Picovoice Cheetah/Leopard | 商业 | — | — | 好 | 是 | — | **被排除：需要 AccessKey** |

选择依据：**先用最小的**。Vosk 是唯一一个「一个 Maven 依赖＋内置端点检测＋不新增仓库源、不往 git 里塞几十 MB 二进制 AAR」
就能落地的方案，且许可干净、纯本地。它唯一的弱点是中文 CER 明显高于 FunASR 系。
因此本次按 AGENTS.md 的顺序做：**先上最小方案并实机测量，不达标再升级**，而不是凭论文数字预先决定。

实测（见下一节）：四句验收语——包括命名实体「三星堆」——**全部逐字正确**，所以最小方案在这个用例集上够用。
这不等于它在大模型级别的自由口述上同样好；长句、口音、专有名词的更严格要求时，sherpa-onnx 是下一个候选，
且因为已经有 `SpeechEngine` 边界，换引擎不需要再动 Journey／Director。

**代价必须说清楚**：APK 从 9.5 MB 变成约 74 MB（模型解压 68 MB＋arm64/v7 原生库约 19 MB）。
模型放在 `assets/` 里随包发布，所以不需要下载子系统、不需要网络、不需要校验与断点续传代码——
这比省几十 MB 更符合 V0「先删复杂度」。原生库只保留 `arm64-v8a` 与 `armeabi-v7a`。

## Phase C — 实现边界

只加了一层很薄的接缝，`VoicePort`／`Journey`／`Director` 的语义没动：

```
interface SpeechEngine { val id; val watchdogMs; fun start(sink); fun cancel() }
interface RecognitionSink { onReady / onRms / onEndOfSpeech / onFinish }
  ├─ SystemSpeechEngine("system")   原 Android 框架路径，代码搬过来而已
  └─ VoskSpeechEngine("vosk-cn")    AudioRecord 16 kHz mono + 离线模型 + 内置端点
```

选择与回落规则（`AndroidVoice.runAttempt`）：

- 顺序：系统识别 → 本地模型。
- **只有在「从未进入 ready」且失败属于后端损坏**（UNAVAILABLE／BUSY／NETWORK／SERVER／CLIENT）时才回落。
- `NO_MATCH`、`TIMEOUT` 是**声学结果**，永远不触发回落——沉默不能被解释成后端坏了。
- 已经在监听之后才出错（ready 之后报错）不重启识别，直接如实报出，避免把一次正常尝试变成两次。
- UNAVAILABLE／CLIENT 会被记住，之后的点击不再重复探测坏掉的后端（本地模型失败同样记住）；
  BUSY／NETWORK／SERVER 视为瞬时，下次仍给系统识别机会。
- 全部后端都不可用时，保留原来的准确失败文案 `系统语音识别暂不可用。`，绝不写成「没听清」。
- 每次点击只交付一个结果：`serial` token 之外再加一层 per-attempt `closed`，
  被放弃的尝试的 watchdog 不可能再触发第二次回落（这条是实测日志里发现的真实缺陷，已修并有测试）。

隐私与音频：`AudioRecord` 只读进一个 1600 样本（100 ms）的短数组，用完立刻 `stop/release`，识别器同步 `close()`；
不落盘、不留历史、不后台常驻。麦克风只在一次 `说点什么` 期间打开。唤醒词、持续监听、录音历史都未引入。

首次使用：模型从 `assets/` 解到应用外部私有目录（`getExternalFilesDir`，靠 `uuid` 文件幂等，卸载即清），
本次实测首次 8 s 内完成、之后每次 `ready` 只花 **76–91 ms**。这条路径只在系统识别已经失败之后才会走，
系统识别能用的设备完全不受影响。

## 实机验收（真人发声，非键盘输入）

后端：`vosk-cn`（`vosk-model-small-cn-0.22`），系统识别每次都先 24–40 ms CLIENT(5) 失败后自动回落。

| 说出的话 | 识别文字 | 结果 | 后续行为 |
| --- | --- | --- | --- |
| 再讲一点 | `再讲一点` | SUCCESS ×3 | 进入原 Director 路径并朗读回复 |
| 跳过 | `跳过` | SUCCESS ×2 | 走 skip 分支 |
| 三星堆为什么这么有名 | `三星堆为什么这么有名` | SUCCESS ×4 | 命名实体逐字正确，进入 Director |
| **完全不出声** | （空） | **TIMEOUT** | 提示「没等到语音，想说时再点一下。」，peakRms=8 |
| 讲述进行中点「说点什么」再讲 | `再讲一点` | SUCCESS | TTS 被打断后监听仍然正常开启 |

逐字日志片段（完整原始日志：`artifacts/phone-asr-session-A.txt`）：

```
asr backend=system elapsedMs=24 unusable outcome=CLIENT code=5; falling back to vosk-cn
asr backend=vosk-cn elapsedMs=89 ready
engine=vosk finish outcome=SUCCESS endpoint=true voiced=true peakRms=395 frames=42 text=三星堆为什么这么有名
asr backend=vosk-cn elapsedMs=4747 finish outcome=SUCCESS code=null rmsCallbacks=43 text=三星堆为什么这么有名
tts started session=12 / tts finished session=12 success=true
```

要点：

- **识别结果确实走进了既有 Director 流**：SUCCESS 之后同一 session 出现 `tts started/finished success=true`，
  也就是文字→`Journey.beginListening` 回调→`DirectorLoop.user`→Provider→TTS 全链闭合，不是只停在 ASR 层。
- **静音／没听清 与 后端故障 分开**：不出声得到 `TIMEOUT`（不是 SUCCESS 也不是 CLIENT）；
  而系统识别坏掉得到 `CLIENT`＋「语音识别没启动成功」/回落，两者文案与 outcome 都不同。
  这条在实机上驱动了一个真实修正：Vosk 的语言模型会把接近静音的噪声编成 `我要去绵羊` 这种看着合理的句子，
  现在**没有 voice energy 就不接受转写**（`voiced` 门限），噪声不会再冒充用户指令。
- **RMS 监听条仍然工作**：每次都有 `rmsCallbacks=24…52`，本地引擎自己从 PCM 计算电平喂给原来的 `ListeningFeedback`。
- **取消／结束／安静 优先于迟到结果**：`voice.stop()` 之后迟到的 `SUCCESS` 被丢弃（有测试覆盖）。
- 没有重播过期结果；每次点击只交付一次。

## 工程门禁

`scripts/verify.ps1`（assembleDebug／assembleRelease、testDebug／testReleaseUnitTest、lintDebug／lintRelease）：

```
PASS: Debug 154 + Release 154 tests; install artifacts\kitt-v0-debug.apk
BUILD SUCCESSFUL in 4m 38s
```

- **154 + 154 tests，0 failures / 0 errors / 0 skipped**（上一批 136＋136，本批新增 18 项）。
- lint **0 errors**（Debug／Release）。本次第一条 lint 报错是真实缺陷：
  `MissingPermission`——`AudioRecord` 在没有本地权限检查的位置被调用。
  处理方式不是 `@SuppressLint`，而是让识别引擎**自己**在开麦前显式检查权限，并把缺权限如实报成 `PERMISSION_DENIED`。
- APK v2 签名 PASS；`kitt-v0-debug.apk` 73.9 MB，SHA-256 `D0C1634E3614DA7CFD163BC2FBE1D08B5B1BF1F88A57F067853E06C845C8797C`。
- 既有回归全部保持：编辑自由度／章节素材架（13 章节、13 次进入机会、6 个题材家族、三星堆可选且被选两次）、
  独立地标（18 机会＝14 SILENT＋4 SPEAK_NOW）、M1.2 晚到再现、M1.3 对话节奏、Fake 黄金路径。
  新测试见 `RecognizerBackendTest`（后端选择、回落、静音不回落、取消胜过迟到结果、过期 watchdog 不再触发二次回落、
  不重复探测坏后端、瞬时 BUSY 下次仍给机会、本地电平驱动监听条、文本确实进入安静指令路径、权限、Vosk 文本去空格）
  与 `QuietCountdownTest`。

测试有效性做过反向验证：把 `quietRemainingNow` 绕过、改回直接读 `journey.quietRemaining`，
3 个倒计时用例里 2 个立即 FAILED——也就是说它们真的钉住了那个 bug，而不是恒真断言。

## 实机复验状态（最终包，已完成）

上面那张真人表是在一次小重构**之前**的构建上取的（重构仅把 `AudioRecord` 创建从工作线程移到带显式权限守卫的调用处，
事件顺序与逻辑未变）。手机重新接上后，**已在最终发布包上重做**，全部通过：

包：`kitt-v0-debug.apk` **0.2.1 / versionCode 3**，设备侧确认 `versionName=0.2.1`，
SHA-256 `D0C1634E…`（原始日志：`artifacts/phone-asr-session-B-final.txt`）

| session | 后端 | outcome | rmsCallbacks | 识别文字 |
| --- | --- | --- | --- | --- |
| 9 | vosk-cn | SUCCESS | 26 | `安静十分钟` |
| 14 | vosk-cn | SUCCESS | 21 | `效果`（一句较长话的尾部，见下方说明） |
| 18 | vosk-cn | **TIMEOUT** | 52 | （空，voiced=false，peakRms=9） |
| 20 | vosk-cn | SUCCESS | 43 | `三星堆为什么这么有名` |
| 25 | vosk-cn | SUCCESS | 31 | `跳过` |
| 29 | vosk-cn | SUCCESS | 26 | `再讲一点` |

四句验收语在最终包上**全部逐字正确**，包括之前缺真声证据的 `安静十分钟` 与命名实体 `三星堆`。
`ready` 延迟 66–91 ms。

**没有重试风暴**（这条以前只是单测断言，现在是硬件事实）：第一次 `system → CLIENT(5)` 被记住之后，
上面整个窗口里每一次监听都**直接进入 `vosk-cn`**，日志里不再出现任何 `engine=system` 探测。

**静音没有被当成话**：session 18 完全不出声 → `TIMEOUT`、空文本、`peakRms=9`（低于 300 的语音门限）。
这正是幻觉门限要做的事——上一轮曾出现过把接近静音编成 `我要去绵羊` 的情况。

**一句需要说明的观察**：session 14 得到 `效果` 两个字。那是用户在说较长句子时截到的尾部，
不是四句验收语之一；它说明**长句会被端点检测提前切断**（`EndpointerMode.LONG` 已经是较宽档）。
短指令与中等长度问句没问题，更长的自由口述需要真路上继续观察，属已知限制。

**安静倒计时（最终包，实机取样，60 秒不碰手机）**：

```
engaged 剩余 9:56 | +10s 9:44 | +20s 9:32 | +30s 9:20 | +40s 9:08 | +50s 8:56 | +60s 8:44
```

数字按真实秒速下降（每个采样点含一次 uiautomator dump 约 2 秒，所以差值略大于 10 秒），安静期间没有被取消。
之前有两次安静在我采样约 20 秒后自己结束，时间点与用户结束测试、按屏幕上「结束安静」重合；
**放置 60 秒不动则稳定保持**，因此没有证据表明存在自发作废，但这一条留作后续观察项而不是宣布已排除。

顺带确认：识别成功之后同一 session 出现 `tts configured voice=yue` → `tts started`，
说明文字确实走回 Director，而不是停在 ASR 层。

## 第二阶段：识别文字上屏 + 打字入口（任务卡新增要求）

任务卡在我完成 ASR 之后追加了一节 `Live transcript + typed-input fallback`。实现只加了一层 UI/交互，
**没有**改 Director 语义、没有第二套指令语法、没有新的持久化。

### 屏幕上的识别文字

| 情况 | 行为 |
| --- | --- |
| 后端能给 partial/interim | 实时显示，前缀「在听：」，随识别推进更新 |
| 后端只能给 final | **不伪造中间结果**：监听条照常活着，最终文本出现时才显示 |
| 最终结果到达 | 前缀变成「你说：」，并在这次交互期间持续显示，Director 处理时仍看得见 |
| partial | **只用于显示，永不提交**（`TranscriptText.final=false`，提交路径只认 final 或打字） |
| 没听清／没出声／后端坏了 | transcript 被清空，走 `journey.notice` 独立节点、独立样式（`journey-notice`），不会看起来像用户说过的话 |

系统识别通过 `EXTRA_PARTIAL_RESULTS=true` + `onPartialResults` 供中间结果；离线模型用 Vosk 的
`partialResult` 供中间结果，只在内容真的变化时才发出。两者都是引擎真实给出的假设，不是合成的。

`TranscriptText` 是纯 transient UI 状态：不落盘、不进旅程纪要、下一次监听开始即清空，
返回／取消也会清（`AndroidVoice.clearTranscript()`）。

### 打字入口与语音共用同一条路

点「说点什么」之后，同一块界面上除了麦克风还有「或打字回答」输入框 + 发送 + 取消。
KITT 用 `ASK_USER` 提问之后也是同一块界面，所以**提问不再只能靠语音回答**。

关键实现：打字提交调用的是 `Journey.submitReply(text)`，它触发的是**当初交给语音的那个同一个 handler**
（生产里就是 `DirectorLoop::user` → `Journey.requestInput` → `dispatch`）。所以打字和说话不是两套语义，
而是同一个入口的两个来源。`awaitingReply` 只在一次交互期间为真，90 秒无人回答就过期作废并把麦克风还回去。

竞争处理（都有测试）：

- 输入框**获得焦点**就 `stopListeningForTyping()`：先把 `listening` 置假再 `voice.stop()`，
  于是这次取消不会被当成回答，同时监听立刻停止；
- **提交**先 `invalidate()`（epoch 自增 + 停识别器），所以迟到的 ASR 回调被 epoch 守卫吃掉，
  既不能覆盖用户打的字，也不能重复提交；
- 回答一旦交付就 `clearReply()`，第二次 `onResults` 不会再算一次回答；
- 取消／返回 → `cancelReply()` + `clearTranscript()`，界面与瞬时文字一起清掉；
- **识别器全坏了也不堵路**：`UNAVAILABLE`／`CLIENT`／权限拒绝都保持 `awaitingReply` 为真，打字照旧能用；
  只有「没听清／没出声／被取消」这类声学结论才关闭这次交互；
- 用户正在回答时不会被自动讲述打断（`shouldCheck` 把 `awaitingReply` 和 `listening` 同等看待）。

### 这一阶段的测试

`ReplyExchangeTest`（12 项，JVM）＋ `ReplySurfaceUiTest`（6 项，Robolectric Compose 竖屏）＋
`RecognizerBackendTest` 新增 5 项文字上屏断言。最终包 **Debug 177 + Release 177，0 failures / 0 errors / 0 skipped**，
lint 0 error，签名 PASS。

| 任务卡要求的 case | 覆盖它的测试 |
| --- | --- |
| partial 能显示 | `interimHypothesisAppearsOnScreenButIsNeverSubmitted`、`interimTextIsShownAsStillBeingHeardAndNeverLooksCommitted` |
| final 能显示且只提交一次 | `finalTranscriptStaysVisibleAfterTheAttemptCloses`、`finalTextIsShownAsWhatTheUserActuallySaid`、`speechAnswerSubmitsOnceAndRejectsASecondTranscript` |
| 打字提交进入 Director | `typedAnswerTravelsTheSameRequestInputPathAsSpeech`、`theOpenExchangeOffersTypingAlongsideTheMicrophone` |
| 打字后迟到 ASR 被丢弃 | `aLateTranscriptAfterTypedAnswerCannotOverwriteOrDuplicate`、`reachingForTheKeyboardHandsTheMicrophoneBackButKeepsTypingOpen` |
| ASK_USER 既能说也能打字 | `askedQuestionAcceptsEitherSpokenOrTypedReply`、`askedQuestionCanBeAnsweredByVoiceAndClosesAfterOneResult` |
| ASR 不可用时仍能打字 | `anUnavailableRecogniserStillLeavesTheExchangeOpenForTyping`、`recognitionTroubleIsShownAsANoticeNotAsTranscriptText`、`aStartupErrorFromEveryBackendIsNotAMissingAnswer` |
| 取消／返回清状态 | `cancelClosesTheExchangeAndHandsTheMicrophoneBack`、`cancelClosesTheSurfaceAndReportsBackToTheCaller`、`cancelAndTheNextListenBothStartFromAnEmptyTranscript` |
| 无人回答要干净作废 | `anUnansweredExchangeExpiresAndStopsCapturing` |
| 错误提示与识别文字视觉区分 | `aMissOrASilenceNeverLeavesAnInventedTranscriptOnScreen`、`recognitionTroubleIsShownAsANoticeNotAsTranscriptText` |

### 真机补测结果（0.2.2 / versionCode 4 / SHA `8BBF7042…`）

**我自己在这台机器上直接确认到的**（有日志与界面 dump，`artifacts/phone-asr-session-C.txt`）：

- 中间结果**确实实时上屏并逐字变化**：同一句里先后抓到 `在听：尔康成立是` → `在听：四马尔康县惨烈是`，
  监听条与「或打字回答／发送／取消」在同一屏共存，驾驶页没有出现可滚动节点；
- 新一轮监听开始或取消时麦克风被释放（`asr cancelled`）；
- 不出声得到 `outcome=TIMEOUT`、`voiced=false`、`peakRms` 只有 8–27，**屏幕上不留任何编造的 `你说：`**。

**由用户自行验收确认为通过**（逐字上屏、final 明确显示、打字进入 Director、`ASK_USER` 只打字能答、
取消／返回键清掉识别文字、四句验收语与 TTS 打断不回归）：用户回报「已经可以了」。

**证据缺口必须说清楚**：这一轮的 `logcat` 我没能归档——手机在用户测完后、我读取日志前又断开了
（`adb devices` 已空，`logcat -d` 挂住超时）。所以**当前写在档上的逐字识别文本仍来自 0.2.1 那一轮**
（`artifacts/phone-asr-session-B-final.txt`）。0.2.2 这一轮是「用户确认可用」，不是「我手上有文本记录」。
下次接上手机只要跑 `adb logcat -d -s KITTVoice > artifacts/phone-asr-session-D.txt` 就能把这一轮补齐。

### 从这轮真机数据里浮出来的准确率问题（不要粉饰）

同一句疑似含「马尔康」的话，在 0.2.2 上被抓成**四种不同结果**：

```
尔康藏匿是  /  起码尔康类似  /  四马尔康县惨烈是  →  最终  四马尔康县长劣势
```

而 0.2.1 那一轮四句短指令与「三星堆为什么这么有名」全部逐字正确。合起来读就是：
**短指令、常见词、命名实体没问题；稍长或地名密集的口语会明显飘**。这与模型自报 CER 23.5 %／38.3 % 一致，
不是新 bug，而是当初选择最小方案时接受的代价。

由此给出明确建议：**如果实际使用中要口述地名和长句，应当把离线模型升级为
sherpa-onnx + Paraformer/Zipformer 中文模型**（Apache-2.0，仍然免费、无 key、纯本地）。
`SpeechEngine` 接缝已经在了，换引擎不需要动 Journey／Director，主要成本是再引入一个 AAR 与更大的模型体积。
本批次**没有**擅自做这个升级——它改变 APK 体积与依赖来源，属于需要用户拍板的一次决定。


## 顺带发现并修掉的一个旧缺陷（与 ASR 无关）

用户在测「安静十分钟」时报：**安静倒计时一直停在 9:59 不走**。

排查结论：安静**时长本身一直是对的**（`JourneyTest:20-21`、`SimulationCadenceTest:106-109` 证明到点即 `resume`，
`KittRuntime` 也有 1 秒 `loop.check()→journey.tick()` 驱动）。坏的是**显示**：
`quietUntil` 是一个绝对截止时间，随时间流逝没有任何被观察的值发生变化，Compose 因此不会重组这行文字，
倒计时就停在第一次画出来的数字上。实机取样 16 秒完全冻结复现：

```
+2s 剩余 9:16 | +4s 9:16 | … | +16s 9:16     （修复前）
```

修复只在 UI 层：`DrivingScreen` 里用 `quietRemainingNow(journey)` 在组合中按秒重新读取剩余时间。
**没有改动**任何安静时长、到点恢复、墙钟计算或 stale 逻辑。测试 `QuietCountdownTest` 会在绕过修复时失败
（已验证：绕过时 3 个用例里 2 个 FAILED），所以这个回归被钉住了。
