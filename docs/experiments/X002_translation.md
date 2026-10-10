# X002 — Segmentation / Translation Fidelity / Context

State: HARNESS_READY (N25 source corpus shape complete; no provider outputs or human Arabic verdict recorded).

## Purpose
Per ACCEPTED AR-01 (Runtime Kernel V3.1 baseline), X002 compares semantic unit alone; semantic unit plus bounded neighboring SOURCE context; and approved deterministic terminology/examples where useful. Keep legacy segmentation/request behavior as the production comparator. Never use previous TARGET translation chains or rolling translated target context. Every output-affecting input participates in request identity.

## Current safe implementation
- `SourceSegmenter` is shadow-only and requires presentation-anchored words supplied by a clock boundary; it does not infer STT units.
- `TranslationPlanner` keeps `nvidia-text-v1` as one semantic unit / text-only / system `en-ar` and rejects few-shot inputs under protocol v1.
- Request signatures use canonical length-prefixed UTF-8 fields. Acceptance dependencies are hashed separately so validation/glossary changes do not force a provider POST when the sent request is unchanged.
- `quality_corpus.json` is synthetic source text only and now freezes the N25 sampling shape: 48 passages, exactly 12 explicitly tagged adversarial and 12 explicitly tagged integrity cases, with technical/business/conversation plus pronoun/negation/number coverage.
- The corpus intentionally contains no Arabic reference answers and no automated quality score. It is input material for a later paired blind Arabic review, not evidence that any profile is good.

## W2 release evidence — 2026-10-10
Implementation checkpoint `5b56764fe524b031f8811feb12ee6a141ed60038` adds a shadow-only X002 evaluation surface without changing production truth:
- deterministic `UNIT_ONLY` and `BOUNDED_SOURCE_CONTEXT` request modes, with at most one neighboring SOURCE unit per side and explicit request/binding identities;
- result fencing that rejects stale unit/signature pairs, policy-blocked or structurally failed candidates, and malformed/missing/extra/duplicate batch mappings rather than mixing results;
- request/result contracts that carry translation text/identity but no timing ownership;
- a 12-case synthetic SOURCE-only context fixture bound to the semantic planner by regression tests;
- `X002_BLIND_ARABIC_EVALUATION.md` plus `scripts/x002_blind_eval.py`, which create a complete paired-output skeleton, deterministic blinded A/B package, separate mapping key, and fail-closed scorer. The tool does not call a provider or invent human scores.

Local deterministic verification at this checkpoint: 15/15 Kotlin semantic/planner regression cases passed using a local JUnit shim because Gradle is unavailable in the worker runtime; the context resource/planner test syntax compiled separately; and the exact Python scorer bytes now stored in Git blob `a5e511ffe9cf2330a442d6d8cf1df99b05ae83a7` passed 3/3 Python `unittest` cases. These checks are not GitHub Actions CI and are not translation-quality evidence.

No `NVIDIA_API_KEY` or `NVAPI_KEY` was available in the worker runtime, so no provider call was made. Paired provider outputs and blind Arabic human scores remain PENDING. Legacy remains comparator/production truth.

Shared durable recovery still validates request plans through `ReceiptRecoveryPlanner.plan` → `LegacyParityTranslationPlanner.isLegacyAcceptanceSignatureValid`. If X002 is later accepted for production, that shared recovery/adoption boundary requires an integrator-approved change; it is intentionally not changed by this shadow evaluation work.

## Not yet executed
No paid/live NVIDIA calls and no human Arabic scoring have been performed for this checkpoint. No legacy-vs-semantic paired outputs exist yet. Do not mark X002 PASS. Production segmentation activation remains BLOCKED_BY_X002, and protocol-v2 approved examples remain experiment-only.

## Required completion evidence
Run the same 48 source passages through the legacy baseline and semantic-v1 under frozen request identities, then conduct a blind Arabic review using the canonical rubric. Record critical omissions, number/integrity corruption, command following, fidelity/naturalness ratings, request counts, review-required incidence and layout-overflow incidence. N25 requires >=90% rated >=4/5 for fidelity and naturalness and zero critical errors in the reviewed subset. Any approved-example profile must be compared on the same units under a distinct request identity; failure keeps text-v1/legacy behavior rather than silently changing model or prompt.
