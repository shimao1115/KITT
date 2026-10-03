# Overview → Topic 研究链收口 — 2026-10-04

## 最终真机结果

vivo V2405A，沿用手机已有 ChatGPT OAuth、`gpt-5.6-luna`、默认 effort；独立 OpenAI research 未勾选。
OAuth hosted search 仍为 `stream=true`、required `web_search`、来源 include。没有新 API key、Provider 切换或重新授权。

| 项目 | 最终包实测 |
| --- | --- |
| Overview search 开始 → `response.completed` | 42991 ms，HTTP 200 |
| Overview 研究开始 → READY | 43018 ms；章节入口 → READY 43041 ms |
| Overview 可靠对象 / 来源 | 5 / 6 |
| READY → Director 请求 | 同一完成回调，约 4 ms 内开始；不等待 Topic |
| 章节入口 → 第一段有证据旁白 | 49799 ms，SPEAK_NOW；系统中文 TTS 成功播放完成 |
| Topic 对象 | 成都市新都区杨升庵祠及桂湖（从已发现对象选择） |
| Topic search → `response.completed` | 75199 ms，HTTP 200 |
| Topic 开始 → READY | 75215 ms；探针轮询读到结果为 75418 ms |
| Topic 可用事实 / 来源 | 3 / 5；另外两条缺政府认定依据的事实被剔除 |
| 用户“再讲一点” | USE_CONTEXT → SPEAK_NOW；补充杨氏家规、职业／家礼／教育和公共家风教育角度 |
| 追问系统 TTS | started=yes，completed=yes，Voice completed success=true |
| 最终两次研究 SSE completion timeout | 未出现；每次仍受生产 90 秒总边界限制 |

Overview 自动发现的5个对象（标题按实际输出保留）：

- 成都市新都区宝光寺
- 成都市新都区杨升庵祠及桂湖
- 成都市新都区天府家风馆
- 成都市新都区新繁棕编
- 成都市新都区新繁东湖

验收名称杨升庵／杨慎、桂湖、宝光寺、新繁东湖全部发现；人物与祠园合并成一条线索。
这些名称只在验收断言和此记录中列出，未进入生产 Overview prompt、固定 query 或路线 fixture。
Topic query 中的名称来自这次真实 Overview 返回的对象，不是预先注入答案。

本轮并未达到十几秒概况目标；最终43秒明显小于旧全区请求的约87秒失败，但不是十几秒或成功率保证。
Topic 仍可能接近90秒；本轮不扩大为新的 transport、架构或调度工程。

## 实现边界

- Overview schema 只有 orientation、sources、objects，对象只含 title、why_it_matters、family、salience、source_ids。
  Prompt 目标4–8个不同对象、5–10来源；简短 orientation 和基础介绍，不做分类配额。人物与关联园／祠／馆合并，避免重复占位。
  Parser 上限8对象／10来源；证据不足不填凑。没有完成搜索或实际来源匹配的对象不能 READY。
- Director 继续接收原 Local Dossier 证据卡；Overview adapter 明确只可使用有依据的基础介绍，不得从标题扩写故事或年代。
- Topic 使用窄对象输入，目标3–6条事实／3–8来源；parser 上限6条事实／8来源。来源质量与政府认定检查仍先执行，剔除后可以少于目标，不能制造数量。
- 现有区县／章节 Overview 槽最多2个。后台 Topic 最多1个，且本章 Overview READY 后才启动；刚讲对象优先，未匹配时选高 salience 线索。
  pending 不进入 Director 或模拟移动门禁。没有自动批量队列、自动 retry 或持久知识库。
- 用户打字／语音入口先取消 Topic，再走原主动判断／必要搜索支线。Topic READY 新证据可用于追问；
  “再讲一点”等当前主题追问补充指代信息，不更改八字段 Director contract 或主动搜索 policy。
