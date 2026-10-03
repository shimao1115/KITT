# Post-M1.4 batch — non-phone evidence

Date: 2026-10-03 (Asia/Shanghai). Chinese ASR is out of scope.

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
