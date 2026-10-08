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

## Not yet executed
No paid/live NVIDIA calls and no human Arabic scoring have been performed for this checkpoint. No legacy-vs-semantic paired outputs exist yet. Do not mark X002 PASS. Production segmentation activation remains BLOCKED_BY_X002, and protocol-v2 approved examples remain experiment-only.

## Required completion evidence
Run the same 48 source passages through the legacy baseline and semantic-v1 under frozen request identities, then conduct a blind Arabic review using the canonical rubric. Record critical omissions, number/integrity corruption, command following, fidelity/naturalness ratings, request counts, review-required incidence and layout-overflow incidence. N25 requires >=90% rated >=4/5 for fidelity and naturalness and zero critical errors in the reviewed subset. Any approved-example profile must be compared on the same units under a distinct request identity; failure keeps text-v1/legacy behavior rather than silently changing model or prompt.

