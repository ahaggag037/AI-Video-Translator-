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
- Local STT WAV preparation: IMPLEMENTED + DEVICE VERIFIED.
- The app decodes up to the first 60 seconds with `MediaExtractor` + `MediaCodec`, streams decoder PCM, downmixes to mono, and writes a PCM 16-bit WAV in app cache. No full media file is loaded into RAM.
- Decoder end-of-stream handling was corrected so a source shorter than the requested sample does not leave the codec waiting indefinitely.
- NVIDIA HTTP prototype client: IMPLEMENTED + LIVE PROVIDER VERIFIED on the target Android phone.
- Prototype model: `NVIDIA Parakeet CTC 1.1B (en-US)` for the current English test video.
- Request path: local WAV only -> NVIDIA HTTP transcription endpoint with `language=en-US` and word time offsets requested. The video itself is not uploaded.
- NVIDIA API key is entered by the user in-app and held in screen memory for this prototype only; it is not committed to GitHub, embedded in the APK, or persisted to disk yet.
- Real-device/provider evidence on 2026-10-07: NVIDIA STT success state displayed `WAV PCM 16-bit mono`, sample rate `44100 Hz`, duration `01:00`, WAV size `5.0 MB`, `217` words, first word timestamp `80 ms`, last word timestamp `60000 ms`, and a coherent English transcript.
- The successful live response proves the current HTTP request contract, parser path, and word-timestamp normalization work end-to-end on the target phone for this one-minute English gate. Transcript semantic accuracy against the source audio has not been formally audited word-for-word.
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
- P0-C has now proved: Android audio decode -> valid WAV -> NVIDIA request -> transcript + usable word timestamps on the target phone.
- No API key is committed to GitHub or embedded in the APK.

## Verification levels
- P0-A launch/video selection/metadata: `DEVICE VERIFIED`.
- P0-B extraction: `DEVICE VERIFIED`.
- P0-B playback correctness: `DEVICE UNVERIFIED`.
- P0-C code: `IMPLEMENTED + CI TESTED + LINT PASSED + ARTIFACT VERIFIED`.
- P0-C local WAV conversion on target phone: `DEVICE VERIFIED`.
- P0-C live NVIDIA request/response: `PROVIDER + DEVICE VERIFIED`.
- P0-C current response parser / word timestamp contract: `PROVIDER VERIFIED` for the tested Parakeet one-minute English path.

## Next valid action — P0-D translation gate
1. Keep STT and translation as separate stages; timestamps remain owned by STT.
2. Convert the 217 timestamped words into stable subtitle-style source cues with IDs, start/end times, and English text.
3. Send a small batch of those cues to NVIDIA's OpenAI-compatible LLM chat-completions endpoint for English -> Arabic translation.
4. Require the translation response to return the same cue IDs/count and Arabic text only; translation must not alter timestamps.
5. Validate IDs/count programmatically and fail safely on malformed model output.
6. Display the Arabic translation beside the original cue timing on the phone.
7. Once a live translation request succeeds on-device, promote P0-D and then generate/export the first SRT from the same cue model.
