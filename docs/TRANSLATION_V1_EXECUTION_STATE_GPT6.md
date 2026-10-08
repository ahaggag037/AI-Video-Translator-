# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled branch HEAD: `527cf889387dda7e997563d7cc1a6e800b95bcb8` (`tv1(B012): migrate manifest source binding to schema v2`).
- Exact verified recovery gate remains X005 PASS anchor `b7948478eacef67b2552d4540e4358152cf72dd6`; do not retroactively attribute newer CI or experiment evidence to that SHA.
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final.

## Current batch/task
X005 persistence/recovery is closed PASS at its exact evidence anchor. Active independent work is now:
- X001 evidence-only clock/provenance capture; production STT timing interpretation remains unchanged.
- B011 deterministic SRT millisecond representability regressions.
- B012/Task17 persistence prerequisite: manifest schema v2 + explicit source binding foundation; controller/UI resume activation is still blocked.

## Completed / implemented
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy verified by Android CI run `37723687884`; additive detailed NVIDIA transport preserves legacy request behavior.
- B004 shadow segmentation/planning + X002 seed + deterministic translation integrity validation; no X002 production activation.
- B006 Unicode/ICU ownership + measured Arabic layout shadow foundations; production renderer unchanged. SDK-36 repair `1f3d1e95c67776f1518ded7c8cc834fb8d8d4bbf` is green in run `37726381108`.
- B011 display timing, cue index, semantic SRT, manual-edit preservation, stale-response fencing. SRT now rejects positive-us intervals that collapse at 1 ms precision instead of clamping/extending. `43a7339b15f784361408cc260d863e3a6c46ea62` adds boundary regressions for `999..1001 us`, exactly touching cues, and a later collapsing cue; no exporter policy change.
- X005 durable receipt protocol: PREPARED → current-fenced SENT → RECEIVED; exact epoch/manifest/entry/request fencing; local recovery planning; immutable-entry-first adoption; deterministic crash-retry IDs; manual truth preservation.
- Production durable send cannot invoke transport before durable PREPARED and current-fenced SENT persistence succeeds. Stale SENT is quarantined; current SENT remains UNKNOWN_REMOTE_OUTCOME requiring explicit retry choice.
- NVIDIA durable request identity covers exact endpoint/body-affecting profile plus versioned non-secret transport contract; exact JSON key/message shape and legacy/default serialization equivalence are regression-tested.
- Android X005 harness covers submission counting, send fencing, crash/replay, late callbacks, ENOSPC, corrupt receipt/manifest/entry, and schema restore.
- X001 diagnostic chain preserves timing schema family/path/field names, raw scalar JSON type, original item index/full path, blank-text timing-bearing items, all alternatives, and verbatim provider response + SHA256 (`208c1dcf…` then `65e2f651…`). This is diagnostic-only; unredacted responses must not be logged/relayed/persisted.
- B012 manifest version domains are split at `527cf889…`: manifest schema v2, entry schema v1, receipt schema v1. Legacy manifest v1 decodes to explicit `LEGACY_UNBOUND`; active attachment/snapshot refs are state-checked. No locator/range/track/source-object persistence or resume activation is claimed yet.

## Experiment status
- X001: HARNESS_READY_PARTIAL — synthetic ambiguity/nonzero-origin/N24 fixtures and high-fidelity raw evidence inspector exist; real Android/provider canary bundle and audio-clock identity evidence still missing. No PASS.
- X002: HARNESS_READY — seed corpus + deterministic integrity validator; no live/human quality verdict. No PASS.
- X003: HARNESS_READY — measured shadow layout foundation; pinned-font/device readability evidence missing. No PASS.
- X004: NOT_STARTED.
- X005: **PASS** at `b7948478eacef67b2552d4540e4358152cf72dd6` against canonical fake-provider/crash/recovery matrix.
- X006: HARNESS_READY — CueIndex correctness fixture only; no device performance measurement. No PASS.

