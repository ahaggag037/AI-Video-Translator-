# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled code checkpoint: `dc695ae3823dfd8dcecd533d65f5fd77f22f6b93`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011/X005 recovery hardening layered on B004 deterministic validation, exact request identity, and session fencing. No experiment-gated production activation.

## Completed / implemented
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy fully verified by CI run `37723687884`; additive detailed NVIDIA transport preserves the legacy request contract.
- B004 shadow segmentation/planning + X002 seed and deterministic integrity validation; known segmenter defects found during CI-oriented work were corrected without activating the path.
- B006 Unicode/ICU ownership plus measured Arabic layout shadow engine foundations; production renderer unchanged. SDK-36 lint/constant repair at `1f3d1e95c67776f1518ded7c8cc834fb8d8d4bbf` is fully green in CI run `37726381108`.
- B011 display timing, cue index, semantic SRT, manual-edit preservation and stale-response fencing.
- X005 durable receipts freeze epoch + manifest revision + active-entry revision + exact provider request identity.
- X005 recovery planner revalidates a self-consistent exact request plan before considering a RECEIVED candidate and performs no provider POST.
- X005 recovered candidate adoption runs inside the store single-writer boundary, preserves manual semantic truth, writes immutable history before atomic manifest advancement, and uses deterministic receipt-derived revision IDs for pre-manifest crash retry idempotency.
- Sol independent audit `SOL-X005-AUDIT-001` was read and acted on: stale SENT receipts are quarantined rather than offered retry (`f3b62efbe99b98f2100f7061c9bffa659a9b465d`), and the V1 durable-attempt executor persists SENT before caller transport can execute (`dc695ae3823dfd8dcecd533d65f5fd77f22f6b93`).

## Partial / blocked
- X005 Android crash injection, ENOSPC/corruption, real provider-boundary submission-count proof, and device/emulator execution remain outstanding; X005 is not PASS.
- Newest X005 CI runs are still in progress at this checkpoint; do not promote their status until observed complete.
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 lacks pinned-font/device readability evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed corpus + deterministic integrity validator; no live/human verdict)
- X003: HARNESS_READY (measured shadow engine foundation; no pinned-font/device verdict)
- X004: NOT_STARTED
- X005: HARNESS_READY (core receipt/fence/recovery/adoption/send-order invariants implemented; crash/ENOSPC/device execution not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- B006 repair CI `37726381108` @ `1f3d1e95c67776f1518ded7c8cc834fb8d8d4bbf`: PASS end-to-end (unit tests, lint, debug APK, APK existence, signature/checksum, verification reports, artifact upload).
- Newer request-plan binding, atomic recovery adoption, deterministic recovery-ID, stale-SENT, and durable-send-order workflows were still running at this checkpoint.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated.
- A current persisted SENT attempt is deliberately conservative UNKNOWN_REMOTE_OUTCOME after process loss and requires an explicit retry decision; a stale SENT fence is quarantined and offers no retry.
- V1 intentionally keeps session-global `manifest.revision` in the adoption fence. An unrelated unit commit can therefore false-stale an in-flight response; this is an explicit conservative liveness/cost tradeoff, not an unnoticed race.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Deterministic validation finds integrity risks but does not claim general semantic correctness.
- Received candidates are revalidated locally before adoption; recovery itself performs no provider POST.
- Exact epoch + manifest revision + active entry revision + request signature fence stale adoption.
- Request-plan self-consistency binds exact source/profile/examples to request identity before recovery validation.
- Manual text outranks machine candidates and survives recovered/retranslated machine history.
- The V1 durable-attempt executor makes PREPARED→SENT persistence precede the first caller transport invocation.
- Layout acceptance is fail-closed.

## RESUME HERE
Inspect the newest X005 CI runs and repair the earliest concrete failure first. If green, build/execute the remaining X005 falsification harness in this order: durable-send submission counts, forced crash after immutable entry publication before manifest advancement, stale callback after epoch bump, then ENOSPC/corruption/device execution. Do not mark X005 PASS until those executions are evidenced.
