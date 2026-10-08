# X005 — Persistence / Recovery / Request Fencing

State: HARNESS_READY (JVM/data foundations only; Android crash/ENOSPC execution still required).

## Durable receipt protocol
Each provider attempt has one atomic receipt file under `requests/<attemptId>.json` and advances monotonically:

1. `PREPARED` — request identity frozen locally; no network submission has been declared.
2. `SENT` — persisted before invoking transport. After process loss this is deliberately treated as `UNKNOWN_REMOTE_OUTCOME`, even if the crash happened just before actual socket submission; the conservative false-positive costs a user decision but prevents blind duplicate execution.
3. `RECEIVED` — provider outcome/candidate persisted before any manifest adoption. Recovery can revalidate/adopt this receipt without a provider POST when signatures and fencing still match.

Receipt files contain no API key or Authorization header. Candidate text is durable user/session data and may be retained because successful paid semantic results must not be cache-only.

## Remaining X005 execution
- Android `AtomicFile` crash injection at every transition.
- late callback after epoch bump / cancellation.
- crash after `RECEIVED` before manifest adoption.
- ENOSPC / corrupt receipt behavior.
- recovery adoption into entry + manifest with exact signature/revision fence.
- prove fake transport submission counts: known received success = zero extra POST; `SENT` = explicit retry decision, never automatic repost.

Do not mark PASS until those executions are recorded.
