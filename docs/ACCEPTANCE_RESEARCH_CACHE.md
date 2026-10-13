# 沿途 V0.3.5.2 地区研究证据缓存 — Issue #18

日期：2026-10-13（Asia/Shanghai）。从 V0.3.5.1/code11 继承，只实现稳定、有来源的 Overview/Topic 跨旅程复用。定位、行政区 enrichment 生命周期、语音、八字段 Director 与独立实时研究入口保持。#19 家庭签名移交不是本轮阻塞。

## 存储与证据边界

- `filesDir/research-evidence-v1/`，每项一个版本1 JSON；文件名为地区完整 `AreaIdentity.key` + 规范主题的 SHA256。Overview 的空主题与 Topic 分开；省、市、区县、镇街逐字段比对。缺省/市/区县身份不持久化。主题只做 NFKC、大小写及空白规范，不合并人物别名或语义主题。
- 保留原研究时间、来源 URL/标题/类别、事实/概况对象、资料缺口，以及 completed-hosted-search 标识；SHA256 检查内容损坏，不代表来源真实性认证。只有实际 Provider 响应经现有 completed search/provenance 解析后才标记 `verifiedSearch`。读取再次验证来源、事实引用、官方认定和 schema；空事实、未经核验、假域名、地区不匹配、旧版本和损坏记录均拒绝。
- 单项≤40000 UTF-8 bytes，最多128项、2MiB；写入成功后按最早写入淘汰。临时文件 `.json.new` 同目录写入、文件 fsync 后原子替换，无不安全复制降级；临时残片不参与命中，下次写入清理。写入/验证失败不阻止本次成功研究用于 Director。真实断电/Android文件系统故障未实测，自动测试注入 rename 前失败。
- 同目录实例共享每个身份/主题的协程锁和磁盘短锁；锁在取消后释放。一次 Journey 内已有 Entry 防止重复 Overview；最多两个研究槽和一个后台 Topic 保持。
- Overview 命中 READY_UNCHECKED，已缓存 Topic 随对应已发现对象载入素材；新的 Director 判断照常。结束/reset 清除 examined、pending、机会、专题尝试、PREPARE 等运行态，保留磁盘证据。没有固定旁白、录音、网页正文、请求、对话、坐标轨迹或凭据入缓存；新增诊断仅 hit/miss/write/rejected/research_call 和原因摘要。
- 生产入口在模拟或 Fake 配置时禁止读写持久证据。稳定事实不因 start 或固定 TTL 失效；内部 `invalidate` 可按身份/主题失效。历史证据显示原 searched_at 与“此前有来源的研究（本次未联网复查）”，不声称本次刚查过。
- 明确重新研究/搜索、查最新，以及开放、票价、天气、路况、临时展览、时事走既有 `researchQuestion`。按需动态答案不落入稳定缓存；网络失败仍由原 Journey 简短报告无法确认。没有新增离线 Director；关闭全部 AI 网络不会让远程 Director 在本地运行。

## 自动验证

本版最终 Debug/Release、assemble、lint、签名与模拟结果见根 [HANDOFF](../HANDOFF.md) 及 [验证摘要](evidence/v0352-verification.json)。原始构建日志仅在本地忽略的 artifacts 目录；本文件不以旧版数字证明本版。

`ResearchEvidenceStoreTest` 新增16项，和 `ActiveResearchTest` 13项一起实际执行通过29项；首次负例失败是测试直接注入镇街/未来时间绕过生产位置护栏的错误假设，修正为生产 AreaResolutionCache 与前进的测试时钟后通过，未修改定位策略。

