# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`.
- Last CI-green code checkpoint: `da521a41ec2479bcfae9bc427465ebe2f8963d6a`, Android CI `37775661909` green — run ID reported by Kimi K3 via ModelBridge relay 2026-10-08; direct Actions access is unavailable from the worker vantage, so this is relay-sourced evidence.
- CI FAILURE evidence (relay-reported by Kimi K3, exact diagnostics):
  - `1f1837615d992dc4ffc0d4d2f29bed82185322dc`: Android CI `37792006164` FAILED in compileDebugKotlin — public function exposing internal return type (`EXPOSED_FUNCTION_RETURN_TYPE`). Lint/APK stages skipped.
  - `889320e53ed74c253ea20a175e2546e210adeb02`: Android CI `37792432928` FAILED with the same diagnostic at `NvidiaSttClient.kt:65`.
  - Root cause introduced at `f53f7f88`; repaired at `af56decd61b8619de581bdc5ab387c2bd0846636` (function made `internal`) plus `3dc60ce352fee76ef2a02be245b0fbedaaa27ffe` (same defect class fixed preemptively in the session coordinator).
- Next verified-checkpoint candidate: `af56decd` — carries ALL accepted repairs; CI conclusion pending. Do not record as verified until an exact green run ID exists.
- Exact frozen X005 PASS anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; newer descendant CI proves compatibility only and does not move that experiment anchor.
- Canonical design is named `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (Translation & Subtitle System V1 V4.1 Final). The canonical file itself is not currently surfaced through the active project/repository tools, so no missing text from it is being reconstructed from memory.

## Current batch/task
X005 persistence/recovery is closed PASS at its exact anchor. Active implementation is B012/Task17 durable source/session ownership, with X001 timing activation still blocked.

Current B012 order:
1. durable manifest/source attachment ownership,
2. live source revalidation,
3. immutable accepted SourceSnapshot with clock status separated from semantic durability,
4. exact audio/STT provenance needed to build that snapshot safely,
5. production source-attachment construction boundary + transport-bound STT observation (DONE pending CI),
6. session-level CAS composition of capture→binding and evidence→snapshot→binding (DONE pending CI; `SourceSessionCoordinator`),
7. only then controller/UI resume wiring (Task17 still gated).

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
- Profile identity binds provider/model, exact NVCF endpoint, POST, `language=en-US`, `word_time_offsets=True` and WAV media type. Authorization secret and actual WAV bytes are intentionally outside this profile.
- Exact Android CI `37773725083` @ verified checkpoint `a29c83303a7b21527db1f389f036424468c87e9e`: full success.
  - Reports `11549586172`, digest `sha256:748bbcaeb2ed5fb40d4f9f00ba010703ec315fb6c35cd923599ae43cd2a80a2f`.
  - APK `11549261464`, digest `sha256:b6939d89e605fe325270f155279bc82070db2695e094e1b3cade193a6c041492`.

### Pure fail-closed SourceSnapshot factory (CI-green @ da521a41)
- `701c350a602837bd6be0c4696450d479cbacf7fa`: factory builds UNVERIFIED snapshot strictly from exact evidence; legacy normalized word offsets dropped at the boundary.
- `be5ddb3e91a6056104833cf1e850b326a1d55a36` + `04486acfd6916b1e0b2e89c21439bf80e52c2e93`: versioned accepted-parser identity (`nvidia-stt-legacy-parser-v1`) carried with the accepted parse.
- `bcedad40ec0e13bb125e88d6bc2090e205a7c4a4`: RIFF32 evidence bounds tightened (`dataBytes ≤ 0xFFFFFFFF-36`, header field arithmetic).
- `5761a18a80486cb997e5e02607f0c2b382ad3f61` + `da521a41ec2479bcfae9bc427465ebe2f8963d6a`: request profile must be explicit and current; invented/drifted profile provenance is rejected.
- Exact verification: Android CI `37775661909` green @ `da521a41` (relay-reported by Kimi K3).

### Transport-bound redacted STT observation (repair chain, CI pending @ af56decd)
- `f53f7f88211c7a580b24ba446fe080c751e23233`: additive same-response detailed network path (intermediate; superseded).
- `9c3e99e2e70ba6d883230b7accabe10e9e65b2fb` / `889320e53ed74c253ea20a175e2546e210adeb02`: first observation shape + cross-pairing tests (superseded; compile-red).
- `cac88158c1ece8fab94be23a6fe687543891a326`: repair of Kimi K3 findings (relay `6061989391`, `6062067815`):
  - Sample bytes materialized in memory and hashed BEFORE request creation; request streams exactly those pinned bytes (`NvidiaSttWireContract.request(key, fileName, bytes)`). Streamed bytes ≡ hashed bytes; no dependence on the mutable prepared path.
  - `NvidiaSttTransportObservation` REDACTED: exactly `{requestProfile, result, parserVersion, rawResponseSha256, sampleSha256}`; no `timingEvidence`/`rawResponseUtf8` in its graph. `rawResponseSha256` = SHA-256 of the UTF-8 JSON text as decoded (text identity, documented; not transport-octet identity).
  - `internal bindDetailedResponse(body, status, sampleSha256)`: single response-handling point, unit-testable without provider; non-2xx mirrors legacy failure classification/messages exactly and never yields an observation.
  - `NvidiaSourceSnapshotFactory.buildUnverified` consumes ONLY the observation; independently supplied provenance parts cannot reach snapshot construction. Factory re-inspects the prepared WAV and requires byte-identity with the observation's pre-send digest; post-anchor path replacement fails closed.
- `741430e0253f6af69b060bb01d0f75d2a21bf30a`: discriminating falsifiers — equal-shape/different-payload A/B pairing rejection (digest fence is sole discriminator), fixed-path post-anchor replacement race rejection with swap-proof, structural redaction pin via declared-field reflection, legacy-exact non-2xx failure shapes (400/401/403/429/500 JSON-detail + non-JSON fallback), bytes-vs-file multipart byte-equivalence, malformed digest/parser rejection.
- `af56decd61b8619de581bdc5ab387c2bd0846636`: `transcribeEnglishSampleDetailed` made `internal` (repairs `EXPOSED_FUNCTION_RETURN_TYPE` that failed CI `37792006164`/`37792432928`).
- CI: PENDING @ `af56decd` (run IDs requested via relay).

### Production source-attachment construction boundary (repair chain, CI pending)
- `f036c9614cea65f15342dd30ec2d8077ae199abc`: initial assembler+builder (superseded).
- `1f1837615d992dc4ffc0d4d2f29bed82185322dc`: assembly fences pinned (non-readable statuses, non-positive duration, range overflow, identity sensitivity, grant-as-observation).
- `4dfe670fc14073ab538f897a3f98480dcddcdc2c`: cross-open TOCTOU repair (Kimi K3 relay `6062025344`): provider stream opened EXACTLY ONCE; single pass copies+hashes into a unique private cache file; duration and first-audio-track read from that exact copy, never a second provider open. Distinct `SourceCaptureException(SourceReadStatus…)` failures.
- `3dc60ce352fee76ef2a02be245b0fbedaaa27ffe`: eager stale-capture sweep REMOVED (Kimi K3 relay `6062252472`): each capture owns a unique temp file deleted in `finally`; crash leftovers reclaimed by Android cache eviction; concurrent builders cannot unlink each other's live temp paths.
- CI: PENDING.

### Session-level CAS composition (CI pending)
- `e343fa2bc2f342a103085ecdba18489be0b6e686`: `SourceSessionCoordinator` — `captureAndBindInitialSource` (capture → `bindInitialSourceAttachment`) and `snapshotAndBindInitialSource` (active attachment + preparation + observation → factory → `bindInitialSourceSnapshot`), both through the existing store CAS. Snapshots built OUTSIDE the store lock; identical evidence yields identical immutable identity, so retries republish identical bytes.
- `CasRetry` pure policy: retries ONLY while the manifest revision actually advanced; require-failures (`IllegalArgumentException`) never retried; invariant failures with unchanged revision never retried; bounded at 3 attempts.
- `6fa100edd554f5eba4e6971869657a0c7258cd12`: 6 pinned policy tests including contention budget and identity-idempotence across retry.
- `3dc60ce3…`: coordinator visibility made module-internal (same defect class as the client fix).
- CI: PENDING. No UI wiring; Task17 still gated.

## Experiment status
- X001: **HARNESS_READY_PARTIAL**. High-fidelity raw timing evidence, declared-ms/seconds ambiguity controls, nonzero-origin controls, exact audio-track/PCM provenance, request-profile identity and a redacted transport-bound same-response observation now exist. Missing: a real provider canary atomically bound to exact sample identity + verified presentation-origin evidence. Production unit inference remains unchanged. No PASS.
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
- Current preparer still selects the first audio track and prepares the first duration window; it is not yet selected-range aware. Therefore a snapshot builder must not invent a selected-range membership fence until the preparer itself owns that range. The preparer's fixed mutable `stt_sample.wav` path is intentionally unchanged: the transport pins sample bytes in memory pre-send and the factory re-verifies byte identity, so drift fails closed without altering legacy behavior.
- Full-source hashing is intentionally strong resume identity evidence and may later require X006 performance measurement; do not weaken it to URI/size/mtime without evidence. Source capture makes one full copy pass to a private file for the same reason (single provider open); X006 will qualify both on device.
- A successful live probe is not TOCTOU freedom. Source-dependent operations still need current fencing/stable ownership.
- Session-global `manifest.revision` remains an explicit safety-over-liveness X005 policy.
- Provider timeouts remain a liveness policy, not translation request identity.
- The transport observation's module-internal constructor means in-module tests can forge parts; the guarantee is by-construction at the single transport site plus factory re-verification of every independently checkable component (profile currency, parser identity, HTTP status, sample digest). No cryptographic response↔request binding exists because the NVCF response echoes no request fields.
- Static audits by the worker must include Kotlin visibility exposure (`EXPOSED_*`) — the `f53f7f88` defect escaped one audit round and cost two CI runs.
- MASTER-OS local runtime was checked during this session but its tunnel was unavailable; no external runtime persistence is being claimed.

## Active reviews
ModelBridge relay (issue #1, `ahaggag037/Workspace-for-teera.ai`) is the active review channel with Kimi K3:
- Kimi REVIEW `6061834386` (caller-asserted provenance): ACCEPTED, repaired.
- Kimi BLOCKER-CHALLENGE `6061989391` (response↔audio binding, mutable path): ACCEPTED, repaired at `cac88158` + falsifier `741430e0`.
- Kimi FALSIFIER-FAIL `6062067815` (late hash + non-redacted observation): ACCEPTED, repaired at `cac88158`/`741430e0`.
- Kimi REVIEW `6062025344` (builder cross-open TOCTOU): ACCEPTED, repaired at `4dfe670f`.
- Kimi REVIEW `6062252472` (capture-sweep race): ACCEPTED, repaired at `3dc60ce3`.
- Kimi REPAIR-REVIEW `6062218850`: redaction + sample-byte binding ACCEPTED by reviewer; compile blocker reported and fixed at `af56decd`.
- GPT6 repair reports: relay `6062050979`, `6062236768`, `6062297168`.

## RESUME HERE
1. Re-check live branch HEAD and exact CI before each new dependent checkpoint. Worker cannot observe Actions directly; obtain run IDs via ModelBridge relay (Kimi K3) or the user before recording verification.
2. When CI for `af56decd` (or then-HEAD) settles GREEN, reconcile this doc + the acceptance ledger verified-checkpoint line to that SHA with the exact run ID/artifacts. If RED, repair first.
3. Next implementation candidates (in order): (a) reopen/resume evaluation path composing `SourceContentProbe` + `SourceResumeEvaluator` behind the coordinator (pure decision surface, no UI); (b) session-creation boundary (single app-owned `TranslationSessionStore` root ownership contract); (c) only then controller/UI resume wiring — Task17 remains gated until (a)/(b) are owned and tested.
4. Under `UNVERIFIED`, deliberately discard current legacy `startMs/endMs`; persist no interpreted word intervals. Do not activate X001.
5. Do not impose `sourceStartUs ∈ selectedRange` yet: the current preparer is not range-aware. Range ownership must be moved into preparation first, then fenced.
