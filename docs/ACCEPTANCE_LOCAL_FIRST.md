# V0.4.1 / #24 本地优先运行热修验收

2026-10-14，分支 `fix/offline-native-no-destination-question`，[Draft PR #25](https://github.com/shimao1115/KITT/pull/25)。先完成工程交付，随后用户明确批准原位升级与真机验收，现已执行并STOP；不合并、不卸载、不清数据、不进入下一版本。工程结果见 [工程证据](evidence/v041-verification.json)，真机结果见 [手机证据](evidence/v041-phone-acceptance.json) 和根 [HANDOFF](../HANDOFF.md)。

## 2026-10-14 真机结果（当前）

- **安全升级PASS**：原包code14及原证书实核；单次install-r成功到code15，装机APK摘要与8beac9f7…8ce43f一致，首次安装时间/数据目录及inode不变。可见账号、ChatGPT启用、语速1.0/默认中文声音保留；精确code14 model/effort因列表不可用未逐项读到，单列PENDING。
- **联网地方讲述与0自动云PASS**：普通按钮真实NETWORK±37m/1秒、新都本地杨慎/桂湖两段引擎完成，云Director/研究启动0、目的地提问0；用户确认首次杨慎听清。独立120秒实机Probe为109秒可信NETWORK、自动完成1、自动Director/Research增量0/0。未观察到卫星GPS。
- **主动问题真实失败隔离部分PASS**：固定“再讲一点”本地完成、0云；单次开放问题Director1/Research0返回FAILED，短句处理结束、无挂起。接下来的重播/这里有什么及同Journey失败后本地恢复因位置已失去可信度PENDING。
- **断网位置链PENDING**：Wi-Fi/流量均0、物理互联网false（VPN仍VALIDATED），120秒仅旧位置，新鲜GPS/NETWORK与可信区县0，正确安静/未知、不问目的地、自动云0/0；不能称断网地方故事PASS。
- **独立断网TTS真人PASS**：本地zh/1.0固定试听，STARTED→success=true、UI试听结束、用户确认中文清楚；首个操作误开下拉列表无发音，重试才有效。本项不代替断网定位链。
- **实际按钮打断真实TTS、10分钟默认静音/提前退出、跳过计数、后台LOCATION服务/正常结束PASS**；满10分钟实际到期、音频中skip专门语义、真人ASR/语义重播、异常进程恢复与连续行驶PENDING。未改生产main、资产、版本、APK，仅增加androidTest探针并构建/lint实跑。
- 手机已回出发页、旅程/前台服务/录音/TTS停止，Wi-Fi关/流量开恢复，未改VPN/账户或保存设置。Issue24 OPEN / PR25 Draft，验收结束STOP。

## 实现范围

真实物理旅程固定本地自动路径：新鲜 GPS/NETWORK → 已安装新都包的可信几何 → OfflineNarrator → 系统已安装离线中文 TTS。Wi-Fi、移动网络、ChatGPT 账号/模型是否可用都不决定自动开口；未覆盖、粗/旧位置、LAST_KNOWN 或坏/缺包安静，不能自动转云。网络存在不证明云模型可用。

有限“这里有什么/再讲一点/再说一遍”在有本地资料时直接本地处理；普通开放式问题、图片或时效请求保留原按需在线路径，失败/超时有界提示，之后仍能本地讲述。连接恢复不自动启动研究，也不打断当前本地 TTS。真实旅程要求离线中文 TTS，但联网语音识别仍保持既有系统→Vosk 回退顺序，断网识别只用 Vosk。

取消自动目的地追问，保留用户主动提供目的地与可选路线参考。修正旧 ASK_USER 将其他澄清答复写成目的地的问题；目的地抑制不占用合法澄清的一次回答窗口。模拟仍走专用 Fake/Director 主链。无新内容、成品故事、评分删除或新设置；#20/#13/#14/#17/#18 的旧真机边界保持独立。

## 安装前工程证据能证明的范围

- `LocalFirstHotfixTest`：联网/断网/云不可达自动讲述在调用 coroutine 前就提交本地 speech，Provider/Research 选取和云 dispatch 均为0；未知、旧、粗、桥接、未覆盖/缺包均不转云。这里、补充、重播0云；开放问题仅按需调用；实时研究失败、35秒 Director 超时后本地恢复；新本地问题取消旧云响应，迟到不补播；网络恢复/丢失不打断本地音频，不启动自动研究。
- `LocalFirstRuntimeTest`：Robolectric 的 `LocationManager GPS` 事件经过真实 `RealLocationSource` 和正常 KittRuntime 接线，故障 ChatGPT 配置、系统联网已验证仍本地出声、Director/Research 计数0；联网且选中云声音时实际提交给 Shadow TTS 的声音为本地中文，保存的用户声音选择不改。
- `OfflineLocationTest`：联网本地几何优先，0 Geocoder；断网 GPS↔NETWORK、粗/旧位置、离开新都后不保留旧区县/街道。新都资料和定位可信阈值未放宽。
- 既有全量生命周期、静音/提前恢复、skip/打断、ASK_USER一次监听、PREPARE/旧响应、恢复、缓存/已播、解析与六组成都→绵阳 Fake 模拟全部执行。离线声音选择和实际设备声音品质分别判断。

以上工程部分使用合成LocationManager/时钟、Fake/异常Provider、TestVoice/Shadow TTS，不能代替上方真机证据。工程阶段没有真实ChatGPT请求；后续真机阶段仅发上述一次主动问题。没有账号凭据、GPS轨迹或私人聊天导出，日志留本机忽略的artifacts。

## 后续补齐步骤（不要重复安装当前code15）

1. 当前手机已核0.4.1/code15、原包/原签名/原数据，先审阅上方PASS/PENDING和证据；不重装、不降级、不卸载/清数据，不使用标准包替换runtimecheck。
2. 停车/乘客状态，在新都可信内部取得新鲜精确物理 GPS。普通“开始沿途”不问目的地；Wi-Fi/流量打开、云端不可达或未授权时，本地故事应直接发声，Director/Research **新增实际调用0**（用开始前后差值，不能把历史主动请求计数算作自动请求）。不可为验收破坏既有账号/设置。
3. 再断 Wi-Fi/流量验证同一路径；切换网络不打断本地当前播放。无可信位置应未知，离开覆盖范围应安静/显示缺资料，不能猜地址或自动转云。
4. 这里有什么、再讲一点、再说一遍应直接响应且0云。分别试skip、说话/打字打断、10分钟安静/提前恢复、后台/恢复及结束；确认没有旧音频复活。
5. 主动问一个开放式/今天路况问题，按实际网络/Provider条件回答或短句失败；随后仍可本地讲述。听清离线中文，检查已保存账号/模型/语速/声音选择保留。模型列表不可达不能阻断本地故事。

没有新鲜物理位置时，对应时段自动地方链继续PENDING；单独试听不算整链。历史许可几何的2026权威复核、镇街和内容深度不在本轮解决。本轮实机结束STOP等待用户决定，不进入#23或V0.5.0。
