# Translation & Subtitle V1 — Acceptance

**NOT RELEASE-READY. Task17 NOT ACTIVATED.** This file is the current evidence/gate index, not a chronological journal. Historical detail remains in Git/CI and `docs/experiments/`.

Canonical successor entry point: **`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`**. External coordination surfaces (Notion, ModelBridge, Base44) remain non-authoritative unless the owner explicitly reactivates them.

Repository: `ahaggag037/AI-Video-Translator-`  
Active branch: `build/p0g-gpt6-cleanroom-v1`  
Frozen P0-F: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`  
Canonical architecture: user-supplied `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`; AR-01..AR-05 remain accepted in `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`.

## Current verified implementation checkpoint

**`253eb617ce7ef32b7f5be3d12bbb8e0c905be633`** — `fix(B012): keep STT attempt identity stable across profiles`

Exact descendant verification at this SHA:
- Android CI `37859703696`: **SUCCESS** — unit tests, lint, debug APK build, APK existence/signature/checksum and report upload all passed.
- X005 Android Recovery `37859703620`: **SUCCESS** on API35 instrumentation.
- Recovery artifact `11586211706`, archive digest `sha256:00b7513f254c7baabc30bfd5e4ef24a8377c4881747bfc7a45e9bda34e13a4a9`.
- Downloaded recovery XML independently checked: **58 tests, 0 failures, 0 errors, 0 skips**; `SourceSnapshotOperationInstrumentedTest` contributes **12 tests**, including durable `RECEIVED` recovery without provider resubmission, `SENT` unknown-remote-outcome/no-repost, and profile-upgrade no-repost coverage.

Immediate parent implementation checkpoint:
- `9a3f2c677bd8bd8445954f96338e70a76a3b0ba1` — introduced the durable STT attempt journal and recovery lifecycle. Android CI `37858541293` and X005 Android Recovery `37858541340` both succeeded.

Earlier same-capture checkpoint retained for provenance:
- `6781ee1689181fe240f3fa63b5c1085517c51a69`: Android CI `37839877093` SUCCESS; API35 recovery `37839876832` SUCCESS; artifact `11577287792` / `0cb66921e4ba305363e09673d2dd119cc87c5af40a6aa19a942f6c78121290f7`; downloaded XML: 55 tests, zero failures/errors/skips.

**Scope of the current checkpoint:** additive source/session recovery infrastructure only. It adds a durable STT-specific `PREPARED → SENT → RECEIVED → ADOPTED` lifecycle around `SourceSnapshotOperation`, makes `SENT` without a durable accepted response an explicit unknown remote outcome with **no blind automatic repost**, lets a durable `RECEIVED` snapshot survive process death and be adopted after reopen without another provider call, and keeps the operation identity stable across request-profile upgrades so an older unresolved `SENT` cannot be hidden by a new profile ID. Task17/UI activation, timing activation and experiment acceptance remain unchanged.

## Experiment gates

| Experiment | Status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL` | Track/PCM provenance, raw/synthetic timing evidence and redacted transport-bound sample/response identity exist. Missing real hosted response paired with independently verified presentation origin. Legacy timing-unit inference remains production comparator. |
| X002 translation | `HARNESS_READY` | N25: 48 synthetic passages, including 12 adversarial and 12 integrity cases. Missing paired legacy/semantic outputs + blind Arabic human scoring. |
| X003 layout | `HARNESS_EXECUTED_PARTIAL` | API29/API35 native geometry controls at `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, workflow `37753803179`. Human actual-size readability/parity remains unmeasured. |
| X004 parity | `SHADOW_FOUNDATION_ONLY` | Pure export-media and upright-frame/fit-center foundations only. Missing decoded-device frame/audio/cue-switch/preview-lag evidence. |
| X005 recovery | **PASS at exact frozen anchor only** | `b7948478eacef67b2552d4540e4358152cf72dd6`; CI `37730225777`; API35 recovery `37730225779`; artifact `11530100316` / `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant runs, including `253eb617...`, are compatibility evidence only and do not move the PASS anchor. |
| X006 performance | `NOT_STARTED / correctness foundations` | No target-device profiler, full-source copy/hash, allocation or export-stress qualification. |

