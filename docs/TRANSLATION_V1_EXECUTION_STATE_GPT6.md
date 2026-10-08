# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before B004 checkpoint: `5f9fbb34e9c0a444588becc0cda8d43d17be1e4f`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B004 — semantic segmenter/planner shadow path and X002 harness seed. Production semantic activation remains blocked by X002 evidence.

## Completed
- B001: frozen regression baseline and clean-room branch established.
- B002: typed microsecond clocks, immutable semantic ownership models, lossless legacy bridge, minimal single-writer atomic session store foundations.
- B003: orthogonal provider outcome classifier and bounded retry/recovery policy foundations added; unknown remote outcome is not auto-reposted.
- B004 shadow: deterministic source segmentation foundation with exact word conservation, explicit presentation-word anchors, hard limits, punctuation/gap boundary scoring, and forced-fragment warnings.
- B004 planner: `nvidia-text-v1` request identity is canonical length-prefixed UTF-8; request vs acceptance signatures are separate; protocol v1 refuses silent few-shot prompt changes.
- X002 seed corpus/documentation added using synthetic fixtures only.

## Partial / blocked
- B003 production client integration remains pending CI confirmation; existing `NvidiaTranslationClient` request body is still the production path.
- B004 is shadow-only. B005 activation is BLOCKED_BY_X002.
- X001 real provider/device clock evidence unavailable; B007 clock activation remains BLOCKED_BY_X001.
- Session receipt persistence/controller and manual edit workflow are not yet complete.
- Arabic Android layout/font work (B006) remains independent and safe to start before X002 completes.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed corpus only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (partial persistence/fencing foundations; no crash/device execution)
- X006: NOT_STARTED

## Latest meaningful test status
- Frozen baseline Android CI run `37648228258`: historical PASS on exact baseline SHA.
- B003 branch CI run `37723687884`: observed IN_PROGRESS while B004 foundations were built.
- B004 tests are committed for CI but are not recorded PASS until Actions concludes.

## Known deviations / repository facts
- The execution-state file had been stale after early B002/B003 commits; it is now reconciled at this checkpoint.
- STT still infers seconds-vs-ms from numeric magnitude; known defect remains gated by X001.
- `SttAudioPreparer` records `sourceStartUs`; production origin wiring still requires X001 evidence.
- Repository has no Gradle wrapper; CI uses installed Gradle.
- Shadow segmenter takes explicit presentation anchors rather than inventing a mapping from raw STT time.

## Active architectural decisions
- Semantic truth is separate from display/layout truth.
- Core V1 timing uses typed `Long` microseconds.
- Provider outcome dimensions remain orthogonal.
- `nvidia-text-v1` remains text-only and one-unit-per-request until X002 says otherwise.
- No previous-target translation chain is allowed.
- Minimal single-writer persistence remains preferred; no Room/Hilt/backend/FFmpeg/toolchain upgrade.
- Experiment-gated production behavior is not activated without evidence.

## RESUME HERE
Inspect B003/B004 CI. If green, continue independent B006 Android text-policy/layout foundations and B011 semantic manual-edit/SRT primitives while X001/X002 remain gated.
