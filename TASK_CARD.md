# CURRENT TASK CARD — Signing preflight / Issue #19

**状态：2026-10-13 办公室原密钥/专用路径/runtimecheck与标准Debug/Release构建验签 PASS；家庭安全传输与实际验证 PENDING。当前唯一目标仍为 [Issue #19](https://github.com/shimao1115/KITT/issues/19)，保持 open。**
**办公室工程已交付，停止等待家庭端；本轮不启动 V0.3.5.2（#18）、#13或离线包，不为验签改版本或安装手机。** 真实结果、工程提交和最小家庭操作见 [HANDOFF.md](HANDOFF.md) 与 [签名文档](docs/SIGNING_UNIFICATION.md)。双机统一尚未签 PASS。

## 先继承事实

- 当前手机装的是 `com.kitt.reader.runtimecheck` V0.3.5.1 / code11，已同签名升级并保留 ChatGPT 账号和设置；标准 `com.kitt.reader` Debug/Release 不是该数据空间。
- 手机已安装证书 SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。**不能换签名、不能卸载旧包、不能清数据。**
- 先读 `AGENTS.md`、`HANDOFF.md`、`docs/ROADMAP.md`、`docs/SIGNING_UNIFICATION.md`、#19 以及本卡。当前代码在 `app/build.gradle.kts`、`scripts/check-kitt-signing.ps1`、`scripts/verify.ps1`、`scripts/build-runtime-smoke.ps1` 已有统一签名设计，施工端首先审计、真实执行验证而不是重复改造。

## 唯一工作范围

1. 检查已安装手机 runtimecheck 证书、办公室原验收密钥与家中目标文件的对应关系。仅用可信的本机 `keytool` / `apksigner` 获取证书指纹，不碰 ChatGPT 凭据。证书指纹绝不能替代私钥。
2. 原办公电脑密钥先经指纹匹配，确认是旧版真正签名源，再由用户在两个电脑之间通过个人控制的安全方式复制**相同的完整原密钥文件**到各自 `%USERPROFILE%\.kitt\signing\acceptance.keystore`。不更改各自全局 `~/.android/debug.keystore`，不上传 keystore、密码到 GitHub、Issue、聊天或云端构建服务。若办公室电脑暂不可操作，先自检脚本，剩余仅留一个人工复制步骤。
3. 检查项目专用 Debug 签名配置与 runtimecheck 子工作区、Release 验收签名脚本是否严格使用同一证书。先调用 `scripts/check-kitt-signing.ps1`，不一致时应立即失败而不是签出错误验收 APK。若现有脚本有缺陷，作最小修复并保留文档。
4. 在**办公室与家用电脑分别**构建至少一个同应用包名 `com.kitt.reader.runtimecheck` 的验收 APK，并校验证书 SHA256。只有实际执行并收集脱敏验证证据后才能写双机 PASS。APK 文件字节不必一致，证书必须一致。版本号必须上升才能测试原位升级：本轮不为签名测试擅自伪造新版本或降级安装已有 0.3.5.1。不得误用标准包名覆盖现有 runtimecheck。
5. 本轮工程变更只限工具链、签名检查、安全复制指导以及必要的基础构建/回归。**严禁先实现 #18 缓存代码，也不做 #13、离线包、定位改造。**

## 交付 Gate

- A: 办公室密钥证书正确 PASS；
- B: 家庭密钥证书正确 PASS（实际完成安全复制）；
- C: 两机 runtimecheck APK 的证书均是手机目前的 SHA256，且包名、versionCode 规则已验证；
- D: 无私钥泄漏，旧 Android 工程全局 Debug key 未被覆盖；
- E: 构建 / 脚本完整执行情况与未测部分、提交、最短安装说明已写 HANDOFF。

**缺少 B/C 的任何一步，只能标记“配置已就绪，实际双机签名统一待验”；绝不能提前宣称完成。** 
确认 #19 后，将 [#18 原任务](https://github.com/shimao1115/KITT/issues/18) 恢复为当前施工卡。#18 的需求与验收已由用户先前明确授权，勿因为签名前置工作丢失内容。施工里程碑之间要明确止步界限。

---

## 已授权的下一任务（暂不施工）

V0.3.5.2 跨旅程研究缓存的全部需求、实现边界和验收以 [Issue #18](https://github.com/shimao1115/KITT/issues/18) 为准。用户此前已授权此小版本；当前仅因本签名前置任务暂缓。完成 #19 的双机私钥验证后，再由施工 Agent 恢复 #18 为仓库唯一任务卡，**不要同时执行两项。**
