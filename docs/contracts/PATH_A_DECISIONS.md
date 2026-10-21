# PATH A — Productization Decisions

Status: FROZEN for implementation unless contradicted by repository evidence or physical/device evidence.

Base branch: `build/p0g-gpt6-cleanroom-v1`
Verified base HEAD at start: `47e172dfd12d2698910eb5e7630d813e1258a32a`
Working branch: `build/path-a-productize`

## 1. Strategy

Use **Path A**: productize the existing durable engine. Do not rewrite working source/session/provider/export subsystems merely for architectural elegance. The current engineering UI is replaced incrementally by the final product shell and route-driven Compose UI.

## 2. Decisions confirmed by code inspection

1. Session persistence already uses `android.util.AtomicFile`, explicit `fsync`, writer locking, immutable published objects, schema versions, and a durable active-session pointer. Do not add a second AtomicFile abstraction merely because the external review suggested one.
2. Translation requests are already one durable unit per provider request. `TranslationRequestPlan.unitId` binds the returned text to one unit outside the provider wire format; IDs and timing do not cross the provider interface. Do not batch multiple cues into one LLM response unless a later measured requirement justifies reopening this decision.
3. Translation transport already disables redirects/retry-on-connection-failure through `NvidiaTranslationWireContract`. Preserve it.
4. STT currently marks durable `SENT` before invoking transport. This is intentionally conservative and prevents blind repost after process death. Keep this boundary for now; first hardening step is to disable OkHttp automatic retry and redirects so one logical SENT attempt cannot be replayed inside the transport layer.
5. Current subtitle segmentation already exists in `SubtitlePipeline.sourceUnits()` for the prototype window. The final implementation must generalize it beyond the prototype/sample boundary rather than inventing an unrelated second segmenter.
6. The current MP4 exporter owns presentation styling internally. Final Editor Preview, Fullscreen Preview, and MP4 must instead consume one shared presentation/layout truth.
7. `ACTION_OPEN_DOCUMENT` is already the picker contract. The current activation sequence is still wrong for the final product because durable activation begins before candidate probing finishes. Final source flow is Candidate → Probe → Ready → explicit/atomic activation.
8. `android:allowBackup="false"` already exists. Preserve it.

## 3. Product contract clarifications

### Review flags

Keep semantic/text review and visual/layout review separate:

- `TextReviewFlag`: semantic acceptance problem. Blocks SRT and MP4 until resolved/accepted according to the specific reason.
- `LayoutResult.FITS | OVERFLOW | REVIEW_REQUIRED`: presentation problem. Blocks MP4 when not FITS; does **not** block SRT by itself.

Do not overload one `REVIEW_REQUIRED` enum across both domains.

### Translation ownership

Translation owns text only. It never owns source timing. Current one-unit-per-request binding remains the V1 mechanism that guarantees a provider response cannot merge/split the app timeline.

### Both mode

If MP4 is blocked by presentation state, `Both` is disabled with an explicit reason. SRT remains independently available. There is no silent downgrade from Both to SRT.

### Export interruption

Process death/interruption during an active MP4 export becomes typed `INTERRUPTED`. A previously validated sibling output remains valid and visible.

### Restore machine text

`Restore machine` creates a new reversible manual draft/revision whose content equals the latest accepted machine text. It never deletes manual history.

### Style override identity

Cue style overrides are keyed by stable `cueId`. Text/timing edits do not silently erase them. Revision lineage is provenance/fencing metadata, not the lookup key.

### Single active project

V1 keeps one app-owned active session/project. Starting a new source requires explicit replacement confirmation when durable work exists. Exported user files remain independent of session replacement.

## 4. Reopen precedence

The final pure reopen resolver uses this priority:

1. unreadable/unsupported session → Local Recovery
2. no active session → Home
3. unresolved provider SENT/unknown outcome → Provider Recovery
4. interrupted export → Export Progress typed failure
5. locally resumable processing state → Processing (never auto-submit provider work merely because the app reopened)
6. credential missing/rejected **only when the next required step needs a provider** → AI Setup
7. source missing/incompatible → Source Recovery
8. validated output matching the current accepted revision → Result
9. edit-ready accepted text → Editor
10. active source without accepted text → Source Review / AI Setup according to next required step

Home remains the stable product root under resumed destinations.

## 5. UI/productization architecture

- Single Activity + Compose stays.
- `MainActivity` becomes a thin host.
- One app-owned route/back-stack owner; durable session is semantic truth, not the navigation stack.
- UI components emit intents; provider/export side effects remain outside Composables.
- One `EditorViewModel`/state owner coordinates selected cue, playback, Text/Timing/Style, and fullscreen preview.
- No permanent bottom navigation.
- Quiet Pro Media tokens remain the design source.

## 6. Shared presentation rule

One implementation must own:

`EffectiveText + EffectiveDisplayTiming + EffectiveStyle + FrameGeometry + PinnedFontProfile → authoritative presentation/layout snapshot`

Editor Preview, Fullscreen Preview and burned MP4 consume that snapshot. No independent Compose Text wrapping is allowed to become export truth. Spatial style values remain video-content-relative.

## 7. Transport safety ruling

Immediate safety change before new live-provider qualification:

- STT OkHttp: `retryOnConnectionFailure(false)` and `followRedirects(false)`.
- Keep durable SENT-before-transport semantics until a separately tested EventListener boundary is proven better without weakening duplicate safety.
- Translation keeps its existing explicit retry/redirect policy.

This intentionally prefers a conservative HOLD over an accidental duplicate provider request.

## 8. Evidence-gated items that remain open

Do not manufacture PASS for:

- X001 authoritative STT timing unit/origin
- X002 Arabic semantic/human acceptance
- X003 physical Arabic readability
- X004 Preview/MP4/audio/rotation parity
- X006 performance/resource/thermal qualification
- exact shipping subtitle fonts/weights/licenses
- exact long-running Android service/FGS declaration for current target SDK
- exact credential secret-store implementation until implemented and reboot-tested
- release storage/MediaStore edge behavior until device-tested

These do not block starting Path A. They may block beta/final release according to their owning gate.

## 9. Explicit non-decisions from the external review

The following suggestions are **not** adopted automatically:

- custom navigation framework choice beyond app-owned semantic routing
- EventListener-defined SENT boundary replacing the existing conservative boundary
- a user acknowledgement that bypasses true visual overflow
- `isShrinkResources=true` while minification remains disabled
- exact foreground-service permission/type declarations without current Android documentation verification
- custom crash handler as a release requirement

They require repository/device/platform evidence before adoption.
