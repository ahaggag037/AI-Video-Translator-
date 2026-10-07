# Project State

Date: 2026-10-07

## Objective
Build a personal Android video translator using direct AI provider APIs. NVIDIA NIM is the chosen provider family for the first implementation.

## Verified current state
- Repository: `ahaggag037/AI-Video-Translator-` (private; note trailing dash in repository name).
- Working branch: `build/p0-prototype`.
- Pull request: #1 open against `main`.
- Architecture V2: DESIGNED, not yet fully implemented.
- P0-A Android skeleton: IMPLEMENTED + CI TESTED + DEVICE VERIFIED on the target Android phone.
- Video picker using `ACTION_OPEN_DOCUMENT`: DEVICE VERIFIED.
- Persistable read-permission attempt: IMPLEMENTED; persistence across process/device restart still needs a dedicated device check.
- Local video metadata probe: DEVICE VERIFIED with a real 11:13 MP4 on the target phone.
- P0-B local audio sample extraction: IMPLEMENTED + CI TESTED, NOT YET DEVICE VERIFIED.
- P0-B behavior: copies up to the first 60 seconds of the selected audio track into an app-cache M4A sample without network access; reports measured duration/size and can play the sample locally.
- P0-B unit tests: PASSED in GitHub Actions run 37627588752.
- P0-B Android lint: PASSED in GitHub Actions run 37627588752.
- P0-B debug APK assembly: PASSED in GitHub Actions run 37627588752.
- P0-B APK existence check and artifact upload: PASSED.
- P0-B artifact name: `ai-video-translator-debug-apk`.
- P0-B APK SHA-256 after download/extraction: `3e0517f5fe7194230a051e04f52881e828f55bffb438976814dc7a2e5bc49bb6`.
- P0-B APK archive integrity check: PASSED.
- NVIDIA STT/translation integration: NOT IMPLEMENTED.

## Build baseline that currently passes CI
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk 36
- targetSdk 36
- minSdk 29
- Compose BOM 2026.06.01

## P0-B implementation boundary
P0-B deliberately does **not** send any video/audio/text to NVIDIA. Its purpose is to prove that the selected video's audio track can be extracted on the real phone, that the produced sample has a plausible duration, and that it can be played locally before introducing provider/API variables.

The current extractor remuxes encoded audio samples into an M4A container. This is a local extraction test, not yet the final timestamp-normalized STT audio profile. Before timestamp-sensitive NVIDIA STT testing, P0-C will compare/establish the production audio profile (for example PCM/WAV versus compressed audio) and its timeline mapping.

## External constraints
- NVIDIA hosted NIM access is treated as zero monetary API cost for this personal prototype, but the client must still handle throttling/rate limits such as HTTP 429.
- The exact NVIDIA speech model/endpoint must be selected and contract-tested before the STT adapter is implemented.
- No API key is committed to GitHub or embedded in the APK.

## Current verification level
- P0-A: `DEVICE VERIFIED` for launch, video selection, and metadata display.
- P0-B: `IMPLEMENTED + CI TESTED + ARTIFACT VERIFIED`, but NOT `DEVICE VERIFIED`.

## Next valid action
1. Install the P0-B APK on the target Android phone.
2. Select the same known-good video.
3. Tap `استخراج عينة 60 ثانية`.
4. Confirm that extraction succeeds and the measured duration is approximately 60 seconds (or the remaining source length if shorter).
5. Tap `تشغيل عينة الصوت` and confirm the audio is the expected first minute and sounds normal.
6. Report the displayed MIME type, sample duration, and any error message/screenshot.
7. After P0-B device verification, implement P0-C timeline/audio-profile gate, then NVIDIA STT.
