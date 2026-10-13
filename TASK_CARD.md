# CURRENT TASK CARD — #19 KITT 家庭电脑导入原签名并双机统一验签

**用户于 2026-10-13 在家庭 Windows PC 授权本机 Codex 完成签名迁移。当前唯一执行任务 #19：导入办公室原私钥副本 → 验签 → 在家庭电脑构建 V0.3.6/code13 并验签 → 记录结果 → STOP。**
**本卡不包含本地私钥来源的实际盘符路径；用户会在本机 Codex 会话中另行告知。私钥路径、字节和密码都不得写进公共 GitHub Issue、提交、附件、生成日志或聊天。** 本卡只是向施工 Agent 发任务，尚未表明家庭端任何一步已执行或 PASS。

## 范围与已知基线

- 先读 `AGENTS.md`、`HANDOFF.md`、`docs/SIGNING_UNIFICATION.md`、`docs/ROADMAP.md`、[#19](https://github.com/shimao1115/KITT/issues/19)，检查当前 Git 工作树；在干净状态 `git pull --ff-only origin main`，不要强制重置/覆盖未提交修改。
- 办公室端原签名及构建已 PASS；家庭端仍 PENDING。原手机 `com.kitt.reader.runtimecheck` 已通过原位升级到 V0.3.6/code13，账号设置目前可见，无需重复安装。
- 唯一可信证书 SHA-256（小写、无冒号）：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。
- 家庭目标私有路径：`%USERPROFILE%\.kitt\signing\acceptance.keystore`，**不能覆盖全局** `%USERPROFILE%\.android\debug.keystore`；不能另造密钥、转格式丢失私钥条目、上传 keystore、打印/回传二进制或提取其中的账号资料。

## 操作要求（本机 Codex 自主执行）

1. 用户在**本机私密会话**给出来源文件的绝对路径。只读预检此文件是否存在，调用仓库 `scripts/check-kitt-signing.ps1 -KeystorePath <该本地路径>`，验证它**确实是含原 PrivateKeyEntry 的正确密钥**（仅记录是否 PASS 和公开证书指纹）。如果文件不存在、路径不可访问、指纹不一致，STOP 等待用户，**不复制**。
2. 只有来源 PASS 后，创建私有 KITT 目标目录。若目标已存在，**先比较 SHA256 文件摘要**；不同则绝不覆盖/移动、报告冲突等待用户；相同则保持不变。若不存在则原样复制源文件。为目标目录及文件设置只允许当前 Windows 用户和 SYSTEM 访问的 ACL；不改源文件/原文件权限。
3. 使用 `scripts/check-kitt-signing.ps1` 再验证目标文件PASS。不要更改仓库的 Gradle signingConfig 或 app ID，除非发现真实阻塞且最小修复得到用户批准；本轮原则上不改任何生产代码。
4. 家庭电脑确保 JDK17/Android SDK35 可用，运行 `scripts/build-runtime-smoke.ps1 -Offline`（本地依赖缺失时先说明，再考虑普通构建），然后 `scripts/check-kitt-signing.ps1 -ApkPath artifacts\kitt-v0.3.6-runtimecheck.apk -ExpectedApplicationId com.kitt.reader.runtimecheck`。需确认签名正确、applicationId相同、versionName `0.3.6`/versionCode `13`，并记录该家庭产物的**文件 SHA256**；家庭/办公室 APK 文件哈希不要求相同，证书必须一致。
5. 如可用再执行 `scripts/verify.ps1 -Offline` 与 `scripts/test-kitt-signing.ps1 -RuntimeApkPath artifacts\kitt-v0.3.6-runtimecheck.apk`，核对 Debug/Release 同一证书。写明哪些命令真正执行、哪些因为环境未准备而 PENDING；绝不重复冒称办公室的历史测试数为家里的结果。
6. **不操作手机、不降级/原位重装当前 code13、不清数据、不读取 ChatGPT 凭据**。只构建和验签，不触发真实 Provider/研究搜索；不施工 #13、#14、#17、#18 或 V0.4.0。
7. 签名源是可同步目录时要提示用户私钥已可能在同步端留存：仅建议在验证完毕后自行清理同步副本或转移至加密介质；**未经用户另行批准，不自动删除/移动同步源，也不上传额外备份**。保留办公室原始密钥且不得撤销使用权。

## 交付门禁

- 家庭源文件确为原始 PrivateKeyEntry PASS；本机项目私有 keystore PASS，目标与源字节一致。
- 家庭实际构建 runtimecheck APK、实际 APK 签名/包名/versionName/code13 PASS；若做了完整 Debug/Release 构建及签名回归，附脱敏结果。
- 办公室 PASS（历史证据）+ 家庭 PASS（本机实测证据）才可把 #19 标为**双机签名统一 PASS**，并在 `HANDOFF.md`、[#19](https://github.com/shimao1115/KITT/issues/19) 记录不含任何私钥及本地私有路径的结果；需要提交的仅是脱敏验收文档，不用为验证强迫产生新代码提交。若家庭构建未验证，只签密钥 PASS、产物 PENDING，#19 继续 OPEN。
- 完成 STOP；不要擅自启动其他版本施工。

## 上一任务状态（禁止重复施工）

V0.3.6/code13 已工程交付 Debug395/Release382、六组模拟通过，真实自动 ChatGPT→TTS 成功完成1段；真人听感/同义去重/补充重播仍待用户体验，#13 保持 OPEN，不再为家庭签名导入更改生产 APK。#18 区县持久缓存已实机阶段 PASS，其余自然现场仍 PENDING。
