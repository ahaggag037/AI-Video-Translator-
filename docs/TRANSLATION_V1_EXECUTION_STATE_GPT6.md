# Translation V1 Execution State — GPT-6 Clean Room

## Identity
- Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Implementation branch: `build/p0g-gpt6-cleanroom-v1`
- Last reconciled runtime checkpoint: `b7948478eacef67b2552d4540e4358152cf72dd6` (X005 PASS evidence anchor)
- Newer independent test/docs checkpoints exist on the same branch; do not retroactively attribute their evidence to `b7948478…`.
- Canonical design: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` — Translation & Subtitle System V1 V4.1 Final

## Current batch/task
X005 persistence/recovery gate is closed PASS at its exact evidence anchor. Active independent work: X001 clock-evidence harness (production interpretation unchanged) and B011 SRT millisecond-representability hardening, both checkpointed for Sol review. No X001/X002/X003/X004 experiment-gated production activation.

## Completed / implemented
- B001 baseline freeze/regression ledger.
- B002 typed clocks, semantic ownership, legacy bridge, minimal atomic store.
- B003 classifier/retry policy verified by Android CI run `37723687884`; additive detailed NVIDIA transport preserves the legacy request contract.
- B004 shadow segmentation/planning + X002 seed and deterministic integrity validation; no X002-gated production activation.
- B006 Unicode/ICU ownership plus measured Arabic layout shadow foundations; production renderer unchanged. SDK-36 repair at `1f3d1e95c67776f1518ded7c8cc834fb8d8d4bbf` is fully green in run `37726381108`.
- B011 display timing, cue index, semantic SRT, manual-edit preservation, stale-response fencing.
- X005 durable receipt protocol: PREPARED → current-fenced SENT → RECEIVED, exact epoch/manifest/entry/request fencing, local recovery planning, immutable-entry-first adoption, deterministic crash-retry revision IDs, manual truth preservation.
- Production durable send cannot invoke transport before durable PREPARED and current-fenced SENT persistence succeeds.
- Stale SENT receipts are quarantined; current SENT is explicit UNKNOWN_REMOTE_OUTCOME/retry decision only.
- Request-plan identity covers exact NVIDIA endpoint, body-affecting profile fields and a signed/versioned non-secret transport contract. Exact JSON field/message shape is tested; legacy/default-plan serialization equivalence has a regression test.
- Android X005 harness covers attempt submission counting, send fencing, crash/replay, late callbacks, ENOSPC, corrupt receipt/manifest/entry, and schema restore.
- X001 evidence-only checkpoint `679b049a7704adefb8900a6b973cc3136b6121d0` adds synthetic raw-unit ambiguity, nonzero-origin and N24 arithmetic falsifiers; production `NvidiaSttClient.normalizeTimes` remains unchanged.
- B011 SRT checkpoint `a71b688b07b3675c2273d5005398739b062146a2` rejects positive-us intervals that collapse to equal timestamps at SRT's 1 ms precision instead of silently clamping/extending them; exact review/CI still pending at this reconciliation.

## Experiment status
- X001: HARNESS_READY_PARTIAL — synthetic evidence falsifiers implemented; original/live raw-unit evidence and Android synthetic media/PTS/PCM capture still missing. No PASS.
- X002: HARNESS_READY — seed corpus + deterministic integrity validator; no live/human quality verdict. No PASS.
- X003: HARNESS_READY — measured shadow layout foundation; pinned-font/device readability evidence missing. No PASS.
- X004: NOT_STARTED.
- X005: **PASS** at `b7948478eacef67b2552d4540e4358152cf72dd6` against the canonical fake-provider/crash/recovery matrix.
- X006: HARNESS_READY — CueIndex correctness fixture only; no device performance measurement. No PASS.

## X005 exact PASS evidence
- Android CI run `37730225777` @ `b7948478eacef67b2552d4540e4358152cf72dd6`: full success (unit, lint, APK build/existence, signature/checksum, reports, APK artifact).
- X005 Android Recovery run `37730225779` @ same SHA: full success including instrumentation and report upload.
- Recovery report artifact ID `11530100316`, digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`.
- Android CI verification reports ID `11529800538`, digest `sha256:2d93fdb447cb66fe501513cf3158689df9ccff120b0bfa5414c2f20d904bee49`.
- Android CI debug APK ID `11529507092`, digest `sha256:22ca17fd79538d3489e9ede20d1c7c0e81c4e623e05cd3a4a1cab0485cc90a2f`.
- Executed Android attempt-ledger falsifier proves known RECEIVED restart/adoption adds zero POSTs; unknown SENT restart does not blindly repost.
- JVM recovery policy proves current SENT requires explicit retry decision and stale SENT offers no retry.
- Sol exact-SHA review `SOL-X005-AUDIT-004` found prior HIGH wire-identity gaps closed; only timeout-scope policy remained MEDIUM/non-blocking.

## Deliberate V1 policies / known deviations
- STT seconds-vs-ms magnitude inference remains unchanged pending X001; it is not accepted as unit evidence.
- `SttAudioPreparer.sourceStartUs` is observed extractor first PTS, but `sourceEndUs` is currently derived from decoded frame count; continuity/priming/edit-list correctness is not assumed until X001.
- `AudioSampleExtractor` similarly rebases copied sample PTS and later derives an end from measured duration; X001 must capture first/last PTS and PCM/marker evidence before Task 10 mapping activation.
- Exact font family/weight remains X003-gated.
- Session-global `manifest.revision` stays in the X005 adoption fence. Unrelated session mutation may false-stale an in-flight result; this is an explicit safe liveness/cost tradeoff.
- Connect/read/call timeout values remain V1 liveness policy outside provider request identity. Changing them requires recovery/UX re-evaluation; current conservative transport-failure classification still prevents blind duplicate submission.
- Commit `720f35821ebcf194faa8bcac34b57c8972a4823b` is a tree-identical no-op created while reconciling an already-fixed TextPolicy compile regression; preserve history, no force rewrite.

## Latest active verification
- X001 checkpoint `679b049a7704adefb8900a6b973cc3136b6121d0`: Android CI run `37730712932` was still executing at last observation.
- Legacy/default-plan request-equivalence checkpoint `c5ea42924bc197e5dd19716fed61354c23beebd9`: Android CI run `37730776751` was still executing at last observation.
- B011 SRT checkpoint `a71b688b07b3675c2273d5005398739b062146a2`: review requested as `G6-B011-001`; inspect its exact CI before further SRT/timing changes.
- X001 review requested as `G6-X001-001`; do not stack Task 10/SampleClockMapper until review + evidence support it.

## RESUME HERE
1. Inspect exact CI for `a71b688b…`, `679b049a…`, and `c5ea429…`; fix the earliest concrete regression if any.
2. Read Sol replies to `G6-X001-001` and `G6-B011-001`; apply evidence-backed corrections before stacking either high-risk chain.
3. With X005 now PASS, inspect Task 17 activation surface (`MainActivity` / `TranslationCard` / session controller) against the canonical condition “Task 16 + Task 07 or approved legacy profile”; do not activate X002/X001/X003/X004 behavior as a side effect.
4. Highest-value X001 next evidence, once review permits: Android synthetic media capture of raw first/last PTS + decoded PCM sample count + known marker origin; no production raw-unit reinterpretation until the experiment establishes it.
