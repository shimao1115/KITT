# ChatGPT OAuth hosted research transport probe — 2026-10-04

> Capability conclusion superseded by [the minimal SSE capability probe](ACCEPTANCE_MINIMAL_SEARCH_CAPABILITY.md):
> same saved OAuth account/model completed search with sources and response.completed in 6699ms (A).
> The non-streaming parameter rejection and full-dossier timeout below remain accurate historical observations.

## Scope and fixed inputs

Based on upstream `e01f301` / accepted V0.3.1. No production Provider, Journey, Director,
Local Dossier schema/parser, TTS, ASR or narration streaming changes. The probe is Debug-only;
the Release classes contain no `ResearchTransportProbe`.

Device: vivo V2405A, serial `10AF352AFP003DH`. Saved Provider: ChatGPT,
model `gpt-5.6-luna`, default effort; independent OpenAI research unchecked.
The final probe directly reuses the phone's still-valid saved OAuth access token in memory:
no credential export, refresh, model-catalog request, API key, alternate account or Provider.

Request area: **四川省 / 成都市 / 新都区**, with no street/chapter restriction.
Uses `LocalResearchContract.payload(...)`, the same `web_search`, `tool_choice=required`,
`include=[web_search_call.action.sources]`, instructions and strict `local_dossier` schema.
The JSON probe changes only the payload's `stream` boolean; Accept is `application/json`.
The four benchmark names are only result checks in Debug code, never prompt/query seeds.

Each invocation captures one saved token and one payload/research timestamp. A successful
JSON result would gate a two-sample-per-arm A/B using that captured token/payload. JSON was
rejected, so this gate was not met and no success-rate/dossier-consistency A/B was performed.
The separate SSE control uses the same saved account/model/area and production contract;
separate runs have separate research timestamps and are not claimed as a controlled A/B.

## Non-streaming phone result

Final foreground, credential-backed JSON probe:

| Field | Observed result |
|---|---|
| HTTP status | **400** |
| Header first byte | 2113 ms |
| Body first byte | 2114 ms |
| Total request elapsed | **2116 ms** |
| Transport outcome | Complete HTTP error body, represented as `ChatGptFailure` |
| Response Content-Type | `application/json` |
| Error code | Not supplied; empty string |
| `x-request-id` | Not supplied; empty string |
| Response shape | `{"detail":"string"}` |
| Exact returned detail | **`Stream must be set to true`** |
| Completed `web_search_call` | No observed completion |
| Provenance URLs / accepted facts | 0 / 0 |
| Local Dossier READY | No |

Earlier JSON runs independently returned the same HTTP400 detail at 1123ms and 1367ms.
The first run at 1125ms retained only the generic `detail` shape; that missing diagnostic
was repaired before interpreting the response. These were explicit probe invocations,
not automatic transport retries.

This is an explicit rejection of **`stream=false`**, not an `unsupported_tool`, hosted-search
permission or account-capability rejection. The exact response supplies no tool rejection code.
It does not prove that OAuth hosted search is available, nor that it is unsupported.
There is no evidence supporting a switch of chapter research to non-streaming on this route.

## SSE control and final classification

**Conclusion 3: the streaming completion/transport problem remains unresolved; hosted-search
capability cannot currently be determined.** No tool-capability rejection was observed.
The non-streaming request was expressly rejected for its streaming parameter; this does not
satisfy the evidence needed to conclude that the service rejected `web_search`.

Final foreground SSE control:

| Field | Observed result |
|---|---|
| HTTP status | **200** |
| Header first byte | 2065 ms |
| Body first byte | 2102 ms |
| Total request elapsed | **87089 ms** |
| Failure stage | **body read**; DNS/connect/TLS/headers had succeeded |
| Terminal exception | `IllegalArgumentException` |
| Safe failure reason | **`sse_completion_timeout`** |
| Local failure site | **`ChatGptStream.read`, `ChatGptTransport.kt:144`** |
| `x-request-id` / response Content-Type | Neither supplied |
| Tool error code / permission refusal | None observed |
| Completed `web_search_call` | No observed completion in a complete response |
| Provenance URLs / accepted facts | 0 observed / 0 |
| Local Dossier READY | No |

