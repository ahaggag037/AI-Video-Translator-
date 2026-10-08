# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Recorded implementation HEAD: `861aadcb36cccee83d2c86e9a0c0a03b1efe6720` (branch creation point; later checkpoints update this field)
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
B001 — baseline freeze and durable execution state. Next: B002 typed semantic models, clocks, and lossless legacy bridge.

## Completed
- Repository access verified with push permission.
- Frozen branch verified at the exact required SHA.
- Historical Android CI run `37648228258` verified successful on the frozen SHA.
- Repository tree, build configuration, CI, production subtitle/STT/translation/audio-preparation sources, and all three baseline JVM test files inspected.
- Dedicated clean-room branch created from the frozen SHA; no abandoned experimental branch or Notion relay was consulted.
- P0-F regression ledger added.

## Partial / blocked
- B001 branch CI: pending first branch commit/push.
- X001 real provider/device clock evidence unavailable in this session; no timing interpretation change is activated.

## Experiments
- X001: NOT_STARTED
- X002: NOT_STARTED
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: NOT_STARTED
- X006: NOT_STARTED

## Latest meaningful test status
Historical baseline only: Android CI run `37648228258` succeeded on frozen SHA. No new-branch CI result yet.

## Known deviations / repository facts
- STT still infers seconds-vs-ms from numeric magnitude; this is a known defect and remains gated by X001.
- `SttAudioPreparer` records `sourceStartUs`, while legacy subtitle timing uses sample-relative milliseconds; production origin wiring requires evidence before activation.
- Repository has no Gradle wrapper; CI intentionally invokes installed Gradle 9.6.
- Existing tests: 21 JVM tests across three files.

## Active architectural decisions
- Semantic truth remains separate from display/layout truth.
- Core V1 timing uses typed `Long` microseconds; legacy milliseconds are bridged explicitly with checked conversion.
- Manual revisions and provider candidates will be modeled separately; style/layout changes must not trigger translation.
- No Room/Hilt/backend/FFmpeg/toolchain upgrade is introduced by default.
- Unknown remote provider outcome must not be blindly re-posted.

## RESUME HERE
Implement B002: add pure Kotlin semantic/clock models plus a lossless legacy bridge and focused JVM tests, without changing the production P0-F flow.
