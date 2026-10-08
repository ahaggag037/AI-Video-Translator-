# Translation V1 Architecture Amendments

Only ACCEPTED entries outrank the canonical Masterplan. Tests/review/CI are evidence, not governance approval. New autonomous amendments default to PROPOSED; acceptance requires the product owner or explicit mission delegation.

Baseline provenance and approval authority for AR-01 through AR-05: **Runtime Kernel V3.1 baseline**, explicitly supplied by the product owner in the current mission. Seeded 2026-10-08 against `7063131db17bf86091d89b51e7f4e496a34973d6`. No experiment gate is promoted by this seed.

## AR-01 — SOURCE CONTEXT
- Status: ACCEPTED
- Created/updated: 2026-10-08
- Relevant SHA: `7063131db17bf86091d89b51e7f4e496a34973d6`
- Original Masterplan affected: D002 / §7 translation context and X002.
- Problem/evidence: Runtime Kernel V3.1 baseline corrects the experiment comparison scope; current X002 document omits neighboring source context.
- Decision: X002 compares semantic unit alone; semantic unit + bounded neighboring SOURCE context; approved deterministic terminology/examples where useful. Do not create previous-target translation chains. Do not feed rolling translated target text as hidden context state. Every output-affecting input participates in request identity.
- Scope: X002 experimental request planning; no production activation.
- Supersedes: conflicting context restrictions in the original plan only.
- Downstream contracts: TranslationPlanner, request identity, X002 protocol.
- Approval authority: Runtime Kernel V3.1 baseline.

## AR-02 — PROVIDER OUTCOMES
- Status: ACCEPTED
- Created/updated: 2026-10-08
- Relevant SHA: `7063131db17bf86091d89b51e7f4e496a34973d6`
- Original Masterplan affected: §7 provider failure/refusal taxonomy.
- Problem/evidence: Runtime Kernel V3.1 baseline specifies orthogonal outcome dimensions.
- Decision: Model orthogonal dimensions such as transport, protocol, policy, content validation. Derive retry, recovery, user action, terminal state from those dimensions. Unknown remote outcome is not the same as definitely-not-submitted. Do not blindly repost a possibly submitted request.
- Scope: provider classification and recovery.
- Supersedes: conflicting flat-taxonomy interpretation only.
- Downstream contracts: TranslationProviderOutcome, RetryPolicy, durable receipts.
- Approval authority: Runtime Kernel V3.1 baseline.

## AR-03 — FONT
- Status: ACCEPTED
- Created/updated: 2026-10-08
- Relevant SHA: `7063131db17bf86091d89b51e7f4e496a34973d6`
- Original Masterplan affected: D012 / §9 font selection.
- Problem/evidence: Runtime Kernel V3.1 baseline distinguishes committed font determinism from experiment-gated family/weight.
- Decision: Committed: deterministic bundled pinned licensed Arabic-capable font system. Experiment-gated: exact family / weight. Do not silently depend on arbitrary device fonts for authored subtitle output.
- Scope: authored subtitle typography and X003.
- Supersedes: premature commitment to a particular family or weight.
- Downstream contracts: SubtitleFontProfile, asset provenance, X003 fixtures.
- Approval authority: Runtime Kernel V3.1 baseline.

## AR-04 — LAYOUT METRICS
- Status: ACCEPTED
- Created/updated: 2026-10-08
- Relevant SHA: `7063131db17bf86091d89b51e7f4e496a34973d6`
- Original Masterplan affected: §9 / §14 layout acceptance and experimental thresholds.
- Problem/evidence: Runtime Kernel V3.1 baseline specifies evidence-led metrics.
- Decision: Track FIT, OVERFLOW, REVIEW_REQUIRED, human intervention. Accepted clipping remains forbidden. Do not invent arbitrary success thresholds before evidence.
- Scope: layout measurement and experiment acceptance.
- Supersedes: unsupported numerical layout-success thresholds only.
- Downstream contracts: SubtitleLayoutResult, X003 measurement/reporting.
- Approval authority: Runtime Kernel V3.1 baseline.

## AR-05 — PERSISTENCE CEILING
- Status: ACCEPTED
- Created/updated: 2026-10-08
- Relevant SHA: `7063131db17bf86091d89b51e7f4e496a34973d6`
- Original Masterplan affected: D008 / §10 session persistence.
- Problem/evidence: Runtime Kernel V3.1 baseline limits persistence complexity.
- Decision: Use minimal robust single-writer persistence for V1. Do not build a homemade relational database. Reconsider Room only when actual relational/multi-writer complexity justifies it.
- Scope: local V1 persistence.
- Supersedes: none; clarifies implementation ceiling.
- Downstream contracts: TranslationSessionStore and schema evolution.
- Approval authority: Runtime Kernel V3.1 baseline.
