# 2026-10-18 D2 正式发布能力收尾：工程验证完成，Draft PR 交付后 STOP

唯一任务 [Issue #42](https://github.com/shimao1115/KITT/issues/42)，分支 `feat/v060-d2-production-release`；基线为已合并 [PR #41](https://github.com/shimao1115/KITT/pull/41)，merge `baea614ba02f1aad7776e65ed6766833ee451b3f`。本轮只完成可审查实现和隔离模拟，首次真实身份设置/首次正式发行仍未授权。

## 现在具备的工程能力

复用原 KCPK v1、固定 Git blob、D0 内容/版权/Visitor Gate、D1 Builder/Verifier 和 D2 `prepare/review.md/reviewhash`。原 TEST_ONLY 演练保留；生产入口为 `identity-init / production-prepare / publish`，没有新内容 Schema、服务、数据库、额外人工审批或专用签名机。

- 本机一次初始化能力：工作树外独立 RSA-3072 身份，先限制所有者 ACL 再写私钥；每次检查目录/文件权限，解析联接后检查实际位置及目标 ACL，拒绝 Git 目录和已有初始化覆盖。默认自动发现本机身份，与 APK 密钥分离。**本轮从未执行真实初始化**。
- 公开登记格式只包含 key_id、scope、虚拟标记和 SPKI 公钥；从固定源码 commit 的 `config/pack-publishers.json` 读登记，拒绝未知/不匹配公钥、TEST_ONLY 与虚拟身份进入真实 CLI。当前该表是**空数组**，没有正式可信根；手机接入留 V0.6.1。
- 预览展示基础事实、**全部 Claim 正文及依据**、完整故事和来源/权利，并绑定县、源码、两个版本、sequence、完整待签名区域 SHA256。唯一 stdin 决定之后重新读取同一 Git blob，核对摘要，自动检查/签名/验签。用户在聊天中已经作出的决定可由宿主转录；PowerShell 5/7 均能原样传递一行 UTF-8 中文，无需用户再在终端确认。Agent 不能自己编造决定；工具不独立认证输入者身份。
- 发布仓库固定 `github.com/shimao1115/KITT`；Tag `kitt-pack-cn-县码-s序号`、附件 `cn-县码-s序号.kcpk`。两侧生产版本用可比较的 `vN.N.N`，不改变 D0 Schema。读取现有 Release/Tag，检查同县序号、两侧版本、来源祖先和同版本字节一致性，再通过一次 `gh release create` 完成草稿/上传/公开流程；回读 manifest、附件大小/SHA256 和 Tag 实际指向后才报成功。不覆盖、不编辑/删除旧发布，不自动重试。

## 本轮最终实际验证（本机，非外部 CI/真机）

**Debug 644 + Release 631 + packtool 13 = 1288 项**，0 失败/错误/跳过。生产专项 **58 例双 Variant**，原 D2 19 / D1 27 例双 Variant 均在全量内。旧 PR41 的1172项、本轮前置641/631运行及专项重复均不相加；最终两 Variant 在完整 Claim 预览修正后重新全量执行。

Python 内容门禁 **7 项 PASS**；六组确定性/Fake 模拟、Debug/Release assemble/lint PASS；APK 原签名 **2 接受 + 6 拒绝 PASS**。仅用已有 code20 runtimecheck 作静态签名夹具，SHA256仍为 `2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60`；没有新 runtimecheck 候选或安装。

虚拟身份均在 JUnit 临时目录、工作树外，并与 fake gh 绑定。两独立身份从同一合成源码 commit 各自完成一次决定、内容检查、实际签名/验签及 fake create；有效输入才可到达 create，危险输入零 create。create/回读失败用例允许一次 fake 尝试，但绝不报成功或重试。覆盖 TEST_ONLY、审核对象变化、错县/版本/序号、旧版/重复 Tag、未知钥、权利不明、缺 Source/Claim、candidate/Visitor pending、签名/发布说明篡改、缺命令、网络/认证失败、超时与钥匙路径/权限。

另有 **PowerShell 7 与 Windows PowerShell 5 独立进程替身检查**：中文和 SHA 原样转录，模拟标记/错摘要/多行拒绝、CLI exit_code 拒绝向上传递、宿主编码恢复。初次绑定失败、Windows PowerShell 5 ASCII 问号问题均保留且已修正，不记作最终通过成绩。

[本轮脱敏证据](docs/evidence/v060-d2-production-verification.json) 记录新鲜 XML/日志哈希、前置运行、失败尝试、保护文件前后 SHA256 和模拟上传参数；[一页普通中文说明](docs/COUNTY_PACK_D2_PRODUCTION.md)；证据重建脚本 `scripts/record-v060-d2-production-evidence.py`。真实新都再审计仍为 11 来源 / 9 清楚 / **2 权利不明**（yang、ritual-paper-lead），正式故事 **0**；未制作或发行真实合规子集。

## 尚未做与已知限制

**无真实生产私钥/正式根、真实人工出版批准、GitHub Tag/Release/附件上传；无手机/ADB/安装/合并。** `app/src/main`、真实杨慎正文与审批前后哈希相同；最后已验收手机仍 code19，本轮未读取其20份历史/设置，旧 settings.xml 漂移 UNKNOWN 保留。App Provider/模型配置未改，本轮不验在线账号。

办公室↔家庭两实体电脑 **PENDING**；此处是单机两虚拟本机身份与 fake gh。此工作环境没有 PATH 中的 gh，真实首次运行需安装/授权及用户另行授权的身份设置/登记；不借用其他应用的凭据。手机 JCA、公钥固化、下载/导入/原子安装与持久防回滚均属 V0.6.1，未施工。

stdin 只证明该行决定绑定哪些字节，不能证明输入者身份，宿主必须转录真实用户决定。同县不同序号并发没有全局锁，**不要两机同时发布同县**；不声称分布式线性化。历史读取达到1000条或记录不完整即停止。gh 内部不是单次服务端事务；上传开始后异常返回 `release_outcome_unknown_check_remote` 和原因，先只读核查草稿/Tag/实际发布，程序不自动重试/删除，也不假称已发。

最近实现：`ce750b8` 生产入口；`577dba3` 实际目录 ACL；`4f7c019` 默认 stdin；`d0f73c0` 全 Claim 预览；`52188e8` PowerShell 5/7 中文管道。签名与发布代码仅在 JVM 工具/测试，未进 APK。

**STOP 边界**：完成 Draft PR、证据与交接后等独立工程复审/用户授权合并；不做真实初始化或出版，不处理 PR29/旧外场 Issue，不启动 V0.6.1。首次设备安全设置另行授权，此后合规内容每份只由用户看一次，无额外签名/上传/PR内容二审。

---

# 2026-10-17 下一施工阶段：D2 正式发布能力最小工程（仅模拟授权）

D2 的 TEST_ONLY [PR #41](https://github.com/shimao1115/KITT/pull/41) 已合并到 main（merge `baea614ba02f1aad7776e65ed6766833ee451b3f`，工程 1172 项系上一轮证据，非本轮测试成绩）。现有的 D2 演练确认、预览、内容门禁与多测试身份均已实现，但真实生产身份和 GitHub Release 上传仍未启用。

**最新施工许可**：[Issue #42](https://github.com/shimao1115/KITT/issues/42)，分支 `feat/v060-d2-production-release` 的最新 [TASK_CARD.md](TASK_CARD.md) 是唯一任务卡：**只开发和模拟**“Agent 在任一电脑制作 → 用户一次审核确认 → 程序自动校验/签名/验签/上传”所缺失的最薄正式发布能力。**本轮不生成真实生产密钥、不建真实生产根、不创建或修改 Tag/Release、不实际上传、不开手机/V0.6.1、不中途合并**；使用 fake gh 和临时模拟密钥覆盖正反例，交付 Draft PR、证据、独立工程复审后 STOP。真实新都来源权利与杨慎 Visitor 仍 BLOCKED，正式故事 0；下方旧“PR41 Draft/未合并”是当时历史，不代表现状。

---

# 2026-10-17 D2 最新任务：任一电脑制作、一次确认、当前电脑发布准备 — 工程完成 / 待独立复审

唯一任务 [Issue #40](https://github.com/shimao1115/KITT/issues/40)，分支 `feat/v060-d2-dual-workstation-release`。已合入用户更新的任务卡（远端 `037d13b`）：取消专用签名机、重复 Visitor 人工签字、PR 内容二审和第二次上传确认。**日常出版是用户看一次正文及来源说明，然后当前电脑自动检查/签名/验签/上传；本轮仅 TEST_ONLY 模拟，不是真实出版。** D1 已合并 main（`7637755`）；下方旧 D1 STOP/未合并文字仅属历史。

**实际交付：[Draft PR #41](https://github.com/shimao1115/KITT/pull/41)，OPEN / DRAFT / 未合并。STOP 等独立工程复审。**

## 本轮交付

复用原 `PackBuilder`、KCPK v1、`ReleaseVerifier` 与 `D0CountyGate`，只补 `prepare / release-preview / release-dry-run` 和 PowerShell 薄入口。无密钥 `prepare` 从固定 Git blob 生成四块、待签名区、清单及 `review.md`；`release-dry-run` 接收一次 **SIMULATED_TEST_ONLY** 决定及待签名区摘要，自动检查、签名、验签、生成不可直接执行的 gh 参数模板。没有第二次人工 Gate；`ready_to_publish=false / upload_performed=false / real_human_approval_recorded=false`，不能把模拟当真人已批准。生产 CLI 保持关闭。

办公室/家庭未来各自保存独立私钥与不同 key_id，复用 D1 多公钥 TrustStore，不通过 GitHub 传私钥、不转交给另一台签名机。首次正式身份初始化与真实发布仍需用户单独授权，此后每个合格内容包只看一次。操作见 [一页说明](docs/COUNTY_PACK_D2_WORKFLOW.md)。

**合法子集**：Agent 可在新的已提交目录中明确剔除不合格事实/Claim/故事及依赖，再交给原门禁；不自动改原始权利或审批。测试中原全量继续拒绝，显式合规子集得到 2 事实/1 来源/0 Claim/0 故事；这是合成夹具，**没有制作或发布真实新都合规子集**。

## 最终真实验证（旧 1162 / D1 1134 均不计入）

- **Debug 586 + Release 573 + packtool 13 = 1172 项**，0 失败/错误/跳过；D2 19 例、D1 27 例在两 Variant 都执行。Python 内容门禁 7 项、六组 Fake 模拟、Debug/Release assemble/lint 均 PASS。本机工程证据，不是外部 CI 或真机验收。
- APK 原签名保护 **2 接受 + 6 拒绝** PASS；仅将现有 code20 runtimecheck APK 作静态夹具，没有新建 runtimecheck 候选或安装手机。
- CLI 正例：Fact-only 4 事实/2 来源/7 Claim/0 故事，合成含故事样例 2 篇；一次决定后自动签名/验签/发布准备模拟成功。篡改、错县、错版本/序号、未知钥、PRODUCTION 冒充、未确认、确认摘要变化均拒绝。权利/待审/缺 Claim/Source 负例及两个独立本机测试身份在专项中通过。
- 两个**真正独立项目目录**（显式 `--project-dir`）从同一提交 `39475a510bd62f0dc3f1c1603deaf88d3737ab64` 各自编译并无密钥预制，四块与完整待签名区逐字节相同：内容摘要 `6d9e0f9c122abd9699bd03e6d4c024e564478b88ddd64dd00cc80b819da8bcb2`；待签名区 **10,049 B**、SHA256 `d78ac2ca315b899227fa0d3f23684b985ac672c6a178647c016d4ccda6e70614`。本轮此项不比较签名成品；不同本机 key_id/私钥的签名成品本就不要求相同。两个临时 worktree 已清理，原工作树与哨兵完好。
- `app/src/main` 与 `content/yangshen` 相对 D1 零差异，14 个受保护文件哈希入证据。初次编译兼容修复、专项 helper 修正、操作示例误填空故事版本而被正确拒绝的尝试均保留说明，未计入最终通过。

完整去敏证据：[v060-d2-verification.json](docs/evidence/v060-d2-verification.json)。功能实现 `70a9f8e`；CLI 和工作树对照基于 `39475a5`。证据生成器检查 XML 新鲜度、日志哈希、跨入口同提交/同摘要、受保护文件与路径泄漏。

## 现实边界与 STOP

**办公室↔家庭两台实体电脑实测 PENDING**；目前只有单机两工作树和两独立测试身份。真实新都全量仍 **BLOCKED：11 来源中 2 权利不明**；杨慎 `candidate / visitor_gate=pending`，正式故事 **0**。没有真实人工出版确认、生产私钥/正式可信根、Tag/Release/上传；本机 gh 上传仅参数模板，未执行。手机仍为最后已知 code19，本轮未访问设备/ADB/安装、未重读历史；旧 settings.xml 漂移根因 UNKNOWN 保留。真机 JCA、手机公钥固化/下载/导入/原子安装/持久序号仍留 V0.6.1。

本轮以独立 **Draft PR** 交付后 STOP 等工程复审；不合并、不实际发布、不操作手机、不合并 PR29、不扩展 V0.6.1。工程复审不是为日常出版重新增加一次人工审稿。

---

# D1 最终工程复审与用户合并授权

2026-10-17 D1 第二轮独立工程复审 PASS（PR #39 head 15894f5；评审 https://github.com/shimao1115/KITT/pull/39#issuecomment-6233819737）。用户已明确批准仅做最终文档收口及 PR #39 合并 main；此段是合并前授权与审查状态，不预先宣称合并成功。测试依据提交的工程证据1134项零失败，ChatGPT未实跑Gradle；生产代码 app/src/main 零改动。仍无生产密钥、Release、正式新都可发行故事、手机JCA验收、D2授权；sequence只是调用方下限，非持久防回滚。旧本机路径留在Git历史，未授权不得重写。实际合并状态以GitHub PR merged及merge commit为准。

---

# 2026-10-17 V0.6.0 D1 评审修复轮 — Draft PR #39 仍待复审（STOP）

**触发**：[PR #39 首轮独立评审](https://github.com/shimao1115/KITT/pull/39#issuecomment-6233095568) 要求三项最小修复并对防回滚边界作明确交代。本轮只做这些修复与对应验证，**未合并、未操作手机、未发布、未生成生产密钥、未开 D2**。

## 三项修复

1. **外层/内层绑定**（评审第 1 项，`InnerBinding.kt`）：签名与分块摘要通过后，逐字段比对签名外层 `area_id / parent_id / fact_version / narrative_version / minimum_code / pack_id` 与内层两份 manifest 及两份正文，不一致即 `inner_binding`（detail 指出具体字段）。此前“用 420502 的合法块重签成外层 510114”会整体 `Accepted`；现在被拒。新增 4 个负例（别县、改 fact 版本、改最低 App 版本、别县 Narrative 拼接改为由内层绑定先拒）。
2. **证据脱敏**（评审第 2 项）：`scripts/record-v060-d1-evidence.py` 内置 `scrub()`，运行时把家目录/临时目录/复现工作树替换为 `<HOME>`、`<TEMP>`、`<REPRO_WORKTREE_n>`，并对成品断言不含盘符路径或 `AppData`。本轮 JSON 已重新生成，旧 JSON 中的本机路径仅存在于 Git 历史（**未获授权，不改写历史、不 force-push**）。
3. **复现脚本安全**（评审第 3 项）：`scripts/d1-reproduce.ps1` 改为每次随机目录名；目标目录已存在即 STOP、绝不删除；`finally` 只清理本轮创建且 `git worktree list` 仍登记的工作树。新增哨兵测试：预置 `kitt-d1-repro-guard-1/sentinel.txt` 后运行，脚本 STOP 且哨兵文件完好（证据 `reproducibility_guard`）。

## 防回滚边界（评审“另一个必须如实界定的后续门禁”）

manifest 增加单调 `sequence`；验签器接收调用方 `minimumSequence`，低于即 `sequence_rollback`。这是任务卡要求的**最薄**检查，**不等于**完整防回滚：D1 没有持久安装序号、没有版本库、没有撤销中心；伪造更高序号仍需可信锚保护。持久序列与撤销明确留在 V0.6.1/D2，契约与验收文档均已按此修订，不再宣称“序列回放已完整实现”。

## 本轮真实测试（旧 1124 项不继承）

- **Debug 567 + Release 554 + packtool 13 = 1134 项**，0 失败/错误/跳过；D1 专项 `ReleaseD1Test` **27 例**在两 Variant 均执行。
- **两干净 worktree**（随机目录、各自构建）：unsigned `6d9e0f9c122abd9699bd03e6d4c024e564478b88ddd64dd00cc80b819da8bcb2`、整包 `cf283c6f28ef7a16ac8868c89894450aee2f649b6a09e9a67a104ccb9020a263`、10,468 B，两次相同。
- **CLI**：fact-only `ACCEPTED_TEST_ONLY`（4 事实/2 来源/7 Claim/0 故事，`sequence=1`）、含故事 2 篇；篡改→`bad_signature`；App code 19→`minimum_code`；序号 3 对下限 4→`sequence_rollback`；同块重签别县→`inner_binding`；真实新都审计仍 `BLOCKED`（2 来源权利不明）。
- **签名回归** 2 接受 + 6 拒绝 PASS；**Python 内容门禁** 7 项 OK，`--check` 仍 16 Claim / 0 可发布故事 / 2 排除；构建与 lint 通过；`app/src/main` 自 D0 起零改动。

## 未变

无生产私钥/官方公钥/Release/第二仓库；验签库仍只在 JVM 与单测，未进 APK；真机 JCA PENDING；真实新都对外发行 BLOCKED；杨慎两篇仍 `candidate / visitor_gate=pending`，正式完整故事 0。

**STOP**：Draft PR #39 保持未合并，等待再次复审与用户验收。

---

# 2026-10-17 V0.6.0 D1 首轮工程交付 — Draft PR #39 待独立复审（STOP）

**唯一任务**：[Issue #38](https://github.com/shimao1115/KITT/issues/38)；分支 `feat/v060-d1-signed-county-packs`；交付 [Draft PR #39](https://github.com/shimao1115/KITT/pull/39)（**未合并**）。基线为已合并 D0（[PR #37](https://github.com/shimao1115/KITT/pull/37)，merge `1330574d15deb55bc8881783c55e889ddf77ddf0`）。实现提交 `a69ccfe4`，证据在 `dad8282e` 之后生成；本轮**不操作手机、不安装/发布 APK、不创建 Release 或第二仓库、不审批故事、不合并 PR、不启动 D2**。

## 交付了什么

- 新增纯 JVM 模块 `:packtool`（**不进 APK**）：KCPK v1 固定字节帧容器（非 ZIP）、canonical manifest、分块与整包摘要、`SHA256withRSA`(PKCS#1 v1.5) 签名、调用方预置 `key_id→公钥+scope` 可信锚、以及“先签名后 D0 内容门禁”的验签器。
- CLI（`app/src/test` + `:app:d1Pack` Gradle JavaExec，跑在单测 classpath 上，因此签名代码零进入生产包）：`keygen / sample / build / verify / tamper / audit-newdu`。
- D0 门禁适配 `D0CountyGate`：在 D0 阅读路径之上更严格——发行候选里任何被 blocked 的事实或故事都拒绝整包，`fact_only` 必须如实报 0 篇。
- 确定性 TEST_ONLY 样例 `tools/d1/sample/{fact-only,with-story}`（湖北宜昌西陵行政标签，内容全部合成）；`.gitattributes` 对 `tools/d1/**` 加 `-text`，防止 Windows 检出把 LF 改 CRLF 而改哈希。
- 契约与验收：[COUNTY_PACK_D1_SIGNING_CONTRACT.md](docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md)、[ACCEPTANCE_COUNTY_PACK_D1.md](docs/ACCEPTANCE_COUNTY_PACK_D1.md)、[证据](docs/evidence/v060-d1-verification.json)、`scripts/d1-reproduce.ps1`、`scripts/record-v060-d1-evidence.py`。

## 本轮真实验证（旧 D0 的 1067 项不作本轮成绩）

- **Debug 563 + Release 550 = 1113** 项应用单元测试，加 **packtool 11** 项 = **1124 项**，0 失败/错误/跳过；XML 全部为本轮重跑且经 mtime 新鲜度断言。D1 专项 `ReleaseD1Test` **23 例在 Debug/Release 两个 Variant 都执行**。
- **两干净目录字节复现**：从同一提交建两个 `git worktree`，各自用同一 TEST_ONLY 密钥构建 fact-only 包——unsigned `6d9e0f9c122abd9699bd03e6d4c024e564478b88ddd64dd00cc80b819da8bcb2`、整包 `5046cd93c395df34c7181689078dcb086561ac0478301e8e57f41ea5ed3491b2`、10,455 B，两次完全相同（PKCS#1 v1.5 对固定密钥确定性）。
- **CLI 真实返回**：`ACCEPTED_TEST_ONLY`（4 事实/2 来源/7 Claim/**0 故事**）、含故事样例 `2 故事`；篡改一字节 → `REJECTED bad_signature`；旧 App code 19 → `REJECTED minimum_code`；真实新都审计 → `BLOCKED external_redistribution_rights_unverified`（11 来源中 9 许可明确、2 不明：`yang`、`ritual-paper-lead`，正式故事 0）。
- **签名回归** 2 接受 + 6 拒绝 PASS（真实 keytool/apksigner 门禁，含“只有公钥证书的 keystore”拒绝）；Python 内容门禁 7 项 OK，`--check` 报 16 Claim / 0 可发布故事 / 2 排除 / acceptance_candidate=False。
- **生产代码零改动**：`git diff 1330574 -- app/src/main` 为空，未重建/未安装 APK；`app/src/main/assets/offline` 与 `content/yangshen` 14 个受保护文件哈希入证据。

## 边界与未完成

- **没有生产私钥/官方公钥/信任根**；TEST_ONLY 密钥只存在于工作树之外的临时目录，未入 Git、未打印；**未复用 Android APK 签名证书**。生产密钥仪式、锚固化、轮换与撤销执行属 D2。
- 验签库**未接入 APK**（D1 无手机侧路径），下载/导入/原子启用/回滚属 V0.6.1；真机 JCA 可用性 **PENDING**（本轮只有 JDK 17 的 JCA 与 JVM 单测，未上设备）。
- 真实新都对外发行仍 **BLOCKED**（2 个来源权利不明），D1 只产出 TEST_ONLY 合成样板；杨慎两篇仍 `candidate / fact_gate=passed / visitor_gate=pending`，正式完整故事 **0**；PR #29、#23/#24/#27/#30、`settings.xml` 漂移根因 UNKNOWN 均不变。
- 首次 Gradle/IPC 异常、`settings.xml` 字节漂移等旧 UNKNOWN 不随 D1 消失；D1 未发布 Tag/Release/附件，未创建 KITT-PACKS。

**STOP**：Draft PR #39 保持未合并，等待独立工程复审与用户验收；不擅自合并、不上手机、不开 D2。

---

# 2026-10-17 V0.6.0 D0/code20 最终工程复审 PASS — 用户已批准文档收口与 PR #37 合并

**最新授权**：用户对“D0 工程复审通过后，文档收口并将 [PR #37](https://github.com/shimao1115/KITT/pull/37) 合并 main，暂不升级手机”的方案明确回复**批准**。本任务仅执行去敏文档整理与合并操作，不重跑测试、不访问/安装手机、不创建Release或KITT-PACKS仓库、不审批故事、不启动D1/D2或V0.6.1。**合并是否已执行请查询PR #37 的 GitHub merged 状态和实际merge commit；本段记录的是授权及门禁，不预先宣称成功。**

## D0 工程验收及证据封口

- **独立代码复审 PASS**：[首轮评审](https://github.com/shimao1115/KITT/pull/37#issuecomment-6229804921) 指出的县→市州代码归属和来源/Claim许可黑名单两处漏洞均经实现提交 dc764651 最小修复；[第二轮复审 PASS](https://github.com/shimao1115/KITT/pull/37#issuecomment-6231174949)。县与父市州普通代码一致性、特殊层级暂拒；权利字段完整字符串显式允许表、权限允许不能覆盖受限声明；专项反例重新计算源/Claim/故事引用和摘要，确认是权限判断而非旧哈希错误。
- **Qoder 的真实本轮工程测试证据**：Debug **540/79套件**、Release **527/77套件**，**1067项**、0失败/错误/跳过，Python 7、六组确定性Fake模拟、Android构建与lint（标准各6个既有warning/零错误）、原签名**2接受＋6拒绝**通过；干净目录runtimecheck 75/75任务。首次Gradle/IPC/Release进程异常未被算作PASS；后来以 --rerun-tasks --max-workers=1 --no-daemon 真正115/115任务完整成功，IPC确切根因 UNKNOWN。GitHub该SHA没有独立CI Actions运行；独立审阅仅核对源码与已提交日志，并未从本会话直接执行这些测试。
- **本地未安装的 code20 候选**：com.kitt.reader.runtimecheck，versionName 0.6.0 / versionCode 20，**74,461,274 bytes**，SHA256 **2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60**，原证书 SHA256 3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba。该哈希来自Qoder提交的本地证据；本会话未取得 APK 原始字节复核。**手机继续使用 code19**；code19→20仅静态兼容、不代表真机升级成功。
- **结构性验收**：新都内置1县/6对象/22基础事实/6基础来源、不改原资料与可信GPS离线路径；县级 Fact + Narrative 独立Manifest、独立版本、Claim修订/来源审核与地区隔离通过工程验证；宜昌西陵第二县与6篇主故事+1篇延伸仅隔离的 TEST_ONLY 内存夹具，APK不可访问；通用县只读视图没有物理GPS几何。默认Loader拒绝未经验证外部输入，签名发布与GitHub Release尚未实施。
- **真实性保留**：杨慎两篇仍 candidate/fact_gate=passed/visitor_gate=pending，标准正式故事 **0篇 / 发布 BLOCKED**；settings.xml 首次打开原设置页字节漂移根因 UNKNOWN；账号在线有效性及手机真机 D0、外场#23/#24/#27/#30 PENDING，离线/即时模式 DEFERRED。此次无新手机真实历史/设置测试，旧20份真实历史未触碰；用户延后的两个保存按钮间距UI问题不在本D0修复范围。

**最新 D0 交付来源**：[Issue #36](https://github.com/shimao1115/KITT/issues/36)、[PR #37](https://github.com/shimao1115/KITT/pull/37)、[R1 证据](docs/evidence/v060-d0-verification.json)、[协议](docs/COUNTY_PACK_D0_CONTRACT.md)、[工程验收步骤](docs/ACCEPTANCE_COUNTY_PACK_D0.md)。正式V0.6.0整体发行闭环仍未完成：可信发行者/独立签名、可复现双包成品、受控 GitHub Release、下载/导入/原子安装均需后续分别授权，不可因为D0合并自行开工。

**最终 STOP**：文档更新与用户明确批准的 PR #37 GitHub 合并完成后，核验main与PR状态即停止；不擅自操作其他分支、手机、APK、故事审批或开始下一阶段。下方“工程待复审、不能合并”是过往阶段的交接历史，不再代表最新许可。

---

# 2026-10-17 V0.6.0 D0 / code20 评审修正交付 — STOP 待再次验收

[Issue #36](https://github.com/shimao1115/KITT/issues/36) 的第一条县级双包读取黄金路径已完成，交付 [Draft PR #37](https://github.com/shimao1115/KITT/pull/37)，实现提交 `91b96a8ecc711e663007a1c01d406d1afee8a607`。仅本 D0 工程完成，主干/手机仍以已验收 code19 为基线；未合并、未安装、未发布，不代表整个 V0.6.0 发行完成。

## 本次两处最小修正

针对 [独立工程评审评论](https://github.com/shimao1115/KITT/pull/37#issuecomment-6229804921) 的两个验证缺口，实现提交 `dc76465`；相对受审 `9f3ada2`，唯一生产文件改动为 `CountyPacks.kt`。

- 普通县级 `area_id` 与父市州 `parent_id` 必须满足六位码前四位一致、父级末两位00；00/90特殊层级暂不支持，拒绝而不虚构父市州。重封摘要/签名后的跨市、跨省错误归属及特殊层级均有拒绝用例。
- 来源和Claim权利改为有限、完整字符串允许表，仅做首尾空格/大小写规范化。保留既有明确的事实自行概括/古籍范围，不授权复制受保护的来源表达；未知、restricted、all rights reserved、混合许可及中文禁转载均拒绝，`permissions=allowed`不能覆盖它。新用例同步重算source registry、accepted source revision、Claim revision、Narrative accepted revision以及包摘要/测试签名，证明拒绝来自权利门禁；无关篇章及独立事实仍可用。明确CC-BY-4.0及既有已明确范围的正例通过。

新增六项用例，每种Variant全部执行；未改原正文、资产、审批、账号、设置、UI或D0边界。独立工程**复审PENDING**，不将本次施工自测称为独立审核通过。

## 当前可运行能力与范围

- `CountyPackLoader` 接收独立 Fact/Narrative 原始 bytes、各自 manifest/版本、调用方整体摘要与 verifier 来源标签；县ID、父市州目录、Entity/Source/Claim/Story ID、版权权限、具体接受修订、Fact/Visitor Gate 均验证。默认拒绝输入，哈希不产生身份信任。生产唯一入口为固定 APK 内置资产适配；测试 RSA verifier/临时密钥仅在 src/test。
- `ExploreCatalog.load` 与 `KittRuntime` 已接入县级目录的兼容层。真实新都仍为 **1县 / 6对象 / 22基础事实 / 6基础来源 / 0正式故事**；内存索引另保留16条杨慎Claim及11个原研究来源登记（含未采纳项），与6个基础来源用前缀隔离，共17登记来源。原可信几何、离线事实自动讲述、重播、六类兴趣、Journey/TripStore/账号路径保持；普通探索无GPS/无云/无登录。
- 通用模型可接收多实体、多main；只有逐篇审核和依赖合格者进入集合，再按兴趣软排序。测试 Story/Claim 记忆按县ID限定，同Claim换Story ID不再自动重讲。旧杨慎专用解析器的 `<=1 main` 安全限制保留。
- 第二县仅使用湖北宜昌西陵区行政标签，具体人物/对象/Claim/来源/故事均为 **TEST_ONLY 合成**，与新都测试标签不相邻。6篇main+1篇followup只在内存测试中验证多县隔离、六类偏置及资格门禁。APK资产和DEX实查均无第二县夹具/单测类；通用只读县视图无几何，不能制造GPS触发。
- 错地区、重复/悬空ID、格式/大小/Schema/App code、整体/分包摘要与未受信输入拒绝；篇级审核、来源权限和Claim撤回/修订失配只停用实际引用篇章，基础事实独立可用时显示“仅基础事实”。详见[契约与迁移](docs/COUNTY_PACK_D0_CONTRACT.md)。

## 本轮R1真实验证（旧1055项仅历史，不累计）

| 检查 | 结果 |
| --- | --- |
| Debug全量 | **540**，79套件，失败/错误/跳过均0 |
| Release全量 | **527**，77套件，失败/错误/跳过均0 |
| 本轮合计 | **1067**；本次新增6项，两Variant均执行；旧1055及本次不完整失败尝试均不计入 |
| 内容与发布门禁 | Python **7项 PASS**；标准 `--check` 为0合格故事；`--require-playable` **BLOCKED/exit1**；候选无独立输出目录拒绝/exit2 |
| 六组模拟 | 成都新都→安州雎水29点粗路线，full/accelerated/batch/landmark/dossier/driving-area-resolution 全PASS；确定性/Fake Provider及fake Voice，不是实车/真模型/真人耳听 |
| assemble/lint | 标准Debug/Release成功，lint各6 warnings/0 errors；最终完整重跑使用 `--rerun-tasks --max-workers=1 --no-daemon`，**18分34秒 / 115任务全部执行**；随后verify归档检查17秒（2执行/113复用），不重复计数 |
| 独立干净目录 | runtimecheck Debug/AndroidTest/lint成功，**75/75任务实际执行**；原code19工作目录及中止的D0目录完整保留在本地忽略的artifacts，最终目录从不存在状态建立 |
| 签名与权限 | **2接受+6拒绝 PASS**；所有候选均原证书；本地code19候选重新验签/核哈希，code19→20同包同证书递增仅静态兼容；权限逐项一致 |
| 数据/资产隔离 | content、全部main/assets、AndroidManifest、SettingsStore、TripStore、StoryInterestStore、DirectorLoop与施工基线字节不变；读取器专项及既有UI/持久化回归使用合成设置/20份合成历史；真实手机历史未访问 |

[本轮完整证据](docs/evidence/v060-d0-verification.json) 包含逐套测试名/计数/新鲜报告哈希、六组报告摘要/哈希、源码哈希、APK内容、签名及日志摘要。`scripts/record-v060-evidence.py` 已实际执行通过：对照本轮开始标记检查真实任务执行和报告新鲜度，对照独立构建源码；runtimecheck预期的包名/应用label差异被单独核对，不忽略其它差异。本次首轮13分50秒因Gradle本机loopback IPC连接失败及Release Test Executor退出而失败，未取得完整Release XML；日志及当时Debug XML已保留在忽略的 `artifacts/v060-r1-failed-worker-*`。随后未改源代码，以串行独立进程强制完整重跑成功；全部最终XML晚于该次重跑开始标记。证据保留失败原因、时间和日志哈希，失败尝试未累计；不推断IPC问题的确定根因。原D0初交1055项/旧哈希保留在 `9f3ada2` 和本地R0备份，仅为历史。

## 本地候选（未上传/未安装）

全部 versionName `0.6.0` / versionCode `20`；唯一证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

| 本地文件 | 包名 | 字节数 / SHA256 |
| --- | --- | --- |
| `artifacts/kitt-v0.6.0-runtimecheck.apk` | com.kitt.reader.runtimecheck | 74461274 / `2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60` |
| `artifacts/kitt-v0.6.0-debug.apk` | com.kitt.reader | 74461250 / `5b47c4a0809a5abf2ef2c0b6af2afc15a2ebed2660068a236a17a8613095f6a4` |
| `artifacts/kitt-v0.6.0-release.apk` | com.kitt.reader | 70790206 / `8aae53348a378987e6a2924dfb9105f0cefde0c9aa9d9a5bacadef72de214fa4` |

配套AndroidTest仅构建/验签，未执行设备instrumentation。候选未入Git，无GitHub Release或APK上传。

## 已知限制、配置与最短人工验收

正式完整故事仍 **0**；杨慎两篇正文/approval/Claim/manifest原样，仍 `candidate / fact_gate=passed / visitor_gate=pending`。测试通过不等于游客审批、真人耳听或真实道路全链通过。新县尚无生产几何/正式内容，D0不宣称全国县域可用。每包500000 bytes、索引200项、Journey marker总长240字符等为本版安全上限，超限拒绝或阻断篇章；未来发行格式/大引用集合应单独审查。

真实发行者信任根、公钥治理、签名成包、GitHub Release、下载/文件导入/原子安装/Wi-Fi更新、真实六区调查都未实现。市州只聚合县ID，不复制正文。PR #29及 #23/#24/#27/#30状态未触碰。旧 `settings.xml` 首进设置页字节漂移根因仍 **UNKNOWN**，云账号在线有效性及原外场Gate仍PENDING，离线/即时模式仍DEFERRED；用户延期的设置UI按钮间距未改。

默认Provider/模型保持 `FAKE / gpt-4.1-mini`，无新增OAuth/密钥步骤。本轮无真实Provider/研究网络请求；没有访问手机、账号原始记录、真实历史或设备设置。

最短验收：审 [PR #37](https://github.com/shimao1115/KITT/pull/37) → 对照[证据](docs/evidence/v060-d0-verification.json)与[验收说明](docs/ACCEPTANCE_COUNTY_PACK_D0.md) → 核对[协议与隔离边界](docs/COUNTY_PACK_D0_CONTRACT.md)。当前需独立工程复审及用户再次审阅；手机升级、合并及内容正式发布仍需各自独立授权。

最近milestone：`dc76465` 两处验证修正/6专项；`9f3ada2` 初交工程证据；`91b96a8` D0实现/23专项/协议；`edab16f` D0任务文档基线；`53c0e0f` 已合并code19主干基线。**STOP：仅交付Draft PR #37，等待工程验收；不启动后续里程碑。**

---

# 2026-10-17 V0.5.2/code19 最终人工验收与合并收口（用户明确批准）

**人工体验：PASS。** 在 Qoder 唯一一次 code18→19 原签名保数据升级、六类兴趣实机辅助验收后，用户亲手检查已装的 vivo X200 Pro / `com.kitt.reader.runtimecheck` code19，并回复：**“都正常，就是‘保存讲述兴趣’ 和 ‘保存’两个按钮挨的太近，图标就要连在一起了，不过这个是小问题，暂时不修改，以后优化UI的时候一起修改”**。人工认可范围为当前 code19 的设置与兴趣体验；**并不等于** ChatGPT 在线请求、原设置完整语义等价、正式多类故事真实排序、冷进程重启、平板或者真实 GPS 全链已验收。用户随后再次明确**“批准”本次文档收口及 PR #35 正式合并至 main**；实际合并状态/merge SHA 始终以 [PR #35](https://github.com/shimao1115/KITT/pull/35) 为准。

## 本次阶段验收与遗留事项

| 项目 | 最终阶段判定 |
| --- | --- |
| 工程 | **PASS**：Qoder 在实现提交 `d9d9b41` 对 Debug511 + Release498 = **1009项**实际重跑，0失败/错误/跳过，Python7、六组Fake模拟、构建/lint及签名回归均有真实证据；这次最终文档收口**未重跑**。 |
| 原签名一次升级 | **PASS**：唯一 vivo X200 Pro 上 code18→19 一次 `adb -d install -r`、同包同证书、已装APK回读一致；本次文档收口**不操作手机/不重装**。 |
| 六类偏好 | **设备辅助PASS + 用户亲手体验PASS**：六类三档初始全 NORMAL；明确一次保存 `HISTORY=MORE`、`ENGINEERING=LESS`、其余 NORMAL，独立 `story_interests.xml` 生效、重新进入后仍在，未保存选择不落盘；辅助验收没有执行**冷进程重启**，不对此额外声称 PASS。 |
| 旧历史与配置 | **保留PASS（既有证据）**：20/20份真实历史三阶段逐文件 SHA256不变，六项非敏感设置升级前后值一致、账号加密记录存在且非空；不代表账号解密与在线调用已证实。 |
| 设置字节漂移 | **UNKNOWN（未解决风险）**：`settings.xml` 升级前/刚升级后哈希相同，首进设置页从 `4c267ca3…` 改为 `269dc8d6…`，兴趣编辑与独立保存后未再次变化；可确认**不是本次兴趣保存引发的写入**，但具体变化字段/根因和升级前后全量语义等价仍未知，不宣称已修复或账号损坏。 |
| 内容正式发行 | **BLOCKED**：正式审核完整故事 **0篇**；杨慎主/延伸 `candidate / fact_gate=passed / visitor_gate=pending` 原样；“六类兴趣改变实际已审核多类故事播报次序”仍 PENDING（仅内存 TEST_ONLY 夹具测试）。 |
| 来源双模式 | **DEFERRED**：即时优先/离线优先自动切换尚未实现；无需在此版冒险改动云端路由与失败回退。 |
| 历史外场余项 | [#23](https://github.com/shimao1115/KITT/issues/23)、[#24](https://github.com/shimao1115/KITT/issues/24)、[#27](https://github.com/shimao1115/KITT/issues/27)、[#30](https://github.com/shimao1115/KITT/issues/30) **保持 OPEN**，真实GPS/TTS/侧滑/Tab S9 等仅按已测范围验收，不据本次合并自动关闭。 |

**明确延期的两个 UI 细节（非阻断、不得在当前版本修复/重装）**：①「保存讲述兴趣」与底部旧「保存」按钮过近，分组/间距不足，这是**用户亲自确认**的问题；以后集中优化设置界面时增加明显间隔、区块分隔或操作提示。② 在成功保存之后若继续点击某类 Chip，原“已保存讲述兴趣”成功提示可能残留而造成误解，这是**源码评审提示，尚非用户亲测确认**；同次 UI 优化一起回归即可。**用户明确要求本版不修改**，不能以此扩大施工。

**证据链接**：[工程证据](docs/evidence/v052-verification.json)、[本次真实手机证据](docs/evidence/v052-phone-acceptance.json)、[人工体验记录](https://github.com/shimao1115/KITT/pull/35#issuecomment-6220432321)、[正式工程评审](https://github.com/shimao1115/KITT/pull/35#issuecomment-6219618938)、[验收指导](docs/ACCEPTANCE_STORY_INTERESTS.md)。

**本次最终执行边界**：用户已明确批准仅将 V0.5.2 文档收口并在最终合并门禁 PASS 时**合并 PR #35 到 main**。本次仅修订 AGENTS/HANDOFF/任务入口等文字，不修改生产 Kotlin、内容 Story/Claim/approval/manifest、已有设备/工程证据、APK或证书，不重复测试、安装、操作真实设备，也不提前启动 V0.5.3 / V0.6.x。合并成功后 STOP，下一版本独立授权。下方历史 HANDOFF 按其记录当时的 STOP/PENDING 解读；本段优先于之前的“用户亲手体验仍PENDING”或“合并未批准”等历史文字。

---

# 2026-10-17 V0.5.2/code19 — 一次原签名真机升级完成 / 六类兴趣真机 PASS / STOP 待用户亲手验收

**用户批准的唯一一次 code18→code19 原签名保数据原位升级已执行完毕并成功。手机现为 `com.kitt.reader.runtimecheck` 0.5.2/code19。六类讲述兴趣的默认值、独立保存与重进持久化在真机 PASS；20/20 真实旅程历史逐文件哈希安装前后完全相同；普通设置与账号加密记录仍在。唯一升级许可已用尽，不得二次安装。见[本次真机证据](docs/evidence/v052-phone-acceptance.json)。工程 1009 项仍是此前 Qoder 实跑结果，本轮未重跑。**

## 前置核查（全部 PASS 后才装）

- worktree 干净、单一 worktree；`fetch` 后快进到授权提交 `0d3b291`。工程基线核实为实现提交 `d9d9b41b17961c8ed848c12218d3cfb06694ce9e`、交付 HEAD `2a331d999c4cd369ed3ae809b02031915f1a995c`；`2a331d9..0d3b291` 仅改 `TASK_CARD.md`，故所装候选仍对应已审工程提交。
- 候选 `artifacts/kitt-v0.5.2-runtimecheck.apk` **74444882 字节**、SHA256 `3af6f91a0aba318d4be58d53f0d15c7eef25632a14f2ae7caf98ecf6e08b2a4b`、包 `com.kitt.reader.runtimecheck`、`0.5.2/code19`、证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；安装前一刻再次重算哈希相符。
- 唯一 USB/ADB 目标 = vivo X200 Pro（V2405A／V2405DA，Android 16／SDK 36，1260×2800@560dpi）。已装旧包 `0.5.1/code18`，**本轮重新回读手机 APK 实测** 74976037 字节、SHA256 `8fb8b57040787ea82ffd3fb026d230698ac7ab8bde84ab29868b670b32413fea`，与 V0.5.1 历史记录一致（不再是只引用旧证据）；证书与候选同为 `3139a293…9502ba`，versionCode 18→19 单调。
- 无活动 Journey：`files/trips/current.json` 与 `current.json.new` 均不存在、包过滤服务查询为空、无本应用 TTS 播放；非驾车（凌晨 00:25、USB 供电、蜂窝数据连接状态 0）。标准生产包 `com.kitt.reader` 未安装，全程未触碰。
- 安装前只读基准：20 份真实历史逐文件 SHA256、`settings.xml` 整体哈希与非敏感字段摘要、`chatgpt` 记录“存在且非空”布尔值。原始 XML／旅程文本／账号值／设备序列号均未写盘或上传。

## 唯一一次安装

`adb -d install -r` 恰好一次（00:28:46→00:29:02，exit 0，Success）；未 uninstall、未 `pm clear`、未用 `-d/-g/-t`、未重试、未 force-stop。安装后 `versionCode=19`、签名对象与 uid(10340) 未变、`firstInstallTime=2026-10-09 09:42:45` 与数据目录标识不变，手机回读 APK 与批准候选**逐字节相同**（`3af6f91a…`）。

## 真机验收结果

| 项目 | 结果 |
| --- | --- |
| 六类与三档 | 设置页单一「讲述兴趣」区块，自然地理生态／交通与工程／人文文化社会／物产产业生活／历史与人物／旅行现场与常识 × 多讲／正常／少讲；**保存前六行实测均为中间「正常」选中**，即初值全部正常。 |
| 独立保存 | 只把 历史与人物=多讲、交通与工程=少讲，点一次「保存讲述兴趣」→ UI 回显「已保存讲述兴趣」，并新建 `shared_prefs/story_interests.xml`（329 字节，仅六键：`HISTORY=MORE / ENGINEERING=LESS / NATURE=CULTURE=LOCAL_LIFE=TRAVEL=NORMAL`）。 |
| 旧文件隔离 | **点 Chip 与点保存前后，`settings.xml` 哈希逐次相同且 mtime 未变；兴趣写入只产生新文件。**本版“不改旧设置文件”的设计在真机得到证实。 |
| 持久化 | 退出（两次返回＋Home）再重进 App 与设置页，两项选择仍在、其余四类仍正常；未点保存的改动（把自然地理生态改多讲后返回）**未落盘**，文件哈希与内容均不变。 |
| 20 份历史 | 三阶段计数均 20、均可 JSON 解析、逐文件 SHA256 清单哈希完全相同（`e7f8bd10…`），安装与全部 UI 操作未改动任何历史。 |
| 普通设置与账号 | `provider=CHATGPT / model=gpt-6.1-sol / effort=low / speech_rate=1.0 / tts_voice 空(系统默认) / separate_research=false` 升级前后逐项相同；`chatgpt` 记录存在且非空、`credential` 存在且为空、`research_credential` 不存在——**仅证存储结构，未验证解密或联网可用**。设置页仍显示原账号行、“ChatGPT 计划使用：已启用”、原「保存／返回」按钮。 |
| 布局 | 竖屏长页滚动＋返回可用；临时改横屏（随后恢复）后类别行、三档 Chip 与「保存讲述兴趣」仍可呈现并滚动，已保存选择在旋转后保留。旋转会使 MainActivity 重建并回到驾驶首页（非崩溃）。 |
| 内容门禁 | 设置页如实显示“当前没有已通过审核门禁的完整故事”；**正式完整故事仍 0**，杨慎两篇仍 `candidate / visitor_gate=pending`，未注入、未解锁、未改任何审批。 |

## 本轮唯一异常（须用户复核，根因仍 UNKNOWN）

`settings.xml` 整体哈希在 **00:30 第一次打开设置页**时由 `4c267ca3…` 变为 `269dc8d6…`：文件大小仍 5167 字节、六个非敏感值与所有敏感项存在/非空布尔值逐项相同，且此后 W3～W7（含兴趣保存与三次再进设置页）**再未变化**，mtime 一直停在 00:30。这把 code18 遗留的漂移**收窄到“首次进入设置页”这一次写入**，并证明它**不是**兴趣保存造成的。具体改了哪个键仍 **UNKNOWN**：本轮只存整体哈希，未保存原始 XML、未解密、未做逐字段差异，因此不判账号损坏、也不声称全部历史字段保留。另注：本轮 W0 哈希等于 V0.5.1 记录的漂移后终值，说明 16:36 之后未再自行变化；00:25 读到 `accelerometer_rotation=0`、00:35 读到 `1`（本轮此前未写该键），两个读数均如实保留，最终恢复为 00:25 基准。

## 保留的 PENDING 与未声称

用户本人亲手操作舒适度与可达性、真实多类正式故事被偏好改变播报次序（正式故事 0 无法观察）、设置页首进漂移的根因、账号解密与在线可用性、侧滑手势、Tab S9 实机、本版真机离线 TTS 耳听、#23/#24/#27/#30 外场，均未在本轮 PASS。本轮**未重跑**任何 Gradle／Python／模拟／构建／lint／签名回归，1009 项仍是 `d9d9b41` 的工程证据。评审提出的“保存后继续改 Chip 时旧成功提示未清除”一项，本轮未点第二次保存（授权仅一轮明确保存），故**未在真机验证**，留作后续小修。

**执行一次后 STOP。** PR #35 保持 **Draft／OPEN／未合并**，未发布 APK、未改正式故事审核、未启动 V0.5.3／V0.6.x；#23/#24/#27/#30 原状态不变。不得再次安装 code19。下一步由用户本人亲手体验并单独决定是否合并。

最近 commits：`0d3b291` docs 授权一次 code18→19 原位升级与有界验收 ← `2a331d9` docs code19 工程交接 ← `d9d9b41` feat 六类兴趣柔性排序。

---

# 2026-10-16 V0.5.2/code19 — 工程交付 / STOP 待用户工程验收

**分支 `feat/v052-story-preferences`，实现提交 `d9d9b41`，交付 [Draft PR #35](https://github.com/shimao1115/KITT/pull/35)，关联 [Issue #34](https://github.com/shimao1115/KITT/issues/34)，分支基线为已含 PR #31 合并（`9319c271`）的 main。** 本版实际完成：设置页单一「讲述兴趣」区块（六类 `StoryCategory` × 三档 多讲/正常/少讲，初值全部正常）、独立 `story_interests` 偏好文件持久化，以及在**既有资格门禁全部通过之后**的类别柔性排序接线。**手机未访问/未安装，PR 未合并，APK 未上传或发布，任何故事审批未改变。**

## 本版真正可用的能力

| 项目 | 实际状态 |
| --- | --- |
| 六类兴趣设置 | 自然地理生态／交通与工程／人文文化社会／物产产业生活／历史与人物／旅行现场与常识，各三档，**初值全部正常且彼此均衡**；重启与重新进入设置页仍保留。 |
| 独立持久化 | 新命名空间文件 `shared_prefs/story_interests.xml`，有独立的「保存讲述兴趣」动作。缺键→正常，非法/空/类型错值→正常且不崩溃；返回或离开页面不落盘。**不复用原「保存」路径**，因此 `provider/model/effort/tts_voice/speech_rate/chatgpt/credential/research_*` 任一字节都不因调兴趣而变化。 |
| 柔性排序 | 稳定软权重 多讲 > 正常 > 少讲，只重排**已经通过**区域、审批、Fact/Visitor Gate、版权、Claim 修订、可信定位、本趟已播与节奏等既有资格的候选；全正常时结果与旧行为逐字一致。少讲不禁讲，多讲不得让候选/未过门禁内容入场，`stories.count(main) <= 1` 数据门禁保留。 |
| 解耦范围 | 偏好只作用于**自动讲述**候选次序。探索阅读、手动点播、原文重播以及用户明确说出的「再讲一点/这里有什么」不按自动选题偏好重排；旧 `TopicFamily` 未映射到六类。 |
| 内容真实数量 | 标准包与本版 runtimecheck 仍 **新都区1县/6对象/22基础事实/6来源，正式完整故事0**。杨慎两篇 `candidate / fact_gate=passed / visitor_gate=pending` 的正文、Claim、approval、manifest 与标准资产逐字节未改；`--require-playable` 仍如实 BLOCKED，`--acceptance-candidate` 缺独立输出目录仍被拒。设置页显示“当前没有已通过审核门禁的完整故事”。 |

## 工程结果（本版真实重跑，不沿用旧计数）

- **Debug 511 + Release 498 = 1009 项，0 失败 / 0 错误 / 0 跳过**（77/75 个套件，逐套计数与用例名见 [工程证据](docs/evidence/v052-verification.json)）。旧 code18 的 965 项只是历史，未计入本版。
- 新增 22 项专项：六类×三档默认值、重启持久化、非法值回退、code18 旧设置无新键迁移、改一类不动另五类、`settings.xml` 逐字节与 20 份**合成**纪要前后哈希一致、未启动 Journey／未改定位／已播与调度计数不变、云调用为 0、排序只在合格候选内生效、已播不重播、越区静默、候选稿件不因“多讲历史”而可播。多类别竞争一律用**纯内存 TEST_ONLY 夹具**，未写入源码资产或 APK。
- 六组确定性/Fake 模拟 PASS（成都新都→安州雎水 29 点粗粒度轨迹，Fake Provider/fake Voice，生产 Context/Journey/Director）；`driving-area-resolution` 四项节流区间记录一致。Python 内容门禁 7 项 OK 且标准 `--check` 为 STRUCTURE PASS／0 篇合格叙事。`assembleDebug/Release`、`lintDebug/Release`（6 warnings、0 error）与 runtimecheck 独立构建均成功。
- 原签名回归 **2 接受 + 6 拒绝 PASS**；标准包与 runtimecheck 证书 SHA256 仍为 `3139a293…9502ba`。
- 本地候选（**不入 Git，仅本机**）：`artifacts/kitt-v0.5.2-runtimecheck.apk`，`com.kitt.reader.runtimecheck` **code19 / 0.5.2**，74444882 字节，SHA256 `3af6f91a0aba318d4be58d53f0d15c7eef25632a14f2ae7caf98ecf6e08b2a4b`；标准 `kitt-v0.5.2-debug.apk` 74444866 字节 `c5e0aacccb5ff792922745c034c9ec746706362838865627a0822b09274641bf`、`kitt-v0.5.2-release.apk` 70773822 字节 `ef4fd86b4d8e42359c3ed160e656cf3b913367dea9cf0a32338c8d516d3b4256`。
- **code18→code19 仅静态兼容**：同包名、同证书、versionCode 单调 18→19，故原位升级在签名层面被允许。本机没有 code18 APK 文件，基线一侧沿用已记录的`docs/evidence/v051-verification.json`手机回读哈希；**本轮未安装、未访问手机**。

## Provider/模型配置与密钥

本版全部功能不依赖任何密钥或登录：兴趣偏好与排序纯本地。应用内 `SettingsStore` 默认仍是 `FAKE / gpt-4.1-mini`，未改动读取/保存逻辑；真机现装 code18 的实际账号配置以 [V0.5.1 只读核查](docs/evidence/v051-current-settings-readonly.md) 为准，本轮没有再次读取。用户无需为本版执行任何 OAuth 或密钥步骤。

## 来源双模式（离线优先／即时优先）：**DEFERRED，未实现**

按任务卡的停止门槛核查后判定为超出版本边界，理由全部有源码依据：

1. [KittRuntime.kt:93](app/src/main/java/com/kitt/reader/KittRuntime.kt#L93) 把 `preferOffline` 硬绑为 `{ !simulation }`，即真机自动讲述**当前总是本地**；改成两档开关会把自动主链放到真实云/账号/网络路径上，而该行为只能靠活账号联网与真机才能验收，本轮两者均未获授权。
2. [DirectorLoop.kt:81](app/src/main/java/com/kitt/reader/DirectorLoop.kt#L81) 之后，自动分支的云失败只会 `deliver(null)` 静默记 `FAILURE`（[DirectorLoop.kt:177](app/src/main/java/com/kitt/reader/DirectorLoop.kt#L177)），**没有“有界退回已审本地故事”的实现**；补这段退回属于任务卡明确排除的“失败重试调度/大重构”。
3. `!preferOffline()` 还会在连通与进入新区域章节时启动后台 `research.enter(...)`（[DirectorLoop.kt:49](app/src/main/java/com/kitt/reader/DirectorLoop.kt#L49)、[:62](app/src/main/java/com/kitt/reader/DirectorLoop.kt#L62)），触及研究/缓存框架的云上行为，不在本版边界内。

因此本版**没有做只有 UI 的假开关**，也没有声称测试过即时优先的真实网络行为；建议另立独立小版本（V0.5.3 或既定 V0.7.x）专门施工与验收。

## 已知限制与保留的 PENDING

- 「用户现在就能听到六类正式故事」不成立；**正式多类内容真机播报确实受偏好影响**仍 PENDING，本版只交付可靠保存 + 已接入的可复用排序基础设施。
- Compose/Robolectric 的 411×914、914×411、1280×800、320×568+1.5 倍字体检查是合成布局证据，**不是 vivo X200 Pro / Samsung Tab S9 真实屏幕与触控**；侧滑手势、真机大字号与真实 TTS 耳听仍 PENDING。
- code18 的 `settings.xml` 整体字节漂移根因仍是 `UNKNOWN / INSUFFICIENT_BASELINE`；本版只做“新写入隔离在独立文件”的防扩散设计，没有试图修复或重新解释它。账号在线有效性仍未单独验证。
- [#23](https://github.com/shimao1115/KITT/issues/23)、[#24](https://github.com/shimao1115/KITT/issues/24)、[#27](https://github.com/shimao1115/KITT/issues/27)、[#30](https://github.com/shimao1115/KITT/issues/30) 的原 OPEN/PENDING 状态未变；PR [#29](https://github.com/shimao1115/KITT/pull/29) 县级采集分支未触碰；V0.6.x、车机、实时导航未启动。

## 用户下一次最短总验收

1. 审 [Draft PR #35](https://github.com/shimao1115/KITT/pull/35) 代码、[工程证据](docs/evidence/v052-verification.json) 与 [验收说明](docs/ACCEPTANCE_STORY_INTERESTS.md)，核对上面 code19 候选的包名、版本、大小与 SHA256。
2. 若同意升级，**另行明确授权**一次同签名原位升级；本文与验收说明都不构成安装许可。
3. 升级后先看设置页：六类都在且初值全是「正常」，账号/Provider/模型/音色/语速与升级前相同；只改历史与人物=多讲、交通与工程=少讲并点「保存讲述兴趣」，退出重进确认仍在、其余四类未变，20 份纪要原样。
4. 新都区旅程可正常跑且不出现异常讲述；**不应据此声称“偏好已让我多听某类正式故事”**，因为正式故事仍是 0。

**完成工程交付即 STOP。** 等待用户工程验收；代码合并、真机升级、故事正式发布是三件需分别明确授权的事，本轮未执行其中任何一件。

最近 milestone commits：`d9d9b41` feat 六类兴趣柔性排序（V0.5.2/code19）← `c2bf1f2` docs 归一化 Agent 说明头部 ← `f966908` docs 界定 V0.5.2 code19 任务卡 ← `9319c27` Merge PR #31（V0.5.1/code18，main）。

---

# 2026-10-16 最终阶段验收与合并交接：工程/探索 UI 接受，设置漂移根因 UNKNOWN

**用户已接受 V0.5.1/code18 工程及探索 UI 阶段，并明确批准文档同步及 PR #31 正式合并至 main。** [人工反馈](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212597341)：用户“我没有发现什么问题，都是正常的”，记 **HUMAN_UI_ACCEPTANCE=PASS**；[最终合并授权](https://github.com/shimao1115/KITT/pull/31#issuecomment-6212644800)及最新[TASK_CARD](TASK_CARD.md)覆盖下方旧 Draft/HOLD/禁止合并文字的工程合并限制。实际合并状态及 merge SHA 以[PR #31记录](https://github.com/shimao1115/KITT/pull/31)为准，不把人工体验改写成账号、故事或外场全链PASS。

| 项目 | 最终接受范围与保留限制 |
| --- | --- |
| 工程 | **Debug489 + Release476 =965项，0失败/错误/跳过**，Python7、六组模拟、assemble/lint、原签名2接受+6拒绝均为既有真实工程结果；本次文档收口没有重跑或重计。 |
| 手机升级 | 原签名code17→18唯一一次原位升级成功；许可用尽。本轮未操作手机、未重建或发布APK。 |
| 探索 | 新都1县/6对象/22事实/6来源，搜索、来源阅读、横竖屏、键盘、返回、字号与后台恢复等已有辅助检查PASS；用户当前亲手体验PASS。侧滑手势、平板实机仍PENDING。 |
| 历史 | 20/20真实历史三阶段逐文件哈希一致，原记录保留；本次仅复核本机已有证据。 |
| 设置 | 最终XML整体字节漂移确实存在，原FAIL保留；字段、根因、完整升级前后语义等价仍 **UNKNOWN / INSUFFICIENT_BASELINE**。一次只读核查确认六项普通配置匹配较早code17参考，不能补成缺失的紧邻升级前基线。当前用户无可见异常，剩余风险有记录地保留，不声称已修复。 |
| 账号 | 只证明非空加密记录存在；当前解密及在线可用性仍PENDING，人工正常显示不等于令牌刷新或真实请求成功。 |
| 故事/发行 | **正式故事仍0**；杨慎两篇 `candidate / visitor_gate=pending`，审批仍仅acceptance_candidate，发布BLOCKED。探索正式故事实机离线TTS仍PENDING；code17双篇独立人耳历史PASS保留。 |
| 外场 | #23/#24/#27实地GPS/恢复等原余项继续OPEN/PENDING；#30也不由本次合并自动关闭。不施工下一版本。 |

两份办公室真实脱敏报告现纳入本次文档提交：[PC离线调查](docs/evidence/v051-settings-drift-analysis.md)、[一次当前设置只读核查](docs/evidence/v051-current-settings-readonly.md)。本次已先备份并保留原本未提交内容，仅将远端纯任务卡提交安全快进到本地；原始设备XML/截图/旅程/账号及本机脚本留在忽略目录，不上传。生产代码、正文、审批、Manifest、APK和原工程/手机JSON证据不修改。

**收口边界：按用户授权完成GitHub门禁后合并PR31，仅接受工程与探索UI；合并结果见PR最终记录。完成核验即STOP，不再访问手机、不发APK、不提升故事Gate、不关闭旧Issue或启动V0.5.2/V0.6.x。** 下文是各阶段原始交接，保留当时PASS/FAIL/PENDING和STOP，不用后续反馈倒改历史。

---

# 2026-10-16 17:27 最新：一次当前设置只读核查完成 / 再次设备 STOP

**`CURRENT_NON_SENSITIVE_SETTINGS_READABLE` / `ACCOUNT_ENCRYPTED_RECORD_PRESENT`；`PRE_UPGRADE_SEMANTIC_EQUIVALENCE_UNPROVEN`，原漂移根因仍 `UNKNOWN / INSUFFICIENT_BASELINE`，PR31保持Draft/HOLD。** 用户新增一次仅限当前状态的只读授权后，确认唯一USB vivo X200 Pro/V2405A、已装code18、无运行服务和Journey恢复文件；沿用已有非root run-as路径，仅一次在内存读取设置XML。未启动App/设置页、刷新模型/账号、解密、发起账号网络请求或改变任何设备设置，未保存/输出原始XML或账号敏感值。

当前provider=CHATGPT、model=gpt-6.1-sol、effort=low、speech_rate=1.0、tts_voice为空/系统默认、separate_research=false，均存在且类型符合源码，与较早code17验收记录一致；simulation/acceleration/simulation_speed未存储，无基准可据此判为重置。chatgpt为非空string，credential为空string，research_credential不存在；**只证实存储结构，未验证解密或在线账号可用性**。20/20历史不变、code18探索辅助可用沿用原证据；本轮没有重测历史或UI，用户亲手体验仍PENDING。

详见[当前只读脱敏报告](docs/evidence/v051-current-settings-readonly.md)。较早配置记录不能代替缺失的紧邻升级前字段快照，原设置字节FAIL及原965项工程结论不变。上轮未提交调查与HANDOFF已备份并保留；本轮只增补文档，没有提交/推送、生产代码/APK/审批改动。**一次当前状态检查已完成并用尽；再次STOP，不再访问设备，不重复安装，不合并/发布/关闭Issue或开始未来版本，等待用户亲手体验及是否合并的单独决策。**

---

# 2026-10-16 最新：PC本地设置漂移调查完成 / 设备 STOP

**`INSUFFICIENT_BASELINE`；设置语义与根因仍 `UNKNOWN`，整体 HOLD / `FAIL_SETTINGS_BYTE_PRESERVATION_SEMANTICS_UNDETERMINED`。** 本机三阶段JSON仅保留整体设置文件哈希及账号密文存在布尔值；检查已知 `artifacts/` 目录未发现升级前/后/最终原始偏好XML，不能重建升级前字段。仅复核已有记录：升级后立即设置一致，最终仅设置文件变化；**20/20历史始终不变**。已装code18可用的结论限原探索辅助证据；账号可用性、最终密文可解密性及用户亲手UI反馈仍未验证。

详见[无敏感技术判定](docs/evidence/v051-settings-drift-analysis.md)。启动移除旧 `simulation` 与设置页自动模型刷新/临期令牌加密保存均有源码依据，并已存在于code17；本次是否触发仍为hypothesis。AES-GCM密文变化不能证明账号变化或解密失败。本轮只做PC离线文件比较/源码调查，没有访问手机、刷新账号、改生产代码/设置/APK/原设备证据或提交历史，没有重跑或改写原965项结果。

**唯一升级许可已用尽，禁止再次安装。完成本机诊断即STOP，等待用户决定是否新增一次独立手机只读语义检查；新增当前数据也不能补成升级前基线。** 进入设置页/构造Store本身有写入路径，不能未经新授权用这些动作补采。PR31保持Draft；不合并/发布/关闭#23/#24/#27/#30，不启动下一版本。下方为原设备交接与历史证据，原FAIL/PENDING保留。

---

# 2026-10-16 V0.5.1/code18 — 单次真机升级成功；设置文件漂移 / STOP 待用户复核

**手机现为原签名 `com.kitt.reader.runtimecheck` 0.5.1/code18，已用完本轮唯一一次保数据升级许可，不得重复安装。整体真机验收尚未通过：最终设置文件哈希发生变化，检测后已停止手机交互。** 设备记录见 [独立手机证据](docs/evidence/v051-phone-acceptance.json)。本段覆盖下方“手机未访问/仍code17/等待安装授权”等历史状态；工程965项及其原结论保留，不能把本轮辅助观察写成用户亲手满意。

- **前置与升级 PASS**：同步任务卡授权头 `9f6326f`、PR31最新评论；唯一 USB/ADB vivo X200 Pro（V2405A），原包code17、原签名、无运行 Journey/服务或恢复文件。已从手机回读旧APK核哈希/证书，候选核包名、版本、大小和原证书。16:23:06—16:23:50 仅一次 `adb -d install -r artifacts/kitt-v0.5.1-runtimecheck.apk`，exit0/Success。未安装标准包或配套测试APK。
- **实际code18 APK**：74976037字节，SHA256 `8fb8b57040787ea82ffd3fb026d230698ac7ab8bde84ab29868b670b32413fea`；手机安装后回读完全相同。证书SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`，首次安装时间及原数据目录标识不变。沿用原工程候选，没有重建/重签。
- **升级立即比对 PASS；最终设置保字节 FAIL**：升级完成、启动UI前，设置与20份真实历史的逐文件哈希全部相同。完成UI并打开设置页查看账号后，最终16:38:44快照发现仅 `shared_prefs/settings.xml` 哈希漂移，20/20历史仍逐字节相同且JSON必要字段可读。账号加密记录一直非空；设置页可见账号行/计划已启用，无未连接标签。没有点击保存/重连/断连。**未保存升级前各字段指纹，不能判断具体变更、发生时刻或是否仅序列化/加密刷新；不能声称所有设置语义相同，也不能据此断言数据丢失。** 源码中启动清理旧simulation键、设置页自动刷新模型/可能轮换加密凭据只是待查路径，尚非真机根因结论。
- **探索辅助观察 PASS**：Wi-Fi/蜂窝均关、无Journey时可浏览新都1县/6对象/22事实/6来源、正式故事0。实际中文“杨慎”、事实正文“云南”及无命中词搜索/清除正确；逐一滚动核对事实/来源名/网址/检索日期，长按网址出现复制菜单，未打开网页或点击复制。竖屏/横屏、键盘关闭和查询保留、三按钮系统返回、大字号1.3下阅读滚动、Home→最近任务卡恢复原详情及滚动位置均未观察到阻断。没有伪造GPS、注销账号或撤销权限；保留既有账号的断网试验不冒称全新未登录/拒权限实测。
- **系统恢复 PASS**：网络、蓝牙、飞行模式、自动旋转、方向、字号最终逐项与测试前相同；蜂窝已开、Wi-Fi原关、方向0/字号1.0。未启动Journey，最终无服务/恢复文件。发现设置漂移后未修复、回滚、重装或继续手机操作，仅完成只读核对与交接。

**证据边界**：以上为 Codex/ADB 在真实手机上的点击、UI层级和截图辅助观察，用户亲手UI反馈仍PENDING。手机无历史列表入口，20份保留依据真实文件逐份哈希和JSON读取，并非逐份打开历史页面。原三按钮导航未更改，侧滑手势PENDING；Samsung平板、真实GPS自动全链/外场恢复、#23/#24/#27旧待验事项不变。正式故事0且无完整故事播放按钮是正确Gate，探索完整故事实机离线TTS仍PENDING，code17独立双篇真人耳听PASS不重算。所有原始设备截图/层级留本机忽略目录，未公开账号、旅行原文或密钥。

**交接结论与最小下一步**：保留本次失败与不确定性，请用户复核现有设置/账号状态，再单独决定是否调查漂移；若授权，只需补充非敏感逐字段指纹并区分启动与进入设置触发，不需要再次安装或清数据。此轮仅新增手机证据和本交接，不改生产代码/正文/审批。先前Debug489+Release476=965、Python7、六组Fake模拟、构建/lint及原签名回归均未重跑。PR31继续Draft/OPEN，不合并、不发APK、不关闭#23/#24/#27/#30、不展开下一版本或六县采集。**STOP等待用户验收及下一轮明确指令。**

---

# 2026-10-16 KITT V0.5.1/code18 — 工程交付 / STOP 待用户验收

**分支 `feat/v051-explore-places`，实现提交 `327186b`，交付 [Draft PR #31](https://github.com/shimao1115/KITT/pull/31)，关联 [Issue #30](https://github.com/shimao1115/KITT/issues/30)。** 本轮实际完成首页探索入口、已安装县级区域列表、关键词搜索、独立事实与来源阅读、合格完整故事全文及手动离线中文 TTS、播放生命周期和限定范围的布局精修。按 [TASK_CARD](TASK_CARD.md) 完成工程后 STOP。**手机未访问/安装、PR 未合并、APK 未上传/发布。**

## 当前真正可用的内容

| 项目 | 实际数量与范围 |
| --- | --- |
| 标准版与本版 runtimecheck 探索页 | **新都区1县、6对象、22条基础事实、6个已加载来源**。不启动旅程、无需GPS/麦克风/网络/云账号；全文关键词可查，详情展示独立条目、来源名/网址和检索日期。 |
| 正式完整故事 | **0篇**。杨慎两篇保持 candidate/visitor_gate=pending，正文/Claim/审批/标准资产逐字节未改。探索严格加载，不显示候选全文，也不提供伪造的完整故事播放按钮。 |
| 完整故事测试夹具 | **内存中2篇 TEST_ONLY 批准夹具**，仅验证主篇/延伸独立阅读、正文原样点播、来源及状态；不写入真实审批或APK。 |

首页保留「开始沿途」最高主动作，运行旅程时隐藏探索入口。浏览地区不会写GPS、当前区域、Journey、已播记忆、纪要或设置；旧 TopicFamily 未强行映射六类，暂无可信镇街目录/精确对象位置如实提示。合格故事按真实类别显示数量，正文可选择、可滚动；平板阅读最大宽度900dp。专用 AndroidVoice 使用已保存音色/语速并强制离线中文，复用分段和超时；无离线声音拒绝朗读。返回、切文、离页、后台和重建停止，迟到回调丢弃；不自动接播延伸。

## 本轮实际工程结果

- **Debug 489 + Release 476 = 965项**，两套均0失败/错误/跳过；探索相关23项在两变体均执行。完整 Gradle 构建/测试/lint 实际运行25分31秒，退出0；不是复用旧919项。
- Python **7项PASS**；标准内容 `--check` PASS；正式 `--require-playable` 预期exit1/BLOCKED，候选未指定独立目录预期exit2/拒绝；独立目录双篇生成及复检PASS，未覆盖标准资产。
- 六组模拟 **PASS**：完整路线、加速、批量主题、地标、Dossier、行驶区域解析；29点新都→广汉→德阳→绵竹→安州雎水粗粒度路线，确定性/Fake研究/Director/Voice，不能当真实GPS或人耳证据。
- Debug/Release assemble、两套lint、runtimecheck与配套AndroidTest构建/验签通过；lint零错误，各保留既有2警告（ExifInterface、ChromeOsAbiSupport），未扩修非本版内容。
- 原签名回归 **2接受+6拒绝PASS**；本地旧code17 APK与手机既有验收证据SHA256一致，同包名/同证书、17→18递增的静态兼容检查通过，**未执行设备升级**。
- Compose/Robolectric验证手机竖屏411×914dp、横屏914×411dp、平板横屏1280×800dp、小屏320×568dp/1.5倍字体的阅读/滚动/返回/播放；保存恢复保留文章及搜索但不续播，后台停止，错误/超时可重试。20份合成旧纪要及设置前后哈希不变，旅程/定位/已播/云计数未改；实际手机数据本轮未读取。
- runtimecheck构建副本 **44个生产Kotlin文件零差异**，Fact/Narrative源文件、标准资产、TripStore/SettingsStore相对main无差异。APK构建于实现工作树提交前，内嵌AGP VCS字段记施工基线；[证据](docs/evidence/v051-verification.json)以实现提交与实际生产文件字节哈希绑定，未冒称APK内嵌字段已变成新提交。

完整逐套测试名、计数、报告哈希、六组模拟摘要、lint、APK与证书信息见 [本轮证据](docs/evidence/v051-verification.json)，[验收说明](docs/ACCEPTANCE_EXPLORE_PLACES.md)。原始日志在 `artifacts/v051-verify.log`、`v051-runtime-build.log`、`v051-signing.log`、`v051-content.log`；可用 `python scripts/record-v051-evidence.py` 重新汇总本次输出（证据日期受限于本轮，不自动跑测试或访问手机）。

提交前主干新增纯文档整理 `cefe010`，已同步到本施工分支并解决旧路线入口的文档冲突；保留[统一现行路线图](docs/KITT_CURRENT_ROADMAP.md)、市州默认下载/县级物理分包及本地导入的既定次序，只更新本版工程进度。没有合并GitHub PR，也没有改变已测业务代码或候选APK，不据未来规划提前施工。

## 仅本地候选文件

| 文件（仓库根相对路径） | 实际字节 | SHA256 |
| --- | ---: | --- |
| `artifacts/kitt-v0.5.1-runtimecheck.apk` | 74976037 | `8fb8b57040787ea82ffd3fb026d230698ac7ab8bde84ab29868b670b32413fea` |
| `artifacts/kitt-v0.5.1-debug.apk` | 74428494 | `df96faa4604ba916c43096f1465f5c0b54b906f0b824812c44e6dadc9a8e6795` |
| `artifacts/kitt-v0.5.1-release.apk` | 70757438 | `e6b3ea2ae8f89b721fd5f9df48dba2eab5ecd9aa2cb9db78502588c310a3b75c` |

上述应用APK均0.5.1/code18；证书SHA256统一为 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。手机现有包的后续升级候选是 `com.kitt.reader.runtimecheck`，标准Debug/Release为既有 `com.kitt.reader`；本轮均未安装。配套测试APK路径 `artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，161629字节、SHA256 `882e48d408584d03d0ab839cec69e2554a5789bcdde7441c2ba210d2233f81d4`，同证书且仅构建未运行。APK保留本机artifacts，不提交Git/不上传。

## 仍需用户的最小动作与限制

先审 Draft PR31、证据和候选SHA256；若同意，再**另行授权 code17→18 一次同签名原位升级及设备UI验收**。当前无需OAuth/API key，探索不使用云Provider；源码新安装默认仍FAKE、保留既有未启用的模型字段gpt-4.1-mini/空effort，旧保存配置不改，不把它当本轮在线能力验收。

设备获准后按 [最短步骤](docs/ACCEPTANCE_EXPLORE_PLACES.md) 检查无网/无GPS浏览、搜索/来源、横竖屏/键盘/系统返回、设置及真实历史。标准故事0是正确门禁状态。code18真机屏幕、系统侧滑返回与真实语音行为仍 **PENDING**；code17双篇已有真人完整耳听PASS继续有效，不重复安装或重听。#23/#24/#27外场真实GPS自动全链/恢复/旧样本等保留原待验状态；正式Visitor Gate与发布仍BLOCKED。

**STOP：不合并、不关Issue、不操作手机、不发布，不启动六类偏好、下载器、在线搜索或六区采集。** 最近里程碑：`327186b`探索工程；`2c149e1`V0.5.1任务入口；`465d582`V0.5.0阶段工程合并。下方均为历史交接。

---

# 2026-10-16 V0.5.1 施工前入口（历史，工程状态以上方为准）

**现行分支 `feat/v051-explore-places`，唯一施工卡 [TASK_CARD.md](TASK_CARD.md)，Issue [#30](https://github.com/shimao1115/KITT/issues/30)。** 用户已同意开始下一小版本，目标是首页探索入口→查看真实已装新都区6对象/22基础事实→审核完备的故事才开放全文阅读和手动离线 TTS→必要 UI 精修。现有首批杨慎两篇为 `candidate`，`visitor_gate=pending`，标准版**正式故事0/发布BLOCKED**，不能借“探索地点”越权放出。

**上一版最新事实（覆盖下方合并前历史）**：V0.5.0 [PR #28](https://github.com/shimao1115/KITT/pull/28) 已于 2026-10-16 工程阶段性合并 `main`，Merge SHA `465d5828f48a077a8f5b631b6a5bb8f3d0737c85`；code17 已原签名安全安装在手机，真人完整听两篇没有问题；来源审核/试播真实证据见下。#23/#24/#27 保持 OPEN，可信GPS自动讲述/追问/离线恢复等仍未实地验证。上一版919项是2026-10-15旧工程结果，不能当V0.5.1新测试。

**本轮限制**：已授权 Codex 按新任务卡开发并提交独立 Draft PR，但尚未执行新代码、测试、构建、真机操作；**不自动升级手机、不合并、不正式发布、不开六类偏好/下载器/新县资料**。最终验收与安装须用户单独授权。当前页下方 V0.5.0“等待用户合并”的段落均属历史快照，不代表现状。

---

# V0.5.0 / code17 — 2026-10-16 最终合并候选 / STOP

**工程结论：MERGE-READY-FOR-USER-REVIEW，可供用户批准阶段性合并；本轮未发现必须修代码的合并阻断。故事结论：两篇仍为验收候选，正式发布 BLOCKED。** 本轮依据任务卡 `e3d9daa` 收口；未修改 Android、制包脚本、正文、Fact/Claim、审批表或标准资产，没有重构、重建 APK、访问或操作手机。PR [#28](https://github.com/shimao1115/KITT/pull/28) 保持 Draft，等待用户最终合并批准；#23/#24/#27 保持 OPEN。

## 最终合并审查

- 核对起点：远端/本地 `e3d9daaf3e6f0375a551fa45a5141ea4dea103c9`；`origin/main=fd8006e02eb5cbc9e3cee759278e5794073df189` 是本分支祖先，没有主干独有提交。GitHub 返回 `mergeable=true`，无未解决的行内 review thread；已有一条 `COMMENTED` 书面编辑审阅，没有 `APPROVED`。远端返回 commit statuses 与 PR workflow runs 均为空，不能据此声称远端 CI 或必需审批已通过；技术建议不替代用户合并许可。
- PR 相对 main 的工程范围：独立 Fact/Narrative 校验及本地选择、完整正文与最终成功后重播、延伸新 Claim 门槛、单趟尝试记忆、慢语速 TTS 有界超时、独立仪器试听，以及 code16→17 版本递增。候选只通过独立目录进入 Debug/runtimecheck；标准 Debug/Release 排除候选，缺故事时仍提供基础事实并如实提示。
- 兼容性：本轮逐文件核对 `app/`、`content/`、`scripts/` 与 Gradle 配置，均与既有919项回归的实现 `87e41d1` 相同；原 `TripStore`、`SettingsStore`、22条旧事实及几何/manifest 与 main 无差异。未引入存储迁移或账号改写。工程覆盖完整回调/打断/迟到回调、重播/延伸、位置拒绝、云失败后本地恢复及候选隔离；合成测试不冒充设备证据。
- 可接受的阶段性余项：下列外场事项及正式故事未放行。合并只接受工程框架与隔离的候选源码，不意味着标准 App 已提供正式杨慎双篇，也不意味着 #27 全部验收完成。未发现正文/哈希/依赖漂移、候选进入标准资产、数据格式破坏或已知崩溃等必须阻断本次工程合并的问题。

## 审校、试听与发布的独立判定

| 项目 | 最终结论与证据范围 |
| --- | --- |
| 最终文字 | 用户确认定稿，书面编辑审阅通过；两篇正文不再改写。 |
| 事实复核 | `fact_gate=passed`；11来源/16条Claim，来源与现稿相符，执行者为Codex并保留既有独立助手记录。不是人类史学专家签审；未证实的确切作时/作地不入正文，无需重启论文追索。 |
| 真人完整试听 | **PASS**；用户确认“两篇都完整听完，没问题”，主篇117.149秒、延伸59.532秒，正文哈希与审批、JSON、Markdown及手机证据一致。时长含初始化。不是GPS/Journey全链验收。 |
| 现有候选审批 | `approvals.json` 的2026-10-15 `acceptance_candidate / human_listening=pending` 是试听前审批快照，保留原记录；后续真人PASS以本节、[验收说明](docs/ACCEPTANCE_YANGSHEN_STORIES.md)、[手机证据](docs/evidence/v050-phone-acceptance.json)和[PR确认记录](https://github.com/shimao1115/KITT/pull/28#issuecomment-6205351157)纳入审核记录。不得把旧快照解释为用户仍未听完，也不改写原始仪器PENDING。 |
| 正式发布 | **BLOCKED**；仍 `candidate / visitor_gate=pending`，候选审批不构成正式发布批准，标准正式故事0。 |

**故事正式发布具体缺项**：需要在既有事实复核、书面编辑及本次绑定正文哈希的真人反馈基础上，取得游客 Gate 最终判定及明确的正式发布授权；再按现有字段记录正式审批，绑定同一正文与 Claim 集，审核后才可设置 `approved / visitor_gate=passed`，审批范围不能继续仅为 `acceptance_candidate`。届时同步版本/manifest/标准资产并实际验证正式 `--require-playable` 和受影响的工程检查。本轮不执行这些状态提升，不另建审批体系，也不要求为已完成的独立耳听重复安装或重听。**用户批准工程合并本身不等于批准正式故事或发布APK。**

## 本轮实际检查（2026-10-16 收口）

- `python scripts/test-story-packs.py`：**7项PASS**；`python scripts/build-story-packs.py --check`：PASS，正式故事0。
- 临时目录生成候选双篇，再逐字节 `check`：PASS；正式 `--check --require-playable` 预期返回1/BLOCKED；候选CLI未指定独立输出目录预期返回2/拒绝，均符合隔离要求。未覆盖标准资产。
- 两篇 Markdown正文/JSON/审批/手机证据的SHA256、Claim集指纹及状态一致：PASS；既有 code17 APK 的74399802字节、SHA256及内嵌候选全文与手机证据相同：PASS。只读本机既有APK，无构建、验签重跑或设备动作。
- 实现与历史回归基线的零差异、旧持久化与资产兼容差异审查、`git diff --check`：PASS。首次临时核对命令假设Markdown有正文标记而报IndexError，改按实际文件结构读取后全部通过，未修改稿件。
- **本轮未重跑 Android 单测、模拟、assemble/lint、签名回归或仪器测试**。Debug466 + Release453 = **919项零失败/错误/跳过**、Python7、六组成都新都→绵阳安州雎水Fake/确定性模拟、构建/lint和原签名2接受+6拒绝，均为[2026-10-15历史工程证据](docs/evidence/v050-final-verification.json)。本轮新增的Python7独立计次，不与919混计。

## 剩余现场验收（全部 PENDING）

| 归属 | 合适条件下需要补的真实证据 |
| --- | --- |
| #27 | 新鲜可信物理GPS/NETWORK命中新都几何后，自动完整主篇、完成后原文重播、明确追问才播延伸；有网/无网/云不可达时自动Director/Research增量0。 |
| #27 / #24 | 实机播放中跳过、说话/输入打断、静音及提前恢复、结束和迟到回调；不把失败/半截故事标完成。 |
| #24 | 双无线断开后仍有新鲜可信物理定位的本地讲述、这里有什么/重播；主动云请求失败结束后同Journey重新触发本地。无可信位置只能PENDING/安静，不注入定位。 |
| #24 | 连续自然行驶/卫星定位、静音满10分钟自然到期、真人ASR/异常进程恢复、早期边界权威复核等原独立余项。 |
| #23 | 含旧ratings/feedback的真实历史样本读取、原任务的自动讲述真人耳听及显式目的地/平板实机余项。杨慎独立双篇耳听PASS不回填这些不同场景。 |

本轮无新密钥或OAuth阻塞。账号保存状态与设置/20份历史保留已由10月16日设备证据证明；在线登录有效性仍未测。下一阶段仅记最新安排：**V0.5.1“探索地点”及UI优化 → 继续完善主程序 → 按PR #29规范推进全国区县离线知识包生产**，不据此提前施工。

**STOP：提交推送本次文档收口后，只等待用户批准是否合并PR #28。不得解除Draft、自行合并、关闭Issue、发布APK、重复安装或操作手机。**

---

# 2026-10-16 历史记录：原位升级与双篇真人试听完成

用户本轮明确授权 code17 一次同包同签名原位升级、配套 AndroidTest 安装及双篇独立试听。首次只读发现未结束旅程恢复记录，立即停止；用户回复“已结束”，复核恢复文件消失后才执行。手机现为 **`com.kitt.reader.runtimecheck` 0.5.0/code17**，禁止因下方历史说明重复安装。PR #28 保持 Draft，不合并、不关闭 #23/#24/#27、不发布 APK，不施工下一阶段。

## 本轮实际手机结果

- 候选 `artifacts/kitt-v0.5.0-runtimecheck.apk`：**74399802字节**，SHA256 `9d2d2b312c669e0469113e80a54164e3217519c77a6692c80037b4b98c776a6d`；手机实际旧包 code16 与候选唯一证书均为 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。APK完整性/签名核验PASS。
- **仅一次** `adb -d install -r` 原位升级返回 Success；手机回读 APK 哈希与指定候选完全相同。配套测试包 `com.kitt.reader.runtimecheck.test` 目标为runtimecheck、原签名、161599字节，SHA256 `45cd4588981c721846277635a2d70f39c198b9e1a6603c5ad27cb827dfb62b27`；安装一次Success并回读一致。
- 在用户结束遗留旅程后建立基线；升级及两次试听结束后，**整个设置文件和20份旅程历史的设备端逐字节SHA256均未变**，首次安装时间及CE/DE数据目录标识未变。保存的账号状态随设置完整保留，未重连/退出/保存设置；没有验证在线登录有效性或调用模型。保留 CHATGPT / gpt-6.1-sol / low、语速1.0、系统默认声音、独立研究关闭。未清数据、卸载、降级、重签或操作标准包。
- 使用现有 `mode=story-preview`，无GPS注入、无Journey启动，强制离线中文TTS；设备引擎 `com.vivo.aiservice`、声音 `zh`。两篇均有启动日志、实际语音音轨播放、扬声器路由及未静音系统观测，最终成功回调与 instrumentation code 0。时长从创建试听声音前到最终回调，含初始化，不是纯音频波形时长。

| 试听正文 | 实测总时长 | 正文SHA256 | 程序结果 |
| --- | ---: | --- | --- |
| `yangshen-main` | 117.149秒 | `f9b96bb89e8da63a354148542d56e4c420db8923b363380a693281c284ebceb4` | PASS_ENGINE_ONLY |
| `yangshen-followup-dianzai` | 59.532秒 | `559ff2267b73f4e64480bb21e2c114bc4cef3979db68895acfe79daea56e329f` | PASS_ENGINE_ONLY |

用户先回复 **“听到了，没问题”**，在确认两篇都已结束后，再明确回复 **“两篇都完整听完，没问题”**。据真实用户反馈记录 **HUMAN_LISTENING=PASS**，未反馈发音/节奏/易懂问题；不声称进行了逐词专家审音。仪器原始结果中的human_listening仍为PENDING，因为程序无法认证人耳；用户后续确认单独记录，不篡改程序输出。两次试听合计176.681秒，过程中未观察到runtimecheck相关崩溃缓冲记录，结束后无活动instrumentation。

[本轮脱敏手机证据](docs/evidence/v050-phone-acceptance.json)。原APK回读、文件哈希与仪器输出仅留本机忽略目录 `artifacts/v050-phone-acceptance-20261016`，没有上传手机凭据、私人纪要、坐标轨迹、录音或受限论文。本轮只修改文档/证据，未改正文、审批、生产代码或APK；未重跑Android工程回归，下方919项仅为10月15日既有结果。

## 未完成项与停止边界

本次只完成无GPS独立双篇试听。可信物理位置下自动主篇/重播/追问延伸、断网定位与云失败恢复未执行，继续PENDING；不把独立试听当实机全链PASS。#23旧评分真实历史样本及其余未验项、#24外场、#27整体验收继续OPEN。两篇资产仍candidate、visitor_gate=pending，未自动改为正式可播；标准正式故事0、发布BLOCKED。

**后续排序按本轮用户最新指示**：先主程序完整功能／同步UI，之后按[Draft PR #29](https://github.com/shimao1115/KITT/pull/29)六区县级独立分包与镇街全面调查。此处只记录安排，未提前实施；下方“先三星堆”及“等待安装/真人耳听”均为历史。完成本轮即STOP，不自动合并、关闭Issue、公开APK或启动下一阶段。

---

# 2026-10-15 历史交付：仅验收候选已生成，等待安装与真人验收

本轮按最新任务卡完成来源复核、现有门禁的最小区分、完整工程回归与原签名验收候选。**两篇 v2.1 正文、SHA256及原 Claim 接受修订集均未改变；没有安装/操作手机，没有合并 Draft PR #28，没有关闭 #23/#24/#27，也没有提前制作后续主题或开发探索地点/下载器。** 下面“尚无code17候选/用户尚未确认文字/915项冻结”等为历史，由本段覆盖。

## 实际审核结论

用户已[确认最终文字](https://github.com/shimao1115/KITT/pull/28#issuecomment-6187491143)；本轮 Codex 亲读人民网2018、2017两文，并从本机原指纹一致的赵晓晖论文重新提取/渲染、目视核第164页，与现稿版本关系相符。网页工具HTTPS失败、本机证书主机名不符，实际取得的是同站HTTP200原文；机构PDF远端本轮没有下载成功。已在[审校报告](docs/YANGSHEN_CONTENT_REVIEW.md)准确区分本轮亲读、既有独立助手回读及人工未验边界；没有把前次助手PDF不可读或模型互述冒充独立史料。

Fact `2026.10.15-review3`：11来源、16核验条目（14 verified / 1 lead / 1 rejected）；只是增加两条版本旁证，旧Claims和接受集未改。Narrative `2026.10.15-candidate2.1`：主583字符、延伸297字符。两篇 `review_status=candidate / fact_gate=passed / visitor_gate=pending`；Fact passed表示来源复核相符，非人类专家终审。审批表范围严格为 `acceptance_candidate`，绑定真实文字批准、正文及Claim集，`human_listening=pending`。**真人离线TTS、实际时长/发音/听懂、实机全链仍PENDING；正式发布仍BLOCKED，正式故事0。**

最小工程调整只允许 Debug `com.kitt.reader.runtimecheck` 载入事实与文字合格的候选；标准Debug/Release资产仍排除候选。候选制包仅写独立目录，不覆盖标准资产，审批不能被提升为正式发布批准。未改GPS、Journey、TTS、账号或云路径；没有另立Gate/Phase/Agent/Schema。

## 本轮实际验证

- **Debug 466 + Release 453 = 919项**，failures/errors/skipped全0；强制 `--rerun-tasks` 执行，非旧915项或UP-TO-DATE计数。`StoryPacksTest`每Variant18项已计入总数，新增候选隔离、全文与缺审核拒绝检查。Python制包门禁7项另行PASS。
- 六组成都新都→绵阳安州雎水的确定性/Fake模拟本轮重新执行PASS；Debug/Release assemble、两Variant lint、标准及runtimecheck DebugAndroidTest组装PASS。三个lint报告各0 errors/2既有warnings。仪器测试只构建/验签，**没有设备执行**。
- 原签名回归 **2接受+6拒绝PASS**，runtimecheck正例使用本轮code17；配套测试APK验签通过。旧code16本机已知APK与新候选同包名/原证书、16→17递增，本轮没有读取手机当时装机状态，故不冒称真实原位升级或数据保留已验。
- 标准两APK内正式故事0，runtimecheck内准确2篇候选；每个JSON/manifest字节、SHA256、原22条事实/几何均逐字节核对。独立复制工作区42个生产Kotlin与本分支0差异。Markdown正文、JSON、候选正文及用户确认哈希一致。

[完整本轮证据](docs/evidence/v050-final-verification.json) / [脱敏Gradle任务摘要](docs/evidence/v050-final-gradle-summary.txt)。原日志、受限论文/渲染图、APK留本机忽略的artifacts，未上传密钥/token/私人纪要/真实坐标/录音。旧工程记录保留作历史，未混计。

## 唯一手机升级候选（尚未安装）

`artifacts/kitt-v0.5.0-runtimecheck.apk`；`com.kitt.reader.runtimecheck`，0.5.0/code17。

- 字节：**74399802**。
- SHA256：`9d2d2b312c669e0469113e80a54164e3217519c77a6692c80037b4b98c776a6d`。
- 唯一原证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。
- 配套独立试听测试包：`artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，同原签名；当前未安装/执行。

标准 `com.kitt.reader` Debug/Release只是工程产物，不能替代手机账号所在runtimecheck；各自摘要在证据JSON中。本轮没有发布Release或上传APK。

## 待用户最小动作与已知限制

下一步只需用户另行明确授权 code16→17同签名原位升级；执行前须只读核手机当时包名/版本/签名和候选摘要。不能卸载、清数据、降级、重签或重装code16。无GPS时若另获配套测试包安装许可，使用既有 `story-preview` 分别完整听主篇/延伸；真人给听感、发音及实际时长反馈，`PASS_ENGINE_ONLY`不能当耳听PASS。[有GPS/无GPS最短步骤](docs/ACCEPTANCE_YANGSHEN_STORIES.md)。

可信新鲜物理GPS/NETWORK下自动完整主篇、重播/延伸、离线及云失败恢复仍须真机验证；没有可靠位置就PENDING/安静，不能注入位置。#23旧评分真实样本/耳听、#24外场独立OPEN/PENDING。原签名/历史JSON工程回归不代替设备数据保留核查。

默认Provider/模型配置未改，新装默认仍继承Fake / gpt-4.1-mini / effort空；本地自动主链不依赖云账号。本轮未读取/改手机设置或账号、未发真实App云请求，无需新增OAuth/API密钥。

**V0.5.0真机验收后内容先行**：先广汉三星堆一组经审Fact+Narrative，再做另一种题材，复用现有纯文字/本地TTS路径，以真实内容验证复用；本轮仅记录优先级，没有提前施工探索UI、下载器、全国平台、Releases或KITT-PACKS仓库。

最近milestone：`87e41d1` 本轮来源审核及候选隔离；`d6ce317` 收尾/内容优先任务卡；`db4d0da` 固定v2.1最终文字；`9351f17` 已合并code16基线。最终交接证据提交见分支HEAD。**交付后STOP：不安装、不合并、不关闭Issue，等待用户验收。**

---

# KITT V0.5.0 — v2.1 最终文字已由用户确认，进入史实复核待验（2026-10-15）

用户阅读提交 `db4d0da` 对应的杨慎主篇与《滇载记》延伸篇 v2.1 全文后，明确回复 **“确认定稿”**。已在 [PR #28 记录用户确认](https://github.com/shimao1115/KITT/pull/28#issuecomment-6187491143)，两篇正文不再反复改写。正式文字绑定 `yangshen-main` SHA256 `f9b96bb89e8da63a354148542d56e4c420db8923b363380a693281c284ebceb4`，`yangshen-followup-dianzai` SHA256 `559ff2267b73f4e64480bb21e2c114bc4cef3979db68895acfe79daea56e329f`。

**范围说明**：FINAL TEXT APPROVED 只代表用户最终文字确认；独立史实审查尚未完成，赵晓晖论文原件独立复核 PENDING；真人中文 TTS/听感与真机 GPS/离线播放也 PENDING。两篇仍 draft、`fact_gate=pending_human`、`visitor_gate=pending`、`approval_ref` 空、正式故事0，`approvals.json` 仍为空（审批记录不得伪造）；`--require-playable` 仍应 BLOCKED。此前 v2.1 内容校验已通过，但未重跑 Android 测试，915项是旧工程证据。

**下一步**：Codex 依 [最新 TASK_CARD.md](TASK_CARD.md) 和 [Issue #27](https://github.com/shimao1115/KITT/issues/27) 只补来源独立复核与必要真实审批证据；证据通过后执行完整工程回归，才考虑 code17 APK 候选。绝不因文字已确认就跳过审核门禁、擅自制作或安装 APK。PR #28 保持 Draft、不合并；不清手机数据、不关闭 #27、不开其他版本。#23/#24 仍待外场。

---

# KITT V0.5.0 — v2.1 最终候选供逐字确认 / REVIEW-STOP（2026-10-15）

已读取 `b448a9b` 最新任务卡及PR #28后两轮审稿评论。用户认可v2内容方向和整体讲法；本轮仅主篇两句轻量润色，不把方向认可写成最终文字批准、事实终审或真人试听通过。

- [主篇v2.1全文](content/yangshen/yangshen-main.md)：583字符，估计109–135秒（未实测）。只将大礼议句改为生父尊崇与皇位继承礼制下父子名分的冲突；词作流传句省去“毛宗岗父子评刻”，后台仍限定毛氏评本。其他主篇正文未改。
- [延伸全文](content/yangshen/yangshen-followup-dianzai.md)：297字符；Markdown文件、整个Narrative对象、全部引用及正文哈希逐项与输入提交相同。
- [两句前后对照、来源及最终哈希](docs/YANGSHEN_CONTENT_REVIEW.md)：主篇SHA256 `f9b96bb89e8da63a354148542d56e4c420db8923b363380a693281c284ebceb4`；延伸SHA256 `559ff2267b73f4e64480bb21e2c114bc4cef3979db68895acfe79daea56e329f`。沿用原Claim及Source修订，9来源/16核验条目不变，不扩史料。Narrative版本`2026.10.15-draft2.1`；Fact保持`2026.10.15-review2`。
- 本轮实际校验：两句变更范围、Markdown/JSON一致、正文哈希、精确Claim依赖、延伸对象/文件及事实包/审批表不变PASS；Python发布门禁4项PASS；`--check --require-playable`预期BLOCKED，正式故事0。[本轮证据](docs/evidence/v050-v21-content-validation.json)。未执行Android专项/全量测试，不把历史16项或915项算成本轮。
- 两篇继续`draft`、`fact_gate=pending_human`、`visitor_gate=pending`、`approval_ref`空，审批表空。PR评论里的书面游客编辑PASS只记录其原范围，不等于全史实人工审定/用户最终逐字认可/真人耳听。
- 未改Android框架或生产逻辑，未构建任何APK、未操作手机、不合并PR、不关闭Issue；#23/#24外场PENDING不变，不启动新功能。

已将最终候选留在 [Draft PR #28](https://github.com/shimao1115/KITT/pull/28)。**STOP等待用户最后逐字确认。**

---

# v2 改稿交付历史（以下字符数与校验仅属于上一轮）

当前最新交付是按 `1de6549` 任务卡和 PR #28 两轮意见完成的 **v2文字改稿**，未扩功能。下方v1工程交接与915项证据保留作历史，不代表本轮重跑；旧字符数/选题由本段覆盖。

- [主篇v2全文](content/yangshen/yangshen-main.md)：580字符，按260–320字/分钟估计109–134秒。主线为状元—大礼议—受杖赴戍病困—返川与读写—词作流传—新都纪念地；省略年号、官职及审校旁白。
- [延伸v2全文](content/yangshen/yangshen-followup-dianzai.md)：297字符，估计56–69秒。改讲《滇载记》商旅歌谣中的冬雪、夏热、盘缠；有主篇未讲的新Claim，不重讲朝争或古书阅读方法。均未真人计时/试听，不以时长灌水。
- [逐段事实与文风修改](docs/YANGSHEN_CONTENT_REVIEW.md)：来源仍9，核验条目16（14 verified / 1 lead / 1 rejected）。新增赴戍病困与书中歌谣两条原文依据；verified只表示执行者来源相符。九男/十男差异保留后台待核，不播；论文独立终审仍PENDING，不冒签审稿人已读PDF。
- 两篇 `draft` / `fact_gate=pending_human` / `visitor_gate=pending` / `approval_ref`空，审批表保持空；内置正式故事0、候选构建门禁仍BLOCKED。源码版本Fact `2026.10.15-review2`，Narrative `2026.10.15-draft2`，独立manifest已同步，待审正文未入应用资产。
- 本轮实际：结构/哈希/精确依赖与Markdown副本一致性校验PASS，Python发布门禁4项PASS，Debug `StoryPacksTest` 16项、0失败/错误/跳过。删去测试中旧稿“大于1000字符”的硬断言，仍逐字验证全文相等，未改生产Android逻辑。首次专项有2项失败，发现主篇“风土”概述引用了延伸歌谣Claim、使其不再有新Claim；已把主篇收拢为云南历史，歌谣专留延伸，最终专项实际执行通过。不是全量915项复跑。[本轮校验记录](docs/evidence/v050-v2-content-validation.json)。
- 本轮未运行assemble/lint/签名或runtimecheck构建，未生成任何APK，未读/安装/操作手机，未改账号、签名、定位、TTS、Journey或云调用路径；#23/#24待验不变。真实听感、独立/人工事实终审仍PENDING。

第二稿留在 [Draft PR #28](https://github.com/shimao1115/KITT/pull/28)，不合并、不批准、不发布。**STOP等待用户审稿。**

---

# v1 工程框架交付历史（2026-10-15；以下正文字符数不代表v2）

最新权威状态：已在 `feat/v050-xindu-yangshen-story-packs` 完成研究、可追溯待审双包、Android本地消费者及回归。当前 [Draft PR](https://github.com/shimao1115/KITT/pull/28)，**不合并；Issue #27 保持OPEN/REVIEW；没有code17 runtimecheck候选，也没有操作手机**。下方V0.4.2/旧版本文字保留作历史，不据其重复施工或安装。

## 可审阅内容与真实审核状态

- [主篇全文](content/yangshen/yangshen-main.md)：1063字符，估计199–245秒（非实测）；从新都/科举、大礼议与两次廷杖、云南读写到词作流传，独立成篇。
- [延伸全文](content/yangshen/yangshen-followup-dianzai.md)：485字符，讲《滇载记》的世系留白及传说与证据，含主篇未展开的新事实。
- [来源/争议/逐句审校](docs/YANGSHEN_CONTENT_REVIEW.md)、[可复现源](content/yangshen/README.md)：9条来源登记（7已读、1不可读、1检索线索），14条核验条目（12 verified / 1 lead / 1 rejected）。verified仅表示Codex来源相符；独立/人工事实终审、游客阅读、真人试听均PENDING。两篇均为draft；审批表为空，**正式内置故事数0**。
- 已核：明史/故宫的1524大礼议与受杖/谪戍、并非永不返川；《廿一史弹词注》第三段说秦汉的词作位置，并用张仲谋原署专家文章和赵晓晖论文交叉核流传。卒年用1559线索核对、1599拒绝且不入正文；不填精确作年/作词地点/最早刻本，不编江边落笔场景。转录本未作影印底本逐字对勘。
- 没有把人审或耳听签成PASS，没有镜像受限原文/PDF/图片到Git，也没有音频知识包。

## 工程能力与验证

最小独立JSON Fact/Narrative及两个manifest；每篇绑定Claim修订和Source登记修订，校验UTF-8/schema/字节/哈希/许可/审核/六类元数据。只让真实approved且依赖相符的主篇优先；延伸须有新Claim且主篇完整完成；失效仅影响依赖该Claim的篇目，无关Claim更新不误杀整区。

本地消费者已接入既有Journey：完整文字交同一AndroidVoice，本地中文TTS强制要求不改；仅最终成功才完成/允许重播，失败与打断不重播半截；单趟尝试不因32条通用历史轮换而遗忘，开始/结束清空；去掉主篇已经覆盖的旧杨慎/桂湖同角度短介绍；纪要用故事标题，不暴露内部ID。其他5个旧对象、原22事实、多边形与保守定位门禁、V0.4.2结束页/自动保存与旧JSON保留。正常自动有网/无网/模型不可达始终本地，零自动Director/Research；主动云失败后本地仍可选。此处新主篇的运行证明是**明确标为测试夹具的批准数据**，不是实际成品入包或设备证据。

实际冻结源码验证：**Debug 464 + Release 451 = 915项，failures/errors/skipped均0**；另外Python制包门禁4项PASS。两Variant assemble、lint以及DebugAndroidTest编译/组装PASS；lint各0 errors/2 warnings（既有ABI配置警告）。六组成都新都→绵阳安州雎水的确定性/Fake模拟已实际重跑，非真机/非真人听感。[证据](docs/evidence/v050-verification.json) / [脱敏Gradle任务日志](docs/evidence/v050-gradle-summary.txt)。两次中途全量因最后审查修订而主动停止，不计PASS；最早专项TTS测试的Shadow自动回调问题已修正并重跑，详情在证据。

原证书SHA256仍为 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。2接受/6拒绝签名回归PASS（runtimecheck正例用**既有code16**，不是code17）；本轮标准工程Debug `com.kitt.reader` code17亦核原签名，并逐字节核四份资产/正式故事0。它不是手机升级候选，不提供安装。`scripts/build-runtime-smoke.ps1 -Offline`的前置内容门禁已实际返回BLOCKED，未进行runtimecheck组装：缺用户批准的主篇+含新事实的延伸，因此 **artifacts/kitt-v0.5.0-runtimecheck.apk 不存在**。

## 默认配置、手机与下一步最小验收

Provider/账号/模型设置及存储路径未改；新安装代码默认仍继承Fake / gpt-4.1-mini / effort空，物理自动主链不取决于云账号。本轮不读手机账号或私密纪要，不发真实App云请求；code16是上一轮已验事实，本轮没有重新读取装机状态，也没有安装/卸载/清数据/重登。无需新密钥/OAuth；当前阻断是内容审批，不是云连接。

现在只需用户审阅两篇完整稿与来源报告，认可或提出修改。真实批准须绑定正文SHA256及Claim集，不能由Agent伪签。批准后先更新正式数据/版本并重新执行相应工程验收，再生成同原签名runtimecheck code17；**手机安装仍须另行明确授权**。无GPS试听已有显式instrumentation安全入口，但同样只读合格正式包，当前会BLOCKED；不改真实GPS/Journey记忆，不是探索地点。具体 [有GPS/无GPS两种最短步骤](docs/ACCEPTANCE_YANGSHEN_STORIES.md)。

真人完整离线TTS、实际时长/发音/趣味、真机真实新鲜GPS/NETWORK全链、原位升级后设置保留均PENDING。#23/#24已重新只读核为OPEN，旧评分实机样本/真人耳听及断网定位/恢复外场继续待验；不能借本版关闭。没有开发探索地点、六类偏好或离线下载/更新，没有发布Release或新仓库。**本轮STOP，等待用户内容与阶段验收。**

## 最近milestone

- `ec08268` — 本版原生消费者、真实回归与审核门禁工程交付（本次执行源码）。
- `6610c41` — 初稿/来源追踪与拒绝未审稿发布；后续书目补核见当前分支。
- `aa62263` / `e3d731d` — V0.5.0唯一授权任务入口。
- `9351f17` — V0.4.2已合并基线；其历史待验不改写。

---

下面为历史交接。

# KITT V0.4.2 / code16 — 已阶段性验收并合并 main（2026-10-15）

> **最新权威状态**：用户已明确同意将 V0.4.2 阶段性通过并合并，剩余待验以后补做。[PR #26](https://github.com/shimao1115/KITT/pull/26) 已合并，merge commit [`9351f17`](https://github.com/shimao1115/KITT/commit/9351f17f1c4d03143c57e04e1b74311a858528a6)；工程/手机证据来自分支 `ddda17c`。此轮**没有再次操作手机、安装 APK 或重跑 879 项测试**，当前真机已为原签名 `com.kitt.reader.runtimecheck` V0.4.2/code16，**切勿重复安装**。
>
> **PASS（分级）**：Debug446＋Release433＝879 项工程零失败；单次同签名 code15→16 原位升级及设置保留；结束页无五项评分、意见框、保存按钮，主题/空旅程纪要在结束时自动写入并直接返回，真实手机竖横屏可见；真实新都 NETWORK 本地自动讲述及 Director/Research 启动0 为**设备记录 PASS_ENGINE_ONLY**，**真人耳听未核**。
>
> **仍然 PENDING**：[Issue #23](https://github.com/shimao1115/KITT/issues/23) 保持 OPEN，等待有真实旧 `ratings`/`feedback` 文件时只读实机兼容验证（工程回归已通过），以及真人耳听、可选显式目的地/平板等未完成检查。原有最多20份纪要轮换规则未变；测试新增2份后18份旧记录保留、较旧的2份依旧规则淘汰，升级本身未清空数据。独立 [Issue #24](https://github.com/shimao1115/KITT/issues/24) 的断网可信物理定位/重播/云失败后本地恢复等仍 OPEN/PENDING。
>
> **STOP**：V0.4.2 已归档，不重启开发、手机升级或 PR 合并；下一候选 V0.5.0 的高质量离线 Fact/Narrative 双包仍须另行授权。本文件下方“PR26 仍 Draft / 不合并 / 等待用户确认”等描述都是**合并之前的历史现场记录**。

# KITT V0.4.2 / code16 — 真机原位升级与有界验收完成，STOP（2026-10-15）

用户明确授权后，在 `feat/v042-simplify-end-summary` 快进至 `b8890f7`，对唯一候选执行**一次** `adb install -r` 成功；vivo超级守护由用户本人确认。**手机现为 `com.kitt.reader.runtimecheck` 0.4.2/code16，不得再安装一次。** [Issue #23](https://github.com/shimao1115/KITT/issues/23)保持OPEN，[PR #26](https://github.com/shimao1115/KITT/pull/26)保持Draft；不合并、不进入V0.5.0。下方“等待升级授权/手机code15”等为各轮历史。

| 检查 | 本轮实际结果 |
| --- | --- |
| 安全升级 | **PASS**。候选74,369,322字节，SHA256 `36d39438c8efb735cd8e67bee1a250c8e789a25061a9425cb8d9ae7bebe0ccb3`；手机升级前0.4.1/code15、APK SHA256 `8beac9f79e4adcf8a31f95b742562ea393d996c337be3e1ee87cc8c2e58ce43f`。候选/升级前/升级后回读APK均通过原验签脚本，唯一证书SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；回读code16 APK与候选哈希完全相同。未卸载/清数据/降级/重签。 |
| 账号、设置与数据空间 | **PASS已核字段**。同一可见账号、ChatGPT启用；CHATGPT/gpt-6.1-sol/low、语速1.0、默认声音（tts_voice空）、独立研究false保留。安装前最后基线→安装后→验收后整个settings.xml哈希一致。首次安装2026-10-09 09:42:45、CE/DE目录inode不变，更新时间2026-10-15 08:27:34。未保存设置、重连或读取凭据值。打开设置自动触发1次模型目录请求，并在安装前改变文件哈希，因此使用其完成后的安装前基线；**零自动Director/Research不等于全程零网络请求**。不宣称在线推理可用或本轮真人耳听声音身份。 |
| 精简结束页 | **PASS真机**。有主题及空旅程两条路径均显示标题/时长/跳过次数和主题或“这一程以安静相伴。”，无五项评分、意见框、保存按钮、未提供目的地技术状态。有主题页竖横屏均完整可见且无须滚动。每趟恰好一次“回到出发页”到正常出发页，结束后JourneyService不存在。 |
| 自动纪要与历史 | **PASS有界文件核验**。升级本身20份原记录逐份内容摘要相同，全部可按既有纪要字段解析。两次结束各新增1份JSON，仍停在结束页时即已保存；新记录无ratings/feedback。两次返回前后当时20份文件内容摘要逐份一致。**既有20份保留上限正常执行：每次结束各淘汰1份较旧记录，最终18份原记录＋2份测试纪要，保留的原记录未改写**，没有迁移或手工删除。无历史浏览UI，不冒称逐份执行TripStore.latest。纪要摘要对UTF-8读取文本去首尾空白后计算，证明内容一致，不单独证明空白字节/mtime未变；设置哈希为设备sha256sum逐字节核验。 |
| 旧评分字段实机 | **PENDING**：升级前20份真实记录中含ratings/feedback的为0；未向手机伪造旧记录。上轮工程遗留JSON读取/逐字节兼容测试PASS继续有效，但不代替该实机项。 |
| 联网本地自动讲述 | **PASS_ENGINE_ONLY当前条件**。普通开始取得真实NETWORK（±37m/0秒，后见±30m/3秒）并命中新都离线包，自动杨慎主题SPEAK_NOW、`cloud_calls=0`，Voice completed success=true/active=false/ask=false共1次；完整小型本进程诊断及logcat无Director dispatch/start、Research start或active research decision。计数为诊断事件，不冒称仪表requests快照/抓包；非卫星GPS、未注入位置。未主动问目的地。**本轮真人耳听PENDING**。 |

脱敏 [手机证据](docs/evidence/v042-phone-acceptance.json)、[真机验收](docs/ACCEPTANCE_END_SUMMARY.md)及 [竖屏](docs/evidence/v042-end-portrait.png) / [横屏](docs/evidence/v042-end-landscape.png) / [安静页](docs/evidence/v042-end-quiet.png)已提交。原始账号文字/摘要、设备序列号、坐标/轨迹、设置密文/哈希、原始私人纪要、聊天/录音与签名私钥均未上传。仅公开受控结束页截图与汇总布尔/计数。当前进程AndroidRuntime FATAL=0；无持续无崩溃保证。

手机最终在正常出发页，无旅程定位前台服务、录音或TTS；恢复原锁定竖屏（accelerometer_rotation=0/user_rotation=0），Wi-Fi/蜂窝/VPN配置未动。没有安装或运行instrumentation，没有改生产源码/重打包，也没有重跑上轮已完成的Debug446/Release433=879项、六组成都→绵阳模拟、build/lint/签名；这些继续引用2026-10-14工程证据，不算本轮新执行。

后续最短人工项仅为真实旧ratings/feedback记录可得时只读补验、明确目的地/平板硬件和真人耳听；[#24](https://github.com/shimao1115/KITT/issues/24)断网可信新鲜定位地方全链、重播/有限问答和同旅程云失败恢复等仍OPEN/PENDING，不用本轮联网结果覆盖。无待用户本人完成的安装/账号配置动作。实现 `d64adf8`、工程交接 `dcaf390`、同步授权 `b8890f7`；本轮验收文档/证据提交见分支HEAD。**完成即STOP，不重装、不合并、不关闭Issue、不启动下一版。**

---

# KITT V0.4.2 / code16 — 工程PASS，STOP等待真机升级确认（2026-10-14）

当前唯一交付为 [Issue #23](https://github.com/shimao1115/KITT/issues/23) / [Draft PR #26](https://github.com/shimao1115/KITT/pull/26)，分支 `feat/v042-simplify-end-summary`，实现提交 `d64adf8`。**工程完成；本轮未连接/操作手机、未发送真实云请求，不合并、不关闭Issue、不进入V0.5.0。** 手机code15是前轮历史实测，本轮未重新读取；本版升级及真人验收全部PENDING。下方V0.4.1历史与 #24 外场PENDING完整保留。

## 当前能力与兼容性

结束页删除五项评分、默认全3分状态、评语、保存评分按钮及专用提示，清理全仓无其他调用的 `TripStore.feedback()`。保留标题、时长、跳过数、主题、无主题安静文案和直接返回；仅隐藏空值/未询问/未提供/等待回答，明确提供的目的地正常显示。EndScreen无存储实例依赖，不产生保存交互。

原 `KittRuntime.end()` → `TripStore.finish()` 自动保存、JSON版本/字段、原子写入、恢复及既有最多20份纪要保留策略不变；没有迁移、批量清空或扫描重写。旧JSON的ratings/feedback额外键可忽略并读出纪要，读取/显示/返回不改写文件。新纪要无评分字段。定位、自动本地零云、目的地不主动追问、TTS/ASR、账号设置与知识资产未修改。

## 实际工程验证

- 最终 `scripts/verify.ps1 -Offline` **实际重新执行 Debug446 / Release433 = 879项，failure/error/skipped均0**。较code15的441/428各新增5项，总增10；两个旧测试修订保留历史兼容与结束保存验证。UI/持久化专项10项另跑通过，不重复计入879。旧本地优先、生命周期、定位/语音/持久化回归保留。
- Compose/Robolectric验证评分/评语/分数及文本输入不存在、明确/技术目的地和安静文案、竖屏/小横屏/平板返回可见，真实MainActivity结束按钮在返回前自动保存。手写旧ratings/feedback样本，重读及保存另一趟后逐字节不变；损坏current.json安全恢复断言保留。
- **首轮Debug446有1失败、0error/skipped，Release未执行**：旧 `ResearchTransportProbeTest.totalDeadlineReleasesCallerEvenWhenNativeCallbackIsStillBlocked` 的150ms墙钟可能在OkHttp回调初始化前到期，随后读取未初始化句柄。仅修测试：先确认真实回调进入阻塞，再推进同一150ms测试时钟，保留取消/有界返回断言；专项及两Variant完整重跑通过，生产网络代码未改。首轮日志及最终suite-case清单留证。
- Debug/Release assemble与lint PASS；每个标准Variant及runtimecheck lint各2条既有Warning（ExifInterface、ChromeOsAbiSupport），Error/Fatal0。JDK17.0.19、SDK35/build-tools35.0.0、离线依赖。最终runtimecheck `assembleDebug/assembleDebugAndroidTest/lintDebug` exit0，75任务9执行/66复用；AndroidTest只构建/验签，**未执行instrumentation**。原签名回归2接受+6拒绝PASS，测试APK原签名PASS。41个生产Kotlin+4个离线包文件与副本SHA256对照0差异。
- **六组成都→绵阳模拟在全量中重新执行，最终PASS**：full80km/h、29点/90,912米/68分钟、56 Director/2声音；accelerated22调用/22声音；batch13章/23主题/23调用；landmark4节点/28调用；dossier13讲述/33调用/19研究；合成700米章节80/100km/h下12/10查询、最短16秒。均为Fake/确定性Provider和Voice，不是实际云计数、真实行驶或真人耳听。

[完整脱敏证据](docs/evidence/v042-verification.json) 含suite/case、首轮失败、源码/产物/日志摘要；原日志与APK留本机忽略的artifacts。没有提交私钥、账号值、凭据、录音、轨迹或聊天。实现提交后只补证据/文档，不以旧UP-TO-DATE单测冒称新执行。

## 原签名产物

三个生产APK均0.4.2/code16，唯一原证书SHA256 **`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`**。现有手机账号数据空间对应runtimecheck，标准包不能替代。

| APK | applicationId / 字节 | APK SHA256 |
| --- | --- | --- |
| [runtimecheck验收](artifacts/kitt-v0.4.2-runtimecheck.apk) | com.kitt.reader.runtimecheck / 74,369,322 | 36d39438c8efb735cd8e67bee1a250c8e789a25061a9425cb8d9ae7bebe0ccb3 |
| [Debug](artifacts/kitt-v0.4.2-debug.apk) | com.kitt.reader / 74,369,302 | 095e7e30bd3b56c28500c29bb0ec51003f87e9ed8d49f0481132faa958c4aef2 |
| [Release验收](artifacts/kitt-v0.4.2-release.apk) | com.kitt.reader / 70,712,058 | c9bedf1ac8d4c0392abab7b2bbe30205307959945d3208361968b2fa8be9641b |

## 配置、待真人与最近提交

新安装默认仍Fake / gpt-4.1-mini / effort空，无新增OAuth/API密钥/签名准备待办。原手机ChatGPT/gpt-6.1-sol/low等为code15历史记录，**本轮没有读取/修改或验活**。唯一需用户本人完成的下一动作是明确批准code15→16原签名原位升级；批准后仍须读取实际装机状态并核安全门禁。

最短真人步骤见 [结束页验收](docs/ACCEPTANCE_END_SUMMARY.md)：升级/账号设置保留、实际无评分结束页及直接返回、返回前自动纪要/旧记录仍在、竖横屏，以及联网真实可信位置下本地讲述0自动云。**这些本版均PENDING**。#24断网新鲜定位完整地方链、有限问答/重播及同旅程云失败后恢复等仍OPEN/PENDING；不以code16合成回归改写前轮外场结论。

- `2982b70` — 已阶段性接受code15主干基线（含PR25合并）。
- `259d160` — 最新V0.4.2单一任务卡/授权基线。
- `d64adf8` — code16结束页精简、自动保存/旧JSON/布局回归、旧超时测试调度修复。
- `dcaf390` — 全量工程、产物/日志证据及STOP交接；后续PR26链接文档提交见分支HEAD，生产源码/APK未变。**STOP等待真机升级确认，不安装、不卸载、不清数据、不合并、不关闭Issue、不启动下一版本。**

---

# 历史：KITT V0.4.1 / code15 — 已阶段性接受并合并 main（2026-10-14）

> **现行权威状态（覆盖下文合并前历史描述）**：用户已确认**“剩余外场测试以后补做”**，同意 V0.4.1 阶段性通过并合并主分支。[PR #25](https://github.com/shimao1115/KITT/pull/25) 已真实合并，merge commit [`65ec2726`](https://github.com/shimao1115/KITT/commit/65ec2726e0fe173ff431db8ecfebade7c645d179)，工程候选最后提交 [`bc3dea6`](https://github.com/shimao1115/KITT/commit/bc3dea64c6728759dc09457ae53d40d538a37ce9)。**Issue #24 保持 OPEN，仅追踪外场待验**，不得因为合并自动标注全部 PASS；现有手机已装 code15、已安全完成验收、账号及已核设置保留，**不要再装一次**。
>
> **已实测 PASS**：869 项工程测试零失败、原签名安装/哈希、真实 NETWORK（±37 m/1秒）新都区联网本地讲述及云 Director/Research 自动增量 0、不询问目的地、用户耳听首次地方讲述及独立断网中文 TTS、已核打断/安静/后台与设置。**留待外场 PENDING**：断网时取得可信物理新鲜位置后完整地方讲述、同旅程重播/这里有什么及云失败后恢复；另有真实卫星 GPS/连续行驶、ASR/异常恢复、满10分钟自动到期及历史区界审校等独立未验。断网无新鲜位置时正确保持未知/安静，不能据此推断播放故障。无需再专项修复 ChatGPT 连通性。
>
> **STOP**：本轮仅完成阶段性接受、GitHub 合并与交接更新；未再次操作手机或重跑测试，不自动开始 [#23](https://github.com/shimao1115/KITT/issues/23) 评分页清理或 V0.5.0。下方所有“PR 仍 Draft / 不合并 / STOP 等待用户决定”均为**合并前的历史状态**。

# KITT V0.4.1 / #24 原位升级与真机验收 — 部分PASS，STOP（2026-10-14）

**用户已授权并完成原手机14→15原位升级；本轮真机检查结束，等待用户验收决定。** Issue #24保持OPEN、PR #25保持Draft，不合并、不做下一版本。以下结果优先于下方“安装前工程交付”历史，不能把部分PASS扩大为完整断网黄金路径。

| 项目 | 本轮真实结果 |
| --- | --- |
| 原签名安全升级 | **PASS**：单次 `adb install -r` 成功，实际包名com.kitt.reader.runtimecheck、0.4.1/code15；装机APK回读及最后手机sha256sum均为 `8beac9f79e4adcf8a31f95b742562ea393d996c337be3e1ee87cc8c2e58ce43f`（74,369,318字节）。唯一证书仍3139a293…502ba；未卸载/清数据/降级/重签。首次安装时间2026-10-09 09:42:45、数据目录及CE/DE inode不变；更新时间2026-10-14 15:42:49。 |
| 账号/设置 | **PASS可见与已测字段**：升级前后及测试后同一可见账号、ChatGPT启用、语速1.0、默认中文声音/2个本地声音、独立研究未选中。两组Probe内配置不变，读到ChatGPT/gpt-6.1-sol/low。**code14模型列表不可用，精确model/effort升级前后逐项对照PENDING**；未读原始凭据或保存设置/重新连接。 |
| 联网普通自动讲述 | **PASS实际NETWORK来源+真人耳听**：普通开始取得±37m/1秒的真实NETWORK、新都可信几何；先杨慎、再桂湖，诊断2次本地自动SPEAK_NOW/2次完成，云Director dispatch、研究启动、主动研究判断、自动ASK_USER均0。用户明确确认首次杨慎地方讲述“听到了，中文清楚”。不是卫星GPS证据。 |
| 联网有界计数观察 | **PASS_ENGINE_ONLY**：保存的ChatGPT配置、蜂窝联网已验证/VPN可见，120秒中109秒新鲜可信NETWORK，自动完成1、自动Director/Research增量0/0、目的地未询问。固定“再讲一点”真实本地完成1次且0云；随后位置失去可信，重播/这里有什么未签PASS。 |
| 主动云问题 | 实际单次固定通用问题：Director请求1、Research0、FAILED，短句失败处理完成、等待结束。**同Journey失败后有可信位置的再次本地回答PENDING**：该步位置已失去可信；未修改账号/代理制造失败，未继续刷云请求，也不诊断其根因。 |
| 双无线断网 | 安全退化**PASS**：Wi-Fi/流量均0，App识别physicalInternet=false，即使VPN仍VALIDATED。120秒新鲜GPS/NETWORK与可信区县均0，仅LAST_KNOWN；无自动讲述、自动云增量0/0、未问目的地。**真实断网定位→地方讲述PENDING**，不是“断网成功讲了新都”。 |
| 独立离线中文TTS | **PASS真人耳听**：双无线0时，设置固定试听使用本地zh/1.0，16:07:50 STARTED→16:07:56 success=true，UI“试听结束”，用户确认中文清楚。首个操作误开下拉列表、没有发音，已排除并按用户要求重试；**试听不替代断网地方链**。 |
| 实机控制 | 实际播放中点“说点什么”：正在播→不再播/正在听，取消后返回，**PASS按钮打断真实TTS**。10分钟默认值/9:58倒计时、提前退出、跳过计数1、结束/回到出发页PASS；Home期间LOCATION前台服务保持、结束后停止。**满10分钟自动到期、播放中skip专门语义、真人ASR/同义重播、异常进程恢复和连续行驶仍PENDING**。 |

手机最终停在正常出发页，无运行旅程、前台定位服务、录音或TTS；已恢复原Wi-Fi关/移动数据开，VPN配置未动。升级后观察窗口新增KITT AndroidRuntime FATAL=0，不代表无限期无崩溃保证。完整脱敏实测在 [手机证据](docs/evidence/v041-phone-acceptance.json)；无坐标/轨迹、邮箱、账号值、token、聊天或录音导出到Git。

本轮仅新增androidTest有界实机Probe，`assembleDebugAndroidTest/lintDebug`最终57任务、7执行/50复用PASS；现有同签名测试组件原位更新由用户亲自批准vivo超级守护，未绕过保护。**生产main/资产/版本和验收APK未改变、未重建替换**。沿用上轮实际Debug441/Release428及六组模拟工程结果，不冒称本轮重跑全量。

最短后续：在安全室外取得新鲜精确物理位置后，补双无线关闭的地方讲述/0自动云计数、同Journey“再说一遍/这里有什么”及主动云失败后本地恢复；不要重装当前code15、放宽位置门槛或用模拟补PASS。原有历史边界复核/镇街/内容深度和其他Issue未验项不变。**本轮STOP，不重新授权安装，不合并PR或开始#23/V0.5.0。**

---

# 历史：KITT V0.4.1 / #24 安装前工程交付（2026-10-14）

**0.4.1/code15工程PASS，原签名runtimecheck APK已生成；本版手机安装及真机验收PENDING。** 仅完成 [#24](https://github.com/shimao1115/KITT/issues/24) / [Draft PR #25](https://github.com/shimao1115/KITT/pull/25) 的工程范围，不合并、不关闭Issue、不进入#23或V0.5.0。本轮没有adb、手机连接/安装/卸载/清数据、凭据读取或真实ChatGPT请求。旧手机最后有证据版本为0.4.0/code14，本轮未重新读取。

## 当前行为与修复

普通真实物理旅程固定本地：可信新鲜GPS/NETWORK → 已安装新都许可几何/事实 → OfflineNarrator → 已安装离线中文TTS。联网也不等ChatGPT，不启动自动Director/Research；不可信/未覆盖/缺包安静。有限这里/补充/重播直接本地；用户主动开放问题、图片或动态查证才按需在线，失败/超时/取消后仍能在下一合法机会本地开口。出发不问目的地，保留主动目的地/可选路线参考和必要澄清。修正其他澄清被误写为目的地，离线TTS要求与识别后端条件分开，联网仍保留系统→Vosk回退；本地健康提示不受旧云失败覆盖。

## 实际工程验收

- 完整 `scripts/verify.ps1 -Offline`：**Debug441 / Release428，共869，failure/error/skipped均0**；每种Variant相比0.4.0新增18项。Debug/Release assemble、lint PASS；lint各2条既有Warning（ExifInterface、ChromeOsAbiSupport），无Error/Fatal。JDK17.0.19、SDK35、build-tools35.0.0，使用本机离线依赖。
- 原签名回归 **2接受+6拒绝PASS**，另验签runtimecheck AndroidTest；runtimecheck assembleDebug/assembleDebugAndroidTest/lintDebug PASS。41个生产Kotlin和4个离线包文件与runtime副本哈希对照0差异。AndroidTest只构建，未执行手机instrumentation。
- 首轮Release428中4项新增恢复测试失败：1.9km/46秒未到既有3km/180秒讲述门槛；测试保留门槛前安静断言、验证下次合法机会，**未放宽生产冷却/定位阈值**。脚本/验收包及签名负例暴露Windows PowerShell5的UTF8/ANSI、native stderr兼容问题，均修复并重跑相应脚本；失败日志保留。追加标准打包复核时生产源码未变，复用上述已实际执行869项结果，不把UP-TO-DATE算成另一次测试。
- 成都新都→广汉→德阳→绵竹→绵阳安州雎水六组最终重跑PASS：full80km/h、29点/90,912米/68分钟、56 Director/2声音（取消目的地问答）；accelerated22调用/22声音；batch13章/23主题/23调用；landmark4节点/28调用；dossier13讲述/33调用/19研究；合成700米地区解析80/100km/h下12/10查询、最短16秒。**这些是专用Fake/确定性模拟，允许模拟Director调用，不是真实旅程云计数。**
- 本地优先专项11项+Runtime集成2项在两Variant均PASS：联网/断网/云不可达自动路径Provider选取和云调用0、本地speech同步提交、缺口安静；主动问答/研究失败/超时/取消/迟到隔离、网络恢复不打断本地；Robolectric物理源接线、联网几何优先/离开旧地区、本地中文声音选择和ASR回退。**合成位置、TestVoice/Shadow TTS不能证明真机GPS、实际流量或真人耳听。**

完整suite/case清单和产物摘要见 [脱敏证据](docs/evidence/v041-verification.json)，最短人工步骤见 [本版验收](docs/ACCEPTANCE_LOCAL_FIRST.md)。日志/APK在本机忽略的artifacts，不上传密钥、私人录音/轨迹/聊天或二进制。

## 原签名APK

三个生产APK均0.4.1/code15、唯一证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。手机账号数据空间只对应runtimecheck，不能用标准包替换。

| APK | applicationId / 字节 | APK SHA256 |
| --- | --- | --- |
| [runtimecheck](artifacts/kitt-v0.4.1-runtimecheck.apk) | com.kitt.reader.runtimecheck / 74,369,318 | 8beac9f79e4adcf8a31f95b742562ea393d996c337be3e1ee87cc8c2e58ce43f |
| [Debug](artifacts/kitt-v0.4.1-debug.apk) | com.kitt.reader / 74,369,302 | b20ecadf899cb39ae5d802e668270af94cc46eab76d78fad65abf48cd9e01773 |
| [Release验收](artifacts/kitt-v0.4.1-release.apk) | com.kitt.reader / 70,728,442 | 42ef772ab3c86043a54532ed3cf42154c6dac553478a77404eb904f36546fab9 |

## 配置、待真机项目与最近提交

等待用户本轮确认才可原位升级，再停车/乘客状态取得新鲜精确物理GPS，分别验联网且ChatGPT不可达/断网直接本地出声，自动Director/Research**开始前后新增计数0**；出发不问目的地，有限问法/重播/skip、说话/打字打断、10分钟安静/提前恢复、后台/恢复/结束、主动在线失败后回本地及账号/设置保留。以上本版真机均PENDING；无新鲜GPS继续未知，独立试听不算全链。无新增OAuth/API密钥/原签名准备待办。

新安装默认仍Fake / gpt-4.1-mini / effort空，真实自动本地主链与这些设置无关。原手机历史ChatGPT / gpt-6.1-sol / low、速度1.0、系统默认中文声音未改，未宣称本轮重新核对或验活。仅新都6对象/22事实、首次两条拼接，未扩内容深度/成品故事；2017历史许可边界的2026权威复核、镇街及#20/#13/#14/#17/#18旧现场未验项不改PASS。

- `e2d8f22` — code15、澄清状态/语音隔离修正、18项新增回归及验收规范。
- `d4d909a` — 同步main最新规划文档（没有合并PR）。
- `ed3a978` — 本轮授权任务卡。
- 此次Windows脚本兼容、证据及交接提交见当前HEAD。**STOP等待确认，不操作手机或合并PR。**

---

# 历史：KITT V0.4.0 / #20 工程交付 — STOP 等待验收（2026-10-14）

> **2026-10-14 最新用户现场补充（覆盖下文早先“未听到地方讲述”的时间性结论）**：用户报告**无网络状态下已实际听到杨升庵的地方讲述**，真人耳听不再是只有设置中的固定中文试听；新状态 `PASS_USER_REPORTED_OFFLINE_PLACE_NARRATION`。但讲述**过于浅显**，只有身份与官职，缺少流放故事、文学贡献及《临江仙》；用户体验的**内容深度未验收通过**。当前JSON里杨慎对象仅4条事实，`OfflineNarrator` 首次固定 `take(2)`，对应“身份+状元官职”，后两条需“再讲一点”；《临江仙》未收入知识包。用户本轮未提供GPS精度/新鲜度日志、云请求计数、追问/打断/静音测试，因此不能扩大为所有细项完全 PASS。详情见 [#20 用户实测反馈](https://github.com/shimao1115/KITT/issues/20#issuecomment-6159486194)。**#20 仍OPEN；停止施工，不启动下一版本或修改 APK。**

**V0.4.0/code14 工程、全量测试、原签名 APK 和真机原位升级已完成。断网中文试听已由用户确认“听到了，中文清楚”；完整真实 GPS→新都识区→本地地方讲述仍 PENDING，两轮观察及普通前台服务均未取得新鲜物理位置。** 不把独立试听或 Fake 模拟算作完整黄金路径。Issue #20 保持 OPEN；本轮 STOP，不进入第二地区、下一版本或其他 Issue。

## 当前可运行能力与边界

新都单片包 `xindu-2026.10.14-1` 有6个对象、22条可追溯稳定事实，共102,734字节，包含独立来源、版本/哈希及ODbL许可。可信新鲜GPS/NETWORK→本地Polygon→确定性选择两条未尝试事实→既有Journey/TTS；支持有限“这里有什么/再讲一点/再说一遍”，复用静音、中断、完成/失败记忆，不假装离线自由问答。在线包只供原研究/Director作历史审校材料，不污染#18研究缓存或已播记忆。断网取消云端Director/Research，只用已安装本地中文声音及既有Vosk。真机发现VPN在双无线断开后仍保留VALIDATED，已以物理INTERNET能力辨别离线，IMS专网不计互联网。

几何为geoBoundaries/OSM许可明确的2017历史开源Polygon，1017顶点原样提取；精度≤50米、年龄≤15秒、GPS/NETWORK、误差圆+1000米退让带全在内部才识区，不猜街道、不借在线Geocoder完成离线主链。**它不是官方测绘，1000米不是已证明误差上限；2026边界调整权威复核与镇街仍PENDING。** 来源审校及取舍见[来源明细](docs/OFFLINE_XINDU_SOURCES.md)，工程/真机门禁见[独立验收](docs/ACCEPTANCE_OFFLINE_XINDU.md)。后续知识生产以最新[内容标准V1.0](docs/KITT_KNOWLEDGE_STANDARD.md)为准；该并行文档明确不扩本轮范围。

## 实际 build / tests / 模拟

- 最终 `scripts/verify.ps1 -Offline`：**Debug423 / Release410，failure/error/skipped均0**；新增28项每种Variant均执行，Debug/Release assemble、lint PASS。原签名回归2接受+6拒绝PASS。runtimecheck生产及AndroidTest构建/lint/验签PASS，45个生产Kotlin与包文件对照无差异。[完整工程证据](docs/evidence/v040-verification.json)。首次测试曾发现setVoice成功/getVoice为空的兼容问题，已修复后重跑，未隐藏失败轮。
- 成都新都→广汉→德阳→绵竹→绵阳安州雎水六组本机实际重跑PASS：full80km/h、29点/90,912米/68分钟、57调用/4声音；accelerated22调用/22声音；batch13章/23主题/23调用；landmark4节点/28调用；dossier13讲述/33调用/19研究；driving-area-resolution合成700米章节12/10查询、最短16秒。均为Fake Provider/Voice，不是真实路线、云模型内容或真人听感。
- 编译仅有既有deprecated警告。产物与日志留本机忽略的artifacts，未提交密钥、录音、轨迹、账号凭据或原网页全文。仅文档接续时同步最新main两项知识标准提交，生产源码/资产未变，无需重新生成APK。

## 原签名产物与手机事实

三个APK均0.4.0/code14、唯一原证书SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。**已有手机只使用runtimecheck数据空间。**

| 产物 | applicationId / 字节 | APK SHA256 |
| --- | --- | --- |
| [runtimecheck](artifacts/kitt-v0.4.0-runtimecheck.apk) | com.kitt.reader.runtimecheck / 74,868,969 | c041f917827dca99c06b5d4dbe5dc9093ab4dfecd12f941267140b94e6c2e76f |
| [Debug](artifacts/kitt-v0.4.0-debug.apk) | com.kitt.reader / 74,869,042 | cf5629a7b24c0dec9f93144e5310cc797b73d3ec3b9b7f545dd7edfe0adc15d4 |
| [Release验收](artifacts/kitt-v0.4.0-release.apk) | com.kitt.reader / 70,712,058 | 7a9ea3b0f0c117d442b0c2d12df3a2cb1ecb46e6730d339f790b7188dd1cb2de |

用户明确授权覆盖及断网试听。vivo首次拦截后，用户解锁批准，`adb install -r`成功；最终code14再次原位替换成功，手机公开base.apk摘要与本表runtimecheck完全一致。首次安装时间仍2026-10-09 09:42:45，最终更新2026-10-14 09:31:57；未卸载/清数据/导出凭据。两次有效离线Probe各120次观察：新鲜GPS=0、可信区县=0、讲述=0、实际Director/Research调用=0，设置比较不变。普通开始按钮亦确认LOCATION前台服务、系统定位开启和精确定位授权，约两分钟仍未知。已结束旅程、前台服务停止，恢复原Wi-Fi关/移动数据开。[脱敏手机证据](docs/evidence/v040-phone-acceptance.json)。

本地包额外读取/解析实测30.427及27.622毫秒；是初始化后的warm测量，**不是冷启动/内存实测**。系统声明2个本地中文声音；最终断网设置试听显示“试听结束”，用户亲耳确认中文清楚，因此独立离线TTS PASS。没有地方讲述完成/真人内容证据，完整黄金路径仍PENDING。没有注入坐标或延长位置可信期。

## 配置、剩余验收与最近提交

新安装默认保持Fake / gpt-4.1-mini / reasoning effort空。原手机历史配置为ChatGPT / gpt-6.1-sol / low；本轮UI确认ChatGPT计划启用、速度1.0、系统默认中文声音，但在线模型列表暂不可用，未伪称重新核对模型或精确code13账号字符串。未保存设置/重新连接账号，无新增OAuth、API密钥或签名准备待办。

下一次人工总验收只需：**停车/静止于新都区可信内部并取得新鲜精确物理GPS，关闭Wi-Fi/流量，普通“开始沿途”，听一段有来源地方讲述，再试补充/重播/打断/静音。** 手机已经最终code14，无需重装。若没有新鲜定位继续保留未知，按[最短验收](docs/ACCEPTANCE_OFFLINE_XINDU.md)记录云调用零与真人结果；边界权威复核另列PENDING。#13细分真人语义、#18镇街/Topic、#14/#17外场历史未验项保持独立，不在本轮继续施工。

- `a43de31` — V0.4.0/#20生产代码、22条来源事实、许可几何、28项新增回归、工程证据及签名打包（原c4185e1同步main后重放，源码相同）。
- `e11efbf` / `fa95f1f` — 同步接收主线知识标准定稿与历史蓝图标注，仅文档，无本版扩容。
- `f439f99` — 本轮开始时最新main的单片范围契约。
- 最终交接提交见Git当前HEAD：本节、手机脱敏证据、TASK_CARD/ROADMAP与#20验收更新。**STOP，等待一次V0.4.0人工验收。**

---

# 历史：KITT V0.4.0 授权入口（已执行）

**用户明确授权开始下一个小版本：V0.4.0 / [Issue #20](https://github.com/shimao1115/KITT/issues/20)。当前唯一施工入口 [TASK_CARD.md](TASK_CARD.md)。** 当前**只有授权、完整 Issue 和任务卡；没有启动本地 Codex、运行代码或生成 code14 APK**。下方此前 HANDOFF 中的“STOP 等待授权”在这里已被用户此次决定解除，历史实测事实仍然有效。

- **产品目标**：用新都区代表性20–50条已核验有来源稳定知识起步（数量以可证实为准），在真实无互联网的手机上实现**物理 GPS → 本地可信行政区反查 → 地区包本地查证 → 不调用云端模型的确定性讲述选择 → Android 本地离线中文TTS真实播报**。这才是 V0.4.0 产品黄金路径。单有旧缓存但无法识区或发声，不能宣称完全离线。
- **先验证最难的两处**：当前系统 `AndroidAreaResolver` 的 Geocoder 可能依赖系统在线服务；真正离线需要可信有来源有许可的区域几何/空间判断，不能使用伪造边界或 VPN/IP。其次远程 ChatGPT Director 不能离线运行，需一个小型本地确定性讲述路径；Android TTS 必须用断网可用的中文引擎且亲耳验收。
- **继承已通过成果**：0.3.6/code13 同签名升级与 Debug395/Release382、六组模拟工程 PASS；用户反馈“没有多大问题”，#13记录阶段性可用认可但真人同义重复/补充/重播完整听感仍 PENDING。0.3.5.2新都区区县研究真机write/hit/零重复 PASS，镇街/Topic等待自然验收；#19办公室+家庭原签名统一CLOSED/PASS；#14/#17外场定位未全部验收。
- **范围**：这是“成都试点的第一片新都区”，优先完成真实闭环再扩大。仓库长期蓝图 [docs/FUTURE_OFFLINE_KNOWLEDGE_PACKS.md](docs/FUTURE_OFFLINE_KNOWLEDGE_PACKS.md) 的成都全域、NAS批量生产与省包下载属于**后续**，不是本版需要一次实现的项目。
- **手机保护**：当前 `com.kitt.reader.runtimecheck` code13，目标 V0.4.0/code14；原签名证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。两个 PC 均已配置，做新版本只用原签名并合法递增，禁止卸载、清数据、暴露账号私钥。下一轮开发应先执行环境回归。
- **执行与 Stop**：施工 Agent 先按根 `AGENTS.md` + `TASK_CARD.md` + #20 + 本交接理解，独立实现、全量测试、可执行 APK、脱敏验收证据并推送；仅本小版本，交付即停止。不能把工程/Fake 结果冒充真人真断网。别顺手修 #13/#14/#17/#18或开展V0.5.0。

---

# KITT 家庭签名迁移与验证完成 / #19（2026-10-13）

**双机签名统一 PASS：办公室端为历史实测证据，家庭端已在本机完成原私钥导入、限权、预检和 V0.3.6/code13 构建验签。** 从最新 main `eded04d` 干净快进继承，仅提交本段交接与[脱敏家庭证据](docs/evidence/signing-home-verification.json)，没有生产代码、版本号或签名配置修改。任务完成后 STOP，不开启其他 Issue 或版本。下方交接均保留为历史，其家庭签名 PENDING 已由本段实际结果取代。

- 源文件先经 `scripts/check-kitt-signing.ps1` 实核为原 PrivateKeyEntry；目标原先不存在，原样复制到仓库外专用签名位置。源/目标 SHA256 内部比较及逐字节比较一致；目录和文件 ACL 均只允许当前用户与 SYSTEM，禁用继承，并在验收末尾再次核对。源文件保留，全局 debug.keystore 未修改，没有生成、提取或上传私钥。
- 源和家庭目标预检、runtimecheck 生产/测试 APK、标准 Debug/Release 验收 APK 均为唯一原证书：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。本地私钥实际路径、密码、文件字节和私钥文件摘要均未写入本次公共交接或证据。
- 家庭实际执行 `scripts/build-runtime-smoke.ps1 -Offline`：assembleDebug、assembleDebugAndroidTest、lintDebug PASS；4m25s，75任务执行。62个生产文件与 main 一致，只有既有脚本在独立工作区修改包名和应用标签。交付后再次运行 `check-kitt-signing.ps1 -ApkPath artifacts\kitt-v0.3.6-runtimecheck.apk -ExpectedApplicationId com.kitt.reader.runtimecheck`，包名、0.3.6/code13、唯一原证书 PASS；测试包仅构建验签，没有执行 instrumentation。
- 家庭实际执行 `scripts/verify.ps1 -Offline`：Debug395 / Release382，失败、错误、跳过均0；两种 Variant 的 tests、assemble、lint 全部 EXECUTED PASS。12m35s，115任务中61执行/54复用；单测实际重新执行，未借用办公室测试数。JDK Temurin17.0.19、SDK35/build-tools35.0.0，离线依赖齐备。
- 家庭实际执行 `scripts/test-kitt-signing.ps1 -RuntimeApkPath artifacts\kitt-v0.3.6-runtimecheck.apk`：2接受+6拒绝 PASS，包括缺密钥/缺APK/无APK包名约束/错包名/未签名APK/匹配公钥但无私钥；临时公开证书样本已清理。

## 家庭 V0.3.6/code13 产物

三个生产 APK 均为0.3.6/code13并通过原证书验签，APK和构建日志仅保留在本地忽略的 artifacts。办公室/家庭 APK 字节摘要可以不同；标准包 `com.kitt.reader` 仍不能替代已有 runtimecheck 数据空间。

| 家庭产物 | applicationId | APK SHA256 |
| --- | --- | --- |
| [runtimecheck](artifacts/kitt-v0.3.6-runtimecheck.apk) | com.kitt.reader.runtimecheck | 554be971d07249fc1093666f92a66aa73c5011931df45ddb0fd1ac528030cf23 |
| [Debug](artifacts/kitt-v0.3.6-debug.apk) | com.kitt.reader | 7ec61418556c8b3ec8544d0faa87964a4ffaae0ce17921654daf3fd869baee0c |
| [Release验收](artifacts/kitt-v0.3.6-release.apk) | com.kitt.reader | 3461772f8ee13cbb48df780fa0a2175e03db9bf1424b74ae21b68eec3562ed76 |

## 模拟、限制与最小待办

- 本机全量测试重新生成成都新都→广汉→德阳→绵竹→绵阳安州雎水六组报告：full80km/h、68模拟分钟、57调用/4声音；accelerated22调用/22声音；batch13章/23主题/23调用；landmark4节点/28调用；dossier13讲述/33调用/19唯一研究；driving-area-resolution合成700m章节12/10查询、最短16s。均为确定性/Fake Provider和fake Voice，非真实路线、模型内容或真人听感验收，报告摘要见家庭证据。
- 本轮无 adb、手机安装/卸载/降级/清数据、账号/凭据或设置读写，也未调用真实 Provider/研究搜索。原手机已code13，本轮不重复安装；跨机 APK 原位升级 NOT RUN。#13真人语义/听感/补充重播、#14/#17外场与#18剩余现场验收状态保持。
- 签名迁移已无授权/密钥待办。新安装默认仍 Fake / gpt-4.1-mini / effort空；原手机 ChatGPT / gpt-6.1-sol / low 为历史配置，本轮未读写。签名复核最短步骤：在仓库运行上述 runtimecheck `check-kitt-signing.ps1` 命令，核对本表摘要即可，无需操作手机。产品人工总验收仍按下方#13既有步骤另行授权。
- 已提醒同步目录中的源密钥可能在同步端留存，建议用户验证后自行清理同步副本或转存加密介质；未自动删除、移动源文件或上传额外备份。办公室原密钥使用权不变。
- 最近相关里程碑：本次家庭验收文档提交（见Git日志）；`eded04d` 家庭导入唯一任务；`8753240` 办公室原签名工具链验证；`0cc7e68` V0.3.6/code13工程。完成提交/推送及 Issue #19 脱敏更新后停止，工作树只包含文档交付。

---

# KITT V0.3.6 / #13 工程交付 — 停止等待验收（2026-10-13）

**工程施工、全量测试、六组模拟与原签名打包完成，代码提交 [`0cc7e68`](https://github.com/shimao1115/KITT/commit/0cc7e68a867742852c46e000fdc506386eab3862)。用户恢复手机连接后，本版真实ChatGPT→自动旁白→TTS引擎完成已取得一次证据；正常设置已核对。用户反馈“没听到或当时不在旁边”，真人听感、同义去重、补充/重播仍PENDING。停止手机操作，#13保持OPEN等待本小版本人工验收，不自动开启下一版本。** [精简验收](docs/ACCEPTANCE_NARRATION_MEMORY.md)、[本版实际验证](docs/evidence/v036-verification.json)、[手机阶段性事实](docs/evidence/v036-phone-acceptance.json)。下方旧授权/旧交接只作历史。

## 当前可运行能力

同趟最近32条覆盖分开记录地区、对象、事实/角度标签、landmark_id与播放生命周期，不再将topic与memory_update混用。相同对象已覆盖标签的子集/重排不自动重讲，同对象新事实、不同对象、跨区同名与独立地标仍有机会。单一Director在原请求中判断换措辞/别名/Overview→Topic回流的语义重复，沿用对象title和事实标签；没有新Schema、额外语义模型、Reviewer、向量库或数据库。

排队、真正onStart、最终成功回调、失败、未开始取消、开始后打断分别记QUEUED/STARTED/COMPLETED/FAILED/CANCELLED/INTERRUPTED；仅成功完成进入已讲摘要，epoch和语音序号拒绝旧/重复回调。失败/半截不冒称完整听过，也不自动重放。**引擎完成不证明真人听见；模型将同义事实改成全新对象/标签仍可能逃过本地防线，真实语义/听感待验。**

“再讲一点”复用原主动查证链，只补未覆盖材料，重复/无可靠新料简短说明；普通问题/看图正常接管。“再说一遍”本地重播本进程最新一段，0额外Director/研究调用；只留这一段内存正文，不写恢复文件，结束/新Journey/进程重建清空。旧恢复摘要仍兼容，但标为未验证背景，不变成完成覆盖。PREPARE/安静/用户中断/过时与来源规则保持；#18持久来源缓存不作为已讲记录。独立地标只消费额外唤醒，空间有效时新事实仍可用。

## 本轮实际 build / tests / 回归

- 最终 `scripts/verify.ps1 -Offline`：**Debug395 / Release382，失败、错误、跳过全0**；新增30项两种Variant均实际执行。Debug/Release的tests、assemble、lint均EXECUTED PASS，生产包签名PASS；115任务33执行/82复用。没有复制旧版365/352成绩。编译器既有deprecated警告不影响通过。
- 工程交付时 `scripts/build-runtime-smoke.ps1 -Offline`：assembleDebug、compileDebugAndroidTestKotlin、lintDebug均EXECUTED PASS，75任务13执行/62复用；assembleDebugAndroidTest首轮EXECUTED PASS、工程复核UP-TO-DATE。生产/测试APK唯一原证书，40个生产Kotlin文件与最终施工源码逐一一致。恢复手机后仅修正测试Probe文案及断言，再构建12s：compileDebugAndroidTestKotlin、assembleDebugAndroidTest、lintDebug EXECUTED PASS，assembleDebug UP-TO-DATE，75任务7执行/68复用；测试包安装并实际instrumentation返回code0。生产APK摘要不变，本次接续未重跑上述全量单测/模拟。
- `scripts/test-kitt-signing.ps1`：2接受+6拒绝实际PASS，未生成/导出/提交私钥。旧实现两条红测确实失败；迁移播放前题材历史/整节点去重旧断言后最终全量通过。
- 同一粗粒度路线13章刻意3次检查：**26新对象/事实输出、13重复SUPPRESSED、SILENT0、STALE/FAILURE0**。这只是标签/状态确定性证据，不是真模型语义。
- 成都新都→广汉→德阳→绵竹→绵阳安州雎水六组前后均本轮实际执行：full57调用/4声音；accelerated22/22；batch13章/23主题/23调用；landmark4节点/28调用（SPEAK4/SILENT24）；dossier13讲述/33调用/19唯一研究（SPEAK13/SILENT20）；driving-area-resolution合成700m章节80/100km/h含报速0为12/10查询、最短16s。前后计数保持，正常组无STALE/cancel/failure；Fake/假Voice、29点90912m，非实车/真人内容验收。详表与报告时间/摘要见验证JSON。

## V0.3.6/code13 本地原签名产物

三个生产APK均0.3.6/code13，唯一证书SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。APK及原始构建日志只在本地忽略的artifacts，未提交到GitHub。

| 文件 | 包名 | APK SHA256 |
| --- | --- | --- |
| [手机runtimecheck](H:/CODEX/KITT/artifacts/kitt-v0.3.6-runtimecheck.apk) | com.kitt.reader.runtimecheck | 28795780faeff52b394255cd4c73aa540d64572d4ce72304638f084f48626a81 |
| [标准Debug](H:/CODEX/KITT/artifacts/kitt-v0.3.6-debug.apk) | com.kitt.reader | 7a536b523c9832a2a53b60d1bdc57ac632ff088275dd7bccd52f015794f451af |
| [标准Release验收](H:/CODEX/KITT/artifacts/kitt-v0.3.6-release.apk) | com.kitt.reader | 5bf3df44ccef5f59413aa4ee616ac7bf751158579a464b2f4934aa7c89aa161f |

标准包不是原手机账号的数据空间，不能替换runtimecheck。若在其他未升级设备安装，先核对原包名/证书和现有code<13，再用 `adb install -r artifacts\kitt-v0.3.6-runtimecheck.apk`；不卸载、不清数据、不降级。原手机已code13，无需重复安装。

## 手机事实 / Provider / 用户最小待办

- 原手机code12/base.apk已核同证书、同包名。用户本人允许vivo“超级守护”安装后，`adb install -r`返回**Success**；已核0.3.6/code13，更新时点11:34:03，首次安装时间2026-10-09 09:42:45不变。没有卸载、清数据、降级或标准包替换。
- 恢复手机后的正常UI核对：已连接账号1个、ChatGPT/GPT-6.1-Sol/low、语速1.0×、系统默认中文声音、通知允许、独立Responses关闭，均符合既有设置；Probe内配置前后相同。账号文字与暂存的**升级后**UI在内存中比对一致；code12→13精确账号文字比较未留存，不能补造。原UI脚本检查过早：暂存界面仍是“等待账号模型列表”，模型/effort控件不存在，足以令原检查失败；不据此推断数据丢失。未读取/导出凭据或原始账号文件，临时手机UI XML已删除。
- **真实自动讲述链 PASS_ENGINE_CALLBACK_ONLY**：修正后Probe观察98s、新鲜位置98s，ChatGPT/gpt-6.1-sol/low自然地区自动SPEAK_NOW1，STARTED→COMPLETED1，STALE/FAILURE/SUPPRESSED均0。简短主题为“杨慎（杨升庵）与升庵祠、桂湖”。没有注入坐标/移动，测试临时旅程偏好不问目的地；不是连续行驶或真人语义验收。终端研究字段NEVER_ATTEMPTED不代表无研究：当次诊断见后台Topic started，未证明Topic完成或网络调用数，#18不改PASS。
- 首轮Probe测试文案“无资料可安静”误触安静命令，180s观察/170s新鲜位置但无讲述，该次无效，不算去重成功。仅测试文案改为“无资料则SILENT”并断言非安静；生产代码及APK未变。另一次普通UI开始自然ASK_USER/TTS完成，并有地区就绪前STALE，单独记录，不混入上述旁白计数。有效Probe正常结束并回出发页；进程重启清除了临时诊断日志，只留脱敏结果，不把空日志计数冒充0。
- **真人听感/同路段语义/补充/重播PENDING**：用户回答“没听到或当时不在旁边”；既不能证明听见，也不能判定无声故障。没有为补听继续调用模型。新安装默认仍 **Fake / gpt-4.1-mini / effort空**；原手机既有 **ChatGPT / gpt-6.1-sol / low** 已实际运行，本版无新增OAuth/密钥步骤，生产包与测试包vivo安装授权均由用户完成。
- 用户随后明确要求“再次调用一次我再听听看”，另运行一次有界Probe：180s观察/171s新鲜位置，自动FAILURE1、SPEAK_NOW/COMPLETED均0，Provider返回`server_is_overloaded`，未触发TTS；instrumentation code0、配置不变，正常结束。该次是请求失败，不是无声或去重证据；不覆盖上一轮成功链，未继续手动重试。[本次结果](docs/evidence/v036-phone-listening-retry.json)。
- 下一次最小动作：停车/乘客状态普通开始，实际听完一段后，在**同一Journey、结束之前**试“再说一遍”和“再讲一点”，检查同段不换词重复、新事实仍开口及打断/安静；再结束/新Journey。当前Probe已结束，上一段重播正文按设计清空。不要为刷日志重复付费搜索。**本轮到此停止。**
- #18真实区县缓存的阶段性接受仍有效，镇街/Topic及真人语音未全验收；#14/#17外场定位、#19家庭签名仍独立PENDING。未做定位、网络/天气校验修复或V0.4.0离线包。

## 最近milestone commits

- 本次接续 — 仅修正测试Probe安静误触、归档code13真实讲述和设置核对；生产APK保持不变。对应提交见Git历史。
- `5d48b31` — 原工程交接及用户暂停手机验收的历史记录。
- `0cc7e68` — V0.3.6/#13覆盖记忆、诚实播放生命周期、补充/本地重播、地标新事实、30项新回归、code13与本版证据。
- `1e17de1` — 当前任务卡/授权基线，只开V0.3.6，保留V0.3.5.2阶段性结果。
- `519743f` — code12真机原位升级及真实区县跨进程缓存验证。
- `f7cbd6b` — V0.3.5.2/#18持久有来源研究、code12。

**STOP：工程工作及一次真实自动讲述引擎链已完成，等待用户实际聆听与本小版本验收决定。Issue #13保留OPEN，不自动进入下一Destination。**

---

# 历史：V0.3.6 讲述语义去重授权入口（2026-10-13）

**用户已授权下一小版本 V0.3.6 / [Issue #13](https://github.com/shimao1115/KITT/issues/13)，当前唯一任务 [TASK_CARD.md](TASK_CARD.md)。这是施工入口和授权，不表示 Agent 已运行、功能已实现或 APK 已产出。** 前一版本关于“不得自动开 #13”的 stop rule 已在小版本阶段性验收后由本次**明确授权**取代；前一版未完成的验收和安全约束依旧有效。

- **正式目标**：纠正 Journey `topic` 与 `memory_update` 混用，避免重复讲同一对象/事实/角度，只有确有证据时记录播报完成；“再讲一点”提供新证据、“再说一遍”允许主动重播。禁止压成整趟 SILENT，不添加额外语义模型判重、向量库或大规模架构。
- **基线**：手机当前 `com.kitt.reader.runtimecheck` V0.3.5.2/code12；区县新都区真实 `write=1 → force-stop → hit=1 / 重复Overview研究=0`、账号/设置保留已获用户阶段性 PASS；#18 镇街/Topic、持续在线讲述/真人语音未全验收，不因升级改成 PASS。
- **要先区分真实音频与网络故障**：先看当下真实 Provider 是否能完成至少一段自动 Director→TTS 讲述；上版真机出现 `UnknownHostException` / `STALE` 与天气资料校验 `IllegalArgumentException`，这些不是证明语义去重有效或无效的依据。若当时网络无法可靠使用，不阻塞单测和模拟开发，但真机听感必须标 PENDING。
- **同签名原位升级边界**：建议V0.3.6/code13；证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`；只允许同包名 `com.kitt.reader.runtimecheck`、版本号合法递增，不卸载、清数据或导出登录信息。办公室签名通过，#19 家庭端仍 PENDING。
- **施工 stop**：只完成 #13，测试/交付并更新HANDOFF、ROADMAP、Issue后停止。#14/#17定位、#18剩余现场、#19家庭签名、V0.4.0离线知识包均不自动施工。

---

# KITT V0.3.5.2 用户阶段性验收决定（2026-10-13）

**用户接受阶段性结果：工程 PASS；手机安全升级、账号设置保留、真实新都区 Overview 跨进程缓存 PASS。#18 仍 OPEN，以补齐镇街/Topic、持续在线讲述和真人语音的现场证据；这不是“手机总验收 PASS”。** [用户验收决定已记录在 Issue #18](https://github.com/shimao1115/KITT/issues/18#issuecomment-6137058814)。

- 真正手机链路：自然完整区县身份，首次研究成功 `write=1` → 结束并 force-stop → 新进程相同区县 `hit=1` / 重复 Overview 研究调用 `0`；Director 新旅程机会重建。
- 今日天气问题走实时搜索，但资料校验失败后明确无法确认：**实时路由和诚实降级 PASS，天气答案质量及持续在线能力不在 PASS 范围内**。网络异常与合规材料校验失败根因尚未确定。
- 镇街身份未建立、Topic 未自然研究，二者跨重启性能仅有合成回归，真实验收依旧 PENDING；真人 ASR/TTS 听感、播放中打断和持续在线讲述 PENDING。
- **施工状态：停下，不重新开发已通过区县缓存，不自动开启 #13、离线包或额外 Hotfix。** #14 / #17 外场定位、#19 家庭签名继续独立跟踪。本文件下方的“等待用户决定”是作决定前的历史记载。

---

# 当前验收任务：V0.3.5.2 / #18 真实手机原位升级与缓存复用（2026-10-13）

**实际验收：2026-10-13 10:28–10:37（Asia/Shanghai），vivo V2405A / Android16。安全原位升级 PASS，账号/设置保留 PASS，真实新都区 Overview 跨进程缓存 PASS；镇街、Topic、真人语音与手机总验收 PENDING。#18 保持 OPEN，等待用户最终决定。** 脱敏实测见 [验收记录](docs/ACCEPTANCE_RESEARCH_CACHE.md#2026-10-13-真实手机验收) 和 [手机计数摘要](docs/evidence/v0352-phone-acceptance.json)。

- 办公室现成 APK 的摘要、唯一签名、包名/code12 和原手机 code11/同证书全部实核；`check-kitt-signing.ps1` PASS。仅执行 `adb install -r`，返回 Success；安装后手机 base.apk 摘要一致，数据目录标识和首次安装时间不变。未卸载、清数据、降级或替换标准包。
- 常规设置界面升级前后账号显示在内存中比对一致；ChatGPT、GPT-6.1-Sol/low、语速1.0×、系统默认中文声音、通知允许、独立 Responses 研究未启用均保留。没有读取或导出凭据/原始账号文件。首趟真实研究成功证明当次在线能力；不保证后续网络恒定可用。
- **区县实测链**：自然新鲜 NETWORK、完整四川省/成都市/新都区身份；首趟 Overview研究调用=1、搜索开始/完成=1/1、来源校验完成、facts=4、write=1 → 正常结束 → `am force-stop` 后进程不存在 → 新进程普通开始、新鲜同区县 → overview hit=1、重复 Overview研究调用=0；Director自动启动2次。没有套用合成0/0/0。镇街身份未建立，Topic未自然完成，各自PENDING。
- 新进程自动Director先出现 STALE、随后 `UnknownHostException`/FAILURE；缓存仍成功载入并无重复研究。主动打字问一次今日天气，Director判 SEARCH_REQUIRED，实际搜索开始/完成=1/1，但结果校验 `IllegalArgumentException`，App明确显示“刚才没查到可靠资料，暂时无法确认。”，语音完成回调=1；没有写稳定缓存。实时路径/诚实失败 PASS，今日天气内容 PENDING；不自动重试或修复别的issue。
- 启动/结束页/回到出发页、安静10分钟/提前结束、打字提交短测PASS；TTS有实际开始和完成回调，但真人听感、实际说话识别和播放中打断未签PASS。观察窗口无 AndroidRuntime FATAL EXCEPTION；不是无限期无崩溃保证。手机已正常结束旅程并停在出发页。
- 本轮仅验收与文档，未重建或重跑既有工程测试、模拟。下面保留安装前工程交付历史；Debug365/Release352和成都→绵阳六组模拟仍仅代表确定性/Fake结果。没有新密钥或OAuth待办；最小用户动作是审阅本次阶段性结果并决定接受与否，真人语音/连续镇街按现场可用性补验。#14/#17外场与#19家庭签名仍PENDING，不开发#13或离线包。

---

# 安装前工程交付历史：沿途 V0.3.5.2 — 地区研究证据缓存交接

**2026-10-13（Asia/Shanghai），0.3.5.2 / code12，#18 工程交付 PASS；手机原位升级、杀进程重启与真实内容验收 PENDING。到此停止，等待一次小版本人工验收。** 工程提交 [`f7cbd6b`](https://github.com/shimao1115/KITT/commit/f7cbd6bd21bfc33994a3d35fb2c23b4ddac5bbd3)，[精简验收](docs/ACCEPTANCE_RESEARCH_CACHE.md)、[本版验证摘要](docs/evidence/v0352-verification.json)、[零重复研究对照](docs/evidence/v0352-research-cache.txt)。#18 保留 OPEN 等待人工验收，未启动 #13、离线包、定位重构或下一轮 UI。

## 当前可运行能力

有来源并经既有 completed-hosted-search/provenance 契约核验的 Overview/Topic 保存至私有 filesDir/research-evidence-v1；完整 AreaIdentity.key 区分区县/镇街，Topic 按地区与格式规范后的主题取。版本化 JSON、内容摘要校验、同目录原子替换；单项40000 UTF-8 bytes、128项、2MiB。坏/旧/空/假/无依据或错误地区资料不写入，存储失败不阻止成功研究用于本次讲述。模拟/Fake 不读写生产证据。

结束或重新开始只清运行态；下趟从磁盘载入证据、重新产生 Director 机会，不恢复 examined、pending、已播去重或固定旁白。历史证据明确“此前有来源的研究（本次未联网复查）”与原 searched_at；稳定事实不按 start/TTL 强制重查。明确查最新/重新研究以及开放、票价、天气、路况、临时展览和时事仍走实时研究，失败简短报告，动态答案不进入稳定缓存。未增加离线 Director：远程 AI 全断网时自动模式安静降级。

既有 GPS+NETWORK、行政区连续性和新鲜度、模拟路线、语音/打字/看图接管、安静/提前恢复、ASK_USER、PREPARE、评分保持。没有修改 Fix 老化、定位来源、FUSED、VPN/IP 边界或语义去重。没有原网页、请求、对话、录音、轨迹或凭据落入证据缓存。

## 本版实际 Build / tests / 签名

- 最终 `scripts/verify.ps1 -Offline` 实际执行：**Debug365 / Release352**，失败/错误/跳过均0；新增16项缓存测试在两种 Variant 均执行。单独相关 Debug29项（缓存16 + 主动研究13）也实际通过。
- 最终 Debug/Release 的 tests、assemble、lint 均 EXECUTED PASS，标准包验签 PASS。Gradle最终115任务：33 executed / 82 up-to-date；未变资源/依赖任务复用，本版单测任务没有复用0.3.5.1结果。首轮全量也通过；对齐旧系统提示/设置说明后以上最终源再次完整运行。
- `scripts/build-runtime-smoke.ps1 -Offline` 实际执行 assembleDebug、assembleDebugAndroidTest、lintDebug并验签：75任务，25 executed / 50 up-to-date。runtimecheck生产与测试APK证书检查 PASS；**仅构建测试APK，没有执行手机 instrumentation**。
- `scripts/test-kitt-signing.ps1` 实际执行2正例 + 6拒绝例 PASS（缺密钥/缺APK/仅包名约束/错包名/未签名/只有匹配公钥无私钥）。仅临时公开证书样本，没有导出/提交原私钥。
- 缓存合成对照：首次区县 Overview=1、镇街 Overview=1、Topic=1，write=3；clear/end后新管理器+新磁盘存储对象、研究 Provider拒绝网络：同三类 fetch=0/0/0，hit=3，新旅程 Director实际再次判断。**这是磁盘重建测试，不是手机杀进程或真实 Tokens 采样。**

## 成都新都 → 绵阳安州雎水：六组本版回归

全部由最终完整单测重新执行，确定性/Fake Provider与fake Voice，非实车/真模型；路线29点、90912m，为粗粒度测试折线。当前报告头部、时间与摘要在验证JSON，原始报告在本地 artifacts。

| 报告 | 本版结果 |
| --- | --- |
| full-simulation | 80km/h、68模拟分钟；57 Director调用、4 TTS；接管/安静/结束 PASS |
| accelerated-simulation | 100km/h、16×；22调用、22声音输出；零 stale/cancel/failure |
| batch-simulation | 13章节，23选题/23调用；零 stale/cancel/failure（不沿用历史14段数字） |
| landmark-simulation | 13章节，4独立地标，28调用；零 stale/cancel/failure |
| dossier-simulation | 13章节、19唯一研究、13讲述、33调用；零 stale/cancel/failure |
| driving-area-resolution | 700m合成章节；80/100km/h与报速0条件12/10查询，最短16s；非真实Geocoder路线 |

#17 的600秒新鲜合成定位/120秒刷新失败等回归及 #14 的 GPS/NETWORK、粗/旧位置、用户打断、ASK_USER、安静和过时护栏均包含在本版全量通过结果中；真机外场仍各自未全验收。

## 办公室同签名交付 APK

三个生产APK均 **versionName=0.3.5.2、versionCode=12、单一 signer**，证书 SHA256：
`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

| APK | applicationId | APK SHA256 |
| --- | --- | --- |
| [手机 runtimecheck](artifacts/kitt-v0.3.5.2-runtimecheck.apk) | com.kitt.reader.runtimecheck | 4e9526b932801ca000c00ee552e086d74820023ccf48580b31d328da92fa7b88 |
| [标准 Debug](artifacts/kitt-v0.3.5.2-debug.apk) | com.kitt.reader | 6e48fec824f372a122f29faa6bbdfad92cdd35f6b2e4f33e1244401a9dec87ad |
| [标准 Release验收](artifacts/kitt-v0.3.5.2-release.apk) | com.kitt.reader | 4adb512bc09b9d542a16c49673d8d9d628ac0c94d33687e3f4f425cfd31400c2 |

标准包不属于原手机账号空间；交付APK和原始构建日志在本地忽略的 artifacts，未提交二进制或密钥。办公室通过不等于家庭端通过。

## 最短原位升级与人工验收

本轮**没有 adb、连接手机、安装、卸载、清数据、账号/设置/凭据读取或真实 Provider请求**。手机仍以已有0.3.5.1/code11历史记录为基线，本版实际升级 PENDING。

1. 在办公室核对 runtimecheck 文件摘要和上述证书、包名，确认手机已有版本仍低于code12，再于仓库根目录运行：
   `adb install -r artifacts\kitt-v0.3.5.2-runtimecheck.apk`
   保留原App，不卸载、不清数据，不使用标准包替代，也不降级。
2. 用现有账号普通“开始沿途”，在新鲜完整新都区/镇街身份下首次成功研究概况与一个专题后结束；观察 write，再杀进程重启、同地开始：区县/镇街/已有专题 hit，重复研究调用=0；Director仍做本趟判断。已研究对象再次讲述不等于缓存失败，#13本轮未做。
3. 问查最新/今天开放/票价/天气等，确认实时研究或明确失败；安静/提前恢复、说话/打字打断、ASR/TTS与结束正常。不要为证明命中反复触发真实昂贵搜索。详见精简验收文档的5步检查。

## Provider / 用户最小待办 / 已知限制

- 新安装默认仍为 Fake / gpt-4.1-mini / effort空；未改设置默认或 Provider架构。原手机已保存 ChatGPT / gpt-6.1-sol / low 是0.3.5.1历史证据，本轮未读取或验活；同包名同签名递增升级应保留数据，**本版保留结果尚未实测**。
- #18无需新增密钥/登录步骤。剩余最小动作是上述原位升级与小版本人工验收。真实新都内容/来源质量、手机杀进程重启、OEM文件系统断电行为、真人ASR/TTS及Tokens节省比例均未测；已有来源URL核验不等于独立语义事实审计。
- #17 真机连续镇街仍待验；既有600秒自然现场仅305秒新鲜、名称缺失与定位间断结论不重写。#14室外GPS↔NETWORK独立待验，不用延长旧Fix掩盖。
- **#19保持 OPEN/PENDING：办公室签名已 PASS，家庭端本人安全携带密钥、复制和验签未做，非本轮阻塞。** 最小家庭动作见 [SIGNING_UNIFICATION](docs/SIGNING_UNIFICATION.md)；不要发送或上传私钥。

## 最近 milestone commits

- `4b1f061` — 本轮安全code12原位升级/真实区县缓存验收任务交接，随后实测结果见本文件顶部。
- `32fd345` — V0.3.5.2工程交付、签名APK与合成验收交接；手机结果当时PENDING。
- `f7cbd6b` — V0.3.5.2/#18有来源地区研究持久化、16项新测试、历史证据/实时语义与code12。
- `8faf246` — 最新恢复#18授权，家庭签名后续不再阻塞。
- `8753240` — #19办公室原密钥构建/验签与签名正负例。
- `74161cf` — V0.3.5.1行政地区稳定工程基线，实机部分仍待验。

旧交接原文可查 [施工前HANDOFF存档](https://github.com/shimao1115/KITT/blob/8faf246/HANDOFF.md)。当前只等待V0.3.5.2人工验收，不继续下一Destination。
