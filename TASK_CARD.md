## 2026-10-15 收尾提速与下一站内容生产（最新安排）

用户最新要求：“尽快做完，然后进入到之后的离线知识包制作去。”当前仍先完成 **V0.5.0 / Issue #27 / Draft PR #28**，不跳过事实准确、工程与真机安全边界，但**禁止继续无必要的论文追索、第三轮改稿或新架构设计**。

**Codex 立即执行的剩余工作**（沿用本任务原授权，不需新建 Issue/Phase/Gate/Agent/Schema）：

1. **补足史实依据即止**。用户确认的 v2.1 双篇正文与 SHA256 固定，阅读 [PR #28 现有证据评论](https://github.com/shimao1115/KITT/pull/28#issuecomment-6187551774)。对《临江仙》卷首来源，新获可直接访问的人民网文献：[《经典咏流传》传统文化进行时](https://media.people.com.cn/n1/2018/0320/c40606-29877094.html)（明确记其原属《廿一史弹词》说秦汉、后入毛氏评本）及[《军师联盟》不合史实？](https://culture.people.com.cn/n1/2017/0720/c172318-29416428.html)（明确原小说不带该词、毛本增加）。亲自打开并核对原文、登记可回读链接与定位；赵晓晖 PDF 无法直接阅读就保留 PENDING/不可读，不要为了单一 PDF 阻断可由独立可信资料支持的既有准确叙述，也不要伪称 PDF 已读。其余已查证依据只补真实缺口，切勿重新启动全面文献研究。
2. **依法完成已有正式审批流程**。用户“确认定稿”只完成 FINAL TEXT / 用户文字审阅，书面审核已记 PR；Fact 独立复核需有真实可回读依据并明确审核主体与范围。没有证据绝不假填；对程序若把真人“听感”硬设成**首次候选 APK 构建的先决条件**，先审查是否形成循环：允许在用户文字认可和事实可靠情况下生成**仅供验收、不对外发布**的候选，再由用户真机耳听，不能为了绕过门禁把未试听记为 PASS。必要时在现有审批语义里区分“文字/事实可构建候选”和“真机耳听可正式发布”，**只做最小调整，不新增审批体系**。若仍有关键未满足项，准确列明并 STOP。
3. **回归＋候选**：审核允许后跑真实内容门禁、Debug/Release 全量、六组模拟、assemble/lint、原证书与哈希及升级兼容检查；以最新执行日志给出数字，不拿旧915项当新证据。工程 PASS 才可生成 **code17 同签名 runtimecheck 验收候选**，有无 GPS 都具备既定的独立试听方案。输出 APK 的真实 SHA256/字节及安装说明，不调用 adb 安装。
4. **严格 STOP**：不安装/卸载/清空手机、不改变原有账号设置、不合并 PR #28、不关闭 #27、不开发 V0.5.1 UI/下载器。提交代码和证据后停下，供用户分别批准装机与合并；#23/#24 外场 PENDING 不擅自关闭。

**下一个明确的产品优先级（当前只规划、不提前施工）**：验收 V0.5.0 后，优先**批量制作真实沿途离线知识内容**，先复用现有 Fact/Narrative/来源/审核/本地 TTS 黄金路径；以成都→绵阳既有模拟走廊为范围，首个新样板优先广汉/三星堆（历史文化）并选一项非人物内容（如德阳工业/绵竹年画）验证跨题材复用。按区/镇/乡组织讲述，不把地区关联误说成经过景点门口。先做可审查的高质量文字，不开新知识包平台；等两个以上地区内容真实完成再以实际包大小和发行需要决定 GitHub Releases、独立 KITT-PACKS 及手机下载/导入的最小方案。

---

## 2026-10-15 用户已确认 v2.1 双篇最终定稿｜下一步仅补史实复核与工程验收

**当前唯一有效的新授权事实**：用户看完 `db4d0da` 两篇 v2.1 最终全文后明确回复 **“确认定稿”**。可追溯记录：[PR #28 用户文字定稿确认](https://github.com/shimao1115/KITT/pull/28#issuecomment-6187491143)。这是真实的**两篇最终文字确认（EDITORIAL / FINAL TEXT APPROVED）**，不再启动第三轮文字改写；不是独立史实终审、真人 TTS 已听通过、代码合并或手机安装许可。下面“等待最终文字确认”等描述均已成为历史，仅本段覆盖。

- 主篇 `yangshen-main`：SHA256 `f9b96bb89e8da63a354148542d56e4c420db8923b363380a693281c284ebceb4`。
- 延伸 `yangshen-followup-dianzai`：SHA256 `559ff2267b73f4e64480bb21e2c114bc4cef3979db68895acfe79daea56e329f`。
- 当前定稿版本：Narrative `2026.10.15-draft2.1`，Fact `2026.10.15-review2`；两篇故事正文与既有 Claim 引用不得为凑时长随意变更。

**下一步最小施工目标**（继承 Issue #27 已授权范围）：只对必要但仍 PENDING 的关键史实/来源做独立可追溯复核，重点是赵晓晖论文原文支持《临江仙》进入毛氏《三国演义》评本卷首的论据；不获取到来源或不能证明时如实 BLOCKED，不代签已读影印件或擅自更改讲述。事实核验达到要求后再按现有批准字段/审核脚本处理事实审批，严禁把“文字定稿”直接填成 `fact_gate=passed` 或 `visitor_gate=passed`。必要的正式可播审批缺失前继续 `review_status=draft`、两个 gate PENDING、`approval_ref` 空、`approvals.json` 空。不要设计额外审批系统。

**下一工程验收**：审批证据足够后，同步正式包和正文指纹，实际执行内容门禁、Debug/Release 全量、六组既有模拟、assemble/lint、原签名验证；全部通过再做 code17 同签名 runtimecheck 候选并交用户决定是否安装。不要把旧915项作为本轮重跑。**禁止安装/卸载/清除手机、合并 PR #28、关闭 Issue #27、开始 V0.5.1/其他功能**；#23/#24 外场独立 PENDING。当前仍没有 code17 runtimecheck 候选 APK。

---

# CURRENT TASK CARD — KITT V0.5.0 / Issue #27 杨慎完整离线故事黄金路径

## 2026-10-15 最新用户反馈：认可 v2 两篇故事的**内容方向与整体讲法**，仅两句润色后进行最终文本确认

**用户原话“认可”明确回应的问题是“是否认可这两篇故事的内容方向和整体讲法”。** 这构成对**第二稿整体叙事路线与车载风格的编辑认可**，**不是**对尚未呈现的微调最终版逐字批准，也不是完整事实终审、真实耳听通过、发布批准或安装许可。请不要把它误记为“用户已亲耳听完/两篇正式 approved”。

### 本次唯一施工：最小文字收尾 + 最终审稿交付

1. 主篇在“大礼议”段微调“认另一位先皇为父”这句，使其白话准确、适合车内听：嘉靖皇帝坚持尊自己的生父，大臣认为继承皇位必须依照相应礼制处理名分。**避免将复杂争议说成皇帝纯粹认不认父亲的私事，不引入冗长术语**。其余段落保持 v2 主线和自然长度。
2. 主篇《临江仙》段把“毛宗岗父子评刻《三国演义》”压缩为“后来这首词又被放到了《三国演义》卷首”之类的自然表达；在后台 Source/Claim 中保留毛氏评本、非最早文本、成文时地未证实等史料边界，**不得误说《三国演义》原版一开始就有**。
3. 延伸《杨慎书里，赶路人的一首歌》**整篇保留**，无需增加九男/十男、六诏等考据，不要为了打磨而扩成讲座。只有明确事实错误才作最小纠正。
4. 依实际文字变化，同步 Markdown→`content/yangshen/narratives.json` 正文/哈希/paragraph_claims、来源追踪、manifest/asset 必要一致性、`docs/YANGSHEN_CONTENT_REVIEW.md` 和有效的校验报告。准确记录本轮真正运行的测试；不把上一轮915项回归说成本轮重新执行。**不要为了两句编辑重新设计 Android 程序**。
5. 生成清晰的“**v2.1 最终候选全文**”和前后两句对照、来源及最终正文 SHA256，回复给用户做**最后一次逐字文本确认**。保留先前口语与车载体验准则：首次约1.5–3分钟但不凑字数、延伸约1分钟，无显性审校口吻。
6. **审批 Gate**：`review_status=draft`、`fact_gate=pending_human`、`visitor_gate=pending`、`approvals.json` 空表持续不变，直到用户看过 v2.1 最终正文且明确确认并且真实事实审核可追溯。**没有真实真人耳听前不要登记“已试听通过”**；可在以后正式文本审批并生成 APK 候选后，把真机耳听留为独立 PENDING。不可将此次“认可内容方向”自动转换成批准哈希的记录。
7. 原分支 [Draft PR #28](https://github.com/shimao1115/KITT/pull/28) 可提交两句修订及必要内容校验，更新 HANDOFF，完成即 STOP 等用户最终确认。**不要生成 code17 runtimecheck APK、不要安装手机、不要合并 PR、不要关闭 Issue #27 或开始新版本**。#23/#24 外场待验不变。

## 2026-10-15 用户最新编辑指令（高于下方早期3–5分钟目标）

**车上听，不是坐着读。用户要求：人物、事件、物件讲清楚，抓核心、不长篇累牍、不用生硬或需要高度集中注意力的语言，深入浅出、娓娓道来。** 最新跨主题准则已写入 [内容标准 V2.4 §6“驾驶途中听感”](docs/KITT_KNOWLEDGE_STANDARD.md)。这是用户对下一稿的明确创作方向，**不是对当前 v1 稿件的批准，也不意味着可以修改审批状态**。

本轮下一小步**只修订杨慎主篇与延伸篇 v2，以及必要的 paragraph/Claim/Source/哈希追踪**。主篇把“他是谁→为何从状元走向远谪→在云南如何继续读书著述→何以与新都及词作流传有关”讲成一个自然、精炼、听得明白的故事；无需堆砌所有年份、官职和《明史》考订句式。真人感知的一般参考长度约1.5–3分钟，延伸1–2分钟，**不作为程序硬阈值或灌水长度**。开篇快速给听众背景与悬念，转折有真实可核经历，一两个关键画面即可；结尾点题而不讲成课堂总结。不要为造戏剧性编人物心理、对白或江边作词场景。

**上一轮 PR 评论对“《滇载记》九男/十男异文”建议已由用户优先级澄清所修正**：它可以留在 Fact/Review 研究材料供准确性核查，但**不是本轮必须新增入播的情节**，也不应为了凑趣讲文献校勘。现有《滇载记》延伸篇过度偏向“怎样读古书”，请改以**杨慎在云南的人生/书写与当地人文的关系**为核心；如果《滇载记》难以讲得轻松有趣，可以提出换成更适合车内听的延伸选题，而不是死守原稿。该调整应符合真实证据，不能自动假定通过。播报应让用户分神数秒后仍能接上，而不是需要不断追听注释。

**内容改稿后**保留 `review_status=draft`、`fact_gate=pending_human`、`visitor_gate=pending`、`approvals.json` 空表；不伪造来源、真人已读或试听。只按改动实际执行内容校验；不重复宣称已做手机试听/安装。提交 v2 全文与逐段事实/文风修改说明，留在 Draft PR #28 供用户审稿。**尚未授权 code17 手机安装、PR 合并或正式发布**，内容审稿完成前原代码17 runtimecheck 构建应继续被 BLOCKED。STOP 等用户审稿。

**本步执行结果 / v2 REVIEW-STOP**：主篇580字符、延伸297字符，正文及逐段依据见 [内容审校](docs/YANGSHEN_CONTENT_REVIEW.md)。补核首次赴戍病困与《滇载记》商旅歌谣两条Claim，未把九男/十男异文放进故事。两篇仍draft、审批空、正式故事0；Python4项及StoryPacks Debug专项16项通过，内容发布门禁仍BLOCKED。[本轮记录](docs/evidence/v050-v2-content-validation.json)与下方v1的915项工程历史分开。未构建APK、未操作手机、不合并PR，等待第二稿审阅。

> **2026-10-15 V0.5.0当前交付 / REVIEW-STOP**：杨慎主篇＋延伸全文与9来源/14核验条目已提交审阅；两篇仍draft、正式故事0。工程冻结回归 Debug 464 + Release 451 = 915 项零失败，Python4项、构建/lint及原签名回归通过；详见 [HANDOFF](HANDOFF.md)、[Draft PR](https://github.com/shimao1115/KITT/pull/28)。**内容人工/独立终审和游客试听PENDING，runtimecheck code17被门禁阻断、没有候选APK；手机未操作，不合并，不开下一版本。** 下方“目前仅任务卡/尚未实施”等为施工前记录，由本段覆盖；不能据旧授权重复安装code16。

**2026-10-15 用户明确要求“下一阶段”**：现在授权**正式开始本版本的内容研究、事实核验、最小双包架构、Android 消费者/测试与同签名 APK 工程候选开发**。本任务为唯一活跃施工单：[Issue #27](https://github.com/shimao1115/KITT/issues/27)，分支 `feat/v050-xindu-yangshen-story-packs`，基线为已合并 V0.4.2/code16 的 `main`。**当前只是分支和任务卡已准备，不能称 V0.5.0 已实施、史料已查证或设备已验收。**

## 首先继承最新事实、不得返工上一版

1. 读本卡、[AGENTS.md](AGENTS.md)、[HANDOFF.md](HANDOFF.md)、[正式知识内容标准 V2.4](docs/KITT_KNOWLEDGE_STANDARD.md)、[知识工厂 V2.4](docs/KITT_KNOWLEDGE_FACTORY.md)、[未来双包蓝图](docs/FUTURE_OFFLINE_KNOWLEDGE_PACKS.md)、[离线包管理标准](docs/KITT_OFFLINE_PACK_MANAGEMENT.md)、[本地优先路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)，然后读现有 `OfflinePack.kt`/`DirectorLoop.kt`/`KittRuntime.kt`/`Journey.kt`/`AndroidVoice.kt` 与 `app/src/main/assets/offline/xindu-*`、[原离线来源登记](docs/OFFLINE_XINDU_SOURCES.md) 及相关测试。
2. V0.4.2/code16 真实手机 `com.kitt.reader.runtimecheck` **已安全原位升级**；Debug446 + Release433 =879 项工程 PASS；[#23](https://github.com/shimao1115/KITT/issues/23)（旧评分实机样本、真人听感）与 [#24](https://github.com/shimao1115/KITT/issues/24)（无网新鲜位置下地方讲述/重播/在线失败后恢复等）**独立 OPEN/PENDING**。不可更新为“已完整验收”。别重装 code16、别动密钥或账号、别排查 ChatGPT 连接作为本版前置。
3. 以**最短原生黄金路径**施工：真实有效 GPS/NETWORK 新都区→已装的审核过的杨慎 Fact + Narrative 互相校验→本地选择可播主故事→设备真正离线中文 TTS 播**完整正文**。有网络/无网络/云不可达三种情况正常自动讲述均不经过在线 Director/Research；不得假定 Fake 模拟等于真机。

## 本版严格限定的内容：杨慎（杨升庵）一个对象

**先核验史料、再写成品、再上设备，不反过来让漂亮故事“倒逼”虚构事实。** 以下是研究提纲，不是预先核实的定论：

- 人物与新都、桂湖/升庵祠的实际联系；字用修、号升庵、1511 年科举状元及翰林身份。
- 嘉靖朝“大礼议”背后的冲突是什么、杨慎参与到什么程度、结局/谪戍云南具体时序；拒绝把复杂朝争简化成单一口号。
- 在云南的经历、文学与学术工作的可核实成就，不止念官职和著作目录。
- 《临江仙·滚滚长江东逝水》作者/版本、与其他作品和后世《三国演义》相关改编的关系，**尤其核查历史出处、年代及作品归属，不将电视剧/小说中的使用等同于原作历史诞生情景**。
- 梳理旧中央纪委页面 `source_id=yang` 已记录的**卒年矛盾**（见 `docs/OFFLINE_XINDU_SOURCES.md`），不能直接继承矛盾年份。其他有争议的游记/传说/逸闻须归属来源，证据不足就不要作为肯定句播放。

**应交付的内容**：

A. 一个可以独立审查的 **Fact Pack**：稳定 Area/Entity/Claim/Source ID、`source_id+locator` 真实可回读、访问/出版时间、版本/修订指纹、有效性/证据状态（`verified|contested|lead|rejected`）、适用范围与版权/许可；从旧6对象/22条事实增补，不得丢旧知识或把旧 `TopicFamily` 强行映射成六类 Story 分类。

B. 一个独立版本的 **Narrative Pack**：至少**一篇经过事实和游客双 Gate 审核的杨慎主故事**（目标约3–5分钟、本地 TTS 可完整读完；有背景、人生转折、远谪/创作、作品与新都联系、收束，非年表）和**至少一篇有不同新事实的延伸篇**（如远谪/学问、作品流传，避免重复）；每篇稳定 `narrative_id`、`story_type=main|followup`、`primary_category=HISTORY` 或通过审核更合适的六类之一、辅分类仅在实际内容适用时标记、`supported_claim_refs` 精确绑定所用的 Claim ID + 认可修订、`review_status`、预计时长、县域关联≠现场可见。

C. **审稿产物**：Source Registry、Claim 核验表/冲突与不可读来源说明、主要叙事句子与 Claim 追踪、普通游客听稿反馈/未完成项、版权核查和稳定包 manifest。来源必须真的打开或拿到合法的具体书目/卷页再标已读；只搜索到标题/二次摘要则登记为 `lead/unverified`，**不能编造页码、DOI、访问成功、人体试听结果或作者归属**。模型换个名字不代表独立来源。

**关键冻结 Gate**：`draft/lead/rejected` 或无必要证据/许可/人工认可的 Narrative **不能伪标 approved 或以“正式可播”方式入 APK**。Codex 可制作真实草稿及供用户核阅的文本，不能代替用户签署“已读/亲耳听完”的最终审核。如没有通过内容门禁，应独立交付审校草稿/阻断信息，**阶段 STOP，不强行做成假合格发行**。实际审稿来源及期限诚实记录。

## Android 最小实现边界

1. **两包独立**：设计最小可审计 UTF-8 JSON（或 JSONL）来源稿、manifest、schema/version/hash/byte length/Claim 精确依赖与审稿状态的校验；在本版先**跟 APK 内置**，不建立计划中的 `KITT-PACKS` 仓库，也不做 GitHub Releases、全国包、在线下载器/自动更新或泛化复杂平台。既有 `xindu-facts.json` / polygon 的加载规则与保守位置门禁不回退。推荐为正式事实来源档案与 Android 内置适配提供可复现转换，不把受版权限制的原文直接上传公共 GitHub。
2. **本地选择器最小化**：真实物理旅程在新都区且主 Narrative 通过内容/依赖审查时，优先输出一次**完整杨慎主故事文字**，走既有 `Journey/Voice` 播放完成/取消/跳过/静音/冷却/已播记忆。首次自然开口不能只讲杨慎身份和官职，把人生转折留待用户追问；`再讲一点` 候选同主题**未讲过的延伸篇**，`再说一遍` 回放已完成主篇正文，不把未播/已中断当完成；正常自动不能在同旅程连续重复主篇，也不能播完长故事立刻重复旧两条同角度事实。对其余5个旧对象保持有来源的短事实降级，**不要扩建第二完整主故事**。
3. **失败关门**：Source/Claim 缺失、版本指纹不符、审核状态非 approved、包 SHA 失败或内容损坏，都不可播该“完整故事”；可明确降级原有 verified 事实级介绍或安静，不能静默改为在线 Director 编一篇。不同版本 Fact/Narrative 协同必须只对该故事引用的 claim 失效，不能因无关 claim 修改让整个地区全体故事过期。
4. **独立可测试的真人试听**：因为用户办公室在无可靠 GPS 时长期不能完成真机完整自动链路，可提供**显式、手动、不冒充真实位置**的开发/验收用本地故事试听（例如专用调试/验收入口或安全的 instrumentation 入口，择最少代码）；可读**同一完整成品文字**，离线 TTS 真正读完，供用户初步验稿。它**不能更改可信 GPS 状态/讲述记忆或默认启动流程**。这一点不是未来 V0.5.1 的正式“探索地点”跨地区搜索 UI。
5. 讲述文本/知识包**只存文字**、不用预生成音频/SSML 厂商绑定；设备离线中文 TTS，不从云端获取配音。长篇文本分段若必要，保持回调生命周期与打断/skip 语义正确，不出现前半段播完就记“全部完成”。可用有界测试验证正文未丢、未提前成功。

## 测试、交付、STOP

- 先 Review `main` 最新状态和真实内容审核结果，再选择合法递增版本，**建议 V0.5.0/versionCode17**，不改 `applicationId`（标准包是 `com.kitt.reader`、原手机账号使用 `com.kitt.reader.runtimecheck`）。原签名证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。
- 增加 Source/Claim/Narrative 依赖、哈希/schema/篡改、审稿状态、六类枚举、已播/重播/延伸/打断、缺包回退、无可信定位安静、不联网且无云账号/开启联网但模型不可达均 **0 自动 Director/Research** 的真实确定性单测/Robolectric。仍跑旧 Debug/Release 全量、六组成都→绵阳模拟、assemble/lint、2接受+6拒绝原证书验证及 `scripts/build-runtime-smoke.ps1 -Offline`；输出具体运行而非 UP-TO-DATE 假计数。
- 通过内容 Gate + 工程 Gate **两者**后生成原签名 `com.kitt.reader.runtimecheck` code17 APK 工程候选、字节/SHA256、来源和审稿报告、文本可读性/独立离线试听方案、最短真机有 GPS/无 GPS 两种验收。用户**尚未授权手机 code17 安装或 PR 合并**，不 `adb install`、`adb uninstall`、`pm clear`、`-d`、不下载/上传任何私钥/token/私人纪要；完成提交推送后先 STOP。审核/工程不达标的事项以 PENDING/BLOCKED 如实报告，**不要伪称新版本工程全部 PASS 或真人已听过**。
- 仅本版 Issue #27，PR 创建为 **Draft**；更新 `HANDOFF.md`、`docs/ROADMAP_OFFLINE_FIRST_NEXT.md`、来源审稿文件、脱敏日志证据。禁止顺便开发 V0.5.1 探索地点、V0.5.2 六类偏好、V0.6.0 下载发布、地图重构、云连接修复。**完成后 STOP 等待用户验收。**

---

## 历史交接（下面归档卡保留以防误读，不能再执行）
# ARCHIVED TASK CARD — V0.4.2/code16 已阶段性合并（2026-10-15）

**现行状态：STOP / 本轮任务完成归档。** 用户明确同意 V0.4.2 阶段性验收通过并合并 `main`，详见 [已合并 PR #26](https://github.com/shimao1115/KITT/pull/26)，merge commit [`9351f17`](https://github.com/shimao1115/KITT/commit/9351f17f1c4d03143c57e04e1b74311a858528a6)。Debug446/Release433 合计879项工程 PASS；真实手机 `com.kitt.reader.runtimecheck` 已是 code16，删除评分、自动纪要、原签名升级、设置保留、直接返回 PASS；联网 NETWORK 本地自动讲述零云调用是设备级记录，真人耳听未核。

**剩余待验以后补做**：[Issue #23](https://github.com/shimao1115/KITT/issues/23) OPEN，真实旧评分字段实机样本为0、真人耳听等继续 PENDING；[Issue #24](https://github.com/shimao1115/KITT/issues/24) OPEN，断网新鲜位置下地方讲述/重播/云失败后恢复另验。历史20份纪要上限不变，新增两趟测试按既有规则淘汰最旧2份，未保留为永久档案。

**不再是当前施工任务**：下方所有手机安装授权、PR Draft 与禁止合并文字已因用户后续确认而成为历史，绝不能据其重复执行安装/升级/清数据。新版本（候选 V0.5.0）应新建单一授权任务卡后再动工；现在没有开启下一版本。

---

# CURRENT TASK CARD — KITT V0.4.2 / Issue #23 结束旅程页面精简

> **2026-10-15 当前执行结果：本轮唯一升级授权已使用，单次code15→16原位安装成功；真机有界验收完成，STOP。** 无评分结束页、自动保存/一次返回、账号设置与联网真实NETWORK本地自动0云分项PASS；真实旧评分样本0份/真人耳听及#24外场仍PENDING。详见 [HANDOFF](HANDOFF.md) 与 [手机证据](docs/evidence/v042-phone-acceptance.json)。**手机已是code16，不得根据下方历史授权重复安装；PR26保持Draft、Issue23保持OPEN、不合并、不启动V0.5.0。**

## 2026-10-14 用户已明确批准 code16 原位升级与真机验收 — 当前唯一活动步骤

用户在 V0.4.2/code16 工程已完成、[Draft PR #26](https://github.com/shimao1115/KITT/pull/26) 等待真机确认后，明确答复**“进入真机验收”**。承接上一轮已明确提出的“允许 Codex 将 V0.4.1/code15 原位升级到 V0.4.2/code16 并检查”的询问，该回复构成**本轮手机原位安装和有界验收的明确授权**，**不用再重复请求是否允许同签名安装**。但 USB 授权和 vivo 系统安装确认仍需用户在手机上操作；不允许绕过设备安全提示。

**当前已知实测手机是 `com.kitt.reader.runtimecheck` V0.4.1/code15，来自前轮记录；此次操作必须重新读取实际手机状态，不能照抄历史。**

### Codex 本轮唯一任务

1. **先同步当前分支，不重新开发已完成功能**：在办公室 Windows `H:\CODEX\KITT` 检查 `git status`，若有未提交改动不要覆盖；`git fetch origin` 后检出 `feat/v042-simplify-end-summary` 并快进到远端最新，先阅读本卡、`HANDOFF.md`、`docs/ACCEPTANCE_END_SUMMARY.md` 和工程证据；现有候选生产版 code16 / 工程 Debug446＋Release433＝879 已实际完成，**不用为了验收盲目重跑或重新打包**。
2. **安装前安全门禁**：核验磁盘文件 `artifacts\kitt-v0.4.2-runtimecheck.apk` 真实存在、文件大小 **74,369,322 bytes**、SHA256 **`36d39438c8efb735cd8e67bee1a250c8e789a25061a9425cb8d9ae7bebe0ccb3`**、applicationId **`com.kitt.reader.runtimecheck`**、`versionName=0.4.2`/`versionCode=16`、唯一证书 SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**，继续使用仓库既有 `scripts/check-kitt-signing.ps1`。读取当前 ADB 设备的实际包名、versionCode/签名及连接授权状态，确认 **code15 且同证书**，否则安全 STOP、报告，不要重复安装、降级或生成新密钥。
3. **条件全部满足才做单次原位升级**：`adb install -r artifacts\kitt-v0.4.2-runtimecheck.apk`；绝不 `adb uninstall`、`pm clear`、`-d` 降级、改包名或安装标准 `com.kitt.reader`；不读出/上传账号密钥与私密历史资料。系统若要求确认，在手机上等用户手动批准；无法通过则停止，不绕过安全防护。
4. **升级后回验**：回读 code16/包名/原签名（可安全验证装机 APK SHA256）、首次安装时间/数据空间与升级前对照、可见账号/模型偏好/语速/离线中文声音等设置保留；不要编辑或重登账号制造网络请求。
5. **最短真实 UI 黄金路径**：普通“开始沿途”→“结束旅程”后出现**无评分、无评语、无保存按钮**的简洁纪要，显示时长/跳过次数/已讲主题（无主题时“这一程以安静相伴”）；目的地未提供时不出现“未询问/未提供”等技术状态；“回到出发页”一次返回且前台服务停止。核实纪要在结束时自动保存，返回不丢失。用**只读、必要且脱敏**的方法检查含旧 `ratings`/`feedback` 的历史旅程仍可被读取、内容未被 UI 操作改写；旧数据若无法安全取得则记 PENDING，**不要构造假历史实机证据**。
6. **保护上轮已验能力**：有新鲜可信真实新都 GPS/NETWORK 时再检查自动本地故事及自动 Director/Research 增量 0、不主动询问目的地；没有可信位置就标本项 PENDING，不注入假位置、不放宽定位门槛、不把断网安全安静当断网故事成功。Issue #24 关于断网新鲜位置、重播/有限问答和云失败后恢复等外场事项继续 OPEN，**本任务不得擅自关闭它**。竖横屏 UI 如设备支持可验证；不必为 UI 项目强行室外路测。
7. **交接与 STOP**：把安装前后实际 code/哈希、原签名、真实 UI 与纪要保留情况、已核设置、PASS/PENDING/失败和脱敏证据写入 `HANDOFF.md`、`docs/ACCEPTANCE_END_SUMMARY.md`、新的手机验收 evidence、Issue #23 和 PR #26；提交并推送此分支，工作树清洁后 STOP。**保持 PR #26 为 Draft、不合并、不关闭 Issue #23、不启动 V0.5.0**。此授权仅覆盖这一次 code16 安全原位升级和真实验收。

> 本段覆盖下方“等待升级授权/不能手机安装”等**工程交付时的历史 STOP 文案**；用户尚未实际通过本轮 Codex 对手机安装，**本任务卡更新不是手机安装成功的证据**。

工程候选已推送 [Draft PR #26](https://github.com/shimao1115/KITT/pull/26)，保持Draft，等待真机升级确认。

**2026-10-14 当前工程结果：V0.4.2/code16 已完成，STOP等待用户明确批准真机升级。** 独立分支 `feat/v042-simplify-end-summary`，唯一目标 [#23 删除五项评分](https://github.com/shimao1115/KITT/issues/23)，实现 `d64adf8`；评分/评语/保存接口已移除，自动纪要/旧JSON/直接返回保留。最终Debug446/Release433共879项、failure/error/skipped0，六组模拟、构建/lint、原签名及runtimecheck APK PASS。首轮Debug1个旧超时测试调度失败已修订并两Variant完整重跑。见 [HANDOFF](HANDOFF.md)、[证据](docs/evidence/v042-verification.json)、[真机步骤](docs/ACCEPTANCE_END_SUMMARY.md)。本轮未操作手机/发送真实云请求；code16真人全部PENDING，Issue23保持OPEN，Draft PR不合并，#24外场PENDING不改写。**不据下方开发授权重新施工或自动安装；工程验收已结束，等待新指令。**

> **STOP 边界**：本轮只做 UI/旧评分接口的必要清理，完成工程构建与签名产物后 STOP 请求用户确认。**本句“继续”仅授权开发、测试及准备验收 APK，不视为已经授权对手机执行 `adb install -r`**。用户当前手机上是已验收的 `com.kitt.reader.runtimecheck` V0.4.1/code15；不可卸载、清数据、换签名、擅自替换标准包、合并 PR、关闭 Issue 或进入 V0.5.0。V0.4.1 [Issue #24](https://github.com/shimao1115/KITT/issues/24) 真实断网和恢复外场 PENDING **继续 OPEN**，不得改写。

## 开发起点与最小需求

先读本卡、`AGENTS.md`、`HANDOFF.md`、[路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md) 和 [Issue #23](https://github.com/shimao1115/KITT/issues/23)；以分支当前实际代码为准。最小黄金路径：**点击“结束旅程” → `KittRuntime.end()` 自动将轻量 `TripSummary` 写入 `TripStore.finish()` → 展示简洁 `EndScreen` → 点击“回到出发页”**。现有完整链路无需任何评分按钮即可保存。不得因此修改写入时序、旅程恢复与持久化语义。

1. 在 `app/src/main/java/com/kitt/reader/EndScreen.kt` 删除五项 1—5 分问卷及其默认全 3 分状态、`想说的话（可选）` 输入框、`保存评分` 按钮、保存成功/失败提示及专用状态与交互。**不要把五项替换成六类满意度评分**。
2. 保留 **“这一程，读过的山河”**、时长、跳过次数、已经讲过的主题、无主题时的安静文案及 **“回到出发页”**。已有目的地**若由用户明确提供则可显示**；`未询问`、`未提供`、`等待回答` 这类技术状态不应作为旅程目的地展示；不从 GPS 推断或伪造目的地。界面在手机竖屏、小横屏/平板上不需额外复杂滚动。
3. 让 `EndScreen` 无需依赖评分 `TripStore` 实例（若删除 `store` 参数则同步更改 `MainActivity.kt` 和 Compose 测试），不影响 `KittRuntime.end()` 的 `store.finish()` 自动保存。**本次不增加保存纪要按钮或数据库迁移**。
4. 可以在全仓确认调用只剩旧测试后清理 `TripStore.feedback()` 死接口，**但严禁擦除、迁移或改写用户本机已有 `trip-*.json`**；历史文件可能有 `ratings`/`feedback` 可选字段，`TripStore.latest()`、恢复/读取必须继续忽略额外键并正确返回 `TripSummary`。优先小改，必要的遗留接口保留不影响最终 UX。
5. **不动主核心功能**：离线优先自动讲述、联网零自动 Director/Research 调用、目的地不主动追问、GPS 可信门槛、Android 中文离线 TTS、ASR、主动在线回答、静音/打断/后台服务/已播记忆，及六类偏好/双层知识包/离线更新/探索地点均不属于本版。

## 必须做的最小回归

- `DrivingUiTest.kt` 中 `EndUiTest.endScreenSavesRatingAndReturns` 是**旧评分设计的正向断言**；替换为**简洁结束页显示 + 评分元素确实不存在 + 返回生效**，并验证已有 `TripSummary` 内容仍完整且保存自动完成。验证显式目的地与 `未询问` 时的不同展示、无已讲主题的安静文案。
- `PersistenceTest.kt` 中 `summaryRatingRetentionAndCorruptRecovery` 正在直接调用旧 `store.feedback()`。**绝不能删掉这条历史兼容性验证**：改为手工构造/读取**带遗留 `ratings`/`feedback` JSON**，确认旧记录依然可被读取且不会因新版 EndScreen 自动改写/抹去；单独验证现在不依赖评分也会 `store.finish(summary)` 并保存纪要。仍验证损坏 `current.json` 恢复安全。
- 其余导航、竖横屏、Journey-end、旧设置、自动播报本地优先、安全签名和原 V0.4.1 869 项测试都应全套回归。不能删除失败测试、改生产定位/冷却阈值换 PASS；如果新增测试，报告新旧真实计数。
- `app/build.gradle.kts` 计划递增为 **V0.4.2/versionCode16**，先核对当前实际 `main`/手机 code15；如版本已更高，先报告并合法递增，不可覆盖更高版本或复用旧 code。
- 办公室 JDK17 / Android SDK35：运行 `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Offline`（Debug＋Release 单测、assemble、lint、签名）与 `powershell -ExecutionPolicy Bypass -File scripts/build-runtime-smoke.ps1 -Offline`、`scripts/test-kitt-signing.ps1` 等现成原签名核验脚本。完成既有六组模拟回归。不能把 UP-TO-DATE 的旧单测充当当轮新执行测试。
- **唯一能覆盖现有手机账号数据空间的候选是**同原证书 `com.kitt.reader.runtimecheck` code16 验收 APK，非普通 `com.kitt.reader` 标准包。原证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；校验候选 APK 的包名、versionCode、唯一证书、SHA256、字节数并存脱敏结果。不能生成替换密钥、导出或提交私钥/账号凭据。
- 如果工程绿灯：构建 Android instrumentation APK（若现有工具链适用），补一页最短真人验收：原位升级后“结束旅程”不出现评分、正常返回、自动纪要/旧记录/账号设置仍在；V0.4.1 联网本地讲述零云调用不退步。**这里只需工程候选，先不要 ADB 安装手机**。

## Git / 汇报 / STOP

Codex 可以基于此分支自主施工、运行测试、修复回归、提交推送，并在有代码更改后开启 **Draft PR → main**，更新 [Issue #23](https://github.com/shimao1115/KITT/issues/23)、`HANDOFF.md`、相关简要验收记录以及路线图，不改写 V0.4.1 外场 PENDING。若本地已有未提交文件，先保存/解释而不是强行覆盖；同步远端 `main` 时注意当前分支已包含合并后的 code15 基线。

报告精确 **版本/commit、Debug/Release 新实际测试计数与 skipped/errors、六组模拟、build/lint、原签名检查、APK 包名/大小/SHA256、待真人项目**。然后 STOP。**需要用户后续明确批准才对手机执行安装，之后真实验收通过再讨论合并**。

---

## 已归档的上轮施工上下文（勿重复执行）

# ARCHIVED TASK CARD — V0.4.1/code15 阶段性交付已合并（2026-10-14）

**状态：STOP / 已阶段性接受。** 用户确认“剩余外场测试以后补做”；[PR #25](https://github.com/shimao1115/KITT/pull/25) 已合并进 `main`，merge [`65ec2726`](https://github.com/shimao1115/KITT/commit/65ec2726e0fe173ff431db8ecfebade7c645d179)。工程 441 Debug＋428 Release = 869，零失败；真机 code15 原位升级、联网新都可信 NETWORK 自动本地朗读零云调用、不主动问目的地及中文耳听通过。**Issue #24 继续 OPEN 以追踪真实断网新鲜定位下地方讲述、重播/本地问答及云失败后的恢复等外场 PENDING。** 不以本轮测试/合并取代外场验收。

**当前手机已安装 `com.kitt.reader.runtimecheck` code15，不能重装/卸载/清数据。** 下文原先“等待批准/禁止合并”是本轮施工前及验收中的历史约束，合并行为已按用户后续明确授权执行。新版本必须重新授权、另立单一任务；此卡不再是活跃施工指令。下一个候选才是 [#23 去五项评分](https://github.com/shimao1115/KITT/issues/23)，**尚未启动**。

# CURRENT TASK CARD — KITT V0.4.1 / Issue #24 本地原生讲述热修

**2026-10-14最新执行状态：code14→15原签名真机原位升级成功，本轮验收结束，STOP等待用户决定。** 联网真实NETWORK→新都本地讲述、0自动云调用、不问目的地及首次真人耳听PASS；120秒联网109秒可信NETWORK/自动0云、主动问题单次FAILED已短句结束；断网120秒没有新鲜物理位置，完整地方链PENDING但正确未知/安静/0云；独立断网中文试听、实际播报按钮打断、默认10分钟静音/提前退出、结束/账号设置保留分别PASS。手机最终出发页，服务停止，原Wi-Fi关/流量开已恢复。不卸载/清数据、不合并PR/关闭Issue或开始下一版本；**已是code15，不要重复安装**。详见 [HANDOFF](HANDOFF.md)、[真机证据](docs/evidence/v041-phone-acceptance.json)。工程Debug441/Release428等结果仍是安装前实际执行证据。

## 2026-10-14 下午：用户已明确批准真机原位升级与验收（当前生效）

用户刚刚明确答复**“允许”**：授权办公室 Codex **将原手机已安装的 `com.kitt.reader.runtimecheck` V0.4.0/code14 原位更新为 V0.4.1/code15，并完成 [docs/ACCEPTANCE_LOCAL_FIRST.md](docs/ACCEPTANCE_LOCAL_FIRST.md) 的真机验收**。此许可替代本卡上方“STOP 等待手机安装确认”的**安装与验收**部分；**不解除**不卸载、不清数据、不泄露凭据、不合并 Draft PR #25、不关闭 Issue #24、不开始 #23 或 V0.5.0 的其他 STOP 约束。

**以下授权已执行完成，保留作审计，不要据此重启安装或验收。** 本轮后续需等用户新指令。执行时的安全技术门禁如下：

1. 在办公室 Windows 仓库最新 `fix/offline-native-no-destination-question` 上确认工作树清洁、原 APK 路径确实存在：`artifacts/kitt-v0.4.1-runtimecheck.apk`（本机 `H:\CODEX\KITT\artifacts\kitt-v0.4.1-runtimecheck.apk`）。重新核查 SHA256 **`8beac9f79e4adcf8a31f95b742562ea393d996c337be3e1ee87cc8c2e58ce43f`**、长度 **74,369,318 字节**、包名 **`com.kitt.reader.runtimecheck`**、版本 **0.4.1/code15**、唯一原证书 SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**。用 `scripts/check-kitt-signing.ps1 -ApkPath artifacts\kitt-v0.4.1-runtimecheck.apk -ExpectedApplicationId com.kitt.reader.runtimecheck` 等现有检查；**不能使用 `com.kitt.reader` 标准包代替**。
2. 取得手机的**实际当前状态**：ADB 仅在设备授权、安全连接后查询；核对真实安装包名、现有 versionCode **应小于15（预期14）**、签名兼容、设备可通信；不凭 0.4.0 历史记录猜当前版本。若已是15或更高、设备/证书不匹配、APK 路径/哈希不符，**停止安装并报告**，不要覆盖、降级、重签或重建密钥。
3. 只有以上均满足才执行**单次** `adb install -r artifacts\kitt-v0.4.1-runtimecheck.apk`；若 vivo 弹出安装或 USB 授权，等用户在手机上亲自批准；失败时**不进行** `adb uninstall`、`pm clear`、降级、解锁/提取账号或任何绕过安装保护的操作。安装后独立回读实际包名/code15、必要时验证装机 APK SHA256、首次安装时间/用户设置不变，记录脱敏结果。
4. 真机验收严格分开：正常真实 GPS/NETWORK 新鲜且新都可信几何内时，**有移动网络/ChatGPT 不可达**和**双无线断网**分别确认无需先联网即可本地中文讲述、开始不问目的地、自动云 Director/Research **调用增量0**；另验本地有限问法/重播、skip、打断、安静、后台/恢复、在线主动问题失败后的本地恢复、账户及 TTS 设置保留。不要为验收破坏账户/代理配置或授权状态；不存原始 GPS 轨迹、凭据、聊天正文。
5. 若办公室得不到真实新鲜 GPS：不猜坐标、不注入模拟位置、不降低 15 秒/50 米与范围几何边界门槛。可验证安装、语音引擎、设置和 UI，但**真实位置→自动离线讲述及有网零云调用真人听感仍记 PENDING**，等待安全的室外/行车后验。
6. 用新**手机实测**更新 HANDOFF、Issue #24、PR #25 与脱敏证据（不能用旧 Robolectric 的 0 云计数顶替），完整列明 PASS、PENDING、BLOCKED，并推送提交。**完成验收后立即 STOP**；PR 仍 Draft，未经新授权不合并、不进入后续版本。

> **本任务替代本分支中的旧 V0.4.0 #20 任务卡；主干的 V0.4.0 历史工程交接仍保持原样。** 当前唯一可施工目标：[Issue #24](https://github.com/shimao1115/KITT/issues/24) 和 [Draft PR #25](https://github.com/shimao1115/KITT/pull/25)。请先读本卡、AGENTS.md、HANDOFF.md、docs/ROADMAP_OFFLINE_FIRST_NEXT.md、docs/KITT_KNOWLEDGE_STANDARD.md。旧 #20 的工程已交付，真实物理 GPS 等待验收部分仍 PENDING，不重新做或改写其结论。

## 本轮目标与严格范围

**联网也本地直接开口，不先等待 ChatGPT；“开始沿途”也不先问目的地。** 作为下一小版本 **V0.4.1 / versionCode 15** 的候选计划；如实际主干已经有更高版本号，先按当前代码校验再选用合法递增版本，不可降级/复用 versionCode。

1. 普通**真实物理定位**旅程：先判可信新鲜 GPS/NETWORK、当前已安装新都地区包与范围，再用本地 OfflineNarrator/后续 Story Selector 决定是否出声，Android **已安装、无需互联网的中文 TTS**直接播放。Wi-Fi、流量、代理、ChatGPT 连接或模型列表**均不是自动播报的启动条件**。即使网络可用也不调用在线 Director/Research/ChatGPT 的自动路径。当前本地故事较浅属于 V0.5.0 的下一任务，本轮不扩内容包。
2. 有可信物理位置但未覆盖本地地区知识：可以安静、如实标识“当地暂无资料”，**不能偷偷转入自动云端旁白**；未知/不新鲜 GPS 更不能猜地址。模拟模式仍保留既有 Fake Provider / 专用六组回归，不拿模拟替代真机。
3. **主动提问**：“这里有什么”“再讲一点”“再说一遍”在有本地资料时应直接从本地响应；主动问没有本地证据的开放式问题、图片或今天路况/天气时按现有在线可用条件走 AI/实际可信来源，无法在线则短句说明，但**不能阻断下次本地开口**。当前未实施“即时优先” UI，**不在此热修捆绑新增模式或六类设置**。
4. **取消目的地追问**：普通开始不主动问“今天准备去哪儿？”；如用户主动提“去绵阳”等，继续把目的地作为可选背景；“添加路线参考图（可选）”不删除。保留真正需要答复的其他澄清问题，但后台或驾车安全门禁不松。
5. 可信已安装**离线行政区几何优先于在线 Geocoder**（即使移动数据打开）；覆盖不到的地区仅按旧有安全地理规则降级，不允许从 IP/代理或旧街道推断现位置。
6. 常规本地叙述时 TTS 优先真正离线中文声音；云端上次失败/超时状态不能覆盖本地运行健康提示或留下全局阻塞。保留 skip/quiet/replay/打断/前台服务/已播记忆和恢复逻辑。

## 当前候选改动供检查，不是 PASS

分支 `fix/offline-native-no-destination-question` 已包含 DirectorLoop、KittRuntime、RealLocationSource、Journey、DirectorProvider、LocationContext、RuntimeStatus 和相应 Unit/Robolectric/模拟测试的最小改动。**先完整 Review 候选，不假设此前远端文档级修改一定编译、不会回归。** 重点检查：

- `preferOffline` 只用于生产真实物理旅程，模拟 provider 回归依然可控；不应从网络状态决定是否讲本地知识。
- 在线情况下 `RealLocationSource` 的本地几何优先、边界退让、LAST_KNOWN 不误认，以及离开新都时不会保留过期地区。
- `OfflineNarrator` 的有限已支持问法与明确开放问题的在线分支不被误合并；离线条件下不使用云端 TTS。
- `ASK_USER` 禁目的地在所有实际入口/提示词/旧测试中都一致；用户主动告知目的地并没有损失。
- `app/src/main/java/com/kitt/reader/RuntimeStatus.kt` 的失败状态、`DirectorLoop` 取消/并发和章内研究机会不会复活旧云请求；假 TTS 完成/旧无网语音试听不能冒充真人沿途 Gate。
- PR 相关测试和其余未改的历史测试都必须实际执行，任何失败按原因修复并重跑，不删除测试/放松可信定位阈值求绿。

## 办公室 Codex 的执行顺序

1. **检出 Draft PR #25 的分支**（不是重启 main 的旧 V0.4.0 任务）并确认办公室 Git 工作区没有未保存的个人变更：`git fetch origin && git switch fix/offline-native-no-destination-question && git pull --ff-only origin fix/offline-native-no-destination-question`。若本地已有未提交改动，停止危险覆盖，仅报告冲突处理计划。运行 `git status --short`、核对编译 JDK17 和 Android SDK35。
2. 先 Review 候选，视回归结果只做必要代码/测试修订；调整 `app/build.gradle.kts` 到 **V0.4.1/versionCode15**（真实主干版本高则递增到更高），不能修改 applicationId、签名文件路径、私钥/密码，也**绝不能把私钥、账号令牌、GPS轨迹、私人聊天记录写入 GitHub/日志**。
3. **真实运行**：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Offline`。包括 Debug/Release Unit、assemble、lint、签名；再运行六组项目模拟脚本/测试及必要的地理/线上超时隔离专项测试。若已有本地依赖可以使用非 Offline 构建但必须说明原因，不能隐藏 skipped/failed。重要断言：**真实运行路线在线 / 无网 / 在线但 ChatGPT 不可达**，地方包命中时自动讲述不调用云端 AI（计数为0），非覆盖地区安静；主动联网问题按需调云且失败不阻塞本地。
4. 运行 `powershell -ExecutionPolicy Bypass -File scripts/build-runtime-smoke.ps1 -Offline`，生成**同签名**独立验收包 `com.kitt.reader.runtimecheck`，而不是错误地把普通 `com.kitt.reader` APK 安装到已经有账号/设置的数据空间。运行 `scripts/check-kitt-signing.ps1` 验证 APK 唯一证书 SHA256 必须是 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，且 Android `versionCode > 14`。
5. **安装前先报告** APK 包名、code、哈希、真实全套测试数与失败项及旧手机 APK 信息。**未获得本轮手机原位升级确认前，不自动 adb install、uninstall、clear data 或替换手机 App**。用户确认后，只用原签名 `adb install -r`，手动真机断网/联网+不可用 ChatGPT、GPS、免目的地、主动问答/静音/打断及本地中文发声分开验收。
6. 工程全绿与真机结果分别记到 Issue #24、PR #25、HANDOFF 和脱敏 evidence；必要时先同步主干新增文档避免 PR 失效。**没有真机 GPS 或 AI 观测证据则明确 PENDING，不伪造 PASS**。质量合格才申请 PR Review/Merge；本轮完成 STOP，暂不做 #23 评分删除、V0.5.0 故事、Explore、离线包下载器。

## 完成定义与 STOP

交付候选同签名可覆盖 APK + 可复核构建日志/测试计数 + 真实改动/未验证分项 + 一页最短真机验收操作。工程与手机分开判定。**没有完整验证前不得说“手机上已经修好了”。**
