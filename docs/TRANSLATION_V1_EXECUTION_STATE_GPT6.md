# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `d124800c363059a7be7b0307705f8f80fd166e9b`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011/X005 durable request receipts and recovery semantics, layered on B003 typed transport. No production semantic-segmentation or clock activation.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy fully verified by CI run `37723687884`; additive detailed NVIDIA transport is pushed and awaits its own CI.
- B004 shadow deterministic segmentation/request identity + X002 seed; compile regression corrected at `2e055752...`.
- B006 Unicode/ICU boundary/layout ownership foundations in shadow mode.
- B011 semantic display timing, binary cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 receipt persistence foundation: PREPARED→SENT→RECEIVED atomic phase progression, bounded codec, no credential fields, durable received provider candidate/outcome, and explicit recovery disposition where SENT is unknown rather than auto-retryable.

## Partial / blocked
- Receipt recovery adoption into translation entry/manifest still needs controller-level CAS integration and Android crash/ENOSPC execution.
- Current later CI runs remain in progress/queued; no PASS claims beyond verified B003.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 has no pinned-font/raster/device evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (durable receipt protocol + fencing foundations; crash/ENOSPC/device execution not yet PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- Fixed B004, B006, B011 and detailed-transport workflows are pending/in progress at this checkpoint.
- Receipt codec/recovery tests are committed for CI and are not marked PASS yet.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated.
- `SENT` is intentionally conservative: because it is written before network invocation, a crash in the tiny pre-call window may be reported as unknown. This preserves the no-blind-repost invariant.

## Active architectural decisions
- Semantic, display, timing, persistence and renderer ownership remain separated.
- Provider outcome dimensions are orthogonal; unknown remote outcome is not auto-reposted.
- Successful received provider text becomes durable before manifest adoption.
- Manual text outranks machine candidates and survives retranslation/style/SRT/render changes.
- Experiment-gated production paths remain inactive without evidence.

## RESUME HERE
Inspect all current CI and fix the earliest failing commit first. Then implement recovery adoption from matching RECEIVED receipts into immutable translation entries under SessionFencing/CAS, or build X003 Android layout/raster harness if CI is green and device/font evidence is available.
