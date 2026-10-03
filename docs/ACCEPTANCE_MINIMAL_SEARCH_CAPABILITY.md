# Minimal ChatGPT OAuth web_search capability — 2026-10-04

## Conclusion A: minimal web_search successfully completed

**The saved ChatGPT OAuth account + `gpt-5.6-luna` can execute and complete hosted web search.**
The vivo phone returned HTTP200, an actual `web_search_call` item, its in_progress/searching/
completed lifecycle events, real source provenance, output text, and `response.completed`.
Total request duration was **6699ms**. This supersedes the earlier capability-undetermined
conclusion; it does not establish the cause of the full Local Dossier request's 85s SSE timeout.

No production chain, Provider, Local Dossier contract, Journey, Director, ASR or TTS change.
No non-streaming request, API key, separate OpenAI Provider, seeded landmark, account change,
new login, token export or automatic network retry was used in this batch.

## Actual phone request

- vivo V2405A (`10AF352AFP003DH`), existing saved valid OAuth access token, default effort.
- Same `https://api.openai.com/v1/responses` OAuth direct route; no model-catalog request or token refresh.
- `model=gpt-5.6-luna`, `stream=true`, `store=false`.
- `tools=[{"type":"web_search"}]`, `tool_choice=required`.
- `include=["web_search_call.action.sources"]`; no `text.format`, no strict schema or Local Dossier.
- Minimal instruction: `请实际联网搜索，简短回答并保留来源。`
- Exact user input:

```text
请联网搜索四川省成都市新都区，找出一个具体、值得了解的历史文化地点。只用一句简短中文回答，并保留搜索来源。
```

The four benchmark names do not appear in this payload. Answer content/quality and landmark
discovery are deliberately not retained or evaluated; this is a tool-capability test only.

## Complete observed timeline

Times are milliseconds from request start. **Every actually dispatched SSE event is listed**,
including repeated deltas. JSON `type` and SSE `event:` name agreed for every observed event.
The lifecycle types below came from the phone response, not expected/generated fixture labels.

| elapsed_ms | Actual event / transport observation | Safe metadata |
|---:|---|---|
| 2268 | First response-header byte | HTTP200; no x-request-id supplied |
| 2269 | HTTP headers received | HTTP200 |
| 2286 | First response-body byte | |
| 2290 | `response.created` | |
| 2350 | `response.in_progress` | |
| 2802 | `response.output_item.added` | item type `web_search_call`, status `in_progress` |
| 2832 | `response.web_search_call.in_progress` | tool lifecycle observed |
| 4011 | `response.web_search_call.searching` | |
| 5995 | `response.web_search_call.completed` | search completed |
| 6026 | `response.output_item.done` | `web_search_call`, `completed`; 21 distinct provenance URLs |
| 6078 | `response.output_item.added` | `message`, `in_progress` |
| 6104 | `response.content_part.added` | |
| 6217 | `response.output_text.delta` | output text began; content not recorded |
| 6233 | `response.output_text.delta` | |
| 6258 | `response.output_text.delta` | |
| 6285 | `response.output_text.delta` | |
| 6302 | `response.output_text.delta` | |
| 6444 | `response.output_text.delta` | |
| 6450 | `response.output_text.annotation.added` | source union increased to 22 distinct URLs |
| 6639 | `response.output_text.done` | |
| 6665 | `response.content_part.done` | |
| 6689 | `response.output_item.done` | `message`, `completed` |
| 6697 | **`response.completed`** | complete response received |
| 6698 | Reader terminal | `response.completed`, phase `body read` |
| 6699 | Probe returned | classification **A** |

Observed search completion: **yes**. Sources: **22** distinct syntactically valid HTTP(S) URLs,
counted from actual tool sources and citation annotations; URLs/content themselves were not logged.
Output text began: **yes**. Response completed: **yes**. Unparseable events: **0**.
Socket EOF, timeout, exception: **none before completion**. Reader returned at the terminal
`response.completed` and closed the response; it did not wait for socket EOF or a `[DONE]` marker.

## Diagnostic boundary and regressions

The new `SearchCapabilityProbe` and event reader exist only in `src/debug`; the only entry is
the explicitly invoked instrumentation mode `search-capability`. No production caller exists.
The probe's native Call/read limits and independent coroutine total deadline are **180 seconds**.
It does not use `ChatGptStream.read()` and therefore has no inherited 85s SSE completion deadline.
Production research remains at its original 90s boundary. The 180s diagnostic run completed at
6.699s; no 85–180s inference was necessary and no production timeout was increased.

SSE line/frame/total size, event count and provenance count are bounded. HTTPS, no redirects,
no automatic retry and off-caller native cancellation are retained. Each actual event appends
bounded metadata to a Debug cache JSONL timeline; interruption/timeout preserves prior events.
The report records the last SSE type/time and terminal phase. Finalizing freezes the event
collector so late callbacks cannot alter evidence. No body, query results, item IDs, URL values,
access/refresh tokens or auth headers are persisted by the probe.

New regressions cover all-event recording (including added/searching/unknown types), source and
completion evidence, completed tool with no final response (B), no tool (C), explicit rejection
(D), `[DONE]` not substituting for completion, citation/named-event shapes, bounds, metadata
redaction, late-event freezing, and total deadline/native cancellation.

Targeted probe/socket regressions: **8 tests PASS**. Final `scripts/verify.ps1 -Offline`:
**Debug245 / Release232 tests, zero failures/skips; both assemble/lint variants and Debug APK
v2 signature PASS**. Phone APK and delivered APK SHA256 match; the temporary test package was
uninstalled. Logs are in `artifacts/search-capability-final-gates.log` and root `HANDOFF.md`.

## Reproduce only this SSE capability probe

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode search-capability com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
adb shell run-as com.kitt.reader cat cache/search-capability-timeline.jsonl
adb shell run-as com.kitt.reader cat cache/search-capability-report.json
adb uninstall com.kitt.reader.test
```

Use matching app/test APKs and a valid saved access token. The runner launches the target Activity
inside instrumentation to keep it visible on this OEM. Do not run UIAutomator concurrently.
If a later saved token is expired, use the existing account refresh/settings flow first; the
probe does not change OAuth or retrieve a new account.

Local evidence (git-ignored, metadata only): `artifacts/search-capability-phone.log`,
`search-capability-report.json`, `search-capability-timeline.jsonl`, `search-capability-regression.log`.
The table above is the checked-in complete event sequence. Full Local Dossier optimization is
subsequent work; this batch stops after proving the capability and preserving its evidence.
