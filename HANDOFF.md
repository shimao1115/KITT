# KITT V0.2 — 当前交接

截至 **2026-10-03（Asia/Shanghai）**：Post-M1.4 **Phase A–E 已实现并完成非手机门禁**，
用户补充的详细新都→雎水模拟、独立高价值地理／地标接近机会也已交付。
**Debug/Release 各127测试、两个构建、两个 lint 全部 PASS**；本次没有连接或操作手机。
中文 ASR 明确不在本批次范围，手机专属 Gate 全部 **DEFERRED TO COMBINED PHONE ACCEPTANCE**。
首个剩余 Destination 是[联合手机验收](docs/COMBINED_PHONE_ACCEPTANCE.md)，无需重复施工历史阶段。

## 直接安装与验收

最新可安装 APK：`H:\CODEX\KITT\artifacts\kitt-v0-debug.apk`，**0.2.0 / versionCode 2 / Android 8.0+**。
本地 `adb install -r artifacts/kitt-v0-debug.apk`，或将 APK 在手机打开安装；本批次没有执行安装。
APK v2 签名验证 PASS；SHA-256：
`1911D4694CF53EA10158BF29033B676609EA06FEC878DD3E227CD82D165A2BAE`。

最短合并顺序：TTS声音试听／保存 → 普通开始验证真实GPS → 结束 → 添加可选路线截图
→ 标题点五次，**100km/h / 16× / 开始** → 全程观察章节／文化内容／独立地标机会，期间追问、安静、旅途看图
→ 后台／锁屏 → 结束纪要 → 普通GPS重启 → 横竖屏。逐项标准见[11项清单](docs/COMBINED_PHONE_ACCEPTANCE.md)。
开发文本输入可测试用户接管，不算 ASR 成功；无需先修复 vivo 识别服务。

## 当前可运行能力

- Journey 开始／结束、10分钟真实时间安静与提前退出、跳过、最新用户意图、一次 ASK_USER、一个失效型 PREPARE、简短纪要。
- GPS 压缩 Context；区县背景＋镇乡街道章节，本旅程缓存；现实位置 best-effort 系统逆地理编码，限频且不阻塞 GPS。
- 同一个 Director 面向自然地理、工程交通、地方史、古镇遗产、文化名胜、地名习俗、产业和有依据的人物候选；最近题材提供多样性偏好，不设配额。
- 有依据的地标接近可在同章节内独立唤起检查，优先于泛泛道路／聚落解释。几何支持山峰、河流渡口、湖库、地貌、桥坝隧道、建筑、博物馆／遗址／遗产共14类。
  当前四个粗粒度参考节点独立于模拟 fixture，真实GPS同样可用。每趟机会去重、成功交付节点去重，主动深入允许；驶离后的晚到内容不补播。
- 可选路线图由当前多模态 Provider **只分析一次**，保存≤240字会话 RouteHint；以后仅传文字，GPS优先。取消／结束／服务丢失清空，不落恢复文件。
- 乘客／停车时“旅途看图”→ 系统相机／选图＋可选短文本问题，经主动 Director／本地TTS回答。旧图不带入自动检查，不长期保存，不新增相机或存储权限。
- 黑红夜间／高对比日间界面，大按钮与简洁地点；竖屏／横屏驾驶页不滚动；保留 M1.4 RMS监听条和慢速讲述scanner语义。
- ChatGPT账号隔离授权／目录／SSE，Fake／Responses／兼容API；实时位置与开发模拟独立，普通开始始终手机GPS。无新后台、导航、地图数据栈或多Agent。

## 默认 Provider／外部步骤

**新安装默认 Fake；模型／effort 无实际用途**，无需账号、AI key或地图key即可跑完整技术闭环。
Fake 固定演示不代表真实内容质量，明确拒绝图片。真实AI首选设置中的 ChatGPT账号及账号实际返回模型／effort，
模型接受图片时走原生图像请求；不支持或服务报错会简短解释，不隐藏换Provider或计费fallback。

