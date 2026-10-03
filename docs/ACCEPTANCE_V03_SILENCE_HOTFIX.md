# V0.3 total-silence hotfix acceptance

Scope: `HOTFIX_V03_TOTAL_SILENCE.md`, based on upstream main `74bdf85`, preserving the local Google ASR reprobe documentation.
Test session: 2026-10-03 evening through 2026-10-04 (Asia/Shanghai).
Implementation milestone: `3397221` — `fix: restore V0.3 narration while research is pending`.

## Regression evidence

Before changing production code, `TotalSilenceHotfixTest` reproduced:

```text
pending initial request calls=0 research=true voice=0
failure retained opportunity=false status=FAILED
```

The pre-fix test results/logs are in ignored local artifacts `hotfix-before-results.xml` and `hotfix-before-tests.log`.
The old production simulation pause lambda also explicitly included `loop.research.pending`; this made background research a movement gate.
The original simulation test stand-in omitted that condition, which explains why it did not expose the production freeze.
The new regression uses the same `DirectorLoop.simulationPaused` policy as Android runtime.

The connected vivo V2405A running **0.3.0 / code 5**, ChatGPT narration, independent OpenAI research unchecked,
completed a 100km/h / 16× simulation with **17 dispatches, 17 FAILURE, zero speech/ASK_USER**.
This also establishes a separate live narration failure, not proof that research caused every failure.
UI evidence: `artifacts/hotfix-phone-before-ui.txt`.
OEM application log output was unavailable (`persist.sys.log.ctrl=no`; changing it via shell was denied).
No system log control, input method, recognition service, credentials, or billing provider was changed.

## Implementation

- Background research no longer gates automatic checks or developer simulation movement.
- Initial interaction and independent grounded landmarks remain available while research is running.
- A pending chapter stays unexamined until READY or FAILED is actually inspected. FAILED retains honest status and one safe check.
- READY district evidence can be inspected while town research is still running; town READY gets its own opportunity later.
- Quiet, cooldown, speech, listening/typing, image interaction and fresh-location checks still delay opportunities.
- Only current-chapter evidence is presented. Leaving before READY caches evidence but never creates old-scene narration.
- Failed evidence remains absent. No completed search/provenance means no Local Dossier, no invented local facts or search success.
- Logs record start/ready/failure elapsed time, retained/consumed opportunity and Director delay reason. Hosted-search rejection fields remain separate from narration.
- Debug builds have a bounded 128KB cache fallback (`run-as com.kitt.reader cat cache/hotfix-diagnostics.log`) for OEM log suppression.
  It contains local technical diagnostics and short topic metadata, no credentials, transcripts, audio or raw track; reset on runtime creation, absent in release.
- An actual transient ChatGPT failure starts a fixed 60s backoff. Locally rejected checks no longer overwrite its diagnosis or extend the deadline indefinitely.
- The ChatGPT transport uses OkHttp native cancellation and total call limits (35s narration/auth, 90s search), with no automatic retries or redirects.
  The old Android HTTP implementation did not return from timed-out phone probes; adding a separate disconnect coroutine still did not make them finish.
  Those unbounded probes were stopped without accepting their results. A real TCP socket withholding response headers now has an automatic deadline regression test,
  in addition to the late-response cancellation test. Authorization, request payloads and completed-search provenance validation are unchanged.
  Native socket close is dispatched independently on IO: an OEM blocking close cannot retain the cancelled coroutine or its auth/research slot.
  The cancellation regression explicitly blocks the close operation and proves prompt coroutine release and rejection of a late response.

## Phone observations before the final independent probe

The first hotfix run dispatched the Director immediately with `research_pending=true`, then failed at OAuth token refresh with `UnknownHostException`.
This is a network failure before search admission, not proof of hosted-search rejection.
The user reported restarting the proxy; no proxy/account configuration was changed by the agent.

The subsequent real ChatGPT run returned **ASK_USER** in about **11.9s** while chapter research was still pending;
Android TTS reported **success=true**, and the one-shot listening window opened and closed without an answer.
Later `research_pending=true` / `paused=false` progress increased from **1784m** to subsequent chapter positions.
This establishes that research no longer globally mutes interaction or freezes driving.
It does not establish completed local search: the research streams later failed with `SocketException` and no READY evidence.
Logs: `artifacts/hotfix-phone-dns-failure.log`, `hotfix-phone-proxy-retest.log`.

