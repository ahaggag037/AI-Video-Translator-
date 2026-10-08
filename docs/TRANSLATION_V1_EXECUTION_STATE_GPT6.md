# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `db1b8016b1d0f74f7465aa7520b071e54bd4bbb2`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011/X005 recovery planning layered on B004 deterministic validation and exact receipt fences. No experiment-gated production activation.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy fully verified by CI run `37723687884`; additive detailed NVIDIA transport preserves the legacy request contract.
- B004 shadow segmentation/planning + X002 seed and deterministic integrity validation; known segmenter defects found during CI-oriented work were corrected without activating the path.
- B006 Unicode/ICU ownership plus measured Arabic layout shadow engine foundations; production renderer unchanged.
- B011 display timing, cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 durable receipts freeze exact adoption dependencies; `ReceiptRecoveryPlanner` now maps PREPARED/SENT/RECEIVED to safe local actions, revalidates matching candidates without network, blocks stale receipts, and never turns SENT into automatic repost.

## Partial / blocked
- Single-writer manifest adoption of a validated recovered candidate remains to be implemented/tested atomically.
- Current newer CI runs remain in progress/queued; no PASS claim beyond verified B003 until observed.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 lacks pinned-font/device readability evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed corpus + deterministic integrity validator; no live/human verdict)
- X003: HARNESS_READY (measured shadow engine foundation; no pinned-font/device verdict)
- X004: NOT_STARTED
- X005: HARNESS_READY (durable receipts + exact fences + recovery planner; crash/ENOSPC/device execution not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- Newer B004/B006/B011/detailed-transport/X005 workflows were still running/queued at this checkpoint.
- Recovery planner tests are committed for CI and are not marked PASS yet.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated.
- A persisted SENT attempt is deliberately conservative UNKNOWN_REMOTE_OUTCOME after process loss.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Deterministic validation finds integrity risks but does not claim general semantic correctness.
- Received candidates are revalidated locally before adoption; recovery itself performs no provider POST.
- Exact epoch + manifest revision + active entry revision + request signature fence stale adoption.
- Manual text outranks machine candidates and survives retranslation/style/SRT/render changes.
- Layout acceptance is fail-closed.

## RESUME HERE
Inspect current CI and repair the earliest failure first. If green, implement atomic single-writer adoption of a locally validated RECEIVED candidate into immutable translation history while preserving any manual revision.
