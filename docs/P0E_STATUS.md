# P0-E — Subtitle/Video Synchronization Gate

Date: 2026-10-07

## Input state
- P0-C STT is provider + device verified for the one-minute English sample.
- P0-D live NVIDIA translation and in-app Arabic SRT generation are device verified by the user's 2026-10-07 screenshot.
- Saving/opening the exported SRT in an external player remains separately unverified.

## P0-E implementation
Branch: `build/p0e-subtitle-preview`

Implemented:
- Explicit conversion from sample-relative subtitle cues to the original video's presentation timeline.
- SRT output now uses presentation-timeline cue clocks.
- On-device preview of the selected original video with the active Arabic cue rendered from the same cue timestamps used by SRT.
- Preview is intentionally limited to the translated sample window.
- Unit tests cover non-zero sample offsets, video-end rejection, and active-cue boundary semantics.
- Version: `0.1.3-p0e-preview` (`versionCode=5`).

## Verification state
- IMPLEMENTED: yes.
- Unit/Lint/Build CI: pending at time of this note.
- DEVICE VERIFIED: no.

## Device acceptance gate
1. Install the P0-E APK.
2. Select the same source video and run STT + Arabic translation.
3. Start the embedded video preview.
4. Verify spoken phrases and Arabic cue transitions are visually synchronized through the first minute.
5. Save `sample_ar_video_timeline.srt` and open it against the original video in an external subtitle-capable player if available.

Do not mark synchronization DEVICE VERIFIED until the user confirms visual timing on the phone.
