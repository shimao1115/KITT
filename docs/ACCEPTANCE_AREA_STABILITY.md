# 沿途 V0.3.5.1 地区名称稳定性验收 — Issue #17

日期：2026-10-12（Asia/Shanghai）。仅修复行政区 enrichment 生命周期；真机离线，vivo 现场与覆盖安装尚未执行。当前交付结果、APK/hash/commit 见根 [HANDOFF](../HANDOFF.md)。

实际门禁：`scripts/verify.ps1 -Offline` **Debug349 / Release336，失败/错误/跳过均0；两种assemble/lint/签名PASS**。runtimecheck独立包assemble/测试APK构建/lint与证书核对PASS，证书匹配V0.3.5交付包；没有执行手机测试APK。工程commit `74161cf`，[十分钟报告](evidence/v0351-area-stability.txt) 与 [摘要](evidence/v0351-verification.json) 已入Git。

## 原因与修复

确定性测试在旧实现上失败：新鲜同地定位持续到第31秒，成功 geocode 的30秒锚点寿命与静止120秒复查之间出现名称空窗。空 Geocoder 结果覆盖旧成功值也是独立确定路径。这证明代码通路，不证明每次手机闪烁都是同一原因。

只保存一个本次运行中的成功地区锚点。当前设备位置持续新鲜、可信、同来源且距锚点≤450m时，名称不受首次查询年龄限制。空/异常/8秒超时不替换成功值；可靠不同区域或仅区县结果直接替换。刷新仍走原节流，使用单调时钟计间隔：驾驶至少15秒/250m，低速至少30秒/150m，静止120秒；请求 pending 不并发。

GPS当前年龄≤15秒、NETWORK≤30秒，精度必须>0且≤1500m；超过450m、丢位置/过时、观测中断、来源改变撤销缓存。已失效的旧名称不能因回原地/回切而复活，越界期间晚到结果也不得写回。精度>500m或仅粗权限撤销镇街；精度恢复须新解析才能恢复镇街。LAST_KNOWN只保留上个区县的不确定背景，原测量最多60秒，不查询、不带镇街；重启新 resolver，不持久化行政缓存。

坐标、物理来源与优先选择器未改，仍只注册 LocationManager GPS+NETWORK。FUSED不接生产；无地图SDK/IP/VPN/DNS/搜索定位推断。日志仅 geo_started/success/empty/error/timeout/discarded、失效原因，无坐标、地区名、轨迹、异常文本或认证信息。

## 自动证据与范围

- `DrivingAreaResolutionTest`：修复前新增十分钟同地新鲜位置断言失败，修复后通过；旧位置与>450m负例保留。
- `AreaResolutionCacheTest`：GPS/NETWORK各600秒连续新鲜定位、只有一次AreaCards进入；空值、可靠不同区域、仅区县替换、100km/h第17秒越过450m、过时/缺失/观测中断、切换、粗精度、停止及晚到结果。
- `RealAreaContinuityTest`：生产RealLocationSource/选择器/resolver/AreaCards，Robolectric API34合成NETWORK ±30m；600秒连续发布，6次查询，成功→空→异常→8秒超时→成功，无成功后null或镇街消失，1次章节进入。另测从未成功300秒仅3次查询、移动/切换、桥接/重启、不同区县即时替换、粗权限。
- `AndroidAreaResolverTest`：系统Geocoder不存在（API26/34）、超时与异常脱敏、源切换/停止的晚结果、过时/桥接/不可用精度不查询。
- 全量Debug/Release单测、assemble、lint与签名，以及成都→绵阳80km/h主链、100km/h/16×研究、快进/地标等六组既有模拟：实际结果以HANDOFF与`artifacts/verification.json`为准。GPS↔NETWORK回归是确定性设备回调，路线仍为29点粗粒度测试折线。

这些测试不证明OEM自然回调、真实Geocoder地址质量、真实边界、VPN出口、真人研究/ASR/TTS听感。Android26–32的同步Geocoder调用可能无法被协程立即终止，期间保持单请求，GPS持续，晚结果仍过连续性校验；本轮8秒超时证据为可取消的注入查询，vivo Android16使用异步平台接口。

## 最短人工验收

1. 设备上线后对runtimecheck执行同签名`adb install -r`；不要卸载/清数据。确认版本0.3.5.1、原ChatGPT账号/模型/设置仍在。构建证书与V0.3.5交付包一致，但本轮未实机验证安装与账号保留。
2. 室内原地前台、保持新鲜NETWORK/GPS ±30m。首次成功名称后连续看5–10分钟，跨30秒及120秒刷新仍有地区名称，无章节假退出/重复研究。首次始终无结果保持“未解析”是正常降级。
3. 停车或乘客操作：无网络/临时服务失败仍有可信同地名称；真实离开、来源切换或位置过时应撤销镇街，不能拿旧街道当新现场。恢复可靠新解析后新地区应取代旧值。
4. 说话/打字打断、安静/提前恢复、模拟100km/h/16×与结束正常。来源切换可能短暂未解析，这是本版保守失效策略；无可靠新位置不能要求持续挂旧名称。

验收后停止；#18、#13与离线包未施工。#14/#15外场/用户验收继续独立保留。
