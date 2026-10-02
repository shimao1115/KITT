# Provider spike (2026-10-03)

Local Codex auth metadata reports ChatGPT sign-in, no Platform API key. No relevant API-key environment variable or key file was found. Credential values were not printed, transferred or embedded.

Official [Codex authentication](https://learn.chatgpt.com/docs/auth) distinguishes Codex subscription sign-in from general API keys. New [ChatGPT plan usage](https://developers.openai.com/siwc/token-sharing-open-source) requires a separate client registration, host ID and user consent. [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) require streamed Responses and app-managed OAuth renewal. The existing desktop login is not a KITT app authorization. Implementing a new OAuth client and renewal/streaming stack is disproportionate for this V0 and would still require sleeping-user consent. Use the allowed ordinary API fallback.

Implemented: Fake Provider (default), OpenAI Responses with strict JSON Schema, and a thin compatible Chat Completions adapter using JSON mode plus identical strict local validation. Ordinary API keys are supplied in on-device Settings; never compiled into an APK. Unknown-model reasoning is omitted. No native search is enabled: the constitution explicitly limits unsupported claims to stable general mechanisms and declines unverified local specifics.

[Structured output documentation](https://developers.openai.com/api/docs/guides/structured-outputs) informed the schema request. Contract/transport tests use fakes; a real provider call is blocked only on an API key. Fake content is scripted demo content, not evidence of real AI narration quality.
