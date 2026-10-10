# Translation & Subtitle V1 — Current State and Acceptance

**NOT RELEASE-READY.** This is the single operational state/evidence index for the active V1 branch. Historical worker handoffs, relay instructions, phase journals, P0-era ADRs, and superseded status pages are intentionally excluded from the active documentation surface.

## Repository identity

- Repository: `ahaggag037/AI-Video-Translator-`
- Active branch: `build/p0g-gpt6-cleanroom-v1`
- Frozen P0-F baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Accepted architecture deltas: `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`
- Frozen regression evidence: `docs/P0F_REGRESSION_BASELINE.md`
- Experiment evidence: `docs/experiments/**`

Refetch the live branch HEAD and CI before every consequential write. Documentation-only descendants are not new implementation verification anchors.

## Latest verified implementation anchor

**`b1d91a7f46998a002236c060a56723eb5b7ed1d6`** — `feat(X004): activate shared raster preview and export`.

Exact successful checks observed at this SHA:
- Android CI `38017688062`: **SUCCESS**.
- X003 Native Layout Controls `38017688016`: **SUCCESS**.
- X005 Android Recovery `38017688010`: **SUCCESS**.

This checkpoint descends from the already-activated Task17 durable source/STT/legacy-translation path. Documentation-cleanup descendants do not change the implementation anchor unless code changes and fresh verification explicitly supersede it.

## Active product truth

- One application-owned durable session root owns source/session/STT/legacy-translation state used by production UI.
- Source selection is persisted and revalidated on reopen.
- STT lifecycle is durable: `PREPARED → SENT → RECEIVED → ADOPTED`.
- `SENT` with uncertain remote outcome is never blindly reposted.
- Accepted STT/translation text can be recovered after restart without another provider call where the durable receipt permits it.
- Legacy P0-F translation segmentation/profile remains the production comparator until X002 closes.
- Shared raster subtitle presentation is wired into preview/export on the canonical branch.
- Activation does not equal acceptance: timing, semantic-switch, readability, media-parity, and performance gates remain independently controlled.

## Experiment gates

| Gate | Current state | Acceptance boundary |
|---|---|---|
| X001 clock mapping | `HARNESS_READY_PARTIAL` | Requires real hosted response bound to exact sample plus independently verified presentation origin. No guessed timing-unit/origin promotion. |
| X002 translation | `HARNESS_READY` | Requires paired legacy/semantic outputs and blind Arabic human scoring before semantic translation becomes production truth. |
| X003 layout/readability | `HARNESS_EXECUTED_PARTIAL` | Native geometry controls exist; actual-size Arabic human readability remains required. |
| X004 preview/export parity | `ACTIVATED / ACCEPTANCE_INCOMPLETE` | Shared raster preview/export is active, but decoded-device frame/audio/cue-switch/preview-lag parity evidence must close before PASS. |
| X005 recovery | **PASS at frozen exact anchor only** | Frozen accepted anchor remains `b7948478eacef67b2552d4540e4358152cf72dd6`; descendant green runs are compatibility evidence only. |
| X006 performance | `IN_PROGRESS / NOT_ACCEPTED` | Isolated performance branches exist, including a 5000-cue sequential raster stress probe. Target-device memory/allocation/hash/capture-copy/seek/export qualification is not yet accepted on canonical. |

## Quality boundaries that remain non-negotiable

1. Translation/model output owns translated text, never timing.
2. Manual edits remain authoritative; style/edit/re-export must not repeat successful AI work.
3. Unknown remote outcome is distinct from known failure; no blind retry after durable `SENT`.
4. One application-owned session store is the supported writer model; do not introduce a second live store owner without a demonstrated blocker.
5. Raw provider bodies, API keys, private source locators, and private transcripts do not belong in repository docs/logs/relay evidence.
6. X005's accepted anchor does not move merely because later descendants are green.
7. A green CI run does not substitute for missing human/provider/device acceptance evidence.

## Required before release

- Close X001 before changing authoritative STT timing interpretation.
- Close X002 before switching away from the legacy translation comparator.
- Close X003 with actual-size Arabic human readability evidence.
- Close X004 with decoded-device preview/export/audio/cue-switch/lag parity evidence.
- Close X006 with target-device performance qualification.
- Run the final device/regression/release checklist and provide legitimate release signing credentials outside the repository.

## Current work frontier

The canonical branch has completed Task17 ownership activation and advanced X004 into production wiring without declaring the gate PASS. Performance qualification has begun on isolated X006 branches and must remain non-authoritative until its evidence is reviewed and deliberately integrated.

Do not revive old worker TODOs, model relays, historical branch frontiers, or superseded handoff instructions as current authority.

## Documentation retention policy

Keep in the active branch only:
- this current-state/acceptance index;
- the concise repository `README.md`;
- accepted architecture amendments;
- frozen regression evidence still needed for comparison;
- experiment evidence that supports reproducibility/auditability.

Delete superseded phase-status notes, worker-specific execution/handoff files, old P0 ADRs, and obsolete architecture summaries from the active branch. Git history remains the historical archive.
