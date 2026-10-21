# X001 — Baseline / Clock Mapping Evidence

State: PROVIDER_UNIT_DOCUMENTED / ANDROID_ORIGIN_EVIDENCE_PENDING. No production presentation-clock activation is enabled by this file alone.

## Canonical question
Does the product preserve the real media presentation clock when provider word offsets are mapped back onto the selected video timeline?

## Evidence rules
- Raw provider offset fields, their schema location, and documented/observed unit must be captured before any mapping.
- API keys, Authorization headers, and private transcript/media are never committed.
- Personal source identity/hash may be recorded only in the user's local evidence bundle with consent; personal media remains outside Git.
- Repository fixtures are synthetic or explicitly publish-authorized and must say so.
- A historical source that cannot be recovered is recorded as unavailable; a new fixture must not be presented as the historical sample.
- No silent clamp, magnitude-based unit promotion, or zero-origin assumption can turn an unverified timeline into a verified one.

## Provider unit evidence — 2026-10-21
NVIDIA's current Speech NIM ASR gRPC API reference documents `WordInfo.start_time` and `WordInfo.end_time` as **time offsets in milliseconds relative to the beginning of the audio** when word offsets are enabled:

- https://docs.nvidia.com/nim/speech/26.10.0/reference/api-references/asr/protos.html

The hosted Parakeet CTC 1.1B model API page documents the exact HTTP endpoint/profile used by `NvidiaSttWireContract`, including `word_time_offsets=True`:

- https://build.nvidia.com/nvidia/parakeet-ctc-1_1b-asr/api

The production parser therefore no longer uses numeric magnitude to infer seconds versus milliseconds. `NvidiaSttParserContract` is versioned as `nvidia-stt-riva-ms-parser-v2`, and short values such as `80..100` remain 80..100 ms rather than being multiplied by 1000.

This closes the **provider-unit documentation gap**. It does **not** prove the Android media-origin mapping or end-to-end drift for the exact hosted HTTP response on the target device.

## Current synthetic falsifiers
`app/src/test/resources/translation_v1/x001_raw_timing_vectors.json` establishes two deterministic facts:

1. Raw offsets `[20, 80]` cannot establish a unit from numeric magnitude alone. This remains a regression falsifier for any future heuristic parser even though the current provider documentation now supplies the unit contract.
2. With a declared millisecond offset of `80 ms` and a verified presentation origin of `500000 us`, the presentation start is `580000 us`, not `80000 us`.

`X001ClockEvidenceTest` freezes the N24 calculation used by the experiment: marker/drift tolerance is `max(local frame duration, 40 ms)`, and container-duration tolerance is `250 ms`.

## Required capture bundle before X001 PASS
For an original source if available, plus a newly named synthetic MP4 with delayed audio start, nonzero clip origin, AAC audio and short speech/marker anchors, capture locally:

- source identity/fingerprint and selected range;
- exact build SHA, device model and Android API;
- redacted raw hosted STT response schema/field paths and raw word offsets before normalization;
- provider-unit evidence reference plus observed hosted response shape;
- PCM sample count/rate/channels and first/last relevant presentation PTS;
- known waveform/beep marker presentation anchors and mapped subtitle anchors;
- SRT clock policy/output and MP4 track codecs/durations;
- MP4 hash and representative start/middle/end frames where publication is authorized.

## N24 verdict
For known synthetic anchors:
- absolute marker mapping error must be `<= max(local frame duration, 40 ms)`;
- end-to-end drift must be within the same bound;
- container duration difference must be `<= 250 ms`.

If clock continuity/origin cannot be verified on Android, the timeline remains `TIMELINE_UNVERIFIED`. Production presentation mapping must not invent timestamps or clamp invalid mappings.

## Current gaps
- Provider offset unit is now documented as milliseconds relative to submitted audio.
- No live hosted NVIDIA canary has yet proven that the exact HTTP endpoint response shape used in production matches the documented WordInfo timing contract on the release build.
- No synthetic MP4/PCM/PTS marker capture has yet been executed on the target Android device.
- Therefore X001 is still not PASS; the remaining blocker is **hosted-response confirmation plus Android media-origin/continuity evidence**, not a guessed unit.
