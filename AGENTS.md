> **2026-10-17 当前新任务：D2 正式发布能力最小工程（仅代码和模拟，不真实发布）**：用户明确“下一步”，已建立 [Issue #42](https://github.com/shimao1115/KITT/issues/42) 与分支 `feat/v060-d2-production-release`；**唯一施工依据为此分支 [TASK_CARD.md](TASK_CARD.md)**。PR #41 已于 merge `baea614ba02f1aad7776e65ed6766833ee451b3f` 合并 main，旧“D2 待复审/禁止继续施工”属于历史。目标仅让办公室/家里任一电脑未来能在用户**一次内容确认**后自动完成安全校验、独立本机签名、验签并由 gh 发布；不增审批/服务器/仓库/架构。本次**只做实现、模拟和 Draft PR**，不生成真正生产身份/密钥、公钥信任根，不创建 Tag/Release，不上传文件，不更改 app/src/main/杨慎审批/手机 code19，不启动 V0.6.1，STOP 等独立复审。

---

> **2026-10-17 D2 工程交付 / STOP 待独立工程复审**：按更新后的 [Issue #40](https://github.com/shimao1115/KITT/issues/40) 实现“任一电脑制作 → 用户一次确认 → 当前电脑自动检查/签名/验签/发布准备”的 TEST_ONLY 演练，取消专用签名机和额外人工 Gate。最终 **Debug586 + Release573 + packtool13 = 1172 项**零失败/错误/跳过；D2 19 例双 Variant，两干净工作树真实独立编译/无密钥字节复现、CLI 正反例、Python7、六组 Fake、构建/lint、原签名2+6均 PASS。显式合法子集测试通过且原全量继续拒绝，未修改真实审批。见 [HANDOFF](HANDOFF.md)、[任务卡](TASK_CARD.md)、[证据](docs/evidence/v060-d2-verification.json)。**两实体电脑 PENDING；没有生产私钥/正式根/Release/真实人工出版确认，没有手机/ADB/安装/合并，app/src/main 和真实杨慎内容零差异；新都全量仍 BLOCKED、正式故事0。** Draft PR 交付后 STOP，不擅自开 V0.6.1；首次真实身份/出版另行授权，此后的合规内容只需用户看一次，不恢复旧“专用签名机/多重人工审批”设想。

---
> **2026-10-17 最新 D2 施工授权（覆盖下方 D1 的历史 STOP）**：用户已批准**最小 D2 工程准备**，要求办公室和家庭两台 Windows 电脑都能参与离线知识包制作、相同 Git commit 可各自预制并校验；办公室和家里均可独立完成最终发布（用户只需一次内容终审；程序负责自动内容门禁、验签、签名和上传）；建议两机各自本地持有独立发行私钥，复用现有TrustStore，不将私钥通过GitHub同步；实际生产密钥生成与第一次正式发布仍须另行授权，拒绝过度设计。唯一任务 [Issue #40](https://github.com/shimao1115/KITT/issues/40)，唯一分支 `feat/v060-d2-dual-workstation-release`，施工前首先阅读该分支 [TASK_CARD.md](TASK_CARD.md)。D1 [PR #39](https://github.com/shimao1115/KITT/pull/39) 已合并 main；本阶段只做流程、工具、测试和 Draft PR，保持手机 code19、真实新都发行 BLOCKED，**不建 KITT-PACKS、不生成生产密钥、不发 Tag/Release、不装机/合并、不启动 V0.6.1**。

---

> **D1 最终工程复审通过 / 用户批准合并范围**：2026-10-17 D1 第二轮独立工程复审 PASS（PR #39 head 15894f5；评审 https://github.com/shimao1115/KITT/pull/39#issuecomment-6233819737）。用户已明确批准仅做最终文档收口及 PR #39 合并 main；此段是合并前授权与审查状态，不预先宣称合并成功。测试依据提交的工程证据1134项零失败，ChatGPT未实跑Gradle；生产代码 app/src/main 零改动。仍无生产密钥、Release、正式新都可发行故事、手机JCA验收、D2授权；sequence只是调用方下限，非持久防回滚。旧本机路径留在Git历史，未授权不得重写。实际合并状态以GitHub PR merged及merge commit为准。

---

> **2026-10-17 V0.6.0 D1 评审修复轮 / STOP 待再次复审（覆盖下方“首轮交付”文字）**：[PR #39 首轮独立评审](https://github.com/shimao1115/KITT/pull/39#issuecomment-6233095568) 的三项要求已修：①`InnerBinding` 在签名后逐字段比对签名外层与内层四块的 `area_id/parent_id/两个版本/minimum_code/pack_id`，同块重签成别县现在返回 `inner_binding`；②证据生成脚本内置 `scrub()`，本机家目录/临时目录/复现工作树在运行时即替换为 `<HOME>`/`<TEMP>`/`<REPRO_WORKTREE_n>`（旧 JSON 的路径只留在 Git 历史，未授权不改写历史）；③`d1-reproduce.ps1` 改随机目录、已存在即 STOP、只清理本轮登记的工作树，并有哨兵测试。另按评审要求把防回滚做成**最薄**实现：manifest 增加单调 `sequence`，调用方给 `minimumSequence`，低于即 `sequence_rollback`；**持久安装序号/撤销中心仍属 V0.6.1/D2，不宣称完整防回滚**。本轮真实重跑 **Debug 567 + Release 554 + packtool 13 = 1134 项**零失败，D1 专项 27 例双 Variant；两干净 worktree unsigned `6d9e0f9c…`、整包 `cf283c6f…`、10,468 B 一致；CLI 新增 `sequence_rollback`/`inner_binding` 拒绝；签名回归 2+6、Python 7 项 PASS；`app/src/main` 仍零改动。**无生产密钥/Release/手机/合并/D2；真机 JCA 与真实新都对外发行仍 PENDING，正式故事 0。** 见 [HANDOFF](HANDOFF.md)、[契约](docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md)、[验收](docs/ACCEPTANCE_COUNTY_PACK_D1.md)、[证据](docs/evidence/v060-d1-verification.json)。

---

> **2026-10-17 V0.6.0 D1 首轮工程交付 / STOP 待独立复审（覆盖下方“施工中”文字）**：[Issue #38](https://github.com/shimao1115/KITT/issues/38) 的 D1 黄金路径已完成，交付 [Draft PR #39](https://github.com/shimao1115/KITT/pull/39)，实现提交 `a69ccfe4`。新增纯 JVM 模块 `:packtool`（KCPK v1 固定帧容器、canonical manifest、`SHA256withRSA` 签名、调用方预置可信锚、验签后接 D0 内容门禁）与 `build/verify/keygen/tamper/audit-newdu` CLI；**签名代码不进 APK，`app/src/main` 自 D0 起零改动**。本轮真实重跑 **Debug 563 + Release 550 + packtool 11 = 1124 项**，0 失败/错误/跳过；两干净 worktree 用同一 TEST_ONLY 密钥产出相同 unsigned `6d9e0f9c…` 与整包 `5046cd93…`（10,455 B）；CLI 正例 `ACCEPTED_TEST_ONLY`（fact-only 4 事实/2 来源/7 Claim/**0 故事**、含故事样例 2 篇）、负例 `bad_signature`/`minimum_code`、真实新都审计 `BLOCKED external_redistribution_rights_unverified`（11 来源中 2 个不明）；签名回归 2 接受+6 拒绝、Python 内容门禁 7 项 PASS。**没有生产私钥/官方公钥/Release/第二仓库，未操作手机、未安装 APK、未合并 PR、未审批杨慎、未开 D2；真机 JCA 与真实新都对外发行仍 PENDING**。详见 [HANDOFF](HANDOFF.md)、[契约](docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md)、[验收](docs/ACCEPTANCE_COUNTY_PACK_D1.md)、[证据](docs/evidence/v060-d1-verification.json)。

---

> **2026-10-17 当前唯一获授权施工：V0.6.0 D1 县级双包可复现打包与可信签名验证（**仅TEST_ONLY发行候选、不得正式Release**）**。用户在 [D0 PR #37](https://github.com/shimao1115/KITT/pull/37) 已正式合并 main（`1330574d15deb55bc8881783c55e889ddf77ddf0`）之后明确指示“下一阶段”。本阶段唯一 [Issue #38](https://github.com/shimao1115/KITT/issues/38)；分支 `feat/v060-d1-signed-county-packs` 的最新 [TASK_CARD.md](TASK_CARD.md) 和 [D1审计](docs/reviews/V060_D1_RELEASE_FORMAT_AUDIT.md) 为当前施工入口。目标：用明确未受信任的TEST_ONLY临时密钥从冻结输入构建县级Fact/Narrative**可复现字节**、签名并离线验签，保留独立审核门禁与默认生产拒绝；真实新都内容若发行版权未明确应BLOCKED而非自动放行，杨慎两篇Visitor未通过，正式完整故事**0**。本轮仅电脑本地开发、全量验证、独立Draft PR交付后 STOP。**不准创建/托管真正发行私钥/官方根，不能发布Tag/GitHub Release、KITT-PACKS仓库、手机APK升级/文件导入/激活，不改UI、TripStore、设置或旧史料，不审批杨慎、合并本PR、施工D2/V0.6.1**。D0原本工程1067项PASS**不能复用为D1成绩**，手机仍为code19、settings.xml字节漂移根因仍UNKNOWN；下方 D0“D1不准开工”的文字是先前阶段记录，已由此条新用户授权在D1精确范围内覆盖。

---

> **2026-10-17 最新（优先于下方各阶段旧 STOP）：V0.6.0 D0/code20 独立工程复审 PASS，用户已明确批准文档收口并合并 [PR #37](https://github.com/shimao1115/KITT/pull/37) 至 main。** 工程实施 `dc764651035e365c082be0c339b4de1a2a3c611b`、交付 `2195ad04ddc2f954bdd0e98a6a7aa59262842add`，Debug **540** + Release **527** = **1067 项** 0失败/错误/跳过；Python7、六组Fake模拟、构建/lint、签名回归有Qoder可追溯证据。两处独立审查提出的县级/父级码归属与来源/Claim显式许可门禁已修复复审通过（[评审](https://github.com/shimao1115/KITT/pull/37#issuecomment-6231174949)）；**独立审阅源码/日志不等于重新运行外部CI或真机**。D0只建内置新都兼容与通用县级Fact/Narrative只读模型；第二县仍TEST_ONLY且不得进入正式APK，正式故事0、杨慎 `visitor_gate=pending`，无发行者签名根、无GitHub Release、无外部下载/导入/原子安装。**本次许可只包括去敏文档收口＋在最终PR门禁通过后合并PR37**：当前手机仍为已验收 code19，**不得访问/升级手机、重构APK、发布APK/知识包、晋升杨慎审批、处理PR29、关闭#23/#24/#27/#30、启动D1/D2/V0.6.1或UI优化**。旧 `settings.xml` 首进设置页字节漂移根因继续 UNKNOWN；Gradle首次IPC/Release进程失败的确切根因亦UNKNOWN，最终强制全量重跑已PASS。**PR实际是否合并及合并SHA以GitHub状态为准；不要依据文档授权误认已经执行。** 下方“待复审/不准合并/D0仍施工中”仅属历史阶段文字。

---

> **2026-10-17 D0/code20 两处评审修正 / STOP 待再次验收**：已按 [PR #37 独立评审](https://github.com/shimao1115/KITT/pull/37#issuecomment-6229804921) 完成县级码/父级码归属校验与来源/Claim显式权利允许表，修正提交 `dc76465`；生产差异仅 `CountyPacks.kt`，新都正文、杨慎审批、账号设置及Android UI不变。**本轮完整重跑 Debug540 + Release527 = 1067项，0失败/错误/跳过**；六项新增用例两Variant均执行，Python7、六组Fake模拟、构建/lint、原签名2接受+6拒绝PASS。首轮Gradle本机IPC失败尝试已保留且排除，最终串行独立进程全量重跑通过。code20 runtimecheck本地候选 **74461274字节 / SHA256 `2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60`**，旧1055项及旧候选仅为历史。详见[HANDOFF](HANDOFF.md)、[证据](docs/evidence/v060-d0-verification.json)。**Draft PR #37 保持未合并；无手机/ADB/安装、无发布、未启动后续里程碑；正式故事0、杨慎candidate/visitor_gate=pending不变。独立工程复审与用户验收仍PENDING。**

---

> **2026-10-17 V0.6.0 D0/code20 工程交付 / STOP 待用户验收**：[Issue #36](https://github.com/shimao1115/KITT/issues/36) 已由实现 `91b96a8` 完成县级 Fact/Narrative 独立读取、manifest/整体哈希/来源验证、县与市州索引及精确Claim修订门禁，交付 [Draft PR #37](https://github.com/shimao1115/KITT/pull/37)。真实新都保持1县6对象22基础事实6来源、正式故事0；西陵仅非相邻 TEST_ONLY 合成夹具，未入APK，无生产GPS几何。**本轮重跑 Debug534 + Release521 = 1055项，0失败/错误/跳过**，Python7、六组Fake模拟、构建/lint、原签名2接受+6拒绝PASS；全新目录runtimecheck75/75任务执行。code20本地候选74461274字节、SHA256 `2762d6ffc623f80261f14ac0ea0ea78e56611c39817fa56f63ca255308462821`；与本地code19重新核签后仅静态兼容、权限不变。见[HANDOFF](HANDOFF.md)、[证据](docs/evidence/v060-d0-verification.json)、[契约](docs/COUNTY_PACK_D0_CONTRACT.md)。**无手机/ADB/安装，无合并、APK上传或GitHub Release；PR29未动，杨慎candidate/visitor_gate=pending不变，外部发行者根及下载/导入/安装器未实现。** 旧设置漂移UNKNOWN、来源双模式DEFERRED、#23/#24/#27/#30原状态保留。只等待本D0工程验收，不启动D1/D2或后续里程碑；下方开发授权按历史时点解释。

---

> **2026-10-17 当前唯一新施工任务：V0.6.0 · D0 / code20 县级 Fact+Narrative 通用加载器首个小版本**。用户在 V0.5.2/code19 [PR #35](https://github.com/shimao1115/KITT/pull/35) 已合并 main（`53c0e0fa`）、用户真机手工体验 PASS 后同意推进下一阶段“先审 PR29、再拆最小通用加载器工程”。本轮已经完成只读 [PR29差距审计](docs/reviews/V060_PR29_AND_LOADER_AUDIT.md)，当前仅执行 [Issue #36](https://github.com/shimao1115/KITT/issues/36)／分支 `feat/v060-county-pack-loader-d0` 最新 [TASK_CARD.md](TASK_CARD.md)，以 **Draft PR + 全量测试 + 真实工程证据后 STOP** 为唯一交付。**不合并 PR29、本工程PR、不碰手机、不创建 KITT-PACKS、无 GitHub Release、不允许外部未经验证的包成为正式内容、不审批杨慎、不开六区采集。** 正式故事0、20份真实历史保留、兴趣保存PASS；原 `settings.xml` 首进设置页哈希漂移根因 UNKNOWN，云账号在线可用性未证，离线/即时双模式DEFERRED。用户要求“保存讲述兴趣”和“保存”两个按钮挨太近暂时不改，等后续统一UI优化。旧本文下方 V0.5.2 “工程未完/手机未升级/不得开始新版本”属于**历史阶段**，不得覆盖当前授权。

---

> **2026-10-17 最新：V0.5.2/code19 用户人工体验 PASS，已明确批准文档收口及 PR #35 正式合并**：用户亲手检查已安装 code19 后反馈“都正常”，唯独设置页 **「保存讲述兴趣」与原「保存」按钮挨得太近**；用户明确要求**本版不改，留到以后集中 UI 优化**，也可顺便处理更改 Chip 后旧“已保存”提示可能未清除的问题。Qoder 本版 Debug511 + Release498 = **1009项**零失败/错误/跳过；一次 code18→19 原签名原位升级PASS；六类兴趣真实设备辅助检查PASS，用户体验亦PASS；20/20真实历史逐文件哈希不变；兴趣保存只写独立 `story_interests.xml`。**首次打开原设置页时 `settings.xml` 又出现字节漂移，具体字段与根因仍 UNKNOWN，不代表账号在线可用已验证**。正式完整故事仍0篇，杨慎两篇仍候选/Visitor Gate未过，双来源模式DEFERRED、外场#23/#24/#27及#30保持OPEN/PENDING。此授权**仅允许先完成去敏文档收口，在PR无冲突、无未解决review、未夹带代码/内容改动等门禁通过后把 [PR #35](https://github.com/shimao1115/KITT/pull/35) 合并到 main**；不允许再次访问/安装手机、重建APK、正式发布、变更故事审核、开始V0.5.3/V0.6.x或关闭旧Issue。**合并是否完成以 GitHub PR 实际状态为准；下方旧“code19未安装/不得合并”均为历史阶段文字。**详见[HANDOFF](HANDOFF.md)与[设备证据](docs/evidence/v052-phone-acceptance.json)。

---

> **2026-10-16 V0.5.2/code19 工程交付 / STOP 待用户工程验收**：[Issue #34](https://github.com/shimao1115/KITT/issues/34) 的六类讲述兴趣已在分支 `feat/v052-story-preferences` 完成工程实现（实现提交 `d9d9b41`，交付 [Draft PR #35](https://github.com/shimao1115/KITT/pull/35)）：设置页单一「讲述兴趣」区块（六类×三档，初值全部正常）、独立 `story_interests` 偏好文件持久化，以及**全部既有资格门禁之后**的类别柔性排序。本轮真实重跑 Debug511 + Release498 = **1009 项零失败/错误/跳过**（旧 code18 的965不得重算），Python7、六组确定性/Fake模拟、构建/lint、原签名2接受+6拒绝均 PASS；原签名 `com.kitt.reader.runtimecheck` **code19** 本地候选 74444882 字节、SHA256 `3af6f91a0aba318d4be58d53f0d15c7eef25632a14f2ae7caf98ecf6e08b2a4b`，code18→code19 仅**静态**同包同证书兼容。**手机未访问/未安装、PR 未合并、APK 未上传/发布、任何故事审批未改变；正式完整故事仍0，杨慎两篇仍 `candidate / visitor_gate=pending`，`--require-playable` 仍 BLOCKED。** 来源双模式（离线优先／即时优先）判定 **DEFERRED**：`preferOffline` 硬绑 `!simulation`、自动分支无有界本地回退、会牵动后台 research，均超出本版边界，故未实现、未测试、也没有做假 UI 开关，详见 [HANDOFF](HANDOFF.md)。兴趣写入被隔离在新文件，`settings.xml` 字节漂移根因仍 `UNKNOWN`，本版不声称修复它。#23/#24/#27/#30 保持原状态，PR #29 与 V0.6.x 未触碰。合并代码、真机升级、故事正式发布需各自单独授权；当前 **STOP 等用户工程验收**。下方授权段与更早文字按历史时点理解。

---

> **2026-10-16 当前唯一新开发授权：V0.5.2/code19「六类兴趣柔性选题」**：用户在 [PR #31](https://github.com/shimao1115/KITT/pull/31) 已正式合并至 main（`9319c271`）、code18 用户人工探索验收 PASS 之后明确说“好的，我们开始下一步”。仅施工 [Issue #34](https://github.com/shimao1115/KITT/issues/34) 和分支 `feat/v052-story-preferences` 的**最新版 [TASK_CARD.md](TASK_CARD.md)**，开独立 Draft PR；旧 V0.5.1“未授权施工/尚未安装”均为历史状态，以 main 的最终 HANDOFF 为准。code18 设置 XML 漂移根因仍 UNKNOWN，20份真实历史不变；杨慎两篇仍候选、正式故事0；旧真实GPS Gate PENDING。本轮不操作手机、不更新安装、不合并/发版、不自动批准故事、不启动V0.6.x。优先六类三档持久化 + 门禁之后的软排序，来源双模式只有能有界完整闭环才顺带施工，否则DEFERRED。旧验收计数965不得重算为本轮成绩。

---

> **2026-10-16 V0.5.1/code18 工程完成 / STOP待用户验收**：探索入口、县级列表/搜索/事实与来源阅读、合格故事全文/手动离线TTS及生命周期/布局已实现。实际UI新都6对象/22基础事实，正式故事0；两篇杨慎候选/审批/正文不变，2篇批准夹具仅测试。Debug489 + Release476 = **965项零失败/错误/跳过**，Python7、六组模拟、assemble/lint和原签名2接受+6拒绝PASS。原签名 `com.kitt.reader.runtimecheck` code18本地候选74976037字节、SHA256 `8fb8b57040787ea82ffd3fb026d230698ac7ab8bde84ab29868b670b32413fea`；见[HANDOFF](HANDOFF.md)、[证据](docs/evidence/v051-verification.json)、[Draft PR31](https://github.com/shimao1115/KITT/pull/31)。**本轮手机未访问/安装，现有code17不动；设备UI/语音仍PENDING，code17历史真人耳听PASS保留。** 不合并、不发布、不关闭23/24/27，不扩下一版本。下方施工授权与未施工文字按历史时点理解；后续安装须用户另行许可，PR29独立不受影响。

> **2026-10-16 V0.5.1/code18 施工授权（工程结果由上段覆盖）**：V0.5.0 工程 PR [#28](https://github.com/shimao1115/KITT/pull/28) 已合并主干（merge `465d5828`），手机现为原签名 runtimecheck code17（不要再次安装）。用户明确开始下一轮，唯一施工目标为 [Issue #30](https://github.com/shimao1115/KITT/issues/30)「探索地点」和有界 UI 精修，分支 `feat/v051-explore-places`，最新入口为 [TASK_CARD.md](TASK_CARD.md)。Codex 已获代码开发、全量测试、原签名候选构建及提交 Draft PR 的授权，**尚未获得新 APK 设备安装、合并或故事正式发布授权**。两篇杨慎故事仍 candidate、正式故事0；#23/#24/#27 外场继续 PENDING。不许绕过内容门禁、不许误将测试候选当正式故事；PR #29 六区采集规范独立不受影响。

---

> **2026-10-16 最新手机验收完成 / STOP（覆盖下方安装限制与未来排序）**：用户明确授权 code17 同包同签名一次原位升级、配套 AndroidTest 安装及 `story-preview` 双篇试听；不含清数据、合并或发布。首次只读发现未结束旅程后立即停止，用户回复“已结束”，复核恢复文件消失后才执行。手机现为 `com.kitt.reader.runtimecheck` **code17**，一次 `adb install -r` 成功，回读 APK 与指定候选 SHA256 一致，配套测试 APK 已安装验签。升级及试听前后设置与20份历史逐字节哈希相同，原数据目录标识及首次安装时间未变。主篇117.149秒、延伸59.532秒均PASS_ENGINE_ONLY；用户明确反馈**“两篇都完整听完，没问题”**，HUMAN_LISTENING=PASS。**禁止重复安装code16/code17；实机自动全链未验、Visitor未提升，正式故事0、发布BLOCKED。** 见[HANDOFF](HANDOFF.md)及[手机证据](docs/evidence/v050-phone-acceptance.json)。不合并Draft PR28，不关闭23/24/27，不发布APK。后续排序按用户最新指示：**先主程序完整功能／同步UI，之后按Draft PR29六区县级独立分包及镇街全面调查**；仅记录安排，不据此提前施工。

> **2026-10-15 历史交付 / CANDIDATE-STOP**：用户已确认v2.1双篇最终文字；本轮来源复核相符，原正文/哈希/Claim修订不变，Debug 466 + Release 453 = 919项零失败/错误/跳过，Python7、六组模拟、构建/lint及原签名2接受+6拒绝PASS。已生成仅供用户验收的原签名 `com.kitt.reader.runtimecheck` code17，74399802字节、SHA256 `9d2d2b312c669e0469113e80a54164e3217519c77a6692c80037b4b98c776a6d`。两篇仅candidate，Visitor/真人耳听PENDING，标准正式故事0、发布BLOCKED。详见[HANDOFF](HANDOFF.md)及[工程证据](docs/evidence/v050-final-verification.json)。当时未安装；安装状态与后续排序由上方2026-10-16记录覆盖。

---

# AGENTS.md — KITT Autonomous Build Contract

> **2026-10-15 最新 v2 改稿交付 / REVIEW-STOP**：已按驾驶场景重写主篇580字符、延伸297字符，延伸改讲《滇载记》商旅歌谣；9来源/16核验条目。两篇仍draft，Fact人工终审/Visitor Gate均PENDING，审批空，正式故事0。仅内容改稿、追踪与专项校验；本轮Python4项、StoryPacks Debug专项16项PASS，发布门禁仍BLOCKED。下方915项为v1冻结工程历史，未在本轮重跑。**未构建任何APK，未操作手机，PR #28保持Draft、不合并；STOP等用户审阅第二稿。**

> **2026-10-15 V0.5.0当前交付 / REVIEW-STOP**：杨慎主篇＋延伸全文与9来源/14核验条目已提交审阅；两篇仍draft、正式故事0。工程冻结回归 Debug 464 + Release 451 = 915 项零失败，Python4项、构建/lint及原签名回归通过；详见 [HANDOFF](HANDOFF.md)、[Draft PR](https://github.com/shimao1115/KITT/pull/28)。**内容人工/独立终审和游客试听PENDING，runtimecheck code17被门禁阻断、没有候选APK；手机未操作，不合并，不开下一版本。** 下方“目前仅任务卡/尚未实施”等为施工前记录，由本段覆盖；不能据旧授权重复安装code16。

> **2026-10-15 最高优先：用户已授权启动 V0.5.0/code17 杨慎完整离线主故事+延伸篇**，唯一活动任务 [Issue #27](https://github.com/shimao1115/KITT/issues/27)，独立分支 `feat/v050-xindu-yangshen-story-packs` 的 [TASK_CARD.md](TASK_CARD.md) 为施工权威入口。V0.4.2/code16 已阶段性通过并由 [PR #26](https://github.com/shimao1115/KITT/pull/26) 合并，手机已有原签名 code16，**不可重装或在本轮直接安装 code17**。本轮先来源核实与事实/游客双 Gate、纯文字 Fact/Narrative 双包、仅杨慎完整主篇＋至少一篇延伸、最小 Story Selector 和真实全量 Android 测试，工程/内容达标才产候选 APK。**创建分支或 AI 草稿≠来源已核验/用户已经审稿/手机已经听完**；稿件审核不充分宁可留 BLOCKED，不可编造来源、卒年、写作年份或正式批准。**V0.5.1 探索地点、V0.5.2 六类偏好、V0.6.0 GitHub Releases、KITT-PACKS 仓库、登录/定位重构均未授权**。#23/#24 外场待验继续开放。下文所有“V0.5.0 尚未授权”等为历史。

> **2026-10-15 最新权威交接（覆盖下方历史优先级与 STOP 文字）**：用户同意 V0.4.2/code16 阶段性通过并合并 `main`，已合并 [PR #26](https://github.com/shimao1115/KITT/pull/26)，merge `9351f17`；原版 [PR #25](https://github.com/shimao1115/KITT/pull/25) 已更早合并。手机现为 `com.kitt.reader.runtimecheck` **code16**，不得重复安装、卸载、清数据；此轮工程879项/结束页与自动纪要已通过，联网本地自动讲述零云调用为设备级观测。[#23](https://github.com/shimao1115/KITT/issues/23) OPEN，仅补旧评分实机样本兼容/真人耳听等 PENDING；[#24](https://github.com/shimao1115/KITT/issues/24) OPEN，仅补离线实地定位与恢复等 PENDING。**V0.4.2 任务卡已归档，下一候选 V0.5.0 高质量 Fact/Narrative 离线双包尚未授权**；Codex 不得按下方旧“code16 等待安装/PR Draft”任务重复执行，也不能自动启动下一版本。详见 [HANDOFF](HANDOFF.md)、[已归档 TASK_CARD](TASK_CARD.md) 和 [路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)。

> **2026-10-15 当前STOP状态**：用户已授权并完成V0.4.2/code16单次同签名原位升级及有界真机验收；手机已code16，不得重复安装。最新结果见 [HANDOFF](HANDOFF.md)、[TASK_CARD](TASK_CARD.md)、[手机证据](docs/evidence/v042-phone-acceptance.json)。旧评分实机样本/真人耳听及#24外场仍PENDING；Issue23/24保持OPEN、PR26 Draft、不合并、不启动V0.5.0。下方“等待手机升级许可”等为历史。

> **2026-10-14 最新用户授权 / 当前唯一施工目标：V0.4.2/code16 的旅程结束页评分移除**。V0.4.1/code15 已阶段性接受并通过 [PR #25](https://github.com/shimao1115/KITT/pull/25) 合并 `main`，真机手机 code15、[#24](https://github.com/shimao1115/KITT/issues/24) 的断网定位/云失败恢复外场仍 OPEN/PENDING。本分支 `feat/v042-simplify-end-summary` 有[最新独立 TASK_CARD.md](TASK_CARD.md)：只删 5 项 1—5 评分、可选评语及保存按钮；保留纪要及返回，`TripStore.finish()` 继续自动写入并保持历史 JSON（含旧 `ratings`/`feedback`）兼容性。**不得为了完成 UI 而修改定位/在线调用/TTS/账号，六类偏好和双层包下版再做**。V0.4.2 开发/测试/生成新签名 APK 已授权，**手机 code16 原位安装未获本轮独立许可**，构建后先 STOP 等确认；**本段覆盖下方旧版本排序与 STOP 文字，它们仅为当时历史**。

> **2026-10-14 优先级修正**：已核实 V0.4.0/code14 真正的 Android 源码：联网时自动沿途仍会调用云端 Director，且 Fake/提示词有“去哪儿”的目的地追问。**当前最高优先的未来施工任务改为 [#24 本地原生开口热修](https://github.com/shimao1115/KITT/issues/24)，候选在 [Draft PR #25](https://github.com/shimao1115/KITT/pull/25)**；必须先用办公室/家庭电脑实跑 Android Debug/Release、模拟、真机 GPS/断网及原签名回归，审核通过后才能合并/发布。**随后**是 #23 删评分，再 V0.5.0 成品双包。旧版 code14、当前 TASK_CARD/#20 与真机状态尚未修改；以下先 #23 的日期较早排序由本条取代。

> **最高优先未来排序（2026-10-14 用户确认）**：[本地知识优先下一阶段路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)。先 V0.4.1 #23 结束页去评分，再 V0.5.0 新都高质量双包、V0.5.1 探索地点、V0.5.2 六类兴趣，其后 Github 分发/一键更新/在线 AI 检索。**不把 ChatGPT 账号不响应、在线模型列表或代理 VPN 不稳定视为当前施工 blocker**；云请求超时/失败不能阻碍本地知识使用，不许为查在线故障越权重构登录/定位/语音。无可信 GPS 时自动沿途仍不得推断位置；主动探索异地不需要 GPS。每一小版本须另行下发任务卡验收。**目前只有规划更新，V0.4.0/#20 的施工 STOP 与未验状态仍有效**。

> **2026-10-14 后续知识包接班规范（不扩当前施工）**：凡涉及离线事实包、成品文字讲述包、多模型资料生产、GitHub 存储、下载和发行，应先读 [正式内容标准 V2.4](docs/KITT_KNOWLEDGE_STANDARD.md)、[知识工厂 V2.4](docs/KITT_KNOWLEDGE_FACTORY.md) 与 [长期离线蓝图 V2.4](docs/FUTURE_OFFLINE_KNOWLEDGE_PACKS.md)、[离线包管理与更新规范 V1.0](docs/KITT_OFFLINE_PACK_MANAGEMENT.md)。用户已确定 **Fact Pack + Narrative Pack 两层纯文字知识、手机本地 TTS、基础功能不需要 API**；旧 V0 单层事实/在线 Director/不准预制旁白的说明只作为历史实现证据，不得用来否定未来按主题制作审核过的完整文字故事。**更新决定：仅办公室电脑/家庭电脑生产，GitHub 存储协作和 Releases 分发，NAS 不参加。讲述默认离线优先；即时优先由 AI 现场组织，但**已选方案 B**：无论哪种讲述模式，AI 的地方知识问答均可先查相关本地 Fact/Narrative，缺证据或临时问题再联网查证，失败提示并回退本地故事；真正实时问题需要可靠在线新数据。** **知识包管理参考已定：按地区一键准备事实＋讲述、默认仅 Wi-Fi 自动更新已装包（可关闭）、显示真实覆盖/更新摘要、校验后原子安装/失败保留旧包；行前提示及签名本地导入列后续，差分优化暂缓。**新增正式交互“探索地点”**：手机上按当前位置或输入任何地区查已有主题，点主题直接看已审稿**完整文字讲述**，正文下方“播放”才让本地 TTS 朗读同一篇文字；无包、有网有 AI 才允许标明为临时在线探查。浏览别处不得覆盖真实 GPS 状态；不做音频包。模式属于未来任务而非已实现。**只有已授权任务卡才能改代码或发布包；V0.4.0/code14 已工程交付，不能因此自行施工 V0.5.0 或调整 #20 历史验收。

> **后续用户确定（2026-10-14）**：主题分类第一版严格六类，具体内容类别依[知识标准 V2.4](docs/KITT_KNOWLEDGE_STANDARD.md)；新成品 Narrative 每篇一主类及可选辅类，兴趣设置每类“多讲/正常/少讲”默认正常，**不可根据旧 `TopicFamily`/对象 `family` 直接推断主题分类**，也不可固定比例强制讲述。用户决定**取消旧结束页五项评分、意见框和保存评分**；保留轻量纪要和返回，旧本机 JSON 不得被破坏。**不得以此擅自修改现有 code14；由单独下一小版本施工任务与实测验收。**

> **2026-10-14 进一步确认的技术北极星：常规自动沿途讲述必须以本地知识包、本地确定性 Story Selector、离线中文 TTS 形成完整主路径，**绝不先调用/等待云 Director/ChatGPT/Research，也不依赖代理、网络或登录**。只有用户明确提出需要在线的临时/开放问题，或**主动选择即时优先**，才进入在线增强分支；相关已审 Fact 可以先本地检索。云请求失败/上次失败状态不得阻止下次本地讲述触发，不得无限重试/占用 TTS。无故事可安静，不降位置门禁，不伪造离线通用问答。此为后续小版本验收契约，**不是现有 code14 已完成，也不许可修改它**。详见[正式知识标准的 3A 节](docs/KITT_KNOWLEDGE_STANDARD.md)与[分阶段路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)。

> **新增关键运行门禁**：未来默认离线优先的正常自动讲述，必须完全依靠已装 Fact/Narrative、本地故事选择器和设备离线 TTS；不先请求或等待 ChatGPT、云端 Director/Research、代理连接、模型列表。只有用户按需提问需联网的问题、或主动选“即时优先”，才进入在线分支（相关本地事实仍优先参考）。云端请求失败只影响该请求，不允许错误状态阻断离线下一次选题、不许无限重试。可信 GPS、音频状态、真实证据仍照常校验；无故事可安静。详见 [离线优先路线图](docs/ROADMAP_OFFLINE_FIRST_NEXT.md)。本要求待未来小版本实现与验证，**不改变当前 V0.4.0/code14 代码或 #20 结论**。

本文件是 KITT 仓库中对开发 Agent 的最高优先级工程说明。任何实现必须同时满足本文件与 `docs/PRODUCT_SPEC.md`、`docs/AI_CONTRACT.md`、`docs/ROADMAP.md`。

## 1. 任务

自主完成“路上读山河”V0，使它成为一个可运行、可模拟验收的原生 Android AI 副驾驶。

不要把用户当项目经理或搬运工。除非遇到以下真正阻塞，否则不要中途索要决策：

- 需要用户本人完成 OAuth / 登录 / 授权；
- 必须输入外部密钥且仓库与环境中均不存在；
- 必须由用户提供现实世界素材才能完成最终替换；
- 外部服务明确不可用，且没有文档允许的替代路径。

遇到上述阻塞时：
1. 把阻塞压缩成最小范围；
2. 先使用接口、fake/mock 或 fallback 完成其他所有可完成工作；
3. 在 `HANDOFF.md` 记录准确阻塞、已验证事实和用户只需完成的最小动作；
4. 继续后续不依赖该阻塞的里程碑。

## 2. AI Native 原则

不要把 Roadmap 当逐行脚本。它是一条 Destination Chain。

你应当：
- 先理解目标与验收；
- 自主选择最短实现路径；
- 先跑通黄金路径；
- 只有真实失败或质量不足时，才增加中间层；
- 优先删除复杂度，而不是用更多抽象修复复杂度。

三层决策顺序：

> **本地确定 → 问用户 → AI 推理**

含义：
- 本地代码能廉价、确定地判断的，不调用 AI。
- 一个自然问题就能得到可靠答案的，不构造复杂算法长期猜测。
- AI 用在真正有价值的判断：选题、解释、对话、事实核验决策。

### Agent 接班原则

KITT 允许 Codex、WorkBuddy、Qoder、OpenCode 或其他施工 Agent 接力开发。任何新 Agent 接班时：

> **先继承现状，再继续第一个未完成的 Destination；除非现有实现明确阻塞验收，否则不要为了个人偏好重构已经通过 Gate 的部分。**

接班前先阅读 `AGENTS.md`、`docs/ROADMAP.md`、最近 milestone commits 和 `HANDOFF.md`（若已存在），以 GitHub 当前状态作为唯一施工真相源。不同 Agent 的角色是轮班施工队，而不是重新设计项目。

## 3. 禁止擅自扩张 V0

除非规格明确要求，不要新增：

- 自建导航或路线规划器
- 强制目的地/途经点管理
- 复杂路线预测
- 大型地图数据栈、DEM/OSM/地质数据库
- RAG、向量数据库、知识库
- Room/复杂数据库（除非实测证明轻量存储无法满足）
- 多 Agent / Director+Writer+Reviewer 链
- 大小模型自动路由
- 本地大模型
- 云端高级 TTS
- 常驻唤醒词
- 后台持续录音
- 摄像头识别
- Android Auto / 车机控制
- 音乐焦点联动、自动 ducking、车机音源控制
- 酒店/餐馆预订、支付、消息、日历等执行工具
- 行为埋点平台、远程分析后台
- 完整 GPS 轨迹长期留存
- 完整对话/录音长期留存
- 自动写入 GitHub Memory Hub
- 为模拟测试建设生产级路线 API
- “以后可能需要”的服务器与后台

如果一个功能不能直接帮助：
1. 更准确地知道当前现场；
2. 更好地判断该不该开口；
3. 把一件值得理解的事讲清；
4. 让用户更自然地接管 AI；
则 V0 默认不做。

## 4. 架构责任边界

产品层只保留五个核心责任域，实际包结构可以更少，不要为了架构图创建空层：

### Journey
开始/结束旅程、10 分钟安静模式、目的地意图、Session Instructions、最近主题、一个 PREPARE、旅程纪要。

### Context
真实 GPS 或模拟 GPS → 压缩后的 Context Card。地图 Provider 只能做 enrichment，不得成为主链硬依赖。

### AI Director
同一个 AI 能力负责自动导演、主动对话、是否查证、是否 ASK_USER。V0 不拆多 Agent。

### Voice
Android 本地 TTS + 按需语音识别。用户主动说话立即停止当前 TTS。

### UI
驾驶大按钮界面和旅程结束页。UI 不自行判断导演逻辑。

## 5. Provider 解耦

AI Director 不得依赖某个具体模型名或厂商业务逻辑。

定义薄 Provider Adapter，使上层只关心：
- system constitution
- session instructions
- context card
- optional user utterance
- structured director response

首选技术 Spike：当前可行的 ChatGPT/Codex 相关授权路径。
如果不可行或限制不适合 V0，直接使用普通 API Provider。不要让首选 Provider 阻塞整个项目。

设置中允许：
- Provider
- Model
- Reasoning effort（仅在 Provider/模型支持时）

不要在业务代码散落 `if model == ...`。

## 6. 质量规则

每个里程碑：
- 编译必须通过；
- 与该里程碑相关的自动测试必须通过；
- 不得遗留已知崩溃；
- 不得把密钥提交到仓库；
- 对无法自动验证的能力留下最小人工验收说明；
- commit message 清晰描述 Destination；
- 工作树尽量保持干净。

至少覆盖这些状态测试：
- journey start/end
- silence 10min / early exit
- user interruption stops TTS
- ASK_USER one-shot listening
- PREPARE invalidation
- stale content never replays
- provider failure silent-degrades for auto mode
- active request failure reports briefly
- director response parser/schema validation

## 7. 施工完成后的交接

最终创建/更新根目录 `HANDOFF.md`，至少包含：
- 当前可运行能力
- build/test 结果
- 最终默认 Provider/模型配置（若可用）
- 尚需用户本人完成的授权/密钥步骤
- 成都→绵阳模拟验收结果
- 已知限制
- 下一次人工总验收最短步骤
- 最近 milestone commit 列表

目标不是“代码很多”，而是用户醒来后可以直接做总验收。
