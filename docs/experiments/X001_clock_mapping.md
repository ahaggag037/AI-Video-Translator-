# X001 — Baseline / Clock Mapping Evidence

State: HARNESS_READY_PARTIAL. No production STT-unit reinterpretation is enabled by this file.

## Canonical question
Does the P0-F baseline preserve the real sample/presentation clock, or do raw STT-unit inference and audio-origin loss create timing error?

The production legacy parser currently infers seconds versus milliseconds from numeric magnitude. X001 treats that inference as an observed defect candidate, not as a unit contract. Typed microseconds in the new core do not establish what unit the provider emitted.

## Evidence rules
- Raw provider offset fields, their schema location, and any documented/observed unit must be captured before normalization.
- API keys, Authorization headers, and private transcript/media are never committed.
- Personal source identity/hash may be recorded only in the user's local evidence bundle with consent; personal media remains outside Git.
- Repository fixtures are synthetic or explicitly publish-authorized and must say so.
- A historical source that cannot be recovered is recorded as unavailable; a new fixture must not be presented as the historical sample.
- No silent clamp, magnitude-based unit promotion, or zero-origin assumption can turn an unverified timeline into a verified one.

## Current synthetic falsifiers
`app/src/test/resources/translation_v1/x001_raw_timing_vectors.json` contains no personal content and establishes two deterministic facts:

1. Raw offsets `[20, 80]` are numerically compatible with both milliseconds and seconds. They therefore map to different microsecond intervals and cannot establish a unit by magnitude alone.
2. With an explicitly declared millisecond offset of `80 ms` and a verified presentation origin of `500000 us`, the presentation start is `580000 us`, not `80000 us`.

`X001ClockEvidenceTest` also freezes the N24 calculation used by the experiment: marker/drift tolerance is `max(local frame duration, 40 ms)`, and container-duration tolerance is `250 ms`.

These tests prove only arithmetic/evidence requirements. They do not prove NVIDIA's actual response unit or the actual source audio origin.

## Required capture bundle before X001 verdict
For an original source if available, plus a newly named synthetic MP4 with delayed audio start, nonzero clip origin, AAC audio and short speech/marker anchors, capture locally:

- source identity/fingerprint and selected range;
- exact build SHA, device model and Android API;
- redacted raw STT response schema/field paths and raw word offsets before normalization;
- explicit raw-offset unit evidence (schema/provider evidence), not numeric inference;
- PCM sample count/rate/channels and first/last relevant presentation PTS;
- known waveform/beep marker presentation anchors and mapped subtitle anchors;
- SRT clock policy/output and MP4 track codecs/durations;
- MP4 hash and representative start/middle/end frames where publication is authorized.

## N24 verdict
For known synthetic anchors:
- absolute marker mapping error must be `<= max(local frame duration, 40 ms)`;
- end-to-end drift must be within the same bound;
- container duration difference must be `<= 250 ms`.

If raw-unit evidence is absent or clock continuity/origin cannot be verified, the timeline remains `TIMELINE_UNVERIFIED`. Task 10 / production `SampleClockMapper` activation remains blocked. A failing experiment may change only the clock adapter/mapping contract needed by the evidence; it must not invent timestamps or clamp invalid mappings.

## Current gaps
- No original personal source/media was available to this clean-room run for inspection.
- No live NVIDIA STT canary was executed; no secret-dependent request is part of CI.
- No synthetic MP4/PCM/PTS marker capture has yet been executed on Android.
- Therefore X001 is not PASS and production `NvidiaSttClient.normalizeTimes` remains unchanged.
