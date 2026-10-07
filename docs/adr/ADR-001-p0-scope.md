# ADR-001 — P0 is a vertical risk-reduction slice

## Decision
The first build implements local video selection and probing only, then evolves toward a 45–60 second end-to-end sample. It does not start with Room, resume orchestration, FFmpeg, or a large navigation surface.

## Why
The largest unknowns are media/timeline correctness, STT capabilities, and Arabic preview-vs-render parity. Building persistence infrastructure before those gates would lock assumptions prematurely.

## Reversal evidence
If basic Android file access cannot reliably preserve source access for the user's actual sources, SourceAsset materialization moves earlier.
