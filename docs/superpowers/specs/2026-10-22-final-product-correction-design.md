# Final Product Correction Design

**Date:** 2026-10-22
**Branch:** `build/final-product-correction`
**Base:** `5c09f53d7f9c06b0555dcd69c9cd5c937c50d7f6`

## 1. Purpose

Turn the current signed-but-incomplete Android build into the product that was actually designed: a stable Arabic-first video translation workflow whose navigation, processing, editing, preview, export, recovery, and release behavior are all functional and verifiable.

This correction must preserve the substantial productization work already present on `build/path-a-recovery-correction`; it must not restart the application from scratch.

The visual/product reference is the existing `ai_video_translator_prototype_studio.html` prototype plus the accepted project masterplan and current durable-session architecture. The prototype defines product hierarchy and interaction intent; the Android implementation remains authoritative for platform constraints and durable truth.

## 2. Observed release failures to correct

The previously distributed signed APK is not a valid final-product baseline because it was cut from an older snapshot before the later productization work.

User-visible failures observed on device include:

1. Processing can remain on the Translation stage without enough progress information to distinguish healthy work from a stalled provider call.
2. `Processing -> Details` was non-functional in the older signed APK.
3. `Back` from Processing visibly leaves and is then immediately forced back to Processing by route synchronization while an active session exists.
4. Several product surfaces existed as Compose shells before their state/callbacks were fully wired.
5. Release signing/build success was treated as equivalent to product acceptance. It is not.

The correction branch must close these failures against the latest productized code, not merely patch the old signed APK.

## 3. Product invariants

### 3.1 Home is a real root

Home is the stable root of the product. A deliberate user navigation to Home must remain on Home unless a **critical recovery condition** makes normal product use unsafe (for example unreadable/corrupt local session state or a source/provider recovery state that requires explicit handling).

An active background/durable session is not, by itself, permission to override an explicit user choice to view Home.

Home may show an active-project card with truthful progress and a `Continue` action that re-enters the appropriate route.

### 3.2 Navigation is user-owned; durable state is truth-owned

`ProductNavigator` owns transient route intent. Durable stores/ViewModels own session/provider/export truth. `ProductRouteSynchronizer` may recover or advance a route when opening/reopening a session, but it must not continuously erase explicit user navigation.

Critical recovery may preempt ordinary navigation. Ordinary in-progress work may not.

### 3.3 Remote work is not falsely cancelled

Leaving Processing does not imply remote cancellation. If a provider request has already been submitted, the app must preserve the durable state and truthfully communicate that the operation may still complete.

If safe cancellation is genuinely supported before remote submission, it can be offered. No false cancellation affordance is allowed after submission/unknown outcome.

### 3.4 No reachable dead controls

Any control visible on a reachable production route must either:

- perform its promised action,
- be visibly disabled with a truthful reason, or
- be removed from the production surface.

No reachable production control may depend on a default empty callback.

## 4. Processing experience

The Processing surface must align with the original prototype hierarchy while remaining evidence-driven.

### 4.1 Required visible information

Show:

- session/project identity when available;
- five product stages: audio preparation, STT, text preparation, translation, review preparation;
- completed/current/upcoming stage state;
- provider submission state when known;
- elapsed local processing time;
- latest truthful activity/status detail;
- determinate progress only when a real denominator exists.

For translation, if the engine knows `completed units / total units`, show that exact progress and a percentage derived from those counts. If it does not know the denominator, show indeterminate activity instead of inventing a percentage.

For full-video STT windowing, the same rule applies: show `completed windows / total windows` only when both are known.

### 4.2 Processing details

`تفاصيل المعالجة` must open a real details/diagnostics surface containing, where available:

- current stage;
- session id;
- source state;
- STT phase;
- translation phase;
- completed/total window or translation-unit counts;
- provider submission/unknown-outcome state;
- last non-secret error or status message;
- app version/build type.

No API key, signing secret, authorization header, raw credential, or sensitive provider payload may appear.

### 4.3 Leaving Processing

Back from Processing returns to Home and remains there. If work is active, Home represents it as an in-progress project and offers `Continue`.

Returning to the active project must restore the appropriate stage from durable state rather than restart a provider request.

## 5. Editor and presentation flow

The product flow after successful translation is:

`Editor Text -> Timing -> Style -> Fullscreen Preview -> Export Center -> Export Progress -> Result`.

The existing latest-branch implementations should be preserved and completed rather than replaced.

### 5.1 Text editor

