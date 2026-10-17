# KITT V0.6.0 D2 — 双电脑协作制作 + 最小可信发布流程

> **当前唯一开发任务**：[Issue #40](https://github.com/shimao1115/KITT/issues/40)，分支 `feat/v060-d2-dual-workstation-release`。用户已授权启动 D2 的**最小工程准备与测试**，强调 **办公室和家里两台 Windows 电脑都能做知识包**、拒绝过度设计。前一阶段 D1 [PR #39](https://github.com/shimao1115/KITT/pull/39) 已合并 main；手机仍为 code19，**本轮不实际发布、不生成生产密钥、不上手机**。

## 要解决的实际问题

- 任何一台 Windows 电脑都能读取 GitHub 最新版本、编辑合法可公开的 Fact/Claim/Source/Narrative 与审稿资料、运行相同的检查和**预制**知识包；地点/用户名/目录不同不影响结果。受版权限制的原文和 PDF 不提交公开 GitHub。
- **同一 Git commit** 是两台机器之间的一致性依据；每台机器的本地工作区可不同。当前 D1 KCPK v1、`packtool`、D0 审核门禁已经存在，**直接复用**。
- **最终正式签名只在一台指定电脑做**，位置稍后由用户决定；制作不等于签名，私钥绝不放 GitHub/PR/Release/普通日志，也不为了跨设备制包复制到两台电脑。可以设计一份清晰的离线加密备份人工说明，不搭复杂密钥平台。
- GitHub Releases 是最终发布点，但**正式发行永远要独立的人为批准**；工程完成不能代替真实资料版权/Visitor 审核。

## 施工路径：先实现最少的内容

1. **先检查现成工具**：阅读 `AGENTS.md`、`HANDOFF.md`、[D1 合约](docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md)、[现行路线图](docs/KITT_CURRENT_ROADMAP.md)、[知识工厂](docs/KITT_KNOWLEDGE_FACTORY.md)、[内容标准](docs/KITT_KNOWLEDGE_STANDARD.md)，以及 `packtool`、`D1PackCli`、D0 Gate，核对原分支/worktree/ignored artifacts，不删除或覆盖。
2. **打通最小两机工作流**：使用已有 GitHub 仓库、固定 commit、KCPK v1 和校验入口，实现/补齐“制作候选 → 审核/验签 → 生成待发布清单（地区、事实/故事数、版本、sequence、scope、签名密钥 ID、源 commit、SHA256）”。尽量在现有 CLI/脚本上加最少参数，允许 Windows 上用**不同绝对路径**运行。用一页中文用户说明解释“办公室编辑→提交 GitHub→家里同步→校验→指定单机最终签名/发布”，顺序亦可反过来。
3. **保留发行安全边界**：测试期间只用 **TEST_ONLY** 隔离密钥和合成数据验证程序步骤；PRODUCTION 只能在**后来用户明确授权创建真实发行身份**时使用正式密钥。TEST_ONLY 不得被任何流程标成正式可发布。发布命令优先交给人工 `gh release` / GitHub 网页手工执行，不做 CI 自动签名或复杂自动发布；目前只能生成本地待发布预览，不能写入 GitHub Release/Tag。
4. **内容门禁必须在生成/发布前生效**：复用 D0 Fact/Claim/Source/Visitor 合格条件，签名不代表版权许可，不允许通过外层标签、假公钥、版本、改哈希绕过。新都 11 个研究来源有 2 项再分发权利不明确；杨慎两篇 `candidate/visitor_gate=pending`，正式故事数 **0**，新都对外发行 **BLOCKED**。本轮不得更改审核字段来制造 PASS。
5. **交付工程证据**：本轮真实运行 Debug/Release/packtool 及相关新单测、Python 内容门禁、六组 Fake 模拟、assemble/lint、既有 APK 原签名回归。两个独立 Windows 路径/工作树固定 commit 的 unsigned bytes/hash 必须相等，TEST_ONLY 正例能预检，篡改、错县、错版本/序号、未知可信锚、待审故事、未知版权、不足依赖都拒绝；没正式密钥/未授权时**没有**可正式发布状态。有条件做两台真机（办公室/家里）对比；不能实测就留 PENDING，不得虚称双机实测通过。保护密钥与用户路径、拒绝删除他人目录。
6. **收口**：更新 `HANDOFF.md`、必要的一页验收说明/证据（不复制旧D1巨量结果），将功能变更交付独立 **Draft PR**。工程通过后 STOP，等待独立复审和用户下一次授权。

## 明确不做

不创建第二个 `KITT-PACKS` 仓库、NAS/服务器/数据库、HSM、密钥中心、复杂轮换和自动撤销、CI 自动发布、大型工作流或多 Agent 框架；不真实生成正式私钥或写入正式公钥；不创建 Tag/Release/上传正式包；不修改 `app/src/main`、用户手机/ADB/APK、GPS/讲述/TTS/账号、真实审批结论；不启动 V0.6.1 的下载/本地导入/原子安装、不合并 PR #29。

**验收一句话**：办公室和家里可以基于同一 GitHub 源码分别制作并校验**完全相同的未签名内容**，有资格的内容以后可由**一台受控签名电脑**完成正式发行；未经审批的真实新都内容仍无法发布。

> D1 原任务卡历史以 main 分支 [本文件在 PR #39 合并时的版本](https://github.com/shimao1115/KITT/blob/76377555bf3e00be1060a1d03cdf3d4eda8be768/TASK_CARD.md) 及 [Issue #38](https://github.com/shimao1115/KITT/issues/38) 为准；不在当前任务卡重复历史内容。
