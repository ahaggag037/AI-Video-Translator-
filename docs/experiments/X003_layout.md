# X003 — Arabic Font / Unicode / Layout

State: HARNESS_READY_PARTIAL (pinned experimental font and native controls implemented; no human readability/complete device acceptance verdict).

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

The shadow engine now requires a `LoadedSubtitleFont` created by the hash-verifying resource loader. It rejects unsupported visible glyphs instead of silently borrowing a device font. Native automatic line breaks are validated against the same legal/grapheme/protected-span boundaries as forced breaks; accepted ranges must cover text exactly once.

A native-control harness covers exact packaged font, diacritic/protected URL boundaries, emergency wrapping, missing glyphs and 21 synthetic geometry outcomes. `.github/workflows/x003-native-layout.yml` runs API29/API35 controls and collects build-bound JSON metrics. Results belong to exact CI runs; harness presence is not PASS.

## Still required before X003 PASS
- Execute the API29/API35 controls and inspect exact artifacts.
- Expand/inspect the complete Arabic/BiDi/grapheme matrix and independent raster containment evidence.
- Measure N26 actual-size human readability, bright/dark/busy scenes and intervention/overflow rates.
- Accept a family/weight only from the required experiment evidence; current candidate is not that decision.
- Do not activate production rendering before X003 and X004 gates.
