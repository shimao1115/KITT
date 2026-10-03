# Provider spike and M1.1 (2026-10-03)

The D1 spike used Fake and ordinary API-key providers while separate KITT authorization was unavailable. M1.1 now implements KITT's own Sign in with ChatGPT flow. Desktop Codex/ChatGPT credential files were not inspected, copied or reused during M1.1.

Official [ChatGPT plan usage](https://developers.openai.com/siwc/token-sharing-open-source) requires a separate client registration, host ID and user consent. [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) require streamed Responses and app-managed OAuth renewal. Those are implemented in the focused adapter described in [M1.1](CHATGPT_SIWC.md); physical-phone/live authorization results are in [HANDOFF](../HANDOFF.md).

Implemented: Fake Provider (default), OpenAI Responses with strict JSON Schema, and a thin compatible Chat Completions adapter using JSON mode plus identical strict local validation. Ordinary API keys are supplied in on-device Settings; never compiled into an APK. Unknown-model reasoning is omitted. No native search is enabled: the constitution explicitly limits unsupported claims to stable general mechanisms and declines unverified local specifics.

[Structured output documentation](https://developers.openai.com/api/docs/guides/structured-outputs) informed the schema request. Contract/transport tests use fakes; a real provider call is blocked only on an API key. Fake content is scripted demo content, not evidence of real AI narration quality.
