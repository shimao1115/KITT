# 2026-10-17 最终授权：V0.6.0 D0 工程复审 PASS，文档收口并合并 PR #37

> 用户明确**批准** [Draft PR #37](https://github.com/shimao1115/KITT/pull/37) **最终文档收口及合并至 main，不升级手机**。这只解除下方历史“未获PR合并许可”限制，不是授权 code20 手机安装、APK/县包正式发行、KITT-PACKS创建、内容Visitor审核、PR29合并或 V0.6.0 D1/D2施工。
>
> 独立复审结论：[PASS](https://github.com/shimao1115/KITT/pull/37#issuecomment-6231174949)。最新已测实现是 dc764651；交付HEAD 2195ad04；**Debug540+Release527=1067项**、零失败错误跳过；六组Fake模拟/Python7/构建lint/原签名回归通过，证据详见 [v060-d0-verification.json](docs/evidence/v060-d0-verification.json)。受限权利与县父级代码两缺口均复审解决；新都1县6对象22基础事实与旧路径保留，第二县TEST_ONLY且普通APK无测试资料，正式故事0、杨慎Visitor pending。GitHub未记录独立CI运行，设备code20未验收；这些不能写成PASS。旧settings.xml漂移、Gradle首轮IPC根因 UNKNOWN。
>
> **本轮执行仅允许**：更新AGENTS/HANDOFF及现行路线的已完成状态→核查main/PR审查与安全文件差异→将PR从Draft转Ready（GitHub如需）→使用受保护HEAD合并PR37→核验实际merge SHA/main有证据→STOP。只准文档改动，不改Kotlin/Story/Claim/manifest/assets/签名/APK，不重跑安装、不访问手机、不做下一阶段。手机仍留code19。**下方所有旧的“待复审/禁止合并/D0仍未开发”均为时间较早的阶段文字。**

---

> **2026-10-17 D0/code20 两处评审修正 / STOP 待再次验收**：已按 [PR #37 独立评审](https://github.com/shimao1115/KITT/pull/37#issuecomment-6229804921) 完成县级码/父级码归属校验与来源/Claim显式权利允许表，修正提交 `dc76465`；生产差异仅 `CountyPacks.kt`，新都正文、杨慎审批、账号设置及Android UI不变。**本轮完整重跑 Debug540 + Release527 = 1067项，0失败/错误/跳过**；六项新增用例两Variant均执行，Python7、六组Fake模拟、构建/lint、原签名2接受+6拒绝PASS。首轮Gradle本机IPC失败尝试已保留且排除，最终串行独立进程全量重跑通过。code20 runtimecheck本地候选 **74461274字节 / SHA256 `2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60`**，旧1055项及旧候选仅为历史。详见[HANDOFF](HANDOFF.md)、[证据](docs/evidence/v060-d0-verification.json)。**Draft PR #37 保持未合并；无手机/ADB/安装、无发布、未启动后续里程碑；正式故事0、杨慎candidate/visitor_gate=pending不变。独立工程复审与用户验收仍PENDING。**

---

> **2026-10-17 D0/code20 已工程交付 / STOP 等用户验收**：实现 `91b96a8`，交付 [Draft PR #37](https://github.com/shimao1115/KITT/pull/37)。本轮真实 **Debug534 + Release521 = 1055项**，0失败/错误/跳过；Python7、六组Fake模拟、assemble/lint、原签名2接受+6拒绝PASS。新都1县6对象22事实6来源及原自动/探索兼容，第二县仅TEST_ONLY；APK检验正式故事0/无夹具。详见[HANDOFF](HANDOFF.md)、[证据](docs/evidence/v060-d0-verification.json)、[协议草案](docs/COUNTY_PACK_D0_CONTRACT.md)。本地code20 runtimecheck74461274字节，SHA256 `2762d6ffc623f80261f14ac0ea0ea78e56611c39817fa56f63ca255308462821`，未安装/上传/发布。无手机、无合并、无故事审批变化；真实发行/下载/导入未实现。以下为本阶段原任务与验收标准，不能用其施工文字重复启动已交付工程或下一阶段。

---

# KITT V0.6.0 · D0 / code20 — 唯一工程任务：县级双包通用加载器的第一条黄金路径

> **授权日期：2026-10-17**。用户在 [V0.5.2 PR #35](https://github.com/shimao1115/KITT/pull/35) 已合并 main `53c0e0fab4ebd5ce45556b8677337664293c0a87` 后，明确同意按既定路线审 [PR #29](https://github.com/shimao1115/KITT/pull/29) 和现行规范，进入**下一步的最小 V0.6.0 工程**。**唯一 Issue [#36](https://github.com/shimao1115/KITT/issues/36)**；**唯一分支 `feat/v060-county-pack-loader-d0`**；验收终点为独立 **Draft PR**，工程交付后 **STOP**。这是 V0.6.0 的**第一小步 D0**，不代表整个 V0.6.0/正式 Release 完成。
>
> **前一版实情**：主干 code19/V0.5.2 已合并，真实工程1009项PASS、一次 vivo code18→19 原签名升级PASS、20/20份历史文件哈希不变、用户本人六类兴趣体验PASS；用户认定「保存讲述兴趣」与旧「保存」过近的小问题**后续 UI 优化再改，不准本次顺手改**。旧 `settings.xml` 第一次进设置页字节漂移根因仍 **UNKNOWN**；账号在线有效性PENDING，杨慎双篇 `candidate/visitor_gate=pending`、正式故事0、外场#23/#24/#27/#30 OPEN、离线/即时模式 DEFERRED。**绝对不借新框架改写旧验收结论。**

## 0. 接手纪律与 PR #29 结论

1. 先检查工作树/所有worktree并保护未提交数据，`git fetch origin`；从最新 `main` `53c0e0fa` 的**本独立分支**施工，禁改 main/PR #29分支。先读 [AGENTS.md](AGENTS.md)、[HANDOFF.md](HANDOFF.md)、[Issue #36](https://github.com/shimao1115/KITT/issues/36)、[审计说明](docs/reviews/V060_PR29_AND_LOADER_AUDIT.md)、[路线图](docs/KITT_CURRENT_ROADMAP.md)，及 `OfflinePack.kt`、`StoryPacks.kt`、`ExplorePlaces.kt`、`DirectorLoop.kt`、`KittRuntime.kt` 的真实源码。尚未合并的 PR #29 两个文档是**候选设计参考**；其分支比main落后**64提交**且未获合并审批，**不得**直接 merge/rebase/cherry-pick 旧分支进生产，也不用它覆盖新设计。要继承其好规则，逐条对照现行 V2.4 内容、工厂、包管理规范。
2. 路线图实际后继：**本 D0 通用读取** → 后续独立审查**可信生产/发布/正式 GitHub Release 最小闭环** → V0.6.1 手机 GitHub下载 + 本地文件导入共享验签及原子安装 → V0.6.2 Wi-Fi自动更新（按需） → V0.7.x AI本地证据先行。只有这次 D0 获得当前实现授权。**不得**创建/发行 `KITT-PACKS`、发行Release、外部导入、下载器、后台更新、UI新页面、扫码、云账号或地图系统。

## 1. Golden Path / 反例

**先验证旧用户路径稳健，再验证新县级数据模型不再绑死新都。**

A. **旧真内容不退化**：利用现有已内置新都 **1县/6对象/22条基础事实/6来源**，按需添加最少的兼容 Adapter 或统一读取/目录接口，使 `ExploreCatalog` 仍能无GPS/无云/无登录浏览新都事实，`OfflineNarrator` 在已有可靠位置+同意门禁条件下维持旧自动事实介绍，六类偏好/重播/已播状态/Journey仍稳定。**本版仍是正式完整故事0**，杨慎两篇未正式审核、不能为演示新加载器偷偷让它们在普通 APK 播放。

B. **第二县测试隔离**：在 unit/instrumented 测试目录放**与新都不相邻**的第二个县级 TEST_ONLY 纯内存或 fixture 包（可用湖北省宜昌市西陵区的真实行政标签，任何具体景点/人物/Claim内容必须明确标合成，不宣称已核验，不打入生产 assets/APK、不能被 GPS 或探索正式目录发现）。两个县要有明确稳定`area_id`，父市州可聚合**索引而不重复县正文**；一次查询只命中正确县，不发生跨县误播、旧 county ID 静默覆盖、跨县同名实体误碰撞。

C. **真正通用的 Fact/Narrative 契约**：解析器/模型接收原始县级 Fact bytes + Narrative bytes + **各自独立manifest和版本、外部整体哈希/可信来源输入标签**；按 Area/Entity/Source/Claim与受审 StoryID 索引，Story 引用`claim_id + accepted_revision`，主/延伸类型与现有六类 `StoryCategory` 保持清晰；旧 `TopicFamily` 不假映射六类。各县可以**多位人物/多篇 main**存在，但**仅**当其内容门禁、版权/来源/Claim修订、地域/可信GPS/已播/节奏全部合格后进入**测试级**可选集合，实际应用内生产故事仍0。不能把旧 `StoryPacks.parse` 的 **`<=1 main`** 数据门禁直接删掉而不构造新的安全限定、引入未审核内容，不能改 `approval_ref/visitor_gate` 或正文。
 
D. **精确破坏测试**：错`area_id`、重复/悬空 ID、hash不符、格式错、超范围尺寸/schema、App最低code不支持、来源权限缺失/争议/撤回、`visitor_gate=pending`、Claim accepted revision失配、非可信来源输入/签名无效都必须**fail closed** 或按准确引用**只停用受影响篇章**，事实基础仍可独立展现时标「仅基础事实」。不接受“所有风险都忽略，只要 JSON 能解析就列可播”。包级 fatal 与篇级 soft-block 区分清楚，记录可核对 blocked reason 但不输出敏感数据。

E. **安全边界**：SHA256**≠**发行者签名。D0 可以用**纯内存 TEST_ONLY 公钥/Verifier**隔离验证层，**不能**把 TEST_ONLY 公钥当真实发行者、不能上传私钥、不能复用Android APK签名私钥。真实正式发行根证书、公钥治理、GitHub Release 发布和下载/安装器属于**后续单独授权 D1/D2**；**没有正式可信发行者来源的外部县包不得由标准 App 静默启用**。D0 只接受打包在 APK 内的可信内置新都资产 + 不入产品的自动测试夹具，不处理任意文件输入。
 
F. **结构性 Stop Rule**：如果发现真机自动主链和探索首页无法在 D0 中安全适配，需要整体重写GPS/账号/研究云链/签名/历史重播、改正式故事 Gate 才能打通，则保留原行为，交**最小可复现差距报告/拆分提案**；不能用跳过资格校验、硬编码“第二县”或给测试故事开放正式入口来伪造 D0 PASS。不要引入数据库/服务端/NAS/自制地图。

## 2. 验收检查（本版重新实测）

- Debug/Release **真实全量重跑**，记录各自 tests/failures/errors/skips（0异常、0跳过），六组Fake确定性模拟，Python 7项现有内容门禁，assemble/lint，原签名2接受+6拒绝（若环境可行），包校验脚本及权限回归。**V0.5.2旧1009不能直接记成本版结果**。若改变版本号，本地独立 `code20/versionName0.6.0` 候选 APK及证书/字节数/SHA256如实记录；**不是已发布正式 V0.6.0**，继续无手机/ADB权限。
- 专项：真实新都兼容6对象/22事实/6来源；两个不相邻县测试数据的`area_id`唯一和父市州目录归属；独立Fact/Narrative版本；坏hash/错证书签名/错地区/错Schema/丢源/跨县悬空Claim/单Claim撤回只影响引用Story；0正式故事时探索提示正确；无定位不主动假播、用户指定异地不能篡改GPS；正常6类偏好默认顺序旧序与合法Test-only多候选偏置，已播不重复。
- 受限资产和真机真实20份历史不复制到测试目录或Git；只用**合成历史样本**做数据/字节回归。绝不读/改用户已装 code19设置、账号、签名keystore密钥或已存日志，不做真实网络调用。
- 工程代码、专项测试、协议草案/迁移说明、HANDOFF及去敏证据放独立 [Issue #36](https://github.com/shimao1115/KITT/issues/36) 对应 Draft PR。**归档本阶段真实差距与 PENDING**：D0不是GitHub Release、不能对普通用户外部安装新县包、更不宣称全国县域可用。

## 3. 最终 STOP

完成最短Golden Path和全部安全门禁后提交**独立 Draft PR**和可复核工程交付说明，**STOP 等用户审核**。不可自行：合并PR #29/本PR、访问/安装手机APK、上传/发布Release、建KITT-PACKS仓库、变更杨慎故事审稿状态、关闭 #23/#24/#27/#30、启动真实六县普查/下个小版本，或把“新都以外 TEST_ONLY fixture”宣称为已核验地区知识。
