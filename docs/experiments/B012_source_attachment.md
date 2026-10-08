# B012 — Immutable source attachment / resume prerequisite

State: IMPLEMENTED, NOT ACTIVATED. Android verification must be recorded against the exact commit in Acceptance/Execution State. This is not a new X005 PASS and does not close Task17.

## Contract

`SourceAttachment` binds a session, exact content URI, historical persisted-read-grant observation, complete byte-stream SHA-256 + byte count, typed-us selected presentation range, source duration and selected container audio-track descriptor. URI/name/size/mtime alone are not identity. Every field participates in a length-prefixed UTF-8 content address. The private JSON object has its own schema 1; manifest schema 2 and entry/receipt schema 1 remain unchanged.

`bindInitialSourceAttachment` accepts only a new UNBOUND session with no active translation entries, under expected manifest revision. It publishes the immutable object first, then atomically advances manifest revision and epoch. A crash/ENOSPC between publication and manifest commit leaves only an inactive orphan, reusable by the same validated retry. New binding invalidates old request fences. It never silently assigns old manual/legacy entries to new media. Reattachment, snapshot adoption and legacy migration are deliberately not implemented by this method.

`SourceResumeEvaluator` requires a new source observation bound to session/revision/epoch/attachment ID/locator. The old persisted-grant flag cannot prove current access. Same URI + same size + different digest is SOURCE_CHANGED. Missing, denied, unsupported and I/O failure remain distinct. Late observations are STALE_OBSERVATION. Missing/corrupt attachment fails closed while semantic/manual files remain intact.

AVAILABLE means only that a complete read matched at the observation boundary. It is not a reusable permission token and does not authorize any gated clock/STT/render path. Future source-dependent operations must revalidate the current fence and operate on the same validated stable descriptor or private immutable copy. A provider-backed URI can mutate even after a successful hash. This slice intentionally supplies no live ContentResolver probe and makes no claim about TOCTOU freedom or cloud-document availability.

## Scope and next boundary

- Production `MainActivity`, `TranslationCard`, NVIDIA transport, STT unit inference, SRT and renderers are unchanged.
- No implicit zero origin or new SourceSnapshot mapping is introduced. That separate object must preserve accepted words/parser/clock provenance without promoting X001.
- New controller must own one store instance per root. This is not a multi-process/multi-writer database.
- No locator/digest/transcript belongs in logs, Git or Relay. Tests use synthetic identities only.
- Format guards: locator <=8192 UTF-16 units; source JSON <=65536 UTF-8 bytes; IDs use the existing <=128 bound and exclude `.`/`..`. These are bounded storage/input guards, not multimedia heuristics.
- New source JSON parsing rejects wrong numeric types, unsupported versions, missing/unknown fields, content-address mismatch and out-of-range selection. Reads cap allocation before parsing.

## Falsifiers

JVM: every field changes identity; strict round trip including microseconds >2^53; no numeric-string coercion; valid-JSON content mutation rejected; permission revocation, replaced bytes under same URI/size, stale epoch/revision/session/locator and legacy-unbound classification.

Android: initial bind/restart; crash after immutable publish; manifest ENOSPC rollback/retry; prepared-send fencing; stale CAS; missing/tampered/oversized object; manual history and v1 legacy attachment refusal; dot-session path traversal. Existing X005 instrumentation reruns as the compatibility check. No actual document-provider experiment is claimed by these synthetic tests.

## Rollback

All new behavior is additive/unwired. Revert source methods/callers together if needed; preserve private source objects and existing translation/manual entries. Older manifest-v2 code reads the manifest shape but must not be treated as source-resume capable. Never relabel a v2 manifest as v1 to make an older build accept it. Gate statuses remain unchanged until their own accepted evidence exists.
