# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `700855d8f646fa1702162802f136f209ab374248`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011 independent semantic timing/index/SRT primitives, while B003/B004 CI and experiment gates proceed.

## Completed
- B001 baseline freeze and regression ledger.
- B002 typed clocks, semantic ownership models, legacy bridge, and minimal atomic session-store foundations.
- B003 orthogonal provider classification + bounded retry/recovery foundations.
- B004 shadow semantic segmenter/planner and X002 seed harness; no production activation.
- B011 pure primitives: display timing preserves speech intervals and only extends into explicit known gaps; binary half-open cue index; semantic-unwrapped UTF-8 SRT construction with ORIGINAL_VIDEO vs EXPORTED_RANGE clock policy.

## Partial / blocked
- B003 production client integration awaits completed branch CI verification.
- B004 production activation remains BLOCKED_BY_X002.
- X001 clock mapping remains NOT_STARTED and B007 remains BLOCKED_BY_X001.
- B011 manual-edit UI/controller wiring and recovery harness remain incomplete.
- B006 Arabic/Unicode/layout foundation remains next independent safe slice.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (partial store/fencing foundations only; no crash/device run)
- X006: HARNESS_READY (CueIndex random-seek contract only; no performance measurement)

## Latest meaningful test status
- Frozen baseline Android CI `37648228258`: historical PASS.
- B003 run `37723687884`: Unit tests observed PASS; lint was running at last check.
- B004 run `37723984637`: started; final result not yet recorded.
- B011 tests are committed for CI; no PASS claim until Actions completes.

## Known deviations / repository facts
- Execution state was stale early in the campaign; it is now updated at coherent checkpoints.
- STT still infers seconds-vs-ms by magnitude; no speculative fix before X001.
- `SttAudioPreparer.sourceStartUs` is not yet an activated presentation mapping.
- Repository has no Gradle wrapper; CI uses installed Gradle.
- Display timing uses known STT speech gaps conservatively; it does not label unknown audio as silence.

## Active architectural decisions
- Semantic content, display timing, layout, and rendering remain separate ownership layers.
- Core V1 timing is `Long` microseconds and half-open intervals.
- SRT owns semantic unwrapped text and explicit clock policy, not bitmap line wrapping.
- Unknown remote provider outcome is never blindly re-posted.
- No experiment-gated production activation without evidence.

## RESUME HERE
Inspect current CI. Fix any regression first. If green, build B006 TextPolicy/Android boundary/layout foundations in shadow mode; do not switch production renderer before X003/X004.
