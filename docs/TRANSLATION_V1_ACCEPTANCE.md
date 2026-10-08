# Translation & Subtitle V1 — Acceptance Ledger

Status: **NOT RELEASE-READY**. This file records evidence already observed on `build/p0g-gpt6-cleanroom-v1`; it does not convert a harness into a PASS and it does not activate experiment-gated behavior.

Canonical design name: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (V4.1 Final). Frozen product baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`. The canonical file itself is not currently surfaced through the active project/repository tools; no missing clauses are inferred from memory.

## Experiment gates

| Experiment | Current status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | HARNESS_READY_PARTIAL | Raw timing evidence preserves schema/path/raw JSON type/lexeme and verbatim response SHA; synthetic declared-ms/declared-seconds/nonzero-origin controls exist. B012 descendants now also expose the exact decoded input track, PCM frame count, WAV/sample provenance and a content-addressed hosted STT request profile. No real provider canary atomically bound to independently verified presentation-origin/PCM evidence; production magnitude-based unit inference remains unchanged. No PASS. |
| X002 translation quality | HARNESS_READY | N25 source corpus shape is 48 synthetic passages with exactly 12 adversarial + 12 integrity cases. No paired legacy/semantic provider outputs and no blind Arabic human scoring. No PASS. |
| X003 Arabic layout/readability | HARNESS_EXECUTED_PARTIAL | Native controls passed API29/API35 at `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, workflow `37753803179`. Artifacts: API29 `11540110191` / `sha256:0dd433168be3374fef389da24335d791ebee76f831a99cc651c48e5a5e4e2333`; API35 `11539188153` / `sha256:3371e1c104fad08529326da806c8dce0dd720ff22137c5a183c819c84dede13f`. Human readability and preview/export parity remain `NOT_MEASURED`. No PASS. |
| X004 preview/export parity | SHADOW_FOUNDATION_ONLY | Pure export-media evaluator plus canonical upright-frame / preview fit-center transform foundation exist (`0149f55e…` and descendant transform work). No decoded-device audio-marker/frame/cue-switch/preview-lag parity evidence. No PASS. |
| X005 persistence/recovery | **PASS** | Exact PASS anchor `b7948478eacef67b2552d4540e4358152cf72dd6`; Android CI `37730225777` and X005 Android Recovery `37730225779` green. Recovery artifact `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Newer B012 recovery/device runs are compatibility evidence only and do not move the exact X005 PASS anchor. |
| X006 performance/scalability | NOT_STARTED / correctness foundations only | CueIndex correctness and stronger source identity exist, but no canonical device memory/allocation/random-seek/full-source-hash/export-stress measurement. No PASS. |

## Quality gates

| Gate | State | Evidence / release blocker |
|---|---|---|
| QG1 Ownership / invalidation | PARTIAL | Manual revisions outrank machine history; stale response fencing and durable source attachment/snapshot ownership exist. Production source builder/controller/default reopen workflow is not accepted end-to-end. |
| QG2 Translation quality | BLOCKED_BY_X002 | Requires N25 blind Arabic review and zero critical factual errors in reviewed subset. |
| QG3 Untrusted source / provider protocol | PARTIAL_GREEN | Translation request wire identity is pinned; hosted STT request endpoint/form semantics now have a typed content-addressed profile and exact multipart regression at verified checkpoint `a29c8330…`. Literal translation/security behavior still needs X002 execution. |
| QG4 Unicode | PARTIAL_GREEN | Android ICU/native controls execute successfully on API29/API35. Broader device/readability acceptance remains X003-gated. |
| QG5 Containment | PARTIAL_GREEN / BLOCKED_BY_X003 | Native synthetic geometry controls prove fail-closed contained `FITS` outcomes for the tested matrix, not complete human/device acceptance. |
| QG6 Readability | BLOCKED_BY_X003 | N26 actual-size Arabic human readability has not been measured. |
| QG7 SRT | PARTIAL_GREEN | Deterministic SRT tests cover ASCII timestamps under Arabic locale, exported-range origin, semantic-unwrapped policy, unsafe arrow rejection and fail-closed sub-ms collapse. End-to-end clock confidence remains X001-gated. |
| QG8 Recovery / security | **PASS at X005 anchor** | Fake-provider submission counting, crash/replay, late callback, ENOSPC, corruption, schema restore and durable PREPARED/SENT/RECEIVED fencing executed successfully on Android at `b7948478…`. B012 source/snapshot descendants continue to pass the API35 recovery suite. |
| QG9 Provenance / cache | PARTIAL_GREEN | Translation endpoint/body/transport identity, source full-byte identity, active track/range ownership, immutable SourceSnapshot schema, exact decoded-track/PCM provenance, accepted-parse/raw-response separation and hosted STT request-profile identity are now implemented/tested. Missing: production source builder + same-response detailed network path + end-to-end controller/default resume. |
| QG10 Parity / media | BLOCKED_BY_X004 | Shadow export/frame transform models do not replace decoded-device parity/audio evidence. Legacy renderer/export remains production reference. |
| QG11 P0-F CI / regression | BASELINE_PASS; VERIFIED_CODE_CHECKPOINT_GREEN | Frozen baseline remains historical PASS. Latest verified code checkpoint `a29c83303a7b21527db1f389f036424468c87e9e` passed Android CI `37773725083` (unit, lint, APK, signature/checksum, artifacts). Reports `11549586172` / `sha256:748bbcaeb2ed5fb40d4f9f00ba010703ec315fb6c35cd923599ae43cd2a80a2f`; APK `11549261464` / `sha256:b6939d89e605fe325270f155279bc82070db2695e094e1b3cade193a6c041492`. |
| QG12 Performance | BLOCKED_BY_X006 | No device profiler evidence. Full-source hashing strength is accepted for identity correctness, not yet performance-qualified. |
| QG13 Exceptional semantic warnings | NON_BLOCKING / PARTIAL | Deterministic integrity warnings/review states exist; broad out-of-corpus correction UX remains later work. |

## Durable-session / Task17 status

X005 passing removes the recovery experiment blocker but does not make durable resume safe by itself.

Implemented B012 evidence now includes:
- manifest schema v2 + explicit `LEGACY_UNBOUND` at `527cf889387dda7e997563d7cc1a6e800b95bcb8`;
- immutable source attachment persistence at `97681630071a96ec41e29d516bc22094e79aa751`;
- immutable SourceSnapshot persistence at `ffb0ee8711a7edd8063245a12f4637725dddfb2b`, with corrected descendant recovery fixture `294fe599…`;
- live full-byte `ContentResolver` source probe at `9a5f2d8dd3fcacd677d03ab91c9a95f2b435d3d2`;
- exact decoded input-track + PCM-frame provenance at verified `c1267f0aa7be7c0115ca90a2c1377e84f3c29c72`;
- accepted legacy STT parse paired with raw timing evidence, verified at `280d0d4e09f5ca9ae44954dc6f2605ad25bc6435`;
- hosted NVIDIA STT request profile + exact multipart contract, verified at `a29c83303a7b21527db1f389f036424468c87e9e`.

Exact B012 descendant evidence:
- Snapshot/store: Android CI `37764997539`; API35 recovery `37764997633`; artifact `11544187616`, digest `sha256:421184089e9377befcba273fbb274b3662a53cf45d67e1291dcb9ffbfc12e843`.
- Live source probe: Android CI `37766064823`; API35 recovery `37766064846`; artifact `11544906188`, digest `sha256:5d7ca34a1553574c6cac5f00d6e924d0b14d1a7455bec1c20f8d358310a5d986`.
- Audio track/PCM provenance: Android CI `37772215065` full green.
- Detailed STT evidence separation: Android CI `37772600052` full green.
- STT request-profile wire contract: Android CI `37773725083` full green.

Task17 remains **NOT ACTIVATED**. The current UI still owns URI/STT state in Compose memory and no production session controller owns source selection/reopen. A persisted URI alone is never permission/identity proof; source-dependent work must re-probe and distinguish AVAILABLE / PERMISSION_MISSING / SOURCE_MISSING / SOURCE_CHANGED / unsupported/I/O/legacy-unbound states.

An UNVERIFIED durable snapshot may preserve accepted text/word/confidence and provenance, but it **must not persist legacy interpreted word offsets**. Production STT unit/origin mapping remains X001-gated.

## Current release blockers

1. X001 real provider + independently grounded audio-clock canary evidence before production STT unit/origin mapping changes.
2. X002 paired provider outputs + blind Arabic review before semantic segmentation/profile activation.
3. X003 human/device readability evidence before new renderer activation.
4. B012 pure fail-closed snapshot factory, production source-attachment construction, same-response detailed STT network path, and safe controller/default resume wiring.
5. X004 real-device preview/export media parity evidence before new renderer/export becomes default.
6. X006 device performance/memory/allocation/full-source-hash/export-stress evidence.
7. B013 final regression/device checklist after preceding activation gates are accepted.

## Scope discipline

- Keep the proven legacy NVIDIA text translation profile as the production comparator until X002 authorizes replacement.
- Keep production STT time interpretation unchanged until X001 supplies real raw-unit + presentation-origin evidence.
- Keep the legacy preview/hard-burn renderer as production visual path until X003/X004 authorize the new path.
- Do not infer selected-range membership from `SttAudioPreparer` yet; it is not range-aware. Move range ownership into preparation before enforcing such a fence.
- Keep raw provider response in-memory diagnostic evidence only; durable storage keeps its digest/provenance, not raw unredacted body.
- Keep successful semantic/manual records durable; style/layout/export changes must not trigger AI calls.
- Never promote HARNESS_READY/PARTIAL to PASS from code presence alone; record exact SHA/run/artifact/device evidence and the actual experiment verdict.
