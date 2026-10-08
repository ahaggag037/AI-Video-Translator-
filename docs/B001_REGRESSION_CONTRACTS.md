# B001 Regression Contracts

These are the baseline behaviors that later Translation V1 work must preserve unless an experiment-gated migration explicitly replaces them.

## P0-F frozen contracts

1. Video selection remains functional.
2. Audio preparation/WAV generation remains functional for the existing first-segment flow.
3. NVIDIA STT transport/authentication behavior is not silently changed.
4. Existing transcript/timestamp parsing remains available behind the legacy path until the typed clock migration is verified.
5. NVIDIA translation transport remains text-only and retains the existing provider/model endpoint contract until the new protocol is activated after evidence.
6. Arabic subtitle preview remains available.
7. SRT export remains available.
8. Hard-burn MP4 export remains available and preserves the audio track.
9. Existing unit tests remain green.
10. Existing Android CI continues to run unit tests, lint, assemble, APK existence/signature/checksum checks.

## Known defects that are NOT baseline guarantees

The following existing behavior is known-risk and should be fixed behind explicit TV1 work rather than preserved as desirable behavior:

- semantic segmentation can split clauses badly
- STT time-unit inference by numeric heuristic is unsafe
- audio source origin is not propagated correctly in all paths
- subtitle renderer uses fixed sizing/scaling and can clip long Arabic text
- preview/export do not yet share one authoritative layout truth
- translations/manual edits are not durable

## B001 rule

B001 locks what must not regress while allowing the known defects above to be replaced later under their corresponding experiment/task gates.
