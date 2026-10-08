# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD before this checkpoint: `a2efed20a0552e7e697539f5a5f726f4cb913964`
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B011 semantic durability foundations: manual revision preservation, display timing/index/SRT, and stale response fencing. Production UI/session resume wiring remains later.

## Completed
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic session-store foundation.
- B003 orthogonal provider outcome + retry/recovery foundation; B003 unit tests observed PASS in Actions.
- B004 shadow semantic segmenter/planner + X002 seed harness; no production activation.
- B011 display timing, binary cue index, explicit-clock semantic SRT primitives.
- B011 manual edit policy: retranslation creates machine candidates without overwriting effective manual text; source changes retain manual history and mark REBASE_REQUIRED; style/layout changes do not invalidate translation.
- Session fencing foundation rejects stale epoch, stale manifest/entry revision, and mismatched request signature before adoption.

## Partial / blocked
- B003 full CI conclusion still pending at the last inspection; production client integration not yet activated.
- B004 activation remains BLOCKED_BY_X002.
- B007 clock fix remains BLOCKED_BY_X001.
- Session receipt file persistence and process-death recovery scan are not yet complete.
- B006 Arabic/Unicode/layout foundations remain independent safe work.

## Experiments
- X001: NOT_STARTED
- X002: HARNESS_READY (seed only; no live/human verdict)
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: HARNESS_READY (codec/manual/fence foundations; no Android crash/ENOSPC run)
- X006: HARNESS_READY (CueIndex correctness fixture only; no device performance measurement)

## Latest meaningful test status
- Frozen baseline Android CI `37648228258`: historical PASS.
- B003 run `37723687884`: Unit tests PASS; lint was running at last inspection.
- B004 run `37723984637`: Unit tests were running at last inspection.
- B011 commits are queued through normal branch CI; do not treat as PASS until Actions completes.

## Known deviations / repository facts
- Execution state was stale early in the campaign and is now updated at coherent checkpoints.
- STT still guesses seconds-vs-ms; no timing interpretation change before X001.
- `sourceStartUs` exists but is not yet promoted into an unverified production mapping.
- No Gradle wrapper; CI uses installed Gradle.

## Active architectural decisions
- Manual semantic truth outranks machine candidate and survives style/SRT/render changes.
- Retranslation is additive candidate history, not destructive replacement.
- Async response adoption is fenced by epoch + expected revisions + request signature.
- Core time uses half-open `Long` microseconds.
- SRT is semantic-unwrapped with explicit ORIGINAL_VIDEO / EXPORTED_RANGE clock ownership.
- No experiment-gated production activation without evidence.

## RESUME HERE
Inspect current branch CI and repair failures first. Then build B006 TextPolicy/Android boundary/layout foundations in shadow mode, without changing the default preview/export renderer before X003/X004.
