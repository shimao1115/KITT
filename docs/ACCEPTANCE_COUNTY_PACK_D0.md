# V0.6.0 D0 / code20 工程验收

仅验收 Issue #36 / Draft PR #37 的电脑工程。禁止本流程使用 ADB、手机、安装、合并或发布。正式故事仍0；不要求为演示先批准杨慎。

1. 审阅 [读取契约与兼容说明](COUNTY_PACK_D0_CONTRACT.md)，确认普通入口只有固定APK内置新都，没有外部包加载/下载/导入入口；Schema 2为内部草案，TEST_ONLY verifier和密钥仅在测试目录。
2. 在有既有 JDK17/Android SDK/依赖缓存及原签名配置的干净checkout运行 `scripts/verify.ps1 -Offline`。检查两种Variant全量XML、assemble/lint、六组Fake报告。后续重复验证必须实际执行测试任务；UP-TO-DATE或历史XML不能再次记为新成绩。缺原签名环境应如实BLOCKED，不能生成替代私钥。
3. 检查 `CountyPacksTest`：两个不相邻县的ID/父市州隔离、县码与父级码前缀归属（00/90特殊层级拒绝）、独立版本、默认拒绝、整体/单包摘要、TEST_ONLY签名错误、大小/Schema/code、重复/悬空ID、Visitor待审、Source权限、Claim撤回/修订及精确影响范围。来源/Claim权利均必须通过显式允许表；restricted/all rights reserved等反例同步重算了所有接受修订，必须仍被准确拒绝，不能因permissions=allowed绕过。六类多main只在测试中可选，排序不越过门禁，换ID不重讲同Claim。
4. 检查 `BundledCountyPacksTest` 与既有 OfflinePack/StoryPacks/Explore/兴趣/TripStore 回归：实际 App registry 只有新都，6对象22事实6基础来源；17个来源登记索引（6旧基础+11杨慎登记），16个Claim。无账号/无网络的旧自动事实与重播保持；探索只读、20份合成文件及合成设置不改写。真实手机历史未访问，本地测试不能给旧外场Gate补PASS。
5. 使用全新目录运行 `scripts/build-runtime-smoke.ps1 -Offline`，再运行 `scripts/test-kitt-signing.ps1 -RuntimeApkPath artifacts/kitt-v0.6.0-runtimecheck.apk`。核实2接受6拒绝、code20/0.6.0、原包名原证书；APK中无第二县夹具/测试类，原offline资产逐文件相同，权限不新增。这里的runtimecheck仅是本地候选。

本轮可复核结果见 [D0工程证据](evidence/v060-d0-verification.json) 与根 [HANDOFF](../HANDOFF.md)。证据脚本 `scripts/record-v060-evidence.py` 校验本轮开始标记、XML新鲜度、真实测试任务执行、APK内容、签名、原资产和受保护路径不变，并比对独立构建源码摘要。

D0不包含真实发行者信任根、外部县包正常启用、GitHub Release、下载/本地导入/原子安装、多县真实GPS几何、真实六区调查或设备验收。`settings.xml` 首进设置页漂移根因UNKNOWN、双来源模式DEFERRED、#23/#24/#27/#30原PENDING均保留。验收后仍需用户分别授权合并、手机升级、故事正式发布和下一阶段。
