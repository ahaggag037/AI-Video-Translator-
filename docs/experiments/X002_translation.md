# X002 — Segmentation / Translation Fidelity / Context

State: HARNESS_READY (seed only; no live provider result recorded).

## Purpose
Compare legacy segmentation with semantic-v1 and, only if separately approved for experiment, bounded source-context / deterministic approved examples. Never use previous translated target text as hidden context.

## Current safe implementation
- `SourceSegmenter` is shadow-only and requires presentation-anchored words supplied by a clock boundary; it does not infer STT units.
- `TranslationPlanner` keeps `nvidia-text-v1` as one semantic unit / text-only / system `en-ar` and rejects few-shot inputs under protocol v1.
- Request signatures use canonical length-prefixed UTF-8 fields. Acceptance dependencies are hashed separately so validation/glossary changes do not force a provider POST when the sent request is unchanged.
- `quality_corpus.json` contains synthetic seed cases only.

## Not yet executed
No paid/live NVIDIA calls and no human Arabic scoring have been performed for this checkpoint. Do not mark X002 PASS. Production segmentation activation remains BLOCKED_BY_X002.

## Required completion evidence
Expand to the planned reviewed corpus, compare legacy vs semantic-v1 on whole passages, record factual/negation/instruction-following failures, request counts and review-required/overflow incidence. Any few-shot/context profile must have a distinct request identity and may activate only if its evidence is better without critical regressions.
