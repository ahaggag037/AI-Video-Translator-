# AI Video Translator

Personal Android app for AI-assisted video translation.

## Current state

**P0-A: CI-tested on `build/p0-prototype`** — select a video through `ACTION_OPEN_DOCUMENT`, request persistable read access when supported, and probe basic metadata locally.

No video/audio is uploaded in this build. NVIDIA NIM integration starts after the audio/timeline prototype gate.

## Verified build baseline

- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- JDK: 17
- compileSdk / targetSdk: 36
- minSdk: 29
- Compose BOM: 2026.06.01

CI runs:

```bash
gradle --stacktrace testDebugUnitTest
gradle --stacktrace lintDebug
gradle --stacktrace assembleDebug
```

The debug APK is verified to exist and is uploaded as a GitHub Actions artifact when CI succeeds.

## Verification state

- Source implemented: yes
- Unit tests: passed
- Android lint: passed
- Debug APK assembly: passed
- APK artifact: produced
- Device installation / behavior: not yet verified
