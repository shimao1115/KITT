# Future Task — TTS Quality Upgrade

Status: **BACKLOG — do not implement now**
Scope: future narration voice quality only

## Product gates

Any future replacement for the current Android TTS must satisfy **both** of these hard requirements:

1. **No extra usage cost**
   - no per-character/per-minute/per-request billing;
   - no paid API dependency;
   - no required subscription beyond what the user already has;
   - no solution that becomes expensive simply because a long road trip produces a lot of narration.
   - cloud TTS that requires paid API usage is therefore not an acceptable default path.

2. **Must be practical on the phone**
   - the user's vivo phone must be able to install it, load it and run it reliably;
   - APK/model size must remain practically acceptable;
   - RAM/CPU load, startup latency, battery impact and long-trip stability must be measured on-device;
   - do not choose a model merely because desktop quality is good;
   - prefer a smaller robust model over a huge model whose quality gain is marginal.

## Quality goal

Improve:
- long-paragraph naturalness;
- sentence segmentation and pauses;
- prosody / emphasis;
- Chinese proper-noun pronunciation;
- listener fatigue on 2–5 minute narration.

## Architecture

Preserve the existing content pipeline:

> Local Dossier / Director text → VoiceEngine → audio

Do not merge TTS replacement with Journey/Director/search logic.

Prefer a small VoiceEngine boundary so multiple backends can be compared without changing product semantics.

## Candidate directions to evaluate later

Only evaluate candidates that can satisfy the two hard gates above.

Potential categories:
- better local/system TTS if available on-device;
- compact local neural TTS;
- Qwen/CosyVoice-family local models only if a mobile-practical deployment exists;
- other free/offline Android-capable engines.

Paid OpenAI/API TTS may be used only as a quality reference in an isolated comparison, not as the production default unless the user later changes the no-cost requirement.

## Low-cost improvement before model replacement

Before replacing the engine, evaluate a text-only prosody preprocessing step:
- split overlong sentences into natural breath groups;
- improve punctuation/pause structure;
- preserve wording/facts;
- do not summarize or rewrite content meaning.

This may improve the current Android TTS substantially with almost no runtime cost.

## Future benchmark

Use the same 2–5 minute Chinese narration across candidates and compare:
- naturalness;
- long-sentence pause quality;
- proper nouns;
- first-audio latency;
- real-time factor;
- APK/model size;
- peak/resident memory;
- CPU/battery behavior;
- stability during a 1–2 hour drive;
- offline availability;
- licensing.

Acceptance requires real-phone listening, not desktop demos.

## Boundary

Do **not** start this during the current 沿途 V0.3 content/search/trigger milestone.

Current priority remains:
- accurate narration;
- rich local content;
- comfortable real-world use.

Revisit TTS only after the core content experience is solid.
