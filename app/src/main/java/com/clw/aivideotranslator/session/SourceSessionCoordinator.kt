package com.clw.aivideotranslator.session

import android.content.ContentResolver
import android.content.Context
import com.clw.aivideotranslator.semantic.PresentationIntervalUs

/**
 * Additive session-level composition for B012, still not wired to production UI. Connects the
 * Android capture boundary and the transport-bound STT observation to the existing manifest CAS
 * machinery without changing any store semantics: immutable object published before manifest
 * reference, revision+epoch advance on success, legacy/manual history protection untouched.
 * Evidence is built OUTSIDE the store lock under a pre-construction token. Adoption validates
 * that token atomically; even revision-only drift fails conservatively without automatic retry.
 * Identical immutable bytes do not authorize adoption across a new source epoch.
 * Module-internal: the durable evidence plumbing intentionally never crosses the module boundary.
 */
internal object SourceSessionCoordinator {
    fun captureAndBindInitialSource(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        contentUri: String,
        requestedRange: PresentationIntervalUs? = null,
    ): Result<SessionManifest> = captureAndBindInitialSource(store, sessionId) {
        SourceAttachmentBuilder
            .build(context, sessionId, contentUri, requestedRange)
            .getOrThrow()
    }

    internal fun captureAndBindInitialSource(
        store: TranslationSessionStore,
        sessionId: String,
        capture: () -> SourceAttachment,
    ): Result<SessionManifest> = runCatching {
        val token = SourceBindingToken.from(store.readManifest(sessionId))
        val attachment = capture()
        store.bindInitialSourceAttachmentIfCurrent(token, attachment)
    }