历史 M1.4 手机最后已验证配置为 **ChatGPT / gpt-5.6-luna / high**，TTS **yue**。
这只是历史事实，**本批次没有重验当前会话／模型可用性**。已有有效授权可直接使用；新装或过期时，用户本人完成系统浏览器授权。
普通 API 是可选替代，需自己的 key。本地 `OPENAI_API_KEY` 存在性检查为否，未读任何手机或桌面账号 token。
**没有阻塞 Fake／离线验收的密钥或素材待办**；无需用户先提供路线截图。

## 模拟结果与工程证据

新 fixture：**成都市新都区 → 青白江／广汉／德阳／绵竹 → 绵阳市安州区雎水镇**，
**29点、13个镇街章节、90,912m测试折线**。坐标、道路线形及章节边界粗粒度，非推荐驾驶线路或导航真值。
旧110km fixture留在test resources，保留原M1.2六次晚到响应全部STALE的历史再现。

100km/h /16×，真实Context/Journey/Loop＋确定性测试Provider／fake Voice，5s请求＋20s TTS：
章节参考回归 **13缓存／13转换、三星堆可选且被选、6题材、14机会、零stale/cancel/failure**。
加载当前生产空间参考集的独立地标回归：**全部4节点各选一次、17机会＝13 SILENT＋4 SPEAK_NOW、零stale/cancel/failure**。
这两份报告是不同测试情景，不是篇数配额。新路线M1.3延迟对话回归15请求；80km/h Fake黄金路径68模拟分钟／51检查／4语音输出。

最终门禁：`scripts/verify.ps1 -Offline` 完成 **assembleDebug/Release、testDebug/ReleaseUnitTest、lintDebug/Release**，
**127＋127 tests，0 failures/errors/skips**；lint **0 errors**，Debug5／Release2 warnings
（依赖更新提示与framework ExifInterface建议），APK签名 PASS。
阶段A96、B102、C108、D116测试／variant均通过，最终保留原89项并新增38项。
完整证据：[批次验收](docs/ACCEPTANCE_POST_M1_4.md)；空间规则：[独立地标](docs/LANDMARK_PROXIMITY.md)。
本地报告：`artifacts/final-verification.log`、`landmark-and-phase-e-gates.log`、`verification.json`、`SHA256.txt`、
`full-simulation.txt`、`accelerated-simulation.txt`、`batch-simulation.txt`、`landmark-simulation.txt`。
构建／APK／本地日志忽略入Git，可通过验证脚本重建。

## 已知限制及延后的 Gate

当前只有 **三星堆、绵竹年画村、龙门山山前地带、雎水太平桥** 四个带来源的空间区域参考，
不承诺全国POI自动发现；参考位置不证明建筑可见、实际过桥、精确距离或开放情况。当前模型 Adapter 未启用搜索，
仅允许保守稳定事实，不能核实的数字／日期／现状删去；账号搜索探测属于可选后续增强。
系统Geocoder可用性依设备；API33+超时8s，旧API单个IO调用无法强制中断，但GPS继续且不堆并发。

真实ChatGPT题材／图片能力、相机URI授权／照片方向、当前物理GPS／地点／地标时机、主观TTS／屏幕表现、
OEM锁屏长时间运行、手机结束清理均 **DEFERRED TO COMBINED PHONE ACCEPTANCE**。
Windows Robolectric不能替代Android FileProvider实体URI或硬件表现；声明／数据边界／生命周期已有自动测试。
历史vivo公开ASR服务问题原证据保留，但**不是当前批次待办，也不要求用户先解决**。

## 最近 milestone commits

| Commit | Destination |
| --- | --- |
| `c794840` | M1.5：区域章节、本地候选、题材偏好 |
| `e1476f8` | M1.6：一次性路线图 → RouteHint |
| `fc78cd7` | M1.7：主动相机／选图问答与瞬时图片生命周期 |
| `169fb38` | Phase D：29点新都→雎水、完整模拟、地理／图片边界 |
| `bc92395` | 独立地理／地标机会，节点去重和接近过时保护 |
| `8cf0e58` | Phase E：黑红状态／横竖屏、127测试门禁、打包与合并验收 |

当前交接作为后续文档提交；已完成的历史 M1.1–M1.4 证据原文保留在
[历史交接](docs/HANDOFF_PRE_V0_2.md)。以本文件顶部状态为当前真相；历史安装与ASR记录不构成本次手机PASS。
