# Translation & Subtitle V1 — Successor Handoff

> **Canonical operational entry point for a fresh successor model.** Read this first, refetch the live branch, then act only on the delta. This handoff is reconciled against verified implementation checkpoint `253eb617ce7ef32b7f5be3d12bbb8e0c905be633`; the docs commit containing this file will be a documentation-only descendant unless a later exact code/CI checkpoint supersedes it.

## 1. Product mission
V1 incrementally evolves the existing Android AI Video Translator: select a source video, bind the exact source/audio identity, obtain STT text with trustworthy provenance and safe remote-request recovery, translate into Arabic without letting the model alter timing, create durable semantic subtitle state/SRT, preserve manual edits, preview and export while recovery, timing ownership and legacy behavior remain explicit until their experiment gates close. V1 is not a rewrite and is **not release-ready**.

## 2. Repository identity
- Repository: `ahaggag037/AI-Video-Translator-`
- Active branch: `build/p0g-gpt6-cleanroom-v1`
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Canonical architecture: user-supplied `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`
- Accepted amendments: `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md` (AR-01..AR-05)
- Acceptance/evidence index: `docs/TRANSLATION_V1_ACCEPTANCE.md`
- Current operational state: `docs/TRANSLATION_V1_EXECUTION_STATE_GPT6.md`
- Experiment evidence: `docs/experiments/**`
- Never force-push/rewrite the frozen baseline or use `main` as the implementation work branch.

## 3. Verified implementation state

Latest verified code checkpoint:

**`253eb617ce7ef32b7f5be3d12bbb8e0c905be633`** — `fix(B012): keep STT attempt identity stable across profiles`

Exact evidence:
- Android CI `37859703696` — **SUCCESS**: unit tests, lint, debug APK, APK existence/signature/checksum and report upload.
- X005 Android Recovery `37859703620` — **SUCCESS** on API35 instrumentation.
- Recovery artifact `11586211706`, archive digest `sha256:00b7513f254c7baabc30bfd5e4ef24a8377c4881747bfc7a45e9bda34e13a4a9`.
- Downloaded XML independently checked: **58 tests, 0 failures, 0 errors, 0 skips**; `SourceSnapshotOperationInstrumentedTest` executed **12 tests**.

Parent `9a3f2c677bd8bd8445954f96338e70a76a3b0ba1` introduced the durable STT attempt lifecycle. Earlier same-capture code checkpoint `6781ee1689181fe240f3fa63b5c1085517c51a69` remains historical provenance only.

**Rule:** a documentation-only descendant is not a new implementation verification anchor. Refetch live HEAD/CI before every write.

## 4. Architecture invariants
- Incremental evolution; no app rewrite.
- Preserve NVIDIA/Media3 and legacy production comparator until explicit gates close.
- Semantic truth and presentation truth remain separate.
- Translation/model output owns translated text, never timing.
- Timing uses explicit typed clocks/provenance; no guessed STT unit/origin becomes production truth before X001.
- Manual edits remain authoritative; style/re-export must not repeat successful AI work.
- Layout/rendering remains fail-closed where acceptance requires it.
- No blind remote retry when a request may already have been submitted.
- Unknown remote outcome is distinct from known failure.
- One application-owned session store is the current writer model; do not add Room/backend/multi-writer architecture absent a demonstrated blocker and authority.
- No raw provider response, secret, private source locator or private transcript in docs/logs/relay/durable evidence.
- Implemented/tested/reviewed/accepted are distinct states.

## 5. Production / legacy / shadow map
| Surface | Activation | Current truth |
|---|---|---|
| MainActivity URI/STT/UI ownership | `LEGACY_PRODUCTION` | Important state is still legacy/Compose-memory. **Task17 NOT ACTIVATED.** |
| Legacy NVIDIA STT timing interpretation | `LEGACY_PRODUCTION` | Magnitude-based interpretation remains comparator; do not replace before X001. |
| Legacy NVIDIA text translation | `LEGACY_PRODUCTION` comparator | New semantic path is not default before X002. |
| B012 source/session persistence | `IMPLEMENTED_NOT_ACTIVATED` | Strong additive foundation; not full app resume. |
| Same-capture `SourceSnapshotOperation` | `IMPLEMENTED_NOT_ACTIVATED` | Verified private-source/decode/STT/fencing path. |
| Durable STT attempt journal | `IMPLEMENTED_NOT_ACTIVATED` | Verified PREPARED/SENT/RECEIVED/ADOPTED recovery/no-repost semantics. |
| Semantic segmentation/translation validation | `HARNESS_ONLY / SHADOW` | N25 exists; no production switch. |
| Arabic layout/font | `EXPERIMENTAL / SHADOW` | Native geometry evidence exists; human readability incomplete. |
| Preview/export parity | `SHADOW` | Pure foundations; real device parity absent. |
| Semantic SRT | `IMPLEMENTED_NOT_ACTIVATED` | Logic exists; source-clock truth remains X001-gated. |
| X005 recovery foundation | accepted scoped anchor | Does not mean full application resume is complete. |

