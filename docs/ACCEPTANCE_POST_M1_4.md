# Post-M1.4 batch — non-phone evidence

Date: 2026-10-03 (Asia/Shanghai). Chinese ASR is out of scope.

> **Evidence record.** This file documents what was built and measured in the post-M1.4 batch. Its editorial wording is
> superseded by `docs/TASK_EDITORIAL_FREEDOM.md`: candidate titles are now neutral subject labels on an open local
> material shelf, entering a town/township/street chapter grants its own Director opportunity, families no longer
> demote candidates, and Sanxingdui is available-but-not-forced. Test counts and gate results below remain the record
> of that batch; the current suite is reported in `HANDOFF.md`.

## Phase A — area chapters

Implemented platform reverse geocoding on IO, immediate GPS delivery, one lookup at a time,
60-second minimum attempt interval, 1km movement / five-minute refresh, session-only chapter
cards, topic-family history, conservative local narrative policy and both-orientation place display.
The coarse fixture includes Guanghan; its candidate pool notices Sanxingdui without a narration script.
Candidate ranking is advisory: boundary crossing never forces speech.

`assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug lintRelease
--offline --console=plain`: PASS, 96 tests per variant (89 preserved + seven area regressions),
zero failures/errors/skips, lint zero errors. Local log: `artifacts/phase-a-gates.log`.

