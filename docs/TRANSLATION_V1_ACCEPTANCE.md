# Translation & Subtitle V1 — Acceptance

**NOT RELEASE-READY. Task17 durable source/STT/legacy-translation ownership is now ACTIVATED.** This file is the current evidence/gate index, not a chronological journal. Historical detail remains in Git/CI and `docs/experiments/`.

Canonical successor entry point: **`docs/TRANSLATION_V1_SUCCESSOR_HANDOFF.md`**. External coordination surfaces remain non-authoritative unless the owner explicitly reactivates them.

Repository: `ahaggag037/AI-Video-Translator-`  
Active branch: `build/p0g-gpt6-cleanroom-v1`  
Frozen P0-F: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`  
Canonical architecture: user-supplied `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md`; AR-01..AR-05 remain accepted in `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`.

## Current verified implementation checkpoint

**`b8c2c059e89a5b180b92c6f0a01c79abfde33730`** — `feat(Task17): activate durable translation ownership in UI`

Exact canonical verification at this SHA:
- Android CI `37991965285`: **SUCCESS** — unit tests, lint, debug APK build, APK existence/signature/checksum and artifact upload all passed.
- X005 Android Recovery `37991965251`: **SUCCESS** on API35 connected instrumentation.
- The same production tree was first falsified on isolated staging before canonical fast-forward; workflow-only staging commits were not copied into canonical history.

The immediately preceding durable-translation foundation checkpoint is `64d87ada8e411e4969a6835b1ed83c5fa9401f09` (`feat(Task17): integrate durable legacy translation recovery`). Its exact Android CI and API35 jobs also passed after infrastructure-only reruns. Earlier source/STT recovery checkpoints remain in Git history for provenance.

### Scope now activated in production UI

- One application-owned `TranslationSessionStore`, `ActiveSessionRegistry` and `TranslationRequestPlanStore` share the same durable session root.
- `TranslationSessionViewModel` owns cancellable source, STT and legacy-translation work; Compose no longer owns provider submission truth.
- Source selection creates/activates a durable session and is revalidated on reopen.
- STT persists `PREPARED → SENT → RECEIVED → ADOPTED`; unresolved `SENT` is `UNKNOWN_REMOTE_OUTCOME` and is never blindly reposted.
- Durable legacy translation preserves the current P0-F segmentation/profile. Per-unit request plans and receipts are persisted; compatible accepted entries are reused without another provider call.
- Translation `SENT` reopens as unknown/no-repost; `RECEIVED` can be validated/adopted locally with zero provider calls.
- The live provider response used to build the durable STT snapshot is also the only source of live P0-F timing for the current process; no second STT request is made merely for UI timing.
- On process restart, accepted STT/translation text can be restored without network. Word timing is intentionally not reconstructed from guesswork, so timing-dependent preview/SRT/MP4 remains unavailable after restart until a verified timing source exists.
- NVIDIA credentials remain in transient UI memory and are not written to session/request-plan/receipt storage.

Task17 activation does **not** close X001/X002/X003/X004/X006 and does not make the APK release-ready.

## Experiment gates

| Experiment | Status | Exact evidence / missing proof |
|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL` | Track/PCM provenance, raw/synthetic timing evidence and redacted transport-bound sample/response identity exist. Missing real hosted response paired with independently verified presentation origin. Legacy timing-unit/origin behavior remains the production comparator; durable snapshots marked `UNVERIFIED` do not gain invented word intervals. |
| X002 translation | `HARNESS_READY` | N25: 48 synthetic passages, including adversarial/integrity cases. Missing paired legacy/semantic outputs + blind Arabic human scoring. The activated durable path deliberately preserves legacy P0-F segmentation/profile and does **not** switch to semantic translation. |
| X003 layout | `HARNESS_EXECUTED_PARTIAL` | API29/API35 native geometry controls exist. Human actual-size readability/parity remains unmeasured. |
| X004 parity | `DEVICE_EVIDENCE_PARTIAL` | Android export-media inspector now reads real container duration, track MIME, sample count and first/last PTS, and the canonical descendant passes device recovery regression. Missing decoded-device frame/audio/cue-switch/preview-lag parity evidence, so X004 is not PASS. |
| X005 recovery | **PASS at exact frozen anchor only** | Frozen accepted anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; CI `37730225777`; API35 recovery `37730225779`; artifact `11530100316` / `d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`. Descendant runs, including `37991965251`, are compatibility/extension evidence only and do not move the frozen PASS anchor. |
| X006 performance | `NOT_STARTED / correctness foundations` | No target-device profiler qualification for full-source copy/hash, allocation/heap behavior, long translation batches or export stress. |

