# Future Task — Unified Speech Stack Upgrade

Status: **BACKLOG — do not implement now**
Scope: future voice input + voice output quality and latency

## Why this exists

Current voice system has three known quality limits:
- ASR: short commands work, but longer natural speech and local place/person names can drift;
- TTS: long sentences/paragraphs can sound mechanical and pause at awkward places;
- latency: a road-trip product cannot wait so long that the vehicle has already passed the relevant place before audio starts.

This is a future quality project, not part of the current 沿途 V0.3 content/search/trigger milestone.

## Hard product gates

Any future speech-stack upgrade must satisfy all of these:

1. **No extra usage cost**
   - no per-minute/per-character/per-request billing;
   - no paid API dependency as the default;
   - no required new subscription or key.

2. **Phone-practical**
   - must install and run reliably on the user's vivo phone;
   - APK/model size, RAM, CPU, startup latency, battery and long-drive stability must be measured on-device;
   - prefer smaller robust models over desktop-quality models that are impractical on mobile.

3. **Low latency**
   - optimize time from 'KITT decides this should be said now' to first audible speech;
   - do not wait for a full long narration to finish synthesizing before playback starts;
   - do not let local processing delay make location-aware narration stale.

## Preferred architectural direction

Do not chase a single giant multimodal model that listens, reasons and speaks inside one black box.

Prefer one local speech runtime with two specialized engines behind stable boundaries:

> microphone → local streaming ASR → existing Director/search/Local Dossier → local neural TTS → speaker

Keep Journey, Director and search semantics independent from the speech engine.

A shared runtime such as sherpa-onnx is worth evaluating because it can host both Android ASR and TTS, but the exact model choice must be benchmarked rather than assumed.

## Input side (ASR)

Future ASR work may evaluate:
- Streaming Paraformer;
- SenseVoice;
- Sichuan/Chongqing-oriented Chinese models where practical;
- other free/offline Android-capable recognizers.

Preserve:
- one-shot interaction;
- live partial transcript where supported;
- typed-input fallback;
- no wake word;
- no persistent raw audio.

Explore contextual proper-noun assistance from current journey state:
- current chapter/area;
- Local Dossier entities;
- nearby landmarks;
- destination/route hints;
- recently mentioned places/people.

Possible mechanisms:
- backend hotwords/contextual biasing if stable;
- conservative post-ASR proper-noun correction using only high-confidence contextual candidates.

Do not let correction rewrite arbitrary sentence meaning.

## Output side (TTS)

Future TTS work should improve:
- long-paragraph naturalness;
- sentence segmentation and pauses;
- prosody/emphasis;
- Chinese proper-noun pronunciation;
- listener fatigue over 2–5 minute narration.

Before replacing the TTS engine, evaluate a low-cost text-only prosody preprocessing layer:
- split overlong sentences into natural semantic/breath groups;
- improve punctuation and pause structure;
- preserve facts and wording meaning;
- do not summarize or editorially rewrite the narration.

Then compare free/offline mobile neural TTS candidates.

## Streaming playback requirement

Long narration should be produced and played as a pipeline, not as one giant blob:

> Director produces a short semantic chunk
> → TTS synthesizes that chunk
> → playback starts immediately
> → while chunk 1 is playing, synthesize chunk 2
> → keep only a small look-ahead buffer

Preferred look-ahead: approximately 1–2 semantic chunks, not an entire multi-minute narration.

Chunking must follow meaning and natural breath points, not naive comma splitting.

## Search/preparation latency interaction

Speech latency cannot be solved only at the TTS layer.

To avoid talking too late:
- research/local-dossier work should happen before the exact narration moment when possible;
- chapter/landmark approach opportunities should prepare context early;
- at trigger time, the Director should only need to choose and phrase the next useful chunk;
- stale/left-behind content must still be dropped.

## Benchmark

Use the same real-phone test set across candidate stacks.

ASR phrases should include:
- 再讲一点
- 跳过
- 安静十分钟
- 三星堆为什么这么有名
- 宝光寺有什么历史
- 杨升庵和桂湖是什么关系
- 马尔康有什么值得看的
- several 10–20 second natural spoken sentences.

TTS benchmark:
- the same 2–5 minute Chinese narration, including long sentences and local proper nouns.

Measure:
- ASR accuracy;
- local proper-noun accuracy;
- partial-result quality;
- first-token/final ASR latency;
- endpointing behavior;
- TTS first-audio latency;
- streaming continuity;
- long-sentence pause quality;
- naturalness;
- proper-noun pronunciation;
- APK/model size;
- peak/resident memory;
- CPU/battery behavior;
- 1–2 hour driving stability;
- offline availability;
- licensing.

## Acceptance principle

Do not choose by benchmark tables alone.

The final decision must be made from real-phone use in a moving-road scenario.

The key product metric is:

> **从沿途决定“这句话该讲了”，到扬声器出现第一个字，要足够短，而且声音自然、内容还没过时。**

## Boundary

Do **not** start this work during the current 沿途 V0.3 milestone.

Current priority remains:
- accurate narration;
- rich local content;
- comfortable real-world use.

Revisit this project only after the core product is useful enough that speech quality is the next meaningful bottleneck.
