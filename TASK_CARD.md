# KITT V0.6.0 D2：双电脑制包，一次人工确认，当前电脑直接发布

> **2026-10-17 本轮工程验收完成**：最终 Debug586 + Release573 + packtool13 = **1172 项**零失败/错误/跳过，D2 19 例双 Variant；一次确认 TEST_ONLY 自动制包/验签/上传参数演练、显式合法子集、两干净工作树无密钥字节复现均 PASS。见 [HANDOFF](HANDOFF.md)、[证据](docs/evidence/v060-d2-verification.json)、[操作说明](docs/COUNTY_PACK_D2_WORKFLOW.md)。只交付 Draft PR 等工程复审；两实体电脑 PENDING，真实新都全量 BLOCKED，生产身份/Release/手机未操作，真实故事仍0。本段不改变下方“日常一次确认”的用户决定。

> **唯一任务**：[Issue #40](https://github.com/shimao1115/KITT/issues/40)。用户明确要求**去除多余的人工审核/多电脑交接环节**。D2 工程只打通这个日常使用流程：**办公室或家里 Codex/WorkBuddy/Qoder 制作 → 用户过目并点一次确认 → 这台电脑完成自动校验、签名、上传 GitHub Release**。另一台电脑也能独立照做。下方明确区分 D2 的施工模拟与将来真实发布。

## 工作结果与边界

- 两个 Windows 工作区使用**同一 KITT GitHub 仓库**同步可公开的文字和审批状态；Agent 承担查证、写稿、登记出处和合法可发布权限、修订、生成 Fact + Narrative 候选、运行检查。受限 PDF/原文/扫描不上传公开 GitHub。
- **唯一人工动作**：用户检查 Agent 提供的内容预览及简短的事实/来源说明，确认“这版可发布”。这一次即内容终审和发布同意。**不要再引入另一位 Reviewer、单独的 Visitor 人工签字环节、交给专用签名电脑、PR 二次审稿或第二个“确认上传”步骤**。Agent 在确认前完成所有准备，必要的审稿状态由这一次用户决定按真实证据记录。
- 之后由确定性程序**自动**检查 D0 原有许可/Claim/Source/Story/Visitor/地区/版本/摘要/签名规则，按 D1 KCPK v1 生成并验签，再将已合格文件上传 Release；失败则列出具体不可发布内容，不悄悄放行、也不让用户逐项人工盖章。**必须能把权利不明或待审条目排除后只提交合格的子集，但不伪造审核、修改原始权利或拿忽略错误替代合法剔除；具体以既有 D0 Gate 的合法输入为准。**
- 两机**各自可完成终端发布**，取消“固定单台签名机”的硬要求。优先采用**办公室/家里各自保存一个独立生产签名私钥及 key_id**，复用现有 D1 TrustStore 多发行者公钥机制，私钥不进 Git/日志、独立于 APK 签名；真正生成和配置两把正式钥留待首次正式发布另行授权。若发现明显更简单且同样安全、可在任一电脑直接发布的方法可采用，但不得引入 NAS/服务端密钥中心/CI 自动签名/HSM/复杂密钥仪式。平时不得让用户管理密钥或在两地跑腿。
- 内容生产与工程发布是两回事：允许用户**一次性内容终审**，并不意味着版权许可、真实性或签名可绕过。**旧新都全量候选继续 BLOCKED**：11 研究来源中 2 项对外再分发权利未明，杨慎两篇仍 candidate/visitor pending，正式完整故事仍 0；只有重新核实权利或合规地剔除后才可另行测试可发行子集。

## 最小施工路径（不新造轮子）

1. 读取 [AGENTS](AGENTS.md)、[HANDOFF](HANDOFF.md)、[签名契约](docs/COUNTY_PACK_D1_SIGNING_CONTRACT.md)、[内容规范](docs/KITT_KNOWLEDGE_STANDARD.md) 和 `packtool`/`D1PackCli`，先检查现有 worktree/忽略目录，不改旧数据。
2. 复用现有 Builder、Verifier、Content Gate，**只补缺少的薄命令/参数**：候选生成、用户一次确认输入（明确由本人执行）、安全检查/签名、Release 上传预览/上传手段。无须自建发布框架；能调用成熟 `gh release` 就不要另写 GitHub API 客户端。日常流程应是 Agent 接管工具步骤、用户只看一次。
3. 用 **TEST_ONLY 合成数据**证明两台不同路径各自可以从同 Git commit 生成相同 unsigned bytes/hash；模拟一次用户确认后自动检查＋本地制包，**不得实际触发生产签名或 GitHub Release/Tag**。模拟上传可以输出准备好的 gh 命令/参数与校验清单，不宣称已发。
4. 做必要的正负例与已有 Debug/Release/packtool、Python、六组 Fake、assemble/lint、APK 签名回归，报告新鲜结果。损坏、错县、未授权/无来源、待审故事、TEST_ONLY 伪装 production 一律拒绝；检查不应增加人工审核步骤。能做双实体机器实测则记录，否则 PENDING。
5. 保留一页普通中文“办公室或家里使用”说明，更新最简证据和 `HANDOFF.md`，提交 Draft PR 等独立工程复审。

## 此次不允许

**不生成正式生产密钥、正式可信根、GitHub Tag/Release 或任何真实出版附件**（后续首次真实发行单独授权）；不修改 `app/src/main`，不升手机 code19，不做手机侧 V0.6.1 下载/导入/原子安装，不合并 PR #29，不设置额外人工 Gate，不建 KITT-PACKS/数据库/NAS/服务器/多 Agent 审稿架构，不更改原始故事版权/审稿结论来伪造放行。

**验收一句话**：用户在办公室或家里让 Agent 做好一本“可发布的书”，**只看一遍并确认**，随后这台电脑能够自动完成应有的机器检查与发布；不合格则具体指出原因。本轮先把工具和测试做好，STOP 在 Draft PR。

> D1 原始任务卡留于 [PR #39 合并记录](https://github.com/shimao1115/KITT/blob/76377555bf3e00be1060a1d03cdf3d4eda8be768/TASK_CARD.md)，不要回退主干内容或延用旧 STOP。
