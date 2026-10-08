# X003 font candidate — actual-byte provenance

Status: EXPERIMENTAL CANDIDATE, NOT PRODUCTION FONT ACCEPTANCE (AR-03). No system-font substitute is permitted.

| Field | Observed value |
|---|---|
| Upstream owner/repository | `notofonts/arabic` |
| Release | `NotoSansArabic-v2.012` |
| Source commit | `6c8320740db19efbb3127c3697b0ee5d0fa62319` |
| Official URL | https://github.com/notofonts/arabic/releases/download/NotoSansArabic-v2.012/NotoSansArabic-v2.012.zip |
| Release asset | `NotoSansArabic-v2.012.zip`, GitHub asset ID `134330270`, 20,494,293 bytes |
| Actual archive SHA-256 | `65bceb5106ca17e8e0b4660bacec4d362afd56e0251e71fedf83f76dfe9f4abe` |
| Selected archive member | `NotoSansArabic/full/ttf/NotoSansArabic-Regular.ttf` |
| Actual font SHA-256 | `472abe37ec7a7ce61aa2ca6f01d9c4299f6add431da828f054cd1b41ac0ed5a4` |
| Actual font bytes | 292,600; unmodified static Regular, OS/2 weight 400 |
| App font path | `app/src/main/res/font/tv1_noto_sans_arabic_regular.ttf` |
| License | archive `OFL.txt`, SIL OFL-1.1, copyright 2022 The Noto Project Authors |
| App license path | `app/src/main/assets/licenses/notosansarabic_ofl.txt` |
| Actual license SHA-256 | `07fc70bfeb985cc1a87a8587d0a0c80bab11c86c9dc3fd95b6f0cb332f983e96` |

## Why this exact face

Actual-byte inspection with FontTools found that the release `hinted/ttf` and `unhinted/ttf` Regular faces lack basic Latin letters used in Android/GPT and characters such as `@` and `/`. They would need another pinned font or device fallback for common mixed subtitles. The release's `full/ttf` Regular face includes Arabic plus the tested Latin/digit/punctuation corpus (1,561 cmap entries, 69 compact coverage ranges). `googlefonts/ttf` also covers the sample, but is a different binary/hash; it was not substituted.

The exact complete face was copied without conversion/subsetting/renaming its internal family. `BundledArabicCoverage` was generated from this font's best cmap, excluding glyph ID 0, and binds the asset hash. Coverage is not a proof of correct shaping or human readability. Unsupported emoji/CJK or other absent visible glyphs return REVIEW_REQUIRED instead of borrowing an arbitrary device font. ZWJ/ZWNJ and layout controls remain Unicode-policy responsibilities.

`LoadedSubtitleFont` can only be obtained through a resource loader that hashes the packaged bytes, checks the pin, loads that same resource, and checks style. The shadow layout engine accepts this loaded object rather than an unrelated Typeface and asserted hash. No MainActivity/legacy preview/burn activation occurs.

## Native controls and remaining experiment

`SubtitleLayoutInstrumentedTest` verifies packaged font/style/license, native Arabic diacritic/protected-URL boundaries, emergency-token-wrap rejection, missing-glyph review and 21 synthetic text/geometry outcomes. A dedicated workflow runs these controls on API29 and API35 and exports JSON metrics. FIT/OVERFLOW/REVIEW_REQUIRED counts are observations, not an invented success threshold.

This is a PARTIAL X003 harness. It does not measure N26 human actual-size readability, all bright/dark/busy backgrounds, full Arabic/BiDi corpus, arbitrary unsupported Unicode, independent frame raster acceptance, or X004 preview/export parity. Family/weight acceptance remains pending. The production renderer remains unchanged.

Rollback: revert the shadow loader/engine/test batch together; keep exact provenance/license with any retained font bytes. Do not reactivate arbitrary font fallback to make a failed experiment appear successful.
