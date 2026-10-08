# Translation V1 Execution State

## Baseline
- Branch: `build/p0f-hardburn-mp4`
- SHA: `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`

## Current implementation branch
- Branch: `build/p0g-translation-subtitle-v1`
- Control mode: `STATIC_RESERVATION_V0`

## Architecture source
- Canonical Masterplan: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (external project artifact until committed here)
- Safety freeze: `docs/CT00_SAFETY_ARCHITECTURE_FREEZE.md`
- Deterministic policy: `.clw/control-policy-v0.yaml`

## Control Tower V0
- CT00: DONE
- CT01: DONE
- CT02: IN_PROGRESS — static reservation validator is present; CI wiring is the remaining step.

## TV1 frontier
- B001: READY — freeze regression baseline/harness
- B006: READY after B001 baseline evidence — Arabic/layout harness can proceed independently
- B002: blocked until B001
- B003: blocked until B002
- B004: blocked until B003
- B011: blocked until B002

## Experiments
- X001: NOT_STARTED
- X002: NOT_STARTED
- X003: NOT_STARTED
- X004: NOT_STARTED
- X005: NOT_STARTED
- X006: NOT_STARTED

## Current safety rule for K3
K3 must not use the broad GitHub OAuth connection against the vault repository. Autonomous K3 work requires a separate worker identity/sandbox. Until then, controlled work continues through the authenticated ChatGPT/GitHub connection on the integration branch.

## Known deviations from the earlier Control Tower design
- Dynamic leases/heartbeats are deferred.
- V0 uses static reservations.
- Ownership is WRITE SET + READ/BASE SET + CONTRACT SET.
- Final CI evidence must refer to the prepared integration candidate, not only the worker commit.

## RESUME HERE
Finish CT02 by wiring the existing Android CI to validate `.clw` state and to run on pull requests targeting `build/p0g-translation-subtitle-v1`; then immediately execute B001 instead of expanding Control Tower scope.
