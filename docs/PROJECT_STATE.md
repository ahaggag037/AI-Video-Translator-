# Project State

Date: 2026-10-07

## Objective
Build a personal Android video translator using direct AI provider APIs. NVIDIA is the provider family for the first live STT prototype.

## Repository / active work
- Repository: `ahaggag037/AI-Video-Translator-` (private; note trailing dash in repository name).
- P0-A/P0-B baseline branch: `build/p0-prototype`.
- Current P0-C branch: `build/p0c-nvidia-stt`.
- Architecture V2: DESIGNED, not yet fully implemented.

## Verified current state
### P0-A — video selection / probe
- Android skeleton: IMPLEMENTED + CI TESTED + DEVICE VERIFIED on the target Android phone.
- Video picker using `ACTION_OPEN_DOCUMENT`: DEVICE VERIFIED.
- Persistable read-permission attempt: IMPLEMENTED; persistence across process/device restart still needs a dedicated device check.
- Local video metadata probe: DEVICE VERIFIED with a real 11:13 MP4 on the target phone.

### P0-B — local audio sample
- Local encoded-audio sample extraction: IMPLEMENTED + CI TESTED + DEVICE VERIFIED for extraction.
- Real-device evidence on 2026-10-07: selected 11:13 / 426x240 / 15.6 MB MP4 produced `sample.m4a`, MIME `audio/mp4a-latm`, measured duration `01:00`, size `0.9 MB`.
- The screenshot proves extraction/output metadata only. Correct playback of the first minute through `تشغيل عينة الصوت` remains DEVICE-UNVERIFIED until the user explicitly confirms hearing it.
- P0-B CI run: `37627588752` — unit tests, lint, debug APK, APK existence check, and artifact upload PASSED.
- P0-B APK SHA-256: `3e0517f5fe7194230a051e04f52881e828f55bffb438976814dc7a2e5bc49bb6`.

### P0-C — NVIDIA STT / timestamp gate
- Local STT WAV preparation: IMPLEMENTED on `build/p0c-nvidia-stt`.
- The app decodes up to the first 60 seconds with `MediaExtractor` + `MediaCodec`, streams decoder PCM, downmixes to mono, and writes a PCM 16-bit WAV in app cache. No full media file is loaded into RAM.
- Decoder end-of-stream handling was corrected so a source shorter than the requested sample does not leave the codec waiting indefinitely.
- NVIDIA HTTP prototype client: IMPLEMENTED, but NOT LIVE-PROVIDER-VERIFIED yet.
- Prototype model: `NVIDIA Parakeet CTC 1.1B (en-US)` for the current English test video.
- Request path: local WAV only -> NVIDIA HTTP transcription endpoint with `language=en-US` and word time offsets requested. The video itself is not uploaded.
- NVIDIA API key is entered by the user in-app and held in screen memory for this prototype only; it is not committed to GitHub, embedded in the APK, or persisted to disk yet.
- Live response JSON shape, transcript correctness, and word timestamp semantics remain DEVICE/PROVIDER-UNVERIFIED until a request succeeds with the user's NVIDIA key.
- OkHttp `5.5.0` was rejected after CI proved it requires compileSdk 37; project baseline remains compileSdk 36. P0-C uses OkHttp `5.3.2`, which passed this repository's compile/test/lint/build gates.
- P0-C CI run: `37631153808` — unit tests PASSED, Android lint PASSED, debug APK assembly PASSED, APK existence check PASSED, artifact upload PASSED.
- P0-C artifact ID: `11485929885`, name `ai-video-translator-debug-apk`.
- Downloaded P0-C APK archive integrity: PASSED (`unzip -t`: no compressed-data errors).
- P0-C APK SHA-256: `7206ab3d82abb8ec808af4337883e5d2dc64a2b08060aec1ca1c671006f6cec0`.

## Build baseline that currently passes CI
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk 36
- targetSdk 36
- minSdk 29
- Compose BOM 2026.06.01
- OkHttp 5.3.2 for P0-C

## External / provider constraints
- NVIDIA hosted access is treated as zero monetary API cost for this personal prototype, but the client must still handle provider/network failure and throttling such as HTTP 429 later.
- The P0-C Parakeet CTC model is deliberately an English-only prototype choice. Multilingual/Arabic-source STT remains a separate provider/model decision after this transport/timestamp gate is proven.
- Current P0-C goal is not production STT architecture; it is to prove: Android audio decode -> valid WAV -> NVIDIA request -> transcript + usable timestamps on the target phone.
- No API key is committed to GitHub or embedded in the APK.

## Verification levels
- P0-A launch/video selection/metadata: `DEVICE VERIFIED`.
- P0-B extraction: `DEVICE VERIFIED`.
- P0-B playback correctness: `DEVICE UNVERIFIED`.
- P0-C code: `IMPLEMENTED + CI TESTED + LINT PASSED + ARTIFACT VERIFIED`.
- P0-C local WAV conversion on target phone: `DEVICE UNVERIFIED`.
- P0-C live NVIDIA request/response: `PROVIDER UNVERIFIED`.

## Next valid action
1. Install the P0-C APK on the target Android phone.
2. Select the same known-good English video.
3. Enter the NVIDIA API key in the app (do not send or paste the key into chat).
4. Tap `تشغيل اختبار NVIDIA STT`.
5. Verify that WAV preparation succeeds and shows PCM 16-bit mono, a plausible sample rate, approximately 01:00 duration, and a plausible WAV size.
6. Verify that NVIDIA returns a transcript; record displayed word count and first/last timestamp if present.
7. Send a screenshot of the success state or the exact displayed error.
8. Separately confirm whether the prior P0-B `تشغيل عينة الصوت` button actually played the expected first minute.
9. Only after the live request succeeds should the NVIDIA response parser/timestamp contract be promoted to PROVIDER-VERIFIED and used to design the reusable STT provider adapter.
