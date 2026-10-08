# Translation & Subtitle V1 — Acceptance Ledger

Status: **NOT RELEASE-READY**. This file records evidence already observed on `build/p0g-gpt6-cleanroom-v1`; it does not convert a harness into a PASS and it does not activate experiment-gated behavior.

Canonical source: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (V4.1 Final). Frozen product baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.

## Experiment gates

| Experiment | Current status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | HARNESS_READY_PARTIAL | Raw timing evidence inspector now preserves schema/path/value type/lexeme and verbatim response evidence; declared-ms/declared-seconds/nonzero-origin synthetic controls exist. No real provider+audio-origin/PCM/PTS canary bundle; production raw-unit inference remains unchanged. |
| X002 translation quality | HARNESS_READY | N25 source corpus shape is 48 synthetic passages with exactly 12 adversarial + 12 integrity cases. No paired legacy/semantic provider outputs and no blind Arabic human scoring; no PASS. |
| X003 Arabic layout/readability | NOT_STARTED / shadow foundation only | Native boundary/layout shadow code exists, but no bundled pinned Noto Sans Arabic file/license/hash and no API29 + API35/36 actual-size readability/containment verdict. |
| X004 preview/export parity | NOT_STARTED / shadow media foundation only | Pure export-media contract evaluator exists; no decoded audio-marker, frame, rotation/content-rect, cue-switch or preview-lag device evidence. |
| X005 persistence/recovery | **PASS** | Exact PASS anchor `b7948478eacef67b2552d4540e4358152cf72dd6`; Android CI `37730225777` and X005 Android Recovery `37730225779` both green. Recovery artifact ID `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. |
| X006 performance/scalability | NOT_STARTED | CueIndex correctness exists, but no canonical N23/N28 device memory/allocation/random-seek/export-stress measurement. |

## Quality gates

| Gate | State | Evidence / release blocker |
|---|---|---|
| QG1 Ownership / invalidation | PARTIAL | Manual revisions outrank machine history; recovered/retranslated candidates preserve manual truth; stale response fencing exists. Full §11 invalidation table/default UI workflow is not yet accepted end-to-end. |
| QG2 Translation quality | BLOCKED_BY_X002 | Requires N25 blind Arabic review and zero critical factual errors in reviewed subset. Source-corpus shape alone is not a quality result. |
| QG3 Untrusted source / provider protocol | PARTIAL | Provider roles/body/transport contract and response taxonomy are pinned; adversarial source corpus exists. Literal translation/security behavior still needs X002 execution. |
| QG4 Unicode | HARNESS_READY | Text policy + Android ICU/native boundary foundations exist. Device/font matrix acceptance is still X003-gated. |
| QG5 Containment | BLOCKED_BY_X003 | No accepted pinned-font/device raster containment verdict. |
| QG6 Readability | BLOCKED_BY_X003 | N26 actual-size Arabic reader recovery has not been measured. |
| QG7 SRT | PARTIAL_GREEN | Deterministic SRT tests cover ASCII timestamps under Arabic locale, exported-range origin, semantic-unwrapped policy, unsafe arrow rejection and fail-closed sub-ms collapse. Final clock-origin confidence remains tied to X001; do not claim complete end-to-end timing acceptance yet. |
| QG8 Recovery / security | **PASS at X005 anchor** | Fake-provider submission counting, crash/replay, late callback, ENOSPC, corruption, schema restore and durable PREPARED/SENT/RECEIVED fencing executed successfully on Android at `b7948478…`. |
| QG9 Provenance / cache | PARTIAL | Exact request signatures, endpoint/body/transport contract binding and recovery plan self-consistency are covered. Full session/source provenance and default resume are still B012 work. |
| QG10 Parity / media | BLOCKED_BY_X004 | B009 shadow media evaluator does not replace decoded-device parity/audio evidence. Legacy export remains production reference. |
| QG11 P0-F CI / regression | BASELINE_PASS; CURRENT_HEAD_PENDING | Frozen baseline CI remains historical PASS. Multiple clean-room checkpoints are green; current B009 head `95f37e86b6b5467bfc6a63c4842d72ca3f9849a0` Android CI `37732638702` was still running when this ledger was written. |
| QG12 Performance | BLOCKED_BY_X006 | No N23/N28 device profiler evidence; correctness tests are not a performance PASS. |
| QG13 Exceptional semantic warnings | NON_BLOCKING / PARTIAL | Deterministic integrity warnings/review states exist; broad out-of-corpus correction UX remains later review work. |

## Durable-session / Task17 status

X005 passing removes the recovery experiment blocker but does not make durable resume safe by itself.

Active manifest-v2 checkpoint `527cf889387dda7e997563d7cc1a6e800b95bcb8` separates manifest schema v2 from entry/receipt v1 and maps legacy v1 manifests to explicit `LEGACY_UNBOUND`. It passed Android CI `37732068184` and X005 recovery instrumentation `37732068152`.

Task17 remains **not activated** because the repository still lacks the complete durable source-attachment/snapshot contract and controller/UI ownership required for safe reopen. Before default resume, source-dependent work must distinguish permission missing, source missing, source changed, available, and legacy-unbound states; a persisted URI string alone is not proof that media access survived.

## Current release blockers

1. X001 real provider + audio-clock evidence before production STT unit/origin mapping changes.
2. X002 paired provider outputs + blind Arabic review before semantic segmentation/profile activation.
3. X003 pinned font provenance/hash and API29/API35+ device readability/containment acceptance.
4. B012 complete source attachment/snapshot persistence + safe resume/controller wiring.
5. X004 real-device media/parity evidence before new snapshot renderer/export becomes default.
6. X006 device performance/memory/allocation evidence after renderer/snapshot path exists.
7. B013 final regression/device checklist only after the preceding activation gates are actually accepted.

## Scope discipline

- Keep the proven legacy NVIDIA text request/profile as the production comparator until X002 authorizes a replacement path.
- Keep production STT time interpretation unchanged until X001 supplies raw-unit + presentation-origin evidence.
- Keep the legacy preview/hard-burn renderer as the production visual path until X003/X004 authorize the new snapshot path.
- Keep successful semantic/manual records durable; style/layout/export changes must not trigger AI calls.
- Never promote a gate from HARNESS_READY/PARTIAL to PASS from code presence alone; record exact SHA, run/artifact/device evidence and the experiment verdict.
