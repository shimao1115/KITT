# 沿途 V0.3 — 工程验收与真实搜索缺口

日期：2026-10-03（Asia/Shanghai）。继承 `b5a2018 main`，前置中文 ASR／文字上屏／打字已经由用户验收。
本 Destination 唯一任务源是 [TASK_POST_ASR_YANTU_V03.md](TASK_POST_ASR_YANTU_V03.md)，包括第4A节主动按需搜索补充。施工期间同步了上游 `0b5694d`，未来统一语音栈仍保持 PARKED。

## 已实现

- Launcher、驾驶页、设置、前台通知、授权返回提示、TTS试听和 Director 人格均使用“沿途”；包名仍为 `com.kitt.reader`。
- `LocalResearchProvider` 独立于旁白：整章与主动问题复用同一 Responses / ChatGPT OAuth transport。
  研究使用独立 strict Local Dossier schema，`web_search` + `tool_choice=required` + `include=web_search_call.action.sources`，`store=false`。
  只有完整 search-call 和来源证据才能成为 READY；没有 tool-call 的模型输出不是搜索。
- 完整省／市／区县／镇街身份消歧；每趟每个区县和镇街各一次，最多两项后台研究同时进行，90秒超时，无自动重试。
  调研结果含事实、来源标题／URL／域名／类别、置信度与资料缺口。事实 URL 必须存在于 tool sources 或 citation annotations。
  官方认定要求政府来源；仅有 OTHER 线索的事实不进入可讲材料。没有实际搜索时不允许以素材栏目或模型记忆补足当地事实。
- session 缓存不保存原始网页／查询历史；结束／重置作废工作、清空缓存。短暂 unresolved 不重新搜索或制造已检查章节的假重访。
- READY_UNCHECKED 跨讲话、监听、打字、看图、冷却、安静保留，实际 Director 请求开始才消耗。离开旧章节或接近区后不补播。
  后完成的区县背景也会得到检查机会；已经拿到证据的旧章节可供真实重访复用。
- 真实 Geocoder 在道路速度下至少15秒＋250m刷新（没有 speed 字段时用位移／时间判断）；慢速30秒＋150m，静止120秒。
  最多一个 lookup，旧标签只复用450m／30秒，迟到 lookup 也用相同限制；GPS保持即时且权威。
- 重要地标绕过普通45秒检查间隔及自动旁白软冷却；用户对话、跳过、看图、安静、stale、空间去重保护保持。
- 默认听众是第一次来的外地人；解释陌生人物、概念、地方关联，给足背景和具体细节；重大节点可讲透，仍无作文模板／题材配额／代码字数目标。
- 主动搜索：明确查找和实时问题本地强制；其余由同一 Director 判断现有证据是否充分。
  足够就直接回答；缺乏当地依据时先研究，再交回原 Director/TTS。typed 与 ASR 共用原 handler。
  失败只说“刚才没查到可靠资料，暂时无法确认”，不向模型索取虚假的搜索结果；新意图能取消迟到回答。
  主动搜索整体最多140秒，普通主动／自动 ticket 仍45秒；返回同一 Journey／章节／目的地，不自动合并时效事实。

## 自动验证

最终命令：`scripts/verify.ps1 -Offline`。Debug/Release 单测、assemble、lint、APK v2签名均要求通过。
工程里程碑 `5ed61bf`：**Debug 222 / Release 222 项测试 PASS，零失败／跳过，build／lint／APK签名 PASS**。
最终数量、SHA与完整日志见本地 `artifacts/verification.json`、`artifacts/SHA256.txt`、`artifacts/yantu-final-verification.log`；根 HANDOFF 记录最终结果。

| 范围 | 证据 |
| --- | --- |
| Local Dossier 与机会生命周期 | LocalResearchTest、ChapterOpportunityTest；真实生产 Context/Journey/Loop，确定性研究／Director／Voice |
| 主动按需搜索 | ActiveResearchTest；已有资料不搜索、明确／实时强制、缺资料先搜、失败／超时／新意图、ASR／打字、同旅程、同TTS |
| OAuth 回归 | 既有 ChatGptTest + 搜索拒绝不破坏普通旁白、研究不持有认证网络锁、断开后的研究不交付、无隐藏 API-key fallback |
| 真车解析策略 | DrivingAreaResolutionTest；80/100km/h，每2秒fix，700m短章节，含 speed=0 情况：6/6章节都有lookup，最小间隔16秒；这是策略测试，不是实体Geocoder验收 |
| 新都→雎水研究链 | DossierSimulationTest：29点、90,912m、13章节＋6区县＝19次唯一研究、13次有证据的测试旁白、零stale/cancel/failure |
| 原黄金路径 | FullSimulationTest：80km/h约68分钟模拟；start/end、ASK_USER、主动打断、安静真实墙钟、PREPARE、纪要保留 |
| 独立地标 | 原四节点各一次，零stale/cancel/failure；地标与dossier同实体按现有id／名称匹配去重，主动深入仍可用 |
| 已接受交互 | 原ASR后端、live transcript、打字回答与取消、QuietCountdown、RouteHint、Visual Talk、真／模拟GPS隔离完整回归 |

