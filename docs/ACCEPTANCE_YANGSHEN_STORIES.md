# V0.5.0 / code17 杨慎故事最短验收

**仅验收候选；本轮不安装、不操作手机、不合并。** 用户已确认v2.1双篇最终文字，本轮来源复核相符。两篇 `candidate / fact_gate=passed / visitor_gate=pending`，审批范围只到 `acceptance_candidate`；真实耳听、正式发布与实机全链未完成。#23/#24外场独立PENDING。

## 当前门禁与安装边界

标准 `python scripts/build-story-packs.py --check` 应PASS且完整故事0；加 `--require-playable` 的正式发布门禁仍BLOCKED。`scripts/build-runtime-smoke.ps1 -Offline` 在独立目录按真实文字和事实审批生成候选，只有Debug/runtimecheck装载候选；这一步不是正式批准，也不能把耳听写成PASS。

候选 applicationId 必须为 `com.kitt.reader.runtimecheck`、versionCode17、唯一原证书 SHA256 `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`。**未来仅用户另行明确授权安装后**：先只读核手机当时实际包名/版本/签名、候选哈希与同签名递增；满足才使用一次 `adb install -r`，不卸载、不清数据、不降级、不重签。核同一账号与原设置/数据保留，不导出凭据。标准 `com.kitt.reader` 不能替代账号所在runtimecheck包。

独立无GPS试听需另获授权安装配套同签名测试APK：`artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。先试听两篇，再补真实位置下完整自动链路；无新鲜位置保持安静/PENDING。

## 不需要GPS的独立完整试听

1. 只在后续明确获准安装同签名runtimecheck候选及配套instrumentation之后进行；先结束当前Journey。
2. 显式手动执行 `mode=story-preview`，`narrative_id=yangshen-main`；这不是默认启动流程或探索地点。仪器入口只读取事实/文字合格的同一候选正文，离线中文TTS强制开启；缺审核、缺正文、缺声音均不能假成功。
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

JVM中的正式批准状态仅是明确标记的测试夹具；另有实际候选状态隔离回归，验证纯本地选择、依赖、全正文和Journey状态。Robolectric长篇TTS测试手动控制最后分段回调；不等于设备实际朗读或真人听感。本轮实际数目、执行时间、哈希见 `docs/evidence/v050-final-verification.json`；旧915项历史在 `docs/evidence/v050-verification.json` 和根目录HANDOFF。
