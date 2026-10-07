# P0-D NVIDIA translation response hotfix

Date: 2026-10-07
Branch: `build/p0d-response-hotfix`

## Trigger
Real-device P0-D test reached the translation step but displayed: `استجابة ترجمة غير صالحة أو غير مكتملة؛ لم يتم إنشاء SRT` after the already verified NVIDIA STT flow succeeded.

## Diagnosis
The original translation parser required an unnecessarily exact response envelope: exactly one choice, `finish_reason == "stop"`, `message.role == "assistant"`, no tool calls, and scalar-string `message.content`. The provider contract we actually need for this prototype is a completed textual translation in `choices[].message.content`.

## Change
- Accept omitted/null/empty or `stop` finish reason.
- Reject known incomplete/unsafe reasons such as `length` and `content_filter`.
- Accept scalar string content and text-part content arrays/objects.
- Keep optional `role` validation when present.
- Keep tool-call responses rejected.
- Normalize provider-response validation failures to one `IllegalStateException` family so UI/test behavior is predictable.
- P0-C STT/audio code was not changed.

## Verification
- Code head: `c6f0bd80c5eab8b07bc9f076b581b75f1ff377d3`.
- GitHub Actions run: `37638893322` — SUCCESS.
- Unit tests: PASSED (including provider envelope variants).
- Android lint: PASSED.
- Debug APK assembly: PASSED.
- APK existence check: PASSED.
- APK signature verification: PASSED.
- Artifact upload: PASSED.
- APK artifact ID: `11491441244`.
- APK size: `31,015,041` bytes.
- APK SHA-256: `21cafb657059e2eeaa765e744e44bffabf05715c5c23e3f86c3de8839c4d9055`.
- Local APK ZIP integrity after artifact download: PASSED.

## Verification state
The hotfix is `IMPLEMENTED + CI TESTED + LINT PASSED + APK ARTIFACT VERIFIED`.
It is **NOT yet PROVIDER/DEVICE VERIFIED**. The user's next real-device run must prove that the actual NVIDIA response shape is now accepted and that `sample_ar.srt` is created. If the live response is still rejected, do not broaden the parser blindly; add privacy-safe structural diagnostics or capture only non-content envelope metadata and investigate the exact contract.
