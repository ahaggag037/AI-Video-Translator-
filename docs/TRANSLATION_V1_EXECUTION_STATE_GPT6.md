# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `31b3f861146b582d091676358bccd970dfb485c6`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B003 detailed NVIDIA transport integration plus independent B004/B006/B011 foundations. Legacy production `translate()` remains unchanged; typed transport is additive for later durable session/controller use.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 provider outcome classifier/retry policy verified by full Android CI run `37723687884`: unit tests, lint, debug APK, signature/checksum and artifacts all succeeded.
- B003 detailed NVIDIA transport entry point uses the exact existing request body and returns structured outcomes; uncertain OkHttp failures fail closed as unknown remote outcome rather than inviting automatic repost.
- B004 deterministic shadow segmentation/request planning + X002 seed harness; compile regression was detected and corrected at `2e055752...`.
- B006 Unicode/boundary/layout ownership foundations in shadow mode.
- B011 display timing, cue index, semantic SRT, manual-edit preservation and stale-response fencing foundations.

## Partial / blocked
- The detailed transport is not wired into the user-visible translation controller yet; existing P0-F wrapper remains the active production behavior.
- B004/B006/B011 latest workflows remain pending/in progress at this checkpoint; no PASS claim until Actions completes.
- B005 remains BLOCKED_BY_X002.
- B007 remains BLOCKED_BY_X001.
- X003 has no pinned-font/raster/device evidence.
- Session request-receipt persistence/recovery scan and UI controller wiring remain incomplete.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (codec/manual/fence foundations; no crash/ENOSPC execution)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS on frozen SHA.
- B003 CI `37723687884`: PASS end-to-end for repository CI workflow.
- Fixed B004 CI `37724315502`: unit-test stage still in progress at last check.
- B006 CI `37724433047`: unit-test stage in progress at last check.
- This detailed-transport checkpoint is not marked PASS until its own Actions run completes.

## Known deviations / repository facts
- Early execution-state staleness was corrected.
- STT timing-unit inference remains unchanged pending X001.
- `sourceStartUs` is not treated as verified presentation mapping without X001.
- Exact font family/weight remains experiment-gated; no permanent font commitment is hard-coded.
- HTTP `Retry-After` delta-seconds are honored by the detailed adapter; HTTP-date form is intentionally not guessed yet.

## Active architectural decisions
- Semantic truth is independent of display truth.
- Provider outcomes remain orthogonal; unknown remote outcome is not auto-reposted.
- `nvidia-text-v1` payload remains text-only, one semantic unit per request, system `en-ar`.
- Raw/manual text is preserved; display canonicalization is derived.
- Android/ICU owns shaping and boundaries.
- Manual edits survive style/layout/retranslation.

## RESUME HERE
Inspect current branch CI and repair any regression first. Then add durable RequestReceipt PREPARED/SENT/RECEIVED persistence and recovery adoption, without activating semantic segmentation or clock changes that remain experiment-gated.
