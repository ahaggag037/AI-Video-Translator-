# B012 — Immutable source attachment / resume prerequisite

State: IMPLEMENTED, DEVICE-VERIFIED, NOT ACTIVATED. Exact live-probe checkpoint: `9a5f2d8dd3fcacd677d03ab91c9a95f2b435d3d2`. Android CI run `37766064823` and API35 recovery run `37766064846` are green; recovery artifact `11544906188`, digest `sha256:5d7ca34a1553574c6cac5f00d6e924d0b14d1a7455bec1c20f8d358310a5d986`. This is not a new X005 PASS and does not close Task17.

## Contract

`SourceAttachment` binds a session, exact content URI, historical persisted-read-grant observation, complete byte-stream SHA-256 + byte count, typed-us selected presentation range, source duration and selected container audio-track descriptor. URI/name/size/mtime alone are not identity. Every field participates in a length-prefixed UTF-8 content address. The private JSON object has its own schema 1; manifest schema 2 and entry/receipt schema 1 remain unchanged.

`bindInitialSourceAttachment` accepts only a new UNBOUND session with no active translation entries, under expected manifest revision. It publishes the immutable object first, then atomically advances manifest revision and epoch. A crash/ENOSPC between publication and manifest commit leaves only an inactive orphan, reusable by the same validated retry. New binding invalidates old request fences. It never silently assigns old manual/legacy entries to new media. Reattachment and legacy migration remain deliberately separate operations.

`SourceResumeEvaluator` requires a new source observation bound to session/revision/epoch/attachment ID/locator. The old persisted-grant flag cannot prove current access. Same URI + same size + different digest is SOURCE_CHANGED. Missing, denied, unsupported and I/O failure remain distinct. Late observations are STALE_OBSERVATION. Missing/corrupt attachment fails closed while semantic/manual files remain intact.

`SourceContentProbe` is the blocking Android evidence adapter. It reopens the current `content://` URI through `ContentResolver`, recomputes the complete byte-stream SHA-256 and byte count, reports current persisted-read-grant state separately, and returns typed source-read status. A stored grant never substitutes for a successful byte read. A zero-byte replacement is treated as changed/invalid source rather than a valid fingerprint. Security, missing-file and generic I/O outcomes cannot become AVAILABLE.

AVAILABLE means only that a complete read matched at the observation boundary. It is not a reusable permission token and does not authorize any gated clock/STT/render path. Future source-dependent operations must revalidate the current fence and operate on the same validated stable descriptor or private immutable copy. A provider-backed URI can mutate even after a successful hash, so this does not claim TOCTOU freedom or guarantee cloud-document availability.

## Snapshot boundary now present

The accepted-source snapshot checkpoint is also implemented but still non-activating. `SourceSnapshot` binds the attachment to accepted transcript/word text, STT response/provenance identity, exact prepared-WAV/PCM identity and an explicit observed presentation origin. Unverified clocks are structurally forbidden from carrying interpreted word intervals. Snapshot publication is immutable-before-manifest and advances the source epoch, so pre-snapshot request fences cannot silently continue under new source truth. Exact descendant verification passed Android CI `37764997539` and API35 recovery `37764997633`; recovery artifact `11544187616`, digest `sha256:421184089e9377befcba273fbb274b3662a53cf45d67e1291dcb9ffbfc12e843`.

This snapshot contract deliberately does not bless the legacy magnitude-based STT timing normalization. X001 still owns unit/origin activation.

## Scope and next boundary

- Production `MainActivity`, `TranslationCard`, NVIDIA transport, STT unit inference, SRT and renderers are unchanged.
- Controller/UI resume remains blocked.
- Next ownership bridge is behavioral provenance only: `SttAudioPreparer` must expose the exact input container audio-track descriptor and PCM frame count it actually used, so a future durable builder cannot persist one track while decoding another.
- A separate additive detailed-STT result must later expose exact response hash/parser/profile provenance to the snapshot builder without changing legacy STT behavior or activating X001.
- New controller must own one store instance per root. This is not a multi-process/multi-writer database.
- No locator/digest/transcript belongs in logs, Git or Relay. Tests use synthetic identities only.
- Format guards: locator <=8192 UTF-16 units; source JSON <=65536 UTF-8 bytes; IDs use the existing <=128 bound and exclude `.`/`..`. These are bounded storage/input guards, not multimedia heuristics.
- New source JSON parsing rejects wrong numeric types, unsupported versions, missing/unknown fields, content-address mismatch and out-of-range selection. Reads cap allocation before parsing.

## Falsifiers

JVM: every attachment field changes identity; strict round trip including microseconds >2^53; no numeric-string coercion; valid-JSON content mutation rejected; permission revocation, replaced bytes under same URI/size, stale epoch/revision/session/locator and legacy-unbound classification.

Android attachment/store: initial bind/restart; crash after immutable publish; manifest ENOSPC rollback/retry; prepared-send fencing; stale CAS; missing/tampered/oversized object; manual history and v1 legacy attachment refusal; dot-session path traversal.

Android live probe: a real `ContentResolver` read reproduces the full expected fingerprint without inventing persisted permission; same-URI byte replacement becomes SOURCE_CHANGED; zero-byte replacement fails closed as changed/invalid; deleted source becomes SOURCE_MISSING; unsupported non-content schemes are never opened; token/attachment mismatch is rejected before observation creation.

Android snapshot/store: bind/restart; crash after immutable snapshot publication; manifest ENOSPC rollback/retry; binding fences prepared sends; wrong attachment and stale CAS are rejected; missing/corrupt/oversized active snapshot fails closed. Existing X005 instrumentation reruns as the compatibility check.

## Rollback

All new behavior is additive/unwired. Revert source methods/callers together if needed; preserve private source/snapshot objects and existing translation/manual entries. Older manifest-v2 code reads the manifest shape but must not be treated as source-resume capable. Never relabel a v2 manifest as v1 to make an older build accept it. Gate statuses remain unchanged until their own accepted evidence exists.
