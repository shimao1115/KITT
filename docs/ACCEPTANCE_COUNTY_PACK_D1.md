# V0.6.0 D1 验收清单：可复现打包 + TEST_ONLY 离线验签

> 2026-10-17 · [Issue #38](https://github.com/shimao1115/KITT/issues/38) · [Draft PR #39](https://github.com/shimao1115/KITT/pull/39)
> 工程证据：[v060-d1-verification.json](evidence/v060-d1-verification.json)；格式与信任契约：[COUNTY_PACK_D1_SIGNING_CONTRACT.md](COUNTY_PACK_D1_SIGNING_CONTRACT.md)
> **本文件只描述电脑端工程验收。没有手机、没有 APK 安装、没有 GitHub Release、没有合并授权。**

## 1. 本轮交付内容

| 交付 | 位置 |
| --- | --- |
| 发行容器/签名/验签库（纯 JVM，不进 APK） | `packtool/src/main/kotlin/com/kitt/packtool/` |
| 格式层单元测试 | `packtool/src/test/kotlin/com/kitt/packtool/ReleaseFormatTest.kt` |
| CLI（`keygen`/`sample`/`build`/`verify`/`tamper`/`relabel`/`audit-newdu`） | `app/src/test/java/com/kitt/reader/d1/D1PackCli.kt` |
| D0 内容门禁适配 | `app/src/test/java/com/kitt/reader/d1/D0CountyGate.kt` |
| 端到端与负例测试 | `app/src/test/java/com/kitt/reader/d1/ReleaseD1Test.kt` |
| 确定性 TEST_ONLY 样例 | `tools/d1/sample/{fact-only,with-story}/` |
| 两干净目录复现脚本 | `scripts/d1-reproduce.ps1` |

## 1.1 本轮评审修复（PR #39 独立评审）

| 评审项 | 修复 |
| --- | --- |
| 外层 Manifest 未绑定内层县包身份/版本 | 新增 `InnerBinding`：签名通过后逐字段比对 `area_id`/`parent_id`/`fact_version`/`narrative_version`/`minimum_code`/`pack_id`，不一致即 `inner_binding`；新增“同块重签成别县”“改外层版本”“改外层最低版本”三类拒绝用例 |
| 证据含本机 Windows 路径 | 证据生成脚本内置 `scrub()`，运行时把家目录/临时目录替换为 `<HOME>`/`<TEMP>`/`<REPRO_WORKTREE_n>`，并断言成品不含 `C:` 盘符路径或 `AppData`；本轮 JSON 已重新生成（历史提交中的旧值仍在 Git 历史，未获授权不改写历史） |
| 复现脚本无条件递归删除固定临时目录 | 改为每次随机目录名；目录已存在即 STOP、不删除；`finally` 只清理本轮创建且 `git worktree list` 仍登记的工作树；新增哨兵测试 |
| 防回滚边界未实现也未声明 | manifest 增加单调 `sequence`，验签器接收调用方 `minimumSequence`，低于即 `sequence_rollback`；文档明确这只是最薄检查，持久安装序号/撤销仍属 V0.6.1/D2 |

## 2. 判据与结果

本轮实测（评审修复后，实现提交 `5c803e4`）：

| 项 | 实测 |
| --- | --- |
| 应用单元测试 Debug | **567 项 / 80 套件**，0 失败/错误/跳过 |
| 应用单元测试 Release | **554 项 / 78 套件**，0 失败/错误/跳过 |
| packtool 单元测试 | **13 项**，全通过 |
| 合计 | **1134 项** |
| D1 专项 `ReleaseD1Test` | **27 例**，Debug/Release 两个 Variant 均执行 |
| 两干净 worktree | unsigned `6d9e0f9c…`、整包 `cf283c6f…`、10 468 B，两次相同 |
| CLI 正例 | `ACCEPTED_TEST_ONLY`：4 事实 / 2 来源 / 7 Claim / **0 故事**，`sequence=1`；含故事样例 2 篇 |
| CLI 负例 | `bad_signature`（篡改 1 字节）、`minimum_code`（App code 19）、`sequence_rollback`（序号 3 < 下限 4）、`inner_binding`（把 420502 的块重签成 510114） |
| 真实新都审计 | `BLOCKED`：11 来源中 9 明确、2 不明（`yang`、`ritual-paper-lead`），正式故事 0 |
| 复现脚本哨兵 | 预置同名目录 → 脚本 STOP、哨兵文件未被删除 |
| 签名回归 | 2 接受 + 6 拒绝，全部 PASS |
| Python 内容门禁 | 7 项 OK；`--check` 报 16 Claim / 0 可发布故事 / 2 排除 |
| 生产代码 | `git diff 1330574 -- app/src/main` 为空 |

逐项字段见证据文件；证据中的本机路径已全部替换为 `<TEMP>/…`、`<HOME>/…`、`<REPRO_WORKTREE_n>`。

## 3. 复核步骤

```bash
# 单元测试与构建（本轮实际执行）
./gradlew :packtool:test :app:testDebugUnitTest :app:testReleaseUnitTest \
          :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --offline

# TEST_ONLY 密钥（工作树之外）
./gradlew :app:d1Pack -Pd1.command=keygen -Pd1.root=. -Pd1.keydir="$TEMP/kitt-d1-keys"

# 打包 → 验签
./gradlew :app:d1Pack -Pd1.command=build -Pd1.root=. -Pd1.sample=fact-only \
    -Pd1.keydir="$TEMP/kitt-d1-keys" -Pd1.out="$TEMP/kitt-d1-out" -Pd1.commit=$(git rev-parse HEAD)
./gradlew :app:d1Pack -Pd1.command=verify -Pd1.pack="$TEMP/kitt-d1-out/pack.kcpk" \
    -Pd1.keydir="$TEMP/kitt-d1-keys"

# 序号低于调用方下限 → sequence_rollback
./gradlew :app:d1Pack -Pd1.command=build -Pd1.root=. -Pd1.sample=fact-only \
    -Pd1.keydir="$TEMP/kitt-d1-keys" -Pd1.out="$TEMP/kitt-d1-seq3" -Pd1.commit=$(git rev-parse HEAD) -Pd1.sequence=3
./gradlew :app:d1Pack -Pd1.command=verify -Pd1.pack="$TEMP/kitt-d1-seq3/pack.kcpk" \
    -Pd1.keydir="$TEMP/kitt-d1-keys" -Pd1.minsequence=4

# 用同一批块重签成别县 → inner_binding
./gradlew :app:d1Pack -Pd1.command=relabel -Pd1.pack="$TEMP/kitt-d1-out/pack.kcpk" \
    -Pd1.out="$TEMP/kitt-d1-out/relabelled.kcpk" -Pd1.keydir="$TEMP/kitt-d1-keys" \
    -Pd1.area=cn-510114 -Pd1.parent=cn-510100
./gradlew :app:d1Pack -Pd1.command=verify -Pd1.pack="$TEMP/kitt-d1-out/relabelled.kcpk" \
    -Pd1.keydir="$TEMP/kitt-d1-keys"

# 篡改一字节后应拒绝
./gradlew :app:d1Pack -Pd1.command=tamper -Pd1.pack="$TEMP/kitt-d1-out/pack.kcpk" \
    -Pd1.out="$TEMP/kitt-d1-out/tampered.kcpk" -Pd1.offset=4000
./gradlew :app:d1Pack -Pd1.command=verify -Pd1.pack="$TEMP/kitt-d1-out/tampered.kcpk" \
    -Pd1.keydir="$TEMP/kitt-d1-keys"

# 真实新都内容权利审计（应 BLOCKED）
./gradlew :app:d1Pack -Pd1.command=audit-newdu -Pd1.root=.
```

两干净目录复现：`scripts/d1-reproduce.ps1`（从固定提交建两个 `git worktree`，各自构建并比较）。本机执行策略默认禁止运行 `.ps1`，实际执行时先用 `Set-ExecutionPolicy -Scope Process Bypass` 放开当前进程，再用脚本内容等价的内联命令完成，结果见证据。

## 4. 本轮明确未做

- 未生成任何**生产**私钥/公钥，未建立官方信任根；TEST_ONLY 密钥只存在于工作树外的临时目录，未入 Git、未打印。
- 未使用、未读取 Android APK 签名私钥；APK 证书与知识包密钥无关。
- 未发布 Tag / GitHub Release，未创建 KITT-PACKS 或任何第二仓库，未上传 PR 附件。
- 未操作手机、未安装/升级 APK、未做真机 JCA 验证（**PENDING**）。
- 未把验签库接入 APK：D1 的库只存在于 `:packtool` 与单元测试，`app/src/main` 零改动。
- 未放行杨慎候选故事（仍 `candidate / visitor_gate=pending`），正式完整故事仍 **0**。
- 未实现下载、导入、原子启用、回滚、版本比较 UI（V0.6.1）。
- 未做真实新都对外发行：2 个来源权利不明，审计 BLOCKED，本轮只有 TEST_ONLY 合成样板。
- 未合并 PR、未启动 D2。

## 5. 验收结论（待用户判定）

工程侧已完成 D1 首轮：格式、签名、验签、拒绝链、可复现与证据齐备，**Draft PR #39 保持未合并**，等待独立工程复审与用户验收。
