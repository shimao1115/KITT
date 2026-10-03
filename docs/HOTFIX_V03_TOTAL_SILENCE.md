# Hotfix — V0.3 total-silence regression

Status: **URGENT**
Base: current main at/after 7de4303
Scope: restore narration responsiveness without weakening research truthfulness

## User report

After installing the new V0.3 build, the app can run for a long stretch without saying a single sentence.

This is a regression against the previous usable behavior.

## Code-level suspect to verify first

Current DirectorLoop.check() contains:

```kotlin
if (research.pending && !context.proximity.opportunity) return
```

and simulation pauses movement when:

```loop.research.pending
```

Current ChapterResearch also clears the old chapter-entry flag on area transition and only re-creates an opportunity after a dossier is READY.

Consequences to verify on-device/logs:
- a chapter research request can block all normal automatic Director checks for up to the 90s research timeout;
- if hosted search is slow/unsupported/rejected, the app can appear completely mute;
- if research fails, the chapter may lose its one chapter-entry check because no READY dossier exists;
- in simulation, research.pending can freeze route progress while waiting for network research;
- initial destination ASK_USER / other non-research automatic behavior can be unintentionally delayed behind research.

## Product rule

**Research enriches and grounds local content; it must not globally mute the co-pilot.**

Mandatory search still means:
- do not narrate chapter-specific factual claims as “researched” before real search succeeds;
- do not use model memory to fabricate local details when research failed.

But research failure/pending must not block:
- user interaction;
- already-grounded landmark content;
- destination/interaction prompts;
- other safe, non-local or already-grounded narration opportunities;
- the whole journey loop.

## Desired lifecycle

1. Chapter entry starts Local Dossier research asynchronously.
2. Keep a retained chapter state such as RESEARCHING / READY_UNCHECKED / FAILED.
3. Do not consume/lose the chapter merely because research is pending.
4. When READY and still in the chapter, create one strong chapter Director opportunity.
5. If FAILED:
   - record the failure honestly;
   - do not fabricate local facts;
   - do not keep blocking the Director loop;
   - retain an explicit “research unavailable” state so the app can still answer/user-interact and use any already-grounded content.
6. If the vehicle leaves before READY, cache evidence but do not late-narrate the old chapter.
7. Normal real GPS and simulation should continue moving while background research runs; only TTS/listening/image interaction/user-driven blocking should pause the developer simulation if that is still desired.

## Latency guard

A background chapter search must not create a 90s “dead air” gate.

If the product wants a brief grace period to wait for research before the first chapter narration, keep it very small and explicit, and do not apply it to user interaction or already-grounded landmark content.

## Diagnostics

Add clear KITTResearch/KITT logs for:
- research start/ready/failed with elapsed ms;
- whether Director check was delayed and why;
- whether a chapter opportunity is retained/consumed;
- actual ChatGPT hosted-search rejection/status if present.

## Tests

Add regression tests proving:
- research.pending does not globally suppress all Director checks;
- initial ASK_USER can still happen while research is running;
- grounded landmark opportunity can dispatch while chapter research is running;
- research failure does not leave the app permanently mute;
- failed research does not permit fabricated local dossier facts;
- READY dossier still creates/retains a chapter opportunity;
- leaving before READY prevents late narration;
- simulation does not freeze solely because research.pending;
- user typed/ASR input remains highest priority.

Run full Debug/Release tests/build/lint.

## Real-phone hotfix acceptance

On the user's current phone:
1. keep independent OpenAI research unchecked unless explicitly testing it;
2. use the existing ChatGPT account narration provider;
3. start a normal/simulated journey;
4. capture `adb logcat -s KITTResearch KITT KITTSim KITTAuth`;
5. verify the app is no longer completely silent even if hosted search is slow/rejected;
6. separately report whether ChatGPT hosted web search actually succeeds.

Do not conflate “search unavailable” with “narration system unavailable”.
