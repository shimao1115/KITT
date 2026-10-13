# CURRENT TASK CARD — 无待执行施工任务

**状态（2026-10-13）：#19 家庭电脑/办公室双机签名统一已完成，双端 PASS，Issue #19 已关闭。** 原本 #19 的签名迁移任务**不得再重复执行**，不得重新复制密钥、重签 APK 或再次安装当前手机 code13。

## 已完成的最新里程碑

- [#19 双机签名统一](https://github.com/shimao1115/KITT/issues/19) **CLOSED / PASS**。办公室原始签名及家庭原样私钥经 PrivateKeyEntry、文件一致性、ACL、证书、runtimecheck 包名/0.3.6/code13 和 Debug/Release 构建验签。家庭本机 Debug395/Release382、lint、六组确定性模拟、2正例+6拒绝签名回归均实际 PASS。详见 [HANDOFF.md](HANDOFF.md)、[家庭脱敏验证](docs/evidence/signing-home-verification.json) 和 [5b736d2](https://github.com/shimao1115/KITT/commit/5b736d2743d2eb29b068cafbfbef186480063d10)。
- 同一证书 SHA256：`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。私钥仅在授权本地路径保存；任何新 APK 仍必须使用相同 `com.kitt.reader.runtimecheck` 包名、证书和递增 versionCode 才能保留现有账号数据覆盖升级。**当前手机已 code13，#19 无手机安装要求。**
- 同步位置的源私钥副本目前未由 Agent 删除。建议用户**自行**确保已有受保护的私钥备份，再处理同步端副本与回收站/版本历史，避免明文私钥长期暴露；未经再次授权不得自动删除或上传密钥。

## 尚未签收但不属于当前施工

- [#13 V0.3.6 讲述语义去重](https://github.com/shimao1115/KITT/issues/13)：工程 PASS，真实自动 ChatGPT→TTS 引擎完成一次；真人听感、同义去重、主动补充/重播仍待用户实际验收。**不得伪称真人PASS**。
- [#18 研究证据缓存](https://github.com/shimao1115/KITT/issues/18)：真实区县缓存阶段性 PASS，镇街/Topic 真机待验。
- #14/#17 外场定位与地名连续性验收独立待办；V0.4.0 离线知识包尚未获下一轮施工授权。

**STOP Rule：没有新的用户授权就不启动任何施工、修改业务代码、做手机操作或进入 V0.4.0。** 新版任务经用户授权后才替换本卡。已完成 #19 的历史任务与逐步操作可在其 GitHub Issue、[签名迁移文档](docs/SIGNING_UNIFICATION.md) 和 Git 历史中查阅。