Real GPS platform quality, real ChatGPT narrative variety and account web-search verification probe:
**DEFERRED TO COMBINED PHONE ACCEPTANCE**. No phone credentials were read or reused.
Web search is not enabled for narration; the first version uses conservative stable associations.
Sources checked: [OpenAI preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations),
[Android Address](https://developer.android.com/reference/android/location/Address),
[三星堆博物馆](https://www.sxd.cn/).

User refinement during this batch: final fixture must start in 成都市新都区 and end in
绵阳市安州区雎水镇, with more detailed chapters. Final route evidence supersedes this intermediate fixture.

## Phase B — route-reference image

Android single-image photo picker (older devices use its document-picker fallback); no storage permission.
Images are source-bounded, downsampled, orientation-corrected and JPEG re-encoded without EXIF metadata.
One selected-provider request uses native Responses `input_image` or compatible Chat Completions
`image_url` parts. ChatGPT uses the existing account adapter, completion gate and strict schema.
Protocol support does not imply every selected model supports images; errors explicitly explain this.
Fake rejects images before reading them. No hidden provider switch or paid fallback.

Only a <=240-character RouteHint remains in session memory, reused as text. GPS and user intent
stay authoritative. Start cancels unfinished analysis; clear, end and service loss discard the hint.
No persistent URI permission, image file upload endpoint, backend or trip-photo persistence.

Same six-task all-variant command: PASS, **102 tests per variant**, zero failures/errors/skips,
lint zero errors. Log: `artifacts/phase-b-gates.log`.
Photo picker on vivo, screenshot interpretation and real selected-model image support:
**DEFERRED TO COMBINED PHONE ACCEPTANCE**.
Interface sources: [OpenAI images and vision](https://developers.openai.com/api/docs/guides/images-vision),
[Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker).

## Phase C — visual talk

Active-trip secondary **旅途看图** control opens **拍照 / 从相册选择**, an optional typed
question, and the default **帮我看看这个**. Camera capture delegates to the system camera
with a narrowly scoped, non-exported FileProvider cache URI; no CAMERA/storage permission.
Opening stops TTS/listening and cancels automatic work. The image interaction pauses simulated
travel and auto checks; send/cancel releases it with a short cooldown.

A single image goes through the same Director with current GPS/session/RouteHint, strict JSON,
local TTS and active-request failure behavior. Later auto checks have no image. Selection
replacement, skip, quiet, new user intent and trip end invalidate pending pictures; no image queue.
The temporary camera file is deleted after reading, cancellation, disposal, trip end/service loss
and next runtime initialization. Process recreation drops unfinished image interaction rather than
restoring raw media. Existing photo-picker originals remain under the user's own gallery control.

Real camera/gallery/provider/TTS response: **DEFERRED TO COMBINED PHONE ACCEPTANCE**.
The optional follow-up in this batch is typed; Chinese ASR implementation/acceptance remains excluded.
All-variant build/tests/lint: PASS, **108 tests per variant**, zero failures/errors/skips,
lint zero errors. Log: `artifacts/phase-c-gates.log`.

## Phase D — refined route and content regression

User-requested start **成都市新都区**, end **绵阳市安州区雎水镇**. The 29-point
coarse scenario traverses 13 town/street chapters through 青白江、广汉、德阳、绵竹 and 安州.
Test polyline length **90,912 m**; not a recommended driving route, surveyed road alignment or
authoritative administrative boundary. The original 110km fixture remains test-only for the exact
M1.2 historical six-stale-response reproduction. No old regression scenario was removed.

At **100 km/h / 16×**, production Journey/Context/Director with deterministic Provider/fake Voice,
five-second request delay and twenty-second TTS: **13 chapters / 13 cached cards**, Sanxingdui
eligible and selected, **six topic families**, **14 opportunities, zero stale/cancel/failure**.
The deterministic selector is test-only; no Sanxingdui narration script or category quota was added
to production. Separate all-SILENT and failed-provider tests establish no forced speech/retry storm.
M1.3 delayed dialogue gate: 14 opportunities, 15 requests, zero stale/cancel/failure on the new route.
The 80 km/h Fake golden path: 68 simulated minutes, 51 checks, four voice outputs, quiet/interruption/end PASS.
Reports: `artifacts/batch-simulation.txt`, `artifacts/accelerated-simulation.txt`, `artifacts/full-simulation.txt`.

Broader candidate lenses plus small stable associations: 绵竹年画 and 雎水太平桥/踩桥、沙汀故居.
Sources: [绵竹市政府](https://www.mz.gov.cn/gk/zfxxgk/fdzdnr/cdgz/1594150.htm),
[四川统一战线](https://www.sctyzx.gov.cn/my/202409/54308962.html).
They are orientation/candidate facts; no dates, records, current events or sightline claims are inferred.
Endpoint coordinates are rounded coarse representative coordinates, not navigation truth.

Chinese Geocoder field-layout normalization improved; Android 33+ uses an eight-second bounded
async lookup. Older platforms use a single IO lookup: a blocked system implementation cannot be
force-interrupted, but GPS/Journey stay live and no concurrent geocode requests accumulate.
Additional image bounds/metadata/cleanup and FileProvider declaration tests PASS. AndroidX URI grants
remain a physical Android gate because Windows JVM canonical paths differ from Android's '/' paths.

All-variant build/tests/lint: **PASS, 116 tests per variant**, zero failures/errors/skips,
lint zero errors; `artifacts/phase-d-gates.log`. No local `OPENAI_API_KEY` was present (presence-only check).
Phone account state was not accessed. Real ChatGPT narrative variety, image inference and optional
account web-search probe remain **DEFERRED TO COMBINED PHONE ACCEPTANCE**; no real-AI product PASS claimed.

## Additional requirement — independent geographic / landmark proximity

Area Chapters remain the main container. A bounded, source-backed spatial catalog independently creates
opportunities inside an unchanged chapter, ahead of generic road/settlement explanation. The same Director
decides the content; SILENT is valid. The four shipped regional references are independent of simulated
fixture metadata and work with real GPS as well. All 14 requested geographic/landmark kinds use the same
geometry. No nationwide discovery, surveyed coordinates or visibility inference is claimed.

Nine focused tests cover same-chapter/sub-distance opportunities for every kind, the 45s gate,
quiet passing without replay, image/listening priority, no SILENT retry storm, receding-node stale rejection
below 1500m, entity dedup with active deepening, reset, catalog validation and six/seven-field compatibility.
The schema now uses eight strings including `landmark_id`. See [spatial rules and coverage](LANDMARK_PROXIMITY.md).

Production catalog/Context/Journey/Loop, deterministic Provider/fake Voice, **100km/h / 16× / 5s latency / 20s TTS**:
**13 chapters, all four nodes selected once, 17 opportunities, 13 SILENT + 4 SPEAK_NOW, zero stale/cancel/failure**.
This is a behavior regression, not a real AI narration count requirement. Report: `artifacts/landmark-simulation.txt`.

## Phase E — visual identity and final combined engineering gates

Restrained black/red night palette, high-contrast daylight palette, distinct status capsules, slow scanner
with a faint glow and unchanged RMS listening semantics. Current place stays concise; image controls remain
secondary. Main touch controls remain at least 48dp. Compact portrait and landscape keep the driving page
unscrolled; the optional passenger/stopped image question can scroll with the keyboard.
No navigation or state architecture rewrite, dependency or new permission was added.

Two Compose/Robolectric tests establish compact **360×640 night portrait** and **891×411 day landscape**
visibility, state/control transitions, current place, quiet countdown, route/photo controls and no driving-page
scrolling. Existing driving/landscape/voice tests remain green. Actual phone contrast, touch feel, camera URI
grants and OEM background endurance are **DEFERRED TO COMBINED PHONE ACCEPTANCE**.

Final six-task combined gate: **Debug and Release builds / 127 tests each / both lint tasks PASS**,
zero test failures/errors/skips. Log: `artifacts/landmark-and-phase-e-gates.log`.
Lint has **zero errors, five Debug / two Release warnings**: existing dependency notices and the Android
framework ExifInterface advisory introduced with image reading. Framework EXIF support matches the minimum
Android 8 target; actual device image orientation is retained in the phone checklist.
Packaging/signature/hash and reproducible verification are generated by `scripts/verify.ps1 -Offline`.

All original M1.1–M1.4 scenarios are preserved; the original 110km fixture remains test-only for its historical
stale-response reproduction. No Chinese ASR integration/replacement was attempted. No phone was accessed,
no current account token was inspected and no new real-provider/phone PASS is claimed.
Only the [combined phone pass](COMBINED_PHONE_ACCEPTANCE.md) remains required for these physical/product gates.
An account-level web-search probe is optional future work; it does not block conservative V0.2 narration.
