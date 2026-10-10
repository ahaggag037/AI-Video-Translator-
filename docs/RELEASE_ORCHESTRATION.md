# Translation V1 — Parallel Release Orchestration

This file is the active coordination contract for the final release push. It does **not** replace `docs/TRANSLATION_V1_ACCEPTANCE.md`; GitHub code, exact SHAs, CI/device/provider evidence, and that acceptance index remain the engineering truth.

## Release authority

1. Product owner: final product/release authority.
2. Release leader/integrator: this ChatGPT/Sol thread. Only the leader may approve worker output for integration, update canonical acceptance state, merge worker PRs, or produce the final release candidate.
3. Worker models: isolated gate owners. They may implement/test only inside their assigned branch and may not declare a global release PASS.
4. Canonical branch: `build/p0g-gpt6-cleanroom-v1`.
5. Worker base checkpoint: `bb7560eef83a24e574f2539a041f04fecfdc826a`.

## Worker branches and ownership

| Worker | Gate ownership | Branch | Primary responsibility |
|---|---|---|---|
| W1 | X001 | `parallel/release-x001-timing` | Provider/STT timing interpretation, sample/presentation clock mapping, hosted-response evidence harnesses. |
| W2 | X002 | `parallel/release-x002-translation` | Legacy-vs-semantic translation comparison, request identity/context correctness, blind Arabic evaluation package. |
| W3 | X003 + X004 | `parallel/release-x003-x004-presentation` | Arabic readability/layout plus preview/export raster, cue-switch, lag and decoded-media parity. |
| W4 | X006 | `parallel/release-x006-performance` | Source copy/hash, memory/allocation, 5,000-cue seek/burn, export/performance qualification and bounded-resource fixes. |

The leader owns all cross-gate production changes, release configuration, signing/packaging, acceptance-document edits, and final integration.

## Isolation rules

- Never commit directly to canonical from a worker session.
- Never push a worker's changes into another worker branch.
- Each worker starts by verifying its exact branch HEAD and the canonical acceptance contract.
- A worker may read any code/evidence but should modify only files necessary for its gate.
- If a required fix crosses another worker's ownership boundary, stop that cross-scope edit and report it as `CROSS_GATE_DEPENDENCY` to the leader with exact file/symbol/reason.
- Do not rewrite shared architecture, session ownership, provider semantics, or release configuration merely to simplify a local test.
- No worker may change `docs/TRANSLATION_V1_ACCEPTANCE.md` or this orchestration file.
- No worker may move X005's frozen accepted anchor.

## Evidence rule

A claim is acceptable only when its evidence class matches the claim:

- code behavior: exact commit SHA + automated test/CI;
- Android/device behavior: exact SHA + device/API/model evidence;
- provider behavior: exact request/response contract evidence without secrets or private transcripts;
- Arabic readability/quality: human scoring evidence where the gate explicitly requires it;
- performance: measured target-device evidence when the acceptance boundary requires target-device qualification.

Green CI is compatibility evidence, not automatic experiment PASS. Missing real-human, provider, or device evidence must remain explicitly pending.

## Worker execution loop

1. Read `docs/TRANSLATION_V1_ACCEPTANCE.md` and this file.
2. Verify the assigned branch and exact base SHA.
3. Inspect existing experimental branches/evidence first; reuse sound work instead of rebuilding it.
4. State the narrow falsifiable acceptance question for the gate.
5. Implement the smallest production-safe change or evidence harness that advances that question.
6. Run the strongest available deterministic tests.
7. Commit coherent checkpoints with gate-prefixed messages such as `test(X006): ...` or `fix(X001): ...`.
8. Repeat until the gate is either evidence-complete or blocked by a genuinely external requirement.
9. Open one PR from the worker branch to canonical only when the branch is internally coherent and CI is green enough for review.
10. Deliver the handoff record below; do not self-merge.

## Mandatory worker handoff

Every worker must return exactly these fields:

```text
WORKER_HANDOFF v1
worker: W1|W2|W3|W4
scope: X001|X002|X003+X004|X006
base_sha: <exact original base>
head_sha: <exact worker HEAD>
pr: <number or NONE>
changed_files: <paths>
verified: <only directly verified facts>
ci_runs: <run ids + conclusions>
device_provider_human_evidence: <exact evidence or PENDING>
remaining_blockers: <none or explicit list>
cross_gate_dependencies: <none or explicit list>
recommended_gate_state: <state; never global RELEASE_READY>
risk_notes: <concise>
```

Any handoff missing an exact HEAD SHA or overstating unverified evidence is rejected without integration.

## Leader integration protocol

For every worker PR the leader must:

1. Refetch canonical HEAD and worker HEAD.
2. Inspect changed filenames and full patches.
3. Reject scope leakage, duplicated architecture, weakened fail-closed behavior, hidden retries, or undocumented provider/timing assumptions.
4. Confirm CI results at the worker HEAD.
5. Re-run/require gate-specific checks where the PR evidence does not cover integration risk.
6. Merge with expected-head fencing so a moved worker branch cannot be merged accidentally.
7. Immediately verify canonical CI after each merge.
8. If canonical fails, revert/fix before accepting another worker merge; do not stack unknown failures.
9. Update `TRANSLATION_V1_ACCEPTANCE.md` only after evidence is valid on the integrated lineage.

## Preferred integration order

Order is evidence/risk-driven, not completion-time-driven:

1. X001 if it changes timing interpretation used downstream.
2. X002 if it changes production translation semantics.
3. X003/X004 presentation path.
4. X006 performance-only changes/harnesses.

Independent test-only commits may be integrated earlier if they cannot alter product semantics. The leader may change the order when dependency evidence requires it.

## Shared-file collision policy

Workers do not resolve shared-file conflicts by guessing. The worker reports the desired semantic change and leaves integration to the leader when any of these are touched by multiple gates:

- production UI ownership;
- durable session/store ownership;
- shared timing primitives;
- shared raster/export coordinator;
- provider request identity/retry semantics;
- Gradle/release/workflow configuration;
- acceptance/orchestration documentation.

The leader performs the single combined edit on canonical/integration lineage and adds regression coverage for both gates.

## Release candidate pipeline

A final APK can be called release candidate only after:

1. all required gates have explicit accepted evidence or the product owner explicitly narrows the release requirement;
2. canonical Android CI is green at one exact SHA;
3. final device/regression checklist is executed on that SHA;
4. no unresolved `SENT` retry/recovery correctness regression exists;
5. preview/export/SRT use authoritative timing and shared presentation truth;
6. performance qualification is accepted for target devices;
7. release build is produced from the exact accepted canonical SHA;
8. signing uses legitimate credentials supplied outside the repository;
9. APK/AAB checksum, signing identity, version, and source SHA are recorded;
10. a clean-install smoke test of the signed artifact passes.

## Final artifact record

The leader records:

```text
RELEASE_ARTIFACT v1
source_sha: <exact canonical SHA>
version_name: <value>
version_code: <value>
artifact: <APK/AAB name>
sha256: <digest>
signing_identity: <non-secret certificate identity>
ci: <run id>
device_matrix: <verified devices/APIs>
gates: X001=... X002=... X003=... X004=... X005=... X006=...
known_limitations: <NONE or explicit list>
```

The project is not declared complete merely because an APK was generated. Completion means the artifact above is traceable to accepted evidence and the exact source SHA.