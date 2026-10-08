# Translation V1 — Execution State

## Identity and checkpoint
- Branch: `build/p0g-gpt6-cleanroom-v1`; frozen baseline: `861aadcb36cccee83d2c86e9a0c0a03b1efe6720` on `build/p0f-hardburn-mp4`.
- This continuation recovered `0da65a11a147f2736651745ccb44d9edbd4813ba` directly, then repaired the earliest CI failures before source/session work.
- Verified implementation checkpoint: **`9e7f9091dbbdbbf93a183d535e9d4ba7928ca614`**. Exact run evidence belongs below and in Acceptance; do not infer success from an earlier SHA.
- Direct GitHub Actions access is available in this continuation. Earlier worker statements that Actions was unavailable are historical.
- Canonical V4.1 report is available as the user-attached `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1(3).md`; no repository copy or missing text is invented.
- Accepted amendments AR01–AR05 remain unchanged. Active coordination: `ahaggag037/Workspace-for-teera.ai` issue #1. Kimi K3 reported being paused; this checkpoint does not claim a fresh independent review.

## Current truth
B012 source/session prerequisites are additive. **Task17 is NOT ACTIVATED.** MainActivity still owns production URI/STT state in Compose memory. Production NVIDIA transport, magnitude-based STT timing interpretation and legacy preview/hard-burn paths remain the comparator.

Implemented foundations: typed clocks/semantic ownership, shadow segmentation and translation validation, versioned provider wire identity, manual-over-machine history, semantic SRT, display timing/CueIndex, atomic session/receipt recovery, immutable SourceAttachment/SourceSnapshot, live URI probe, exact audio-track/PCM evidence, redacted STT transport observation, private-copy source capture, source adoption/resume composition.

### Repairs in this continuation
1. `961017f275ce0e49086ba6d32c3e882be52a3d84`: test failure helper now throws AssertionError on unexpected success, providing compiler-guaranteed control flow.
2. `6e4567826112236df1be03ce7a14ba53425c196e`: structural STT redaction test pins instance fields and excludes compiler/plugin static metadata. No raw response field was added or allowed.
3. `b92e3807ee95757f8230fe8009d246ac02fe1c3e`: coordinator freezes session/revision/epoch/binding/attachment/snapshot token before evidence construction; store checks it under the same lock as bind. Automatic source CAS retry removed. Revision-only drift also fails conservatively. Deterministic interleaving tests replace the old retry-policy tests.
4. Same source repair: locked manifest/attachment read classifies known file/codec failures as CORRUPT_BINDING. Missing/invalid manifest or session and probe/programming errors remain failures. Null/corrupt attachment never triggers URI I/O. Resume probe still compares its pre-I/O token with the current post-I/O manifest.
5. Application ownership: `9e7f9091dbbdbbf93a183d535e9d4ba7928ca614` (verified). One lazy synchronized store in private no-backup storage; explicit UUID session creation; no UI resume activation or implicit legacy reassignment.

## Exact verification
- Source repair `b92e3807ee95757f8230fe8009d246ac02fe1c3e`: Android CI `37799782433` SUCCESS (unit/lint/APK/signature), API35 recovery `37799782358` SUCCESS. Downloaded recovery artifact `11560945445`, SHA-256 `096394b5204fd9baa8c1dd80b4c3cb6ba3024d63401697a007f8617f19fbf30d`; XML: 44 tests, zero failures/errors/skips, all 13 coordinator regressions present.
- Test-only checkpoint `6e4567826112236df1be03ce7a14ba53425c196e`: Android CI `37799149989` SUCCESS.
- Application owner `9e7f9091dbbdbbf93a183d535e9d4ba7928ca614`: Android CI `37800765078` SUCCESS (unit/lint/APK/signature), API35 recovery `37800765148` SUCCESS. Recovery artifact `11560447678`, SHA-256 `a31d1fd7a47a095cf144c967553e74964707879e81e5cf111600a2fa1e5608bc`; downloaded digest and XML checked: 46 tests, zero failures/errors/skips, both application-owner cases and all 13 coordinator cases present. Verification reports `11560567077` / `c88a5496ca7fc42786ade6211485edd9eb641e2c425abfb6eeea728c072c0e73`; APK archive `11560637192` / `1232a3a632591077731bb0861ab13f2ae3770a94640da8426194a87f52c184e8` (archive digests, not APK-file digest).

