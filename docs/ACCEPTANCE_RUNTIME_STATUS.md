# V0.3.4 / Issue #15 — 运行状态透明化第一阶段验收

日期：2026-10-09（Asia/Shanghai）。本轮只做驾驶页摘要、真实任务等待时间和沉默原因。展开式诊断、事件时间线、日志导出与 Issue #13 不在本轮；Issue #14 继续等待外场验收。

## 状态从哪里来

| 驾驶页信息 | 生产事实来源与边界 |
| --- | --- |
| 定位 | Journey 当前接受的物理 Fix；来源、原始来源、±精度和单调测量年龄。GPS / NETWORK / 模拟直接标明，LAST_KNOWN 显示“沿用 GPS/NETWORK”，并标粗略。过期为未知；地区名称只来自已解析标签。 |
| 网络 | Android 默认网络 callback 的 Wi-Fi/蜂窝/以太网和 VALIDATED；仅 INTERNET capability 不算联网验证。丢失、无法读取分别显示未连接、未知。 |
| VPN | 普通 ACCESS_NETWORK_STATE 权限下可见的 VPN transport，另有被动 VPN callback，避免默认网络绕过 VPN 时误报关闭。只说已检测/未检测到/未知，不宣称流量已走 VPN、TUN 健康或出口国家。 |
| AI / 研究 | 各自现有调用的最近成功、失败、超时、无效回复、不可用和年龄；首次为未验证。Fake 不产生真实服务成功，系统联网成功不等于 AI 成功。换配置重置证据。没有后台 ping。 |
| 当前任务 | 薄 Provider 观察器的真实调用开始/finally 结束，以及 Journey、地区解析、定位等待和 Voice 生命周期。搜索服务一次请求中的内部“搜索/整理”不可可靠区分，统一显示本地概况/当地专题/问题搜索。Director 显示“决策与生成讲述”，不猜测内部阶段。 |
| 准备/播放 | Android TTS 入队为准备语音，只有 onStart 才显示讲述；原初始化/播放 watchdog、stop、finish 清理等待。PREPARE 只是准备线索，仍需新现场确认，不冒充可直接播放的旁白。 |
| 沉默原因 | 安静、冷却、正常等待、位置不足、地区解析、服务失败分别投影。用户交互优先，讲话/监听与相关后台任务可主次并列；独立定位/网络始终可见。权限问题不覆盖安静和监听。 |

状态层不调度、不取消任务、不修改 Director 输出。请求按真实 Job、Director ticket epoch 和当前地区筛选；取消、跳过、驶离、结束后的旧状态不能重新占据主提示。只保留当前 work 和两项最新服务结果，进程重启即未验证，无事件历史、原始提示词、令牌、坐标或连续轨迹。

UI 只有可见且旅程运行时的 1 秒 elapsed 刷新；网络用 callback，Activity 重入刷新快照，后台 Journey 继续保持订阅。驾驶页按钮固定，紧凑接管界面将发送/取消与输入框合并一行。既有 Journey 五态、Director 八字段、Overview→Topic、定位选择阈值、ASR/TTS 后端与反重复策略沿用。

## 自动回归

最终 `scripts/verify.ps1 -Offline`：**Debug 314 / Release 301 tests**，失败/错误/跳过均0；Debug/Release assemble、lint 和 APK 签名全部 PASS。证据为 `artifacts/verification.json` 和 `artifacts/runtime-delivery-gates.log`。首次完整 lint 找到 API30 的 clearCapabilities，不提高 minSdk，改用 API21 的 removeCapability 并加入 API26 callback 回归后，重跑完整 Gate 通过。

新增 **29** 个测试：18 个状态/协程、5 个 Android 网络、4 个 Compose 布局、2 个 Android Voice。网络 callback 生命周期测试运行在最低 Android API 26，其余 Android 状态/布局回归 API 34。