## 6. Verified foundations
- Typed clock and semantic/presentation ownership primitives.
- Manual-over-machine history/invalidation foundations.
- Provider wire identity + redacted STT observation.
- Translation segmentation/planning/validation harnesses and N25 corpus.
- Semantic SRT/display timing/CueIndex foundations.
- Arabic/native layout geometry controls with API29/API35 evidence.
- Atomic session/translation-receipt recovery foundation and frozen X005 PASS anchor.
- Immutable `SourceAttachment` / `SourceSnapshot`; current URI/source identity probe.
- Exact audio-track/PCM evidence, private same-capture decode and source/epoch fences.
- Typed corrupt-attachment source resume composition.
- Single application-owned session store model.
- Durable STT lifecycle: `PREPARED → SENT → RECEIVED → ADOPTED`.
- `SENT` reopens as unknown remote outcome with no automatic repost.
- `RECEIVED` survives process death and is adopted after reopen with no second provider call.
- Attempt identity survives request-profile upgrades so an older unresolved `SENT` remains discoverable.

## 7. Current incomplete work
1. **Full source/snapshot reopen validation** before Task17/controller ownership.
2. Controller/ViewModel/UI ownership and Task17 activation.
3. X001 real provider/audio/presentation-origin proof.
4. X002 paired legacy/new outputs + blind Arabic human scoring.
5. X003 actual-size human readability.
6. X004 decoded-device preview/export/audio/cue-switch/lag parity.
7. X006 target-device performance qualification.
8. Final release/device/B013 regression checklist.

## 8. Open correctness / residual risks
1. **Reopen completeness:** source availability is typed, and STT attempt recovery is durable, but one complete reopen decision does not yet validate active snapshot integrity + attempt state + current source observation for controller ownership.
2. **Corrupt active snapshot:** a missing/corrupt/oversized snapshot currently fails closed at store read; Task17 needs an explicit tested reopen-blocking classification rather than relying on an unstructured exception.
3. **Timing truth:** X001 real evidence is absent; legacy timing interpretation must remain untouched.
4. **UI ownership:** premature Task17 activation would split durable truth and Compose-memory truth.
5. **Performance:** full-source copy/hash/sample materialization is correctness-first and unqualified on target devices.
6. **PREPARED profile migration liveness:** an old PREPARED-only attempt under a different request profile currently fails closed rather than refreshing. This is safe because transport has not run, but an explicit migration rule may be useful if profile changes become live.

## 9. Experiment gates
| Gate | State | Exact/current evidence | Missing proof / blocks |
|---|---|---|---|
| X001 | `HARNESS_READY_PARTIAL` | Track/PCM + redacted transport/sample/response provenance | Real hosted response + independently verified presentation origin; blocks timing reinterpretation |
| X002 | `HARNESS_READY` | N25 48 synthetic passages incl. 12 adversarial + 12 integrity | Paired old/new outputs + blind Arabic scoring; blocks semantic switch |
| X003 | `HARNESS_EXECUTED_PARTIAL` | API29/API35 geometry anchor `1195e9e...`, workflow `37753803179` | Human actual-size readability; blocks default layout acceptance |
| X004 | `SHADOW_FOUNDATION_ONLY` | Pure media/frame/fit-center foundations | Decoded-device frame/audio/cue-switch/preview-lag parity |
| X005 | **PASS at frozen exact anchor** | `b7948478...`; CI `37730225777`; recovery `37730225779`; artifact `11530100316` / `d302f9...` | Descendant green runs are compatibility only; anchor does not move |
| X006 | `NOT_STARTED / correctness foundations` | none | Target-device memory/allocation/hash/capture-copy/seek/export measurements |

## 10. Persistence / recovery truth
Durable foundations exist for session state, translation receipts, source attachment/snapshot identity, atomic/fenced writes, typed attachment corruption and current source probing. STT now has its own durable lifecycle:
- `PREPARED`: no transport yet.
- `SENT`: transport may have happened; crash/response loss becomes unknown remote outcome; **no blind repost**.
- `RECEIVED`: accepted redacted snapshot durably recoverable before manifest adoption.
- `ADOPTED`: manifest points to the exact snapshot.

This does not by itself make the whole user-visible project resumable; active snapshot integrity + controller/UI ownership remain the next boundary.

## 11. STT truth
- New same-capture operation keeps one private source through native decode and owns a unique WAV for its lifetime.
- Detailed transport binds exact submitted sample digest, typed request profile, accepted result, parser/response hash/status; durable snapshot/journal excludes raw response body.
- UNVERIFIED snapshots contain text/confidence/provenance but no interpreted word intervals.
- Durable unknown-outcome/recovery semantics are implemented and verified at `253eb617...`.
- A request-profile upgrade cannot hide an older unresolved `SENT` attempt.
- No gate or production timing behavior changed.

## 12. Translation truth
Legacy NVIDIA text translation remains production comparator. New segmentation/planning/validation and bounded neighboring SOURCE context remain harness/shadow. No rolling target-translation chain. X002 acceptance requires paired outputs + blind Arabic scoring before switching production.

## 13. Arabic / layout truth
Native API29/API35 geometry controls exist and fail closed, but actual-size human readability—especially low-resolution/vertical Arabic—remains unmeasured. X003 stays partial.

