# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`.
- Last verified code checkpoint: `a29c83303a7b21527db1f389f036424468c87e9e` (`test(B012): make STT multipart helpers explicit`).
- Exact frozen X005 PASS anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; newer descendant CI proves compatibility only and does not move that experiment anchor.
- Canonical design is named `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (Translation & Subtitle System V1 V4.1 Final). The canonical file itself is not currently surfaced through the active project/repository tools, so no missing text from it is being reconstructed from memory.

## Current batch/task
X005 persistence/recovery is closed PASS at its exact anchor. Active implementation is B012/Task17 durable source/session ownership, with X001 timing activation still blocked.

Current B012 order:
1. durable manifest/source attachment ownership,
2. live source revalidation,
3. immutable accepted SourceSnapshot with clock status separated from semantic durability,
4. exact audio/STT provenance needed to build that snapshot safely,
5. only then production source/snapshot builder + controller/UI resume wiring.

Task17 is still **NOT ACTIVATED**. Production Compose state, STT timing interpretation and renderer/export behavior remain legacy.

## Completed / implemented
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge and minimal atomic store.
- B003 provider classifier/retry policy verified by Android CI `37723687884`.
- B004 shadow segmentation/planning + X002 source corpus + deterministic translation integrity validation; no X002 production activation.
- B006 Unicode/ICU ownership + measured Arabic layout shadow foundation; production renderer unchanged.
- B011 display timing, CueIndex, semantic SRT, manual-edit preservation and stale-response fencing. SRT fails closed when a positive-us cue collapses at 1 ms serialization precision.
- X005 durable PREPARED → current-fenced SENT → RECEIVED protocol, exact epoch/manifest/entry/request fencing, deterministic recovery identity, crash replay, manual-truth preservation and no blind repost after unknown SENT.
- X003 native layout controls execute on API29/API35 with pinned font evidence; human readability remains unmeasured.
- X004 shadow media work now includes pure canonical upright-frame / preview fit-center geometry and an explicit preview transform foundation. No decoded-device preview/export parity verdict exists.

## B012 durable source lineage
### Manifest + attachment
- `527cf889387dda7e997563d7cc1a6e800b95bcb8`: manifest schema v2 separated from entry/receipt v1; legacy v1 manifests decode to explicit `LEGACY_UNBOUND`.
- `97681630071a96ec41e29d516bc22094e79aa751`: immutable `SourceAttachment` persistence without activation. Attachment identity binds session, `content://` locator, historical persisted-read-grant observation, full-source SHA-256+size, duration, typed selected range and active audio-track descriptor.
- Source attachment binding publishes immutable object before manifest CAS, advances revision+epoch, refuses legacy/manual history reassignment and fails closed on corruption/collision.

### Immutable SourceSnapshot
- `ffb0ee8711a7edd8063245a12f4637725dddfb2b`: immutable accepted-source snapshot persistence.
- `294fe599f9d933482fb3bb7a346fbdf348c0535c`: recovery-test fixture repair; production snapshot/store semantics unchanged.
- Exact descendant verification:
  - Android CI `37764997539`: success.
  - API35 X005 recovery `37764997633`: success.
  - Recovery artifact `11544187616`, digest `sha256:421184089e9377befcba273fbb274b3662a53cf45d67e1291dcb9ffbfc12e843`.
- `SourceSnapshot` persists transcript/word text, WAV/PCM identity, STT provenance and explicit observed presentation origin.
- `ClockVerificationStatus.UNVERIFIED` structurally forbids durable interpreted word intervals. Only `VERIFIED_AFFINE` may carry them. This prevents B012 durability from laundering X001 timing assumptions.

