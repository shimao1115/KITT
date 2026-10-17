# KITT V0.6.0 · D2 正式发布能力收尾（仅工程施工，不进行真实发行）

> **唯一任务**：[Issue #42](https://github.com/shimao1115/KITT/issues/42)。**唯一分支**：`feat/v060-d2-production-release`。基线为已合并的 D2 TEST_ONLY [PR #41](https://github.com/shimao1115/KITT/pull/41)（merge `baea614ba02f1aad7776e65ed6766833ee451b3f`）；本轮只让原有流程未来能实际正式发布，仍交付 **Draft PR + 工程证据**，STOP 待 ChatGPT 复审/用户授权合并。

## 目标：不要把简单事再做复杂

**办公室或家里的 Codex / WorkBuddy / Qoder 做好知识包 → 用户看一次正文及来源说明、明确说“可以发布” → 当前电脑自动检查、签名、验签、上传 GitHub Release**。机器执行技术细节，不再找用户重复确认，不交给专门签名机。只需填补 PR41 真正缺失的生产密钥接口与实际 gh 上传路径，不新建架构。

## 黄金路径：优先复用，尽量合并

1. **保护现场与审计**：先 fetch、检查所有 worktree、ignored 本地文件和已有 PR/Release，不改、删、覆盖其他任务；读 `AGENTS.md`、`HANDOFF.md`、现有 D2 工作流、KCPK v1 契约、`packtool` / `D2Local.kt` / `D1PackCli.kt` / `scripts/d2-pack.ps1`。保留现有 `prepare`、`review.md`、`reviewhash`、D0 内容门禁和签名验签。不要造新版 Schema、Publisher 服务、Agent、多级 Gate、额外数据库。
2. **正式发布的薄实现**：增加本机独立生产身份的最简一次性初始化手段、受保护钥匙位置（工作树外、不可打印）、对应公开 `key_id` / 公钥登记格式；办公室和家里各自有独立密钥，绝不通过 GitHub 同步私钥。与 APK 签名钥隔离，不复用 TEST_ONLY 钥匙。日常 Agent 自动使用本机钥、无需用户管理参数/多次点击。正式公钥的手机可信锚接入单独留给 V0.6.1。
3. **固定审核对象**：从固定 Git commit 制备审核预览，唯一真人确认必须绑定当次包的地区、源码、版本、sequence、完整待签名区域 SHA256。不可让 Agent 用模拟批准或自己编造用户决定。确认后重新读同一 Git blob 并核对 hash，再用同一个 D0 内容/版权/Visitor Gate 和 D1 Builder / Verifier 签名、验签；拒绝任何 TEST_ONLY 或自带不可信公钥冒充 PRODUCTION。
4. **发布到 GitHub**：调用已有 `gh release`，不要重写 GitHub API 客户端；固定 `shimao1115/KITT`，按**县级 ID 与单调 sequence**制定最薄的 tag/附件命名，发布前读取现有发布记录并检查地区/版本/序号，不允许同县旧版倒退、重复序号、同名覆盖或修改删除既有 Release/Tag。失败（包括无网、无授权、冲突）清晰报错；不误报成功、不重试乱创建。使用成熟 GitHub CLI 的发布原子操作，不增加外部版本数据库/锁服务/CI 私钥。
5. **真人只审一次**：只要内容与发布对象未改变、校验通过，用户一句确认之后不要求“签名批准”“PR再审”“二次上传确认”。若内容被拒，Agent 说明缺少哪些证据/许可，先修或在**新已提交源目录**明确剔除不合格内容（严格维护引用关系）；不偷改 rights 或 visitor 状态，不把自造许可/审核 PASS 混入。首次生产公钥配置是**一次性设备安全设置**，不是每个内容包的审批。
6. **不碰 App 和真实内容**：手机仍是已验收 code19；`app/src/main`、本机历史/设置、GPS/TTS/账号、`content/yangshen` 和旧审批零意外改动。真实新都完整内容仍 BLOCKED（11 来源中2项权利不明，杨慎两篇 Visitor 待审、标准正式故事 0）。不因本工程完成就宣布可以发布新都。

## 本轮验收，只测不真发

- 使用**仅在临时测试目录的虚拟发行身份**和 fake gh（或者进程注入式替身）演练整个正式控制流；Fake 记录的上传调用只能在一次真实模拟用户输入、固定审核 SHA、合法合成内容、有效发行公钥和签名/验签全部通过之后发生。测试身份永不成为官方生产身份；禁止把任何测试密钥当成真实根提交或发布。
- TEST_ONLY 不可上传，确认后内容变化、错县/版本/序号、旧版/重复 tag、未知 key、内容版权未知、Source/Claim 缺失、candidate/Visitor pending、签名被改、无网/gh失败、密钥路径在工作树内都拒绝上传。一次用户决定后没有额外人工审批。
- 复测 fresh Debug/Release/packtool 和 D0/D1/D2 相关用例、Python7、六组 Fake、assemble/lint、APK 签名回归；与现有 1172 项历史基线区分，不继承或相加旧数字。留脱敏的成功/失败证据与保护文件哈希。
- 跨实体办公室与家里电脑实测**本轮没有则如实 PENDING**；模拟两种独立本机身份覆盖即可，不要求为了这个测试把个人私钥分发给另一台电脑。
- 写一页普通中文说明：**Agent 制作 → 人工看一次 → 发布完成/具体失败原因**；交付 Draft PR / HANDOFF / 本轮证据后 STOP。

## 红线

**没有授权在本轮真实生成/启用办公室或家庭正式私钥或根、实际创建任何 GitHub Tag/Release、上传真实/合成正式附件、操作或安装手机、启动 V0.6.1 或合并本 PR。** 真实首次身份初始化与首次正式发行由用户之后**单独授权**。当前完成的是可审查、可测试的发布实现，不把模拟等同已上线。

> 已合并 D2 TEST_ONLY 的旧工程事实见 [PR #41](https://github.com/shimao1115/KITT/pull/41) 和 `docs/evidence/v060-d2-verification.json`；本任务卡是**新唯一施工范围**，历史 STOP 不阻碍这里明确允许的模拟开发。
