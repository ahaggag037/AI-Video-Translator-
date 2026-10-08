# Translation & Subtitle V1 — Successor Handoff

> **Canonical operational entry point for a fresh successor model.** Read this first, then refetch the live branch before acting. This document records the reconciled state as of the docs-only handoff based on branch HEAD `4d763ed07c4ab4594049086b101f71630b65e6c5`. The handoff commit itself will be a docs-only descendant. The latest verified implementation checkpoint remains `6781ee1689181fe240f3fa63b5c1085517c51a69` unless newer exact evidence appears.

## 1. Product mission
V1 evolves the existing Android AI Video Translator incrementally: select a source video, extract/identify the correct audio, obtain STT text with trustworthy provenance, translate into Arabic without letting the model alter timing, create durable semantic subtitle state/SRT, support review/manual edits, preview, and export while preserving recovery, source identity, timing ownership, and legacy behavior until experiment gates are closed. V1 is not a rewrite and is not yet release-ready.

## 2. Repository identity
- Repository: `ahaggag037/AI-Video-Translator-`
- Active branch: `build/p0g-gpt6-cleanroom-v1`
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Canonical architecture: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`
- Accepted amendments: `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md` (AR-01..AR-05 accepted)
- Evidence/gate index: `docs/TRANSLATION_V1_ACCEPTANCE.md`
- Current engineering state: `docs/TRANSLATION_V1_EXECUTION_STATE_GPT6.md`
- Frozen/main branches must not be force-pushed, rewritten, or treated as working branches.

## 3. Live Git / verification state
### Reconciliation base
- Branch HEAD before this handoff commit: `4d763ed07c4ab4594049086b101f71630b65e6c5`
- Meaning: docs-only checkpoint, parent `6781ee1689181fe240f3fa63b5c1085517c51a69`.
- Android CI at `4d763ed...`: run `37841156900` — **SUCCESS**.

### Latest verified implementation checkpoint
- Code checkpoint: `6781ee1689181fe240f3fa63b5c1085517c51a69`
- Android CI `37839877093` — **SUCCESS** (unit/lint/APK/signature).
- API35 recovery `37839876832` — **SUCCESS**.
- Recovery artifact `11577287792`, SHA-256 `0cb66921e4ba305363e09673d2dd119cc87c5af40a6aa19a942f6c78121290f7`.
- Downloaded XML: **55 tests, 0 failures, 0 errors, 0 skips**, including all nine same-capture cases.
- Verification reports `11577088867` / `aa6cd1e2fbc53344270a127a88828371f5b12ebfc225cf9f8eef0ecd09cc6c72`.
- APK archive `11576799704` / `894f742af53d707c60b978ddd34cdac87a7bb9a517c9fccdd399569c5b37e77a` (archive digest, not APK-file digest).

**Rule:** current docs HEAD and last verified implementation checkpoint are different concepts. Never infer unverified code success from a docs descendant or from an ancestor.

## 4. Architecture invariants
- Incremental evolution; do not rewrite the app.
- Preserve NVIDIA/Media3 and the legacy production comparator until explicit gates close.
- Semantic truth and presentation truth remain separate.
- Translation/model output owns translated text, not timing.
- Timing uses explicit typed clocks/provenance; no guessed STT unit/origin becomes production truth before X001.
- Manual user edits remain authoritative where defined; style/re-export must not repeat successful AI work.
- Layout remains fail-closed; no accepted clipping.
- No blind remote retry when a request may have been submitted.
- Request/provider outcomes distinguish known failure from unknown remote outcome.
- Single-writer persistence is the V1 ceiling; do not add Room/backends/major frameworks without a demonstrated blocker and authority.
- No raw provider response, API key, private source locator, or private transcript in logs/relay/durable evidence.
- Experiment implementation, tests, reviewer agreement, and gate acceptance are distinct.

## 5. Production / legacy / shadow map
| Surface | Activation | Current truth |
|---|---|---|
| MainActivity URI/STT/UI ownership | `LEGACY_PRODUCTION` | Important production state is still Compose-memory/legacy flow. Task17 is **NOT ACTIVATED**. |
| Legacy NVIDIA STT timing interpretation | `LEGACY_PRODUCTION` | Magnitude-based interpretation remains comparator; do not replace before X001. |
| Legacy NVIDIA text translation | `LEGACY_PRODUCTION` comparator | New semantic path is not default before X002. |
| B012 session/source persistence foundations | `IMPLEMENTED_NOT_ACTIVATED` | Strong additive foundation; not full app resume. |
| SourceSnapshotOperation same-capture path | `IMPLEMENTED_NOT_ACTIVATED` | Verified native ownership/fencing fixtures; no UI activation. |
| Semantic segmentation/translation validation | `HARNESS_ONLY` / `SHADOW` | N25 corpus exists; no production switch. |
| Arabic layout/font system | `EXPERIMENTAL` / `SHADOW` | Native geometry evidence exists; human readability incomplete. |
| Preview/export geometry/media parity | `SHADOW` | Pure foundations only; device parity absent. |
| SRT semantic generation | `IMPLEMENTED_NOT_ACTIVATED` | Logic exists; source-clock correctness remains X001-gated. |
| X005 recovery foundation | accepted experiment anchor | Does **not** mean full application resume is complete. |

## 6. Completed verified foundations
- Typed clock/semantic ownership primitives.
- Manual-over-machine history/invalidation foundations.
- Versioned provider wire identity and redacted STT transport observation.
- Translation segmentation/planning/validation harnesses and N25 corpus.
- Semantic SRT/display timing/CueIndex foundations.
- Arabic/native layout geometry controls with API29/API35 evidence.
- Atomic session/receipt recovery foundation and frozen X005 PASS anchor.
- Immutable `SourceAttachment` / `SourceSnapshot` and live URI/source identity probe.
- Exact audio-track/PCM evidence and source provenance fences.
- Typed corrupt-binding source resume composition.
- One application-owned session store model.
- Same-capture `SourceSnapshotOperation`: one private source capture through native decode, fake-or-real STT callback, and fenced adoption; captured source identity/metadata drift and epoch drift fail closed in tested cases.
- SourceSnapshotOperation verified at `6781ee...` with the 55-test API35 recovery run above.

## 7. Current incomplete work
1. **Durable STT attempt/recovery ownership is missing.** SourceSnapshotOperation can call STT, but there is no durable STT-specific attempt journal that makes remote submission/result recovery idempotent across process death.
2. Full source/snapshot reopen validation is incomplete beyond current source-availability/corruption boundaries.
3. Controller/ViewModel/UI ownership and Task17 activation are not done.
4. X001 real provider/audio/presentation-origin proof is missing.
5. X002 paired legacy/new outputs + blind Arabic human scoring is missing.
6. X003 actual-size human readability is missing.
7. X004 decoded-device preview/export/audio/cue-switch/lag parity is missing.
8. X006 target-device performance qualification is missing.
9. Final release/device checklist/B013 remains after the above gates.

## 8. Open correctness / residual risks
Ranked by consequence:
1. **STT duplicate-cost / unknown-remote-outcome risk:** process death after remote success but before durable accepted-result recovery can lead a future caller to submit again unless an explicit attempt journal exists. There is no automatic retry today; preserve that safety.
2. **Reopen completeness:** source availability does not yet validate every stored snapshot/translation artifact or authorize rendering.
3. **Timing truth:** real NVIDIA response + independently verified presentation origin is still absent; legacy timing reinterpretation must remain untouched.
4. **UI ownership:** new persistent/source composition is not yet the production controller path; premature Task17 activation can split truth between Compose memory and durable state.
5. **Performance:** full-source copy/hash and detailed sample ByteArray behavior are not qualified on target devices.
6. **Media/readability evidence:** X003/X004 require human/device evidence, not more pure geometry alone.

## 9. Experiment table
| Gate | Current state | Exact accepted/current evidence | Missing proof | Blocks |
|---|---|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL` | Synthetic/raw timing evidence + track/PCM provenance + redacted transport-bound sample/response identity | Real hosted response bound to exact sample + independently verified presentation origin | Production STT unit/origin reinterpretation |
| X002 translation | `HARNESS_READY` | N25: 48 synthetic passages, incl. 12 adversarial + 12 integrity | Paired legacy/semantic outputs + blind Arabic scoring | Semantic translation profile activation |
| X003 layout | `HARNESS_EXECUTED_PARTIAL` | API29/API35 geometry anchor `1195e9e...`, workflow `37753803179`; artifacts recorded in Acceptance | Actual-size human Arabic readability/parity | Default authored layout/render acceptance |
| X004 parity | `SHADOW_FOUNDATION_ONLY` | Pure export-media + upright-frame/fit-center models | Decoded-device frame/audio/cue-switch/preview-lag parity | New preview/export path default |
| X005 recovery | **PASS at frozen exact anchor** | `b7948478...`; CI `37730225777`; recovery `37730225779`; artifact `11530100316` / `d302f9...` | Full application resume remains broader than this anchor | Recovery foundation accepted only within scoped anchor |
| X006 performance | `NOT_STARTED / correctness foundations` | No target qualification | Memory/allocation/hash/capture-copy/seek/export measurements | Release performance acceptance |

