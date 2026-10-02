# M1.1 — Sign in with ChatGPT / ChatGPT plan usage

## Goal

Add **Sign in with ChatGPT** as the preferred KITT AI Provider path, so an eligible user can authorize KITT to use AI requests from their ChatGPT plan without copying or entering an OpenAI Platform API key.

This is a focused provider milestone. Do **not** redesign KITT, the Director contract, Journey, Context, Voice, or UI outside the minimum Settings/auth surfaces needed for this integration.

Existing providers must remain available:
- Fake / offline demo
- OpenAI Platform API key
- Compatible API

The new provider should appear as the preferred option:
- **ChatGPT 账号 / Continue with ChatGPT**

## Source of truth

Use the **current official OpenAI Sign in with ChatGPT docs**, not guessed OAuth behavior or desktop Codex token reuse:

- Overview: https://developers.openai.com/siwc/token-sharing-open-source
- Registration and sign-in: https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- Accounts and sessions: https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- Models and inference: https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- Token reference: https://developers.openai.com/siwc/token-sharing-open-source/token-reference
- Errors and recovery: https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
- Preview limitations: https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations

Do not reuse, copy, import, or inspect desktop Codex/ChatGPT access or refresh tokens. KITT must obtain its own user authorization.

## Required OAuth flow

Follow the official open-source dynamic registration flow.

### First sign-in
- Use `client_id=dynamic_agent_client`.
- Persist a stable, opaque `ext_agent_host_id` for this KITT installation/host.
- Use KITT's actual app identity consistently as `agent_name_hint`.
- Generate fresh per-attempt:
  - `state`
  - OIDC `nonce`
  - PKCE verifier + S256 challenge
- Request scopes:
  - `openid profile email`
  - `offline_access`
  - `resource.invoke`
  - `chatgpt.tokens.use.direct`
- Set `resource=https://api.openai.com/v1`.
- Open authorization in the **system browser**.

### Callback
Official OSS plan-usage docs require an HTTP loopback callback on `127.0.0.1` using the exact `/auth/callback` path.

For Android, verify that KITT can host the required loopback listener reliably on-device. Do not silently substitute a custom URL scheme/deep link unless current official docs explicitly allow it.

- Start listener before browser launch.
- Use an available local port.
- Validate returned `state`.
- For initial registration, retain the issued `client_id` returned by the callback.
- Never save `dynamic_agent_client` as the issued client ID.
- Exchange authorization code against:
  `https://auth.openai.com/api/accounts/oauth/token`
- No client secret should be introduced.

If the official loopback requirement proves technically incompatible with this Android runtime, document the exact evidence in `HANDOFF.md` and stop **only this provider milestone**; do not invent a non-compliant auth flow.

## Validation and credential storage

After exchange:
- Validate the ID token against OpenAI JWKS.
- Validate issuer, audience == issued client ID, expiration, and the saved nonce.
- Use validated `sub` as account identity.
- Verify granted scopes contain `chatgpt.tokens.use.direct` before enabling plan-backed inference.
- A successful identity-only sign-in must not be treated as plan-usage authorization.

Store, protected by the existing Android Keystore-backed credential layer:
- stable `ext_agent_host_id`
- issued `client_id`
- validated identity needed for account display
- access token
- refresh token
- retained ID token for future `id_token_hint`
- granted scopes
- expiry / earliest refresh information

Never log tokens or authorization URLs that contain token hints.

Access token lifetime is short; implement refresh-token renewal per current official docs. Refresh-token rotation must replace the stored refresh token after successful refresh.

For V0 UI, one active ChatGPT account is sufficient. Do not build an elaborate user/account system. However, do not key identity solely by email, and keep the storage shape capable of distinguishing issued client registrations.

## Model picker

Once authorized for ChatGPT plan usage:

- Fetch the selected account's model catalog from:
  `GET https://api.openai.com/v1/models`
  using the same OAuth access token.