## X005 exact PASS evidence
- Android CI run `37730225777` @ `b7948478eacef67b2552d4540e4358152cf72dd6`: full success (unit, lint, APK build/existence, signature/checksum, reports, APK artifact).
- X005 Android Recovery run `37730225779` @ same SHA: full success including instrumentation and report upload.
- Recovery report artifact ID `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`.
- Android CI verification reports ID `11529800538`, digest `sha256:2d93fdb447cb66fe501513cf3158689df9ccff120b0bfa5414c2f20d904bee49`.
- Android CI debug APK ID `11529507092`, digest `sha256:22ca17fd79538d3489e9ede20d1c7c0e81c4e623e05cd3a4a1cab0485cc90a2f`.
- Executed attempt-ledger falsifier proves known RECEIVED restart/adoption adds zero POSTs; unknown SENT restart does not blindly repost.
- JVM recovery policy proves current SENT requires explicit retry decision and stale SENT offers no retry.
- Sol exact-SHA review `SOL-X005-AUDIT-004` found prior HIGH wire-identity gaps closed; timeout scope remains an explicit non-blocking liveness policy.

## Deliberate V1 policies / known deviations
- STT seconds-vs-ms magnitude inference remains unchanged pending X001 and is not accepted as unit evidence.
- X001 diagnostic raw response is sensitive evidence: exact unredacted response stays in-memory diagnostic data only unless a separately designed redaction/evidence bundle is approved.
- `SttAudioPreparer.sourceStartUs` is observed extractor first PTS, while derived end/continuity/priming/edit-list correctness remain unaccepted until X001 evidence closes them.
- Exact font family/weight remains X003-gated.
- Session-global `manifest.revision` remains in X005 adoption fencing; unrelated mutation may false-stale an in-flight result as an explicit safety-over-liveness tradeoff.
- Connect/read/call timeouts remain V1 liveness policy outside request identity; changing them requires recovery/UX re-evaluation.
- B012 source binding v2 is only a reference/state foundation. Do not infer source availability, permission survival, stable identity, selected range, audio-track ownership, or clock origin until immutable source persistence objects and resume validation are implemented.
- Commit `720f35821ebcf194faa8bcac34b57c8972a4823b` is a tree-identical historical no-op from compile-regression reconciliation; preserve history, no force rewrite.

## Active reviews / verification
- X001 review `G6-X001-004` requested for exact `208c1dcfa11b2c5c275fc94314410c661622f1c5`; newer `65e2f6511ba058a5fc23ccf9c1bc8c51cfd58c61` additionally retains the verbatim raw response + SHA256. Do not activate Task10 from either checkpoint without real evidence.
- B011 SRT policy review is clear (`SOL-B011-SRT-AUDIT-001`); `43a7339…` adds only the recommended edge regressions. Verify its descendant CI before claiming that test checkpoint green.
- B012 review `G6-B012-002` requested for exact `527cf889387dda7e997563d7cc1a6e800b95bcb8`.
- At last observation, Android CI and X005 Android Recovery for `527cf889…` were in progress. X005 remains PASS only at its frozen evidence anchor until descendant compatibility runs complete.

## RESUME HERE
1. Inspect exact CI for current branch HEAD and fix the earliest concrete regression before adding dependent work.
2. Read Sol replies to `G6-X001-004` and `G6-B012-002`; apply evidence-backed corrections before stacking either high-risk chain.
3. If B012 manifest-v2 foundation is sound, implement the next immutable source persistence object before controller/UI work. Required eventual ownership includes valid locator + persistable-read-grant evidence, stable source identity/fingerprint, selected typed-us range, active audio-track descriptor/ref, accepted SourceSnapshot/source hash/provenance, and explicit source/presentation clock origin. Resume must validate source identity/permission and fail source-dependent work closed on missing/changed media.
4. Highest-value X001 next evidence remains one redacted real canary bundle atomically bound to exact request/build/audio identity: raw timing paths/values + sample digest/stable ID + PCM count/rate/channels + verified sourceStartUs/presentation origin. No production unit reinterpretation until evidence supports it.
5. Keep Task17 controller/UI activation blocked until source persistence objects, resume status evaluation, and source-dependent fail-closed behavior are durable and tested.
