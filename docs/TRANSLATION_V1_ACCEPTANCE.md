# Translation & Subtitle V1 — Acceptance

**NOT RELEASE-READY. Task17 NOT ACTIVATED.** Evidence index, not a chronological journal. Historical details live in Git/CI and `docs/experiments/`.

Canonical successor entry point: **`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`**. External coordination surfaces (Notion, ModelBridge, Base44) are non-authoritative unless explicitly reactivated; current status is recorded in the successor handoff.

Branch: `build/p0g-gpt6-cleanroom-v1`. Frozen P0-F: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`. Canonical V4.1 report is available in this session as a user attachment. AR01–AR05 remain accepted; no architecture amendment in this checkpoint.

## Current verified checkpoint
`6781ee1689181fe240f3fa63b5c1085517c51a69`

- Android CI `37839877093` SUCCESS (unit/lint/APK/signature) and API35 recovery `37839876832` SUCCESS at `6781ee1689181fe240f3fa63b5c1085517c51a69`. Recovery artifact `11577287792`, SHA-256 `0cb66921e4ba305363e09673d2dd119cc87c5af40a6aa19a942f6c78121290f7`; downloaded archive digest and XML verified: 55 tests, zero failures/errors/skips, including all nine same-capture cases. Reports `11577088867` / `aa6cd1e2fbc53344270a127a88828371f5b12ebfc225cf9f8eef0ecd09cc6c72`; APK archive `11576799704` / `894f742af53d707c60b978ddd34cdac87a7bb9a517c9fccdd399569c5b37e77a` (archive digest).

- Source repair `b92e3807ee95757f8230fe8009d246ac02fe1c3e`: Android CI `37799782433` SUCCESS (unit/lint/APK/signature), API35 recovery `37799782358` SUCCESS. Downloaded recovery artifact `11560945445`, SHA-256 `096394b5204fd9baa8c1dd80b4c3cb6ba3024d63401697a007f8617f19fbf30d`; XML: 44 tests, zero failures/errors/skips, all 13 coordinator regressions present.
- Test-only checkpoint `6e4567826112236df1be03ce7a14ba53425c196e`: Android CI `37799149989` SUCCESS.
- Application owner `9e7f9091dbbdbbf93a183d535e9d4ba7928ca614`: Android CI `37800765078` SUCCESS (unit/lint/APK/signature), API35 recovery `37800765148` SUCCESS. Recovery artifact `11560447678`, SHA-256 `a31d1fd7a47a095cf144c967553e74964707879e81e5cf111600a2fa1e5608bc`; downloaded digest and XML checked: 46 tests, zero failures/errors/skips, both application-owner cases and all 13 coordinator cases present. Verification reports `11560567077` / `c88a5496ca7fc42786ade6211485edd9eb641e2c425abfb6eeea728c072c0e73`; APK archive `11560637192` / `1232a3a632591077731bb0861ab13f2ae3770a94640da8426194a87f52c184e8` (archive digests, not APK-file digest).

Scope: compile/test repair; pre-construction source adoption fencing; typed corrupt-binding resume; one application-owned session store; one privately owned source through native decode and STT adoption. These are additive prerequisites, not proof of end-to-end durable resume, translation quality, clock mapping or visual parity.

## Experiment gates
| Experiment | Status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | HARNESS_READY_PARTIAL | Synthetic/raw timing evidence, actual track/PCM provenance and redacted transport-bound sample/response identity exist. Missing real hosted response paired with independently verified presentation origin. Legacy timing-unit inference unchanged. |
| X002 translation | HARNESS_READY | N25: 48 synthetic passages, including 12 adversarial and 12 integrity cases. Missing paired outputs and blind Arabic scoring. |
| X003 layout | HARNESS_EXECUTED_PARTIAL | API29/API35 native controls at `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`, run `37753803179`. Artifacts `11540110191` / `0dd433168be3374fef389da24335d791ebee76f831a99cc651c48e5a5e4e2333` and `11539188153` / `3371e1c104fad08529326da806c8dce0dd720ff22137c5a183c819c84dede13f`. Human readability and parity NOT_MEASURED. |
| X004 parity | SHADOW_FOUNDATION_ONLY | Pure export-media and upright-frame/fit-center models only. Missing decoded-device audio/frame/cue-switch/preview-lag evidence. |
| X005 recovery | **PASS at exact frozen anchor** | `b7948478eacef67b2552d4540e4358152cf72dd6`; CI `37730225777`, API35 recovery `37730225779`; artifact `11530100316`, SHA-256 `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Newer green runs prove compatibility only. |
| X006 performance | NOT_STARTED / correctness foundations | No target-device profiler, full-source copy/hash, allocation or export-stress qualification. |

## Quality gates
| Gate | State | Acceptance boundary |
|---|---|---|
| QG1 ownership/invalidation | PARTIAL | Manual-over-machine truth and source/response fencing implemented; same-capture operation implemented; durable STT request/recovery and end-to-end controller ownership still missing. |
| QG2 translation quality | BLOCKED_BY_X002 | Human fidelity/naturalness/integrity scoring required. |
| QG3 source data/provider boundary | PARTIAL_GREEN | Exact translation/STT wire profiles and redacted accepted observation tested. Literal adversarial translation behavior requires X002. |
| QG4 Unicode | PARTIAL_GREEN | Native ICU/text controls on API29/API35; broader device/human matrix incomplete. |
| QG5 containment | PARTIAL_GREEN | Tested native FITS layouts fail closed; X003 acceptance remains partial. |
| QG6 readability | BLOCKED_BY_X003 | Actual-size low-resolution Arabic review missing. |
| QG7 SRT | PARTIAL_GREEN | Locale-independent timestamps, semantic-unwrapped policy, range origin, invalid-arrow and sub-ms collapse tests; source-clock correctness still X001-gated. |
| QG8 recovery/security | PASS at X005 anchor | Descendant source tests are compatibility only. Current typed corruption does not certify every artifact or full-project resume. |
| QG9 provenance/cache | PARTIAL_GREEN | Attachment/snapshot/wire/sample identities and pre-build epoch fencing. New operation retains one source copy through decode and checks an operation-start token. Legacy UI remains separate; STT receipt recovery is not yet implemented. |
| QG10 media parity | BLOCKED_BY_X004 | Legacy renderer/export remains production reference. |
| QG11 P0-F/CI regression | `6781ee1689181fe240f3fa63b5c1085517c51a69` | Exact current runs above; CI does not substitute for manual legacy MP4/audio/device verification. |
| QG12 performance | BLOCKED_BY_X006 | No profiler evidence. |
| QG13 exceptional semantic warnings | NON_BLOCKING / PARTIAL | Deterministic integrity warnings exist; broader correction UX deferred. |

## Required before activation/release
1. Durable STT attempt/recovery with explicit unknown-remote-outcome handling; full source/snapshot reopen semantics; controller/UI ownership. Same-capture operation is implemented; native ownership fixtures passed at the exact checkpoint above, without moving X001/X005 or enabling UI resume.
2. X001 before changing STT unit/origin interpretation; UNVERIFIED snapshots must contain no interpreted word intervals.
3. X002 before switching semantic translation profile; legacy NVIDIA text translation remains comparator.
4. X003 human readability and X004 decoded-device parity before new renderer becomes default.
5. X006 performance qualification and final B013 regression/device checklist.

Style/edit/re-export must never repeat successful AI work. Preserve manual truth. No raw provider response, key, real source locator or private transcript in logs/relay. Details, known defects and exact next action are in `TRANSLATION_V1_EXECUTION_STATE_GPT6.md` and the canonical successor handoff.
