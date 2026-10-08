# CT-00 Safety Architecture Freeze

Status: FROZEN FOR V0

Repository: `ahaggag037/AI-Video-Translator-`
Frozen baseline: `build/p0f-hardburn-mp4` @ `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`
Integration branch: `build/p0g-translation-subtitle-v1`

## Purpose

This document freezes the minimum safe architecture needed to let AI workers contribute useful work quickly without turning the control system into a second project.

## V0 decisions

1. **Vault + worker sandbox topology.** The real TV1 repository is the vault. AI workers must not be trusted with vault write authority. Worker execution should occur in an isolated sandbox repository/account when K3 is used autonomously.
2. **No direct worker merge authority.** Workers produce candidates; promotion into the integration branch is a separate operation.
3. **Static reservations first.** V0 uses explicit task assignment and ownership records. Dynamic leases/heartbeats are deferred until a worker can reliably call a custom control API.
4. **Three ownership dimensions.** Each assignment declares WRITE SET, READ/BASE SET, and CONTRACT SET. Contract changes invalidate dependent work even when files do not overlap.
5. **Prepared merge identity is authoritative.** Final CI must run on the prepared integration candidate `M = merge(I, C)`, not only on worker commit `C`.
6. **Minimal promotion kernel.** A small deterministic gate decides whether a prepared candidate is promotable. AI review is advisory evidence only.
7. **Credential separation.** No worker may receive provider secrets or a credential that can write to the vault.
8. **Fail closed.** Missing or ambiguous evidence means NOT PROMOTABLE.

## Immediate operating mode

Until a separate K3 worker identity/sandbox is available, K3 must not be used with the broad GitHub OAuth connection against the vault. ChatGPT/GitHub tooling may continue controlled work directly on this integration branch.

## Deferred from V0

- dynamic distributed leases and heartbeats
- multi-agent swarm writes
- model reputation scoring
- autonomous policy mutation
- automatic trust scores
- large vector-memory infrastructure

## Required TV1 invariants

- semantic translation truth is separate from display layout
- manual edits are not silently overwritten
- style/layout changes do not trigger translation calls
- source changes require explicit rebase/review
- output-affecting context participates in request identity
- experiment-gated behavior is not activated early
- no silent subtitle clipping/ellipsizing in accepted layouts
- P0-F remains the regression baseline
