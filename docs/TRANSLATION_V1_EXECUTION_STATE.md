# Translation V1 Execution State

## Baseline
- Branch: `build/p0f-hardburn-mp4`
- SHA: `861aadcb36cccee83d2c86e9a0c0a03b1efe6720`

## Current implementation branch
- Integration branch: `build/p0g-translation-subtitle-v1`
- Last shared integration HEAD at live split: `d2b01ccf2ba124be73458ae81606890a02c663c2`
- Control mode: `STATIC_RESERVATION_V0`

## Live worker lanes
- Johnny/Sol: `agent/johnny/b001-b002-live` — B001/B002
- Tom/K3: `agent/tom/b006-live` — B006/X003 lane
- Live fallback relay: GitHub issue `#2` (`CLW LIVE RELAY — Johnny ↔ Tom (K3 fallback)`)
- Notion relay remains secondary when available.

## Architecture source
- Canonical Masterplan: `ASTRA_TRANSLATION_SYSTEM_MASTERPLAN_V4_1.md` (external project artifact until committed here)
- Safety freeze: `docs/CT00_SAFETY_ARCHITECTURE_FREEZE.md`
- Deterministic policy: `.clw/control-policy-v0.yaml`

## Control Tower V0
- CT00: DONE
- CT01: DONE
- CT02: DONE for V0 — static validator and Android CI wiring are present. No further infrastructure expansion unless product work exposes a real blocker.

## TV1 frontier
- B001: DONE — regression baseline/contracts inspected and frozen without turning known defects into guarantees.
- B002: IN_PROGRESS on Johnny branch — typed microsecond clocks, semantic ownership models, explicit legacy bridge, minimal atomic session foundation.
- B006: RESERVED_FOR_TOM — Arabic/Unicode/layout/X003 harness may proceed independently on Tom branch.
- B003: blocked until B002 foundation is coherent.
- B004: blocked until B003.
- B011: blocked until B002 dependencies are coherent.

## Experiments
- X001: NOT_STARTED — production clock activation remains blocked; B002 clock types are scaffolding only.
- X002: NOT_STARTED
- X003: NOT_STARTED / Tom lane expected to prepare harness
- X004: NOT_STARTED
- X005: NOT_STARTED
- X006: NOT_STARTED

## High-throughput rule
Large coherent implementation blocks are allowed before expensive/device/live-provider verification. Cheap compile/unit/static checks should still be used where available. Anything not genuinely verified must stay explicitly `UNVERIFIED`, `HARNESS_READY`, or `SCOPE_RESTRICTED`; experiment-gated production behavior must not be activated early.

## Current ownership
Johnny WRITE SET:
- `app/src/main/java/com/clw/aivideotranslator/tv1/**`
- corresponding JVM tests under `app/src/test/java/com/clw/aivideotranslator/tv1/**`
- B001/B002 state docs

Johnny READ/BASE SET:
- legacy `SubtitlePipeline.kt`
- `NvidiaSttClient.kt`
- `SttAudioPreparer.kt`
- `MainActivity.kt`

Johnny CONTRACT SET:
- typed microsecond clock semantics
- semantic source/translation ownership
- legacy-to-TV1 compatibility boundary
- single-writer session manifest semantics

Tom provisional WRITE SET:
- Arabic/Unicode/layout/font/X003-specific files, avoiding Johnny's TV1 semantic/session paths unless coordinated in issue #2.

## Known deviations from the earlier Control Tower design
- Dynamic leases/heartbeats are deferred.
- V0 uses static reservations.
- Ownership is WRITE SET + READ/BASE SET + CONTRACT SET.
- Final CI evidence must refer to the prepared integration candidate, not only the worker commit.

## RESUME HERE
Johnny: continue B002 on `agent/johnny/b001-b002-live`, inspect CI for the typed clock/legacy/session foundation, fix compile/regression failures, then open the B003 provider-outcome/retry contracts without activating new translation defaults.
Tom: use GitHub issue #2 if Notion is unavailable, handshake with Johnny, then proceed on the independent B006/X003 lane after confirming repository reality.
