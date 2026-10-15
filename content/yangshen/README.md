# 杨慎文字双包：审校源，尚非正式成品

- `facts.json`：16个核验条目（14 verified / 1 lead / 1 rejected），1个人物对象，9条来源登记；v2补赴戍病困及书中商旅歌谣两条依据。verified 仅表示 Codex 回读来源相符，独立事实审查/用户最终认可仍待完成。原6对象22事实和几何保持原文件，不删除或重新审定历史结论。
- `narratives.json`：当前v2.1最终候选 `2026.10.15-draft2.1`；主篇583字符，延伸297字符（均含标点/换行），均 `draft`。本轮仅主篇两句润色，延伸全文及元数据均保持v2不变。用户认可方向与整体讲法，最终逐字文本仍待确认。正文的Markdown副本供阅读，不是另一套成品。正文哈希、每段 Claim 和接受修订均可回读。篇幅没有程序最短门槛。
- `approvals.json`：当前为空。只有真实审稿证据才能填写；AI不能代填“用户已读/真人听完”。审批绑定正文 SHA256 及 Claim 引用集 SHA256，修改正文/依赖后必须重新批准。
- [审校报告](../../docs/YANGSHEN_CONTENT_REVIEW.md)：Source Registry、已读/未读、争议、逐句追踪及版权/游客自审。

`python scripts/build-story-packs.py` 生成两个独立版本的手机 JSON 和各自 manifest。它验证数据结构及指纹，不鉴定史实。未获批准的篇目全部排除，**APK 当前完整故事数为0**，不会朗读这两篇待审稿。

`python scripts/build-story-packs.py --check --require-playable` 是候选构建前置门禁，目前应以 BLOCKED 退出。`scripts/build-runtime-smoke.ps1 -Offline` 在 code17 也执行此门禁，不会把没有批准正文的 APK 冒充本版工程候选。

Android 的 `StoryPacks` 只消费审核通过、依赖匹配的故事；无合格主故事时使用 V0.4.2 既有基础事实路径，并明确提示降级。测试里的 `TEST_ONLY_NOT_CONTENT_APPROVAL` 只存在 JVM 临时数据，不是审批，不进入应用资产。没有下载器、导入器、音频包或用户兴趣界面。
