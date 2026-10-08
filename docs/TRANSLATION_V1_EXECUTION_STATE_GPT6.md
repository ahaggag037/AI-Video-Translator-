# Translation V1 — Execution State

## Identity and canonical continuation
- Repository: `ahaggag037/AI-Video-Translator-`.
- Active branch: `build/p0g-gpt6-cleanroom-v1`.
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.
- Canonical architecture: user-supplied `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`; accepted amendments AR-01..AR-05 are in `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`.
- Canonical successor entry point: **`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`**.
- Acceptance/evidence index: `docs/TRANSLATION_V1_ACCEPTANCE.md`.
- Experiment evidence: `docs/experiments/**`.

## Current Git / verification truth
- Reconciliation base before the successor-handoff docs commit: **`4d763ed07c4ab4594049086b101f71630b65e6c5`**, a documentation-only child of the verified implementation checkpoint.
- Android CI `37841156900` at `4d763ed...`: **SUCCESS**.
- Latest verified implementation checkpoint: **`6781ee1689181fe240f3fa63b5c1085517c51a69`**.
- At `6781ee...`: Android CI `37839877093` **SUCCESS** (unit/lint/APK/signature) and API35 recovery `37839876832` **SUCCESS**.
- Recovery artifact `11577287792`, SHA-256 `0cb66921e4ba305363e09673d2dd119cc87c5af40a6aa19a942f6c78121290f7`; downloaded XML verified **55 tests, 0 failures/errors/skips**, including all nine same-capture cases.
- Reports `11577088867` / `aa6cd1e2fbc53344270a127a88828371f5b12ebfc225cf9f8eef0ecd09cc6c72`; APK archive `11576799704` / `894f742af53d707c60b978ddd34cdac87a7bb9a517c9fccdd399569c5b37e77a` (archive digest).
- Do not confuse the current docs HEAD with the last verified implementation checkpoint; refetch live HEAD before every write.

## Current activation truth
**NOT RELEASE-READY. Task17 NOT ACTIVATED.** MainActivity/legacy production flow still owns important URI/STT/UI state in Compose memory. Production NVIDIA transport, legacy magnitude-based STT timing interpretation, legacy NVIDIA text translation, and legacy preview/hard-burn remain the comparator where the corresponding experiment gate is not accepted.

The new B012/session/source work is additive infrastructure, not proof of full application resume or UI activation.

## Verified foundations
- Typed clocks and semantic/presentation ownership primitives.
- Manual-over-machine history/invalidation foundations.
- Translation segmentation/planning/validation harnesses and N25 corpus.
- Semantic SRT/display timing/CueIndex foundations.
- Native Arabic/layout geometry controls.
- Atomic session/receipt recovery foundation and frozen X005 PASS anchor.
- Immutable `SourceAttachment` / `SourceSnapshot`, source identity probe, exact audio-track/PCM evidence, redacted STT transport observation.
- Pre-build source/epoch fencing and typed corrupt-binding resume composition.
- One application-owned session store model.
- `SourceSnapshotOperation` owns one private source capture through real native decode, fake-or-real STT callback, and fenced adoption. It rejects source identity/metadata drift, unsupported non-full-source selection, and early/late epoch drift in the verified fixture matrix. It does not reopen the URI for decode.

## Current correctness boundaries
1. **Durable STT attempt/recovery is not implemented yet.** A remote STT success can still become an unknown billable outcome if the process dies before the accepted result is durably recoverable. There is no automatic retry; preserve that safety.
2. Reopen source availability does not validate every stored snapshot/translation artifact or authorize rendering.
3. UNVERIFIED snapshots preserve text/confidence/provenance but do not contain interpreted legacy word intervals. X001 still owns real unit/origin activation.
4. MainActivity remains the production UI owner; do not wire Task17 before durable STT recovery + reopen semantics are correct.
5. Full-source copy/hash and detailed sample ByteArray behavior are not performance-qualified; X006 remains open.
6. One process-owned store is the supported writer model. Do not create a second live store owner or multi-process writer without revisiting locking.

