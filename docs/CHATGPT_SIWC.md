# M1.1 implementation and acceptance

Checked against official OpenAI documentation and production OIDC discovery on **2026-10-03 (Asia/Shanghai)**.

KITT uses its own public-client dynamic registration. No desktop Codex/ChatGPT credential files are read. The existing Director, Journey, Context, Voice and driving UI are unchanged; the new path is selected only through Settings and the provider factory. Fake remains the credential-free default, with ChatGPT listed first as the preferred real-AI path. No automatic switch to API-key billing occurs.

## Authorization and storage

- Before the system browser opens, a short-lived Java `ServerSocket` binds exclusively to `127.0.0.1` on an available port. The callback path is `/auth/callback`, and the exact URI is reused during exchange. No deep-link substitution or WebView.
- A UUIDv4 host URI persists across reconnects. First registration uses `dynamic_agent_client` and the actual app name `路上读山河`; later attempts reuse the issued ID. Each attempt has fresh state, nonce and S256 PKCE. Duplicate query parameters, mismatched state/client ID, missing issued ID, errors and reused callbacks fail closed.
- Save a newly issued registration before exchange so an expired code does not require another registration. Validate the ID token using the fixed OpenAI JWKS URI and JCA RS256 signature verification (the only signing algorithm currently advertised by production discovery), then issuer, audience/authorized party, expiry, nonce and subject. A returning identity must match the selected subject and registration.
- One active registration is sufficient for V0. The encrypted record contains host/client mapping, issuer/subject/email, tokens, granted scopes, expiry and earliest refresh. Email is only a display label. The entire record uses the existing Android Keystore AES-GCM layer and an atomic private preference commit; backup/transfer remain disabled. Token-bearing types have redacted diagnostic representations. Authorization URLs and token hints are never logged.
- Identity-only sign-in stays connected, with plan usage disabled. Reauthorization requests all scopes and uses `prompt=consent` only when the user explicitly chooses to enable plan usage; deployment of `force_reconsent` is not assumed.
- Serialize refresh and account operations using one mutex. Refresh near expiry, respecting `earliest_refresh_at`, and atomically replace access/rotating refresh tokens, scopes and expiry. Terminal refresh errors clear tokens while retaining the host and verified registration. Transient failures retain credentials.
- Disconnect attempts the discovered revocation endpoint, clears local tokens/hints, and retains registration. If revocation is unconfirmed, Settings directs the user to disconnect KITT in ChatGPT Settings.

## Models and inference

The account's bearer token fetches `/v1/models`; preserve ordering, filter `visibility == "list"`, display `display_name`, and send `slug`. Preserve a selection still present, otherwise select the first listed model. Refresh after successful authorization and on request. Never infer reasoning capability from model names: only explicit `supported_reasoning_levels[].effort` metadata enables controls; if absent, omit reasoning.

Plan requests go only to `https://api.openai.com/v1/responses`, with `store: false`, `stream: true`, instructions and a self-contained input array. The existing strict Director JSON schema and local action validation remain. Unsupported preview fields and previous-response references are omitted. TTS receives no stream deltas: only a validated response after `response.completed` can leave the adapter. Failed/incomplete/interrupted streams never become successful Director results.

Settings offers account/permission status, reconnect/disconnect, account models, optional advertised effort, usage-settings link and a **测试 Director 连接** action. The latter makes a real structured Director request without starting a journey or invoking voice. Auth/admission/usage errors leave an actionable Settings message. Driving auto failures still become silence, and active failures retain the existing short response. No retries or billing fallback are hidden in the adapter; terminal admission/usage errors pause requests until an explicit Settings action. Temporary failures have a bounded cooldown. The `KITTAuth` tag records fixed non-secret lifecycle stages and HTTP status, safe error code, request ID and response shape, never response messages, authorization URLs, token hints or credentials.

## Lifecycle and preview boundary

The first phone attempt exposed a real background reliability failure: probing the bound listener while Firefox was foreground timed out; returning to KITT allowed callback delivery. The device's activity record reported freezing while KITT was cached. To protect this user-initiated task, login now runs with a dedicated bounded `shortService` foreground notification, using the existing foreground-service permission. There is no additional permission, server backend or persistent login service. The attempt expires after **150 seconds**, before Android's short-service deadline; success/failure/cancel closes the listener and stops the notification. Both Android timeout callbacks also cancel and stop. See [Android short-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#short-service).

The attempt belongs to the application runtime and survives Activity recreation. If the process is killed anyway, or login takes longer than the bound, start a fresh attempt; pending verifier/code values are not restored. Existing issued registration and validated credentials persist securely. Journey service behavior and its quiet/end notification actions are unchanged.

Physical-phone acceptance evidence and the remaining user-only authorization step are recorded in [HANDOFF](../HANDOFF.md). Local tests cover actual HTTP loopback exchange, full signed-token sign-in and returning-identity mismatch, encrypted storage, refresh rotation/concurrency, token expiry/earliest refresh, revocation failure, model filtering, stream terminal semantics, usage/admission failures and provider isolation, alongside all existing D0–D11 tests. Mock tokens prove protocol handling, not live account eligibility or plan availability.

## Official sources

- [Registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)
- [Errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
- [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Production discovery](https://auth.openai.com/.well-known/openid-configuration)
