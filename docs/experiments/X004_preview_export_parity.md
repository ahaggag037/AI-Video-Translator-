# X004 — Preview / export parity evidence map

State: `HARNESS_READY_PARTIAL / DEVICE_EVIDENCE_PENDING`

This document inventories the production shared-raster falsifiers and the remaining evidence boundary. Harness presence or compilation is not an X004 PASS.

## Production truth under test

- `LivePresentationRasterSnapshotFactory` freezes one upright geometry, pinned font identity, exact cue text/timing, and layout descriptor set.
- `RasterVideoSubtitlePreview` consumes those raster requests and never lays out subtitle text on display callbacks.
- `SnapshotBurnedSubtitleExporter` consumes the same snapshot through `SnapshotBitmapOverlay`; gaps are transparent and active raster failure is fail-closed.
- `RasterCoordinator` owns current/next production rasterization, single-worker preparation, cache budget, eviction, and leases.

No second preview/export layout truth is introduced by the W3 changes.

## Automated falsifiers already present or strengthened

- `LivePresentationRasterSnapshotInstrumentedTest`: exact text/times, upright rotation geometry, audio-presence truth, missing-geometry fail-closed.
- `SnapshotBitmapOverlayInstrumentedTest`: source/output clock mapping, half-open end boundary, transparent gaps, exact touching-cue boundary switching, geometry mismatch, rejection fail-closed, clock overflow, overlap rejection.
- `RasterCoordinatorInstrumentedTest`: single worker, current+next prefetch, nonblocking preview peek, lease-safe eviction, cache-pressure retry, request-ID collision, bounded export wait, stale in-flight suppression and seek-back rerender.
- `PreviewRasterLatencyInstrumentedTest`: Choreographer/display-frame probe against the 100 ms preview cue-lag budget.
- `DecodedFrameParityInstrumentedTest`: real Media3 Transformer export followed by decoded-frame comparison for descriptor position, gaps, and one-frame cue-boundary allowance.
- `AndroidExportMediaInspector` + `ExportValidator`: readable output, duration/timeline, video track, and required audio-track presence.
- `DecodedAudioEvidenceInstrumentedTest`: platform decode of known AAC markers. This proves the decoder/evidence path, **not** decoded subtitle-export audio parity by itself.

W3 additionally keeps preview playback through the complete source sample even when the last subtitle ends early, so the trailing transparent interval remains observable in both preview and export.

## Required device evidence manifest

Every executed device/emulator run must record:

- exact HEAD SHA
- test class + method
- device/emulator identity
- Android API level and build fingerprint/model
- run ID / CI run ID
- source fixture identity and dimensions/rotation
- result and failure text
- measured preview lag where applicable
- decoded-frame result and displacement/error metric where applicable
- decoded exported-audio result where applicable

Emulator results must be labeled emulator evidence. They do not satisfy a target-physical-device requirement by themselves.

## Remaining acceptance boundary

At this package revision, the worker has no local Android device/emulator execution capability. Therefore worker-head decoded-frame, preview-lag, rotation-on-device, and exported-audio decode evidence remain `PENDING` until an Android execution environment runs the exact HEAD.

The existing decoded-audio fixture test does not yet prove that audio surviving a subtitle-burn export decodes to the expected source marker. X004 must remain non-PASS until that export-audio evidence and the target-device requirements in the acceptance contract are directly satisfied.
