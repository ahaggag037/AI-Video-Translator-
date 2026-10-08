# X003 — Arabic Font / Unicode / Layout

State: NOT_STARTED (foundation only; no Android/device verdict).

## Implemented foundation
- `TextPolicy` preserves raw text, derives NFC only for ordinary spans, leaves URL/email/code-like spans opaque, preserves ZWJ/ZWNJ and digit style, and escalates explicit BiDi controls for review rather than silently deleting them.
- Protected ranges are remapped into canonical-text offsets after normalization, so later boundary/layout consumers do not use stale raw offsets.
- `AndroidTextBoundaryProvider` delegates grapheme and line boundaries to Android ICU and only accepts line offsets that are also grapheme boundaries and are not inside protected spans.
- Layout result types make `FITS`, `OVERFLOW`, and `REVIEW_REQUIRED` explicit; a `FITS` descriptor cannot be constructed unless ink and box bounds are inside the safe rect.

## Still required before HARNESS_READY/PASS
- Pin and bundle a licensed Arabic-capable font candidate with recorded hash/license for the experiment.
- Implement StaticLayout + ink/raster measurement adapter for API29 and API35/36 paths.
- Add Android instrumentation fixtures for Arabic/Latin/numbers/diacritics/emoji/protected spans and 240p/portrait/square geometry.
- Record actual overflow/readability/containment outcomes. Do not activate production rendering before X003 and X004 gates.