This precisely identifies the **85-second SSE completion deadline**, entered after headers,
as this repeat's terminal failure; it is not Local Dossier JSON/schema validation.
The earlier V0.3.1 `SocketException` was not reproduced in this repeat, and its original root
cause is not established. The first properly foregrounded bounded control also ended at
87668ms in `body read` with `IllegalArgumentException`, before the safe reason/site fields
were added. The final repeat supplies the explicit reason rather than inferring it from elapsed time.

Zero observed evidence is not a claim that no server-side tool ever executed: no complete
Response envelope was available for verification. Do not infer search success from HTTP200,
nor unsupported search from a dropped/stalled stream. The specific network/proxy/OEM versus
upstream incomplete-stream cause is still undetermined. No result supports case 1, 2 (tool
rejection), or 4 (completed search with a failing Local Dossier parser).

Black-box checks **杨升庵（杨慎）、桂湖、宝光寺、新繁东湖** are all **not assessable**:
no valid dossier exists. They are not recorded as confirmed absent or as discovered.
No chapter research default, active chat, Provider or ordinary Director streaming was changed.

Preliminary runs that were stopped without a terminal record are **excluded** from capability,
HTTP, timing and success-rate evidence. Their exact request phase was unrecorded. One stale
test-APK/Debug-APK constructor mismatch caused `NoSuchMethodError`; matching packages fixed
the diagnostic deployment. That setup failure is not a hosted-search result.

## Transport and regression guarantees

- HTTPS only; no HTTP or SSL redirects; no automatic connection retry.
- JSON uses its own byte-bounded reader (1,000,000-byte successful body, 32,000-byte error body),
  not `ChatGptStream.read()`. It reads the complete JSON before evidence/schema inspection.
- 90-second total coroutine deadline plus native Call deadline; cancellation releases the
  caller promptly and dispatches native cancellation off the caller, even when close blocks.
- DNS/connect/TLS/headers/body-read phase, header/body first-byte timing, HTTP status and
  request ID are recorded independently of Local Dossier parsing.
- Search completion and provenance are inspected before strict parsing. Response snapshots
  retain structure, types and counts only; no response text, credentials or auth headers.
- Arbitrary exception messages are not logged. Error detail is bounded/redacted; fixed failure
  labels identify known SSE completion deadlines, byte limits, IO and framing failures.
- Full verification: Debug **239**, Release **232** unit tests; no failures/skips;
  both assemble/lint variants and Debug APK v2 signature PASS. Final diagnostic-label changes
  also pass **66 affected Research/ChatGPT regressions**, Debug assemble and lint.
- Existing Chengdu→Mianyang / Xindu→Jushui deterministic simulation passes unchanged.
  It is not live hosted-search or black-box discovery evidence.

## Minimal reproduction

Keep the phone unlocked. Build and install **matching** app/test APKs; the runner launches
the target Activity within instrumentation so it stays visible on this OEM.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode research-transport com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
adb shell am instrument -w -e mode research-transport-sse com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
adb shell run-as com.kitt.reader cat cache/research-transport-probe.json
adb shell run-as com.kitt.reader cat cache/research-transport-sse-probe.json
adb uninstall com.kitt.reader.test
```

For a nondefault effort, model metadata must already have been loaded in the phone session;
the probe refuses to guess support or silently change effort. If the saved access token has
expired, refresh through the existing account/settings path first; the probe never refreshes it.
Do not run UIAutomator while instrumentation is active.

Local raw terminal evidence: `artifacts/research-phone-json-final.log`,
`research-phone-sse-final.log`; structural reports are in the corresponding phone cache files.
Gate logs: `research-probe-final-gates.log`, `research-probe-reason-regression.log`,
`research-probe-entry-lint.log`. Artifacts remain git-ignored; no credentials were exported.
