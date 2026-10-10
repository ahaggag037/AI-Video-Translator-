# Round 2 field test — provider timeout / durable SENT evidence

Date: 2026-10-10

Scope: isolated `build/field-test-round2` full-video STT path only. This record does not promote or invalidate any release gate by itself.

## Code/test anchor

- Field-test implementation HEAD under test: `3184d6574273f5b9c8f5baedf7421ebde5d1c55d`
- Parent lineage includes canonical `47e172dfd12d2698910eb5e7630d813e1258a32a`.
- Android CI run `38073826630` for exact field-test HEAD: **SUCCESS**.
- The field-test branch is intentionally isolated from canonical active-session/source-snapshot ownership.

## User-device evidence

Raw user media is intentionally **not** committed. API keys, private source locators, and transcript content are excluded. The local artifacts are bound here only by SHA-256 plus non-sensitive metadata and redacted observations.

### Screen recording

- SHA-256: `e8284ed484a584e9af59497b62c11d5df7d9fccb7e6d3463a953fb6db7ae967f`
- Container-observed size: `5,978,735` bytes
- Duration: `68.388422 s`
- Video: H.264, `578x1280`, `60000/1001` fps
- Audio: AAC
- Redacted observation: during the captured interval the Round-2 UI remains in full-audio preparation for the selected full video; the recording alone does not prove an infinite hang.

### Screenshot A — first STT provider attempt

- SHA-256: `147f572e7e6bf6d27eca6c42b9bb18b536995490486edcfc4e232aca20a7183b`
- JPEG dimensions: `695x1536`
- Selected source metadata visible in UI: `11:13`, `426x240`, `15.6 MB` (display name omitted here)
- STT progress: window `1 / 12`, completed `0 / 12`
- Stage: waiting for NVIDIA response
- Window interval: `0:00 -> 1:00`
- Terminal message visible for the invocation: `timeout`

### Screenshot B — next invocation / reopen

- SHA-256: `5d5d9c59614b9d6a97c27cceed815e865a438179f1943bd3272f9b4a402e8b50`
- JPEG dimensions: `695x1536`
- STT completed: `0 / 12`
- Stage: journal preflight before submission
- UI states that a previously SENT STT window has an uncertain remote outcome and automatic resend is stopped to prevent a duplicate request.

## Reconstructed execution sequence

The device evidence and exact implementation agree on this sequence:

1. Full audio is decoded and split into deterministic source windows.
2. Window 1 is prepared and its receipt is persisted as `PREPARED`.
3. The receipt is durably advanced to `SENT` **before** provider transport is allowed to execute.
4. The UI advances to `WAITING_RESPONSE` and invokes `NvidiaSttClient.transcribeEnglishSampleDetailed(...)`.
5. The blocking OkHttp call terminates with a timeout before a successful response can be persisted as `RECEIVED`.
6. Because the exception occurs after the durable `SENT` write, the window remains `SENT`.
7. On the next invocation the full-video preflight finds that `SENT` receipt and throws `UnknownSttRemoteOutcomeException` before any new provider call.
8. The UI enters `STT_UNKNOWN_REMOTE_OUTCOME`; automatic duplicate submission is therefore prevented.

## Root-cause trace

### Transport timeout boundary

`NvidiaSttClient` currently configures:

- connect timeout: 30 seconds
- read timeout: 3 minutes
- write timeout: 3 minutes
- whole-call timeout: 4 minutes

The detailed STT method performs `client.newCall(request).execute()` inside `runCatching`. A transport timeout is therefore returned as a failed `Result`; no accepted observation exists to persist.

### Durable window state

`FieldTestFullVideoSttOperation` intentionally calls `journal.markSent(...)` before invoking provider transport. Its own comment states that any exception/process death after that write leaves `UNKNOWN_REMOTE_OUTCOME` on the next invocation. There is no transition from `SENT` back to a retryable state on timeout because the client cannot prove whether the remote service received/accepted the request.

`FieldTestSttWindowReceipt.recoveryDisposition()` maps `SENT` directly to `UNKNOWN_REMOTE_OUTCOME`.

### UI liveness gap

`FieldTestRound2ViewModel` correctly maps `UnknownSttRemoteOutcomeException` to `STT_UNKNOWN_REMOTE_OUTCOME` and explains that automatic resend was stopped.

`FieldTestRound2Activity` then disables the full-video STT action whenever the phase is `STT_UNKNOWN_REMOTE_OUTCOME`. No reconcile, abandon-window, start-new-session, or explicitly user-authorized retry action is exposed on this Round-2 screen.

Therefore the observed behavior is **not** a duplicate-send bug: the safety fence works. The field-test blocker is a liveness/recovery-UX gap after a provider timeout with unknown remote outcome.

## Gate attribution

- **Not X006:** the failure is provider transport/recovery state, not the integrated target-device performance qualification harness.
- **X005 compatibility boundary:** the behavior preserves the accepted invariant that an uncertain durable `SENT` is never blindly reposted. The frozen X005 PASS anchor is not invalidated by this evidence.
- **Release/field-test blocker:** the current Round-2 full-video test cannot continue in the same field-test session after this timeout because no explicit recovery decision is available in its UI.
- This also prevents the Round-2 run from reaching downstream full-video semantic translation/presentation evidence, but it is not itself evidence about semantic quality, timing correctness, or raster parity.

## Required correction boundary

Do **not** fix this by silently deleting `SENT`, lowering the journal safety bar, or automatically resubmitting after timeout.

A sound correction must preserve unknown-remote-outcome semantics and add an explicit, auditable user decision/recovery path. Candidate designs should be tested against duplicate-submit counting before use with a paid provider. Timeout values may be reviewed separately, but increasing a timeout alone does not resolve the recovery-state liveness gap.

## Verdict

`FIELD_TEST_ROUND2_STT_TIMEOUT = REPRODUCED / ROOT_CAUSE_TRACED / FAIL_CLOSED`

Safety behavior is functioning as designed; same-session Round-2 liveness after an uncertain provider timeout is currently blocked and requires an explicit recovery UX/protocol decision.