旧 `batch-simulation.txt` 仍是素材架编辑自由度回归，不是实际搜索验收；新的 `dossier-simulation.txt` 使用合成事实包检查生产传递链，仍不算真实发现。

## ChatGPT-plan hosted search 探测：NOT RUN

已验证的外部状态：`adb devices` 没有设备；当前进程没有 `OPENAI_API_KEY`。沿途自己的授权保存在手机 Android Keystore，当前无法访问。
没有读取桌面 Codex/ChatGPT 凭据，没有用别的产品账号冒充沿途授权，没有抓搜索引擎结果页，没有隐藏切换计费通路。

因此**不能宣称 ChatGPT-plan 支持或拒绝 hosted web search，也没有可以报告的真实拒绝码**。
官方 [Responses web search 文档](https://developers.openai.com/api/docs/guides/tools-web-search) 已读取，确认 Platform 请求的 required search 和来源字段；它不是该账号通路支持证明。

实现会用同一已验证注册／refresh／目录作专用 SSE 研究；保留 tool items 和 citations，即使 terminal snapshot 空或漏 tool item。
若服务实际拒绝，则记录安全的 HTTP/code/request_id/shape，研究诊断显示未被接受，避免相同配置不断重试；普通 Director 授权不因搜索参数拒绝被暂停。
只有用户显式保存独立 OpenAI Responses 研究配置才使用该 API key；设置保存后可重新探测（重新授权后保存同样配置即可清除之前的拒绝记录）。

## 新都黑盒：生产验收 BLOCKED，外部发现预检成功

生产 prompt、fixture、AreaCard 与候选**没有加入**期望的四个名字。测试只检查输入无提示，不能假装它验证了实际发现。
手机／API credential 缺口阻止一次完整生产 Adapter → Dossier → Director 内容验收；三星堆、雎水与普通镇街真实研究同样待验。

施工时另用桌面 web 工具做了一次无名字提示的外部预检，首条查询只有：

`四川省 成都市 新都区 历史文化 人物 景区 古迹 博物馆 文物保护`

首轮结果自行出现四个期望对象，随后仅为核实已发现线索查询政府来源。这些材料只写在验收文档，**不注入运行时**：

- 杨升庵／杨慎：新都籍明代学者和文学人物；桂湖与其故里纪念相关。省地方志办公室官方发布的[博物馆介绍](https://m.thepaper.cn/newsDetail_forward_33193199)解释其人、经历及馆园关系；
  [重庆市文旅委合作调研](https://whlyw.cq.gov.cn/zwxx_221/ztzl/bswhlyzljs/gzxx/202112/t20211210_10130486.html)也将杨慎与新都文化资源关联。
- 桂湖：古典园林与杨升庵纪念、博物馆结合。上述博物馆来源支持“人—地方—园林”的关联，而不只列名字。
- 宝光寺：属于新都的重要寺院／文化资源，相关[中国网报道](https://guoqing.china.com.cn/2024-05/27/content_117216189.htm)提供寺院布局与文物保护利用线索；正式文保认定仍需政府名单核实，不能凭媒体认定。
- 新繁东湖：首轮即可找到；[四川人大、新都区人大来源](https://www.scspc.gov.cn/chengdu/202603/156637.html)将唐代东湖公园与新繁古镇的历史文化地标关联。

这证明开放检索能发现具体对象，**不等于沿途的真实黑盒验收已通过**，也未把当天开放／活动／交通状态当作稳定事实。

## 最短剩余人工验收

1. 连接原授权 vivo 手机并安装 `artifacts/kitt-v0-debug.apk`。保留既有账号及语音设置；过期才由用户本人重新授权。
2. 设置保存 ChatGPT 实际账号模型，顶部沿途点5次，以100km/h／16×开始新都→雎水。每个新章节有“开始研究→READY/FAILED→检查”的可检查记录。
3. 初始新都定位后进入设置，点“测试已保存的本地研究配置（当前位置）”，这是**独立新鲜研究探测**。检查完整 dossier 是否包含四个对象及可理解背景／来源；不在输入里提示答案。
4. 捕获 `adb logcat -d -s KITTResearch KITTAuth KITTArea KITTSim`。若实际拒绝，记录确切状态／code；只有显式配置可用独立研究 API 才走替代通路。
5. 普通主动问一个 dossier 已有答案，再用语音／打字分别问“查一下附近有什么博物馆”“这个寺庙今天开放吗”；观察搜索、来源、原TTS回答与结束后同旅程。断网时只诚实报告。
6. 继续广汉／三星堆、普通乡镇和雎水，检查初来者能听懂、讲述够具体、追问增加新层；讲话／安静时跨章节，确认还在本章才延迟检查、驶离绝不补播。
7. 保留[联合手机验收](COMBINED_PHONE_ACCEPTANCE.md)中真实GPS、后台／锁屏、TTS与主体内容质量的剩余 Gate。ASR升级不在此批次。

设置探测是用户显式的额外请求，不计入自动“每趟每章一次”；正常GPS不会重复搜索。无设备时无需用户为工程选择参数；待连接手机后只补这项外部验收。
