# ADR-003 — Translation owns text only

Date: 2026-10-07
Status: accepted for P0-D; provider/device validation pending

Use NVIDIA-hosted nvidia/riva-translate-4b-instruct-v2 through chat completions. Official model card explicitly supports Arabic and documents an en-target language-code system prompt. See PROJECT_STATE for dated links and endpoint limits.

The inspected endpoint does not document structured model output. Replace PROJECT_STATE's previous batch/echo-ID plan with sequential requests, one SourceUnit sourceText per request; use system en-ar. The client returns only a String. The caller binds it to its existing unit ID. The model never sees or supplies timestamps or IDs. Validate completed text and the complete local ID/count mapping before deriving cues and SRT.

Tradeoff: more requests, weaker cross-unit context, and manual retry restarts the sample. Fail on 429 rather than claiming an unlimited free tier. No hidden model fallback, no other provider. Translation quality remains a live acceptance gate.

Do not modify P0-C transport, WAV conversion or timestamp normalization. Segmentation rejects invalid timelines rather than repairing them. P0 uses the existing STT integer-millisecond sample clock; full-video source/presentation offsets and microsecond persistence remain Architecture V2 work.
