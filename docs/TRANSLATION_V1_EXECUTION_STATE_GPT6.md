# Translation V1 — Execution State

## Identity and canonical continuation
- Repository: `ahaggag037/AI-Video-Translator-`.
- Active branch: `build/p0g-gpt6-cleanroom-v1`.
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.
- Canonical architecture: user-supplied `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`; AR-01..AR-05 remain accepted in `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`.
- Canonical successor entry point: **`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`**.
- Acceptance/evidence index: `docs/TRANSLATION_V1_ACCEPTANCE.md`.
- Experiment evidence: `docs/experiments/**`.

## Current Git / verification truth

Latest verified implementation checkpoint:

**`253eb617ce7ef32b7f5be3d12bbb8e0c905be633`** — `fix(B012): keep STT attempt identity stable across profiles`.

Exact verification:
- Android CI `37859703696`: **SUCCESS** — unit tests, lint, debug APK, APK existence/signature/checksum and report upload all passed.
- X005 Android Recovery `37859703620`: **SUCCESS** on API35 instrumentation.
- Recovery artifact `11586211706`, archive digest `sha256:00b7513f254c7baabc30bfd5e4ef24a8377c4881747bfc7a45e9bda34e13a4a9`.
- Downloaded XML independently checked: **58 tests, 0 failures, 0 errors, 0 skips**; `SourceSnapshotOperationInstrumentedTest` executed **12 tests**.

Parent `9a3f2c677bd8bd8445954f96338e70a76a3b0ba1` introduced the journal; Android CI `37858541293` and recovery `37858541340` succeeded. Earlier same-capture checkpoint `6781ee1689181fe240f3fa63b5c1085517c51a69` remains useful provenance but is no longer the latest verified implementation checkpoint.

Refetch live HEAD before every write. A later documentation-only descendant must not be confused with the verified code SHA above.

## Current activation truth

**NOT RELEASE-READY. Task17 NOT ACTIVATED.** MainActivity/legacy production flow still owns important URI/STT/UI state in Compose memory. Production NVIDIA transport, legacy magnitude-based STT timing interpretation, legacy NVIDIA text translation and legacy preview/hard-burn remain the comparator wherever the corresponding experiment gate is not accepted.

The B012/session/source work is additive infrastructure, not proof of full application resume or UI activation.

## Verified foundations
- Typed clocks and semantic/presentation ownership primitives.
- Manual-over-machine history/invalidation foundations.
- Translation segmentation/planning/validation harnesses and N25 corpus.
- Semantic SRT/display timing/CueIndex foundations.
- Native Arabic/layout geometry controls.
- Atomic session/translation-receipt recovery foundation and frozen X005 PASS anchor.
- Immutable `SourceAttachment` / `SourceSnapshot`, live source-identity probe, exact audio-track/PCM evidence and redacted STT transport observation.
- Pre-build source/epoch fencing and typed corrupt-binding source-resume composition.
- One application-owned session store model.
- `SourceSnapshotOperation` owns one private source capture through native decode, STT callback and fenced adoption; it rejects source identity/metadata drift, unsupported non-full-source selection and early/late epoch drift in the verified fixture matrix.
- **Durable STT attempt lifecycle:** `PREPARED → SENT → RECEIVED → ADOPTED` around `SourceSnapshotOperation`.
- `SENT` without a durable accepted response reopens as `UNKNOWN_REMOTE_OUTCOME`; the operation does not automatically repost.
- A durable `RECEIVED` snapshot survives process death before manifest adoption and is reused on reopen without another provider call.
- Operation identity is stable across request-profile upgrades, so a newer app/profile cannot hide an unresolved older `SENT` and accidentally submit again.

## Current correctness boundaries
1. **Full source/snapshot reopen validation is still incomplete.** Current source availability/corrupt-attachment logic does not yet produce one complete reopen decision that validates the active snapshot/attempt state before controller/UI ownership.
2. `SNAPSHOT_BOUND` with a missing/corrupt/oversized/identity-mismatched active snapshot currently fails closed at the store boundary but still needs an explicit tested reopen classification/composition suitable for Task17 rather than an unstructured exception path.
3. `ATTACHMENT_BOUND + SENT` is now safely durable/unknown and no-repost; `ATTACHMENT_BOUND + RECEIVED` can recover/adopt without a provider call. The next reopen surface should expose these states without weakening their semantics.
4. UNVERIFIED snapshots preserve text/confidence/provenance but contain no interpreted legacy word intervals. X001 still owns real unit/origin activation.
5. MainActivity remains the production UI owner. Do not wire Task17 until reopen ownership is explicit and tested.
6. Full-source copy/hash and sample materialization are not performance-qualified; X006 remains open.
7. One process-owned store is the supported writer model. Do not create a second live store owner or multi-process writer without revisiting locking.
8. Bounded liveness caveat: a historical PREPARED-only STT journal under a different request profile fails closed instead of automatically refreshing. Because PREPARED precedes transport, this is safe/no-billing-duplication but may need an explicit migration rule later.

