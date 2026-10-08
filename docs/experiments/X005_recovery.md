# X005 — Persistence / Recovery / Request Fencing

State: **PASS at `b7948478eacef67b2552d4540e4358152cf72dd6`** against the canonical V1 X005 fake-provider/crash/recovery acceptance matrix. This does not activate unrelated experiment-gated translation, timing, or renderer behavior.

## Durable receipt protocol
Each provider attempt has one atomic receipt file under `requests/<attemptId>.json` and advances monotonically:

1. `PREPARED` — freezes request identity plus `epoch`, `expectedManifestRevision`, and the expected active entry revision before network work.
2. `SENT` — persisted only after rechecking the frozen fence under the session-store lock and before the V1 durable-attempt executor can invoke transport. After process loss this is deliberately `UNKNOWN_REMOTE_OUTCOME`; blind duplicate execution is prevented. A stale SENT fence is quarantined as stale and does not offer retry.
3. `RECEIVED` — provider outcome/candidate persisted before manifest adoption. Recovery can revalidate/adopt only when the frozen fence still matches the current manifest and a self-consistent exact request plan reproduces the receipt request signature.

Receipt files contain no API key or Authorization header. Candidate text is durable user/session data because successful semantic results must not be cache-only.

## Verified recovery invariants
- `ReceiptRecoveryPlanner` performs no provider I/O.
- `DurableTranslationAttemptExecutor` persists PREPARED and then current-fenced SENT before transport can execute. Failed SENT persistence means zero submissions; transport failure leaves durable SENT.
- RECEIVED adoption runs under the store single-writer boundary, rereads current manifest/receipt, and revalidates the exact request plan before mutation.
- Only deterministic validator `PASS` is auto-adoptable; review/failure states do not auto-adopt.
- Manual semantic truth survives recovered/retranslated machine history.
- Recovery revision IDs are deterministic from frozen receipt identity, so crash after immutable-entry publication but before manifest publication reuses the same bytes/IDs.
- Immutable entry publication precedes atomic manifest advancement; replay after manifest publication is fenced stale and cannot adopt twice.
- Request identity covers model, endpoint, signed transport-contract ID, system content, language pair, protocol version, max tokens, temperature, stream flag, exact source text, and approved examples.
- Exact JSON body shape is regression-tested: no unsigned top-level body fields or extra messages are accepted silently by the current contract.
- Versioned `NvidiaTranslationWireContract` owns POST method, Accept media type, request media type, redirect policy, and retry-on-connection-failure. Legacy and default-plan serialization are regression-compared.

## Verified Android falsifiers
`.github/workflows/x005-android-recovery.yml` runs `connectedDebugAndroidTest` on an API-35 x86_64 emulator.

At exact SHA `b7948478eacef67b2552d4540e4358152cf72dd6`, X005 Android Recovery run `37730225779` completed successfully, including instrumentation and report upload. Artifact:
- `x005-android-recovery-reports`
- artifact ID `11530100316`
- digest `sha256:d302f9be6a4ffcf5f6b306a74f2fe65c410116229139010aaae9cbee5ec62b6c`

The executed Android suite covers:
- known RECEIVED result: one initial fake-provider submission, restart/recovery/adoption with **zero additional POSTs**;
- unknown remote outcome: durable SENT survives restart and recovery does **not** blindly repost; current SENT requires an explicit retry decision;
- stale SENT: no retry offer (`STALE_RECEIPT`) in JVM recovery policy tests;
- crash/failpoint after immutable recovered-entry publication but before manifest advancement: replay reuses the deterministic revision, publishes once, and subsequent replay cannot advance again;
- late RECEIVED callback after epoch bump: durable for audit, rejected from adoption;
- simulated ENOSPC during manifest atomic commit: previous valid manifest/epoch survives restart;
- simulated ENOSPC during SENT atomic commit: PREPARED survives and transport is not allowed through failed durable SENT;
- corrupt receipt, corrupt manifest, and corrupt active-entry payloads fail closed;
- schema-V1 restart restore preserves manual truth and machine history;
- send-fence and attempt-ledger behavior executes on Android.

## Verified general CI
At the same exact SHA, Android CI run `37730225777` completed successfully: unit tests, lint, debug APK build, APK existence, signature/checksum verification, report upload, and APK artifact upload all passed.

Artifacts:
- verification reports ID `11529800538`, digest `sha256:2d93fdb447cb66fe501513cf3158689df9ccff120b0bfa5414c2f20d904bee49`
- debug APK archive ID `11529507092`, digest `sha256:22ca17fd79538d3489e9ede20d1c7c0e81c4e623e05cd3a4a1cab0485cc90a2f`

## Independent review
Sol reviewed exact `b7948478eacef67b2552d4540e4358152cf72dd6` and found the two prior HIGH wire-identity gaps closed: exact body/message shape is tested and endpoint plus versioned non-secret HTTP submission semantics are bound to request identity/contract. Legacy translation request behavior remained equivalent at that checkpoint.

## Deliberate V1 policies, not failures
- Session-global `manifest.revision` remains part of the adoption fence. An unrelated session commit may conservatively false-stale an in-flight result; V1 accepts that liveness/cost tradeoff for simpler stale-state safety.
- Connect/read/call timeouts remain liveness policy outside provider request identity. Current failure classification conservatively treats transport failure as potentially submitted, so changing timeout values does not by itself permit blind duplicate execution; any timeout change still requires recovery/UX re-evaluation.
- A current SENT receipt exposes an explicit retry decision because the remote outcome is unknown. A stale SENT receipt never offers retry against changed state.

## Gate verdict
Canonical X005 asks whether receipt + manifest + fencing preserve successful results/manual edits under fake-provider submission counting, crash/restart, cancellation/late response, receipt-before-manifest crash, ENOSPC, and schema restore. Those falsifiers are implemented and were executed successfully at the exact PASS checkpoint above.

**X005 = PASS for the V1 persistence/recovery gate at `b7948478eacef67b2552d4540e4358152cf72dd6`.**

This verdict is scoped. It does not establish X001 timing correctness, X002 translation quality, X003 layout/readability, X004 preview/export parity, or X006 performance, and it does not constitute a live paid-provider reliability test.
