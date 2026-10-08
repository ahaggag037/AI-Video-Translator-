# CLW Safe Engineering Kernel V0

This directory is intentionally small. It exists to coordinate TV1 work without turning infrastructure into a second product.

## Use it now

For controlled work on `build/p0g-translation-subtitle-v1`:

1. Read `docs/TRANSLATION_V1_EXECUTION_STATE.md`.
2. Pick only a READY task.
3. If using an `agent/**` branch, add one static reservation to `reservations.json` before work starts.
4. Keep edits inside the declared WRITE SET.
5. Treat READ/BASE SET and CONTRACT SET as dependency identity; if they change, revalidate dependent work.
6. Push the branch and open a PR to `build/p0g-translation-subtitle-v1`.
7. The Android CI checks the prepared PR candidate and emits `.clw/out/control-evidence.json`.
8. Do not treat model self-reports as proof of tests or experiment PASS.

## Fast path

ChatGPT-controlled work may continue directly on the integration branch while the K3 sandbox identity is not yet provisioned. The goal is to return to TV1 implementation immediately; do not expand this kernel unless a concrete coordination failure demands it.
