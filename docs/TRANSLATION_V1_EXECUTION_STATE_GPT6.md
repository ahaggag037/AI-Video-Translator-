# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled code checkpoint: `c798da44be5cfc119c3727753801e0ac3059c48c`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011/X005 recovery hardening layered on B004 deterministic validation, exact provider request identity, and session fencing. No experiment-gated production activation.

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
- X005 durable-send path persists PREPARED and revalidated SENT state before caller transport can execute; production SENT persistence is routed through `TranslationSessionStore.markSentIfCurrent`.
- X005 Android recovery harnesses for send fencing, crash/replay, storage faults, and attempt-ledger behavior are present, with dedicated workflow `.github/workflows/x005-android-recovery.yml`.
- Sol independent audits were read and acted on: stale SENT receipts are quarantined rather than offered retry (`f3b62efbe99b98f2100f7061c9bffa659a9b465d`); durable send ordering and current-fence revalidation are enforced in the production executor/store path; crash/replay and storage-fault harnesses were added.
- Request-plan identity now includes the exact NVIDIA endpoint. At `c798da44be5cfc119c3727753801e0ac3059c48c`, the durable NVIDIA path serializes model/system/max-tokens/temperature/stream from the signed plan and sends to the signed endpoint after contract validation; tests compare the signed contract to the serialized body and reject endpoint drift.

## Partial / blocked
- X005 real device/emulator execution and current workflow results remain outstanding at this checkpoint; X005 is not PASS.
- Android CI run `37729766870` and X005 Android Recovery run `37729766863` for `c798da44be5cfc119c3727753801e0ac3059c48c` are in progress; do not promote their status until observed complete.
- The preceding checkpoint `8ba42efb654d29685a64d40fb936a26bfc1faadb` is fully green in Android CI run `37729060025` (unit tests, lint, debug APK, APK verification/signature/checksum, verification reports, artifact upload).
- B005 remains BLOCKED_BY_X002; B007 remains BLOCKED_BY_X001.
- X003 lacks pinned-font/device readability evidence; X004 remains NOT_STARTED.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed corpus + deterministic integrity validator; no live/human verdict)
- X003: HARNESS_READY (measured shadow engine foundation; no pinned-font/device verdict)
- X004: NOT_STARTED
- X005: HARNESS_READY (core receipt/fence/recovery/adoption/send-order invariants and Android falsification harnesses implemented; current device/emulator workflow not yet observed complete, so not PASS)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS.
- B003 CI `37723687884`: PASS end-to-end.
- B006 repair CI `37726381108` @ `1f3d1e95c67776f1518ded7c8cc834fb8d8d4bbf`: PASS end-to-end.
- X005 checkpoint CI `37729060025` @ `8ba42efb654d29685a64d40fb936a26bfc1faadb`: PASS end-to-end.
- Current wire/signature checkpoint `c798da44be5cfc119c3727753801e0ac3059c48c`: Android CI `37729766870` and X005 Android Recovery `37729766863` still running at reconciliation time.

## Known deviations / repository facts
- STT seconds-vs-ms inference remains unchanged pending X001.
- `sourceStartUs` is not a verified presentation mapping until X001.
- Exact font family/weight remains X003-gated.
- A current persisted SENT attempt is deliberately conservative UNKNOWN_REMOTE_OUTCOME after process loss and requires an explicit retry decision; a stale SENT fence is quarantined and offers no retry.
- V1 intentionally keeps session-global `manifest.revision` in the adoption fence. An unrelated unit commit can therefore false-stale an in-flight response; this is an explicit conservative liveness/cost tradeoff, not an unnoticed race.
- Commit `720f35821ebcf194faa8bcac34b57c8972a4823b` is a tree-identical no-op created while reconciling an already-fixed TextPolicy compile regression; history is preserved and no force rewrite is planned.

## Active architectural decisions
- Semantic/display/timing/persistence/renderer ownership remain separated.
- Deterministic validation finds integrity risks but does not claim general semantic correctness.
- Received candidates are revalidated locally before adoption; recovery itself performs no provider POST.
- Exact epoch + manifest revision + active entry revision + request signature fence stale adoption.
- Request-plan self-consistency binds exact source/profile/examples/provider endpoint to request identity before recovery validation.
- Manual text outranks machine candidates and survives recovered/retranslated machine history.
- The V1 durable-attempt executor makes PREPARED→current-fenced SENT persistence precede the first caller transport invocation.
- The durable NVIDIA serializer consumes the signed request plan rather than an independent provider-constant copy; unsupported endpoint/profile drift is rejected before durable transport.
- Layout acceptance is fail-closed.

## RESUME HERE
First inspect Android CI `37729766870`, X005 Android Recovery `37729766863`, and the clean-room relay review for `c798da44be5cfc119c3727753801e0ac3059c48c`. Repair the earliest concrete failure before adding dependent X005/provider work. If both workflows are green and review finds no contract defect, preserve the checkpoint and proceed to the next experiment-backed X005 evidence gap; otherwise continue only unaffected independent work. Do not mark X005 PASS without observed workflow/device evidence.
