# CURRENT TASK CARD — V0.3.5.2 真机原位升级与真实缓存验收 / Issue #18

**2026-10-13 用户明确下发本轮任务：只做已交付 0.3.5.2/code12 的真机安装与小版本验收。不得启动 V0.3.6/#13、离线包、#14 定位修复或新业务开发。**
**最新工程状态：** main 提交 `f7cbd6b`、交接 `32fd345`，Debug365 / Release352、六组确定性模拟、构建/lint/签名与合成缓存对照已经报告 PASS。**这不等于真实手机/ChatGPT 缓存验收已通过。**
**事实源：** [#18](https://github.com/shimao1115/KITT/issues/18)、[HANDOFF.md](HANDOFF.md)、[docs/ACCEPTANCE_RESEARCH_CACHE.md](docs/ACCEPTANCE_RESEARCH_CACHE.md)、[docs/evidence/v0352-verification.json](docs/evidence/v0352-verification.json)。

## 0. 先继承安全边界

先读 `AGENTS.md`、最新 `HANDOFF.md`、`docs/ROADMAP.md`、上述 #18 / 验收文档，检查当前 main 与本机工作树。优先使用**办公室已签名并校验的现成产物**，不为验收顺手改业务代码或重复做昂贵研究。

**手机目标**：`com.kitt.reader.runtimecheck`；原机预期安装 V0.3.5.1 / code11，拥有应保留的 ChatGPT 配置、设置和应用数据。标准 `com.kitt.reader` 包不能替换这套数据空间。

**待安装文件（办公室本地，不存在于 GitHub）**：
`artifacts/kitt-v0.3.5.2-runtimecheck.apk`
- 应有 `applicationId=com.kitt.reader.runtimecheck`、`versionName=0.3.5.2`、`versionCode=12`。
- APK SHA256 应为 `4e9526b932801ca000c00ee552e086d74820023ccf48580b31d328da92fa7b88`。
- APK 签名证书 SHA256 应为 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。
- 本机文件缺失、APK摘要/证书/包名不符、手机现有同包名不是 code11 或存在其他签名/用户数据意外情况时，**STOP，不安装，记录异常等待用户决定**；不要下载或用标准包代替，也不要编一个新签名 APK 冒充。

## 1. 安全原位升级 Gate

1. 在办公室 Windows 环境核对 `git status`，已有已签名 APK 的 SHA256、`aapt` 的 package/versionCode、`apksigner verify --print-certs`；如有条件，先以 `scripts/check-kitt-signing.ps1 -ApkPath ... -ExpectedApplicationId com.kitt.reader.runtimecheck` 验收。不要在结果中显示凭据。
2. 手机连接并完成必要的用户授权后，用 `adb shell dumpsys package com.kitt.reader.runtimecheck` 等只读检查当前应用版本和包名；版本/状态不符先暂停。用户本人需要解锁或批准安装时让用户本人完成，不绕过。
3. **仅在预检全部通过时** `adb install -r artifacts\kitt-v0.3.5.2-runtimecheck.apk`。严禁卸载、清数据、使用 `-d` 降级、覆盖其他包或转储 ChatGPT 账号/密钥。
4. 安装后核对包名、code12、版本、安装数据保留；在 App 常规界面确认原 ChatGPT Provider/模型/effort、语速/设置与旅程入口仍正常。连接状态/凭据可解密 ≠ 已验证在线授权生效；需要真实调用时才确认在线能力。禁止导出凭据/原始账号文件。

## 2. 真实地区缓存的最小验收：先区县，再镇街/专题

在手机的**真实定位**环境用普通“开始沿途”，不要注入 GPS、不把 VPN 出口当当前位置。

- **A 首次学习**：至少一项经成功来源核验的四川省/成都市/新都区 Overview 真正完成并有 `evidence_cache write` 证据。保留脱敏诊断的 stage、search_call、write 计数即可；不要存入完整网页、URL、坐标或账户内容。真模型研究尽量只触发**一次必要研究**，不为刷日志反复搜索；无有效来源就标记 PENDING，不要造研究记录。
- **B 真正跨进程**：正常结束 Journey；在不清数据、不卸载的前提下结束 App 进程（可用 `adb shell am force-stop com.kitt.reader.runtimecheck`，确认不等于 `pm clear`），再启动 App，于**仍然可信的新鲜相同区域身份**重新开始。验证区县 `evidence_cache hit`，相同区县 Overview 的重复研究网络调用 **0**，新一趟 Director 仍有正常判断/讲述机会。不要误把重复讲述本身判为缓存失败（语义去重属于 #13）。
- **C 可用再测**：如果真实定位能持续解析到完整镇乡街道身份，再做对应 Overview；若能够自然完成一个 Topic 才检验其缓存跨重启命中。**不因定位新鲜度/Geocoder 不足而伪造镇街结果；分别记录“区县PASS、镇街PENDING、Topic PENDING”。**
- **D 时效性少量冒烟**：对“今天开放吗 / 最新票价 / 天气如何 / 重新搜索”选一个有代表性的问题，确认旧稳定缓存不会被当成实时证据；实时搜索确实发生或网络失败时明确表示无法确认。不需要反复实际付费查询。
- **E 旧功能冒烟**：启动/结束、安静/提前恢复、主动打字或说话中断、TTS/ASR 按设备当前可用性最短检查即可；遇到定位不新鲜、账号会话过期，明确区分环境/PENDING，不擅自修复其他 issue。

## 3. 必须如实呈现的证据

- 分开列出：APK/升级 PASS 或 FAIL；ChatGPT 原账户/设置保留；自然新鲜地区身份是否建立；区县、镇街、Topic 三个范围各自的 **first write → 真进程重启 → hit → 重复研究调用数**。
- `0/0/0` 是以前的**合成测试**结论，严禁直接充当本次真实手机结果。只有确实观测到才能宣称真实设备零重复研究；没有相应诊断就标记证据不足，不要根据“没有讲到”推定 hit。真模型节省的 Tokens 比例本轮不强制量化。
- 明确系统逆地理编码本来不保证室内连续新鲜；#17 连续街道与 #14 定位新鲜度仍各自待验收。用户本人必要的现场权限/解锁由用户操作，不为达到 PASS 牺牲定位安全。
- 仅收集脱敏结果与必要时间/调用计数，不导出账号、密码、token、坐标轨迹、个人对话、原始日志中的敏感内容。

## 4. 最终交付和 Stop Rule

执行完后更新 `HANDOFF.md`、`docs/ACCEPTANCE_RESEARCH_CACHE.md` 和 [#18](https://github.com/shimao1115/KITT/issues/18)，附最简人工可复验步骤与真实 PASS/FAIL/PENDING 矩阵。如果真正实现了本轮必测 Gate，再请求用户作最终验收决定；**不得把工程交付自动改写成手机总验收 PASS**。

- 如果只验证了安全原位升级：记录升级 PASS、缓存真机 PENDING，允许停止，不强制多次查询。
- #19 家庭电脑签名仍 OPEN/PENDING，不影响这次办公室真机验收；勿代用户上传私钥。
- 完成本任务立即停止，不开发 #13、离线包、地图/定位/UI 改造。手机验收如果揭示真实新缺陷，再提出最小后续 Hotfix 供用户决策，不顺手扩张。