- 驶离取消未完成 Topic，取消后即使晚返回也不接收；完成资料仅 session 缓存至旅程结束，重访可用，离开现场不补播。
- Journey、GPS、ASR、TTS、主动问题研究 schema、独立 OpenAI 备用入口、V0.3.1 静音修复保持。
  政府认定校验补全市／县级规则，仍要求实际 provenance 中的 government + OFFICIAL 引用。

## 本轮失败及中间结果也保留

没有自动重试；以下是开发时分别手动启动的诊断运行：

1. 第一轮 Overview HTTP 请求后约28912 ms出现 SocketException，状态 FAILED，仍发出一次安全 Director opportunity。不能解释为 capability rejection。
2. 调整前完整链跑通：Overview 39424 ms、7来源／6对象；宝光寺 Topic 约43262 ms、7来源／4事实。
   发现同一人物园馆占多个条目，随后只缩轻 Overview 并要求合并同一对象。
3. 一次最终包启动时模型目录请求 UnknownHostException，研究尚未开始；属于 DNS/setup 失败。
4. 调整后 Overview 41069 ms response.completed／41110 ms入口 READY，发现四个验收对象；桂湖 Topic 50566 ms completed／50585 ms READY。
   “再讲一点”却转去讲宝光寺，因此补上当前主题指代，并新增回归；没有把仅内容不同当作专题追问验收成功。
5. 最终结果见上表：追问确实继续桂湖／杨氏家风的新角度，原生 TTS 完整结束。

## 可复现真机验收

`HotfixPhoneProbe` 的 `staged-research` 模式只存在于 androidTest APK。它启动真实主 Activity，预热手机原账号模型目录，
用无答案提示的“省／市／区县”身份、持续更新的静止测试 fix 驱动生产 ChapterResearch、Provider、DirectorLoop、Journey 与原生 AndroidVoice。
Director 包装器仅记录返回值；目的地是普通“去绵阳，沿途了解地方”，没有验收对象提示。
该固定现场是诊断输入，不把手机真实 GPS 改成该位置。普通 GPS／模拟路线代码未改。

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest --offline
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell am instrument -w -e mode staged-research com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell run-as com.kitt.reader cat cache/staged-research-report.json
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell run-as com.kitt.reader cat cache/staged-research-timeline.jsonl
& "$env:ANDROID_HOME/platform-tools/adb.exe" uninstall com.kitt.reader.test
```

最终原始 Debug 证据在忽略目录 `artifacts/staged-research-final-live-report.json`、
`staged-research-final-live-timeline.jsonl`、`staged-research-final-live-transport.log`。
保存实际研究证据和阶段／耗时，不导出 token、auth headers、注册凭据、录音或轨迹。

新增14项自动回归覆盖轻量schema、真实tool与provenance、官方认定、READY即时机会、Topic串行与非阻塞、
用户抢占、离开取消、session重访／结束清理、Topic timeout、Overview failure、黑盒请求不注入、优先深挖已讲对象、追问保持主题与新证据。
完整 Debug **259**／Release **246** 测试通过，失败／跳过均0；两种 assemble 与 lint 通过。
Debug APK v2签名通过并与手机安装包 SHA256 一致。Release 以已有 Android debug keystore 签出验收包，v2／v3校验通过；这是测试签名，不是 Play 发布身份。
交付：`artifacts/kitt-v0-debug.apk`（74654651 bytes）；Release 测试签名包 `artifacts/kitt-v0-release.apk`（70609344 bytes）。
Debug SHA256：`0C718DDDED32204023ABE864C41F309C2A4443C5364767881E7DC9531FFE1DF9`。
Release SHA256：`CD8ABCCF1ECD31F5A062336E0C84AB6A32A682E455D472B917F78ACD25979D21`。
完整 Gate 日志 `artifacts/staged-research-verified-gates.log`；交付复核 `artifacts/staged-research-package-check.log`。
核对后结束测试旅程并卸载 androidTest APK，保存授权／Provider／语音设置保留。旧交接存档见 [V0.3.1研究诊断交接](HANDOFF_V031_RESEARCH_DIAGNOSTICS.md)。

**本轮在这条研究链完成后停止，不继续扩新功能。**
