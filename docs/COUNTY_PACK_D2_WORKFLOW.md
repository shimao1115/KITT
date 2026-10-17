# 两台 Windows 电脑制作县级知识包（D2）

本轮 [Issue #40](https://github.com/shimao1115/KITT/issues/40) 只交付本地工程准备。办公室、家里均用 **KITT 同一仓库**、JDK17、Android SDK35、Python 和 Git；首次联网取得 Gradle 依赖后，下面的工具可离线运行。不建第二仓库、NAS、服务器或自动发布系统。

## 1. 任一电脑编辑，GitHub 同步

`git pull --ff-only` 后编辑合法可公开的来源登记、Fact/Claim、Narrative 与审核记录，提交独立分支/PR。受限原文、PDF、扫描件、私钥不能入 Git。人工审核结论不得由 AI 直接提升。确定输入后，在两机各运行 `$commit = (git rev-parse HEAD).Trim()`，通过 GitHub 确认双方 **40 位 commit 相同**。输入目录包含 D0 Schema2 的 `facts.json` 与 `narratives.json`；没有合格故事时后者明确为 0 篇。

## 2. 两机均可预制，无需任何私钥

在各自仓库根目录运行（`$out` 必须是不存在的新目录）：

```powershell
$commit = (git rev-parse HEAD).Trim()
$out = Join-Path $env:TEMP ('kitt-prepare-' + [guid]::NewGuid().ToString('N'))
& .\scripts\d2-pack.ps1 -Command prepare -Commit $commit -Output $out
Get-Content (Join-Path $out 'prepare.json')
```

默认输入是 `tools/d1/sample/fact-only`，全部为 **TEST_ONLY 合成数据**；可用 `-InputDirectory <仓库相对目录>` 指定其他已提交的 D0 输入。本工具直接读指定 commit 的 Git blob，本地未提交编辑不会被伪装成该提交。它调用原 `PackBuilder.prepare`，生成原 KCPK v1 签名区 `candidate.unsigned`、四个 D0 块与 `prepare.json`；**没有新增容器协议**。预制也运行外内绑定与原 `D0CountyGate`，失败不产生输出目录。已有输出目录一律拒绝，不能覆盖旧材料。

两机比较 `unsigned_sha256`（原四块长度前缀摘要）、`signed_region_sha256`（包含 manifest 的完整待签名字节摘要）；同时保持输入 commit、builder、key_id、AppCode 和 Sequence 相同。改 key_id 或 sequence 会改变签名区摘要。预制通过仅表示内容结构检查通过，`signature_verified=false`，`ready_to_publish=false`，不能导入或发行。

## 3. 本轮仅验证 TEST_ONLY 签名与本地预览

复用原 `:app:d1Pack` 的 `keygen`，在**所有 Git 工作树之外**选择不存在的新临时目录；只生成隔离 TEST_ONLY 密钥。不要使用 Android APK keystore。生成器拒绝已存在目录，不打印私钥或本机路径。

```powershell
$keys = Join-Path $env:TEMP ('kitt-test-keys-' + [guid]::NewGuid().ToString('N'))
.\gradlew.bat :app:d1Pack -Pd1.command=keygen "-Pd1.root=$PWD" "-Pd1.keydir=$keys" --offline --console=plain
$signed = Join-Path $env:TEMP ('kitt-test-pack-' + [guid]::NewGuid().ToString('N'))
& .\scripts\d2-pack.ps1 -Command build -Commit $commit -KeyDirectory $keys -Output $signed
& .\scripts\d2-pack.ps1 -Command verify -KeyDirectory $keys -Pack (Join-Path $signed 'pack.kcpk')
$preview = Join-Path $env:TEMP ('kitt-test-preview-' + [guid]::NewGuid().ToString('N') + '.json')
& .\scripts\d2-pack.ps1 -Command release-preview -Commit $commit -KeyDirectory $keys `
    -Pack (Join-Path $signed 'pack.kcpk') -Area cn-420502 `
    -FactVersion TEST_ONLY-d1-fact-1 -NarrativeVersion TEST_ONLY-d1-narrative-1 `
    -Sequence 1 -Output $preview
```

审核者只需要由可信渠道预置的 `key.json`（key_id、scope）与 `public.pem`，**验证不读取 private.pem**。不能用包自带公钥建立信任。预览重新调用原 `ReleaseVerifier`（信任→签名→版本/序号→摘要→外内绑定→D0 门禁），再与操作者指定的县/commit/两个版本/sequence逐项匹配。清单包含候选文件名、scope、版本、序号、源码 commit、SHA256、事实/来源/Claim/故事数、审核结果；它不依赖可被编辑的 `build.json`。`PREVIEW_TEST_ONLY` 始终带 `ready_to_publish=false`；本轮 CLI 明确拒绝 PRODUCTION，不提供生产签名或 `READY_TO_PUBLISH`。Gradle 的 `BUILD SUCCESSFUL` 本身不是业务成功；包装脚本检查 `exit_code`，拒绝时抛错。

含故事样例可用 `-InputDirectory tools/d1/sample/with-story`，其中 2 篇也只是测试故事。真实新都审计：`& .\scripts\d2-pack.ps1 -Command audit-newdu`，应报告 **BLOCKED / 11 来源中 2 不明 / 正式故事 0**，包装脚本随之失败。

## 4. 首次真实发行前，由用户另行批准

用户指定办公室或家庭中的**一台签名电脑**，并单独批准建立真实知识包发行身份与合格内容。本轮不建立该身份，也未实现启用生产身份的 CLI；未来最小后续是将人工批准的生产可信锚与签名入口接入既有协议，并重新验证 TEST_ONLY 隔离，不能把测试 `key.json` 改成 PRODUCTION 就使用。正式私钥位置可配置，但必须在全部工作树之外，受 Windows 当前用户 ACL 保护，不能进日志/PR/CI，也不能为了两机制包复制到另一台电脑。人工离线加密备份可存独立加密介质，密码另存；不用 HSM、双人仪式或密钥平台。

以后只有本地生产验签/内容/版权/人工批准均通过，才由用户亲自用 GitHub 网页或已有 `gh release create` 发布**同仓库** Release，并核对上传文件 SHA256。发布前再次检查固定 commit、县、两版本、sequence、官方公钥指纹与审核结论；失败不发布。**本文不提供本轮可执行的发布命令，也不执行 Tag/Release/上传。** 手机公钥固化、下载/导入/原子安装、持久序号及撤销留 V0.6.1。

## 5. 本轮验收边界

新测试、两干净 worktree 无密钥字节对照、CLI 真实成功/拒绝、Debug/Release/packtool、Python、Fake模拟、assemble/lint、APK原签名保护回归见 `docs/evidence/v060-d2-verification.json` 和 `HANDOFF.md`。单机两个路径不能证明两台实体电脑：**办公室↔家庭实测 PENDING**。真实新都版权/杨慎 Visitor仍 BLOCKED/PENDING；手机 code19、正式故事 0、旧设置漂移根因 UNKNOWN 不变。只交付 Draft PR，停止等待独立复审。
