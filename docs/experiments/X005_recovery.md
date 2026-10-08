# X005 — Persistence / Recovery / Request Fencing

State: HARNESS_READY (core JVM/data invariants implemented; Android crash/ENOSPC/device execution still required).

## Durable receipt protocol
Each provider attempt has one atomic receipt file under `requests/<attemptId>.json` and advances monotonically:

1. `PREPARED` — freezes request identity plus `epoch`, `expectedManifestRevision`, and the expected active entry revision before network work.
2. `SENT` — persisted before the V1 durable-attempt executor can invoke transport. After process loss this is deliberately `UNKNOWN_REMOTE_OUTCOME`; blind duplicate execution is prevented. A stale SENT fence is quarantined as stale and does not offer retry.
3. `RECEIVED` — provider outcome/candidate persisted before manifest adoption. Recovery can revalidate/adopt only when the frozen fence still matches the current manifest and a self-consistent exact request plan reproduces the receipt request signature.

Receipt files contain no API key or Authorization header. Candidate text is durable user/session data because successful paid semantic results must not be cache-only.

## Implemented recovery invariants
- `ReceiptRecoveryPlanner` never performs provider I/O. PREPARED, SENT, and RECEIVED map to local recovery actions only.
- `DurableTranslationAttemptExecutor` persists PREPARED and then SENT before caller-provided transport can execute. If SENT persistence fails, transport is not invoked. If transport throws/cancels before RECEIVED is persisted, durable state remains SENT.
- RECEIVED candidate adoption runs under the session store's single-writer lock, rereads the current manifest and receipt, and revalidates the exact request plan before mutation.
- Only deterministic validator `PASS` is automatically adoptable; warnings/review/failure states remain non-adopting.
- Recovered machine history preserves any manual revision as effective semantic truth.
- Recovery machine/entry revision IDs are derived deterministically from length-framed frozen receipt identity. A crash after immutable-entry publication but before manifest publication can therefore retry the same immutable bytes/IDs instead of generating another revision.
- Immutable entry publication precedes atomic manifest advancement; a later retry after successful manifest advancement is stale by fence and cannot adopt twice.

## V1 fence policy
V1 intentionally keeps the exact session-global `manifest.revision` in the adoption fence in addition to epoch, active entry revision, and request signature. This can conservatively false-stale a response when an unrelated unit commits while it is in flight. That is an explicit V1 liveness/cost tradeoff in favor of simple stale-state safety; do not narrow it without evidence and a deliberate contract change.

## Remaining X005 execution
- Android `AtomicFile` crash injection at every receipt/manifest transition.
- late callback after epoch bump / cancellation.
- forced crash after immutable recovered-entry publication and before manifest adoption; verify deterministic retry reuses the same revision and semantic adoption occurs at most once.
- ENOSPC / corrupt manifest, entry, and receipt behavior.
- fake/controlled transport proof at the real provider boundary: known RECEIVED success = zero extra POST; current SENT = explicit retry decision only; stale SENT = no retry offer.
- device/emulator execution of the store/recovery path.

The JVM ordering/identity tests are evidence for local invariants only; they are not crash-consistency or device evidence. Do not mark X005 PASS until the remaining executions are recorded.
