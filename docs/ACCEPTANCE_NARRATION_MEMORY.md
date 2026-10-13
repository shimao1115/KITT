# V0.3.6 / #13 — 同趟讲述覆盖验收

2026-10-13（Asia/Shanghai），基线 main `1e17de1` / 手机 code12；目标 0.3.6/code13。最终构建、产物与手机结果见本文件末尾和根 HANDOFF。本轮只收口 #13，工程交付后停止等待用户验收。

## 实现与证据边界

- 最近32条进程内记录分别保留地区、对象、核心事实/角度、landmark_id 和音频状态。`topic` 作为对象身份，`memory_update` 作为分号分隔的事实/角度标签，不再交叉比较。发现对象沿用 Overview title 和研究所属地区，避免 Overview→Topic 回流改变身份。
- 同一个 Director 在原请求中依据 Context 判断别名/同义改写，重复时选 SILENT 或删掉已覆盖部分；App 只比较同对象/地区（或独立 landmark_id）的覆盖标签集合，防止相同标签换旁白、重排或子集重播。新标签、新对象与不同地区的同名对象仍有机会。**本地不是自然语言语义识别器；模型若把同义事实伪装成全新对象/标签，仍需要真人验收发现。** 没有额外模型调用、embedding、向量库、新Schema或Reviewer。
- QUEUED表示交给语音接口，STARTED来自实际TTS onStart；只有最后一块 onDone / 成功complete进入COMPLETED摘要。未证实开始的取消为CANCELLED，已开始后打断为INTERRUPTED，错误/超时为FAILED。epoch + 语音序号共同屏蔽旧/重复回调。TTS引擎完成不是“用户确实听见”。
- 不成功的尝试不算完整讲过，自动也不重放半截；普通主动问题/看图/显式重播可接管。“再讲一点”沿原Director/来源查证链补新材料；重复或SILENT简短说明无可靠新内容，失败使用原简短失败提示。没有新证据不编地方故事。
- “再说一遍”优先本地重播最新一段正文，0新增Director/研究调用；正文仅这一段暂留内存，结束/新Journey/进程重建清空、不checkpoint。恢复文件仍仅保留最多8项简短纪要，恢复摘要标明未验证播放完成，不导入完成覆盖。
- 独立地标的额外接近唤醒仍每节点一次，接近中的依据继续可见；已有事实由Journey过滤，新事实不被整节点黑名单误杀。驶离、精度/新鲜度、45s/1500m/方向、用户优先与PREPARE保护照旧。
- #18磁盘证据只是来源，不是已讲记忆；#14/#17定位、#18剩余自然验收、#19家庭签名及V0.4.0均不在本轮施工范围。

## 回归与模拟

施工前两条红测已实际失败：排队时误记完成、topic与memory_update不同后重复仍交付。修复后核心27项（Journey19 + Loop8，当时的测试集合）通过；最终测试集合与全量结果以验证JSON为准。

新增覆盖包括：同对象同事实改写旁白、事实子集/重排/累计、同对象新事实、不同对象同机制、Overview→Topic回流、PREPARE、离开重入/跨区同名、地标跨区换标题、排队/失败/取消/已开始中断/迟到回调、主动补充新证据/无新材料/搜索失败、0调用重播、普通主动问题、看图接管、旧恢复摘要及不持久化正文。

额外合成重查：粗粒度路线13章节，每章刻意发起3次检查，输出26条独立对象/事实、过滤13次重复；无STALE/FAILURE。**这是标签与主链状态测试，不是真模型语义或正常行驶节奏的证明。** 六组既有节奏模拟单独重跑，并保留本轮施工前对照。

## 最短人工验收

