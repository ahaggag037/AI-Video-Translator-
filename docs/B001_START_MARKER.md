# B001 — Regression Baseline Freeze

Status: DONE

The Control Tower V0 bootstrap is intentionally frozen. Product work has resumed.

B001 objective:
- preserve P0-F behavior as the regression baseline
- inspect existing unit tests/fixtures
- add only the minimum missing regression harness needed before semantic/time/session architecture changes
- do not redesign translation or rendering in this batch

Frozen source baseline:
`861aadcb36cccee83d2c86e9a0c0a03b1efe6720`

## Closure evidence

Existing focused JVM coverage already protects the most migration-sensitive executable P0-F contracts available without a device/provider run:

- `SubtitlePipelineTest.kt`
  - deterministic source-unit IDs/boundaries
  - no invented/clamped timing
  - translation ID matching
  - explicit sample-to-presentation offset mapping
  - active cue/SRT boundary agreement
  - UTF-8 Arabic SRT and injection rejection
- `NvidiaTranslationClientTest.kt`
  - frozen text-only NVIDIA request contract/model
  - provider envelope parsing
  - truncation/content-filter/tool-call rejection
  - legacy STT millisecond fixture
- `VideoMetadataTest.kt`
  - existing metadata baseline fixture

Device/media guarantees (video selection, audio decode, preview, hard-burn MP4, audio-track preservation) remain baseline contracts but are not falsely upgraded to automated proof.

## Known defects intentionally NOT frozen

See `docs/B001_REGRESSION_CONTRACTS.md`. In particular, STT time-unit guessing, missing audio-origin propagation, semantic segmentation weaknesses, fixed renderer sizing, preview/export layout divergence, and non-durable edits remain eligible for gated TV1 replacement.

## Next engineering action

B002 is open: add typed microsecond clock/semantic ownership models, an explicit legacy bridge, and the minimal durable single-writer session foundation without activating X001-gated clock behavior.
