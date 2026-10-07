# Architecture V2 — concise execution baseline

The durable model is non-linear:

- Project owns source revisions, transcript revisions, translation tracks, derived cue snapshots, and export snapshots.
- SourceUnit is language-independent; TranslationEntry owns machine text and user-edited text.
- SubtitleCue is derived and never owns the canonical translation edit.
- Paid/remote operations eventually use WorkItem input signatures plus attempt fencing and durable receipts before adoption.
- Internal media time uses a presentation timeline in integer microseconds; chunk/provider times must map back explicitly.
- Media3 is primary. FFmpeg is absent until a failing acceptance test proves it is needed.
- MVP output is SRT + preview; hard-burn video is V1.

P0 deliberately does not implement the whole persistence model. It first validates media selection, timeline/audio behavior, provider quality, and Arabic rendering.