## 10. Quality gates — current important states
- QG1 ownership/invalidation: `PARTIAL` — manual/source/response fencing exists; durable STT attempt/recovery + end-to-end controller ownership remain.
- QG2 translation quality: `BLOCKED_BY_X002`.
- QG3 provider boundary: `PARTIAL_GREEN`.
- QG4 Unicode: `PARTIAL_GREEN`.
- QG5 containment: `PARTIAL_GREEN`.
- QG6 readability: `BLOCKED_BY_X003`.
- QG7 SRT: `PARTIAL_GREEN`; source-clock truth still X001-gated.
- QG8 recovery/security: PASS only at X005 anchor; do not extrapolate to full-project resume.
- QG9 provenance/cache: `PARTIAL_GREEN`; durable STT attempt recovery missing.
- QG10 media parity: `BLOCKED_BY_X004`.
- QG11 regression: verified implementation checkpoint `6781ee...`; docs-only `4d763ed...` CI is green.
- QG12 performance: `BLOCKED_BY_X006`.
- QG13 semantic warnings: `NON_BLOCKING / PARTIAL`.

## 11. Persistence / recovery truth
Durable foundations exist for session state, receipts, source attachment/snapshot identity, atomic/fenced writes, typed corruption, and source availability. One application-owned store is the supported writer model. What is **not** durable enough yet is the STT attempt lifecycle around remote submission/result adoption. Do not interpret X005 PASS or the 55 source-operation tests as proof that every user-visible project state can be reopened end-to-end.

