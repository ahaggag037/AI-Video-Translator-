# ADR-002 — NVIDIA NIM is the first provider family

## Decision
The first provider adapters target NVIDIA-hosted NIM endpoints. The API key is entered on-device later and is never committed to GitHub.

## Constraints
Free developer access is treated as zero monetary API cost for this personal prototype, but not as unlimited throughput: 429/rate-limit handling remains required.

## Open gate
The exact NVIDIA speech model/endpoint must be selected based on timestamp support and actual audio-language quality before implementing the STT adapter.