The editor must show source text and editable Arabic translation for the active cue, with previous/next cue navigation, pending-draft/commit semantics, undo/redo, restore-machine-translation, and truthful enabling of timing/style/preview/export actions.

### 5.2 Timing

Timing edits must use the durable display-timing store, validate against source/video/neighbor bounds, and never silently create overlapping or out-of-range cues.

### 5.3 Style

Style edits stay draft-local until Apply. Applied project/cue style becomes the single committed presentation truth used by preview and MP4 export.

### 5.4 Preview

Fullscreen preview must consume the same committed presentation bundle/raster truth that export consumes. Preview cannot be a disconnected mock representation.

### 5.5 Export

Export Center must support the modes the current product architecture truthfully supports, including SRT and burned MP4 where eligible.

The filename-edit control must be implemented rather than wired to an empty callback. Filename normalization must remain safe for Android MediaStore/output creation.

Result actions (open/share/locate/retry where applicable) must be wired and must reflect actual output settlement state.

## 6. Visual parity target

The Android UI should follow the prototype's product hierarchy and Arabic visual intent:

- Arabic-first RTL layout;
- dark Quiet Pro visual language;
- clear stage hierarchy instead of a list that looks frozen;
- processing progress presented as a real stepper/status surface;
- editor with video/presentation context plus translation editing;
- export center that clearly separates subtitle-file and burned-video output;
- result confirmation with obvious Home/Open/Share actions.

Pixel-for-pixel HTML reproduction is not required. Functional hierarchy, Arabic readability, spacing intent, and interaction affordances are required.

## 7. Recovery behavior

Unknown provider outcomes, missing source, and corrupt/unreadable local session state remain explicit recovery states.

Recovery must never silently resubmit a provider job whose outcome is unknown.

A fresh provider attempt requires explicit user intent and a fresh durable attempt boundary.

If accepted text survives but media is unavailable, text-only recovery and SRT export should remain available where timing truth permits it.

## 8. Release/signing requirements

The next APK must preserve the same release signing identity used for the already installed signed build so it can update in place.

The encrypted release-signing backup already recovered from the prior successful workflow must be treated as sensitive material. No private key, password, base64 keystore, or decryption secret may be committed to Git.

Release build must fail closed when signing inputs are absent.

## 9. Verification gates before the word “Final”

A build is not Final merely because it compiles, passes unit tests, or is signed.

Before labeling an APK Final, all of the following must be true:

1. Unit tests pass.
2. Lint passes under the project CI contract.
3. Release APK is produced and `apksigner verify --verbose --print-certs` succeeds.
4. The certificate identity matches the prior signed APK/update identity.
5. Navigation regression: Processing -> Back -> Home remains Home; Continue returns to the active durable session without duplicate provider submission.
6. Processing Details opens and shows non-secret live/durable diagnostics.
7. Translation progress cannot appear permanently frozen without either activity/progress evidence, an explicit failure, or recovery state.
8. Editor text/timing/style navigation is reachable after accepted translation.
9. Preview and export consume committed presentation truth.
10. SRT export succeeds on an eligible session.
11. MP4 export is either proven on-device or truthfully blocked from Final acceptance until that proof exists.
12. Result Open/Share/Home behaviors work for successful outputs.
13. At least one physical-device end-to-end run covers source selection -> provider processing -> editor -> export/result, with captured evidence sufficient to identify the build and outcome.

If any required device/provider gate remains unproven, the artifact may be called a release candidate, not Final.

## 10. Implementation boundaries

Preserve current durable stores and provider safety semantics. Prefer small focused additions/projections over another monolithic expansion of `ProductAppHostStyleBridge.kt`.

High-value correction units are:

1. navigation intent vs durable route synchronization;
2. processing progress/detail projection;
3. Home active-project resume state;
4. removal/wiring of reachable empty callbacks;
5. export filename/result action completion;
6. visual alignment of Processing and key product surfaces to the existing prototype;
7. release/update signing verification and acceptance evidence.

Each unit must be developed test-first where behavior can be expressed as pure projection/navigation logic. UI wiring is then built on those tested contracts.

## 11. Explicit non-goals

- Rewriting the application from scratch.
- Replacing the existing durable-session architecture.
- Claiming cancellation of already-submitted remote provider work.
- Inventing progress percentages with no real denominator.
- Shipping a newly generated release key if the existing release identity can be recovered.
- Calling a signed build Final without physical-device/provider acceptance evidence.