### Live source revalidation
- `9a5f2d8dd3fcacd677d03ab91c9a95f2b435d3d2`: Android `ContentResolver` probe reopens the current URI, recomputes the complete byte-stream SHA-256+size and reports current persisted-read-grant state separately.
- `AVAILABLE` is only a point-in-time full-byte match; stored permission metadata never substitutes for a fresh read.
- Same-URI replacement, zero-byte replacement, deletion, permission failure, generic I/O, unsupported locator and stale probe tokens fail distinctly/closed.
- Exact verification:
  - Android CI `37766064823`: success.
  - API35 X005 recovery `37766064846`: success.
  - Recovery artifact `11544906188`, digest `sha256:5d7ca34a1553574c6cac5f00d6e924d0b14d1a7455bec1c20f8d358310a5d986`.

### Actual STT audio provenance
- `d77f79f6e2e9ac6a9e64472963636eaec3385b65` adds an additive detailed audio-preparation surface; legacy `prepareFirstMinute()` return type and behavior remain unchanged.
- `c1267f0aa7be7c0115ca90a2c1377e84f3c29c72` makes optional input-language metadata fail-soft so provenance cannot create a new decode failure.
- Detailed provenance records the **actual container audio track used by `MediaExtractor`** plus exact mono PCM frame count. Input track identity includes index, MIME, optional language, input sample rate and input channel count.
- Exact Android CI `37772215065`: full success.
  - Reports artifact `11547954632`, digest `sha256:04dfb136eae7ad6066ec36c887052f1d2c9b5c70d460d991443c6aafc89b738c`.
  - Debug APK `11548179292`, digest `sha256:016b3f56b1dc672d58efa212957c066f7ddb679035e18528d6294c66fc49f0c8`.

### Accepted STT parse + raw evidence separation
- `6dd4c8b40c87d51386c457cae4d4ee2f78ebc89e` adds an internal detailed parser that first calls the unchanged legacy `NvidiaSttClient.parseResponse`, then pairs that accepted result with the existing high-fidelity `NvidiaSttTimingEvidenceInspector` output.
- `280d0d4e09f5ca9ae44954dc6f2605ad25bc6435` proves detailed result equality with legacy parsing and proves multi-schema timing evidence does not change accepted parser semantics.
- Raw provider response remains in-memory diagnostic evidence; it is not persisted/logged/relayed. Durable snapshot keeps only its SHA-256.
- Exact Android CI `37772600052`: full success.
  - Reports `11548850369`, digest `sha256:1ae7a22c4e293f3196e85cf68331f0f0acc205c871429b6adc08b13b9779406c`.
  - APK `11548975209`, digest `sha256:25ae34e4bf4b8289906d9a3082046e39d6e58b14f0f133fa0e87a18fbeacd977`.

### Hosted NVIDIA STT request provenance
- `7f8d0afb8bfec2c142a2ce45706909472962b7ca` introduces one typed non-secret hosted STT request profile.
- `05202aa3591f5258e22b460c9c8b86cb0f4cae65` pins exact multipart shape.
- `ed7ce84f987ab04e786158cc322528cfbf5566b8` makes `NvidiaSttClient` build its existing request from that profile contract.
- `a29c83303a7b21527db1f389f036424468c87e9e` is the verified checkpoint after test-helper cleanup.
- Profile identity binds provider/model, exact NVCF endpoint, POST, `language=en-US`, `word_time_offsets=True` and WAV media type. Authorization secret and actual WAV bytes are intentionally outside this profile; sample bytes are owned separately by `SourcePcmSampleIdentity`.
- Production response parsing and magnitude-based timing heuristic are unchanged.
- Exact Android CI `37773725083`: full success.
  - Reports `11549586172`, digest `sha256:748bbcaeb2ed5fb40d4f9f00ba010703ec315fb6c35cd923599ae43cd2a80a2f`.
  - APK `11549261464`, digest `sha256:b6939d89e605fe325270f155279bc82070db2695e094e1b3cade193a6c041492`.