## Quality gates

| Gate | State | Acceptance boundary |
|---|---|---|
| QG1 ownership/invalidation | `TASK17_GREEN` | Application-owned durable root, controller/ViewModel ownership, source/STT/legacy-translation activation and cancellation/source fencing are verified on canonical Android CI + API35. |
| QG2 translation quality | `BLOCKED_BY_X002` | Human fidelity/naturalness/integrity scoring required before semantic switch. |
| QG3 source/provider boundary | `PARTIAL_GREEN` | Exact source/sample/request identities and no-blind-repost semantics are active; X001/X002 still gate timing/semantic reinterpretation. |
| QG4 Unicode | `PARTIAL_GREEN` | Native ICU/text controls exist; broader device/human matrix incomplete. |
| QG5 containment | `PARTIAL_GREEN` | Native layout constraints fail closed; X003 acceptance remains partial. |
| QG6 readability | `BLOCKED_BY_X003` | Actual-size Arabic human review missing. |
| QG7 SRT | `PARTIAL_GREEN` | Locale-independent timestamps and range guards exist; source-clock truth remains X001-gated. |
| QG8 recovery/security | `PASS at X005 anchor + descendant coverage` | Frozen X005 anchor remains authoritative; canonical descendant verifies additional source/STT/translation restart semantics. |
| QG9 provenance/cache | `PARTIAL_GREEN` | Attachment/snapshot/sample/request-plan/receipt identities and durable reuse are active. Full release qualification still depends on the remaining experiment gates. |
| QG10 media parity | `BLOCKED_BY_X004` | Production preview/export still requires decoded-device parity evidence before X004 can close. |
| QG11 P0-F/CI regression | `b8c2c059... GREEN` | Android CI `37991965285` SUCCESS and API35 recovery `37991965251` SUCCESS. CI does not substitute for remaining human/provider/device acceptance evidence. |
| QG12 performance | `BLOCKED_BY_X006` | No target-device performance qualification yet. |
| QG13 exceptional semantic warnings | `NON_BLOCKING / PARTIAL` | Deterministic integrity/review states exist; broader correction UX remains deferred. |

## Durable STT attempt/recovery truth

The STT-specific journal is implemented, tested and UI-wired:
- `PREPARED`: durable local intent; transport has not run, so local rebuilding is safe.
- `SENT`: durable before transport callback execution; after process/response loss the remote outcome is unknown and automatic repost is forbidden.
- `RECEIVED`: accepted redacted `SourceSnapshot` is durable before manifest adoption and can be reused after process death without another provider call.
- `ADOPTED`: written only after the manifest points to the exact snapshot.
- Attempt identity remains stable across request-profile upgrades so an older unresolved `SENT` cannot be hidden by a new profile ID.
- Raw provider response bodies remain outside the durable journal/snapshot graph.

A bounded liveness caveat remains: a historical `PREPARED` STT journal written under a different request profile fails closed instead of being silently refreshed. This cannot duplicate a remote request because PREPARED means transport did not run.

## Durable legacy translation truth

The production UI now uses the durable legacy P0-F translation operation rather than a Compose-owned HTTP loop:
- The exact legacy P0-F source-unit split is converted to immutable, bounded request plans; no timing is stored in those plans.
- A compatible active accepted entry is reused with zero provider submissions.
- New work persists `PREPARED`, then `SENT`, then permits transport.
- Cancellation/transport ambiguity after `SENT` leaves the durable receipt at `SENT`; it is surfaced as unknown and is not automatically reposted.
- A durable `RECEIVED` candidate is locally revalidated/adopted after restart with zero provider submissions.
- Review/reject/pending/terminal outcomes stop the batch rather than silently submitting later units.
- Successful text survives restart. Live P0-F timing does not, so recovered text-only state intentionally blocks timing-dependent export rather than fabricating timestamps.

## Required before release

1. X001 before changing STT timing unit/origin interpretation or making recovered timing-dependent output authoritative.
2. X002 before switching from the legacy P0-F translation comparator to the semantic translation profile.
3. X003 actual-size Arabic readability/human review.
4. X004 decoded-device frame/audio/cue-switch/preview-lag parity qualification.
5. X006 target-device performance qualification and the final B013 regression/device checklist.
6. Final packaging/signing: conditional release-signing support exists, but a real release keystore/credentials must be supplied legitimately; no signing secret is stored in the repository.

Style/edit/re-export must never repeat successful AI work. Preserve manual truth. No raw provider response, key, real source locator or private transcript belongs in logs, relay or docs.