| 确定性案例 | 实际断言 |
| --- | --- |
| 第一次完整地区 + 深挖 | 区县 Overview=1、镇街 Overview=1、Topic=1，write=3 |
| 同旅程重入 | Overview 调用不增加 |
| clear/end + 新管理器/新磁盘存储对象，研究 Provider 拒绝网络 | 区县 Overview=0、镇街 Overview=0、Topic=0，hit=3，原 searched_at 保留 |
| 新 Journey/Loop 重建 | Director 对已缓存资料再次实际判断；CHECKED 不跨旅程继承 |
| 同名不同省/市/区县/镇街 + 格式规范主题 | 独立身份不会命中，格式等价主题命中 |
| 20个并发 Overview/20个 Topic，经两个存储实例 | 每种仅1次 fetch；取消后可重新取得锁 |
| 空、失败、Fake/未核验、无来源、坏引用、错地区、坏置信/价值、零时间 | 不写入 |
| 空/坏 JSON、旧版本、改写身份/事实、原子替换失败、存储不可写 | 安全回退；成功资料可供 Director 使用，不破坏已有其他证据 |
| 单项/总体/数量上限 | 拒绝超大条目，容量淘汰后处于上限内 |
| 粗定位、旧位置失效后新定位尚无名称 | 生产 AreaResolutionCache 撤销镇街，不能从知识缓存恢复旧镇街身份 |
| 9类明确实时/刷新请求 + 服务失败 | 强制实时路径，无 USE_CONTEXT 判断；不修改稳定文件，失败不冒充现状 |
| 隐私/模拟隔离 | 文件无运行态/语音/坐标/凭据字段；事件无地区名/来源URL；隔离运行不读写原证据 |

以上全部是合成 hosted-search 响应、合成事实和确定性 Provider/Voice，不发真实查询，不证明真实来源内容或现实行政边界。跨重建用独立管理器/存储对象从磁盘读取，**不等于手机杀进程实测**。摘要见 [research-cache](evidence/v0352-research-cache.txt)。成都→绵阳模拟使用29点、90912m粗粒度轨迹，非导航级；六组既有模拟/驾驶解析回归还包括700m合成边界场景，实际运行情况见交接。

## 2026-10-13 真实手机验收

办公室 Windows、vivo V2405A / Android16，10:28–10:37（Asia/Shanghai）。仅使用交付现成 runtimecheck APK，没有构建或更改业务代码。账号/界面在设备上只读检查；原始UI、账号文件、凭据、网页、来源URL、坐标轨迹、对话和敏感原始日志均未保存至验收产物。日志先在手机内用awk筛选计数/异常类型，再输出脱敏摘要。机器摘要见 [v0352-phone-acceptance.json](evidence/v0352-phone-acceptance.json)，以前的 [工程JSON](evidence/v0352-verification.json) 保留历史。

| Gate | 实测结果 | 证据与范围 |
| --- | --- | --- |
| 现成APK/升级 | PASS | APK SHA256、唯一证书、applicationId/code12/0.3.5.2与任务卡匹配；原机code11/0.3.5.1同证书；签名脚本PASS；仅install -r返回Success；安装后base.apk摘要一致 |
| 原账号/设置保留 | PASS | 数据目录标识、首次安装时间保持；常规UI账号显示在内存中比对一致；ChatGPT、GPT-6.1-Sol/low、1.0×、系统默认中文声音、通知允许、独立研究未启用保持；没有转储凭据；首趟真实调用成功 |
| 新鲜自然地区身份 | PASS（区县）/PENDING（镇街） | 普通开始，无GPS注入；NETWORK精确位置，首趟±30m/0秒、重启后±36m/0秒；诊断确认完整四川省/成都市/新都区。未建立完整镇乡街道身份，VPN仅网络信号 |
| 区县Overview A→B | PASS | 来源校验完成，objects/facts=4/4；first write=1、研究调用=1、search started/completed=1/1；正常结束→force-stop确认进程不存在→新进程普通开始→overview hit=1、重复Overview研究调用=0；自动Director启动2次 |
| 镇街Overview | PENDING | 未建立身份，first write/重启hit/重复调用均无相应研究样本，不能把未触发的0次当PASS |
| Topic | PENDING | 未自然完成Topic；first write/重启hit/重复调用均未验，不为刷日志增加研究 |
| 新旅程Director | PASS（机会重建）/PENDING（持续在线讲述） | 重启后自动请求2次，终态STALE、FAILURE；后者记录UnknownHostException。缓存仍载入，自动模式安静降级；不把未讲述解释成缓存未命中 |
| 时效性D | PASS（实时路径/诚实失败）/PENDING（答案质量） | 仅一次今日天气打字问题，SEARCH_REQUIRED；真实search started/completed=1/1；结果未过校验，IllegalArgumentException，active FAILURE=1；界面明确“刚才没查到可靠资料，暂时无法确认。”，TTS完成回调1；稳定cache write=0、Overview重复研究=0 |
| 旧功能E | PASS（有限界面冒烟）/PENDING（真人语音） | 启动、结束页、回到出发页、安静10分钟/提前结束、打字聚焦/提交观察正常；TTS有开始/完成回调；真人听感、成功ASR转写、播放中说话/打字停止TTS未验。第二进程窗口FATAL EXCEPTION计数0 |
| 手机总验收 | PENDING | #18保持OPEN，等待用户最终决定；不把工程交付或区县PASS扩写为全部实机PASS |