1. 核对本地 runtimecheck APK摘要、唯一原证书、包名和code13；手机当前版本必须小于13且证书一致。只 `adb install -r`，不卸载、清数据或降级，不用标准包替代原账号包；若已经code13，无需重装。
2. 停车或乘客状态，在自然新鲜地区普通开始；先看到一条真实自动旁白和TTS成功完成，网络/研究失败或STALE单独记PENDING，不能当作去重成功。
3. 同路段后续正常检查、Overview→Topic回流或离开再返回，听是否只是换措辞重复。继续有新对象/有来源新事实/独立地标时应自然讲，不能整段失声。真实现场不会因测试强制播报。
4. 打字或说“再讲一点”：新来源支持则补新信息；没新料简短说明。说“再说一遍”：能听到上一段重播。普通问题/看图、播放中打断、安静/提前恢复仍正常；旧自动旁白不续播。
5. 正常结束再开始或重启：不携带上一趟假定已讲事实，不恢复重播正文；#18稳定来源缓存仍可命中。听感结论由用户给出，Issue #13保持OPEN等待验收。

## 最终本轮结果

**工程交付 PASS；手机暂停后已恢复并完成一次真实自动讲述引擎链，真人语义/听感仍PENDING，到此停止。** [全量证据](evidence/v036-verification.json)、[合成重查](evidence/v036-narration-memory.txt)、[手机阶段性记录](evidence/v036-phone-acceptance.json)、[脱敏Probe原结果](evidence/v036-phone-narration.json)。工程全量测试/模拟未在手机接续阶段重跑。

| Gate | 本轮实际结果 |
| --- | --- |
| `scripts/verify.ps1 -Offline` | Debug395 / Release382；失败/错误/跳过全0，新增30项两种Variant均执行；tests、assemble、lint均EXECUTED PASS，最终115任务33执行/82复用 |
| runtimecheck构建 | assembleDebug / compileDebugAndroidTestKotlin / lintDebug最终EXECUTED PASS；assembleDebugAndroidTest本版首轮EXECUTED PASS、最终UP-TO-DATE；75任务13执行/62复用；40个生产Kotlin文件与最终源码一致 |
| 原签名 | 三个生产APK唯一原签名、code13、0.3.6；测试APK同证书；2接受+6拒绝签名回归PASS |
| 施工前红测 | 旧实现两项实际失败，修复后通过；全量过程发现的播放前题材历史/整节点过滤旧断言已迁移到完成历史/事实过滤，最终无失败 |
| 六组模拟 | 下表为本轮实际前后对照；Fake/确定性，不是真语义或手机听感 |
| 合成重查 | 13章×3检查，SPEAK_NOW26、SUPPRESSED13、SILENT0、STALE/FAILURE0；不以整章静音去重 |
| 手机安装 | 用户本人允许vivo守护安装；`adb install -r` Success，已核code13/0.3.6，首次安装时间2026-10-09 09:42:45不变；没有卸载、清数据或降级 |
| 账号/设置核对 | 恢复后正常UI：连接账号1、ChatGPT/GPT-6.1-Sol/low、1.0×、系统默认中文声音、通知允许、独立Responses关闭，符合既有配置；Probe配置前后相同。账号与暂存**升级后**UI文字一致；精确code12→13文字比较未留存。原失败UI仍“等待账号模型列表”，模型/effort控件未就绪；没有数据丢失证据，不导出凭据 |
| V0.3.6真实Director→旁白→TTS | **PASS_ENGINE_CALLBACK_ONLY**；有效Probe98s观察/98s新鲜位置，SPEAK_NOW1、STARTED→COMPLETED1，STALE/FAILURE/SUPPRESSED0，ChatGPT/gpt-6.1-sol/low，runner code0。自然地区、无注入坐标/移动，临时旅程偏好不问目的地，非连续行驶验收 |
| 真人语义/听感、补充/重播 | **PENDING**；用户反馈“没听到或当时不在旁边”，不证明有声或无声故障。没有继续为补听调用模型。当前旅程已结束，上一段重播正文按设计清空 |
| 手机测试修正与构建 | 初次Probe文案“无资料可安静”误触10分钟安静，180s观察/170s新鲜位置、无旁白，无效，不能当去重。仅改测试文案及断言非安静；12s构建，75任务7执行/68复用；compileDebugAndroidTestKotlin、assembleDebugAndroidTest、lintDebug EXECUTED，assembleDebug UP-TO-DATE，生产APK摘要未变 |

