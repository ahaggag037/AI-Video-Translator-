# X001 — Baseline / Clock Mapping Evidence

State: **HARNESS_READY_PARTIAL / FAIL_CLOSED**. X001 is not PASS. Production hosted STT transport no longer turns raw provider offsets into application timing while their exact HTTP unit/origin contract remains unverified.

## Canonical question
Does the P0-F baseline preserve the real sample/presentation clock, or do raw STT-unit inference and audio-origin loss create timing error?

Typed microseconds in the application core do not establish what unit the provider emitted. Timing authority therefore requires evidence for both sides of the mapping:

1. provider offset representation/unit/origin for the exact hosted request/response profile; and
2. the prepared WAV sample-zero to media presentation-origin mapping on Android.

## Evidence rules
- Raw provider offset fields, schema location, JSON scalar representation, and documented/observed unit must be captured before normalization.
- API keys, Authorization headers, raw private transcripts, and private media are never committed.
- Personal source identity/hash may be recorded only in the user's local evidence bundle with consent; personal media remains outside Git.
- Repository fixtures are synthetic or explicitly publish-authorized and must say so.
- A historical source that cannot be recovered is recorded as unavailable; a new fixture must not be presented as the historical sample.
- No silent clamp, magnitude-based unit promotion, rounding, schema precedence, or zero-origin assumption can turn an unverified timeline into a verified one.
- Provider timing authority must be bound to the exact accepted response and exact uploaded WAV bytes, not merely to a model name or field name.

## Current code boundary

### Hosted transport is fail-closed
`NvidiaSttClient.transcribeEnglishSample()` and the durable `bindDetailedResponse()` path use `parseResponseWithoutTimingAuthority()`. They preserve accepted word text/confidence but expose `startMs/endMs = null` until X001 authority is established. The historical magnitude heuristic remains only behind internal `parseResponse()` as a regression comparator and is not called by hosted transport.

`NvidiaSttTimingEvidenceInspector` remains the diagnostic pre-normalization capture path. It preserves the verbatim response text/hash plus timing schema/path/field/type observations without selecting a unit or origin.

### Explicit authority mapper
`NvidiaSttAuthoritativeTimingMapper` has no default timing contract. Mapping requires all of the following:

- the exact `NvidiaSttRequestProfile`;
- the current fail-closed parser identity and a successful HTTP status;
- `rawResponseSha256` equality between transport observation and timing evidence;
- `sampleSha256` equality with the exact prepared WAV bytes used for the request;
- exactly one observed word schema source with the contracted schema path, start/end fields, and JSON scalar type;
- an explicit provider offset unit and `UPLOADED_AUDIO_START` origin contract backed by a named evidence profile;
- exact word sequence agreement with the accepted transport result;
- a `VERIFIED_AFFINE` `SampleClockMap` with a nonblank evidence profile.

Decimal offsets are converted exactly to microseconds with no rounding. Positive half-open intervals are required and overlapping/unsorted provider word intervals are rejected rather than clamped.

### Presentation origin in the preparer
`SttAudioPreparer` records the selected audio track's first `MediaExtractor.sampleTime` as `sourceStartUs`, then rebases decoder input timestamps by subtracting that value before writing the STT WAV. Thus the intended affine relation is:

`presentation_time_us = sourceStartUs + prepared_sample_offset_us`

That code relation is not, by itself, device proof. `X001PresentationClockInstrumentedTest` reuses the existing synthetic 440 Hz AAC fixture, remuxes its encoded packets on Android with a `+500000 us` PTS offset, independently observes the first packet PTS with `MediaExtractor`, runs the production `SttAudioPreparer`, checks that the prepared WAV starts with the tone rather than 500 ms of inserted silence, and checks the nonzero affine mapping. The test must run successfully on an Android device/emulator before it counts as device evidence.

## Provider documentation evidence
NVIDIA's published ASR representations are not timing-interchangeable:

- Speech NIM gRPC `WordInfo.start_time` / `end_time` documentation defines offsets in **milliseconds relative to the beginning of the audio**: <https://docs.nvidia.com/nim/speech/26.10.0/reference/api-references/asr/protos.html>
- Speech NIM Realtime API `words_info.words[].start_time` / `end_time` documentation defines the values in **seconds**: <https://docs.nvidia.com/nim/speech/latest/reference/api-references/asr/realtime-asr.html>
- The application uses the hosted HTTP `/v1/audio/transcriptions` NVCF profile. HTTP request documentation confirms the multipart endpoint shape but does not, by itself, establish that the exact hosted response returned to this profile uses either of the representations above: <https://docs.nvidia.com/nim/speech/26.10.0/reference/api-references/asr/http-asr.html>

Therefore neither field names nor numeric magnitude can establish the app's hosted HTTP timing unit. An actual hosted response bound to the exact request/sample (or exact provider documentation for that HTTP response representation) is still required before constructing a production timing contract.

## Current deterministic regressions
- `X001ClockEvidenceTest`: ambiguous numeric units, nonzero-origin arithmetic, and N24 tolerances.
- `NvidiaSttAuthoritativeTimingTest`: exact explicit-unit conversion, exact response/sample binding, profile/schema/field/type drift rejection, unverified-clock rejection, no sub-microsecond rounding, overlap rejection, and half-open cue boundary behavior.
- `NvidiaSttDetailedEvidenceParserTest`: accepted text is preserved while interpreted timing remains absent and raw evidence remains separate.
- `NvidiaSttTransportObservationTest`: production response binding exposes no timing for second-scale, millisecond-scale, or other ambiguous magnitudes.
- `X001PresentationClockInstrumentedTest`: nonzero Android presentation-origin falsifier; **execution pending**.

These tests and code guards prove fail-closed behavior and mapping requirements. They do not prove the exact current hosted NVCF response unit/schema or substitute for an Android execution result.

## Required capture bundle before X001 verdict
For a publish-authorized synthetic source (and an original source only if separately available/authorized), capture:

- exact build SHA, device model and Android API;
- exact prepared WAV SHA-256/size/sample rate/frame count;
- redacted hosted STT response schema/field paths/types and raw timing offsets before normalization, plus response SHA-256;
- explicit hosted raw-offset unit/origin evidence, not numeric inference;
- first relevant media presentation PTS and prepared sample-zero relation;
- known waveform/marker presentation anchors and mapped subtitle anchors;
- N24 marker/drift measurements where applicable.

No API key, Authorization header, raw private transcript, or private media belongs in GitHub evidence.

## N24 verdict boundary
For known synthetic anchors:
- absolute marker mapping error must be `<= max(local frame duration, 40 ms)`;
- end-to-end drift must be within the same bound;
- container duration difference must be `<= 250 ms`.

If raw hosted-unit/origin evidence is absent or clock continuity/origin cannot be verified, the timeline remains `TIMELINE_UNVERIFIED` and authoritative production timing activation remains blocked.

## Current gaps
- No hosted NVIDIA NVCF response has been captured on this branch with its exact response hash bound to the exact uploaded WAV hash; exact HTTP response schema/unit/origin therefore remains PENDING.
- The delayed-origin Android instrumented falsifier has been added but has not yet produced device/API execution evidence on this branch.
- No original personal source/media was used or committed.
- Preview/export currently contains a hard-coded zero sample-start assumption outside W1/X001 scope; verified nonzero-origin activation must not bypass that boundary.
- Therefore X001 remains **not PASS** despite the fail-closed production guard and expanded regression harness.
