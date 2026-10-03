# 沿途 V0.3.1 — OAuth hosted search 能力验收交接

**最新结论（2026-10-04）：A，最小web_search成功完成，ChatGPT OAuth hosted search可用。** vivo已有授权／`gpt-5.6-luna`／默认effort，使用用户指定的新都区一句话问题、`stream=true`、`web_search`与`tool_choice=required`，去掉Local Dossier schema。**HTTP200，tool item于2802ms出现；实际生命周期in_progress 2832ms、searching 4011ms、completed 5995ms；21个tool provenance URL，citation后总计22个不同来源；output text 6217ms开始，response.completed 6697ms到达，总耗时6699ms。** 20个实际SSE事件全部保留，正文／URL值／item ID／凭据不记录，见 [最小搜索能力验收与完整时间线](docs/ACCEPTANCE_MINIMAL_SEARCH_CAPABILITY.md)。

本轮仅Debug-only SSE capability探针／测试：独立180秒诊断截止（此次6.699秒完成），生产仍为原90秒；没有再运行非流式真机请求。生产Provider／Local Dossier／Journey／Director／ASR／TTS均未修改，没有购买API、更换账号／Provider、新OAuth或token导出。测试包已卸载，真机与交付APK哈希相同。本轮停止于证明能力，完整Local Dossier的85秒超时仍需后续单独定位／缩减研究请求；没有在本批次扩大生产改动。

## 上一批次诊断（历史）

**上一批次当时为第3类；“capability未确定”已被本轮A结果取代。** 当时非流式生产研究payload为HTTP400／2116ms／`Stream must be set to true`，完整Dossier SSE为HTTP200／87089ms／85秒完成超时。其参数拒绝、超时与未获READY事实仍保留，不能再解释为账号不支持搜索。历史原始证据见 [OAuth研究传输验收](docs/ACCEPTANCE_OAUTH_RESEARCH_TRANSPORT.md)。

本轮只有Debug／测试探针与回归：HTTPS、不重定向、不自动重试、90秒总截止、bounded完整JSON读取、取消释放与脱敏证据。**生产 Provider、Local Dossier契约、Journey、Director、TTS／ASR均未修改；后台研究仍保持streaming。** 未换账号／Provider、未购买API key、未要求新OAuth。测试包已从手机卸载，手机与交付Debug APK SHA256相同，原保存授权／设置保留。

截至 **2026-10-04（Asia/Shanghai）**：从 upstream `main / 74bdf85` 合入热修复要求（合并点 `da1cd22`），优先完成 [HOTFIX_V03_TOTAL_SILENCE.md](docs/HOTFIX_V03_TOTAL_SILENCE.md)。已确认并修复研究全局门禁、模拟移动等待研究、失败章节机会丢失；保留真实搜索证据要求。原中文 ASR、识别文字上屏、typed 回复与用户接管路径保持。

当前包：`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`，**沿途 0.3.1 / versionCode 6 / Android 8.0+**，76,215,833 bytes。SHA256：`BDAF346C5A25C6312AC8E9DED47CFFAC0114CD67E9AB48BF628013BC6D8C8074`。新增探针仅在Debug，Release不包含该类。
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

**独立 ChatGPT hosted-search capability已证明可用（A）。** 本轮最小SSE请求完整完成真实工具调用、22来源provenance与response.completed。完整Local Dossier仍未获READY；此前完整请求的完成超时／SocketException与非流式参数拒绝仍需分别解释，不能称为搜索能力拒绝。

## Build / test 与模拟

本轮 `scripts/verify.ps1 -Offline`：**PASS，Debug245／Release232项单测，零失败、零跳过，两种assemble／lint与Debug v2签名均通过**；前置最小probe／socket回归8项PASS。覆盖所有实际事件（含added、searching、未知type）、来源与完整终态、A/B/C/D区分、DONE不能替代response.completed、脱敏／大小边界、取消截止与晚到事件冻结；原研究／授权／Journey／语音／静默回归继续PASS。最终APK与手机SHA256复核PASS。