## Quality gates

| Gate | State | Acceptance boundary |
|---|---|---|
| QG1 ownership/invalidation | `PARTIAL_GREEN` | Manual/source/response fencing, same-capture ownership and durable STT attempt/recovery now exist; full reopen validation + controller/UI ownership remain. |
| QG2 translation quality | `BLOCKED_BY_X002` | Human fidelity/naturalness/integrity scoring required. |
| QG3 source/provider boundary | `PARTIAL_GREEN` | Exact wire/sample/provenance and no-blind-repost semantics tested; X001/X002 still gate activation. |
| QG4 Unicode | `PARTIAL_GREEN` | Native ICU/text controls on API29/API35; broader device/human matrix incomplete. |
| QG5 containment | `PARTIAL_GREEN` | Native FITS layouts fail closed; X003 acceptance remains partial. |
| QG6 readability | `BLOCKED_BY_X003` | Actual-size low-resolution Arabic review missing. |
| QG7 SRT | `PARTIAL_GREEN` | Locale-independent timestamps, semantic-unwrapped policy and range-origin guards exist; source-clock truth remains X001-gated. |
| QG8 recovery/security | `PASS at X005 anchor` | Descendant STT/source recovery tests are compatibility/extension evidence, not a new X005 anchor or proof of full-project resume. |
| QG9 provenance/cache | `PARTIAL_GREEN` | Attachment/snapshot/sample/request identities, pre-build fencing and durable STT attempt lifecycle exist. Full source/snapshot/project reopen validation and controller ownership remain. |
| QG10 media parity | `BLOCKED_BY_X004` | Legacy renderer/export remains production reference. |
| QG11 P0-F/CI regression | `253eb617... GREEN` | Android CI `37859703696` SUCCESS; CI does not substitute for manual legacy MP4/audio/device verification. |
| QG12 performance | `BLOCKED_BY_X006` | No profiler evidence. |
| QG13 exceptional semantic warnings | `NON_BLOCKING / PARTIAL` | Deterministic integrity warnings exist; broader correction UX deferred. |

## Durable STT attempt/recovery truth

The STT-specific journal is now implemented and verified but remains **not UI-wired**:
- `PREPARED`: durable local intent; transport has not run, so local rebuilding is safe.
- `SENT`: durable before transport callback execution; after process/response loss the remote outcome is unknown and automatic repost is forbidden.
- `RECEIVED`: accepted redacted `SourceSnapshot` is durable before manifest adoption and can be reused after process death without another provider call.
- `ADOPTED`: written only after the manifest points to the exact snapshot.
- Attempt identity is stable across request-profile upgrades, preventing a newer profile from hiding an unresolved older `SENT` operation.
- Raw provider response bodies remain outside the durable journal/snapshot graph.

A bounded liveness caveat remains: a historical `PREPARED` journal written under a different request profile currently fails closed instead of automatically refreshing to the new profile. This cannot duplicate a remote request because PREPARED means transport did not run; it is not an activation blocker but should be handled deliberately if profile migration becomes a live requirement.

## Required before activation/release

1. **Full source/snapshot reopen validation + controller/ViewModel/UI ownership.** Reopen must classify missing/corrupt active snapshot state and current source availability without provider resubmission or semantic mutation; Task17 remains blocked until this path is explicit and tested.
2. X001 before changing STT unit/origin interpretation; UNVERIFIED snapshots must carry no interpreted word intervals.
3. X002 before switching the semantic translation profile; legacy NVIDIA text translation remains comparator.
4. X003 human readability and X004 decoded-device parity before the new renderer becomes default.
5. X006 performance qualification and final B013 regression/device checklist.

Style/edit/re-export must never repeat successful AI work. Preserve manual truth. No raw provider response, key, real source locator or private transcript belongs in logs, relay or docs.
