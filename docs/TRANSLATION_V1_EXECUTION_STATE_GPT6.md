# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `0370727c3d5bd32f244df05fdb7dc66f275b78bf`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B004 deterministic translation validation plus B006/X005 shadow foundations. No experiment-gated production activation.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy fully verified by CI run `37723687884`; additive detailed NVIDIA transport is present without replacing the legacy production wrapper.
- B004 shadow segmentation/planning + X002 seed; compile and final-window strong-gap defects corrected; deterministic validation now checks Unicode structure, exact URL/email preservation, numeric fact multisets/percent magnitude, length risk and target-script suspicion without claiming semantic correctness.
- B006 Unicode/ICU ownership plus measured Arabic layout shadow engine foundations; production renderer remains unchanged and X003-gated.
- B011 display timing, binary cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 durable PREPARED/SENT/RECEIVED receipts with exact epoch/manifest/entry/request fence data.

## Partial / blocked
- Translation validator intentionally cannot prove number-to-entity association or general semantic fidelity; those remain X002 human-review concerns.
- Controller-level revalidation/adoption of matching RECEIVED candidates remains incomplete.
- Current later CI runs remain in progress/queued; no PASS claim beyond verified B003 until observed.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 still lacks pinned font asset/device readability evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed corpus + deterministic integrity validator; no live/human verdict)
- X003: HARNESS_READY (measured shadow engine foundation; no pinned-font/device verdict)
- X004: NOT_STARTED
- X005: HARNESS_READY (durable receipts + exact fences; crash/ENOSPC/device execution not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- Fixed B004/B006/B011/detailed-transport/X005 workflows were still running or queued at this checkpoint.
- New validator tests are committed for CI and are not marked PASS yet.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains experiment-gated.
- `SENT` receipts are deliberately conservative unknown-remote state after process loss.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Deterministic validation identifies integrity risks but never exposes a `semanticCorrect=true` claim.
- Provider outcomes are orthogonal; unknown remote outcomes are not auto-reposted.
- Receipt adoption requires exact frozen fence match and later candidate validation.
- Manual text outranks machine candidates and survives retranslation/style/SRT/render changes.
- Layout acceptance is fail-closed; no clipping/ellipsis/drop-word success path.

## RESUME HERE
Inspect current CI and repair the earliest failure first. If green, connect RECEIVED receipt recovery to validation + immutable candidate persistence under the exact fence, while keeping semantic pipeline activation blocked on X002.