| 模拟 | 前 → 后（本轮均实际执行） |
| --- | --- |
| full | 57调用/4声音 → 57/4；80km/h、90912m、68模拟分钟，接管/安静/结束通过；固定Fake只验证交互 |
| accelerated | 22调用/22声音 → 22/22；自动SPEAK_NOW20/SILENT0/STALE0 → 20/0/0，另有ASK_USER1/主动1 |
| batch | 13章、23主题/23调用 → 相同；自动SPEAK_NOW23/SILENT0/STALE0 → 23/0/0 |
| landmark | 4独立地标/28调用 → 相同；SPEAK_NOW4/SILENT24/STALE0 → 4/24/0 |
| dossier | 13讲述/33调用/19唯一研究 → 相同；SPEAK_NOW13/SILENT20/STALE0 → 13/20/0 |
| driving-area-resolution | 700m合成章节，80/100km/h含报速0，12/10查询/最短16s → 相同；不改变Geocoder或定位年龄 |

所有生产包单一证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

| 本地文件（未入GitHub） | 包名 | APK SHA256 |
| --- | --- | --- |
| `H:\CODEX\KITT\artifacts\kitt-v0.3.6-runtimecheck.apk` | com.kitt.reader.runtimecheck | 28795780faeff52b394255cd4c73aa540d64572d4ce72304638f084f48626a81 |
| `H:\CODEX\KITT\artifacts\kitt-v0.3.6-debug.apk` | com.kitt.reader | 7a536b523c9832a2a53b60d1bdc57ac632ff088275dd7bccd52f015794f451af |
| `H:\CODEX\KITT\artifacts\kitt-v0.3.6-release.apk` | com.kitt.reader | 5bf3df44ccef5f59413aa4ee616ac7bf751158579a464b2f4934aa7c89aa161f |

手机已code13，不需要重复安装。下一次停车/乘客状态按上方2–5步，实际听完后在**同一Journey结束前**试补充/重播。没有新增OAuth或密钥待办，本轮停止。#13保持OPEN；#14/#17、#18剩余现场、#19家庭端、V0.4.0均不继续施工。

## 恢复手机后的证据边界

有效Probe简短主题为“杨慎（杨升庵）与升庵祠、桂湖”，只保存状态和短主题，不保存完整旁白/录音/坐标/账号文字。用户允许同签名测试包安装。测试设置临时偏好前显式取消首个自动请求，诊断见CANCELLED1；随后地区就绪的自动请求完成。98s是正式观察窗口，不含Activity启动等待。普通UI另一次开始自然ASK_USER/TTS完成及地区就绪前STALE单独记录，不混进有效Probe计数。

Probe终端字段`research_request_outcome=NEVER_ATTEMPTED`只是当时可见状态回退；诊断已见`Topic started`，不能由它推断没有后台研究或网络调用，更不证明Topic成功。没有补签#18。Probe已正常结束回出发页，启动清理使短期诊断文件不再存在；不得用缺失日志所得的0计数替代原观察。保留脱敏结果及这个解释，真人听感和真实语义由下一次实际聆听确认。

用户随后明确授权再次调用一次以现场聆听。[该次脱敏结果](evidence/v036-phone-listening-retry.json)：180s观察/171s新鲜位置，FAILURE1，SPEAK_NOW/已完成均0，配置不变，runner code0。当次诊断确认Provider返回`server_is_overloaded`，未进入TTS；之前取消的请求另见HTTP传输IOException。研究终端字段仍不充当网络调用数。没有听到旁白是本次请求失败，不能证明音频故障、语义去重或上一轮失败；正常结束后停止，保留上一轮成功证据，真人听感仍PENDING。
