# AI Video Translator

Personal Android prototype. P0-A/P0-B and NVIDIA P0-C are device verified; see docs/PROJECT_STATE.md for evidence.

P0-D adds first-minute English-to-Arabic translation through NVIDIA, timed bilingual unit preview, and UTF-8 sample_ar.srt save/share. Enter your NVIDIA key on-device; no key is embedded or persisted. Translation and SRT require successful timestamped STT. Live P0-D device/provider verification is pending.

Build baseline is unchanged: AGP 9.4.0, Gradle 9.6.0, JDK 17, compile/target SDK 36, min SDK 29, Compose BOM 2026.06.01, OkHttp 5.3.2.

CI executes testDebugUnitTest, lintDebug and assembleDebug; verifies APK signature; uploads APK, SHA-256 and test/lint reports. Test data is synthetic, not a claim of translating the real sample.
