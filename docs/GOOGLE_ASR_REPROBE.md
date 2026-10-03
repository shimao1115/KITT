# vivo Google RecognitionService 公共接口重新探测

日期：2026-10-03（Asia/Shanghai）；设备：vivo V2405A / Android 16 / API 36。
仓库基线：`7de4303`。本次只增加独立诊断工具和报告，不改沿途产品代码或安装包。

## 当前结论

**结论属于 C：Google 服务对普通第三方 App 可解析、可启动，但中文识别实际未成功。**
`com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`
在三次显式创建中均收到 `ready`，随后返回原始 `ERROR_NETWORK(2)`，未交付转写。
不是 `ERROR_NO_MATCH(7)`，也不能据此称中文效果优于 Vosk。

用户随后设置了全局代理并反馈“貌似也不行”，在 23:09–23:11 完成一轮三句重测。
三句均收到 `ready` / `beginningOfSpeech`，但没有部分或最终转写，均达到诊断程序30秒上限而被取消。
重测没有 Google 终结错误码，不记成网络错误、no-match 或系统 speech-timeout。
第一轮三句的真人发声尚未得到明确确认；按钮触发、指定中文、callback 和错误已实际记录，
但不能冒充成功的真人中文识别验收。实际中文准确率仍无法评价。

Gboard 中文输入可用是用户已确认的事实，但 **Gboard 可用 ≠ Android 公共 SpeechRecognizer 可用**。
本轮公共调用证据来自独立 App 的 `PackageManager` 和 `SpeechRecognizer`，没有借用 Gboard 内部接口。

## 范围与方法

- 独立包 `com.kitt.asrprobe`，普通应用 UID `10316`，targetSdk 35；仅申请 `RECORD_AUDIO`。
- 没有 INTERNET 权限；识别通过 Android 公共服务接口委托。这并不代表 Google 服务不会访问网络。
- manifest 使用 `android.speech.RecognitionService` 的 `<queries>`；额外声明四个 Google 包查询。
- 普通进程运行 `queryIntentServices`（正常及 `MATCH_DISABLED_COMPONENTS`）、`resolveService`、
  `getPackageInfo`、组件 enabled 状态读取、公开服务 metadata 读取。
- 所有 `SpeechRecognizer` 操作在主线程；每次由按钮启动，30秒硬上限，终结后 cancel/destroy，页面退后台即取消。
- `createSpeechRecognizer(context, ComponentName)` 显式选择可用 Google 组件；没有尝试启用或调用禁用 Google App 组件。
- 请求：`ACTION_RECOGNIZE_SPEECH`、`LANGUAGE_MODEL_FREE_FORM`、`EXTRA_LANGUAGE=zh-CN`、
  `EXTRA_PARTIAL_RESULTS=true`、`EXTRA_MAX_RESULTS=3`；未强制离线、未下载模型、未输入伪造音频。
- 记录全部 ready / beginning / RMS / partial / end / final / error 及辅助回调；音频 buffer 仅记录长度，不保存录音。
- adb shell 查询只是交叉验证；“普通应用可见”的结论由应用内 PM 查询独立建立。

