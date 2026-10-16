# 杨慎 v2.1 文字双包：仅验收候选

用户已确认两篇最终文字，正文/哈希和原Claim修订集固定。Fact `2026.10.15-review3` 含11来源/16核验条目，Narrative `2026.10.15-candidate2.1` 含主篇583字符、延伸297字符；预计时长非实测。

`approvals.json` 记录真实用户定稿链接及Codex来源复核，范围仅 `acceptance_candidate`。两篇 `review_status=candidate`、`fact_gate=passed`，`visitor_gate=pending`、`human_listening=pending`；没有替人签署已听、正式发布、手机安装或PR合并。详细主体、资料读取边界及版本旁证见[审校报告](../../docs/YANGSHEN_CONTENT_REVIEW.md)。Markdown为同一正文的阅读副本。

- `python scripts/build-story-packs.py --check`：校验标准资产，候选全部排除，正式故事0。
- `python scripts/build-story-packs.py --check --require-playable`：正式发布仍BLOCKED，需真人听感和正式审批。
- `python scripts/build-story-packs.py --acceptance-candidate --require-playable --output-dir artifacts/<验收目录>`：只生成绑定文字与事实审核的候选；禁止覆盖标准资产。
- `scripts/build-runtime-smoke.ps1 -Offline`：在独立复制工作区输出仅runtimecheck Debug可读的候选，验证完整正文与原签名；不安装手机。该路径保留真实耳听PENDING，解决首次候选与耳听的先后循环。

标准包仍用基础事实降级；验收包可测试完整主篇/新事实延伸。Android无下载器、导入器、音频包、兴趣或探索地点界面。测试夹具不构成内容批准。
