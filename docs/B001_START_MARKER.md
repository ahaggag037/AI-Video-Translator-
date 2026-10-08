# B001 — Regression Baseline Freeze

Status: IN_PROGRESS

The Control Tower V0 bootstrap is intentionally frozen. Product work resumes here.

B001 objective:
- preserve P0-F behavior as the regression baseline
- inspect existing unit tests/fixtures
- add only the minimum missing regression harness needed before semantic/time/session architecture changes
- do not redesign translation or rendering in this batch

Frozen source baseline:
`861aadcb36cccee83d2c86e9a0c0a03b1efe6720`

Next engineering action:
inspect current tests on `build/p0g-translation-subtitle-v1`, identify uncovered baseline contracts, and add focused regression coverage before B002.
