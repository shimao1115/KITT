# V0.6.0 D1 县级双包发行容器与签名契约（KCPK v1）

> 2026-10-17，[Issue #38](https://github.com/shimao1115/KITT/issues/38) / 分支 `feat/v060-d1-signed-county-packs`。
> 本文件定义 **D1 的发行候选格式与验签规则**，是 D0 [Schema 2 契约](COUNTY_PACK_D0_CONTRACT.md) 之上的一层封装，**不修改** D0 的任何内容门禁。
> **D1 没有生产签名根、没有 GitHub Release、没有下载/导入/安装路径**；本轮只有 TEST_ONLY 临时密钥与离线验签。

## 1. 边界

| 属于 D1 | 不属于 D1（另行授权） |
| --- | --- |
| 版本化、定序、有限大小的发行容器；canonical manifest | 生产私钥生成/保管、官方公钥固化、密钥轮换与撤销执行 |
| 确定性打包（同一输入 → 同一 unsigned 字节） | GitHub Tag/Release、KITT-PACKS 第二仓库、正式分发 |
| `SHA256withRSA` 签名与**调用方预置**可信锚的离线验签 | 手机侧下载、导入、原子启用、回滚、版本比较 UI（V0.6.1） |
| 先签名、后 D0 内容门禁的完整拒绝链 | 真实新都内容对外发行许可（本轮审计为 BLOCKED） |
| 真实测试、负例、两干净目录字节复现 | 全国采集、PR #29、杨慎 Visitor Gate、任何故事审批变更 |

## 2. 容器格式（不用 ZIP）

KCPK v1 是**固定字节帧**，不是 ZIP：

```
0..8      magic = "KCPK0001"（8 ASCII 字节）
8         format_version = 1（u8）
9..13     manifest_length（u32 BE）
13..      manifest（canonical UTF-8 JSON，键名排序、紧凑分隔符）
          block 1: kind=1 fact_manifest  [kind u8][len u32][bytes]
          block 2: kind=2 facts
          block 3: kind=3 narrative_manifest
          block 4: kind=4 narratives
          签名段：[alg_len u8][alg][key_id_len u8][key_id][sig_len u16][sig]
```

选择固定帧而非 ZIP 的理由：没有成员路径（不存在路径穿越/绝对路径）、没有重复成员歧义、没有压缩炸弹、没有中央目录与时间戳漂移。四个块**必须**按 1/2/3/4 顺序各出现一次，任何缺失、重复、未知 kind 或尾部多余字节都拒绝。

| 上限 | 值 |
| --- | --- |
| manifest | 16 000 B |
| 单块 | 500 000 B（≥1 B） |
| 整包 | 1 100 000 B |
| key_id | ≤64 B，`[a-z0-9][a-z0-9._-]*` |
| 版本字符串 | ≤80 B |
| 签名 | ≤1024 B |

## 3. Manifest

| 字段 | 说明 |
| --- | --- |
| `format` / `format_version` | `KCPK` / `1` |
| `schema_version` | `2`（D0 Schema 2） |
| `area_id` / `parent_id` | `cn-六位县级码` / `cn-四位地级码+00`；必须是普通县→市州层级，`00/90` 特殊层级仍拒绝 |
| `publisher_scope` | `TEST_ONLY` 或 `PRODUCTION`（D1 只产出前者） |
| `key_id` | 与签名段、可信锚表一致，三处不符即拒绝 |
| `source_commit` | 40 位小写十六进制，固定内容来源版本 |
| `minimum_code` | 要求的最低 App versionCode；高于验签方支持版本即拒绝；必须与两份内层 manifest 相同 |
| `sequence` | 单调发布序号（≥1）；低于调用方给定的最低接受序号即拒绝（`sequence_rollback`） |
| `fact_version` / `narrative_version` | 两侧独立版本 |
| `content_kind` | `fact_only` / `fact_and_narrative`；`fact_only` 而实际有故事时拒绝 |
| `blocks` | 四块的 kind/bytes/sha256 |
| `content_sha256` | 四块按序长度前缀 SHA256（不含 manifest，避免自哈希循环） |
| `builder` | `kitt-packtool/1` |

编码规则：键名排序、紧凑分隔符、整数字面量。字段顺序不同不改变字节。解析是**严格**的：未知字段、缺字段、以及任何自带信任声明（`public_key` / `publisher_public_key` / `trusted` / `trust_anchor` / `signature_verified` / `signature` / `certificate`）都直接拒绝。

## 4. 签名与信任

- 算法：**`SHA256withRSA`（PKCS#1 v1.5）**。JDK 17 与 Android `minSdk 26` 平台自带，无需额外 provider；PKCS#1 v1.5 对固定密钥是**确定性**的，因此同一密钥下整包字节也可复现（见第 6 节）。不采用 PSS（随机盐会破坏复现）与 Ed25519（Android 平台支持自 API 33 起，超出 minSdk 26 承诺）。
- 签名覆盖：**magic + version + manifest + 四块**，即签名段之前的所有字节。签名不覆盖自身。
- 信任根：**只来自调用方传入的 `TrustStore`**（`key_id → PublicKey + scope`）。`TrustStore` 默认**为空**，空表对任何输入都返回 `no_trusted_publisher`。
- **scope 绑定**：`TEST_ONLY` 锚只能验 `publisher_scope=TEST_ONLY` 的包，`PRODUCTION` 锚只能验 `PRODUCTION` 包；同一 `key_id` 绑定两个不同 scope 时构造即失败（`ambiguous_anchor`）。
- 包内自带的公钥、`trusted=true`、`signature_verified` 一律不构成信任；SHA256 只证明完整性，不证明作者。
- **不复用 Android APK 签名证书**（`3139a293…9502ba`）作为知识包发行密钥，D1 也未读取该私钥。

## 5. 验签顺序与拒绝码

顺序固定：**解码 → manifest 严格解析 → 可信锚 → 签名 → manifest 规则（版本/地区/类型/尺寸/序号≥1）→ 调用方序列下限 → 分块摘要 → 整包摘要 → 外层/内层绑定 → D0 内容门禁**。任一步失败即 `REJECTED`，不会降级为“部分可用”；新增检查全部排在信任与签名之后，不会绕过验签。

| 拒绝码 | 触发 |
| --- | --- |
| `container_too_large` / `container_truncated` / `magic` / `format_version` | 帧头与大小 |
| `manifest_size` / `manifest_schema` / `unknown_field` / `payload_trust_claim` | manifest 结构 |
| `duplicate_block` / `unknown_block` / `missing_block` / `block_kind` / `block_size` | 块结构 |
| `signature_section` / `signature_algorithm` / `signature_size` / `field_size` | 签名段 |
| `no_trusted_publisher` / `unknown_key_id` / `key_id_mismatch` / `bad_signature` | 信任与签名 |
| `source_commit` / `minimum_code` / `publisher_scope` / `content_kind` / `builder_version` | 版本与声明 |
| `area_id` / `parent_relationship` | 地区码 |
| `inner_binding` | 已签名外层宣称与内层四块不一致（详见第 6 节） |
| `sequence` / `sequence_rollback` | 序号非法，或低于调用方最低接受序号 |
| `ambiguous_anchor` | 同一 `key_id` 绑定两个不同 scope/公钥 |
| `block_integrity` / `content_digest` | 分块/整包摘要 |
| `content_gate_unavailable` / `content_rejected` | 内容门禁缺失或内容不合法 |

内容门禁（`D0CountyGate`）在签名通过后调用 D0 的 `CountyPackLoader`，并且**比 D0 阅读路径更严格**：D0 允许“只显示基础事实、隐藏被阻断的依赖”，但发行候选若静默丢弃事实或故事就是虚假陈述，因此**任何 blocked 条目都拒绝整包**。`fact_only` 包必须如实报告 `0` 篇故事。

## 6. 外层/内层绑定与防回滚

**外层/内层绑定**（`InnerBinding`）：D0 只保证四块**彼此**一致，不知道签名外层的宣称。因此验签器在签名、分块摘要、整包摘要都通过后，逐字段比对：

| 外层字段 | 必须等于内层 |
| --- | --- |
| `area_id` | 两份 manifest 的 `area_id`、两份正文的 `area_id`、Fact `area.area_id` |
| `parent_id` | Fact `area.parent_id` |
| `fact_version` | Fact manifest 与 Fact 正文的 `content_version` |
| `narrative_version` | Narrative manifest 与 Narrative 正文的 `content_version` |
| `minimum_code` | 两份 manifest 的 `minimum_code` |
| `blocks[].pack_id` 隐含 | 两份 manifest 与两份正文的 `pack_id` 必须是 `县ID-fact/-narrative` |

不一致一律 `inner_binding`（`detail` 给出具体字段）。这样“用另一县的合法 TEST_ONLY 块重新签成外层别县”会失败，`Accepted.manifest` 永不谎报实际接收内容。

**防回滚**：manifest 带单调 `sequence`。D1 **不保存任何客户端状态**——由调用方传入 `minimumSequence`，低于它就 `sequence_rollback`。这满足任务卡“未授权序列降级拒绝”的最薄要求，但**不是**完整的防回滚：没有持久安装库、没有版本库、没有撤销中心，也不防止攻击者同时伪造更高序号（那仍需要可信锚保护）。真正的持久序列与撤销属 V0.6.1/D2。

## 7. 可复现

- 容器不含时间戳、绝对路径、用户名、机器指纹、Git 脏状态；`source_commit` 由调用方显式传入。
- 同一输入 + 同一 builder 版本 + 同一 TEST_ONLY 密钥 → unsigned 四块字节、`unsigned_sha256`、整包 `container_sha256` 全部逐字节相同（两干净 worktree 实测，见证据）。
- `tools/d1/**` 在 `.gitattributes` 中标记 `-text`，避免 Windows 检出把 LF 改成 CRLF 而改变载荷字节与所有摘要。
- 签名本身的跨密钥复现**不作承诺**：换密钥必然换签名；D1 只承诺同一密钥下的确定性。

## 8. 密钥治理规划（D2 实施，本轮不执行）

| 事项 | 计划 |
| --- | --- |
| 生成 | 离线、专用机器、无网络，RSA-3072 或更强；一次性仪式，参与者与产物双人复核 |
| 保管 | 私钥不出硬件（HSM/YubiKey/离线加密介质）；不在 CI、不在开发机、不入 Git |
| 分发 | 公钥以 `key_id + SPKI + scope + 生效/失效时间` 形式**随客户端版本固化**，不经包内传递 |
| 轮换 | 新旧双锚并存窗口；包可携带 `key_id` 但客户端只认预置表；轮换需发版 |
| 撤销 | 客户端吊销列表随版本更新；撤销后该 `key_id` 一律 `unknown_key_id`；已安装包保留可读但停止更新 |
| 最低版本 | `minimum_code` 与 `content_kind` 配合，防止旧客户端读新格式 |
| 事故响应 | 保留 `source_commit` + `content_sha256` 以便全量追溯与重签 |

## 9. D2 遗留风险

1. 生产密钥仪式、双人复核与 HSM 选型尚未设计落地，本轮**没有**任何生产私钥或官方公钥。
2. 公钥固化需要 App 发版；D1 的验签库只存在于 JVM 工具与单元测试，**未进入 APK**（V0.6.1 才接手机侧）。
3. 撤销/轮换的客户端语义、离线设备长期不联网时的锚过期策略未定。
4. 持久化防回滚（已安装序号、失败回退、撤销列表）未实现，D1 只有调用方传入的最低序号。
5. 真实新都内容对外发行许可仍未取得（2 个来源权利不明，见验收记录），D1 只产出 TEST_ONLY 合成样板。
6. 真机 JCA 可用性未验证（无设备）：D1 只用 JDK 17 的 `SHA256withRSA`，Robolectric 仍是 JVM 实现，**真 Android 记录为 PENDING**。
7. 分发层（Releases、索引托管、镜像）完全未实现。

## 10. 命令

```bash
# 1) 一次性 TEST_ONLY 测试密钥（写到工作树之外，拒绝写入仓库内）
./gradlew :app:d1Pack -Pd1.command=keygen -Pd1.root=. -Pd1.keydir="$TEMP/kitt-d1-keys"

# 2) 读取固定仓库源码生成 TEST_ONLY 候选
./gradlew :app:d1Pack -Pd1.command=build -Pd1.root=. -Pd1.sample=fact-only \
    -Pd1.keydir="$TEMP/kitt-d1-keys" -Pd1.out="$TEMP/kitt-d1-out" -Pd1.commit=<40 位提交>

# 3) 离线验签 + D0 内容门禁
./gradlew :app:d1Pack -Pd1.command=verify -Pd1.pack="$TEMP/kitt-d1-out/pack.kcpk" \
    -Pd1.keydir="$TEMP/kitt-d1-keys"

# 4) 真实新都内容的外部发行权利审计（只审计，不产出包）
./gradlew :app:d1Pack -Pd1.command=audit-newdu -Pd1.root=.
```

输出为 `status=ACCEPTED_TEST_ONLY` 或 `status=REJECTED` 加 `code=`，并附 `facts/sources/claims/stories` 计数与 `exit_code`。私钥不打印、不进日志、不进 Git。