## 14. Preview / export truth
Canonical/upright frame and fit-center/media shadow foundations exist but do not prove real device/VideoView/export/audio/cue parity. Legacy preview/hard-burn remains production reference until X004 acceptance.

## 15. UI / controller ownership
`MainActivity` still owns important production URI/STT/UI state in Compose memory. Application-owned store + B012 operations are prerequisites, not active UI ownership. **Task17 NOT ACTIVATED.**

## 16. Performance truth
X006 is not qualified. Do not expand into broad full-video/chunking architecture on assumptions; measure target devices first when the project reaches that gate.

## 17. V1 required vs deferred
Required before release/activation:
- full source/snapshot reopen validation + controller/UI ownership;
- X001 timing proof;
- X002 translation acceptance;
- X003 human readability;
- X004 device preview/export/audio parity;
- X006 performance qualification;
- final device/regression/release checklist.

Deferred unless later explicitly pulled into V1: dubbing/multi-voice, diarization, source separation, local models, lip sync, broad full-video chunking/background architecture, general ModelBridge product work.

## 18. Do not do
- Do not rewrite architecture or replace NVIDIA/Media3 without a demonstrated blocker.
- Do not add Room/FFmpeg/Hilt/backend merely for cleanliness.
- Do not activate Task17 prematurely.
- Do not reinterpret STT timing before X001.
- Do not switch semantic translation before X002.
- Do not treat geometry as readability or pure transforms as device parity.
- Do not move the X005 PASS anchor from descendant compatibility evidence.
- Do not blind-repost a possibly submitted STT request.
- Do not destroy/reassign manual or legacy history automatically.
- Do not create a second live session-store owner.
- Do not put raw provider/private source data/secrets in docs/logs/relay.
- Do not force-push.

## 19. External Coordination State
- **Canonical project truth:** active Git branch + `TRANSLATION_V1_ACCEPTANCE.md` + `TRANSLATION_V1_EXECUTION_STATE_GPT6.md` + this handoff + experiment docs.
- **Canonical model relay:** NONE ACTIVE.
- GitHub ModelBridge `ahaggag037/Workspace-for-teera.ai#1`: `PAUSED / ARCHIVE-EVIDENCE`; historical review remains useful, old continuous-worker commands are superseded.
- Notion page `3f392048-ecf1-8134-8179-ef60faa3767b`: `ARCHIVE ONLY / SUPERSEDED`.
- Base44 `CloudWorkspace` (`6ac7947d9b26a35b08dc7c81`): `EXPERIMENTAL_PARTIAL / NON-AUTHORITATIVE`; Android-project continuation does not depend on it.
- GPT-6 project worker: paused unless explicitly reactivated.
- Kimi K3: paused after platform interruption unless explicitly reactivated.
- Sol reviewer: paused; old Notion clean-room relay is archive only.
- Astra Work: stopped at usage limit; later state/implementation was continued by the successor assistant.
- Project automations `ModelBridge GPT Relay`, `ModelBridge Mission`, `CLW GPT6 Continuous Guard`, `مراقبة تقدم Astra`: disabled.
- Do not resume from old prompts/relay ACKs/Notion instructions as current authority.

## 20. Exact next frontier

**Full source/snapshot reopen validation before Task17/controller/UI activation.**

Required behavior to prove:
1. `SNAPSHOT_BOUND + valid snapshot`: semantic snapshot survives restart; current source availability still comes from a fresh probe; no timing/render gate is implicitly activated.
2. `SNAPSHOT_BOUND + missing/corrupt/oversized/identity-mismatched snapshot`: typed/local reopen-blocking result, zero provider/STT calls, no manifest/manual/semantic mutation.
3. `ATTACHMENT_BOUND + SENT`: expose `UNKNOWN_REMOTE_OUTCOME`, zero provider calls.
4. `ATTACHMENT_BOUND + RECEIVED`: recover/adopt with zero provider calls.
5. Attachment corruption remains `CORRUPT_BINDING`; stale probe token remains `STALE_OBSERVATION`; invalid manifest/session/programmer errors remain failures rather than being mislabeled corruption.

### First falsifier
Create a valid `SNAPSHOT_BOUND` session, restart, then separately remove/corrupt/oversize the active snapshot object. The new reopen assessment must return a typed snapshot/binding blocking state without calling source/STT provider code and without mutating manifest/manual/translation data. A valid control must reopen unchanged.

## 21. Resume algorithm
1. Refetch branch HEAD and exact CI; do not assume this docs SHA is still live.
2. If current CI is red, fix the earliest concrete failure first.
3. Otherwise inspect only the existing reopen path: `SourceResumeEvaluator`, `SourceSessionCoordinator`, `TranslationSessionStore`, `SourceSnapshotOperation` and their tests.
4. Implement the smallest typed reopen composition that satisfies the falsifier above; do not create a second persistence/controller architecture.
5. Run focused tests, then Android CI + API35 recovery at a coherent checkpoint.
6. Reconcile Acceptance/Execution/Handoff before moving to Task17/UI ownership.
