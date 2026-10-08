# X003 — Arabic Font / Unicode / Layout

State: HARNESS_EXECUTED_PARTIAL (pinned experimental font and native controls executed successfully on API29/API35; no human readability/complete device acceptance verdict).

## Implemented foundation
- `TextPolicy` preserves raw text, derives NFC only for ordinary spans, leaves URL/email/code-like spans opaque, preserves ZWJ/ZWNJ and digit style, and escalates explicit BiDi controls for review rather than silently deleting them.
- Protected ranges are remapped into canonical-text offsets after normalization, so boundary/layout consumers do not use stale raw offsets.
- `AndroidTextBoundaryProvider` delegates grapheme and line boundaries to Android ICU and only accepts line offsets that are also grapheme boundaries and are not inside protected spans.
- `SubtitleDefaults` derives font floor/preferred size and safe geometry from output/video pixels rather than UI dp/sp; 240p has an explicit absolute floor.
- `SubtitleLayoutEngine` uses Android `StaticLayout`, ICU-legal candidate breaks, descending font sizes only to the configured floor, no ellipsis, no hidden max-line clipping, and guarded raster alpha measurement for actual ink bounds.
- `FITS` can only be constructed when glyph ink and subtitle box are both inside the safe rect. Incomplete ink proof, excessive candidate work, or reviewed controls return `REVIEW_REQUIRED`; inability to fit at the floor returns `OVERFLOW`.
- `SubtitleFontProfile` requires a pinned SHA-256 and renderer environment provenance. Exact family/weight remains experiment-gated.

## Official font candidate intake and native harness
Actual font bytes are now vendored as an **experimental** Noto Sans Arabic full Regular 2.012 candidate. Exact family/weight is still gated by ACCEPTED AR-03. See [X003_font_provenance.md](X003_font_provenance.md) for release/member/hash/license, why the hinted-only face was rejected for mixed Latin text, packaging, and fallback limits.

The shadow engine requires a `LoadedSubtitleFont` created by the hash-verifying resource loader. It rejects unsupported visible glyphs instead of silently borrowing a device font. Native automatic line breaks are validated against the same legal/grapheme/protected-span boundaries as forced breaks; accepted ranges must cover text exactly once.

A native-control harness covers exact packaged font, diacritic/protected URL boundaries, emergency wrapping, missing glyphs and 21 synthetic geometry outcomes. `.github/workflows/x003-native-layout.yml` runs API29/API35 controls and collects build-bound JSON metrics. Results belong to exact CI runs; harness presence alone is not PASS.

## Exact executed evidence — SHA `1195e9e1d1851b208294c2f6ea3b64bbf54e58b2`
Workflow run `37753803179` (`X003 Native Layout Controls`) completed successfully on both matrix jobs:

- API29 job: SUCCESS. Artifact `x003-native-layout-api29`, artifact ID `11540110191`, digest `sha256:0dd433168be3374fef389da24335d791ebee76f831a99cc651c48e5a5e4e2333`.
- API35 job: SUCCESS. Artifact `x003-native-layout-api35`, artifact ID `11539188153`, digest `sha256:3371e1c104fad08529326da806c8dce0dd720ff22137c5a183c819c84dede13f`.
- Both artifacts bind their metrics to the exact build SHA and pinned font SHA-256 `472abe37ec7a7ce61aa2ca6f01d9c4299f6add431da828f054cd1b41ac0ed5a4`.
- API29 reports ICU `63.2.0.0`; API35 reports ICU `75.1.0.0`.
- All 21 synthetic geometry cases on each API returned `FITS` for the current Arabic/mixed/decimal fixtures across 240p, 480p, 720p, 1080p, portrait, square and letterbox geometries.
- Exact reported font sizes are stable across the two API levels for this fixture set: 16 px (240p), 26 px (480p), 39 px (720p), 59 px (1080p), 19 px (portrait), 26 px (square), 29 px (letterbox).
- The artifacts explicitly record `humanReadability = NOT_MEASURED` and `previewExportParity = NOT_MEASURED`.

This is strong native-layout containment/control evidence for the exact SHA. It is **not** a human readability PASS, production-renderer activation approval, or preview/export parity proof.

## Still required before X003 PASS
- Expand/inspect the complete Arabic/BiDi/grapheme matrix and independent raster containment evidence beyond the current synthetic controls where risk remains material.
- Measure N26 actual-size human readability, bright/dark/busy scenes and intervention/overflow rates on representative physical-device output.
- Accept a family/weight only from the required experiment evidence; current candidate remains experimental.
- Do not activate production rendering before X003 and X004 gates.
