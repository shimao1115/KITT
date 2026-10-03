# 沿途 V0.3.1 — 全静默热修复交接

截至 **2026-10-04（Asia/Shanghai）**：从 upstream `main / 74bdf85` 合入热修复要求（合并点 `da1cd22`），优先完成 [HOTFIX_V03_TOTAL_SILENCE.md](docs/HOTFIX_V03_TOTAL_SILENCE.md)。已确认并修复研究全局门禁、模拟移动等待研究、失败章节机会丢失；保留真实搜索证据要求。原中文 ASR、识别文字上屏、typed 回复与用户接管路径保持。

当前包：`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`，**沿途 0.3.1 / versionCode 6 / Android 8.0+**，76,215,833 bytes。SHA256：`7BD51B527748DEF21A87E12198ED95CEBD73209B5A4CCE56C4F971D52DCA2AA2`。
最终全量 Gate 与真机交互通过；托管搜索未成功且没有明确服务拒绝，准确状态见 [热修复验收](docs/ACCEPTANCE_V03_SILENCE_HOTFIX.md)。此前完整 V0.3 能力／内容／ASR 交接存档在 [HANDOFF_V03_PRE_HOTFIX.md](docs/HANDOFF_V03_PRE_HOTFIX.md)。

## 当前可运行能力

- `research.pending` 不再阻止整个 Director 或模拟行驶；初始意图询问、独立有依据地标与主动输入可继续。思考、TTS、用户回答窗口、监听和图片交互仍可暂停开发模拟；真实 GPS 不暂停。
- 当前章节 RESEARCHING 保留待检查状态；READY 或 FAILED 得到一次安全章节机会。区县 READY 可以在镇街研究未完时使用；暂时解析缺口不会反复触发已检查的 FAILED。驶离只缓存、不晚播。
- 失败仍是 FAILED；没有完成工具调用、来源与 provenance 就没有 Local Dossier，不把模型记忆当成查证结果。主动搜索失败仍只简短诚实反馈。
- 研究开始／完成／失败耗时、检查延迟原因、机会保留／消耗有明确日志。该 vivo 屏蔽应用 logcat，Debug 使用最多约128KB的 cache 诊断备份；不保存凭据、完整转录、录音或原始轨迹，运行时重建即重置，Release 无此文件。
- 真机发现的 ChatGPT 暂时网络失败改为固定60秒回退等待；后续本地拒绝不再滑动截止时间或覆盖原故障。
- 真机发现旧 Android HTTP 连接连取消关闭也会阻塞；薄 Adapter 改用原生 Call 取消和总时限（旁白／授权35秒、研究90秒），不自动重试／重定向，不改变授权协议、请求 payload 与事实校验。
- 原旅程开始／结束、10分钟安静、用户打断TTS、ASK_USER一次监听、PREPARE失效、真实／模拟隔离、纪要与既有视觉交流功能继续保留。

## Provider 与当前真机事实

新安装默认 Fake（只验交互，不验搜索）。连接的 vivo V2405A 原配置是 **ChatGPT / gpt-5.6-luna / 默认effort**，独立 OpenAI 研究未勾选；无新密钥、重新授权或替换 Provider 操作。

最终包真机开场 **ASK_USER，约6.5秒**，Android TTS 完成成功，一次监听正常打开／结束。研究等待时模拟前进至4016m、8480m及后续28572m；主动打字一般机制问题经同一 Director 判定 GENERAL_KNOWLEDGE，约12.4秒返回 SPEAK_NOW，TTS完成成功。

用户解锁后最终安装完成，已核对手机 APK 与交付 APK 的 SHA256 相同。后台研究在90012ms超时，取消约3ms完成并释放研究槽；驶离新都不补播。已结束模拟、删除临时测试 APK、恢复临时日志属性。

**独立 ChatGPT hosted-search：未成功，未收到明确服务拒绝。** 当前保存账号的新都街道生产研究请求收到 HTTP200，随后11465ms时 SocketException；没有通过完成工具调用／来源校验的 READY包。背景研究也校验失败或超时。不能把这些结果称为搜索成功、搜索不支持或服务拒绝。

## Build / test 与模拟

`scripts/verify.ps1 -Offline`：**PASS，Debug232／Release232项单测，零失败、零跳过，两种 assemble／lint 与 Debug v2签名均通过**。包含真实无响应头 TCP 超时、关闭操作阻塞仍及时取消、晚到结果不接收、固定回退等待及静默回归。

成都→绵阳黄金路径与新都→雎水链继续使用生产 Context／Journey／Director，加确定性研究与 fake Voice。合成研究报告：29点、13章节、6区县、19次研究、13段旁白、33检查机会，零stale/cancel/failure。这不是真实 hosted-search 或新都黑盒验收。

本地复核：`artifacts/hotfix-final-gates.log`、`verification.json`、`SHA256.txt`、`dossier-simulation.txt`、`full-simulation.txt`、`hotfix-phone-proxy-retest.log`。更多前后回归日志与独立 Probe 方法见热修复验收文档。

## 已知限制与用户最小动作

最终包已装在手机，无需用户再安装／授权。真实地方搜索仍需要稳定完成的托管流；当前没有证据要求购买搜索 API 或更换授权。复测方法在热修复验收文档，若后续实测返回授权失效，才由用户本人重新OAuth。

真实地方内容／新都四对象无提示发现、来源支持与主观叙述质量仍需实际成功研究后验收，不能用确定性 fixture、桌面 web 或模型记忆代替。网络不可用时自动旁白继续安静降级，主动请求简短反馈；该修复不承诺断网仍讲地方事实。

Geocoder 覆盖、短章节驶离、OEM后台／锁屏、TTS主观听感与离线 Vosk 地名准确率沿用 [联合手机验收](docs/COMBINED_PHONE_ACCEPTANCE.md) 与 [ASR验收](docs/ACCEPTANCE_CHINESE_ASR.md) 的限制。没有扩展语音栈、多Agent或全国POI数据。

## 下一次人工总验收最短步骤

1. 解锁手机，`adb install -r artifacts/kitt-v0-debug.apk`，保留原ChatGPT授权与语音设置。
2. 保持独立OpenAI研究未勾选；顶部“沿途”点5次，以100km/h／16×启动新都模拟。观察开场询问、TTS及研究等待中的位置变化。
3. typed／ASR分别问一般问题和需要查证的问题，验证用户优先、原TTS、失败诚实反馈与同一旅程恢复。
4. 在设置测试已保存的本地研究配置；分别记录 READY真实来源、HTTP/code/request_id拒绝或网络错误／超时，不能相互混称。
5. 此设备取诊断可用 `adb shell run-as com.kitt.reader cat cache/hotfix-diagnostics.log`；普通设备同时保留 `adb logcat -s KITTResearch KITT KITTSim KITTAuth`。

## 最近 milestone commits

- 本批热修复提交：`fix: restore V0.3 narration while research is pending`（提交后记录SHA）。
- `da1cd22` — 合入当前 upstream main。
- `74bdf85` — 全静默热修复要求。
- `595ca82` — vivo Google ASR 公开入口与失败复测。
- `7de4303` — V0.3 交接与真实研究 Gate。
- `5ed61bf` — V0.3 地方研究与主动提问实现。
