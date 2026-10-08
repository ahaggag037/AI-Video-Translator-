# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `efb52f1da93ad464c26023db9edc556054649e19`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B006 safe-pre-experiment measured Arabic layout shadow engine, layered on the existing Unicode/ICU ownership foundation and the latest X005 exact recovery fences. Production preview/hard-burn remain unchanged and gated by X003/X004.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy verified by full CI run `37723687884`; additive detailed NVIDIA transport preserves the existing request body and fails uncertain remote outcomes closed.
- B004 deterministic shadow segmentation/request identity + X002 seed; compile and final-window strong-gap defects were identified and corrected without activating the path.
- B006 raw-preserving Unicode policy and ICU boundary ownership foundations.
- B006 measured shadow layout engine: output-pixel safe geometry, explicit preferred/floor sizing, pinned font-profile contract, Android StaticLayout candidates, guarded raster alpha ink measurement, and fail-closed FITS/OVERFLOW/REVIEW_REQUIRED outcomes. No production renderer switch.
- B011 semantic display timing, binary cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 receipts: PREPARED→SENT→RECEIVED atomic phases, bounded codec, no credential fields, durable provider outcomes, explicit unknown-remote recovery for SENT, and exact epoch + manifest revision + active-entry revision + request-signature adoption fences.

## Partial / blocked
- Current B006 measured-engine checkpoint requires CI before any PASS claim.
- X003 still lacks a bundled experimental font asset, Android instrumentation, and real API29/API35-36/device readability/containment evidence.
- Controller-level revalidation/adoption of matching RECEIVED candidates into immutable entries remains incomplete.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001; snapshot/media activation remains BLOCKED_BY_X003/X004.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED (shadow measured engine implemented; no font/device experiment yet)
- X004: NOT_STARTED
- X005: HARNESS_READY (durable receipts + exact fences; crash/ENOSPC/device execution not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- The B004 strong-gap repair and later B006/B011/detailed-transport/X005 checkpoints require completed Actions results before being marked PASS.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated; the architecture requires a bundled pinned font but this checkpoint does not pretend a candidate was tested.
- `SENT` is intentionally conservative because it is durable before network invocation; a crash in the tiny pre-call window may be reported as unknown rather than risk a blind duplicate request.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Provider outcome dimensions are orthogonal; unknown remote outcome is not auto-reposted.
- Successful received provider text becomes durable before manifest adoption.
- Receipt adoption requires exact epoch + manifest revision + active entry revision + request signature match.
- Raw/manual text is preserved; display canonicalization is derived and narrow.
- Android/ICU owns shaping/boundaries; no homegrown Arabic shaper or arbitrary grapheme splitting.
- Accepted authored layout must prove full text coverage plus ink/box containment and respect the font floor; clipping/ellipsis/drop-word acceptance paths are absent.
- Manual text outranks machine candidates and survives retranslation/style/SRT/render changes.
- Experiment-gated production paths remain inactive without evidence.

## RESUME HERE
Inspect current CI and repair the earliest failing checkpoint first. If green, pin a licensed experimental Arabic font candidate and add X003 Android layout/raster instrumentation; independently, continue controller-level RECEIVED-receipt adoption under the exact SessionFencing/CAS contract without activating X001/X002-gated production behavior.
