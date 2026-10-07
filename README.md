# AI Video Translator

Personal Android app for AI-assisted video translation.

## Current state

**P0-A: implemented on `build/p0-prototype`** — select a video through `ACTION_OPEN_DOCUMENT`, request persistable read access when supported, and probe basic metadata locally.

No video/audio is uploaded in this build. NVIDIA NIM integration starts after the audio/timeline prototype gate.

## Build

CI uses Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, compile/target SDK 37, and the stable Compose BOM 2026.09.00.

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The debug APK is published as a GitHub Actions artifact when CI succeeds.