| Gate | 覆盖 |
| --- | --- |
| 初始/服务证据 | 未调用、Fake、不支持研究均不显示真实成功；真实 adapter 的成功、异常、非法 Director JSON、独立研究失败和配置切换。 |
| 慢研究/并行 | 38 秒 Overview 显示真实 elapsed，Director 可继续运行；90 秒研究超时后 work 消失，保留最近超时。 |
| 接管/取消 | Topic 中断、主动问题优先、旧区 Overview 不冒充当前区任务、skip 后晚启动链仍属于旧 ticket、取消不当服务故障。 |
| 静默/语音 | QUIET/LISTENING 优先，包括位置权限不可用；PREPARE、冷却、TTS 入队/onStart/停止/晚回调与初始化 watchdog。 |
| 定位 | selector GPS→NETWORK→GPS、原年龄桥接/过期未知；原 V0.3.3 跳跃、粗地标和 reverse-geocoder 坐标约束回归。 |
| 网络 | Wi-Fi/蜂窝、VPN、未 VALIDATED、无物理 transport、默认网络交接、停止后晚 callback、默认绕过但系统 VPN 可见。 |
| 布局 | 手机 360×640、800×360 横屏、平板 1280×800；状态/输入/取消/大按钮可见，无垂直滚动，状态更新不移动按钮。Windows Robolectric 的中文字形宽度不等于 Android，真实宽度另以真机截图确认。 |
| 原行为 | start/end、10 分钟安静/提前恢复、打断 TTS、ASK_USER one-shot、PREPARE invalidation、stale 不播放、主动失败短报/自动失败静默降级、schema、Overview→Topic、模拟链。 |

成都新都→绵阳安州雎水粗路线：**29 点 / 90912m**。80km/h 完整链、100km/h / 16×加速、章节研究、独立地标和行政节流分别产出六份报告。章节研究 **13 章节 / 19 次研究 / 13 段旁白**，零 stale/cancel/failure；这些是确定性/Fake Provider 和 fake Voice + 生产 Context/Journey/Director 的回归，不替代真实模型内容、GPS路线或听感。

## 实际手机证据

设备：vivo V2405A / Android16，1260×2800，密度560。ADB 授权由用户本人完成。手机原 0.3.3 签名不匹配本机，保留原应用和数据；使用同源码独立包 `com.kitt.reader.runtimecheck`，桌面名“沿途 0.3.4 验收”。它新安装默认Fake，无法读取原应用OAuth/设置；初轮Fake与最终包已有真实配置的结果分开记录，本轮没有修改/导出账号配置。

生产runtime探针观察45秒自动旅程，再经过安静/恢复、one-shot监听/取消、真实打字路径与75秒观察、结束清理。无注入定位、无伪造语音音频、无Provider fixtures。初轮报告 `artifacts/runtime-phone-report.json`，结果 **PASS_CURRENT_CONDITIONS_ONLY**：

- idle：位置未知，蜂窝/系统联网已验证/VPN已检测，AI与研究均未验证。
- 自然定位为 NETWORK，报告中末次±30m、9秒；当前位置质量/年龄随实际 Fix 更新，未用 VPN/IP 定位。
- 自动/主动 Fake 返回及语音链运行，AI一直为离线演示/未验证；研究准确变为不可用，最后没有遗留搜索/决策 loading。
- 观察到 SPEAKING、PREPARING_LISTEN、LISTENING、PROCESSING、IDLE；自然 TTS onStart/onDone 完成。系统 ASR ERROR_CLIENT 后原 Vosk 路径就绪，无人说话后正常超时；没有真人识别内容/声音质量验收。
- quiet 显示剩余时间，恢复与结束清空 work；结束后回到开始页。Fake 调用极短，1秒采样没有采到在途 Provider task，长等待/失败场景以协程测试证明，不能声称已在真机观测慢真实 AI。
- 额外实际断网/恢复：临时关闭 Wi-Fi和蜂窝数据6秒，显示“网络未连接 · VPN已检测”，AI仍未验证；可见 VPN 不代表 VPN可以联网。恢复原 Wi-Fi/data=1，旋转恢复 user_rotation=0；没有修改原应用账号。
- 前后台切回、横竖屏、监听输入与安静界面已有截图；物理定位与地区名称分别显示。未解析地区时不出现坐标。

最终交付包安装复验报告 `artifacts/runtime-phone-delivery-report.json`，设备独立包已保存 **CHATGPT / gpt-6.1-sol / low**，独立研究关闭。结果同样仅 **PASS_CURRENT_CONDITIONS_ONLY**：

