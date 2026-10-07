# Project State

Date: 2026-10-07

## Objective
Build a personal Android video translator using direct AI provider APIs. NVIDIA NIM is the chosen provider family for the first implementation.

## Verified current state
- Repository: `ahaggag037/AI-Video-Translator-` (private).
- Working branch: `build/p0-prototype`.
- Architecture V2: DESIGNED, not yet fully implemented.
- P0-A Android skeleton: IMPLEMENTED ON BRANCH.
- Video picker + persistable URI attempt: IMPLEMENTED ON BRANCH.
- Local metadata probe: IMPLEMENTED ON BRANCH.
- Unit tests for deterministic formatting helpers: IMPLEMENTED ON BRANCH.
- GitHub Actions workflow: not yet added at this checkpoint.
- APK: NOT BUILT / NOT VERIFIED.
- NVIDIA STT/translation integration: NOT IMPLEMENTED.

## External constraints
- NVIDIA hosted NIM access is being treated as zero monetary API cost for this personal prototype, but the client must still handle provider throttling/rate limits such as HTTP 429.
- The exact NVIDIA speech model/endpoint must be selected and contract-tested before the STT adapter is implemented.
- No API key is committed to GitHub or embedded in the APK.

## Next valid action
1. Add ADRs and CI workflow.
2. Open a pull request from `build/p0-prototype` to `main`.
3. Run unit tests, lint, and debug APK assembly in GitHub Actions.
4. Fix failures from actual CI evidence until green.
5. Download and device-test the first debug APK.
6. Then implement P0-B sample-audio/timeline work.
