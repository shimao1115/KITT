# V0.5.0 / code17 杨慎故事最短验收

本轮不安装手机。两篇仍是待审稿，当前runtimecheck候选构建应被内容门禁阻断。下面是真正审稿通过、另行生成候选并获安装授权后的验收步骤，不是本轮实机证据。#23/#24既有外场待验不被本页改写。

## 先审文字与事实

阅读 [主篇全文](../content/yangshen/yangshen-main.md)、[延伸篇全文](../content/yangshen/yangshen-followup-dianzai.md) 和 [来源/争议/逐句审校](YANGSHEN_CONTENT_REVIEW.md)。用户认可必须记录真实证据，绑定正文哈希及Claim引用集；不能只把 `draft` 改成 `approved`。大礼议解释、两次廷杖/返乡时序、词作归属与版本、古书转录及版权限制需独立/人工复核。

打包后运行 `python scripts/build-story-packs.py --check --require-playable`，必须存在同一杨慎对象的合格主篇、含新事实的延伸篇。当前审批为空，预期 BLOCKED。没有这一步的PASS，不生成候选runtimecheck，不发布。

## 不需要GPS的独立完整试听

1. 只在后续明确获准安装同签名runtimecheck候选及配套instrumentation之后进行；先结束当前Journey。
2. 显式手动执行 `mode=story-preview`，`narrative_id=yangshen-main`；这不是默认启动流程或探索地点。仪器入口只读取已批准正文，离线中文TTS强制开启；缺审核、缺正文、缺声音均不能假成功。
3. 未来的调用示例：`adb shell am instrument -w -e mode story-preview -e narrative_id yangshen-main com.kitt.reader.runtimecheck.test/com.kitt.reader.HotfixPhoneProbe`。本轮没有运行该命令，也不据此授权任何安装。
4. 试听不写入GPS、不启动Journey、不写已播记忆/纪要、不改账号和语速/声音设置。朗读与正常沿途使用同一份完整正文与AndroidVoice分段路径。测试结果绑定文字SHA256。
5. 用户耳听到结尾，检查中文发音、节奏、术语、是否有拖沓/难懂段落；另试听延伸 `yangshen-followup-dianzai`。`PASS_ENGINE_ONLY`只证明TTS引擎最终回调，不证明听者听见或认可。真人结果另记。

## 有可信物理位置的Journey黄金路径

1. 使用新鲜GPS/NETWORK且通过原新都多边形门禁，不注入位置、不把缓存/IP/代理位置当现场；正常开始沿途，无需配置云账号。
2. 分别在有网、关闭Wi-Fi与移动数据、模型不可达条件检查：完整主篇直接本地播放，自动Director/Research增量均0。断网拿不到新鲜位置只能记PENDING/安静，不能冒称讲述成功。
3. 完整播毕才记COMPLETED与可重播正文；“再说一遍”复播最新已完成的整篇文字。“再讲一点”在杨慎主题下选择未尝试过且已审的新事实延伸；没有合格延伸就简短说明。主篇不自动重复；其余5个旧对象仍可提供基础事实。
4. 播放中跳过、说话/打字、安静十分钟及提前恢复、结束旅程：立即遵守既有生命周期；打断/失败不标完成、不重播半截，迟到回调无效。一般32条历史轮换后，杨慎单趟尝试仍保留；开始新Journey后清空。
5. 如果用户主动云问题失败，等待该请求结束后，有新鲜可信位置仍能再次选择本地内容；失败不进入无限重试。只记录请求计数/状态，不导出账号、私密纪要或token。
6. 结束页仍无评分、评语、保存按钮；自动纪要、直接返回、历史JSON兼容和原签名/设置保留另按V0.4.2回归。进程重建不恢复“已完整听过”的判断。

## 工程证据边界

JVM中的审核通过故事仅是明确标记的测试夹具，验证纯本地选择、依赖、全正文和Journey状态。Robolectric长篇TTS测试手动控制最后分段回调；不等于设备实际朗读或真人听感。实际数目、执行时间、哈希与构建阻断结果见 `docs/evidence/v050-verification.json` 和根目录HANDOFF。
