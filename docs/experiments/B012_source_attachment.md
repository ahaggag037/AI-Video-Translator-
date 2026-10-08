# B012 — Immutable source attachment / resume prerequisite

State: **IMPLEMENTED / API35 EMULATOR-VERIFIED / NOT ACTIVATED.** Current verified B012 implementation checkpoint: `253eb617ce7ef32b7f5be3d12bbb8e0c905be633`.

Exact current evidence:
- Android CI `37859703696`: SUCCESS (unit/lint/APK/signature/checksum/report path).
- X005 Android Recovery `37859703620`: SUCCESS on API35 instrumentation.
- Recovery artifact `11586211706`, archive digest `sha256:00b7513f254c7baabc30bfd5e4ef24a8377c4881747bfc7a45e9bda34e13a4a9`.
- Downloaded XML: 58 tests, zero failures/errors/skips; 12 `SourceSnapshotOperationInstrumentedTest` cases.
- This is descendant compatibility/extension evidence and **does not move the frozen X005 PASS anchor**.

Earlier provenance checkpoints remain valid historical references:
- same-capture checkpoint `6781ee1689181fe240f3fa63b5c1085517c51a69`: CI `37839877093`, recovery `37839876832`, artifact `11577287792` / `0cb66921e4ba305363e09673d2dd119cc87c5af40a6aa19a942f6c78121290f7`, 55 tests zero failures/errors/skips.
- source-composition repair `b92e3807ee95757f8230fe8009d246ac02fe1c3e`: CI `37799782433`, recovery `37799782358`.

## Source attachment / snapshot contract

`SourceAttachment` binds session identity, exact content URI, historical persisted-read-grant observation, complete byte-stream SHA-256 + byte count, typed-us selected presentation range, source duration and selected container audio-track descriptor. URI/name/size/mtime alone are not identity. The private JSON object has its own bounded schema; manifest/source/snapshot decoding rejects unsupported versions, unknown/missing fields, malformed numeric types and identity mismatches.

Initial attachment binding is allowed only for a new empty UNBOUND session. Immutable source evidence is published before the manifest reference, then manifest revision+epoch advance atomically. Reattachment/migration of manual/legacy history remains explicit; no automatic reassignment is allowed.

`SourceSnapshot` binds the attachment to accepted transcript/word text, exact prepared-WAV/PCM identity, STT request/result/parser/response-hash provenance and explicit observed presentation origin. UNVERIFIED clocks are structurally forbidden from carrying interpreted word intervals. X001 still owns timing-unit/origin activation.

## Live source resume evidence

`SourceContentProbe` reopens the current `content://` URI, recomputes complete byte-stream identity and reports current persisted-read-grant status separately. Stored permission is not current evidence. Same URI/size with changed bytes is `SOURCE_CHANGED`; missing/denied/unsupported/I/O outcomes stay distinct. `SourceResumeEvaluator` returns `STALE_OBSERVATION` for token drift and `CORRUPT_BINDING` for missing/corrupt active attachment at the bounded attachment read boundary.

`AVAILABLE` is only a point-in-time source match; it is not permission to skip operation-time fencing and does not authorize STT timing/rendering.

## Same-capture operation

`CapturedSource` / `SourceSnapshotOperation` retain one private immutable source copy through the real native decoder and STT evidence/adoption path. The operation:
- captures a source/session token before source I/O;
- requires recaptured bytes/media descriptor to match the durable attachment;
- decodes the private copy, not a later URI reopen;
- owns one unique WAV through transport/factory lifetime;
- checks the original source/session token before submission/adoption;
- rejects unsupported selected ranges before media work;
- fails closed on equal-size/equal-metadata byte replacement;
- survives deletion of the original URI backing file after successful private capture;
- never retries automatically after transport ambiguity.

The same-capture fixture matrix remains emulator evidence, not X001/physical-device acceptance.

## Durable STT attempt/recovery — now implemented

`253eb617...` closes the previously documented missing STT-attempt journal around `SourceSnapshotOperation`.

Lifecycle:

`PREPARED → SENT → RECEIVED → ADOPTED`

