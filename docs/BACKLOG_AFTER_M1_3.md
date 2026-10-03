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

### AI speaking visual feedback

The user also wants an obvious animated state while KITT is speaking, so the app feels alive and the user can instantly tell that audio output is active.

Desired UX:
- During `JourneyState.SPEAKING`, show a clear dynamic visual effect rather than only the static text **“正在讲述”**.
- The animation should be smooth, glanceable, and appropriate for driving; no tiny decorative UI that requires attention.
- It can visually echo the future KITT identity (pulse / scanner / waveform / breathing bars), but the first implementation should stay lightweight.
- Listening and speaking states should be visually distinct at a glance.
- Prefer driving-safe motion: large, low-frequency, non-distracting animation rather than fast flashing.
- The effect should stop immediately when TTS stops, is interrupted by **“说点什么”**, skipped, enters quiet mode, or the journey ends.
- No requirement for true audio-frequency analysis in the first version; a state-driven animation is sufficient unless actual TTS amplitude is cheaply available.

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

## 4. In-trip photo / image discussion

User wants a **small camera/image button** available during a journey. This is separate from the route-reference screenshot feature.

Use case:
- The user encounters something on the road that is hard to describe verbally.
- They can take a photo or choose an existing image and send it to the AI.
- The AI discusses the image in the context of the current journey/location/session, so the user can ask things like “这是什么？” or “为什么会这样？”

Desired UX:
- Keep the button visually secondary and smaller than the primary driving controls.
- One tap opens a simple choice such as **拍照 / 从相册选择**.
- After image capture/selection, allow one short spoken or typed question, or default to a concise “帮我看看这个”.
- Reuse the current location / journey intent / session instructions as contextual grounding.
- The image should be treated as an **active user message**, not as automatic background narration.
- After the response, return to the normal driving state without creating a long-lived image queue.

Data / product boundaries:
- Do not store trip photos long-term by default.
- Do not repeatedly resend an old image on every later Director check.
- Do not convert every image into permanent memory unless explicitly requested later.
- If useful, keep only a tiny session-local summary/topic so later dialogue can refer to “刚才那张照片” without retaining the raw image indefinitely.
- This feature should use the multimodal image capability of the selected AI Provider when supported; unsupported Providers should show a clear fallback rather than silently failing.
- Route screenshots and in-trip photos may share the same image plumbing, but they are different product intents:
  - **Route reference** = pre-trip/session guidance.
  - **In-trip image** = active multimodal conversation.

Driving-safety UX:
- The app should not require fine-grained interaction while the vehicle is moving.
- Camera/image capture should remain a secondary action suitable for a passenger or for use when stopped; the core hands-free voice flow stays primary.

Possible later milestone: **M1.6 Visual Talk** (or combine plumbing work with M1.5 while keeping the two intents separate).

## 5. UI visual polish — deliberately later

The user wants a dedicated UI beautification pass, but **after the core interaction, voice, route-reference, and visual-talk behavior are stable**.

Principle:
- Do not spend time polishing surfaces while core phone UX is still changing.
- First make KITT functionally right; then give it a coherent visual identity.

Later visual direction can build on the previously discussed KITT feel:
- restrained dark/black base;
- red scanner / pulse / glow language;
- large, glanceable driving-safe hierarchy;
- speaking/listening/quiet states expressed visually;
- avoid decorative complexity that competes with driving attention.

Possible later milestone: **M1.7 UI Polish / KITT Visual Identity**.

## Priority / sequencing

1. Finish **M1.3 accelerated simulation Director fix** first.
2. Then address **Voice Experience** (speech recognition diagnostics + dynamic listening UI + dynamic AI-speaking UI + TTS voice selection).
3. Then address **Route Reference image**.
4. Then address **In-trip Visual Talk**, optionally sharing the image transport/picker plumbing built for Route Reference.
5. Then do the broader **UI visual-polish pass**.

These are remembered backlog items, not active implementation instructions yet.
