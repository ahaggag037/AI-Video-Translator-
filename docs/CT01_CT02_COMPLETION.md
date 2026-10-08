# CT-01 / CT-02 Completion

CT-01 and the intentionally minimal CT-02 bootstrap are complete enough for immediate TV1 use.

Implemented:
- frozen V0 safety decisions
- static reservation protocol
- deterministic repository validator
- exact checkout-SHA control evidence
- CI on PRs targeting the P0G integration branch
- durable task/execution state

Not implemented by design:
- dynamic distributed leases
- external database
- custom MCP control service
- autonomous K3 promotion
- multi-agent swarm writes

Reason: these are not required to resume the product work safely. Expansion requires a concrete coordination need.

Next: B001.