Semantics:
- **PREPARED**: durable local intent/evidence; transport has not run.
- **SENT**: persisted before transport callback can run. If the process dies or response is lost now, the remote outcome is unknown and automatic repost is forbidden.
- **RECEIVED**: accepted redacted `SourceSnapshot` is persisted atomically before manifest adoption. A process death in this window can reopen and reuse the result with zero additional provider calls.
- **ADOPTED**: written only after the manifest references the exact snapshot.

Important identity property:
- The operation key is stable across request-profile upgrades: it is derived from session + source attachment, while historical request profile remains recorded inside the receipt. Therefore an unresolved older `SENT` attempt cannot disappear merely because a newer build uses a different request profile.
- A verified regression persists `SENT` under a synthetic older profile, invokes current code and confirms **zero provider calls** plus `UNKNOWN_REMOTE_OUTCOME`.

Privacy/trust boundary:
- The journal stores the redacted accepted snapshot/provenance, never the verbatim provider response body.
- Sample digest is bound before/through transport evidence and checked against the durable attempt/snapshot identity.

Bounded liveness caveat:
- A historical PREPARED-only receipt from a different request profile currently fails closed instead of automatically refreshing to the new profile. Because PREPARED means transport has not run, this is safe with respect to duplicate billing; it is a future liveness/migration refinement rather than an activation correctness hole.

## Scope / activation boundary

Still unchanged:
- Production `MainActivity`, legacy NVIDIA transport entry flow, timing-unit inference, SRT/renderers and legacy preview/hard-burn remain the active comparator where gates are not accepted.
- Task17/controller/UI ownership is **NOT ACTIVATED**.
- No X001–X006 state is promoted by this B012 descendant.
- X005 PASS remains frozen at `b7948478eacef67b2552d4540e4358152cf72dd6`.

## Next boundary: full source/snapshot reopen validation

The journal removes the duplicate-cost/recovery ambiguity, but the project still needs one explicit reopen composition before Task17:
1. `SNAPSHOT_BOUND + valid snapshot`: preserve semantic snapshot after restart; require a fresh source probe for current availability; do not authorize timing/render gates.
2. `SNAPSHOT_BOUND + missing/corrupt/oversized/identity-mismatched snapshot`: typed/local reopen-blocking state, no source/STT provider call and no semantic/manual/manifest mutation.
3. `ATTACHMENT_BOUND + SENT`: surface unknown remote outcome, zero provider calls.
4. `ATTACHMENT_BOUND + RECEIVED`: recover/adopt the durable snapshot with zero provider calls.
5. Preserve existing attachment `CORRUPT_BINDING`, source `STALE_OBSERVATION`, and invalid manifest/programmer-failure distinctions.

First falsifier: restart a valid SNAPSHOT_BOUND session, then independently delete/corrupt/oversize the active snapshot. Reopen assessment must return a typed blocking result without provider/STT calls or mutation; a valid control must reopen unchanged.

## Existing falsifier coverage
- Attachment/content addressing and strict codec round trips.
- Initial bind/restart, immutable-before-manifest crash windows and ENOSPC rollback/retry.
- URI probe replacement/missing/permission/unsupported/stale-token cases.
- Snapshot bind/restart, wrong attachment, stale CAS and missing/corrupt/oversized snapshot fail-closed reads.
- Coordinator early/late epoch mutation, typed corrupt attachment and stale probe behavior.
- Native same-capture fixtures: equal-size/equal-metadata byte replacement; URI deletion after capture; private-vs-legacy PCM/provenance/timing equality; independent WAV lifetimes; unsupported range; transport failure/no retry.
- STT journal: durable RECEIVED process-death recovery without resubmission; SENT/no-response reopen as unknown outcome with no resubmission; older-profile SENT remains discoverable and blocks repost.

## Rollback / non-goals
All B012 work remains additive/unwired. Revert source/STT-recovery batches coherently if required; preserve durable objects/history on downgrade. Do not relabel schema versions to force older code to accept newer state. No B012 evidence authorizes timing/translation/layout/parity gate promotion. No locator/digest/private transcript belongs in logs or relay; synthetic test identities only.
