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

**`550376b83cf845cedcdf114708632aff1d007134`** — merge of PR #8, `W3 X003/X004 presentation hardening`.

Exact successful checks observed for this canonical implementation SHA:
- Android CI `38030554683`: **SUCCESS** — unit tests, lint, debug APK build, APK existence/signature/checksum, and artifact upload.
- X003 Native Layout Controls `38030554666`: **SUCCESS** on API 29 and API 35.
- X005 Android Recovery `38030554647`: **SUCCESS** as descendant compatibility evidence only; the frozen X005 PASS anchor does not move.

Pre-merge exact-worker evidence for W3 head `ea5831c3984c727b23b0d72a435bba306cdaa137` also succeeded:
- Android CI `38029681380`: **SUCCESS**.
- Integrator exact-code X003 verification `38030134611`: **SUCCESS** on API 29 and API 35, including the added stale-raster, touching-cue-boundary, and explicit-multiline falsifiers.

The canonical merge fixes the protected-range remap after canonical trimming, preserves explicit multiline raster truth, keeps preview playback through the complete source sample, and strengthens X003/X004 falsifiers without introducing a second rendering truth. These automated checks do **not** replace the remaining human or physical-target-device acceptance evidence.

## Active product truth

- One application-owned durable session root owns source/session/STT/legacy-translation state used by production UI.
- Source selection is persisted and revalidated on reopen.
- STT lifecycle is durable: `PREPARED → SENT → RECEIVED → ADOPTED`.
- `SENT` with uncertain remote outcome is never blindly reposted.
- Accepted STT/translation text can be recovered after restart without another provider call where the durable receipt permits it.
- Legacy P0-F translation segmentation/profile remains the production comparator until X002 closes.
- Shared raster subtitle presentation is wired into preview/export on the canonical branch.
- W3 presentation hardening is integrated on canonical and verified by Android CI plus API29/API35 instrumentation.
- Activation and automated hardening do not equal acceptance: timing, semantic-switch, human readability, physical-device media parity, and performance gates remain independently controlled.

## Experiment gates

| Gate | Current state | Acceptance boundary |
|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL` | Requires real hosted response bound to exact sample plus independently verified presentation origin. No guessed timing-unit/origin promotion. |
| X002 translation | `HARNESS_READY` | Requires paired legacy/semantic outputs and blind Arabic human scoring before semantic translation becomes production truth. |
| X003 layout/readability | `HARNESS_HARDENED / ANDROID_EXECUTED_PARTIAL / HUMAN_PENDING` | Canonical API29/API35 native controls and added Arabic/layout falsifiers are green; actual-size Arabic human readability on a physical target device remains required. |
| X004 preview/export parity | `ACTIVATED / ANDROID_PARITY_HARDENED / ACCEPTANCE_INCOMPLETE` | Shared raster preview/export remains the single presentation truth and automated boundary/stale-raster/trailing-gap checks are integrated; target physical-device decoded-frame, rotation, cue-switch/preview-lag, and decoded subtitle-export audio parity evidence must still close before PASS. |
| X005 recovery | **PASS at frozen exact anchor only** | Frozen accepted anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; `38030554647` at `550376b...` is compatibility evidence only and does not move the accepted anchor. |
| X006 performance | `IN_PROGRESS / NOT_ACCEPTED` | W4 has an isolated X006 qualification PR with green API35 staging evidence, but target-device memory/allocation/hash/capture-copy/seek/export qualification is still required and no X006 work is accepted on canonical yet. |

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
- Close X002 before switching away from the legacy translation comparator.
- Close X003 with actual-size Arabic human readability evidence on the required physical target-device context.
- Close X004 with target physical-device decoded preview/export/audio/cue-switch/lag/rotation parity evidence.
- Close X006 with target-device performance qualification.
- Run the final device/regression/release checklist and provide legitimate release signing credentials outside the repository.

## Current work frontier

W3 / PR #8 is integrated and verified on canonical at implementation anchor `550376b83cf845cedcdf114708632aff1d007134`. X003 and X004 remain acceptance-incomplete only because the explicitly required human/physical-device evidence has not yet been supplied; the automated Android presentation work is no longer isolated worker-only evidence.

W1/X001 and W2/X002 remain isolated until their evidence and PRs are reviewed. W4/X006 has produced an isolated performance qualification PR with green staging evidence, but it must be reconciled against the newer canonical lineage and must not be promoted to PASS without target-device evidence.

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
