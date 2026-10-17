# 办公室或家里：做完一本书，用户只看一次（D2）

> **Issue #42 的生产能力收尾见 [一页说明](COUNTY_PACK_D2_PRODUCTION.md)**；下文保留 PR41 的 TEST_ONLY 演练说明。当前工程仅用临时虚拟身份与 fake gh 验证，首次真实身份/发行未授权，不能据下文历史“生产 CLI 关闭”误读当前代码能力。

[Issue #40](https://github.com/shimao1115/KITT/issues/40) 的最新流程是：**任一电脑由 Agent 制作 → 用户看正文及来源说明、确认这版可发布 → 当前电脑自动检查、签名、验签、上传**。另一台电脑可独立照做，不交给专用签名机，不再问一次“是否签名/上传”，不追加 Reviewer、Visitor 人工签字或 PR 二次审稿。此次仅工程模拟，交付 Draft PR 待工程复审。

## Agent 在用户过目之前完成

两机使用同一 KITT 仓库，安装 Git、JDK17、Android SDK35、Python；初次下载 Gradle 依赖后可离线预检。`git pull --ff-only`，在工作分支整理合法可公开的来源/权利、Fact/Claim、Narrative 与审稿资料，再固定 40 位 commit。原文、受限 PDF/扫描件、私钥不上传 GitHub；不创建第二仓库、NAS 或服务端平台。

默认全量输入若有不合格依赖，程序继续拒绝整包。Agent 应补证据，或在**新的已提交输入目录**明确剔除受影响事实/Claim/故事及其引用，更新内容版本、记录剔除清单；用 `-InputDirectory <仓库相对目录>` 单独检查这个合法子集。原始资料/权利/审批不改、不自动标 allowed，不用忽略错误冒充合法剔除；独立合规内容不必陪绑失败。测试已证明原全量仍 BLOCKED、显式合法 Fact-only 子集可通过。

## 本轮演练：一次模拟确认之后自动到底

以下命令由 Agent 执行，用户平时不需要管理参数或密钥。仅生成隔离 TEST_ONLY 测试钥，目录须在全部工作树之外且不存在；私钥不打印、不提交、不复用 APK keystore。每台电脑可独立初始化自己的 TEST_ONLY `key_id`，不用交换私钥。

```powershell
$commit = (git rev-parse HEAD).Trim()
$keys = Join-Path $env:TEMP ('kitt-test-keys-' + [guid]::NewGuid().ToString('N'))
.\gradlew.bat :app:d1Pack -Pd1.command=keygen "-Pd1.root=$PWD" "-Pd1.keydir=$keys" --offline --console=plain
$review = Join-Path $env:TEMP ('kitt-review-' + [guid]::NewGuid().ToString('N'))
& .\scripts\d2-pack.ps1 -Command prepare -Commit $commit -Output $review
# Agent 展示 review.md：正文、基础事实、来源与权利说明。真人只在此看一次。
$summary = Get-Content (Join-Path $review 'prepare.json') -Raw | ConvertFrom-Json
$out = Join-Path $env:TEMP ('kitt-dry-run-' + [guid]::NewGuid().ToString('N'))
& .\scripts\d2-pack.ps1 -Command release-dry-run -Commit $commit -KeyDirectory $keys `
    -Output $out -Confirmation SIMULATED_TEST_ONLY -ReviewHash $summary.signed_region_sha256
```

`SIMULATED_TEST_ONLY` 只是本轮测试输入，**不能记录为真人已批准**。真正的用户确认必须由本人作出，Agent 不能自行填写“用户同意”。未来这唯一一次确认同时代表内容终审和出版同意，按真实证据记录必要状态；不用再让用户逐项批准 Fact/Visitor/签名/上传。

`prepare` 直接读取固定 Git blob，不把本机未提交修改伪装成指定 commit，输出原 KCPK v1 待签名区、四个 D0 块、清单和 `review.md`。随后 `release-dry-run` 校验确认摘要与当前内容/地区/版本/sequence/源码身份一致，复用原 Builder、Verifier、D0 Gate 自动签名和验签，生成 `pack.kcpk` 与 `release-preview.json`。失败列明条目/原因；没有第二个人工 Gate，也没有调用 GitHub。输出为 `SIMULATED_RELEASE_PREPARED_TEST_ONLY`、`ready_to_publish=false`、`upload_performed=false`，仅附带含 `<PRODUCTION_TAG>` / `<VERIFIED_PRODUCTION_FILE>` 占位符的 **不可直接执行** gh 参数模板，绝不把 TEST_ONLY 附件交给真实 Release。

默认合成 Fact-only 有 4 事实/2 来源/7 Claim/**0 故事**，空讲述版本为 `TEST_ONLY-d1-narrative-empty-1`；`tools/d1/sample/with-story` 里的 2 篇也只是合成测试。`build/verify/release-preview` 继续保留作诊断，不是日常必须再点击的审批步骤。包装脚本检查 CLI 的真实 exit_code，不能把 Gradle 的 BUILD SUCCESSFUL 当作包已通过。

## 两机相同内容，各用本机钥，不跑腿

比对固定 commit、builder、内容版本、sequence 和 `unsigned_sha256`（四块长度前缀摘要）；相同 key_id 时完整 `signed_region_sha256` 也应相同。不同电脑独立 key_id/私钥会改变 manifest 和签名，**不要求签名成品相同**，但原始内容字节/摘要必须相同。原 D1 TrustStore 支持多公钥；测试覆盖两个独立 TEST_ONLY 身份各自完成整条本地链，可信锚仍由调用方预置，不能相信包自带公钥。

未来经用户**单独授权首次真实身份初始化**后，办公室、家里分别配置本机正式钥与不同 key_id；与 APK 密钥独立，只存本机工作树外、受 Windows 用户 ACL 保护，不通过 GitHub 同步私钥，可各自做人工离线加密备份。无需 HSM、签名专机或密钥服务。日常一次内容确认后，由 Agent 在当前电脑调用成熟 gh/GitHub 上传已自动验签的合格文件，无第二次“确认上传”。本轮生产 CLI 仍关闭，真实身份初始化/上传入口启用及手机多锚配置留后续授权；不声称已经正式出版。

## 验收和 STOP

复现沿用 `scripts/d1-reproduce.ps1`：设置 `KITT_D1_COMMAND=prepare`、`KITT_D1_REF=<固定commit>`、`KITT_D1_OUT=artifacts/d2/reproducibility.json`。它用随机新目录及明确 `--project-dir` 分别编译，逐字节比较四块与待签名区，只清理本轮登记且位于指定临时根的工作树。两个 Windows 路径通过也只代表单机两工作树；**办公室↔家庭实体机 PENDING**。

本轮真实结果见 `docs/evidence/v060-d2-verification.json` 和 `HANDOFF.md`。真实新都全量仍 BLOCKED（11 来源中 2 项权利未明）；杨慎 candidate/Visitor pending、正式故事 0 不变。手机 code19、app/src/main、真实审批/旧数据不动；不生成生产钥/正式根，不创建 Tag/Release，不启动 V0.6.1。Draft PR 工程复审不等于日常出版再增加一次人工审稿。
