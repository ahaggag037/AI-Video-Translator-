package com.clw.aivideotranslator.session

import android.content.ContentResolver
import android.content.Context
import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.NvidiaSttTransportObservation
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

    fun snapshotAndBindInitialSource(
        store: TranslationSessionStore,
        sessionId: String,
        preparation: DetailedSttAudioPreparation,
        observation: NvidiaSttTransportObservation,
    ): Result<SessionManifest> = snapshotAndBindInitialSource(store, sessionId) { attachment ->
        NvidiaSourceSnapshotFactory.buildUnverified(attachment, preparation, observation)
    }

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
}

