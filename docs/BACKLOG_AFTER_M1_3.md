# Post-M1.3 backlog — user-observed real-phone UX issues

Status: **remember only / do not implement yet**.

These items were observed by the user on the current real-phone build and should be handled in later milestones after M1.3. Do not fold them into M1.3 unless they directly block that milestone.

## 1. Voice input / “说点什么” listening UX

Observed:
- Tapping **“说点什么”** immediately ends with **“没听清”**.
- The user cannot tell whether KITT is actually listening.

Likely implementation issue to investigate later:
- Android `SpeechRecognizer` errors are currently collapsed into `null`, so service unavailable / busy / startup failure / timeout can all look like “没听清”.
- `RecognitionListener.onRmsChanged()` is currently ignored, so there is no live microphone/listening feedback.

Desired UX:
- Tap **“说点什么”** → enter a clear listening state.
- Show a visible dynamic listening indicator (waveform / bars / pulse) driven by real microphone RMS/voice activity.
- Text such as **“正在听，请说话”** while active.
- Distinguish real error states from “speech not understood”.
- Keep one-shot listening semantics; no always-on microphone or wake word.

Possible later milestone: **M1.4 Voice Experience**.

## 2. Route-reference image input

User wants an optional entry to provide a **Gaode/Amap route screenshot** for AI reference.

Desired product behavior:
- Before or at trip start, user may optionally choose/upload one route screenshot.
- The image is a **reference hint**, not navigation truth and not a replacement for GPS.
- Prefer one-time AI interpretation of the screenshot into a compact trip/session **Route Hint**.
- Subsequent Director calls should reuse the compact text hint rather than repeatedly re-uploading the image.
- Real GPS remains authoritative for actual movement/deviation.
- Do not build route prediction, map scraping, route APIs, RAG, or a backend merely for this.
- Route reference should be session-scoped and cleared at trip end unless explicitly designed otherwise.
- Entry should likely live on the idle/start journey surface rather than permanent Settings.

Possible later milestone: **M1.5 Route Reference**.

## 3. Selectable local TTS voice

Previously requested and reconfirmed.

Desired behavior:
- Enumerate available Android `TextToSpeech` voices, preferably Chinese/zh-CN first.
- Settings provides a **voice selector**.
- One-tap **preview / audition**.
- Persist selected voice.
- Restore on next launch.
- If the selected system voice disappears after OS/TTS update, safely fall back to the default Chinese voice.
- Do not assume reliable male/female metadata; use system voice names and preview when metadata is insufficient.
- Keep current speech-rate control.

This can be grouped with M1.4 if convenient.

## Priority / sequencing

1. Finish **M1.3 accelerated simulation Director fix** first.
2. Then address **Voice Experience** (speech recognition diagnostics + dynamic listening UI + TTS voice selection).
3. Then address **Route Reference image**.

These are remembered backlog items, not active implementation instructions yet.
