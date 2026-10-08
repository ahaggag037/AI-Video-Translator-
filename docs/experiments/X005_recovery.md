# X005 — Persistence / Recovery / Request Fencing

State: HARNESS_READY (JVM/data foundations only; Android crash/ENOSPC execution still required).

## Durable receipt protocol
Each provider attempt has one atomic receipt file under `requests/<attemptId>.json` and advances monotonically:

1. `PREPARED` — freezes request identity plus `epoch`, `expectedManifestRevision`, and the expected active entry revision before network work.
2. `SENT` — persisted before invoking transport. After process loss this is deliberately `UNKNOWN_REMOTE_OUTCOME`; a crash in the tiny pre-call window may be conservative, but blind duplicate execution is prevented.
3. `RECEIVED` — provider outcome/candidate persisted before manifest adoption. Recovery can revalidate/adopt only when the frozen fence still matches the current manifest and request signature.

Receipt files contain no API key or Authorization header. Candidate text is durable user/session data because successful paid semantic results must not be cache-only.

## Remaining X005 execution
- Android `AtomicFile` crash injection at every transition.
- late callback after epoch bump / cancellation.
- crash after `RECEIVED` before manifest adoption.
- ENOSPC / corrupt receipt behavior.
- controller-level revalidation/adoption into entry + manifest under exact fence/CAS.
- prove fake transport submission counts: known received success = zero extra POST; `SENT` = explicit retry decision, never automatic repost.

Do not mark PASS until those executions are recorded.
