# Post-M1.3 backlog — user-observed real-phone UX issues

Status: **remember only / do not implement yet**.

These items were observed by the user on the current real-phone build and should be handled in later milestones after M1.3. Do not fold them into M1.3 unless they directly block that milestone.

> **Superseded on 2026-10-03.** The content-breadth item below was implemented in M1.5–M1.7 and then rewritten by
> `docs/TASK_EDITORIAL_FREEDOM.md`. Treat the observed problems here as historical diagnosis only. In particular,
> “choose the most interesting lens / avoid repeating the explanatory family / Sanxingdui outranks generic road topics”
> is **no longer** current policy: the local material shelf is open-ended, topic families are only a weak
> anti-repetition signal, and Sanxingdui is available-but-not-forced material. Current truth lives in
> `docs/AI_CONTRACT.md`, `docs/PRODUCT_SPEC.md` and `HANDOFF.md`.

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


## 5. Current location display during a journey

The user wants the main driving screen to show the **current location / place** while KITT is running.

Current implementation note:
- The driving screen already tries to show `journey.fix.area`.
- Simulated fixes populate `area`, so simulation can show broad route stages.
- `RealLocationSource` currently emits only raw GPS coordinates/speed/bearing/altitude/accuracy and leaves `area` empty.
- Therefore a real-GPS journey currently degrades to the generic label **“GPS 已定位”** instead of a human-readable place.

Desired UX:
- During a real journey, show a concise, glanceable current-place label on the main screen.
- Prefer human-readable place context such as district / town / nearby area rather than exposing raw latitude/longitude as the primary display.
- If human-readable lookup is unavailable, fall back gracefully to a compact coordinate or “GPS 已定位” state rather than blocking the journey.
- Location display should update at a sensible low frequency; do not reverse-geocode every raw GPS fix.
- This is informational only; it must not turn KITT into a navigation app.
- Keep location lookup separate from AI Provider selection.

Implementation direction to evaluate later:
- Android platform reverse geocoding / Geocoder is the lightest first option if sufficiently reliable on target phones.
- Optional map-provider enrichment can remain a later enhancement; do not add an AMap SDK solely to render a place label unless needed.

Possible milestone: fold into **M1.4/M1.5 usability pass** before the final visual-polish milestone.


## 6. Narrative breadth / local culture and history

Observed on the first real ChatGPT accelerated-simulation run:
- The two automatic narrations were both generic spatial-mechanism topics: why a road bends, and how roadside villages grow.
- The user explicitly found this **too monotonous and too narrow**.
- Desired KITT experience must also surface **local history, cultural geography, notable places, heritage/monuments, famous sites, local stories, industry, food/everyday life, and other human context** when relevant.

Current likely product cause:
- The Director constitution strongly favors `地形 → 道路 → 聚落 → 历史与生活` causal explanation.
- It repeatedly rewards silence and warns against unverified local specifics.
- Current real/simulated Context is sparse and has no verified nearby POIs/history.
- Therefore the model has very little safe local material and naturally falls back to generic geography/roads/settlements.

Important distinction:
- **Do not solve this by merely telling the model “talk more about history”.** Without reliable local facts, that would increase hallucination risk.
- This is partly an editorial-policy problem and partly a Context/enrichment problem.

Desired editorial policy:
- KITT should have multiple narrative lenses available:
  - natural geography / landform;
  - roads / engineering / transport;
  - settlements and how people live;
  - local history and historical change;
  - notable sites / monuments / heritage / scenic places;
  - cultural geography / customs / place names;
  - industry / agriculture / food / everyday economy;
  - people and stories when they are reliably grounded.
- Do not rotate categories mechanically.
- Choose the most interesting lens for the current place and avoid repeating the same explanatory family.
- Silence remains valid, but “generic road mechanism again” should not beat a well-grounded local human/history topic.
- Track recent **topic family** as well as topic text so repeated road/settlement explanations are discouraged.

Desired information strategy:
- Human-readable current place and route reference can help, but they may not be sufficient.
- Evaluate a lightweight **local enrichment / verified-facts** step that can provide a few reliable nearby/history candidates to the Director.
- Search/enrichment should be selective and cached per trip/area, not repeated on every Director check.
- Exact names, dates, records and local claims must remain grounded; if evidence is unavailable, prefer omission over invention.

Acceptance idea for later content milestone:
- On a representative Chengdu→Deyang→Mianyang run, automatic narration should demonstrate **variety across topic families**, including reliably grounded human/history/cultural/site topics when such material is available.
- **Sanxingdui / Guanghan is a concrete acceptance benchmark for this fixture.** A system that drives through the Guanghan/Deyang corridor yet only talks about curved roads and village growth is editorially incomplete. The later enrichment layer should surface major high-salience cultural/history candidates such as Sanxingdui when route/place context makes them relevant.
- Likewise, significant ancient towns, heritage sites, historic settlements, local museums/ruins, and other culturally important nodes along a route should be eligible candidates for narration rather than being invisible behind generic geography.
- Do not hard-code a fixed Sanxingdui script into production. Use it as a benchmark that the place/candidate-discovery system can actually notice major nearby cultural nodes.
- Do not enforce a fixed category quota or scripted sequence.

This is a **core product-content issue**, higher priority than final UI polish.

## 7. UI visual polish — deliberately later

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

1. **M1.5 Area Chapters / local narrative — PRIORITY NEXT.** Fix the core content model first: district/county background + town/township/street chapters, broader history/culture/site candidates, current-place display.
2. Then return to **M1.4 Voice Experience** (speech recognition diagnostics + dynamic listening UI + dynamic AI-speaking UI + TTS voice selection).
3. Then address **Route Reference image**.
4. Then address **In-trip Visual Talk**, optionally sharing the image transport/picker plumbing built for Route Reference.
5. Then do the broader **UI visual-polish pass**.

These are remembered backlog items, not active implementation instructions yet.
