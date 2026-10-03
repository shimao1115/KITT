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
