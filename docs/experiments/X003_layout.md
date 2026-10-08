# X003 — Arabic Font / Unicode / Layout

State: NOT_STARTED (shadow engine implemented; official font provenance resolved, but no bundled-font SHA256/device verdict).

## Implemented foundation
- `TextPolicy` preserves raw text, derives NFC only for ordinary spans, leaves URL/email/code-like spans opaque, preserves ZWJ/ZWNJ and digit style, and escalates explicit BiDi controls for review rather than silently deleting them.
- Protected ranges are remapped into canonical-text offsets after normalization, so boundary/layout consumers do not use stale raw offsets.
- `AndroidTextBoundaryProvider` delegates grapheme and line boundaries to Android ICU and only accepts line offsets that are also grapheme boundaries and are not inside protected spans.
- `SubtitleDefaults` derives font floor/preferred size and safe geometry from output/video pixels rather than UI dp/sp; 240p has an explicit absolute floor.
- `SubtitleLayoutEngine` uses Android `StaticLayout`, ICU-legal candidate breaks, descending font sizes only to the configured floor, no ellipsis, no hidden max-line clipping, and guarded raster alpha measurement for actual ink bounds.
- `FITS` can only be constructed when glyph ink and subtitle box are both inside the safe rect. Incomplete ink proof, excessive candidate work, or reviewed controls return `REVIEW_REQUIRED`; inability to fit at the floor returns `OVERFLOW`.
- `SubtitleFontProfile` requires a pinned SHA-256 and renderer environment provenance. Exact family/weight remains experiment-gated.

## Official Noto Sans Arabic provenance candidate
Canonical D012 names **Noto Sans Arabic** as the V1 default family. The clean-room repository currently contains no `res/font` directory and no vendored Noto font/license, so no device is yet guaranteed to use the same Arabic face.

Upstream facts were resolved directly from the official `notofonts/arabic` GitHub repository:
- release tag: `NotoSansArabic-v2.012`
- annotated tag object: `053348ad718d7c80570ec5630eb8c90a51ac6d49`
- release/source commit: `6c8320740db19efbb3127c3697b0ee5d0fa62319`
- official release asset: `NotoSansArabic-v2.012.zip` (release ID `128285872`, asset ID `134330270`)
- upstream source tree at that commit contains `OFL.txt` (SIL Open Font License 1.1 source license file)

This is provenance only. The release binary could not be materialized through the current text-oriented GitHub connector during this checkpoint, so **no font-file SHA-256 is recorded and no font bytes are bundled**. Do not substitute a HEAD build, device system font, third-party mirror, or guessed hash for the pinned release artifact.

## Required font intake before HARNESS_READY
1. Deterministically materialize the official `NotoSansArabic-v2.012` release artifact from the upstream release.
2. Select the intended static/variable Regular face deliberately; record the exact path/name and whether the app uses a static or variable font.
3. Compute SHA-256 of the exact bundled font bytes and record it in `SubtitleFontProfile`/test provenance.
4. Vendor the applicable OFL-1.1 license/attribution alongside the app asset.
5. Add a build/test assertion that the packaged font bytes match the pinned SHA-256; fail closed on mismatch rather than falling back silently.
6. Prove glyph coverage/fallback behavior on the X003 text matrix; authored output must not silently mix an arbitrary device Arabic font.

## Still required before HARNESS_READY/PASS
- Pin and bundle the licensed font bytes with recorded SHA-256/license as above.
- Add Android instrumentation fixtures for Arabic/Latin/numbers/diacritics/emoji/protected spans and 240p/portrait/square geometry on API29 and API35/36.
- Verify glyph coverage/fallback behavior and renderer provenance.
- Record actual overflow/readability/containment outcomes, including N26 reader recovery.
- Do not activate production rendering before X003 and X004 gates.