## Experiment gates
| Gate | State | Missing evidence / rule |
|---|---|---|
| X001 | `HARNESS_READY_PARTIAL` | Real hosted response bound to exact sample + independently verified presentation origin. No production timing reinterpretation. |
| X002 | `HARNESS_READY` | Paired legacy/semantic outputs + blind Arabic human scoring. No production semantic switch. |
| X003 | `HARNESS_EXECUTED_PARTIAL` | Native geometry exists; actual-size human readability remains unmeasured. |
| X004 | `SHADOW_FOUNDATION_ONLY` | Missing decoded-device frame/audio/cue-switch/preview-lag parity. |
| X005 | **PASS at frozen exact anchor only** | `b7948478eacef67b2552d4540e4358152cf72dd6`; CI `37730225777`, recovery `37730225779`, artifact `11530100316` / `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant green runs are compatibility only. |
| X006 | `NOT_STARTED / correctness foundations` | Target-device memory/allocation/hash/capture-copy/seek/export qualification missing. |

X003 geometry anchor remains `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, workflow `37753803179`; detailed artifact IDs/digests remain in Acceptance/experiment docs.

## External coordination state
- **No model-coordination channel is currently active.** GitHub ModelBridge `ahaggag037/Workspace-for-teera.ai#1` is `PAUSED / ARCHIVE-EVIDENCE`; historical GPT-6/Kimi/Astra messages are useful evidence, but old continuous-worker commands are superseded and require explicit reactivation.
- Notion page `3f392048-ecf1-8134-8179-ef60faa3767b` is now **ARCHIVE / SUPERSEDED** and marked not to resume from it.
- Base44 `CloudWorkspace` (`6ac7947d9b26a35b08dc7c81`) is an **experimental, non-authoritative observer**; project continuation does not depend on it. Direct sandbox inspection is unavailable on the current free Base44 plan.
- GPT-6 project worker: `PAUSED`.
- Kimi K3: `PAUSED` after platform interruption; prior review findings remain evidence.
- Sol clean-room reviewer: `PAUSED`; Notion relay archived.
- Astra: stopped after usage limit; the final reconciliation/handoff was completed by the successor assistant rather than falsely attributing work to a still-running Astra session.
- Project-related automations are disabled: `ModelBridge GPT Relay`, `ModelBridge Mission`, `CLW GPT6 Continuous Guard`, and `مراقبة تقدم Astra`.
- Old prompts/runtime contracts are historical reference or separate experiments, not current operating authority.

## RESUME HERE — single frontier
Before any Task17/UI wiring, implement **durable STT attempt/recovery ownership around `SourceSnapshotOperation`**.

Use an STT-specific durable lifecycle equivalent to:
`PREPARED → SENT → RECEIVED → ADOPTED`.

Required semantics:
- `RECEIVED` accepted result must survive process death before adoption and be reused on reopen without another provider call.
- `SENT` with no durable accepted response reopens as `UNKNOWN_REMOTE_OUTCOME`.
- **No blind automatic repost** from unknown remote outcome.
- Preserve source/request token identity, redaction, and UNVERIFIED clock rules.

First falsifiers:
1. fake accepted STT result is durably `RECEIVED`; kill before manifest adoption; reopen must reuse it and provider call count remains unchanged;
2. persist `SENT`; kill before durable response; reopen must report unknown remote outcome and must not auto-submit.

Startup algorithm for the successor:
1. refetch live branch HEAD and exact CI;
2. read `TRANSLATION_V1_SUCCESSOR_HANDOFF.md` first;
3. inspect Acceptance/experiments only when a gate/evidence decision requires it;
4. fix exact red CI first if present;
5. otherwise implement the STT-attempt falsifier above;
6. checkpoint exact SHA/CI/state before context pressure; never force-push.

## Rollback / non-goals
Revert source-composition/application-owner batches as coherent units only. Preserve durable files/schema versions; do not auto-reattach or delete history on downgrade. No current state authorizes a renderer/provider/timing/semantic gate promotion. Dubbing, multi-voice, diarization, source separation, local models, lip sync, and the separate ModelBridge product experiment are not the current V1 frontier.
