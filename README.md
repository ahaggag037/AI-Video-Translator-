# AI Video Translator

Android AI video-translation prototype. The project is under active V1 validation and is **not release-ready**.

## Canonical state

- Repository: `ahaggag037/AI-Video-Translator-`
- Active branch: `build/p0g-gpt6-cleanroom-v1`
- Current canonical HEAD at the latest documentation cleanup checkpoint should be read from GitHub before any new work.
- Frozen regression baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
- Current state and acceptance gates: `docs/TRANSLATION_V1_ACCEPTANCE.md`
- Accepted architecture deltas: `docs/TRANSLATION_V1_ARCHITECTURE_AMENDMENTS.md`
- Frozen P0-F regression evidence: `docs/P0F_REGRESSION_BASELINE.md`
- Experiment evidence: `docs/experiments/`
- Architectural decision records: `docs/adr/`

## Current product truth

Task17 durable source/STT/legacy-translation ownership is active in the production UI. Source selection, STT request recovery, accepted translation reuse, and restart behavior use application-owned durable state rather than Compose-owned provider submission truth.

Shared raster-based subtitle presentation is now wired into preview/export on the canonical branch, but X004 acceptance is still incomplete until the required decoded-device parity evidence closes the gate. X001, X002, X003, X004, and X006 remain release blockers at their documented boundaries; X005 keeps its frozen accepted recovery anchor.

## Documentation rule

This repository intentionally keeps only current operational documentation plus evidence needed to reproduce or audit decisions. Historical worker handoffs, relay instructions, phase-status notes, and superseded architecture summaries belong in Git history or archived external pages, not in the active documentation surface.

Before changing code, read `docs/TRANSLATION_V1_ACCEPTANCE.md`, refetch the live branch HEAD and CI, and treat GitHub as code truth.
