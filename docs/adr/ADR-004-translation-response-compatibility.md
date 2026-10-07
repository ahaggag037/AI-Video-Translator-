# ADR-004 — NVIDIA translation response compatibility

Date: 2026-10-07
Status: Accepted for P0-D hotfix

## Context
The first real-device P0-D translation request reached NVIDIA but the Android client rejected the HTTP 200 response as invalid. The parser required an exact OpenAI-style envelope: one choice, `finish_reason == "stop"`, `message.role == "assistant"`, no tool calls, and scalar string content.

NVIDIA documents the hosted Riva Translate v2 endpoint as OpenAI-compatible and the useful payload as `choices[].message.content`. `finish_reason`/`role` are envelope metadata and should not be stricter than the provider contract needed by this prototype.

## Decision
Parse the first choice containing a message and extract textual content. Accept omitted/null/empty/`stop` finish reasons. Reject known incomplete/unsafe completion reasons such as `length` and `content_filter`. Accept content either as a scalar string or OpenAI-style text-part arrays/objects. If optional `role` is present it must still be `assistant`.

Do not log or surface raw translated text in parser diagnostics. Error messages may expose only structural metadata such as finish reason/content shape.

## Consequence
The parser remains fail-closed for truncated/malformed responses while avoiding false rejection of provider-compatible response envelopes. P0-C audio/STT code is unchanged.
