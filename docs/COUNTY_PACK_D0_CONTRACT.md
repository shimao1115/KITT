# V0.6.0 D0 县级双包读取契约与兼容迁移

本文件描述 code20 的内部 Schema 2 草案，不是发行格式或外部安装协议。唯一授权为 Issue #36 / Draft PR #37。生产资产仍是原新都文件，正文、审批、Claim 修订、manifest 字节均不修改；正式完整故事仍 0。

## 输入与信任

`CountyBytes` 接收 Fact manifest、Fact 原始 UTF-8 bytes、Narrative manifest、Narrative 原始 UTF-8 bytes，以及调用方给定的整体 SHA256。整体摘要依次计算四块数据，每块前置 4 字节 big-endian 长度。两份 manifest 各自另有字节数及内容 SHA256，版本不要求相等。

`CountyPackLoader` 默认拒绝全部输入。注入的 verifier 在检查前收到独立拷贝，成功返回 `BUNDLED_APK` 或 `TEST_ONLY` 来源标签；标签不从 JSON 取得。摘要只验证完整性，不能产生身份信任。普通 App 唯一调用方是 `BundledCountyPacks.load(Context)`，只读固定 APK 内置资产，不扫描目录、不接受路径/URI/网络文件。转换后的精确 bytes 才获内置信任。

测试目录的 verifier 使用每次 JVM 运行临时生成的 RSA 公私钥和 SHA256withRSA 验证四块数据；它只返回 TEST_ONLY。测试密钥及第二县数据不在 main/assets，不进入产品 APK。D0 没有真正的外部发行者信任根，也没有外部县包启用入口。签名治理、签名成包、GitHub Release、下载/导入/原子安装均留后续独立授权。

## 最小结构

| 层 | 字段与限制 |
| --- | --- |
| 每份 manifest | `schema_version=2`、`minimum_code`（20～当前App code）、`kind=fact/narrative`、`area_id`、`pack_id`（县ID加 `-fact/-narrative`）、独立 `content_version`、`bytes`、`sha256`；不超过16KB |
| 每份正文 | `schema_version=2`、相符的 `pack_id/content_version/area_id`；每份1～500000 bytes，严格 UTF-8 |
| Fact 区域 | `area.area_id` 为 `cn-六位县级码`，不以00结尾；`parent_id` 为市州码，省/市/县名非空且每字段≤80字符；不推导镇街或坐标 |
| Fact 索引 | `entities/sources/claims` 各≤200条，ID为1～64字符 ASCII 小写字母、数字、下划线、连字符；县内唯一，不以中文名称为主键 |
| Entity | `entity_id/title/family/basic_facts`；family 是既有 TopicFamily，与六类兴趣无自动映射；县内标题不能有歧义 |
| 基础事实 | `fact_id/summary/coverage/source_ids`，每对象≤100条；正文≤250字符，coverage≤65字符；源丢失/撤回/权限或修订错误只隐藏引用它的事实 |
| Source | `source_id/title/url/kind/access_status/accessed_at/rights/registry_revision`；可引用原始资料，不分发其全文；`source_permissions[id]=allowed` 是可信生产方对摘要使用权限的机器判定，不能由哈希冒充人工许可 |
| Claim | `claim_id/entity_id/area_ids/text/status/temporal_class/rights/revision/evidence`；可播放引用必须 verified、stable、本县关联、权限allowed；evidence 逐项绑定source ID、accepted source revision、locator及supports关系 |
| Narrative | `narrative_id/story_type/entity_id/area_id/title/text/text_sha256`、`review_status/fact_gate/visitor_gate/approval_ref/license`、六类主/辅标签、`supported_claim_refs`；每篇100～6000字符、1～100 Claim引用；最终Journey marker总长度≤240字符 |

`registry_revision/revision` 是去掉自身字段后，对递归按键排序的紧凑 JSON UTF-8 SHA256，与既有杨慎契约一致。只有 `approved + fact_gate=passed + visitor_gate=passed + 非空approval_ref + CC-BY-4.0` 故事才能入通用合格集合。单个 source/Claim 的权限、撤回或修订失配只阻断实际引用它的篇章，不能因重新封装整个县包而放行旧故事。缺少未引用的 source/Claim 不影响其它篇章。跨县/共享Claim尚无解析器：缺依赖准确阻断，绝不去别县按同名ID补齐。

包级错误（大小、格式、Schema、App code、整体/分块摘要、错地区、重复ID、悬空实体）拒绝整个输入；篇级错误（审批、正文、类别、具体Claim/Source依赖）记录固定 blocked reason，只停用受影响篇。基础事实可以独立阅读时显示“仅基础事实”。目录重复县ID/同地区别名ID/父市州冲突均拒绝，禁止静默覆盖。

## 生产兼容与测试隔离

`BundledCountyPacks` 先复用原 `OfflinePack.parse` 的基础资产/几何门禁和 `StoryPacks.parse` 的旧杨慎门禁，再内存适配 Schema 2。6个原基础来源用 `legacy-` 前缀与11个杨慎研究登记来源隔离（登记中包括未采纳项，不代表全部可用）；22条基础事实保持原文本、顺序和原运行视图。允许摘要使用是继承既有资产许可，不能为此批准新故事。杨慎 Source/Claim 的原修订原样保留。

`ExploreCatalog.load` 使用新县级目录；`KittRuntime` 通过该目录取得原新都 `OfflinePack` 和兼容 `StoryPacks`。保留旧新都几何及原故事ID/coverage/preview边界，设置、账号、TripStore、DirectorLoop、GPS和TTS流程不重写。Narrative损坏时只回退到独立校验通过的原基础事实，不开放任何候选。

通用 StoryPacks 不再有“仅一篇main/仅杨慎”的限制，但逐篇原审核和依赖门禁仍先于兴趣排序。测试故事的 Journey ID与Claim coverage使用 `县ID/本地ID`，防止县间同名碰撞；已尝试过的故事不重复，同Claim换ID也不能再自动讲。延伸仅在本实体main完成后、含新Claim时可选。旧专用解析器的 `<=1 main` 限制仍保留，防止既有审核边界扩张。

市州目录只存县ID列表，正文只在县对象里。测试使用新都行政标签与不相邻的湖北宜昌西陵区标签，所有具体对象、人物、事实、来源和故事均明标 TEST_ONLY 合成，不宣称已核验。通用县的只读事实视图没有几何，不能被GPS触发；在其它县探索不改变Journey位置。D0只证明通用读取、多县隔离与测试级选题，不宣称第二县已可用于沿途或全国县域已覆盖。

人工工程验收只需审阅 PR #37、全量证据及这些边界。手机升级、故事正式审批、工程合并及后续里程碑仍分别等待授权。