成都→绵阳黄金路径与新都→雎水链继续使用生产 Context／Journey／Director，加确定性研究与 fake Voice。合成研究报告：29点、13章节、6区县、19次研究、13段旁白、33检查机会，零stale/cancel/failure。这不是真实 hosted-search 或新都黑盒验收。

本轮本地复核：`artifacts/search-capability-final-gates.log`、`search-capability-regression.log`、`search-capability-phone.log`、`search-capability-report.json`、`search-capability-timeline.jsonl`、`verification.json`及`SHA256.txt`。完整20事件表已入库；旧传输诊断、热修复与模拟日志保留为历史证据。

## 已知限制与用户最小动作

最终包已装在手机，无需用户再安装／授权。真实完整地方研究仍需要Local Dossier READY；最小搜索6.699秒完成已证明账号工具能力，尚不证明完整请求的超时是输出规模、服务端生命周期还是网络等哪一因素。本route强制stream=true，不再用非流式JSON测试。没有证据要求购买API、更换Provider或重新授权；最小SSE复测见能力验收文档。

真实地方内容／新都四对象无提示发现、来源支持与主观叙述质量仍需实际成功研究后验收，不能用确定性 fixture、桌面 web 或模型记忆代替。网络不可用时自动旁白继续安静降级，主动请求简短反馈；该修复不承诺断网仍讲地方事实。

Geocoder 覆盖、短章节驶离、OEM后台／锁屏、TTS主观听感与离线 Vosk 地名准确率沿用 [联合手机验收](docs/COMBINED_PHONE_ACCEPTANCE.md) 与 [ASR验收](docs/ACCEPTANCE_CHINESE_ASR.md) 的限制。没有扩展语音栈、多Agent或全国POI数据。

## 下一次人工总验收最短步骤

1. 解锁手机，`adb install -r artifacts/kitt-v0-debug.apk`，保留原ChatGPT授权与语音设置。
2. 保持独立OpenAI研究未勾选；顶部“沿途”点5次，以100km/h／16×启动新都模拟。观察开场询问、TTS及研究等待中的位置变化。
3. typed／ASR分别问一般问题和需要查证的问题，验证用户优先、原TTS、失败诚实反馈与同一旅程恢复。
4. 若复核搜索能力，按最小能力验收文档安装匹配app／test APK，只运行`search-capability`（SSE／180秒诊断）；查看逐事件时间线与A/B/C/D终态。四对象黑盒与完整内容验收仍等待有效Local Dossier。
5. 此设备取诊断可用 `adb shell run-as com.kitt.reader cat cache/hotfix-diagnostics.log`；普通设备同时保留 `adb logcat -s KITTResearch KITT KITTSim KITTAuth`。

## 最近 milestone commits

- `801a712` — 最小OAuth hosted search真机A：gpt-5.6-luna／20个SSE事件／22来源／6699ms完整完成，Debug-only180秒诊断与245／232 Gate；无生产链路改动。
- `9a2fd17` — 上一批次研究传输诊断交接（capability未确定的历史结论现已被A取代）。
- `23ee7ca` — 隔离OAuth非流式参数拒绝与SSE完成超时，Debug-only bounded探针、239／232 Gate及最终66项相关回归；真机证据不支持账号搜索不可用或非流式默认切换。
- `e01f301` — 已验证V0.3.1热修复交接，本轮继承起点。
- `3397221` — 恢复研究等待期间的旁白／主动交互，保留搜索真实性，修复网络等待并完成232×2 Gate及真机复测。
- `da1cd22` — 合入当前 upstream main。
- `74bdf85` — 全静默热修复要求。
- `595ca82` — vivo Google ASR 公开入口与失败复测。
- `7de4303` — V0.3 交接与真实研究 Gate。
- `5ed61bf` — V0.3 地方研究与主动提问实现。
