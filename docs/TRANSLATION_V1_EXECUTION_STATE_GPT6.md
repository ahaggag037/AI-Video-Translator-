# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `2e055752b6c3a53b69585f7c76e96a12f78029ef`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B006 safe-pre-experiment Unicode/boundary/layout ownership foundations. Production typography/rendering remains unchanged and gated by X003/X004.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 orthogonal provider classification and bounded retry/recovery foundations; unit-test stage observed PASS.
- B004 deterministic shadow segmentation/request planning + X002 seed harness; compile regression in `SourceSegmenter` was detected and corrected at `2e055752...` without activating the path.
- B011 display timing, cue index, semantic SRT, manual-edit preservation, stale-response fencing foundations.
- B006 raw-preserving Unicode policy, protected-span NFC derivation, Android ICU boundary adapter, and fail-closed layout result/geometry contracts.

## Partial / blocked
- Current CI for newer slices is pending/queued; fix any regression before activation.
- B005 remains BLOCKED_BY_X002.
- B007 remains BLOCKED_BY_X001.
- X003 is NOT_STARTED: no pinned font/raster/ink/device evidence yet.
- Session receipt persistence/recovery scan and UI controller wiring remain incomplete.
- Snapshot/preview/export adapters remain gated by X003/X004.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (codec/manual/fence foundations; no crash/ENOSPC execution)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Baseline CI `37648228258`: historical PASS on frozen SHA.
- B003 run `37723687884`: Unit tests PASS at last observation; final workflow result pending at that checkpoint.
- B004 compile regression was observed and fixed in commit `2e055752...`; follow-up CI must prove the repair.
- Later B011/B006 tests are not marked PASS until Actions completes.

## Known deviations / repository facts
- Early execution-state staleness was corrected.
- STT timing-unit inference remains unchanged pending X001.
- `sourceStartUs` is not treated as verified presentation mapping without X001.
- Exact font family/weight remains experiment-gated per architect delta; no permanent font commitment is hard-coded.

## Active architectural decisions
- Semantic truth is independent of display truth.
- Raw/manual text is preserved; canonical display text is derived and narrowly normalized.
- Android/ICU owns shaping and legal boundary rules; no homegrown Arabic shaping/grapheme engine.
- Accepted layout must contain ink + box inside safe area; no clipping/ellipsis/drop-word acceptance path.
- Manual edits survive style/layout and retranslation.
- Unknown remote outcome is never auto-reposted.

## RESUME HERE
Inspect current CI and repair failures first. Then implement X003 StaticLayout/ink/raster harness with a pinned experimental font candidate, or continue request-receipt recovery if device/font work is unavailable.
