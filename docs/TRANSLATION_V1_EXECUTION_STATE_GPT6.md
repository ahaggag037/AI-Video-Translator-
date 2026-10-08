# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `75fb895e1d00e0b72c1aa07b0472c1d4e0774d8a`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011/X005 durable request receipts and exact recovery fences, layered on B003 typed transport. No production semantic-segmentation or clock activation.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy fully verified by CI run `37723687884`; additive detailed NVIDIA transport is pushed and awaits its own CI.
- B004 shadow deterministic segmentation/request identity + X002 seed; compile and final-window strong-gap defects were corrected without production activation.
- B006 Unicode/ICU boundary/layout ownership foundations in shadow mode.
- B011 semantic display timing, binary cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 receipts: PREPARED→SENT→RECEIVED atomic phases, bounded codec, no credential fields, durable provider outcomes, explicit unknown-remote recovery for SENT, and frozen manifest/entry revision fence data for safe post-crash adoption.

## Partial / blocked
- Controller-level revalidation/adoption of matching RECEIVED candidates into immutable entries remains incomplete.
- Current later CI runs remain in progress/queued; no PASS claims beyond verified B003.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 has no pinned-font/raster/device evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (durable receipts + exact fences; crash/ENOSPC/device execution not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- Fixed B004, B006, B011, detailed-transport and receipt workflows remain pending/in progress at this checkpoint.
- Exact-fence receipt tests are committed for CI and are not marked PASS yet.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated.
- `SENT` is intentionally conservative because it is written before network invocation; this preserves the no-blind-repost invariant.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Provider outcome dimensions are orthogonal; unknown remote outcome is not auto-reposted.
- Successful received provider text becomes durable before manifest adoption.
- Receipt adoption requires exact epoch + manifest revision + active entry revision + request signature match.
- Manual text outranks machine candidates and survives retranslation/style/SRT/render changes.
- Experiment-gated production paths remain inactive without evidence.

## RESUME HERE
Inspect current CI and fix the earliest failing commit first. Then implement controller-level recovery adoption from matching RECEIVED receipts into immutable candidate entries under SessionFencing/CAS, or build the X003 Android layout/raster harness if CI is green.
