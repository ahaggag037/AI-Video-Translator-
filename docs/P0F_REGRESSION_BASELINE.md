# P0-F Regression Baseline

Frozen regression anchor: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.

This document freezes intentional P0-F behavior without fossilizing known defects. The clean-room implementation branch must not move or modify the frozen branch.

## Verified repository facts

- Historical Android CI run `37648228258` / run #59 completed successfully on the exact frozen SHA.
- CI commands are `gradle --stacktrace testDebugUnitTest`, `gradle --stacktrace lintDebug`, and `gradle --stacktrace assembleDebug`, followed by APK existence/signature/checksum checks.
- Baseline has 21 JVM tests across `SubtitlePipelineTest`, `NvidiaTranslationClientTest`, and `VideoMetadataTest`.
- NVIDIA translation transport is one text request per source unit, model `nvidia/riva-translate-4b-instruct-v2`, system content `en-ar`, temperature 0, max_tokens 1024, stream false.
- Translation parser intentionally accepts missing `finish_reason`, missing role, empty `tool_calls`, scalar text, and supported text-part arrays.
- Subtitle intervals are half-open for active-cue lookup; SRT uses ASCII timestamps and UTF-8 text.
- Explicit sample-to-presentation offset mapping exists in `SubtitlePipeline`, but production wiring and STT raw-unit interpretation remain experiment-gated defects.

## Do not freeze as required behavior

- semantic splits caused only by legacy size/duration thresholds;
- STT seconds-vs-ms inference from numeric magnitude;
- loss of `SttAudioProfile.sourceStartUs` in legacy call paths;
- fixed-size subtitle rendering, clipping risk, or preview/export layout divergence;
- in-memory-only translation ownership;
- whitespace flattening as the future semantic SRT ownership model.

## B001 acceptance

B001 is complete when the dedicated branch is created from the exact anchor, the existing regression contracts remain present, and the branch CI has been observed after the first implementation checkpoint. Device/media evidence is not fabricated: X001 remains gated until a real or synthetic capture harness can produce clock/audio evidence.
