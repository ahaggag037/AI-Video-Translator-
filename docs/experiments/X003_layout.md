# X003 — Arabic Font / Unicode / Layout

State: NOT_STARTED (shadow engine implemented; no pinned-font/device verdict).

## Implemented foundation
- `TextPolicy` preserves raw text, derives NFC only for ordinary spans, leaves URL/email/code-like spans opaque, preserves ZWJ/ZWNJ and digit style, and escalates explicit BiDi controls for review rather than silently deleting them.
- Protected ranges are remapped into canonical-text offsets after normalization, so boundary/layout consumers do not use stale raw offsets.
- `AndroidTextBoundaryProvider` delegates grapheme and line boundaries to Android ICU and only accepts line offsets that are also grapheme boundaries and are not inside protected spans.
- `SubtitleDefaults` derives font floor/preferred size and safe geometry from output/video pixels rather than UI dp/sp; 240p has an explicit absolute floor.
- `SubtitleLayoutEngine` uses Android `StaticLayout`, ICU-legal candidate breaks, descending font sizes only to the configured floor, no ellipsis, no hidden max-line clipping, and guarded raster alpha measurement for actual ink bounds.
- `FITS` can only be constructed when glyph ink and subtitle box are both inside the safe rect. Incomplete ink proof, excessive candidate work, or reviewed controls return `REVIEW_REQUIRED`; inability to fit at the floor returns `OVERFLOW`.
- `SubtitleFontProfile` requires a pinned SHA-256 and renderer environment provenance. Exact family/weight remains experiment-gated.

## Still required before HARNESS_READY/PASS
- Pin and bundle a licensed Arabic-capable font candidate with recorded source/hash/license for the experiment.
- Add Android instrumentation fixtures for Arabic/Latin/numbers/diacritics/emoji/protected spans and 240p/portrait/square geometry on API29 and API35/36.
- Verify glyph coverage/fallback behavior; authored output must not silently depend on an arbitrary device Arabic font.
- Record actual overflow/readability/containment outcomes. Do not activate production rendering before X003 and X004 gates.