## Experiment gates
| Gate | State | Missing evidence / rule |
|---|---|---|
| X001 | `HARNESS_READY_PARTIAL` | Real hosted response bound to exact sample + independently verified presentation origin. No production timing reinterpretation. |
| X002 | `HARNESS_READY` | Paired legacy/semantic outputs + blind Arabic human scoring. No production semantic switch. |
| X003 | `HARNESS_EXECUTED_PARTIAL` | Native geometry exists; actual-size human readability remains unmeasured. |
| X004 | `SHADOW_FOUNDATION_ONLY` | Missing decoded-device frame/audio/cue-switch/preview-lag parity. |
| X005 | **PASS at frozen exact anchor only** | `b7948478eacef67b2552d4540e4358152cf72dd6`; CI `37730225777`, recovery `37730225779`, artifact `11530100316` / `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant green runs are compatibility only. |
| X006 | `NOT_STARTED / correctness foundations` | Target-device memory/allocation/hash/capture-copy/seek/export qualification missing. |

## External coordination state
- **No model-coordination channel is currently active.** GitHub ModelBridge `ahaggag037/Workspace-for-teera.ai#1` remains `PAUSED / ARCHIVE-EVIDENCE`; old continuous-worker commands are superseded and require explicit owner reactivation.
- Notion page `3f392048-ecf1-8134-8179-ef60faa3767b` is `ARCHIVE / SUPERSEDED` and must not be used as current state.
- Base44 `CloudWorkspace` (`6ac7947d9b26a35b08dc7c81`) is an experimental non-authoritative observer; project continuation does not depend on it.
- GPT-6 project worker, Kimi K3 and Sol reviewer remain paused unless explicitly reactivated.
- Astra Work stopped at its usage limit; subsequent cleanup and implementation were performed by the successor assistant rather than attributed to Astra.
- Project-related automations (`ModelBridge GPT Relay`, `ModelBridge Mission`, `CLW GPT6 Continuous Guard`, `مراقبة تقدم Astra`) are disabled.

## RESUME HERE — single frontier

Before Task17/controller/UI activation, implement **full source/snapshot reopen validation** over the now-durable source + STT attempt state.

Minimum reopen semantics to preserve/test:
1. `SNAPSHOT_BOUND` + valid snapshot: reopen can expose a valid semantic snapshot, but source availability still needs the current URI probe and does **not** authorize X001 timing/render activation.
2. `SNAPSHOT_BOUND` + missing/corrupt/oversized/identity-mismatched active snapshot: return a typed/local reopen-blocking result; do not call provider, do not mutate manifest/manual/translation state.
3. `ATTACHMENT_BOUND + SENT`: expose `UNKNOWN_REMOTE_OUTCOME`; **zero provider calls**.
4. `ATTACHMENT_BOUND + RECEIVED`: recover/adopt the durable snapshot with **zero provider calls**, then continue under the resulting current manifest.
5. Attachment/source probe token drift remains `STALE_OBSERVATION`; current `CORRUPT_BINDING` semantics for attachment corruption remain distinct from invalid session/manifest/programmer errors.
6. Reopen success is still not Task17 activation and does not move X001–X006 gates.

### First discriminating falsifier
Create/reopen a `SNAPSHOT_BOUND` session, corrupt or remove only the active snapshot object, then execute the new reopen assessment. It must produce a typed snapshot/binding corruption/blocking result without source-provider/STT calls and without modifying the manifest or semantic/manual files. A valid snapshot control must survive restart unchanged.

### Startup algorithm
1. Refetch branch HEAD and exact CI.
2. Read this file / `TRANSLATION_V1_SUCCESSOR_HANDOFF.md` only for current truth; inspect older evidence only for a concrete decision.
3. Fix exact red CI first if present.
4. Otherwise implement the reopen falsifier above using the existing `SourceResumeEvaluator`, `SourceSessionCoordinator`, `TranslationSessionStore` and durable STT attempt state; do not invent a second store or broad controller architecture.
5. Run focused tests, then Android CI + API35 recovery at a coherent checkpoint.
6. Reconcile durable docs before moving to UI ownership. Never force-push.

## Rollback / non-goals
Revert B012 source/STT-recovery batches as coherent units only. Preserve durable files/schema versions; do not auto-reattach or delete history on downgrade. No current state authorizes renderer/provider/timing/semantic gate promotion. Dubbing, multi-voice, diarization, source separation, local models, lip sync and the separate ModelBridge product experiment are not the current V1 frontier.
