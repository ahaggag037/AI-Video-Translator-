# X006 — Production Performance Pipeline Architecture

Status: IMPLEMENTATION PLAN / NOT ACCEPTANCE EVIDENCE

This document defines the production performance/observability direction for the final Android AI Video Translator. It is not a field-test-only workaround and does not promote X001 timing interpretation or X002 semantic translation behavior.

## Product requirement

The final app must not make the user wait behind opaque serial stages. Long-video work must be bounded, resumable where provider semantics allow it, and continuously observable. A green CI build is not performance acceptance; target-device evidence remains required.

## Pipeline shape

The target execution graph is producer/consumer based rather than whole-stage serial:

1. SOURCE_CAPTURE
   - Provider/content URI is opened once for source capture.
   - Bytes are copied and SHA-256 hashed in the same pass.
   - The verified private source becomes session-owned media for downstream decode/preview/export instead of being immediately discarded and later reopened.
   - No whole-file in-memory buffering.

2. AUDIO_WINDOW_PRODUCER
   - One sequential MediaExtractor/MediaCodec decode pass.
   - PCM is downmixed to mono PCM16 and finalized directly into bounded STT WAV windows.
   - No full-video PCM/WAV intermediate.
   - A finalized window is immutable and can be consumed while the decoder continues producing later windows.
   - Window hashes are computed during/finalization of the write; transport must not require `readBytes()` of the complete WAV.

3. STT_SCHEDULER
   - Durable PREPARED -> SENT -> RECEIVED lifecycle per exact source/window identity.
   - Bounded in-flight provider requests; initial production target is conservative concurrency with adaptive reduction on rate limiting.
   - No blind retry after SENT.
   - Received windows are reusable after restart.
   - Timing interpretation remains controlled by X001; performance work must not guess provider units/origins.

4. TRANSLATION_SCHEDULER
   - Translation may consume accepted STT text as soon as source units are safely available.
   - Durable provider outcome/recovery rules remain authoritative.
   - Any move from sequential provider execution to bounded concurrency must preserve the one-writer session-store model and cannot weaken stale-state/unknown-outcome fencing.
   - Translation/model output owns text only, never timing.

5. PRESENTATION
   - Preview becomes available incrementally as accepted translated cues become available.
   - Raster retention stays bounded to the existing current/next style window; never retain timeline-sized bitmap state.

6. EXPORT
   - One video transform pass using the shared presentation raster truth.
   - Preserve source geometry/frame rate unless a compatibility requirement forces conversion.
   - Prefer audio passthrough/transmux when Media3/container compatibility permits; transcode audio only when required.
   - Export validation is an explicit visible stage after encoder completion.

## Required user-visible progress contract

The UI must never show only an indefinite spinner for a long operation. Every active operation publishes a machine-readable snapshot containing:

- stage;
- stage label suitable for user display;
- completed work / total work when knowable;
- current window/unit/frame segment when applicable;
- bytes processed and total bytes when knowable;
- elapsed time for the current stage;
- elapsed time for the complete operation;
- throughput when meaningful;
- provider-wait elapsed time while a network request is outstanding;
- whether progress is local CPU/I/O, upload, provider wait, response processing, or validation;
- durable reuse counts (already-received/reused work);
- last-progress monotonic timestamp;
- blocker/failure classification without secrets or private transcript text.

Required high-level stages:

- SOURCE_CAPTURING
- SOURCE_READY
- AUDIO_DECODING
- STT_PREPARING
- STT_SENDING
- STT_WAITING_PROVIDER
- STT_RECEIVED
- STT_ASSEMBLING
- TRANSLATION_PREPARING
- TRANSLATION_SENDING
- TRANSLATION_WAITING_PROVIDER
- TRANSLATION_RECEIVED
- PRESENTATION_BUILDING
- PREVIEW_READY
- EXPORTING_VIDEO
- EXPORT_VALIDATING
- COMPLETE
- BLOCKED_UNKNOWN_REMOTE_OUTCOME
- FAILED
- CANCELLED

A provider wait must say that the app is waiting on the provider and continue increasing wait elapsed time; it must not look like an application hang.

## Resource invariants

- Memory is O(active window/raster), not O(video duration).
- Decoded PCM disk is O(bounded queued STT windows), not O(full video duration).
- Raster memory is O(current/next presentation window), not O(cue count).
- Source identity work is one copy+hash pass for a newly selected source.
- Successful AI work is durable and is not repeated merely because the user edits style or re-exports.
- Provider concurrency is bounded and observable.
- Every queue has explicit capacity/backpressure.

## Performance evidence to collect

For each target-device run record, without secrets/private transcript contents:

- source size and source capture/hash throughput;
- time to first finalized STT window;
- time to first STT request SENT;
- per-window decode duration;
- per-window upload/provider/response duration;
- number of in-flight STT requests over time;
- time to first accepted translation;
- translation request durations and queue depth;
- peak Java/native heap where measurable;
- temporary-disk high-water mark;
- time to preview-ready;
- export speed relative to media duration;
- export validation duration;
- total wall-clock from source selection to translated-preview-ready and to final validated MP4.

## Release rule

The final release is not accepted merely because the pipeline is functionally correct. X006 remains NOT_ACCEPTED until the production path is measured on target physical hardware and the final UI demonstrates continuous progress for long-running local/provider/export stages.