## 12. STT truth
- **Preparation/source bytes:** new same-capture operation retains one private source copy through native decode; each operation owns its WAV. Legacy entry point can still use the old fixed WAV path.
- **Submitted audio identity:** detailed transport pins/hashes the bytes it sends; snapshot construction/fences reject mismatched replacement.
- **Request profile/provenance:** typed request profile + accepted result + parser/hash/status provenance exists; durable observation excludes raw provider response body.
- **Privacy:** raw diagnostic response evidence must not be logged/relayed/persisted.
- **Timing:** UNVERIFIED snapshots do not gain interpreted legacy word intervals. X001 owns real timing unit/origin acceptance.
- **Attempt recovery:** **missing frontier**. There is no STT-specific durable PREPARED/SENT/RECEIVED/ADOPTED ownership around `SourceSnapshotOperation` yet.
- **Retry:** no blind auto-retry. A request that may have been submitted but has no durable accepted response must be treated as unknown remote outcome.

## 13. Translation truth
Legacy NVIDIA text translation remains the production comparator. New segmentation/planning/validation and bounded neighboring SOURCE context (AR-01) are harness/shadow work. Do not create rolling target-translation chains. Every output-affecting context input participates in request identity. X002 acceptance requires paired outputs and blind Arabic human scoring before a production switch.

## 14. Arabic / layout truth
Bundled deterministic Arabic-capable font system is the architectural requirement; exact family/weight remains experiment-gated. Native API29/API35 geometry controls exist and tested FIT/OVERFLOW behavior fails closed. This is **not** proof of actual-size human readability, especially low-resolution/vertical cases; X003 remains partial.

## 15. Preview / export truth
Canonical/upright frame and fit-center/media shadow foundations exist. They do not prove real VideoView/device raster behavior or export/audio/cue timing parity. Legacy preview/hard-burn remains the production reference until X004 is accepted.

## 16. UI / controller ownership
`MainActivity` still owns important production URI/STT/UI state in Compose memory. The application-owned store and new source/session operations are prerequisites, not active UI ownership. **Task17 NOT ACTIVATED.** Do not wire the new path into default UI until durable STT attempt/recovery and reopen semantics are correct and the relevant gates remain respected.

## 17. Performance truth
X006 is not qualified. Full-source copying/hashing and sample ByteArray materialization are correctness-oriented foundations with unmeasured target-device cost. Keep bounded sample scope; do not expand to full-video/chunking on performance assumptions without measurement.

## 18. V1 required vs deferred expansion
### Required before V1 release/activation
- Durable STT attempt/recovery ownership.
- Reopen validation + controller/UI ownership.
- X001 timing evidence before clock reinterpretation.
- X002 translation acceptance before semantic switch.
- X003 human readability.
- X004 device preview/export/audio parity.
- X006 performance qualification.
- Final regression/device/release checklist.

### Deferred / not required merely to close this V1 state
- Dubbing/multi-voice.
- Diarization/source separation.
- Local models.
- Lip sync.
- Broad full-video chunking/background architecture unless later V1 acceptance explicitly requires it.
- General ModelBridge product work.

## 19. Do not do
- Do not rewrite architecture or replace NVIDIA/Media3 without a demonstrated blocker.
- Do not add Room/FFmpeg/Hilt/backend merely for cleanliness.
- Do not activate Task17 prematurely.
- Do not reinterpret STT time before X001.
- Do not switch semantic translation before X002.
- Do not treat geometry as readability or pure transforms as parity.
- Do not move X005 PASS anchor based on descendant compatibility runs.
- Do not blind-repost a possibly submitted STT request.
- Do not destroy/reassign manual or legacy history automatically.
- Do not create a second live session store owner.
- Do not put raw provider response/private source data/secrets in docs, relay, or logs.
- Do not force-push or edit the frozen baseline/main as implementation work.