The existing typed reply UI submitted a stable general-knowledge question into the same Director (`activeDispatched=1`);
the old sliding-backoff build rejected it and played the original brief failure response. That live observation corroborated the backoff regression,
which has its own failing-before / passing-after automatic test. ASR code and backend selection were not changed.

## Gates and phone results

Final `scripts/verify.ps1 -Offline`: **PASS**, Debug **232** / Release **232** tests, zero failures/skips;
both assemble and lint, plus Debug APK v2 signature passed. Log: `artifacts/hotfix-final-gates.log`.
The final run was sequential with no filtered-test run overwriting its reports.
Deterministic Chengdu→Mianyang regression passed; dossier route: 29 points, 13 chapters, 19 synthetic research calls,
13 narrations, 33 checks, zero stale/cancel/failure. These synthetic results do not establish hosted search or Xindu discovery.

Final APK: **0.3.1 / code 6**, 76,215,833 bytes,
SHA256 `7BD51B527748DEF21A87E12198ED95CEBD73209B5A4CCE56C4F971D52DCA2AA2`.
Installed vivo APK SHA256 matches the exported acceptance package. The user unlocked the phone; the final update succeeded.
The test APK was removed, the simulation ended, and temporary per-tag log properties were restored to empty.

Final normal UI run, existing ChatGPT account / `gpt-5.6-luna` / default effort, independent OpenAI research unchecked:

```text
1791045346317 dispatch active=false
1791045346319 director started active=false research_pending=true
1791045352804 terminal active=false outcome=ASK_USER
1791045354805 Voice completed success=true active=false ask=true
1791045370418 progress meters=4016 paused=false research_pending=true
1791045380464 progress meters=8480 paused=false research_pending=true
1791045415490 dispatch active=true activeDispatched=1
1791045420016 active research decision=GENERAL_KNOWLEDGE
1791045427922 terminal active=true outcome=SPEAK_NOW topic=Why rivers bend
1791045436324 HTTP cancellation requested
1791045436327 HTTP native cancellation completed
1791045436329 research failed ... 新都街道 elapsed_ms=90012 reason=timeout opportunity_retained=false
1791045473257 Voice completed success=true active=true ask=false
1791045530128 progress meters=28572 paused=false research_pending=true
```

The final opening ASK_USER took **6.5s**, TTS succeeded and one-shot listening opened.
The typed general question took **12.4s** through the unchanged active-question path and finished TTS successfully.
The vehicle had already left Xindu when its 90s timeout arrived; no late Xindu narration occurred.
Both the timeout and trip-end cancellation released native calls promptly; the waiting research slots began the next queued requests.
Runtime log: `artifacts/hotfix-phone-final-runtime.log`. Requested logcat capture: `hotfix-phone-final-logcat.log` (OEM suppression persists).

**Separate ChatGPT hosted-search result: NOT SUCCESSFUL; NO EXPLICIT SERVICE REJECTION OBSERVED.**
A fresh, direct production research request for 四川省 / 成都市 / 新都区 / 新都街道 used the saved account, no alternate API or seeded object names.
Model catalog and `/responses` returned **HTTP 200**, then the stream failed with **SocketException** after **11,465ms**.
No validated Local Dossier, completed search provenance, sources or READY result was obtained.
No `ChatGptFailure` rejection status/code/request_id/shape was returned; HTTP 200 alone does not establish that a hosted search tool completed.
Background trip research also failed validation or timed out, with no READY evidence. These are network/validation failures, not proof of provider search support or refusal.
Files: `artifacts/hotfix-phone-hosted-search-final.log`, `hotfix-phone-hosted-search-final-diagnostics.log`.

Reproduce the independent credential-backed probe without exposing credentials:

```powershell
.\gradlew.bat assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode search com.kitt.reader.test/com.kitt.reader.HotfixPhoneProbe
adb shell run-as com.kitt.reader cat cache/hotfix-diagnostics.log
adb uninstall com.kitt.reader.test
```

Keep the target app visible during the probe on this OEM. Do not run UIAutomator simultaneously with instrumentation.
Remaining limitation: a stable completed hosted-search stream is still needed for true local content / Xindu black-box acceptance.
There is currently no demonstrated requirement to replace the account or purchase another search API.
