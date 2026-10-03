# Future Task — ASR Quality Upgrade

Status: **BACKLOG — do not implement now**
Scope: future voice-recognition quality work only

## Why this exists

Current Chinese ASR is usable for short commands and simple questions, but longer natural speech and local place/person names can drift.

This is a quality limitation, not a blocker for the current product milestone.

## Candidate future direction

Keep the current interaction and `SpeechEngine` seam. Do not redesign Journey/Director.

Investigate, in a future milestone:

- replace or demote Vosk as the primary offline recognizer;
- evaluate sherpa-onnx based Chinese ASR;
- compare Streaming Paraformer, SenseVoice and a Sichuan/Chongqing-oriented Paraformer where practical;
- preserve live partial transcript where the backend supports it;
- keep ASR free / local / no API key if feasible;
- retain current typed-input fallback.

## Product-specific advantage to explore

Use current journey context to improve recognition of local proper nouns.

Potential sources:
- current area/chapter identity;
- Local Dossier entities;
- nearby landmarks;
- destination/route hints;
- recently mentioned people/places.

Possible techniques:
- native contextual biasing / hotwords if stable on the chosen backend;
- or a conservative post-ASR proper-noun correction layer using only high-confidence contextual candidates.

Do not let correction rewrite arbitrary sentence meaning.

## Future benchmark

Compare candidates on the same real-phone utterance set, including:
- 再讲一点
- 跳过
- 安静十分钟
- 三星堆为什么这么有名
- 宝光寺有什么历史
- 杨升庵和桂湖是什么关系
- 马尔康有什么值得看的
- several 10–20 second natural spoken sentences

Measure:
- Chinese accuracy;
- local proper-noun accuracy;
- partial-result quality;
- first-token / final latency;
- endpointing behavior;
- APK/model size;
- memory use;
- Android arm64 stability;
- licensing/maintenance burden.

## Boundary

Do **not** start this work during the current 沿途 V0.3 search/content/trigger milestone.

Revisit only after real-road usage shows ASR accuracy is a meaningful bottleneck.