Preserved failure history:
- `0da65a11…`: Android CI `37794571807` failed `compileDebugUnitTestKotlin` (missing return in STT helper); API35 recovery `37794571819` succeeded. Recovery artifact `11558462438`, SHA-256 `1de276f7d54066a3aac30e344eaf291f5b98226870cbdf17302622e235de1804` was relay-reported; run conclusion directly verified.
- `961017f…`: Android CI `37798155240` compiled tests, then failed 1/196 (`observationGraphIsStructurallyRedacted`, exact instance-vs-static field assertion). Direct job log `113382938244`.
- Earlier visibility failures `37792006164` / `37792432928` and repairs remain in Git history; they are not the current frontier.

## Experiment gates (unchanged)
| Gate | State | Missing evidence |
|---|---|---|
| X001 | HARNESS_READY_PARTIAL | Real provider response bound to exact sample plus independently verified presentation origin. No production timing-unit/origin activation. |
| X002 | HARNESS_READY | Paired legacy/semantic outputs and blind Arabic scoring of N25 corpus. Corpus existence is not quality proof. |
| X003 | HARNESS_EXECUTED_PARTIAL | API29/API35 native geometry passed; actual-size human readability remains NOT_MEASURED. |
| X004 | SHADOW_FOUNDATION_ONLY | Decoded device frame/audio/cue-switch/preview-lag parity. |
| X005 | PASS at frozen anchor only | `b7948478eacef67b2552d4540e4358152cf72dd6`, Android CI `37730225777`, recovery `37730225779`; artifact `11530100316`, SHA-256 `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant green runs are compatibility, not a new PASS anchor. |
| X006 | NOT_STARTED / correctness foundations | Target-device memory/allocation/hash/capture-copy/seek/export measurements. |

X003 exact geometry anchor: `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, workflow `37753803179`; API29 artifact `11540110191` (`0dd433168be3374fef389da24335d791ebee76f831a99cc651c48e5a5e4e2333`), API35 `11539188153` (`3371e1c104fad08529326da806c8dce0dd720ff22137c5a183c819c84dede13f`). See experiment docs for scope.

## Remaining correctness boundaries
- The new token covers **coordinator evidence construction → adoption**, not arbitrary earlier preparation/network work supplied by a caller. A future operation must capture its source lease before preparation/submission and carry it through adoption.
- SourceAttachmentBuilder copies/hashes one provider stream and derives metadata from that copy, then deletes its own file. SttAudioPreparer currently decodes a separately opened URI. Therefore attachment fingerprint → decoded source continuity is not yet established end-to-end. Matching track metadata is not byte identity. Keep controller activation blocked until preparation owns the same validated stable capture/descriptor.
- The preparer is not selected-range aware; it selects the first audio track/window. Do not invent range membership checks. Its observed sourceStartUs and PCM-derived duration do not establish edit-list/priming/continuity correctness (X001).
- Fixed legacy WAV path remains. Detailed transport hashes/sends pinned bytes; snapshot factory rehashes the WAV and rejects replacement. This is fail-closed sample identity, not a general concurrent-operation scheduler.
- UNVERIFIED snapshots persist text/confidence/provenance, never interpreted legacy word intervals. Resume source availability does not validate every stored snapshot/translation artifact or authorize rendering.
- Full source copying/hashing and detailed sample ByteArray are not yet performance-qualified; maintain bounded sample scope and measure X006 before full-video expansion.
- One process-owned store is the supported writer model; do not create another store on the live application root or add multi-process components without revisiting locking.

## RESUME HERE
1. Refetch live HEAD and exact CI before writing. Fix a new red checkpoint first; never force-push or touch frozen/main branches.
2. Next safe B012 operation: prove and implement **same-capture source identity through audio preparation and snapshot adoption** under an operation-start source token. First falsifier: same URI/track/duration but replaced bytes must never mint a snapshot for the old attachment. Do not alter timing interpretation, enable Task17, or claim X001 while adding this ownership boundary.
3. After that boundary and source/snapshot reopen validation are owned and tested, design controller/UI wiring against existing gates. Avoid a giant rewrite and never auto-reassign legacy/manual history.

## Rollback
Revert each source-composition or application-owner batch as a unit. Preserve durable files and schema versions; no downgrade, deletion or automatic reattachment. Old source composition is not safe to activate after rollback. No new experiment acceptance or production renderer/provider activation is implied.
