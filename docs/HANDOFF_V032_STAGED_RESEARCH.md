# 沿途 V0.3.2 — Overview → Topic 收口交接

截至 **2026-10-04（Asia/Shanghai）**，本轮指定研究链已完成真机验收，**停止，不继续扩功能或架构**。
实现 commit：`c81f641 feat: finish sourced Overview-to-Topic chapter research on OAuth`。
完整记录：[两阶段研究验收](docs/ACCEPTANCE_STAGED_RESEARCH.md)。此前诊断交接：[V0.3.1存档](docs/HANDOFF_V031_RESEARCH_DIAGNOSTICS.md)。

## 当前可运行能力

- 区县／章节进入后先做轻量 Overview：简短概况、不同具体对象和实际来源，不再一次调查全区长事实。
- Overview READY 即保留正常 Director opportunity；只可使用有证据的基础介绍，不从标题扩写未证实年代、故事或官方称号。
- 自动讲过一个对象后，可按该对象或高 salience 线索启动一个后台 Topic；最多一个，不批量并发。
  新证据补入本章证据卡，pending 不阻塞 Director、GPS、TTS 或模拟移动。
- 用户输入先取消 Topic，再走原主动决策与必要搜索。“再讲一点”明确指向刚才对象，使用已完成专题的新角度。
- 驶离取消未完 Topic，晚结果不补播。完成证据只缓存至本次旅程结束，重访可复用。
- completed hosted search 与真实 provenance 仍是 READY 前提。缺政府 + OFFICIAL 引用的文保／非遗认定事实剔除。
- V0.3.1静音修复、Journey、GPS、八字段 Director、主动搜索支线、ASR、TTS、独立 OpenAI Responses 备用入口保留。
  没有新密钥、服务器、RAG、数据库、永久知识库、产品多 Agent 或新 UI 功能。

## Provider 与授权

真机沿用 **ChatGPT OAuth / gpt-5.6-luna / 默认 effort**，独立 OpenAI research **未勾选**。
OAuth 研究仍为 streaming、required web_search、保留 action.sources，生产总时限仍90秒。
本轮无需新 OAuth、API key 或用户授权步骤，手机原授权／设置保留。新安装仍默认 Fake；真实 Provider 通过既有设置连接账号。

## 真机验收

当前 vivo V2405A，以无答案提示的新都区身份运行生产研究与 Director/Journey/TTS：

- Overview search → response.completed **42991ms**；研究开始 → READY **43018ms**；章节入口 → READY **43041ms**。
- **5对象 / 6来源**。自动发现：宝光寺、杨升庵祠及桂湖、天府家风馆、新繁棕编、新繁东湖。
  四个验收目标均发现，人物与祠园合并；没有将验收名称写入生产 prompt/query/fixture。
- READY 回调立即放行 Director；约49.8秒形成第一段有证据旁白，中文系统 TTS 完整播放成功。
- 杨升庵祠及桂湖 Topic：search → completed **75199ms**；研究开始 → READY **75215ms**；**3可用事实 / 5来源**。
  两条缺政府认定依据的事实剔除，不凑数量。
- “再讲一点”：原判断 USE_CONTEXT，原 Director SPEAK_NOW，补充杨氏家规与公共家风教育；原生 TTS started/completed 均成功。
- 最终两次研究没有 SSE completion timeout。中间曾有29秒 SocketException和一次模型目录 DNS UnknownHostException；不把它们解释为搜索能力拒绝。
  开发中发现重复对象和追问切换对象，已分别合并概况线索与补上主题指代，最后重新完成整条真机验收。
- 探针采用持续更新的静止测试 fix，不修改真实 GPS；测试旅程已结束，androidTest APK 已卸载。

## Build / test / 签名

完整 `scripts/verify.ps1 -Offline`：**Debug 259 / Release 246 tests**，失败／跳过均0；两种 assemble、lint 通过。
新增14项两阶段回归；原232项基线及13项Debug传输诊断继续通过。脚本拒绝低于232项的报告，避免把筛选测试误当全量。
全量日志：`artifacts/staged-research-verified-gates.log`；交付复核：`artifacts/staged-research-package-check.log`。

成都新都→绵阳安州雎水粗粒度模拟仍通过：29点／90912m；章节证据模拟13章节／19研究／13旁白。
这是 Fake研究／Director／Voice与生产状态机的确定性回归，不冒充全程真实Provider内容验收；真实研究证据另见上文。

交付版本 **0.3.2 / versionCode 7 / Android 8.0+**：

- Debug：[kitt-v0-debug.apk](artifacts/kitt-v0-debug.apk)，74654651 bytes，v2签名通过，已安装vivo并核对同一SHA256。
  SHA256：`0C718DDDED32204023ABE864C41F309C2A4443C5364767881E7DC9531FFE1DF9`。
- Release测试签名：[kitt-v0-release.apk](artifacts/kitt-v0-release.apk)，70609344 bytes，v2／v3通过。
  SHA256：`CD8ABCCF1ECD31F5A062336E0C84AB6A32A682E455D472B917F78ACD25979D21`。
  使用已有Android debug keystore，供验收；没有创建或配置Play发布签名身份。

## 已知限制与最短人工复核

Overview约43秒，尚未达到十几秒目标；Topic约75秒，仍有托管搜索延迟和手机网络波动。未宣称长期成功率或消除所有 SocketException。
数量为目标区间；证据不足不凑数，官方认定校验可能剔除部分事实。
手机中文 ASR 保持已验收 Vosk 离线实现；系统 recogniser 的既有 ERROR_CLIENT 限制不在本轮改动。

普通复核：打开沿途，保留现有ChatGPT与独立研究关闭设置，开始新都模拟；待Overview完成后听基础介绍，专题完成后说或输入“再讲一点”；结束旅程清掉session资料。
固定新都现场计时按 [验收文档](docs/ACCEPTANCE_STAGED_RESEARCH.md) 的 androidTest 命令运行；无需导出凭据。

## 最近 milestone commits

- `c81f641` — Overview → Topic研究链、真实新都／专题／追问TTS验收、全量回归。
- `d9e860b` — 最小OAuth hosted search能力与完整SSE真机时间线交接。
- `801a712` — 最小搜索成功，6699ms，22条来源provenance。
- `9a2fd17` / `23ee7ca` — 非流式参数拒绝与旧完整研究约87秒完成超时诊断。
- `e01f301` / `3397221` — V0.3.1静音热修复与真机交接。

**到此收口，后续工作须另有用户任务。**