- Display only entries intended for listing (official response field `visibility == "list"`).
- Show `display_name`, send selected `slug` as `model`.
- Refresh the model list after account/re-auth changes.
- Do not hard-code one model as the only ChatGPT-plan model.
- Preserve a sensible selected/default model if still present.

Reasoning effort controls should only be shown/sent when the selected model/provider path actually supports them. Do not guess unsupported levels.

## Responses inference contract

Use the public endpoint:
`POST https://api.openai.com/v1/responses`

Authenticate with:
`Authorization: Bearer <OAuth access token>`

For ChatGPT plan usage over HTTP:
- `store: false`
- `stream: true`
- send required context in an `input` array
- use supported instructions/developer-message mechanisms
- do not rely on `previous_response_id`
- omit fields listed as unsupported in current preview docs

Consume the stream through its terminal event.
Treat inference as success **only** after `response.completed`.

Handle:
- `response.failed`
- `response.incomplete`
- interrupted stream
- usage-limit errors
- revoked/expired auth

Preserve the existing KITT structured Director response and strict local validation. Provider-specific transport must not leak into Journey/Context/UI.

## Settings UX

Preferred layout:

### ChatGPT
- **Continue with ChatGPT** when disconnected
- when connected, show:
  - account identity label
  - ChatGPT-plan usage: enabled / not enabled
  - model picker populated from the account catalog
  - reasoning effort when supported
  - Disconnect / reconnect action

### OpenAI API
Keep existing Platform API-key provider unchanged.

### Compatible API
Keep existing provider unchanged.

### Offline demo
Keep existing provider unchanged.

Do not force ChatGPT sign-in to use the app; fallback providers remain valid.

## Error behavior

- User declines ChatGPT-plan permission:
  - keep identity sign-in if valid
  - clearly mark plan usage disabled
  - allow reauthorization or another Provider
- 401/403/admission errors:
  - surface a concise actionable auth/provider message in Settings
  - do not silently fall back to paid API-key billing
- usage limit reached:
  - explain that ChatGPT plan usage is unavailable/limited
  - do not loop retries
  - allow user to switch Provider manually
- automatic Director call failures during driving:
  - preserve existing KITT behavior: silent degrade
- active user request failure:
  - preserve existing short user-facing failure message

## Tests

Add focused tests without weakening the existing 34-test suite:

- PKCE generation/state/nonce flow
- initial dynamic registration vs returning issued client ID
- callback state validation
- missing/mismatched issued client ID handling
- scope check for `chatgpt.tokens.use.direct`
- token refresh + refresh-token rotation
- model-list parsing / visibility filtering
- streamed Responses success only on `response.completed`
- failed/incomplete/interrupted stream handling
- no silent fallback to API-key billing
- token values never written into logs / plaintext preferences
- existing Fake/OpenAI-key/compatible Providers still work
- existing Director contract tests remain green

## Acceptance gate

M1.1 is complete when:

1. App builds, lint passes, all old + new tests pass.
2. On a physical Android phone, Settings exposes **Continue with ChatGPT**.
3. Tapping it opens the official OpenAI/ChatGPT system-browser authorization flow.
4. User can explicitly grant ChatGPT-plan usage.
5. KITT returns from auth, validates identity/scopes, shows connected state.
6. KITT loads the account-specific model list.
7. A real KITT Director request completes using the OAuth bearer token, with no Platform API key configured.
8. Disconnect + reconnect works without copying desktop tokens.
9. Existing API-key and Compatible API Providers remain intact.
10. Update `HANDOFF.md` with the exact phone acceptance result and any preview limitation.

## Non-goals

Do not add in this milestone:
- web search integration unless it is required to prove the basic plan-backed inference path
- multi-account UI
- backend server
- app account system
- new navigation/map behavior
- cloud sync
- new KITT product features
- model-routing logic

The point is one thing only:

> **KITT can legitimately sign in with the user's ChatGPT account and use authorized ChatGPT-plan inference as another swappable AI Provider.**