API 方法、包可见性声明、错误常量和 on-device 语义参照
[Android SpeechRecognizer 官方文档](https://developer.android.com/reference/android/speech/SpeechRecognizer)。
本机行为以实测日志为准。

## 默认设置与可选状态

| 项目 | 实测 |
| --- | --- |
| `Settings.Secure voice_recognition_service` | `com.vivo.ai.copilot/.framework.wakeup.CopilotRecognitionService` |
| `Settings.Secure default_input_method` | `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` |
| `Settings.Secure voice_interaction_service` | `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService` |
| `SpeechRecognizer.isRecognitionAvailable()` | `true`；不代表默认服务能转写 |
| `SpeechRecognizer.isOnDeviceRecognitionAvailable()` | `false`（麦克风权限已授予时测量） |
| `android.settings.VOICE_INPUT_SETTINGS` | 公开设置入口解析为 `com.android.settings/.Settings$ManageAssistActivity` |

打开公开设置入口只读查看：页面名“数字助理应用”，默认数字助理显示“Google”；
本页没有独立的默认 RecognitionService 选择控件。**数字助理默认 Google，不等于语音识别默认 Google**。
Google 语音服务声明了语言包设置 Activity，但不能把该声明算作系统默认服务已切换或中文模型已可用。
没有切换任何默认值。初次测试后复读三项 Secure 值与探测前一致。

收尾时再次读取：默认识别仍是 vivo，数字助理仍是 Google；默认输入法此时为
`com.bytedance.android.doubaoime/.ImeService`，与起点的 Gboard 不同。
Agent本次没有执行输入法切换或Settings写入，输入法变化来源未验证；如实记录最终设备值，不擅自切回。

`isOnDeviceRecognitionAvailable=false` 表示系统 on-device 公共入口不可用；
不能据此推断 Gboard 内部有没有本地模型，也不能推断某个显式组件所有模式都不支持离线。

## 全部可见 RecognitionService

各包 application.enabled 均为 true；application/component enabled override 均为 `0`（DEFAULT）。
DEFAULT 不等于 enabled，Google App 的 service manifest enabled 实际为 false。

| package | component（完整类名） | service enabled | exported | permission | 普通 query / 显式 resolve | version / code |
| --- | --- | --- | --- | --- | --- | --- |
| `com.google.android.googlequicksearchbox` | `com.google.android.voicesearch.serviceapi.GoogleRecognitionService` | **false** | true | null | **false / false**，只在包含禁用组件的查询出现 | `17.61.20.ve.arm64` / `301812194` |
| `com.google.android.tts` | `com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService` | **true** | true | null | **true / true** | `googletts.google-speech-apk_20260817.01_p0.966249458` / `210673049` |
| `com.vivo.ai.copilot` | `com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService` | true | true | `android.permission.BIND_RECOGNITION_SERVICE` | true / true | `6.9.3.0` / `6090300` |
| `com.facebook.aura` | `com.facebook.aura.assist.HatchRecognitionService` | true | true | `android.permission.BIND_SPEECH_RECOGNITION_SERVICE` | true / true | `9.0.0.23.178` / `1061401224` |

上表 component 与 package 拼接为完整 ComponentName。Google 以外的权限照录，不因 PM 可解析就推断实际绑定成功；
本次不尝试 Aura 服务，vivo 仅通过系统默认 SpeechRecognizer 公共入口测试。

### Google 相关包

| 包 | 当前状态 |
| --- | --- |
| Google App `com.google.android.googlequicksearchbox` | 已安装、应用启用；识别组件禁用，未调用、未启用 |
| Google 语音识别和语音合成 `com.google.android.tts` | 已安装，当前有效公开服务在此包内；名字有 tts 并不表示只支持合成 |
| `com.google.android.apps.speechservices` | 未安装；不能单靠这个包名不存在断言“Google 语音服务未安装” |
| Gboard `com.google.android.inputmethod.latin` | `18.2.6.969776716-release-arm64-v8a` / `175981886`；启用，没有匹配的 RecognitionService |

adb 全包 action 查询与普通 App 查询一致：正常查询共3个，含禁用组件共4个；
声明此 action 的 Google package 只有 Google App 和 `com.google.android.tts`。
adb 包信息显示 `com.google.android.tts` 首次安装时间为 **2026-10-03 22:41:36 +0800**，
晚于旧 ASR 报告；本报告更新的是当前设备事实，不覆盖旧测试的时间上下文。

## 第一轮调用（22:57，待代理调整后重测）

显式 component：

```text
com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService
```

每次都创建新的测试实例，没有修改 `voice_recognition_service`。

| 请求文本 | 完整 callback 顺序（相对启动 ms） | 转写 |
| --- | --- | --- |
| 再讲一点 | ready(563, `{}`) → RMS(716, -2 dB) → error(5479, **2**) | 无 |
| 三星堆为什么这么有名 | ready(60, `{}`) → RMS(200, -2 dB) → error(5046, **2**) | 无 |
| 马尔康有什么值得看的 | ready(72, `{}`) → RMS(308, -2 dB) → error(5050, **2**) | 无 |
| 系统默认服务 | error(18, **5**)；没有 ready | 无 |

三次 Google 调用均没有 `beginningOfSpeech`、`partialResults`、`endOfSpeech`、`finalResults`、
`bufferReceived` 或其他回调；每次仅有一个 RMS callback。缺失的回调明确记为未收到，没有补造。
Google 原始错误 **2 = ERROR_NETWORK**；vivo 原始错误 **5 = ERROR_CLIENT**。
Google error 2 本身没有给出 hostname、HTTP 状态或失败网络层；本次不能进一步宣称根因一定是代理／防火墙／服务器。
Agent未修改网络设置；用户随后自行设置全局代理重测，记录见后文。

## 是否加入 backend 优先级

当前建议：**暂不加入生产优先级**。已证明公开入口存在，却没有一次中文 final result，质量及可靠性均未过 Gate。
本机第一轮错误发生在 ready 之后，代理重测又出现无终结结果；沿途现有回退规则不会在 ready 后自动重启到 Vosk，
贸然把它放在首位可能把已可用的一次性中文输入变成网络失败或超时。

若后续同设备、实际发声三句及包含地名的对照通过，可另做最小改动：
给系统 backend 允许传入可选的公开 ComponentName，先确认其正常可解析，再显式使用该服务；
保留默认系统路径及 Vosk，并在单独任务中确认 ready 后网络失败的回退语义。
Journey、Director、语音 UI 无需因此重写。本次没有实施这些修改。

## 验证、证据与复跑

- 独立 Java 编译、dex、APK打包和 APK v3 签名验证 PASS；真机安装与普通 App 枚举 PASS；第一轮四次请求均得到终结 error。
  全局代理后完整三句均触发探测上限，无 Google 终结 callback。没有观察到探测 App 崩溃。
- 沿途产品代码、manifest、模型、APK未修改，真机原安装仍为 `0.3.0 / versionCode 5`。
- 不重跑与文档/独立诊断无关的生产模拟及全量单测；此结果不是沿途 backend 集成 Gate。
- 独立源码：`scripts/asr-reprobe/`；本地原始证据：`artifacts/google-asr-reprobe/`（gitignored）。
- `probe.jsonl` 与 `logcat.txt` 保留普通应用枚举、全部 RMS 和所有 callback；`shell-services-*.txt` 交叉验证；
  `voice-settings-ui.xml` 保留只读设置页面；没有原始音频。
- 初版 probe APK SHA256：`4BA14DCBDA68A32A1D61167BAD37A483557A5D2080FCD8F349A50FB579735D3C`。
- 测试结束后卸载独立 `com.kitt.asrprobe`，删除其私有诊断文件与临时麦克风授权；电脑证据保留。
  不恢复或更改用户自行设置的代理，不修改Google组件、默认输入法或系统默认识别器。

复跑：设置已有 `ANDROID_HOME` / JDK17 `JAVA_HOME`，运行 `scripts/asr-reprobe/build.ps1`；
安装独立 `artifacts/google-asr-reprobe/probe.apk`，打开 `com.kitt.asrprobe/.ProbeActivity`，
本人授予麦克风权限，依次点三句、等 ready 后真人发声，再测试系统默认。
`adb logcat -d -s GoogleASRProbe:I '*:S'` 读取全部日志。无需覆盖沿途、不需要启用其他组件。

### 第一轮完整普通 App JSONL

下方为原始日志，含所有已收到回调，后续重测另行追加。

```jsonl
{"wallTimeMs":1791039432827,"event":"environment","attempt":0,"sdk":36,"model":"V2405A","uid":10316,"targetSdk":35,"defaultRecognizer":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","defaultIme":"com.google.android.inputmethod.latin\/com.android.inputmethod.latin.LatinIME","voiceInteractionService":"com.google.android.googlequicksearchbox\/com.google.android.voiceinteraction.GsaVoiceInteractionService"}
{"wallTimeMs":1791039432829,"event":"googlePackage","attempt":0,"package":"com.google.android.googlequicksearchbox","label":"Google","version":"17.61.20.ve.arm64","versionCode":301812194,"appEnabled":true}
{"wallTimeMs":1791039432830,"event":"googlePackage","attempt":0,"package":"com.google.android.tts","label":"Google 语音识别和语音合成","version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"appEnabled":true}
{"wallTimeMs":1791039432830,"event":"googlePackageAbsent","attempt":0,"package":"com.google.android.apps.speechservices"}
{"wallTimeMs":1791039432832,"event":"googlePackage","attempt":0,"package":"com.google.android.inputmethod.latin","label":"Gboard","version":"18.2.6.969776716-release-arm64-v8a","versionCode":175981886,"appEnabled":true}
{"wallTimeMs":1791039432832,"event":"queryNormal","attempt":0,"components":["com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"]}
{"wallTimeMs":1791039432835,"event":"service","attempt":0,"package":"com.google.android.googlequicksearchbox","service":"com.google.android.googlequicksearchbox\/com.google.android.voicesearch.serviceapi.GoogleRecognitionService","serviceEnabled":false,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"17.61.20.ve.arm64","versionCode":301812194,"normalQuery":false,"explicitResolve":false,"settingsActivity":"com.google.android.apps.gsa.settingsui.VoiceSearchPreferences"}
{"wallTimeMs":1791039432835,"event":"service","attempt":0,"package":"com.google.android.tts","service":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.google.android.libraries.speech.modelmanager.languagepack.settings.SettingsActivity"}
{"wallTimeMs":1791039432837,"event":"service","attempt":0,"package":"com.vivo.ai.copilot","service":"com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_RECOGNITION_SERVICE","version":"6.9.3.0","versionCode":6090300,"normalQuery":true,"explicitResolve":true,"settingsActivity":null}
{"wallTimeMs":1791039432838,"event":"service","attempt":0,"package":"com.facebook.aura","service":"com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_SPEECH_RECOGNITION_SERVICE","version":"9.0.0.23.178","versionCode":1061401224,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.facebook.aura.main.AuraMainActivity"}
{"wallTimeMs":1791039434490,"event":"availability","attempt":0,"recognition":true,"onDevice":false,"recordAudioGranted":true}
{"wallTimeMs":1791039445209,"event":"create","attempt":1,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039445212,"event":"startListening","attempt":1,"elapsedMs":3,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791039445772,"event":"ready","attempt":1,"elapsedMs":563,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","bundle":{}}
{"wallTimeMs":1791039445925,"event":"RMS","attempt":1,"elapsedMs":716,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","rmsDb":-2}
{"wallTimeMs":1791039450688,"event":"error","attempt":1,"elapsedMs":5479,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","code":2}
{"wallTimeMs":1791039454519,"event":"create","attempt":2,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039454520,"event":"startListening","attempt":2,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名"}
{"wallTimeMs":1791039454579,"event":"ready","attempt":2,"elapsedMs":60,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","bundle":{}}
{"wallTimeMs":1791039454719,"event":"RMS","attempt":2,"elapsedMs":200,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","rmsDb":-2}
{"wallTimeMs":1791039459565,"event":"error","attempt":2,"elapsedMs":5046,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","code":2}
{"wallTimeMs":1791039461104,"event":"create","attempt":3,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039461105,"event":"startListening","attempt":3,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的"}
{"wallTimeMs":1791039461176,"event":"ready","attempt":3,"elapsedMs":72,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","bundle":{}}
{"wallTimeMs":1791039461412,"event":"RMS","attempt":3,"elapsedMs":308,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","rmsDb":-2}
{"wallTimeMs":1791039466154,"event":"error","attempt":3,"elapsedMs":5050,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","code":2}
{"wallTimeMs":1791039470482,"event":"create","attempt":4,"elapsedMs":0,"component":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","expected":"系统默认服务测试","mode":"default","language":"zh-CN"}
{"wallTimeMs":1791039470482,"event":"startListening","attempt":4,"elapsedMs":0,"component":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","expected":"系统默认服务测试"}
{"wallTimeMs":1791039470500,"event":"error","attempt":4,"elapsedMs":18,"component":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","expected":"系统默认服务测试","code":5}
```

## 全局代理后的完整三句重测（23:09:42–23:11:16）

用户自行设置全局代理，并配合诊断界面三句发声。日志记录了语音开始，但没有转写，
因此无法从 ASR 结果验证逐字输入或评价中文准确率。仅探测公共接口，不代替成功的中文验收。

| 请求文本 | 全部 callback 顺序（ms） | 最终探测处置 |
| --- | --- | --- |
| 再讲一点 | ready(74) → RMS(218, -2 dB) → beginningOfSpeech(1108) | probeTimeout(30027)，cancel/destroy |
| 三星堆为什么这么有名 | ready(73) → RMS(215, -2 dB) → beginningOfSpeech(1185) | probeTimeout(30003)，cancel/destroy |
| 马尔康有什么值得看的 | ready(80) → RMS(231, -2 dB) → beginningOfSpeech(1882) | probeTimeout(30007)，cancel/destroy |

三句均未收到 partial / endOfSpeech / final / error / buffer / 其他回调，RMS各一次。
**probeTimeout不是Google返回的ERROR_SPEECH_TIMEOUT(6)，也不是ERROR_NETWORK_TIMEOUT(1)。**
实际的服务终结 error code 在这一轮是“未返回”，不能人为补码。

过程中另有三次中间重试：一轮“再讲一点”11720ms离开页面取消；
一轮“再讲一点”30002ms探测超时；“三星堆”29971ms离开页面取消。
再一次“再讲一点”13227ms取消，是Agent重新打开诊断页造成的页面切换，明确不计服务失败。
它们均无转写，完整日志一并保留。Activity重建会重置attempt，使用wallTimeMs及logcat PID区分会话。

### 代理调整及最终重测完整 JSONL

```jsonl
{"wallTimeMs":1791039949726,"event":"create","attempt":5,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039949727,"event":"startListening","attempt":5,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791039949900,"event":"ready","attempt":5,"elapsedMs":174,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","bundle":{}}
{"wallTimeMs":1791039950080,"event":"RMS","attempt":5,"elapsedMs":354,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","rmsDb":-2}
{"wallTimeMs":1791039951282,"event":"beginningOfSpeech","attempt":5,"elapsedMs":1556,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791039961446,"event":"cancel","attempt":5,"elapsedMs":11720,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","reason":"activityStopped"}
{"wallTimeMs":1791039965325,"event":"environment","attempt":0,"sdk":36,"model":"V2405A","uid":10316,"targetSdk":35,"defaultRecognizer":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","defaultIme":"com.google.android.inputmethod.latin\/com.android.inputmethod.latin.LatinIME","voiceInteractionService":"com.google.android.googlequicksearchbox\/com.google.android.voiceinteraction.GsaVoiceInteractionService"}
{"wallTimeMs":1791039965326,"event":"googlePackage","attempt":0,"package":"com.google.android.googlequicksearchbox","label":"Google","version":"17.61.20.ve.arm64","versionCode":301812194,"appEnabled":true}
{"wallTimeMs":1791039965327,"event":"googlePackage","attempt":0,"package":"com.google.android.tts","label":"Google 语音识别和语音合成","version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"appEnabled":true}
{"wallTimeMs":1791039965328,"event":"googlePackageAbsent","attempt":0,"package":"com.google.android.apps.speechservices"}
{"wallTimeMs":1791039965329,"event":"googlePackage","attempt":0,"package":"com.google.android.inputmethod.latin","label":"Gboard","version":"18.2.6.969776716-release-arm64-v8a","versionCode":175981886,"appEnabled":true}
{"wallTimeMs":1791039965329,"event":"queryNormal","attempt":0,"components":["com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"]}
{"wallTimeMs":1791039965331,"event":"service","attempt":0,"package":"com.google.android.googlequicksearchbox","service":"com.google.android.googlequicksearchbox\/com.google.android.voicesearch.serviceapi.GoogleRecognitionService","serviceEnabled":false,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"17.61.20.ve.arm64","versionCode":301812194,"normalQuery":false,"explicitResolve":false,"settingsActivity":"com.google.android.apps.gsa.settingsui.VoiceSearchPreferences"}
{"wallTimeMs":1791039965331,"event":"service","attempt":0,"package":"com.google.android.tts","service":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.google.android.libraries.speech.modelmanager.languagepack.settings.SettingsActivity"}
{"wallTimeMs":1791039965333,"event":"service","attempt":0,"package":"com.vivo.ai.copilot","service":"com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_RECOGNITION_SERVICE","version":"6.9.3.0","versionCode":6090300,"normalQuery":true,"explicitResolve":true,"settingsActivity":null}
{"wallTimeMs":1791039965339,"event":"service","attempt":0,"package":"com.facebook.aura","service":"com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_SPEECH_RECOGNITION_SERVICE","version":"9.0.0.23.178","versionCode":1061401224,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.facebook.aura.main.AuraMainActivity"}
{"wallTimeMs":1791039965340,"event":"availability","attempt":0,"recognition":true,"onDevice":false,"recordAudioGranted":true}
{"wallTimeMs":1791039966245,"event":"create","attempt":1,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039966246,"event":"startListening","attempt":1,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791039966292,"event":"ready","attempt":1,"elapsedMs":47,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","bundle":{}}
{"wallTimeMs":1791039966429,"event":"RMS","attempt":1,"elapsedMs":184,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","rmsDb":-2}
{"wallTimeMs":1791039967445,"event":"beginningOfSpeech","attempt":1,"elapsedMs":1200,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791039996247,"event":"probeTimeout","attempt":1,"elapsedMs":30002,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","limitMs":30000}
{"wallTimeMs":1791039997320,"event":"create","attempt":2,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791039997321,"event":"startListening","attempt":2,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名"}
{"wallTimeMs":1791039997383,"event":"ready","attempt":2,"elapsedMs":63,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","bundle":{}}
{"wallTimeMs":1791039997528,"event":"RMS","attempt":2,"elapsedMs":208,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","rmsDb":-2}
{"wallTimeMs":1791039998471,"event":"beginningOfSpeech","attempt":2,"elapsedMs":1151,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名"}
{"wallTimeMs":1791040027291,"event":"cancel","attempt":2,"elapsedMs":29971,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","reason":"activityStopped"}
{"wallTimeMs":1791040167692,"event":"create","attempt":3,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791040167694,"event":"startListening","attempt":3,"elapsedMs":2,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791040167770,"event":"ready","attempt":3,"elapsedMs":78,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","bundle":{}}
{"wallTimeMs":1791040167933,"event":"RMS","attempt":3,"elapsedMs":241,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","rmsDb":-2}
{"wallTimeMs":1791040169484,"event":"beginningOfSpeech","attempt":3,"elapsedMs":1793,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791040180487,"event":"environment","attempt":0,"sdk":36,"model":"V2405A","uid":10316,"targetSdk":35,"defaultRecognizer":"com.vivo.ai.copilot\/.framework.wakeup.CopilotRecognitionService","defaultIme":"com.bytedance.android.doubaoime\/.ImeService","voiceInteractionService":"com.google.android.googlequicksearchbox\/com.google.android.voiceinteraction.GsaVoiceInteractionService"}
{"wallTimeMs":1791040180491,"event":"googlePackage","attempt":0,"package":"com.google.android.googlequicksearchbox","label":"Google","version":"17.61.20.ve.arm64","versionCode":301812194,"appEnabled":true}
{"wallTimeMs":1791040180492,"event":"googlePackage","attempt":0,"package":"com.google.android.tts","label":"Google 语音识别和语音合成","version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"appEnabled":true}
{"wallTimeMs":1791040180493,"event":"googlePackageAbsent","attempt":0,"package":"com.google.android.apps.speechservices"}
{"wallTimeMs":1791040180494,"event":"googlePackage","attempt":0,"package":"com.google.android.inputmethod.latin","label":"Gboard","version":"18.2.6.969776716-release-arm64-v8a","versionCode":175981886,"appEnabled":true}
{"wallTimeMs":1791040180494,"event":"queryNormal","attempt":0,"components":["com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"]}
{"wallTimeMs":1791040180495,"event":"service","attempt":0,"package":"com.google.android.googlequicksearchbox","service":"com.google.android.googlequicksearchbox\/com.google.android.voicesearch.serviceapi.GoogleRecognitionService","serviceEnabled":false,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"17.61.20.ve.arm64","versionCode":301812194,"normalQuery":false,"explicitResolve":false,"settingsActivity":"com.google.android.apps.gsa.settingsui.VoiceSearchPreferences"}
{"wallTimeMs":1791040180496,"event":"service","attempt":0,"package":"com.google.android.tts","service":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":null,"version":"googletts.google-speech-apk_20260817.01_p0.966249458","versionCode":210673049,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.google.android.libraries.speech.modelmanager.languagepack.settings.SettingsActivity"}
{"wallTimeMs":1791040180497,"event":"service","attempt":0,"package":"com.vivo.ai.copilot","service":"com.vivo.ai.copilot\/com.vivo.ai.copilot.framework.wakeup.CopilotRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_RECOGNITION_SERVICE","version":"6.9.3.0","versionCode":6090300,"normalQuery":true,"explicitResolve":true,"settingsActivity":null}
{"wallTimeMs":1791040180501,"event":"service","attempt":0,"package":"com.facebook.aura","service":"com.facebook.aura\/com.facebook.aura.assist.HatchRecognitionService","serviceEnabled":true,"appEnabled":true,"componentEnabledSetting":0,"appEnabledSetting":0,"exported":true,"permission":"android.permission.BIND_SPEECH_RECOGNITION_SERVICE","version":"9.0.0.23.178","versionCode":1061401224,"normalQuery":true,"explicitResolve":true,"settingsActivity":"com.facebook.aura.main.AuraMainActivity"}
{"wallTimeMs":1791040180502,"event":"availability","attempt":0,"recognition":true,"onDevice":false,"recordAudioGranted":true}
{"wallTimeMs":1791040180919,"event":"cancel","attempt":3,"elapsedMs":13227,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","reason":"activityStopped"}
{"wallTimeMs":1791040182716,"event":"create","attempt":1,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791040182716,"event":"startListening","attempt":1,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791040182790,"event":"ready","attempt":1,"elapsedMs":74,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","bundle":{}}
{"wallTimeMs":1791040182934,"event":"RMS","attempt":1,"elapsedMs":218,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","rmsDb":-2}
{"wallTimeMs":1791040183824,"event":"beginningOfSpeech","attempt":1,"elapsedMs":1108,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点"}
{"wallTimeMs":1791040212743,"event":"probeTimeout","attempt":1,"elapsedMs":30027,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"再讲一点","limitMs":30000}
{"wallTimeMs":1791040214675,"event":"create","attempt":2,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791040214676,"event":"startListening","attempt":2,"elapsedMs":1,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名"}
{"wallTimeMs":1791040214748,"event":"ready","attempt":2,"elapsedMs":73,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","bundle":{}}
{"wallTimeMs":1791040214890,"event":"RMS","attempt":2,"elapsedMs":215,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","rmsDb":-2}
{"wallTimeMs":1791040215860,"event":"beginningOfSpeech","attempt":2,"elapsedMs":1185,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名"}
{"wallTimeMs":1791040244678,"event":"probeTimeout","attempt":2,"elapsedMs":30003,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"三星堆为什么这么有名","limitMs":30000}
{"wallTimeMs":1791040246240,"event":"create","attempt":3,"elapsedMs":0,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","mode":"explicit","language":"zh-CN"}
{"wallTimeMs":1791040246243,"event":"startListening","attempt":3,"elapsedMs":3,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的"}
{"wallTimeMs":1791040246320,"event":"ready","attempt":3,"elapsedMs":80,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","bundle":{}}
{"wallTimeMs":1791040246471,"event":"RMS","attempt":3,"elapsedMs":231,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","rmsDb":-2}
{"wallTimeMs":1791040248122,"event":"beginningOfSpeech","attempt":3,"elapsedMs":1882,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的"}
{"wallTimeMs":1791040276247,"event":"probeTimeout","attempt":3,"elapsedMs":30007,"component":"com.google.android.tts\/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService","expected":"马尔康有什么值得看的","limitMs":30000}
```
