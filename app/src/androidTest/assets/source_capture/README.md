# Synthetic source-ownership fixtures

No speech, personal media, API output or third-party recording. Generated for B012 correctness tests.
FFmpeg is an offline fixture authoring tool here, not an app/build/runtime dependency.

Generation with FFmpeg 6.1.1 (Ubuntu 6.1.1-3ubuntu5):

```sh
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000:duration=1 -c:a aac -b:a 64k -ac 1 -use_editlist 0 -movflags +faststart tone_a.m4a
ffmpeg -f lavfi -i sine=frequency=880:sample_rate=48000:duration=1 -c:a aac -b:a 64k -ac 1 -use_editlist 0 -movflags +faststart tone_b.m4a
```

Append an MP4 `free` box (big-endian uint32 box length, ASCII `free`, zero padding) to each file,
so both files have `max(original sizes) + 8 = 9307` bytes. Both are AAC, 48 kHz, mono, container
duration 1.021333 seconds, first container timestamp zero in ffprobe. Those observations are
fixture provenance, **not X001 presentation/codec-delay proof**. Native PCM/timing equality is
checked against the unchanged legacy decoder path on the same device.

SHA-256:
- tone_a.m4a: `08780889ebde4b79cef3c6fe217f5b7de65386ca502abbaa88a7008f6095f6c7`
- tone_b.m4a: `82f729b0081c547a6dc311ad9a94ada77d93971ff00fdb72fd8be9f28cfd6a30`

The provider callback in tests is fake and counts submissions. Synthetic transcript content
does not claim tone recognition, provider quality, clock verification or live billing recovery.
