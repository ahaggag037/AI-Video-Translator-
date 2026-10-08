# CT-02 Static Reservation Kernel V0

This is the active coordination contract for TV1 until dynamic leases are introduced.

## Assignment record

An active reservation in `.clw/reservations.json` must contain:

```json
{
  "reservation_id": "R-...",
  "task_id": "...",
  "actor": "...",
  "branch": "agent/<actor>/<task>/<nonce>",
  "status": "RESERVED|RUNNING|VERIFYING|REVIEW_REQUIRED|APPROVED",
  "risk": "R0|R1|R2|R3|R4",
  "write_set": ["path/**"],
  "read_base_set": ["path/or/contract-source"],
  "contract_set": ["ContractName"],
  "control_change": false
}
```

## Enforcement now

- active WRITE SETs may not overlap
- an `agent/**` branch with no matching active reservation fails control validation
- changed files outside the reservation WRITE SET fail control validation
- `.clw/**` and `.github/workflows/**` require an R4 control-change reservation
- CI records the exact checkout SHA it tested
- pull requests targeting `build/p0g-translation-subtitle-v1` run the full Android CI pipeline

## Contract invalidation in V0

CONTRACT SET conflicts are recorded and reviewed before parallel assignment. Automated semantic contract invalidation is deliberately deferred; V0 uses static assignment to avoid pretending this can be inferred perfectly from file paths.

## Promotion rule

A worker commit is not, by itself, promotable evidence. For PR validation, GitHub Actions checks out the PR merge candidate and records that exact SHA. If integration changes, a new candidate must be generated and tested.

## K3 boundary

Do not run autonomous K3 against the vault using the current broad GitHub OAuth grant. K3's future worker identity must be isolated to a sandbox account/repository. Until that is ready, the Control Tower is already usable by ChatGPT/GitHub-controlled work on the integration branch.
