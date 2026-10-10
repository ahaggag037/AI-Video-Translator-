# Translation & Subtitle V1 — Current State and Acceptance

**NOT RELEASE-READY.** This is the single operational state/evidence index for the active V1 branch. Historical worker handoffs, relay instructions, phase journals, P0-era ADRs, and superseded status pages are intentionally excluded from the active documentation surface.

## Repository identity

- Repository: `ahaggag037/AI-Video-Translator-`
- Active branch: `build/p0g-gpt6-cleanroom-v1`
- Active final-release orchestration: `docs/RELEASE_ORCHESTRATION.md`
- Frozen P0-F baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Accepted architecture deltas: `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`
- Frozen regression evidence: `docs/P0F_REGRESSION_BASELINE.md`
- Experiment evidence: `docs/experiments/**`

Refetch the live branch HEAD and CI before every consequential write. Documentation-only descendants are not new implementation verification anchors.

## Latest verified implementation anchor

**`83959720b14a6a5050c6209756f02d69ab78cba3`** — canonical merge of PR #9 after the already-integrated W2/X002 and W3/X003+X004 work, adding the consolidated X006 qualification harnesses without changing production semantics.

Exact successful checks observed for this canonical implementation SHA:
- Android CI `38041521965`: **SUCCESS** — unit tests, lint, debug APK build, APK existence/signature/checksum, and artifact upload.
- X003 Native Layout Controls `38041521981`: **SUCCESS** on API 29 and API 35.
- X005 Android Recovery `38041521968`: **SUCCESS** as descendant compatibility evidence only; the frozen X005 PASS anchor does not move.

Exact pre-merge X006 evidence for synced worker head `60a15145abc6e7d7f6995c478f8bb2990db5a62a`:
- X006 Performance Qualification `38040352226`: **SUCCESS** — build/unit/lint/APKs and API35 instrumentation all green. This remains emulator staging evidence, not target-device acceptance.

X002 was integrated earlier at canonical merge `091dfa3c82e1724d70d4618eea316f03e9341b45` with Android CI `38039985996`: **SUCCESS**. The integration is shadow-only; it does not switch production translation away from the legacy comparator.

W3 presentation hardening remains integrated from PR #8. Its automated checks and the newer canonical descendant checks do **not** replace the remaining human or physical-target-device acceptance evidence.

## Active product truth

- One application-owned durable session root owns source/session/STT/legacy-translation state used by production UI.
- Source selection is persisted and revalidated on reopen.
- STT lifecycle is durable: `PREPARED → SENT → RECEIVED → ADOPTED`.
- `SENT` with uncertain remote outcome is never blindly reposted.
- Accepted STT/translation text can be recovered after restart without another provider call where the durable receipt permits it.
- Legacy P0-F translation segmentation/profile remains production truth; X002 semantic behavior is integrated only as a shadow/evaluation path.
- Shared raster subtitle presentation is wired into preview/export on the canonical branch.
- W3 presentation hardening is integrated and remains green through the current canonical descendant.
- X006 source-copy/hash, large-timeline, seek, raster-retention, and target-device-runner harnesses are integrated; emulator staging is green.
- Activation and automated hardening do not equal acceptance: timing, semantic-switch, human readability, physical-device media parity, and target-device performance remain independently controlled.

## Experiment gates

| Gate | Current state | Acceptance boundary |
|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL / FAIL_CLOSED` | Requires real hosted response bound to exact sample plus independently verified presentation origin. No guessed timing-unit/origin promotion. Integrator verification is actively hardening the nonzero-presentation-origin fixture. |
| X002 translation | `INTEGRATED_SHADOW / HARNESS_READY / HUMAN_PROVIDER_PENDING` | Deterministic shadow evaluation and blind-scoring tooling are integrated. Requires paired live legacy/semantic provider outputs and blind Arabic human scoring before semantic translation becomes production truth. |
| X003 layout/readability | `HARNESS_HARDENED / ANDROID_EXECUTED_PARTIAL / HUMAN_PENDING` | Canonical API29/API35 native controls and added Arabic/layout falsifiers are green; actual-size Arabic human readability on a physical target device remains required. |
| X004 preview/export parity | `ACTIVATED / ANDROID_PARITY_HARDENED / ACCEPTANCE_INCOMPLETE` | Shared raster preview/export remains the single presentation truth and automated boundary/stale-raster/trailing-gap checks are integrated; target physical-device decoded-frame, rotation, cue-switch/preview-lag, and decoded subtitle-export audio parity evidence must still close before PASS. |
| X005 recovery | **PASS at frozen exact anchor only** | Frozen accepted anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; `38041521968` at `83959720...` is compatibility evidence only and does not move the accepted anchor. |
| X006 performance | `INTEGRATED / API35_STAGING_QUALIFIED / NOT_ACCEPTED` | Consolidated X006 harnesses are now on canonical and exact-head API35 emulator staging is green. Physical target-device memory/allocation/hash/capture-copy/seek/export qualification is still required before PASS. |

## Quality boundaries that remain non-negotiable

1. Translation/model output owns translated text, never timing.
2. Manual edits remain authoritative; style/edit/re-export must not repeat successful AI work.
3. Unknown remote outcome is distinct from known failure; no blind retry after durable `SENT`.
4. One application-owned session store is the supported writer model; do not introduce a second live store owner without a demonstrated blocker.
5. Raw provider bodies, API keys, private source locators, and private transcripts do not belong in repository docs/logs/relay evidence.
6. X005's accepted anchor does not move merely because later descendants are green.
7. A green CI or emulator run does not substitute for missing human/provider/physical-device acceptance evidence.

## Required before release

- Close X001 before changing authoritative STT timing interpretation.
- Complete X002 live paired-provider evidence and blind Arabic evaluation before switching away from the legacy translation comparator.
- Close X003 with actual-size Arabic human readability evidence on the required physical target-device context.
- Close X004 with target physical-device decoded preview/export/audio/cue-switch/lag/rotation parity evidence.
- Close X006 with target-device performance qualification.
- Run the final device/regression/release checklist and provide legitimate release signing credentials outside the repository.

## Current work frontier

W2 / PR #11, W3 / PR #8, and W4 / PR #9 are integrated on canonical. Current implementation verification anchor is `83959720b14a6a5050c6209756f02d69ab78cba3` with canonical Android CI, X003 API29/API35, and X005 descendant-compatibility checks green.

W1/X001 is the remaining code/evidence integration frontier. The hosted NVIDIA NVCF HTTP timing contract is still not promoted from another protocol by assumption; the release leader is running a redacted hosted-provider experiment and independent Android presentation-origin verification. X001 remains fail-closed until both sides of that boundary are actually evidenced.

External acceptance still remains for X002 blind Arabic quality, X003 actual-size Arabic readability, X004 physical-device decoded media/lag/audio/rotation parity, and X006 target-device performance. These cannot be replaced by green CI alone.

The release leader remains the only authority for canonical integration, gate-state promotion, final regression, signing, and release artifact production. Do not revive old worker TODOs, model relays, historical branch frontiers, or superseded handoff instructions as current authority.

## Documentation retention policy

Keep in the active branch only:
- this current-state/acceptance index;
- the concise repository `README.md`;
- active release orchestration while the final push is in progress;
- accepted architecture amendments;
- frozen regression evidence still needed for comparison;
- experiment evidence that supports reproducibility/auditability.

Delete superseded phase-status notes, worker-specific execution/handoff files, old P0 ADRs, and obsolete architecture summaries from the active branch. Git history remains the historical archive.
