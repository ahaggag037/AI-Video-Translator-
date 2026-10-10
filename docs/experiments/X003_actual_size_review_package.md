# X003 — Actual-size Arabic readability review package

Status: `PENDING_HUMAN_REVIEW`

This package defines stable fixtures and scoring only. It is **not** human evidence and must not be used to mark X003 PASS until an actual evaluator reviews build-bound captures at actual display size on a physical target device.

## Locked presentation assumptions

- Bundled font candidate: Noto Sans Arabic full Regular 2.012.
- Required font SHA-256: `472abe37ec7a7ce61aa2ca6f01d9c4299f6add431da828f054cd1b41ac0ed5a4`.
- Weight/style: 400 / non-italic.
- Maximum authored lines: 2.
- White glyphs over the shared raster's black rounded subtitle box (`alpha=191/255`).
- Layout safe area, font floor, line spacing, padding, and ink containment are the production `SubtitleDefaults` / `SubtitleLayoutEngine` values for the exact reviewed build.
- No system-font substitution is allowed. Unsupported visible glyphs must fail closed to review rather than borrow an unpinned fallback.

## Stable fixtures

| Fixture ID | Exact text | Upright output | Primary falsifier |
|---|---|---:|---|
| `AR-ACTUAL-01-SHORT` | `نعم.` | 426×240 | very short cue placement/readability |
| `AR-ACTUAL-02-DIACRITICS` | `مَرْحَبًا بِكُمْ فِي التَّطْبِيقِ.` | 854×480 | shaping/diacritics/ink clipping |
| `AR-ACTUAL-03-MIXED` | `يعمل Android 17 مع GPT-6 الآن.` | 1280×720 | Arabic/Latin bidi order |
| `AR-ACTUAL-04-NUMERALS` | `السعر 1,250.50 جنيه، والخصم ١٥٪.` | 1280×720 | Latin + Arabic-Indic numerals |
| `AR-ACTUAL-05-PUNCT` | `«هل وصلت الرسالة؟» نعم، الساعة ١٠:٣٠.` | 854×480 | punctuation and bidi edges |
| `AR-ACTUAL-06-MULTILINE` | `السطر العربي الأول\nالسطر العربي الثاني` | 854×480 | explicit two-line rendering |
| `AR-ACTUAL-07-LONG` | `هذه ترجمة عربية طويلة نسبيًا لاختبار الالتفاف إلى سطرين مع الحفاظ على الوضوح وعدم قص الحروف أو علامات الترقيم.` | 1920×1080 | legal wrap/padding/long cue |
| `AR-ACTUAL-08-PROTECTED` | `افتح https://example.com/guide ثم تابع.` | 1280×720 | opaque LTR token inside RTL text |
| `AR-ACTUAL-09-PORTRAIT` | `ترجمة عربية في فيديو رأسي مع Android 17.` | 360×640 | portrait safe area |
| `AR-ACTUAL-10-ROTATED` | `ترجمة بعد تدوير المصدر 90 درجة.` | encoded 240×426, rotation 90°, upright 426×240 | upright geometry/rotation |

The multiline fixture contains one literal LF between the two displayed lines; capture tooling must preserve it exactly.

## Capture requirements

For every fixture, record the exact build SHA, physical device identity/model, Android API level, source dimensions and rotation, renderer environment string, font hash, fixture ID, and capture timestamp/run ID. Produce paired preview and decoded-export captures from the same source/session. Do not resize, crop, stretch, sharpen, or re-typeset the subtitle capture before evaluation.

Review representative dark, bright, and visually busy video backgrounds. At least one 240p/low-resolution case, one portrait case, one 90°-rotation case, mixed Arabic/Latin, mixed numeral styles, punctuation, explicit multiline, and a long valid cue must be reviewed.

## Evaluator fields

For each fixture the evaluator records:

- `readable_at_actual_size`: YES / NO / UNCERTAIN
- `arabic_shaping_correct`: YES / NO / UNCERTAIN
- `bidi_order_correct`: YES / NO / UNCERTAIN
- `clipping_or_safe_area_violation`: YES / NO / UNCERTAIN
- `preview_export_visual_match`: YES / NO / UNCERTAIN
- `unexpected_font_fallback`: YES / NO / UNCERTAIN
- `outcome`: PASS / FAIL / REVIEW_REQUIRED
- free-text notes and artifact identifiers

## Scoring rule

`PASS` requires readable actual-size Arabic with correct shaping/order, no clipping or safe-area escape, no unexpected fallback, and no material preview/export presentation mismatch for the fixture. Any observed defect is `FAIL`. Any uncertainty, unsupported glyph, ambiguous shaping/order, or evidence/capture problem is `REVIEW_REQUIRED`.

Gate X003 remains `PENDING` until the required human/device review exists. Automated layout/raster containment tests may support the decision but do not substitute for this evaluation.
