# V0.6.0 D1 前置只读差距审计：从已合并 D0 县级加载器到签名成包

> 2026-10-17；仅是按用户“下一阶段”指令形成的设计审计和D1工程准入检查。尚未运行签名成包工程、也不代表内容可正式发行。唯一施工任务 [Issue #38](https://github.com/shimao1115/KITT/issues/38)。

## 1. GitHub真实基线与目前不具备的能力

- [D0 PR #37](https://github.com/shimao1115/KITT/pull/37) 已合并 main，merge `1330574d15deb55bc8881783c55e889ddf77ddf0`。D0第二轮工程复审PASS，Qoder本轮真实Debug540+Release527=1067项通过；这是D1旧基线，**不代表D1已重跑**。code20未上手机，用户已安装并人工体验的是 code19。
- `app/src/main/java/com/kitt/reader/CountyPacks.kt`：`CountyBytes`当前只是四段原始字节 + expected SHA256；`countyDigest`用四次长度大端前缀拼接做整体 SHA256；`CountyPackLoader`默认 `{ null }`拒绝，接收来源判断由**调用者注入**。运行中的 `BundledCountyPacks`仅适配APK内置新都资产，不能从任意URI或GitHub加载外部包。此设计是D1可信验证器的好接口，但**绝无正式签名容器、发行者根或下载/启用机制**。
- `docs/COUNTY_PACK_D0_CONTRACT.md`描述Schema2的四块数据、分块摘要、版本/ID/Claim校验、显式许可允许表和`00/90`特殊层级保守拒绝；D1不要推翻这些门禁，仅在其上**定义签名发行层**。正确地区代码格式**不等于**已经核对全国权威当期行政区划。
- `app/build.gradle.kts`：Android `minSdk=26`、JDK17源码，`versionCode=20/versionName=0.6.0`；已有APK签名只用于Android安装（签名证书 SHA256 `3139a293…9502ba`），**不能复用为知识包发行密钥**。
- `docs/KITT_KNOWLEDGE_FACTORY.md`与`KITT_OFFLINE_PACK_MANAGEMENT.md`：单县独立Fact/Narrative成品、可追溯`source_commit`、独立版本、内容权利、可信发布者签名、Releases正式渠道、下载后先验证/暂存/原子激活、旧数据保留；市州只是县级索引；手机下载+本地导入均安排在V0.6.1，首次Release/生产钥匙在后续独立授权D2。

## 2. 这次D1应避免的三个误区

1. **可验证哈希 ≠ 可验证发行者**：整包SHA或HTTPS不是数字签名。输入携带“publisher_key_id”或“public_key”不能让它自己获取信任。信任根必须由经过独立授权的客户端预置/可信目录提供；D1测试用临时Key不得被生产App视为官方Key。
2. **数据有效 ≠ 内容有权发行**：D0可以按现有“APK自带可审阅事实”建只读目录，但不等于其中所有来源和每份Claim都具备公开再分发许可。`ys-facts.json`存在11个研究来源，其中有两类`旧包仅保留既有事实摘要，不新增转载`、`未取得全文核读；不复制`是明确**不能作为新增外部可发行资料正例**的许可边界；D0加载器的显式许可表也已对这两类拒绝。D1必须独立检查素材/权利，若无法为“新都真实发行候选”证明权限，使用明标TEST_ONLY合成数据替代，记录真实内容发布PENDING。
3. **“Narrative文件有JSON” ≠ “已审故事可发布”**：标准`app/src/main/assets/offline/ys-narratives.json`当前`narratives=[]`；杨慎真人试听的两篇仍`candidate/visitor_gate=pending`，可能存于源稿目录，但不得拷进可发行样板、不得把审核字段改为passed。允许经过校验的Fact-only输出，其Narrative一侧是**0正式故事的明确空集结构**，而不是拷未审原文再隐藏UI。

## 3. D1 最窄独立交付

**一条黄金路径**：用稳定源码输入 → 产生可复现 unsigned 县级Fact/Narrative字节与manifest/外部索引 → 在隔离环境用标准非APK签名算法完成**TEST_ONLY签名** → 用由调用者信任的测试公钥离线验证签名、地区和版本、再使用D0内容验证 → 形成明确`ACCEPTED_TEST_ONLY`或拒绝原因的结果；没有真实生产公钥时**生产拒绝**。

负例必须证明替换单侧包、重放异县bundle、内容改字节、冒名publisher、错误/缺来源及过期Claim、Visitor待审或许可拒绝无法通过；无网无GPS无账号。分别报告每县实际合法可发行事实与故事数量，不得以“测试故事七篇”代表真实地区内容。

## 4. 不在D1中暗中完成的事项

- 真实生产私钥生成与保管、公钥初始固化/密钥轮换与撤销执行、首次对外发布Tag/Release、签名目录正式托管、KITT-PACKS新仓库：**D2另行授权**。
- Android收到文件后的用户确认、版本比较、存储权限、原子启用/回滚、Github下载与USB本地导入、市州批量下载UI：**V0.6.1另行授权**。
- 自动Wi-Fi更新、即时优先在线模型策略、实车GPS、省级/全国行政名录特殊代码支持、完整六区知识调查及杨慎Visitor：均不在本D1之内。

**完成D1首轮代码/测试和脱敏证据后仍只能给 Draft PR 等待独立验收；没有发行者真可信根前，不得声称GitHub Release正式可用。**
