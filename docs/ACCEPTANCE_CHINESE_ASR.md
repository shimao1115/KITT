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

## 实机复验状态（诚实记录）

真人发声验收（上一节的表）是在**同一套选择／回落语义、但早于一次小重构**的构建上完成的：
重构仅把 `AudioRecord` 的创建从工作线程移到带显式权限守卫的调用处，并把识别循环拆成独立函数，逻辑与事件顺序未变，
之后跑完了完整门禁。手机在那之后断开，所以下面三项需要在 0.2.1 最终包上补做，**补做前不应把 ASR 记作最终 PASS**：

1. 再说一遍四句验收语并核对 `logcat` 里的 `text=`；
2. **真人说 `安静十分钟`**——上一轮实机日志里没有出现这句话（四句里只覆盖了 3 句），
   它的键盘路径有 `JourneyTest:62` 与新增的 `spokenFallbackTextReachesTheQuietCommandPath` 证明，但缺真声证据；
3. 安静时屏幕上「剩余 M:SS」** visibly 逐秒减少**。

如果真声复验失败，应如实记为未通过，**不要**把失败改写成「没听清」来让测试变绿。

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
