# X005 — Persistence / Recovery / Request Fencing

State: HARNESS_READY (core JVM/data invariants and Android recovery/fault harnesses implemented; current device/emulator execution still must be observed before any PASS claim).

## Durable receipt protocol
Each provider attempt has one atomic receipt file under `requests/<attemptId>.json` and advances monotonically:

1. `PREPARED` — freezes request identity plus `epoch`, `expectedManifestRevision`, and the expected active entry revision before network work.
2. `SENT` — persisted only after rechecking the frozen fence under the session-store lock and before the V1 durable-attempt executor can invoke transport. After process loss this is deliberately `UNKNOWN_REMOTE_OUTCOME`; blind duplicate execution is prevented. A stale SENT fence is quarantined as stale and does not offer retry.
3. `RECEIVED` — provider outcome/candidate persisted before manifest adoption. Recovery can revalidate/adopt only when the frozen fence still matches the current manifest and a self-consistent exact request plan reproduces the receipt request signature.

Receipt files contain no API key or Authorization header. Candidate text is durable user/session data because successful paid semantic results must not be cache-only.

## Implemented recovery invariants
- `ReceiptRecoveryPlanner` never performs provider I/O. PREPARED, SENT, and RECEIVED map to local recovery actions only.
- `DurableTranslationAttemptExecutor` persists PREPARED and then current-fenced SENT before caller-provided transport can execute. If SENT persistence fails, transport is not invoked. If transport throws/cancels before RECEIVED is persisted, durable state remains SENT.
- RECEIVED candidate adoption runs under the session store's single-writer lock, rereads the current manifest and receipt, and revalidates the exact request plan before mutation.
- Only deterministic validator `PASS` is automatically adoptable; warnings/review/failure states remain non-adopting.
- Recovered machine history preserves any manual revision as effective semantic truth.
- Recovery machine/entry revision IDs are derived deterministically from length-framed frozen receipt identity. A crash after immutable-entry publication but before manifest publication can therefore retry the same immutable bytes/IDs instead of generating another revision.
- Immutable entry publication precedes atomic manifest advancement; a later retry after successful manifest advancement is stale by fence and cannot adopt twice.
- The exact provider request identity includes model, endpoint, system content, language pair, protocol version, max tokens, temperature, stream flag, exact source text, and approved examples. The durable NVIDIA serializer consumes the signed plan for all output-affecting wire-body fields and the endpoint, with contract validation before transport.

## Android falsifiers implemented
The dedicated workflow `.github/workflows/x005-android-recovery.yml` runs `connectedDebugAndroidTest` on an API-35 x86_64 emulator and uploads connected-test reports.

Implemented instrumented cases include:
- crash/failpoint after immutable recovered-entry publication but before manifest advancement: restart reuses the same deterministic entry revision, publishes the manifest once, and a second replay is stale/non-adopting;
- late RECEIVED callback after epoch bump: outcome remains durable for audit but adoption is rejected as `STALE_EPOCH`;
- corrupt receipt JSON: read fails closed and manifest remains unchanged;
- simulated ENOSPC during manifest atomic commit: restart observes the previous valid manifest/epoch;
- simulated ENOSPC during SENT atomic commit: restart observes PREPARED and no manifest mutation;
- schema-V1 restart restore: manual truth and machine history survive round-trip persistence;
- send-fence and attempt-ledger instrumentation covering durable submission ordering/current-fence behavior.

These are implemented falsifiers, not PASS evidence until the corresponding workflow run is observed successful.

## V1 fence policy
V1 intentionally keeps the exact session-global `manifest.revision` in the adoption fence in addition to epoch, active entry revision, and request signature. This can conservatively false-stale a response when an unrelated unit commits while it is in flight. That is an explicit V1 liveness/cost tradeoff in favor of simple stale-state safety; do not narrow it without evidence and a deliberate contract change.

## Current execution evidence
- Android CI run `37729060025` at `8ba42efb654d29685a64d40fb936a26bfc1faadb` is green end-to-end for unit tests, lint, debug APK build, APK verification/signature/checksum, reports, and artifact upload.
- For provider wire/signature checkpoint `c798da44be5cfc119c3727753801e0ac3059c48c`, Android CI run `37729766870` and X005 Android Recovery run `37729766863` were still in progress at the last reconciliation. Do not infer PASS from harness presence.

## Remaining X005 evidence / fault coverage
- observe and archive a successful X005 Android Recovery workflow execution for the current contract checkpoint;
- confirm submission-count behavior at the actual durable provider boundary: known RECEIVED success = zero extra POST; current SENT = explicit retry decision only; stale SENT = no retry offer;
- add fail-closed corruption coverage for manifest and immutable entry payloads (receipt corruption is already covered);
- add any missing atomic-file transition failpoints not exercised by the existing manifest/SENT ENOSPC cases;
- preserve a concrete device/emulator report artifact as experiment evidence and record exact SHA/run ID;
- independently review the signed-plan/serialized-wire contract at `c798da44be5cfc119c3727753801e0ac3059c48c` before stacking dependent provider/recovery work.

The JVM ordering/identity tests are evidence for local invariants only. Android instrumented tests become crash/storage/device evidence only when their exact workflow execution is observed successful. Do not mark X005 PASS until the remaining execution and provider-boundary evidence are recorded.