    /** Adoption-composition test seam only. Real STT operations use SourceSnapshotOperation. */
    internal fun snapshotAndBindInitialSource(
        store: TranslationSessionStore,
        sessionId: String,
        build: (SourceAttachment) -> SourceSnapshot,
    ): Result<SessionManifest> = runCatching {
        val token = SourceBindingToken.from(store.readManifest(sessionId))
        val attachment = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "no bound source attachment for snapshot"
        }
        val snapshot = build(attachment)
        store.bindInitialSourceSnapshotIfCurrent(token, snapshot)
    }

    /**
     * Blocking reopen/resume decision surface. Call from an I/O dispatcher.
     *
     * Fencing discipline: the probe token is minted from the manifest read BEFORE the blocking
     * byte-stream probe, and the observation is evaluated against the manifest RE-READ AFTER the
     * probe — a concurrent binding/advance during I/O makes the token stale and the evaluator
     * answers STALE_OBSERVATION rather than trusting a pre-I/O world. The evaluator's own
     * UNBOUND / LEGACY_UNBOUND / CORRUPT_BINDING / CHECK_REQUIRED branches are preserved; this
     * function adds no new policy, only composition. AVAILABLE remains a point-in-time statement,
     * never an authorization to skip operation-time fencing.
     */
    fun assessSourceResume(
        resolver: ContentResolver,
        store: TranslationSessionStore,
        sessionId: String,
    ): Result<SourceResumeAssessment> = assessSourceResume(store, sessionId) { token, attachment ->
        SourceContentProbe.probe(resolver, token, attachment)
    }

    internal fun assessSourceResume(
        store: TranslationSessionStore,
        sessionId: String,
        probe: (SourceProbeToken, SourceAttachment) -> SourceReadObservation,
    ): Result<SourceResumeAssessment> = runCatching {
        val (manifestBefore, attachment) = store.readSourceResumeInputs(sessionId)
        if (attachment == null) {
            // No immutable attachment object: UNBOUND / LEGACY_UNBOUND / CORRUPT_BINDING is the
            // evaluator's call from the current manifest state alone.
            return@runCatching SourceResumeEvaluator.evaluate(manifestBefore, null, null)
        }
        val token = SourceProbeToken.from(manifestBefore)
        val observation = probe(token, attachment)
        val manifestAfter = store.readManifest(sessionId)
        SourceResumeEvaluator.evaluate(manifestAfter, attachment, observation)
    }

    /**
     * Full local reopen composition required before controller/UI ownership can activate.
     *
     * A SNAPSHOT_BOUND session validates its immutable snapshot locally before any source/provider
     * I/O. Corrupt/missing/oversized/identity-mismatched snapshots therefore fail closed without a
     * probe. A valid snapshot is re-read after the blocking source probe so same-ref file tampering
     * cannot be mixed with a pre-I/O snapshot. No timing gate is changed: an UNVERIFIED snapshot is
     * returned as UNVERIFIED semantic evidence, never promoted to a verified clock.
     */
    fun assessSessionReopen(
        resolver: ContentResolver,
        store: TranslationSessionStore,
        sessionId: String,
    ): Result<SessionReopenAssessment> = assessSessionReopen(store, sessionId) { token, attachment ->
        SourceContentProbe.probe(resolver, token, attachment)
    }

    internal fun assessSessionReopen(
        store: TranslationSessionStore,
        sessionId: String,
        probe: (SourceProbeToken, SourceAttachment) -> SourceReadObservation,
    ): Result<SessionReopenAssessment> = runCatching {
        val beforeInputs = store.readSourceResumeInputs(sessionId)
        val before = beforeInputs.manifest
        val attachmentBefore = beforeInputs.attachment
        val snapshotBefore = if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
            readSnapshotForReopen(store, sessionId)
        } else null

        if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND && snapshotBefore == null) {
            return@runCatching SessionReopenAssessment(
                source = SourceResumeEvaluator.evaluate(before, attachmentBefore, null),
                snapshotAvailability = SourceSnapshotAvailability.CORRUPT_BINDING,
            )
        }

        if (attachmentBefore == null) {
            return@runCatching SessionReopenAssessment(
                source = SourceResumeEvaluator.evaluate(before, null, null),
                snapshotAvailability = if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
                    SourceSnapshotAvailability.CORRUPT_BINDING
                } else SourceSnapshotAvailability.NOT_BOUND,
            )
        }

        if (before.sourceBindingState == SourceBindingState.UNBOUND ||
            before.sourceBindingState == SourceBindingState.LEGACY_UNBOUND) {
            return@runCatching SessionReopenAssessment(
                source = SourceResumeEvaluator.evaluate(before, attachmentBefore, null),
                snapshotAvailability = SourceSnapshotAvailability.NOT_BOUND,
            )
        }

        val token = SourceProbeToken.from(before)
        val observation = probe(token, attachmentBefore)
        val afterInputs = store.readSourceResumeInputs(sessionId)
        val after = afterInputs.manifest

        if (SourceBindingToken.from(after) != SourceBindingToken.from(before)) {
            return@runCatching staleReopen(before)
        }

        val attachmentAfter = afterInputs.attachment
        if (attachmentAfter == null || attachmentAfter != attachmentBefore) {
            return@runCatching SessionReopenAssessment(
                source = SourceResumeEvaluator.evaluate(after, attachmentAfter, observation),
                snapshotAvailability = if (after.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
                    SourceSnapshotAvailability.CORRUPT_BINDING
                } else SourceSnapshotAvailability.NOT_BOUND,
            )
        }

        val sourceAssessment = SourceResumeEvaluator.evaluate(after, attachmentAfter, observation)
        if (after.sourceBindingState != SourceBindingState.SNAPSHOT_BOUND) {
            return@runCatching SessionReopenAssessment(
                source = sourceAssessment,
                snapshotAvailability = SourceSnapshotAvailability.NOT_BOUND,
            )
        }

        val snapshotAfter = readSnapshotForReopen(store, sessionId)
        val finalManifest = store.readManifest(sessionId)
        if (SourceBindingToken.from(finalManifest) != SourceBindingToken.from(before)) {
            return@runCatching staleReopen(before)
        }
        if (snapshotAfter == null || snapshotAfter != snapshotBefore) {
            return@runCatching SessionReopenAssessment(
                source = sourceAssessment,
                snapshotAvailability = SourceSnapshotAvailability.CORRUPT_BINDING,
            )
        }

        SessionReopenAssessment(
            source = sourceAssessment,
            snapshotAvailability = SourceSnapshotAvailability.AVAILABLE,
            snapshot = snapshotAfter,
        )
    }

    private fun staleReopen(before: SessionManifest) = SessionReopenAssessment(
        source = SourceResumeAssessment(SourceAvailability.STALE_OBSERVATION),
        snapshotAvailability = if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
            SourceSnapshotAvailability.STALE_OBSERVATION
        } else SourceSnapshotAvailability.NOT_BOUND,
    )

    private fun readSnapshotForReopen(
        store: TranslationSessionStore,
        sessionId: String,
    ): SourceSnapshot? = try {
        store.readActiveSourceSnapshot(sessionId)
    } catch (error: Exception) {
        when (error) {
            is java.io.IOException,
            is SecurityException,
            is IllegalArgumentException,
            is org.json.JSONException,
            is ArithmeticException,
            -> null
            else -> throw error
        }
    }
}
