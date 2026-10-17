# KITT V0.6.0 D1：县级Fact/Narrative可复现打包与可信签名验证（本轮唯一工程任务）

> **2026-10-17 用户批准进入“下一阶段”**，接续已合并的 [D0 PR #37](https://github.com/shimao1115/KITT/pull/37)，merge `1330574d15deb55bc8881783c55e889ddf77ddf0`。唯一需求 [Issue #38](https://github.com/shimao1115/KITT/issues/38)，唯一分支 `feat/v060-d1-signed-county-packs`，唯一交付 [Draft PR #39](https://github.com/shimao1115/KITT/pull/39)。本阶段定义为 **D1：电脑端可复现县级双包候选、固定签名字节协议、用显式预置公钥离线验证**，不实施真实发行、GitHub Release、第二仓库、手机下载/导入/激活。目标为独立 Draft PR＋真实验证证据，交付STOP等待用户验收；**D2**才处理真实发行密钥的人工治理与受控发布。
>
> **现行基线**：main含 code20 D0县级通用内存Loader，Debug540 + Release527 = 1067项工程PASS（历史记录，D1必须重跑）；手机 vivo 上仍是 code19，code20只静态验证同签名兼容，尚未装机。新都**1县/6对象/22基础事实/6基础来源**，标准正式完整故事 **0**，杨慎两篇仍 `candidate/fact_gate=passed/visitor_gate=pending`，不准让任何候选正文进入可发行知识包。用户延期的设置页两个保存按钮间距小留到之后UI优化；`settings.xml` 首进旧设置页字节漂移根因 UNKNOWN、旧外场 #23/#24/#27/#30仍PENDING。

## 0. 开工读取与保护

先保护本地工作树、所有 worktree 和忽略目录，`git fetch` 后校验本分支基于最新main的 D0合并SHA，阅读 `AGENTS.md`、本任务卡、`HANDOFF.md`、[D1审计](docs/reviews/V060_D1_RELEASE_FORMAT_AUDIT.md)、[D0 schema](docs/COUNTY_PACK_D0_CONTRACT.md)、[内容规范](docs/KITT_KNOWLEDGE_STANDARD.md)、[知识工厂](docs/KITT_KNOWLEDGE_FACTORY.md)、[包管理](docs/KITT_OFFLINE_PACK_MANAGEMENT.md)，与 `CountyPacks.kt`/`StoryPacks.kt`及D0新专项。Qoder/Codex不可同时修改同一分支。**旧D0任务已完成，不重复施工**。

## 1. 黄金路径

1. **只读内容输入与门禁**：接受固定Git commit、确定性文件集；输入事实/来源/Claim/许可/审稿状态必须可追溯，包内未审核 Narrative 永不伪装成 approved。默认本阶段只做**新都Fact-only内部候选**，若涉及未确认许可或从旧 `ys-facts` 含来源权利不明项，则 FAIL/BLOCKED并退化为**完整 TEST_ONLY 合成样板**，不能自动全部标 `allowed`。D0标准资产和正式审批一个字节不改。Narrative 无合格正文时是**明确的0篇结构**，不得将真实未审候选从 content 复制入包，更不许迁移其审批状态。市州目录为县ID索引，不复制正文。
2. **确定性双包容器/索引**：基于 D0 Schema2及四段Fact/Narrative `manifest+UTF8 bytes`，定义**版本化、定序、有限大小与路径的发行容器**（可选确定性ZIP或等价简单字节帧，避免双协议）、分发索引和按县 `area_id` 验证边界。两层内容各自独立版本/摘要，签名元数据具 `key_id`、发行范围/流水版本、`source_commit`、`minimum_code`、schema、所需Claim修订及完整文件哈希。整包摘要与签名处理须避免自哈希循环、不同顺序歧义和ZIP路径穿越/重复成员/压缩炸弹。所有字段长度与资源上限有拒绝测试。
3. **可复现输出**：固定源版本、固定builder版本及同一签名密钥/算法环境，在**两个全新独立目录**重复构建时 unsigned Fact+Narrative原始字节与整体哈希必须逐字节相同；不嵌入当前时间、绝对路径、用户名、机器指纹或Git脏状态。若签名算法本身非确定性，要**分别证明 unsigned 字节复现**，不要虚报签名包哈希固定，或采用恰当确定性标准方案并说明其安全性。签过的内容任何更改必须验证失败。
4. **签名可验证而不越权**：选择JDK17与Android **minSdk26** 兼容的成熟JCA标准签名方案，充分测试真Android API可用性（若本轮不能完成实设备，严格记PENDING）。由调用方提供**可信锚（key_id→public_key）**，验证先建立可信签名，再验证Content/Claim/Source/Visitor Gate，不能把输入自带公钥或 `trusted=true` 当信任；`SHA256`只能证明完整性。**D1 无真正生产签名根**，只在TEST_ONLY或临时本地安全目录用一次性测试密钥完成签名/验签闭环；不要把私钥/seed/keystore/密码打印、传到PR、存储到Git；**不可复用Android APK私钥**，不能让标准App因测试公钥接受外部包。
5. **准入和版本边界**：先验可信密钥/签名/地区/类型/版本/依赖/尺寸，再交D0县级解析器严格审核；县Fact和Narrative的对调/另县拼接、伪造City索引、不认识的publisher key、签名算法混淆、旧scope重放、明确倒退版本等 fail closed；外部传入但无真正publisher公钥时**永不标正式已就绪**。只验证分发候选，**本阶段无手机侧路径、网络下载、原子启用或持久安装**，也不运行真机。
6. **本地可操作体验**：提供一条可复制命令（读取固定仓库源码、生成TEST_ONLY候选）、另一条离线 `verify` 命令，输出 `ACCEPTED_TEST_ONLY` 与 `REJECTED` 等可读状态、来源/Claim/故事计数和准确阻断原因。预览/测试候选不称发行版、不携带真密钥；构建结果仅留Git忽略的本机 artifacts，不能上传GitHub Releases、Assets或PR附件。
7. **不污染旧KITT**：生产 `ExploreCatalog`、`KittRuntime`、旧 `BundledCountyPacks` 启动逻辑和 `SettingsStore/TripStore/StoryInterestStore`默认不改；新增可调用的签名验证模块不得新增“扫描文件并激活”的入口。保持原新都GPS/离线播报、六类偏好、20份合成历史与账号保护回归，正式故事仍为0。

## 2. 真测试和必须拒绝的反例

- **新鲜全量** Debug/Release测试与XML（不得沿用code20旧1067）、六组Fake模拟、Python内容门禁7项、build/lint、签名2接受/6拒绝（若原环境支持）、干净工作区两次打包相同unsigned字节+SHA256、打包/验签CLI的成功/失败真实返回、源代码/产物内容hash及受保护文件清单；失败尝试不计入通过。
- **专项安全负例**：容器格式/Schema/应用版本错误、重复路径/路径逃逸、超限制、删包、改一字节、替换另一县的fact/narrative、旧签名配新manifest、假 `key_id`、包内自带公钥、许可`restricted/all rights reserved`、缺Claim或Source、过期Claim引用、`visitor_gate=pending`或`review_status=candidate`、不可靠Story版权、签名有效但内容不合法、Fact-only正确报告“0故事”。正例不得依赖将测试故事冒充真实游客审批。
- **密钥安全红线**：产物与Git工作树内无任何私人key/seed/密码/账号/真实设备ID，子进程日志也不得暴露私钥或token。仅标准测试data可公开（公钥如被写入src/test则明标不可信的TEST_ONLY，**不得置于src/main资产或真正publisher列表**）。
- **旧门禁不借机放行**：杨慎Visitor仍pending、原真实资产hash/Source Claim版本不动；普通App不识别外部文件/不下载/不安装；没有独立可信发布者就不得声称该样板可以正式分发或下载。

## 3. 工程交付与 STOP

写入`docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md`、`docs/ACCEPTANCE_COUNTY_PACK_D1.md`、新的`docs/evidence/v060-d1-verification.json`、最新HANDOFF；包含专门的密钥治理/撤销/轮换策略**规划**及D2遗留风险。提交独立 Draft PR，注明本次真实测试数据、测试密钥范围、SHA、失败分类、D0兼容及精确未完成范围，**STOP等待独立工程复审**。

**本D1禁止**：GitHub Tag/Release、第二仓库、正式发行者私钥/公开信任根托管、生产自动加载/更新/导入、手机安装或ADB、APK发版、对杨慎两篇批准或修改正文、全国真实采集/PR29合并、开启D2/V0.6.1、修改用户延期UI。不将D1的TEST_ONLY私钥方案称“已实现生产正式可信发行”，也不将本D1工程通过视为用户授权合并。
