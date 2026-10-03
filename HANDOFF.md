# 沿途 V0.3 — 当前交接

截至 **2026-10-03（Asia/Shanghai）**：已从最新 `main` 继承已验收的中文 ASR、识别文字上屏、打字与回答窗口，完成当前
[TASK_POST_ASR_YANTU_V03.md](docs/TASK_POST_ASR_YANTU_V03.md) 的内容／搜索／触发工程实现和第4A节主动按需搜索补充。
**真实 hosted-search 能力与新都黑盒内容验收尚未通过**：当前无连接手机，沿途 OAuth 授权在手机 Keystore 内；环境没有 OpenAI API key。
不能据此判断 ChatGPT-plan 支持还是拒绝搜索，也不能用工程替身或桌面 web 预检宣称生产黑盒已通过。

当前可安装包：`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`，**沿途 0.3.0 / versionCode 5 / Android 8.0+**。
大小 **74,005,087 bytes**；SHA256：`F1A814FAF259D756064D49FC205F93702B80134F77401D3CAB814F12696B3525`。
可复核本地 `artifacts/verification.json`、`SHA256.txt`、`yantu-final-verification.log`（全量门禁）与 `yantu-package-final.log`（最终打包复核）。

## 当前可运行能力

- 用户界面／Launcher／通知／TTS试听／授权返回提示／Director使用 **沿途**，Slogan **读懂沿途的世界**；包名、安装身份仍为 `com.kitt.reader`。
- 完整省市区县镇街身份 → 每趟一次本地研究 → 源 URL／标题／类别保留的 Local Dossier → 同一 Director 自由讲述。
  同时最多两项后台研究、90秒界限、无自动重试；研究失败不是“本地无内容”，模型记忆不能冒充搜索。
  静态素材架只是线索；没有真实 web-search-call 或 provenance 的响应不能成为 READY；官方认定须有政府依据，低质量来源只是线索。
- 章节与晚到区县背景保留未检查现场；讲话、监听、打字、看图、冷却、安静只延迟，实际检查才消耗；驶离不补播。
  缓存只到旅程结束，重访可复用；短暂无行政解析不会重复搜索已检查章节。
- 真实 GPS 不等 Geocoder；道路速度下至少15秒＋250m刷新（含缺少 speed 字段时的位移判断），旧标签只复用450m／30秒。
  独立重要地标可绕过普通检查间隔和自动旁白软冷却，仍守用户／静默／新鲜度／接近／去重边界。
- 外地初来者叙述目标：解释对象是谁／是什么、为何有名、地方关联与具体背景，正常有价值主题给足说明，重大节点可讲透；没有固定结构／题材配额。
- 用户主动提问仍走原 typed／ASR → Director → TTS。明确查找及实时问题强制研究，其余由同一 Director 判断现有证据是否足够。
  成功研究带来源回到原回答；失败只简短说未查到可靠资料；回答后保留同一旅程／章节，不建立搜索页面、不自动合并时效事实。
  实时问题带北京时间研究时点；“附近”只有区域 grounding，不伪造方向、距离或步行时间。
- 原 Journey start/end、真实墙钟安静、ASK_USER 一次监听、用户打断、PREPARE 作废、纪要、ChatGPT OAuth、RouteHint、Visual Talk、真／模拟GPS隔离均保留。
  ASR引擎与回答处理未重写，仅试听文案随品牌更新；升级语音栈仍是 BACKLOG。

## 默认 Provider 与外部最小动作

**新安装默认 Fake**，无 key 可跑交互闭环；Fake明确没有实际研究能力，不能验收地方内容。TTS／Vosk离线中文语音不需要 key。
真实旁白首选设置中的 **ChatGPT账号 + 账号实际返回模型／effort**；已有手机配置保持，不隐藏换 Provider 或产生另一通路计费。
历史验收为 `ChatGPT / gpt-5.6-luna / high`、TTS `yue`，本批次未重新验证其当前可用性。

研究默认探测同一 ChatGPT账号的专用 hosted-search 请求；只在显式保存独立 **OpenAI Responses研究配置**后使用它的 API key。
API研究有自身计费，设置写明；兼容聊天 API 不被当作支持搜索。搜索拒绝记录准确 HTTP/code/request_id/shape，不破坏原旁白授权。
设置保存后清除已缓存的搜索拒绝，可重新探测；重新登录后也先保存配置。

**唯一未能在电脑补齐的动作**：连接原授权手机，安装这版，启动新都模拟，在设置点击“测试已保存的本地研究配置（当前位置）”。
若原授权失效，只有用户本人需要完成系统浏览器 OAuth。若实测通路拒绝且没有其它已配置研究 Provider，才需要一条可用搜索 API 的凭据；目前没有证据要求购买或切换。
未读取任何桌面 Codex/ChatGPT凭据，不抓公开搜索引擎结果页，不把模型记忆称作“查到”。

## Build / test 与模拟验收

最终门禁：`scripts/verify.ps1 -Offline`：Debug／Release assemble、unit test、lint、Debug APK v2签名。
**PASS：Debug 222 / Release 222 项单测，零失败、零跳过；两种构建与Lint均通过，安装包v2签名通过。**
全量工程报告与[详细验收](docs/ACCEPTANCE_YANTU_V03.md)均留存；工程里程碑 `5ed61bf` 对应此安装包。

