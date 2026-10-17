# 做好 → 用户看一次 → 当前电脑发布

本页对应 [Issue #42](https://github.com/shimao1115/KITT/issues/42)。**本轮只是工程实现与隔离模拟；真实首次身份初始化及首次发行仍须用户另行授权。** 不代表新都可以发布，不涉及手机升级或 V0.6.1。

Agent 先整理正文、事实、来源和许可，提交到 Git 并固定 commit。程序从这个 commit 读取内容，使用原 D0 内容门禁及 D1 KCPK v1；生成 `review.md`，包含正文、来源/许可、地区、两侧版本、sequence、源码和完整待签名区域 SHA256。用户看这一份后只决定一次“可以发布”。Agent 只能转录用户真实作出的决定，不能自己批准；程序校验输入，不能认证输入者身份。

确认后，程序重新读取相同 Git blob 和已登记公钥，核对审核摘要，自动检查、签名、验签并调用 **一次 `gh release create`**。不再询问签名、上传或内容 PR 二审。未提交的本机改稿不属于审核对象；改了 commit、地区、版本、sequence、key_id 或任何待签名字节，都需要新的预览及对应决定。

## 设备只设置一次（此次不要执行真实初始化）

办公室、家里各自安装 Git、JDK17、Android SDK35、Python、[GitHub CLI](https://cli.github.com/)，并完成 GitHub 授权。此工作环境当前 `gh` 不在 PATH，工程验收使用注入式 fake gh；没有借用其他程序的登录凭据。未来首次获得单独授权后，由 Agent 执行：

```powershell
& .\scripts\d2-pack.ps1 -Command identity-init
```

默认在本机 `%LOCALAPPDATA%\KITT\pack-publisher` 生成独立 RSA-3072 身份，创建目录后先设仅所有者可访问的 Windows ACL，再写私钥；以后每次检查目录和钥匙文件权限。拒绝所有已登记工作树、其他 Git 目录、符号链接/联接解析后落在工作树内的位置；已存在目录绝不覆盖。私钥与 APK keystore 分开，不打印、不传给 gh、不进 Git。两机独立生成，不能复制测试身份充当正式身份。

只将该目录的 `registration.json` **公开信息**加入 `config/pack-publishers.json` 数组：`key_id`、`scope=PRODUCTION`、`test_identity=false`、`public_key`（SPKI PEM）。首次登记须另行获得授权并进入固定仓库 `shimao1115/KITT` 的正式 main，候选内容分支自行提交名单不能获得发行权限。当前仓库清单为 **空数组**，没有真实公钥/生产根。

预览及发布使用 [gh api](https://cli.github.com/manual/gh_api) 的认证请求，主机固定 `github.com`：先读远端 `refs/heads/main` 的提交 SHA，再按该 SHA 读取清单，避免 main 移动时读取混杂。候选 `--commit` 只提供内容；不采用其名单、工作区文件、本地 main/origin/main 或包内公钥，也没有调用方可选的登记来源参数。确认后重新读取当前 main 登记；若钥已撤销、网络/认证失败或 main 无法校验，直接停止且不上传，不增加第二次人工决定。生产预览因此需要能访问 GitHub；原 TEST_ONLY 离线 prepare 不变。临时测试身份带 `test_identity=true`，只能在测试代码同时注入 fake gh 的运行环境使用；真实 CLI 拒绝。

一次性安全设置后，日常无需用户管理密钥参数。日常内容源码可以使用已固定的候选分支提交，无须为了发布先合并到 main；只有发行公钥的首次登记与变更使用独立授权的 main 管理边界，不增加每份内容的 PR 审批。建议用户在当地另存一份加密离线备份，连同公开登记信息保管；不通过 GitHub 同步私钥。手机公钥固化单独留 V0.6.1。

## 日常由 Agent 操作，用户只看内容

下面参数由 Agent 从当次任务填写。`-InputDirectory` 必须是已提交的合法源目录，不能使用原 TEST_ONLY 样例。两侧生产版本使用 `v数字.数字.数字`（如 `v1.0.0`）；这是发布器的比较规则，未修改 D0 Schema。sequence 按县递增。

```powershell
& .\scripts\d2-pack.ps1 -Command production-prepare -Commit $commit `
  -InputDirectory $source -Sequence $sequence -Output $newReviewDirectory
# 展示该目录 review.md；reviewHash 取自 prepare.json 的 signed_region_sha256。
& .\scripts\d2-pack.ps1 -Command publish -Commit $commit `
  -InputDirectory $source -Sequence $sequence -ReviewHash $reviewHash -Output $newPublishDirectory
```

发布入口只读取一次 stdin：`可以发布 <完整审核SHA256>`。本人的这次输入即内容终审与发布同意；在聊天宿主中，Agent 仅可在已有用户明确决定后将其转录并绑定该摘要，不可自行生成决定或使用 `SIMULATED_TEST_ONLY`。`decision.json` 记录摘要、决定及模拟标记，并如实标明没有独立认证输入者身份。不读第二次输入，不改任何原始 rights/Visitor 状态。

包装脚本支持把这**同一个已作出的真人决定**通过 stdin 管道转录，并保留 UTF-8 中文；交互终端也可直接输入。拒绝模拟标记、不同摘要或多行输入，不再让用户在终端重复确认。

发布目标固定 `github.com/shimao1115/KITT`，Tag 为 `kitt-pack-cn-县码-s序号`，唯一附件为 `cn-县码-s序号.kcpk`。Release 正文使用原 D1 canonical manifest，既有 Releases 是版本记录。发布前及上传前检查历史与远端 Tag：同县序号须严格增大、两侧版本不得倒退、同版本不得变更正文；源码必须沿已有版本前进。不覆盖同名 Tag/Release，不编辑/删除既有发布，不使用 `--clobber`。草稿/孤立 Tag/缺附件/无法解析的同县历史均停止；1000 条读取上限达到时也停止，不能假装历史为空。不要让两台电脑同时发布同一县；本实现没有跨机器、跨序号的全局锁，不承诺分布式线性化。

[gh create 的成熟流程](https://cli.github.com/manual/gh_release_create)会先建草稿、上传附件再公开，并非服务端单次事务。本工具不另写 API 客户端；成功后回读公开状态、原 manifest、附件大小及服务端 SHA256、Tag 实际指向，全部一致才报告 `PUBLISHED`。无网、无授权、冲突、缺 gh 或超时均有拒绝码；上传开始后的异常报告 `release_outcome_unknown_check_remote` 及具体下层原因，**不重试、不清理远端**，Agent 先检查是否留下草稿/Tag/实际已发布，不能宣称成功或再乱建一次。普通预检错误不会调用 create。

## 内容不合格时

Agent 说明缺少哪些许可、Source/Claim 或 Visitor 证据，补证或在**新的已提交源目录**剔除不合格内容及其依赖，再生成预览；不把不明版权改 allowed、不把 candidate/pending 改成 PASS。真实新都全量仍 BLOCKED：11 来源中 2 项权利不明，杨慎两篇 Visitor 待审，正式故事 0。手机最后已验收 code19；App、设置、历史、GPS/TTS/账号均未触碰。

隔离测试通过只说明控制流可审查，不代表正式身份或发布已上线。办公室↔家庭两实体电脑本轮 **PENDING**。工程证据、Draft PR 和 HANDOFF 交付后 STOP；合并、首次正式设置/发行分别等待用户授权。
