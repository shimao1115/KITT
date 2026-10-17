# V0.6.0 D1 验收清单：可复现打包 + TEST_ONLY 离线验签

> 2026-10-17 · [Issue #38](https://github.com/shimao1115/KITT/issues/38) · [Draft PR #39](https://github.com/shimao1115/KITT/pull/39)
> 工程证据：[v060-d1-verification.json](evidence/v060-d1-verification.json)；格式与信任契约：[COUNTY_PACK_D1_SIGNING_CONTRACT.md](COUNTY_PACK_D1_SIGNING_CONTRACT.md)
> **本文件只描述电脑端工程验收。没有手机、没有 APK 安装、没有 GitHub Release、没有合并授权。**

## 1. 本轮交付内容

| 交付 | 位置 |
| --- | --- |
| 发行容器/签名/验签库（纯 JVM，不进 APK） | `packtool/src/main/kotlin/com/kitt/packtool/` |
| 格式层单元测试 | `packtool/src/test/kotlin/com/kitt/packtool/ReleaseFormatTest.kt` |
| CLI（`keygen`/`sample`/`build`/`verify`/`tamper`/`audit-newdu`） | `app/src/test/java/com/kitt/reader/d1/D1PackCli.kt` |
| D0 内容门禁适配 | `app/src/test/java/com/kitt/reader/d1/D0CountyGate.kt` |
| 端到端与负例测试 | `app/src/test/java/com/kitt/reader/d1/ReleaseD1Test.kt` |
| 确定性 TEST_ONLY 样例 | `tools/d1/sample/{fact-only,with-story}/` |
| 两干净目录复现脚本 | `scripts/d1-reproduce.ps1` |

## 2. 判据与结果

| 判据 | 要求 | 结果 |
| --- | --- | --- |
| 全量单元测试（Debug） | 0 失败/错误/跳过，本轮重跑 | 见证据 `tests.debug` |
| 全量单元测试（Release） | 同上 | 见证据 `tests.release` |
| 格式层单元测试 | 全通过 | 见证据 `tests.packtool` |
| D1 专项用例 | 正例接受、负例按码拒绝 | 见证据 `tests.d1` |
| 两干净目录字节复现 | unsigned 与整包 SHA256 相同 | 见证据 `reproducibility` |
| CLI 正例 | `ACCEPTED_TEST_ONLY` + 计数 | 见证据 `cli` |
| CLI 负例 | `REJECTED` + 精确 code | 见证据 `cli` |
| 真实新都发行审计 | 权利不明即 BLOCKED | 见证据 `real_content` |
| 内容门禁 Python | 7 项通过 | 见证据 `content_gates` |
| 构建与 lint | Debug/Release 构建 + lint 通过 | 见证据 `build` |
| 签名回归 | 2 接受 + 6 拒绝 | 见证据 `signing_regression` |
| 受保护内容不变 | 资产/审批/故事哈希不变 | 见证据 `protected_files` |

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