## Experiment status
- X001: **HARNESS_READY_PARTIAL**. High-fidelity raw timing evidence, declared-ms/seconds ambiguity controls, nonzero-origin controls, exact audio-track/PCM provenance and request-profile identity now exist. Missing: a real provider canary atomically bound to exact sample identity + verified presentation-origin evidence. Production unit inference remains unchanged. No PASS.
- X002: **HARNESS_READY**. Synthetic N25 corpus/integrity validation only; no paired provider outputs or blind Arabic human scoring. No PASS.
- X003: **HARNESS_EXECUTED_PARTIAL**. Native API29/API35 geometry controls executed; human readability and preview/export parity remain unmeasured. No PASS.
- X004: **SHADOW_FOUNDATION_ONLY**. Pure export-media evaluator + canonical frame/preview transform model exist; no real decoded frame/audio-marker/cue-switch/preview-lag device evidence. No PASS.
- X005: **PASS** only at `b7948478eacef67b2552d4540e4358152cf72dd6`.
- X006: **NOT_STARTED / correctness foundations only**. No device profiler/memory/allocation/export-stress evidence. No PASS.

## X005 exact PASS evidence
- Android CI `37730225777` @ `b7948478eacef67b2552d4540e4358152cf72dd6`: full success.
- X005 Android Recovery `37730225779` @ same SHA: full success.
- Recovery artifact `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`.
- Verification reports `11529800538`, digest `sha256:2d93fdb447cb66fe501513cf3158689df9ccff120b0bfa5414c2f20d904bee49`.
- Debug APK `11529507092`, digest `sha256:22ca17fd79538d3489e9ede20d1c7c0e81c4e623e05cd3a4a1cab0485cc90a2f`.

## Deliberate V1 policies / known gaps
- STT seconds-vs-ms magnitude inference remains unchanged pending X001. It is not accepted unit evidence.
- `SttAudioPreparer.sourceStartUs` is a direct first extractor PTS observation. `sourceEndUs` is derived from PCM frame count; continuity/priming/edit-list correctness remain unaccepted.
- Current preparer still selects the first audio track and prepares the first duration window; it is not yet selected-range aware. Therefore a snapshot builder must not invent a selected-range membership fence until the preparer itself owns that range.
- Full-source hashing is intentionally strong resume identity evidence and may later require X006 performance measurement; do not weaken it to URI/size/mtime without evidence.
- A successful live probe is not TOCTOU freedom. Source-dependent operations still need current fencing/stable ownership.
- Session-global `manifest.revision` remains an explicit safety-over-liveness X005 policy.
- Provider timeouts remain a liveness policy, not translation request identity.
- No production `SourceAttachment` builder/controller owns the current UI selection yet. The persisted object/store exists, but production construction/wiring remains B012 work.
- MASTER-OS local runtime was checked during this session but its tunnel was unavailable; no external runtime persistence is being claimed.

## Active reviews
Review requests have been posted for:
- source/snapshot durability and live-probe contract,
- actual STT track/PCM provenance,
- accepted STT parse + raw evidence separation,
- hosted STT request-profile identity.
The Notion comment reader has not surfaced newer replies reliably, so no unobserved review approval is assumed.

## RESUME HERE
1. Re-check live branch HEAD and exact CI before each new dependent checkpoint.
2. Build the next **pure fail-closed SourceSnapshot factory/adapter** before controller/UI activation. It should bind the active attachment to the actual decoded track, exact WAV/PCM identity, accepted transcript/word text, raw-response SHA and STT request profile.
3. Under `UNVERIFIED`, deliberately discard current legacy `startMs/endMs`; persist no interpreted word intervals. Do not activate X001.
4. Do not impose `sourceStartUs ∈ selectedRange` yet: the current preparer is not range-aware. Range ownership must be moved into preparation first, then fenced.
5. After the pure snapshot factory is sound, add a production source-attachment construction boundary and a same-response detailed network path without changing legacy semantics; only then consider session controller/UI resume wiring.
6. Keep Task17 blocked until source construction, source revalidation, snapshot creation, durable binding and reopen states are end-to-end owned and tested.