| 实际观测 | 结果 |
| --- | --- |
| 5秒时 | NETWORK ±30m/3秒；“AI正在决策与生成讲述 · 已等待4秒”；AI/研究均未验证。 |
| 35/45秒 | AI最近成功21/31秒前，正常等待；NETWORK从±30m精确变±58m粗略。 |
| 安静/监听 | 剩余9分钟；准备听。探针按计划立即取消监听后发送打字问题，不冒充真人语音识别。 |
| 后续主动链 | 研究真实成功；Provider抛IllegalArgumentException并走原失败短报，TTS onStart/onDone正常。主提示“上次请求失败，等待下次触发”，蜂窝仍已验证/VPN仍检测，AI最近失败44秒前与研究最近成功59秒前各自显示。没有遗留work。 |
| 采到的task/source | DECIDING、OVERVIEW、SEARCH_DECISION；LAST_KNOWN、NETWORK；Voice采到IDLE、SPEAKING。没有采到Topic/主动问题搜索长等待，不宣称这些真机条件通过。 |
| 结束 | work为空、Journey不运行；初始未验证和结束默认页准确。 |

IllegalArgumentException只证明真实失败如实投影/原主动失败短报，不能把失败回答当作内容质量通过。真实Overview超过30秒、Topic接管和真实超时仍待场景。本轮没有人为制造真实AI服务故障。

最终独立APK拉回base核对相同SHA256：`BACA47DD3326339EC9395A5698478231F0AE1B91473BAD0EF6B6BD403A0472F1`。最终照片证据 `runtime-phone-final-idle.png`、`runtime-phone-final-portrait.png`、`runtime-phone-final-landscape.png`；初轮监听/安静/恢复截图另存。早期桥接长文案截断已缩短为“沿用 NETWORK”，不将早期截断图作为最终通过证据。具体大小/三个APK哈希见根HANDOFF。

探针首次启动顺序使测试线程等待Activity初始化，手工终止独立测试进程；已改为先Activity后runtime并完成运行。最终安装后Android16从后台启动测试Activity未自动显示，宿主通过ADB显式打开独立包后探针完成；复现时先 `adb shell am start -n com.kitt.reader.runtimecheck/com.kitt.reader.MainActivity` 再instrumentation。测试启动限制不作生产崩溃/全部手机通过。测试runner已卸载，独立主包保留供验收，无遗留Journey/前台服务。

## 安装与签名边界

标准 Debug/Release APK：`com.kitt.reader`，0.3.4 / code9 / Android8.0+；独立验收APK：`com.kitt.reader.runtimecheck`。三者为验收签名，非 Play 正式发行身份。

手机原 0.3.3 的证书 SHA256：
`6bc696a7310db472b6481bd8fd63103bb56590fc90573815ff2ce33dc4dff92a`。

本机及用户提供 `D:\QSYNC\peanut-pim-dev.keystore` 的证书均为：
`3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。

Android 拒绝覆盖：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`。没有卸载原应用、清除数据或搬运 OAuth。标准包可用于同签名升级/新安装，但**不能覆盖当前手机原0.3.3**。原构建电脑的 `%USERPROFILE%\.android\debug.keystore` 若证书匹配前者，即可使用 `scripts/verify.ps1 -Offline -SigningKeystore <原密钥路径>` 对验收 Debug/Release 重签，再 `adb install -r` 保留数据升级。脚本只支持既有 androiddebugkey/default Android debug password 约定；不提交密钥。

## 最短人工验收与仍待场景

1. 立即打开独立“沿途 0.3.4 验收”，检查初始未验证、开始定位/来源/精度/年龄、横竖屏状态与大按钮、安静/恢复、说话/打字/结束。当前独立包已保存真实ChatGPT配置；切换Fake时其内容只作演示。
2. 找到匹配原手机签名的原debug.keystore后覆盖升级原包，保留原OAuth；真实慢Overview/Topic与接管、网络仍通但Provider不可达/超时后的主提示和结果年龄需继续确认。当前独立包一次真实成功/失败与研究成功已经有探针证据，不等于全部内容验收。
3. 同一驾驶旅程 GPS→NETWORK→GPS、双方都不可用后桥接→未知、本人确认的国外 VPN 出口、粗定位地标抑制，继续按 [定位验收](ACCEPTANCE_LOCATION_FALLBACK.md) 做外场；Issue #14 不关闭。
4. 真人中文 ASR/听感、蓝牙/扬声器音频、长时间后台/锁屏和恢复、Galaxy Tab S9 真硬件/大字体/键盘遮挡仍待人工。已有布局模拟不是平板真机通过。

只在停车或乘客操作时验收。完成第一阶段后停止，待用户总验收；不开始 Stage 2。
