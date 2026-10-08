# Translation & Subtitle V1 — Acceptance Ledger

Status: **NOT RELEASE-READY**. This file records evidence already observed on `build/p0g-gpt6-cleanroom-v1`; it does not convert a harness into a PASS and it does not activate experiment-gated behavior.

Canonical source: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (V4.1 Final). Frozen product baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`.

## Experiment gates

| Experiment | Current status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | HARNESS_READY_PARTIAL | Raw timing evidence inspector preserves schema/path/value type/lexeme and verbatim response evidence; declared-ms/declared-seconds/nonzero-origin synthetic controls exist. No real provider+audio-origin/PCM/PTS canary bundle; production raw-unit inference remains unchanged. |
| X002 translation quality | HARNESS_READY | N25 source corpus shape is 48 synthetic passages with exactly 12 adversarial + 12 integrity cases. No paired legacy/semantic provider outputs and no blind Arabic human scoring; no PASS. |
| X003 Arabic layout/readability | HARNESS_EXECUTED_PARTIAL | Exact native controls passed on API29 and API35 at `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, workflow `37753803179`. Artifacts: API29 `11540110191` / `sha256:0dd433168be3374fef389da24335d791ebee76f831a99cc651c48e5a5e4e2333`; API35 `11539188153` / `sha256:3371e1c104fad08529326da806c8dce0dd720ff22137c5a183c819c84dede13f`. All 21 current synthetic geometry fixtures report `FITS` on each API with the pinned font hash, but human readability and preview/export parity are explicitly `NOT_MEASURED`; no X003 PASS. |
| X004 preview/export parity | NOT_STARTED / shadow media foundation only | Pure export-media contract evaluator exists; no decoded audio-marker, frame, rotation/content-rect, cue-switch or preview-lag device evidence. |
| X005 persistence/recovery | **PASS** | Exact PASS anchor `b7948478eacef67b2552d4540e4358152cf72dd6`; Android CI `37730225777` and X005 Android Recovery `37730225779` both green. Recovery artifact ID `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant compatibility at X003 head is also green, but that does not move the exact X005 PASS anchor. |
| X006 performance/scalability | NOT_STARTED | CueIndex correctness exists, but no canonical N23/N28 device memory/allocation/random-seek/export-stress measurement. |

## Quality gates

| Gate | State | Evidence / release blocker |
|---|---|---|
| QG1 Ownership / invalidation | PARTIAL | Manual revisions outrank machine history; recovered/retranslated candidates preserve manual truth; stale response fencing exists. Full §11 invalidation table/default UI workflow is not yet accepted end-to-end. |
| QG2 Translation quality | BLOCKED_BY_X002 | Requires N25 blind Arabic review and zero critical factual errors in reviewed subset. Source-corpus shape alone is not a quality result. |
| QG3 Untrusted source / provider protocol | PARTIAL | Provider roles/body/transport contract and response taxonomy are pinned; adversarial source corpus exists. Literal translation/security behavior still needs X002 execution. |
| QG4 Unicode | PARTIAL_GREEN | Android ICU/native boundary controls now execute successfully on API29/API35 at the X003 evidence SHA. Broader device/readability acceptance remains X003-gated. |
| QG5 Containment | PARTIAL_GREEN / BLOCKED_BY_X003 | Current native synthetic geometry controls prove fail-closed contained `FITS` outcomes for the tested matrix on API29/API35. This is not yet the complete accepted raster/device/readability verdict. |
| QG6 Readability | BLOCKED_BY_X003 | N26 actual-size Arabic human readability has not been measured; artifact metadata explicitly says `humanReadability = NOT_MEASURED`. |
| QG7 SRT | PARTIAL_GREEN | Deterministic SRT tests cover ASCII timestamps under Arabic locale, exported-range origin, semantic-unwrapped policy, unsafe arrow rejection and fail-closed sub-ms collapse. Final clock-origin confidence remains tied to X001; do not claim complete end-to-end timing acceptance yet. |
| QG8 Recovery / security | **PASS at X005 anchor** | Fake-provider submission counting, crash/replay, late callback, ENOSPC, corruption, schema restore and durable PREPARED/SENT/RECEIVED fencing executed successfully on Android at `b7948478…`. Descendant X005 recovery workflow is green at `1195e9e1…`. |
| QG9 Provenance / cache | PARTIAL | Exact request signatures, endpoint/body/transport contract binding and recovery plan self-consistency are covered. Full session/source provenance and default resume are still B012 work. |
| QG10 Parity / media | BLOCKED_BY_X004 | B009 shadow media evaluator does not replace decoded-device parity/audio evidence. X003 artifacts explicitly record `previewExportParity = NOT_MEASURED`. Legacy export remains production reference. |
| QG11 P0-F CI / regression | BASELINE_PASS; CURRENT_HEAD_GREEN | Frozen baseline CI remains historical PASS. At exact X003 head `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, Android CI run `37753803170`, X005 Android Recovery `37753803211`, and X003 Native Layout Controls `37753803179` all completed successfully. |
| QG12 Performance | BLOCKED_BY_X006 | No N23/N28 device profiler evidence; correctness tests are not a performance PASS. |
| QG13 Exceptional semantic warnings | NON_BLOCKING / PARTIAL | Deterministic integrity warnings/review states exist; broad out-of-corpus correction UX remains later review work. |

## Durable-session / Task17 status

X005 passing removes the recovery experiment blocker but does not make durable resume safe by itself.

Manifest-v2 checkpoint `527cf889387dda7e997563d7cc1a6e800b95bcb8` separates manifest schema v2 from entry/receipt v1 and maps legacy v1 manifests to explicit `LEGACY_UNBOUND`. Subsequent B012 work adds fenced immutable source-attachment persistence without activating controller/UI resume.

Task17 remains **not activated** until the complete durable source-attachment/snapshot contract and controller/UI ownership required for safe reopen are accepted. Before default resume, source-dependent work must distinguish permission missing, source missing, source changed, available, and legacy-unbound states; a persisted URI string alone is not proof that media access survived.

## Current release blockers

1. X001 real provider + audio-clock evidence before production STT unit/origin mapping changes.
2. X002 paired provider outputs + blind Arabic review before semantic segmentation/profile activation.
3. X003 physical-device/human readability and remaining acceptance evidence before production renderer activation; pinned font/native API29/API35 controls are now executed evidence, not the remaining blocker by themselves.
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
