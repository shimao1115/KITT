# V0.6.0 D0 只读设计审计：PR #29 与现有县包代码的接口差距

> 日期 2026-10-17；核查范围仅当前 GitHub。**设计审阅≠执行PR #29或批准故事发行**。主干 [PR #35](https://github.com/shimao1115/KITT/pull/35) 已合并，code19 真机用户体验 PASS。后续任务见 [Issue #36](https://github.com/shimao1115/KITT/issues/36) / 分支 `TASK_CARD.md`。

## 1. PR #29 现状与可复用内容

- [PR #29](https://github.com/shimao1115/KITT/pull/29) **Draft / OPEN / 尚未合并**，源分支 `docs/county-knowledge-collection-v1`。比较当前 main：该分支相对主干 **ahead 2 / behind 64**，diff 仅两个文档：`docs/KITT_COUNTY_KNOWLEDGE_COLLECTION_V1.md`、`docs/templates/KITT_COUNTY_COLLECTION_TASK.md`。它是县域生产规范草案，**不是可直接合并的 V0.6.0 Android 实现**；不要改其状态或擅自 merge/rebase。
- 可直接继承的**设计原则**：县级 Area 稳定 ID 与权威行政名录；逐镇街六类扫描与缺口记录；Entity/Source/Claim/Narrative 严格来源、版权、时间地理和每个具体 claim 修订；只有经过审核的完整 Narrative 才算正式可播；市州目录**只聚合县包、不复制市州正文**；非相邻区县测试；真实范围调查覆盖≠可用故事覆盖≠精确GPS触发覆盖。
- **此次不搬运**县镇真实生产模板到 App 的运行时 Schema，不创建六县调查工单、不声称调查完成，不把旧PR #29的“阶段A：双来源模式全部完成”变成本 D0 前置条件（V0.5.2已将该模式 DEFERRED）。

## 2. 主干代码读取得到的实际技术缺口

| 入口 | code19 已有能力 | 通用县包缺口 |
| --- | --- | --- |
| `OfflinePack.load` | 读取 `assets/offline/xindu-*.json/geojson`，真实GPS县界`resolve`、1县事实/对象 | 固定 xindu 文件名和单个`area`；需通用 `area_id`/manifest 输入与多县只读目录，不能用虚构测试几何参加真机定位 |
| `StoryPacks.load/parse` | `ys-*`独立事实和讲述清单、逐Claim修订与来源/权利校验、Visitor/Fact Gate | 硬编码`cn-510114`、`person-yang-shen`、`yangshen-*`、`<=1 main`；仅能承载当前杨慎组，未来需把安全校验与内容绑定解耦 |
| `ExploreCatalog.from/load` | 单县事实列表/来源、已审核正文可浏览；可选本地 TTS | 构造单个`ExploreRegion`；需有县ID索引与市州聚合视图，但 V0.6.1下载UI不在D0内 |
| `KittRuntime/DirectorLoop` | 单 `offlinePack` + `storyPacks`离线优先主链；六类偏好软排序 | 需按可信县域选择已激活包，**不能**让探索搜索位置篡改GPS；本D0必须优先保留旧单县运行行为及位置门禁 |

## 3. 与现行正式规范的对齐和安全风险

1. **内容权威**：`docs/KITT_KNOWLEDGE_STANDARD.md` (V2.4) 负责内容/审稿/版权；`docs/KITT_KNOWLEDGE_FACTORY.md` (V2.4) 负责生产/跨AI协作；`docs/KITT_OFFLINE_PACK_MANAGEMENT.md` (V1.0) 负责包信任/更新。以 `docs/KITT_CURRENT_ROADMAP.md` 决定**县级最小独立分发**、**市州用户操作入口**、V0.6.1下载+本地导入、V0.6.2更新。旧文本的code17/18“当前”、旧“市州为最小详细物理包”等均为历史状态。
2. **同名同ID冲突**：真实行政区域稳定ID、县内实体与跨县共用实体必须区分县链接与统一全局引用，不以中文名称或省市字符串冒充唯一主键；不允许吞没旧县数据。
3. **安全/发行**：SHA256只证明字节一致，不能证明出版者身份；跨端分发需可验证的**受信发行者签名**且与 APK 私钥隔离。D0只允许内置可信资产+TEST_ONLY纯内存夹具，不从手机文件管理器/网络接受可执行于正常APP的外部内容；不为测试造真实发行公钥或发布私钥到仓库。
4. **审核隔离**：主程序正式故事仍**0篇**，杨慎两篇 `candidate / visitor_gate=pending`；模型能解析故事不代表可供游客看到“已审核完整故事”。某claim修订无效只影响真正引用它的讲述，不能因一个包改变就把所有其它故事或未核验内容一起放行/停用。
5. **许可与空间**：镇街调查名录≠合法空间几何；无可信镇街边界就只有可信县级触发。测试区域禁止生产 GPS 自动播报/正式探索目录发现。
6. **兼容与边界**：不能顺便修改 code19 用户设置/保存两个按钮（已用户指定延期）、历史20份/账号加密与 `settings.xml`；不在本D0实现 GitHub Releases/下载器/更新/新仓库，也不将实际未审核的第二县故事构造成正式内容。

## 4. 下一条最小工程任务与条件

**本次 D0 的唯一验收：** 保持新都1县6对象22事实/6来源及原离线行为；用通用县级 Fact/Narrative 独立输入、Area/Claim修订审核约束，测试第二个非相邻县级TEST_ONLY夹具独立寻址/市州目录映射/拒绝坏包，不产生任何新增正式故事，也不从手机外部来源启用未验证内容。若必须重构大段在线/位置/旧故事逻辑才行，先停下来向用户报告，可再拆工程。

**剩余独立工作**：PR #29 文档更新/审阅合并、可信发布私钥管理与 GitHub Release 正式最小闭环、外部包签名/可信启用及回滚、V0.6.1下载与本地导入、六区实地普查、杨慎Visitor审核、旧外场GPS。每项需单独授权；本次不提前施工。