第二进程在天气问题之前：overview hit=1、Overview研究调用=0、Topic研究调用=0、search started/completed=0/0；天气问题之后：search started/completed=1/1，Overview研究仍0、cache write仍0。这1次是时效性问题研究，不是重复地区Overview。区县是当前唯一自然完成的缓存链，镇街/Topic无样本。来源满足现有completed hosted-search/provenance契约不等于独立语义事实审计；不量化Tokens节省。

系统逆地理编码不保证室内持续新鲜；#17连续镇街、#14外场定位、#19家庭签名仍独立PENDING。UnknownHostException为当次网络异常，天气资料校验失败的根因未判定；没有保存原始响应作猜测，也未重试付费查询或新增Hotfix。若用户要追查，只另开最小“单次时效研究响应契约校验”验收/Hotfix决定，不扩张本轮。

手机已正常结束旅程并回出发页。最短复验：保持可信新鲜同区县，普通开始，查看脱敏overview hit及同区县Overview research_call计数，结束。已有区县证据应命中，不必再次主动研究；force-stop仅终止进程，不执行pm clear。镇街/Topic仅在自然可靠身份/研究可用时补测；真人TTS/ASR与播放中接管由现场用户短测。没有新增授权/密钥要求。到此停止，等待用户决定，不施工#13、离线包或定位/UI改造。

## 安装前人工验收计划（历史PENDING，结果以上方实测为准）

1. 核对交接中的同签名、`com.kitt.reader.runtimecheck`、code12 包，再 `adb install -r` 原位升级。不卸载/清数据，不使用标准 `com.kitt.reader` 代替原账号空间。本轮未连接手机或读取凭据。
2. 保持新鲜位置与完整新都区/镇街身份，普通“开始沿途”。首次成功 Overview/一个 Topic 后结束。日志应出现 evidence_cache write，研究调用事件有对应 stage；缺来源/失败不能算首次成功。
3. 结束后杀进程、重启应用，在同一新鲜身份再开始：区县/镇街各 hit，0重复 Overview；已研究对象 Topic hit，0重复 Topic；Director 仍判断本趟要讲什么。不要以“讲了相同对象”判缓存失败，语义去重属于 #13。
4. 仅拒绝研究网络时既有证据卡仍可提供素材；全部远程 Provider 断网时自动模式安静降级。从未研究地区保持无证据，不能编造。问“查最新/重新研究/今天开放/票价/天气”，确认实时研究或明确失败，不能把历史缓存当今日状态。
5. 说话/打字打断、安静/提前恢复、ASR/TTS 与结束正常。真实账号升级保留、OEM存储行为、真模型来源质量、手机杀进程与省 Tokens 的量化结论本轮均 PENDING；无需为证明命中反复触发昂贵搜索。没有新增授权/密钥要求。

完成工程交付即停止，等待 V0.3.5.2 小版本人工验收；#14/#17外场、#19家庭签名、#13和离线包分别跟踪。
