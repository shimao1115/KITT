# Future Task — Instant Local Destination Prompt

Status: **BACKLOG — do not implement now**

## Problem

After the user taps “开始旅程”, 沿途 currently waits for:
1. a first valid GPS fix;
2. an automatic Director check;
3. ChatGPT network/model latency;
4. then Director returns ASK_USER;
5. TTS finally asks “今天准备去哪儿？”

This creates unnecessary dead air at journey start. The destination question is deterministic and does not depend on GPS, Local Dossier research, or AI reasoning.

## Future change

When a brand-new journey starts and destination is still `未询问`:

- ask locally and immediately via the existing Voice/TTS path:
  **“今天准备去哪儿？”**
- open the existing one-shot reply window immediately;
- allow ASR or typed reply through the same existing handler;
- do not wait for GPS;
- do not wait for Overview research;
- do not call Director merely to decide whether to ask this fixed question.

GPS acquisition, area resolution, Overview research, and other startup work should continue in parallel.

After the user answers, persist the destination into the normal Journey state and continue with the existing Director flow.

## Boundaries

Do not:
- create a second conversation path;
- duplicate command parsing;
- bypass the existing ASR/typed reply handling;
- ask twice;
- re-ask if destination is already known or the user previously declined/failed to answer according to current Journey semantics;
- change Journey/Director behavior beyond removing this deterministic startup question from the network critical path.

## Product principle

> 本地能确定的事情，就不要交给网络 AI 等待。

Desired UX:

> Tap “开始旅程” → immediate local TTS “今天准备去哪儿？” → user answers → GPS / research / Director continue in parallel.

## Acceptance

- first destination prompt begins essentially immediately after journey start, limited only by local TTS startup;
- no GPS or network dependency for the first prompt;
- no duplicate ASK_USER from Director;
- ASR and typed reply both still work;
- unanswered/declined destination follows the existing `未提供` behavior;
- full regression suite passes.

Do **not** implement this during the current outing / current V0.3.2 field-use window.