## 20. External Coordination State
### Canonical project state
- Git branch `build/p0g-gpt6-cleanroom-v1`
- `docs/TRANSLATION_V1_ACCEPTANCE.md`
- `docs/TRANSLATION_V1_EXECUTION_STATE_GPT6.md`
- `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`
- `docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`
- `docs/experiments/**` for experiment evidence

### Canonical successor entry point
`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`

### Canonical model relay
**NONE ACTIVE.** `ahaggag037/Workspace-for-teera.ai` issue `#1` is retained as **PAUSED / ARCHIVE-EVIDENCE**. It contains valuable Kimi/GPT-6/Astra findings, but old `CONTINUOUS-MISSION-ACTIVE` instructions are superseded and must not wake workers automatically. Future model collaboration requires explicit owner reactivation.

### Notion
`ARCHIVE ONLY / SUPERSEDED`. Page `3f392048-ecf1-8134-8179-ef60faa3767b` was renamed/marked archive and explicitly points successors back to repository state. Its old clean-room protocol is historical evidence only.

### ModelBridge GitHub relay
`PAUSED / ARCHIVE-EVIDENCE`. Keep review history; do not treat old worker commands as current authority.

### Base44 observer
`EXPERIMENTAL_PARTIAL / NON-AUTHORITATIVE`. App `CloudWorkspace` (`6ac7947d9b26a35b08dc7c81`) still exists, but the observer experiment is not required for Android-project continuation. Direct sandbox inspection is unavailable on the current free Base44 plan; do not spend project time repairing it unless the owner explicitly resumes the separate ModelBridge product experiment.

### Agent status
- GPT-6 project worker: `PAUSED / superseded as active worker by later Astra continuation; no automatic worker assumed now`.
- Kimi K3: `PAUSED` (platform pause); prior findings remain evidence.
- Sol reviewer: `PAUSED / Notion relay archived`.
- Astra: `STOPPED by usage limit after verified handoff work`; this final state reconciliation is completed by the successor assistant, not falsely attributed to an active Astra session.

### Automations
Project orchestration watchers are **DISABLED** at reconciliation: `ModelBridge GPT Relay`, `ModelBridge Mission`, `CLW GPT6 Continuous Guard`, and `مراقبة تقدم Astra`. Do not assume any background worker is alive. Reactivation must be explicit.

### Old prompts/contracts
Old GPT-6/Kimi/Sol/Astra continuous-worker prompts, AEK/runtime experiments, and ModelBridge activation prompts are `HISTORICAL_REFERENCE` or `SEPARATE_EXPERIMENT`, not current operating authority. Current continuation authority is the canonical architecture + Acceptance/Execution + this handoff + live Git evidence.

### Do not resume from
- Archived Notion clean-room page.
- Old ModelBridge `CONTINUOUS-MISSION-ACTIVE` comments.
- Base44 observer state.
- Old embedded SHAs/prompts without live refetch.

### Future multi-model collaboration
Explicitly choose roles/ownership, refetch live HEAD, reopen one coordination channel intentionally, and post a new activation checkpoint. Never infer liveness from historical ACKs.

## 21. Exact next frontier
**Implement durable STT attempt/recovery ownership around `SourceSnapshotOperation` before any Task17/UI resume wiring.** Use an STT-specific durable attempt record/state machine rather than miscasting translation-unit receipts.

First required falsifiers:
1. Persist a fake accepted STT result as `RECEIVED`, kill/reopen before manifest adoption, then prove reopen reuses the saved accepted result and does **not** call the provider again.
2. Persist `SENT`, kill before any durable response, reopen as `UNKNOWN_REMOTE_OUTCOME`, and prove there is **no automatic repost**.

Conceptual lifecycle: `PREPARED → SENT → RECEIVED → ADOPTED`. Preserve request/source token identity, redaction, and UNVERIFIED timing rules. A pre-send cancellation check cannot prove zero billing once submission races the network.

## 22. Resume algorithm for the next model
1. Refetch `build/p0g-gpt6-cleanroom-v1`; record exact live HEAD and current CI before writing.
2. Read this handoff. If HEAD differs from the handoff docs-only descendant, inspect only the delta.
3. Read Acceptance/Execution only where the delta or a gate decision requires it; do not reconstruct old chats.
4. If exact live HEAD is red, fix the earliest concrete failure first.
5. Otherwise start the durable STT attempt/recovery falsifier above; do not touch UI activation first.
6. Use the cheapest discriminating test, then coherent CI/recovery checkpoint.
7. Update Acceptance/Execution/Handoff only with exact evidence; keep gate states scoped.
8. Before each push refetch HEAD; never force-push or overlap another active writer.