- 成都→绵阳既有黄金路径：**80km/h、90,912m测试折线、约68分钟模拟**，start → GPS → ASK_USER → 一次回答 → TTS → 打断 → 深入 → 安静 → 结束纪要 PASS。
- 新都→雎水研究链：**29点／13章节／6区县、100km/h／16×、19次唯一合成研究、13段依据该事实包的测试旁白**，零stale/cancel/failure。
  此报告使用生产 Context/Journey/Loop 和确定性研究／Director／Voice，**不是真实地方搜索或新都黑盒成功**。
- 原素材架编辑自由度回归保持；四个独立地标各一次，零stale/cancel/failure；chapter／landmark同实体继续去重，主动深入允许新角度。
- 真实驾驶解析策略：80／100km/h + speed=0，对6个700m短章节均有lookup，最小间隔16秒；无请求风暴。实体Geocoder能否返回镇街未由JVM替代。
- 主动分支验证：已有资料不重复查、明确／实时强制、缺资料先搜、来源、超时失败、用户取消、ASR与打字共用路径、同旅程返回原TTS全部覆盖。

本地报告：`artifacts/dossier-simulation.txt`、`driving-area-resolution.txt`、`landmark-simulation.txt`、`batch-simulation.txt`、`full-simulation.txt`。
旧ASR接受与准确率限制完整保存在 [ASR验收](docs/ACCEPTANCE_CHINESE_ASR.md)、[上一版交接](docs/HANDOFF_PRE_V0_3.md)。

## 新都黑盒与已知限制

生产 prompt／AreaCard／fixture **没有注入杨升庵、桂湖、宝光寺、新繁东湖**。无提示输入检查通过；生产真实发现 Gate 仍 BLOCKED。
桌面开放查询自行发现四对象，并保留官方／机构来源的外部预检，见详细验收；它没有被做成运行时种子或知识库，不能代替真实 Adapter验收。
三星堆多角度、普通乡镇资料充实度、雎水真实研究及初来者叙述深度也需要真实 Provider验收。

- ChatGPT-plan是否接受 required `web_search` + 来源返回 + 研究schema 目前 **UNKNOWN / NOT RUN**；不能宣称支持或服务拒绝。
- Geocoder best-effort；无网络／无镇街字段时保持可靠GPS并诚实留空。极短章节、系统定位精度、真实逆地理编码覆盖仍需手机验收。
- 地标几何仍只有四个已有粗粒度参考，未扩大全国POI栈；新发现对象没有可靠坐标时只属章节材料，不伪造空间触发。
- 研究可能90秒；车辆已驶离则只缓存证据，不补播。主动搜索最长140秒，但新用户意图始终能取消；普通主动／自动响应45秒保护保持。
- 长段TTS期间跨多个小章节可能在检查前已经离开；本版不会用待播旁白补偿。模拟只在思考／研究／讲话／监听／看图期间暂停行驶，真实GPS不暂停。
- 来源membership／认定类别验证不是完整网页事实核验；实际故事质量、来源是否支持细节，属内容验收。今日交通／开放没有可靠新近资料时只能说无法确认。
- 不自动合并主动研究进章节，避免时效污染；无永久知识库、原始GPS／录音／照片／网页／完整查询历史留存。
- Vosk中文模型对长口语／地名仍有限、首次解包等待与内存代价不变；未来ASR／TTS／统一语音栈文档保持 BACKLOG，此批次不施工。
- 手机专属GPS／TTS听感／OEM后台锁屏／真实内容质量继续沿用[联合手机验收](docs/COMBINED_PHONE_ACCEPTANCE.md)的剩余Gate。

## 下一次人工总验收最短顺序

1. `adb install -r artifacts/kitt-v0-debug.apk`，保留原ChatGPT授权及中文语音设置；只有过期才重新授权。
2. 保存真实Provider；顶部“沿途”点5次，100km/h／16×开始。新都初始章节做一次设置研究探测，无名字提示，检查四对象、来源与背景。
3. 抓 `adb logcat -d -s KITTResearch KITTAuth KITTArea KITTSim`；确认真实搜索成功或确切拒绝，后者保留诊断而不是假装搜索成功。
4. 用ASR／打字各做已有资料追问、明确查找／今天开放问题，再断网查一次；确认原TTS诚实回答、回到同一旅程，用户随时能接管。
5. 继续广汉／三星堆、普通乡镇、雎水，测试安静或讲话中跨界；检查保留的是当前现场机会、旧现场不补播、追问有新层，最后结束、普通GPS重启、后台锁屏。

## 最近 milestone commits

| Commit | Destination |
| --- | --- |
| `5ed61bf` | 沿途 V0.3：搜索事实包、章节／地标机会、真实驾驶解析、主动按需搜索 |
| `3c1d93b` | 识别文字上屏、打字与ASR同一Director入口（已验收） |
| `bd0bdd9` | 中文一次性语音在vivo实机恢复可用（沿用） |
| `0a8c397` | 安静倒计时显示修复 |
| `d201a38` | 编辑自由度与开放素材架 |
| `bc92395` | 独立地标接近、节点去重与过时保护 |

上游 `0b5694d` 的主动搜索任务补充与未来语音 BACKLOG已经继承；当前未完成的是外部真实研究／内容Gate，不是重新施工已接受ASR。
