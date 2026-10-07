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

## P0-D — Arabic translation + timed subtitles
- Branch: `build/p0d-arabic-subtitles`, based on P0-C commit `fc4d168365bbae76301941de8abcc7aea1c68df7`.
- Inspected latest baseline successful CI `37633708767`: tests, lint, APK assembly and artifact upload all passed.
- IMPLEMENTED + CI TESTED + LINT PASSED + APK ARTIFACT VERIFIED. PROVIDER/DEVICE UNVERIFIED for translation.
- P0-D code commit: `19010f96614ed3f08a5e0ae19755d01e9379cfab`.
- P0-D CI: https://github.com/ahaggag037/AI-Video-Translator-/actions/runs/37635678819 — SUCCESS. All 14 JUnit tests passed (0 failures/errors/skips); lint passed with 6 baseline/dependency warnings and no errors; debug APK assembly and signature verification passed (v2, one signer).
- APK artifact ID: `11489481863`; report artifact ID: `11489501943`. Downloaded ZIP and APK ZIP integrity checks passed; local SHA-256 matches the checksum produced by CI.
- APK SHA-256: `dbafb68234e452b2b511e2776b2776aa7bd7ab041e05f0782976aa4af9f87cca`; size 31,015,041 bytes. Delivery filename: `AI-Video-Translator-P0D.apk`.
- GitHub compare confirmed P0-C media/STT source files unchanged. This verification note is documentation-only; the APK corresponds to the code commit above.
- Model: `nvidia/riva-translate-4b-instruct-v2`, NVIDIA-hosted `POST https://integrate.api.nvidia.com/v1/chat/completions`, Bearer NVIDIA API key.
- Selected after official documentation review on 2026-10-07: NVIDIA's model card explicitly lists Arabic, supports sentence/document translation and publishes English-to-Arabic evaluation. This is a decoder-only 4B translation LLM; documented Arabic support is not a live quality test of this sample.
- Documented model context: 8K tokens. Endpoint max_tokens: 1–4096; prototype requests 1024, temperature 0, stream false, system `en-ar`, user source text.
- No model-specific JSON-schema/response_format guarantee is documented in the inspected endpoint. Therefore ADR-003 replaces the earlier planned batch/echo-ID contract with one text-only request per unit. IDs/count are assigned and validated locally; no timing or ID is sent to or read from the model.
- Requests are sequential, separated by 1.5 seconds (local pacing, not a claimed provider quota). Free endpoint documents possible throttling; account-specific numeric quotas remain unknown. 401/403/429/202 and other non-200 statuses fail visibly; no automatic billable retries. 202 polling is not implemented in this prototype.
- Deterministic segmentation: sentence punctuation, >=700ms pause, up to 16 words / 160 characters / 6 seconds where a word boundary permits. Individual words are never split. Every input word is retained exactly once. Invalid, missing, overlapping, nonpositive or >60000ms times fail, never get clamped or invented.
- SourceUnit IDs and sample-relative millisecond boundaries are local immutable values. TranslationEntry is separate; ArabicSubtitleCue derives its timing exclusively from SourceUnit. The V2 presentation-time microsecond model is still future work; this gate exports the WAV sample clock without claiming full-video synchronization.
- Complete validated output is displayed with English, Arabic and millisecond start/end values. SRT uses ASCII clock digits and UTF-8 Arabic. Save uses Android CreateDocument; share uses a restricted FileProvider and a unique cache snapshot named sample_ar.srt.
- Partial/cancelled/malformed/truncated responses do not enable SRT export. Changing source or rerunning STT removes the translation UI and cancels its coroutine/HTTP call. No translation key is persisted or logged.
- Existing NvidiaSttClient.kt, SttAudioPreparer.kt, AudioSampleExtractor.kt, VideoProbe.kt and VideoMetadata.kt are unchanged.
- CI now retains test/lint reports, verifies the debug APK signature and publishes its SHA-256 alongside the APK. Version: 0.1.0-p0d (code 2).
- Tests cover segmentation boundaries, invalid timing, complete synthetic 217-word timing preservation (80–60000ms), ID/count validation, Arabic-locale SRT, UTF-8, malformed/truncated translation responses, request contract and the unchanged STT parser with a synthetic fixture.
- No real transcript/audio or NVIDIA credential is present in this checkout/session. Consequently no real-sample Arabic SRT or live NVIDIA translation is claimed. The first genuine sample_ar.srt is generated on the phone after its live translation succeeds.

### Official evidence reviewed 2026-10-07
- Model inventory/endpoint: https://docs.api.nvidia.com/nim/reference/llm-apis
- Model card/Arabic/context/prompt: https://build.nvidia.com/nvidia/riva-translate-4b-instruct-v2/modelcard
- Request contract/auth/output limits: https://docs.api.nvidia.com/nim/reference/nvidia-riva-translate-4b-instruct-v2-infer
- Hosted availability/throttling notice: https://build.nvidia.com/nvidia/riva-translate-4b-instruct-v2

### Remaining acceptance gate
Install new APK; select the same video; run STT; translate; check Arabic meaning, cue start/end equality, RTL readability, save/share and open sample_ar.srt. Check cancellation, retry after failure and changing source. Rotation/process death intentionally loses screen state in this P0 prototype. Debug signing continuity with the already installed APK is not guaranteed by the existing ephemeral CI signing setup; installation/update must be device-checked.
