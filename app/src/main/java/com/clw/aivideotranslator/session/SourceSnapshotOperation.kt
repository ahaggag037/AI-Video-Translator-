package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.SttAudioProfile

enum class SourceSnapshotDelivery {
    LIVE_PROVIDER,
    LOCAL_RECOVERY,
    ALREADY_BOUND,
}

/**
 * One STT operation result. A legacy timed result is exposed only when THIS invocation actually
 * received the provider response; recovery never fabricates or re-requests timing that was not
 * durably accepted under X001. The raw provider body is never retained here.
 */
internal data class SourceSnapshotOperationResult(
    val manifest: SessionManifest,
    val delivery: SourceSnapshotDelivery,
    val legacyResult: NvidiaSttResult? = null,
) {
    init {
        require((delivery == SourceSnapshotDelivery.LIVE_PROVIDER) == (legacyResult != null)) {
            "only a live provider response may expose the legacy parsed result"
        }
    }
}

/**
 * Blocking, opt-in B012 operation. The STT attempt lifecycle is durably journaled before transport
 * can run, so a process death never turns an already-possible remote submission into a blind repost.
 * RECEIVED snapshots can be adopted after restart without another provider call. Clock activation
 * remains X001-gated.
 */
internal object SourceSnapshotOperation {
    fun transcribeAndBind(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<SessionManifest> = transcribeAndBindDetailed(context, store, sessionId, apiKey)
        .map(SourceSnapshotOperationResult::manifest)

    fun transcribeAndBindDetailed(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<SourceSnapshotOperationResult> = runDetailed(
        context = context,
        store = store,
        sessionId = sessionId,
        transcribe = { profile ->
            NvidiaSttClient.transcribeEnglishSampleDetailed(apiKey, profile.file).getOrThrow()
        },
    )

    internal fun run(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        transcribe: (SttAudioProfile) -> NvidiaSttTransportObservation,
        capture: (SourceAttachment) -> CapturedSource = { expected ->
            SourceAttachmentBuilder.capture(context, sessionId, expected.contentUri, expected.selectedRange).getOrThrow()
        },
    ): Result<SessionManifest> = runDetailed(context, store, sessionId, transcribe, capture)
        .map(SourceSnapshotOperationResult::manifest)

    internal fun runDetailed(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        transcribe: (SttAudioProfile) -> NvidiaSttTransportObservation,
        capture: (SourceAttachment) -> CapturedSource = { expected ->
            SourceAttachmentBuilder.capture(context, sessionId, expected.contentUri, expected.selectedRange).getOrThrow()
        },
    ): Result<SourceSnapshotOperationResult> = runCatching {
        val before = store.readManifest(sessionId)
        val expected = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "initial snapshot operation requires a bound source attachment"
        }
        val requestProfileId = NvidiaSttWireContract.PROFILE.profileId
        val attemptId = SttAttemptIdentity.forInitialSnapshot(
            sessionId = sessionId,
            sourceAttachmentId = expected.attachmentId,
        )
        val existing = store.readSttAttemptOrNull(sessionId, attemptId)

        // Recovery after the manifest write but before the journal could advance RECEIVED -> ADOPTED.
        if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
            val activeSnapshot = requireNotNull(store.readActiveSourceSnapshot(sessionId)) {
                "snapshot-bound manifest is missing its active snapshot"
            }
            if (existing == null) {
                // Pre-journal sessions that already completed remain valid and need no provider work.
                return@runCatching SourceSnapshotOperationResult(
                    manifest = before,
                    delivery = SourceSnapshotDelivery.ALREADY_BOUND,
                )
            }
            requireAttemptMatchesBoundSnapshot(existing, before, expected, activeSnapshot)
            when (existing.phase) {
                SttAttemptPhase.RECEIVED -> {
                    store.markSttAttemptAdopted(existing.copy(phase = SttAttemptPhase.ADOPTED))
                    return@runCatching SourceSnapshotOperationResult(
                        manifest = before,
                        delivery = SourceSnapshotDelivery.LOCAL_RECOVERY,
                    )
                }
                SttAttemptPhase.ADOPTED -> return@runCatching SourceSnapshotOperationResult(
                    manifest = before,
                    delivery = SourceSnapshotDelivery.ALREADY_BOUND,
                )
                SttAttemptPhase.PREPARED,
                SttAttemptPhase.SENT,
                -> error("snapshot-bound manifest has an incomplete STT attempt journal")
            }
        }

        val token = SourceBindingToken.from(before)
        check(before.sourceBindingState == SourceBindingState.ATTACHMENT_BOUND && before.activeEntryRefs.isEmpty()) {
            "initial snapshot operation requires attachment-bound empty session"
        }
        // The shared legacy decoder owns only the first window; don't invent arbitrary-range support.
        require(expected.selectedRange.start.value == 0L && expected.selectedRange.end.value == expected.durationUs) {
            "selected-range preparation is not supported by this first-window operation"
        }

        if (existing != null) {
            // The operation identity deliberately survives request-profile changes. A SENT attempt
            // from an older app/profile is still an unknown remote outcome and must block repost.
            // A RECEIVED snapshot is already accepted durable evidence and is reused as-is.
            requireAttemptMatchesCurrentSourceFence(existing, token, expected)
            when (existing.phase) {
                SttAttemptPhase.SENT -> throw UnknownSttRemoteOutcomeException(existing.attemptId)
                SttAttemptPhase.RECEIVED -> {
                    val snapshot = requireNotNull(existing.snapshot)
                    val manifest = store.bindInitialSourceSnapshotIfCurrent(token, snapshot)
                    store.markSttAttemptAdopted(existing.copy(phase = SttAttemptPhase.ADOPTED))
                    return@runCatching SourceSnapshotOperationResult(
                        manifest = manifest,
                        delivery = SourceSnapshotDelivery.LOCAL_RECOVERY,
                    )
                }
                SttAttemptPhase.ADOPTED -> error("ADOPTED STT attempt without snapshot-bound manifest")
                SttAttemptPhase.PREPARED -> Unit // No submission happened; safe to rebuild local evidence/profile.
            }
        }

        capture(expected).use { owned ->
            owned.requireMatches(expected)
            store.requireSourceTokenCurrent(token)
            val preparation = owned.prepareFirstMinute()
            store.requireSourceTokenCurrent(token)

            val sampleSha256 = SttAttemptSampleDigest.sha256(preparation.profile.file)
            val prepared = SttAttemptReceipt(
                attemptId = attemptId,
                sessionId = sessionId,
                epoch = token.epoch,
                expectedManifestRevision = token.revision,
                sourceAttachmentId = expected.attachmentId,
                requestProfileId = requestProfileId,
                sampleSha256 = sampleSha256,
                phase = SttAttemptPhase.PREPARED,
            )
            store.persistPreparedSttAttempt(prepared)
            store.requireSourceTokenCurrent(token)

            // SENT is durable before caller transport code can run. A crash/throw from this point
            // leaves UNKNOWN_REMOTE_OUTCOME and forbids automatic repost on the next invocation.
            val sent = store.markSttAttemptSent(prepared.copy(phase = SttAttemptPhase.SENT))
            val observation = transcribe(preparation.profile)
            require(observation.sampleSha256 == sent.sampleSha256) {
                "transported STT sample does not match durable SENT sample identity"
            }
            val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(expected, preparation, observation)
            val received = store.persistReceivedSttAttempt(
                sent.copy(phase = SttAttemptPhase.RECEIVED, snapshot = snapshot)
            )

            // Adoption remains fenced by the original source token. RECEIVED is deliberately kept
            // even if that fence is now stale, so the result is evidence rather than a lost response.
            val manifest = store.bindInitialSourceSnapshotIfCurrent(token, snapshot)
            store.markSttAttemptAdopted(received.copy(phase = SttAttemptPhase.ADOPTED))
            SourceSnapshotOperationResult(
                manifest = manifest,
                delivery = SourceSnapshotDelivery.LIVE_PROVIDER,
                legacyResult = observation.result,
            )
        }
    }

    private fun requireAttemptMatchesCurrentSourceFence(
        attempt: SttAttemptReceipt,
        token: SourceBindingToken,
        expected: SourceAttachment,
    ) {
        check(attempt.sessionId == token.sessionId &&
            attempt.epoch == token.epoch &&
            attempt.expectedManifestRevision == token.revision &&
            attempt.sourceAttachmentId == expected.attachmentId) {
            "durable STT attempt belongs to stale source/session evidence"
        }
    }

    private fun requireAttemptMatchesBoundSnapshot(
        attempt: SttAttemptReceipt,
        manifest: SessionManifest,
        expected: SourceAttachment,
        activeSnapshot: SourceSnapshot,
    ) {
        check(attempt.sessionId == manifest.sessionId &&
            attempt.sourceAttachmentId == expected.attachmentId) {
            "snapshot-bound STT attempt identity mismatch"
        }
        val snapshot = requireNotNull(attempt.snapshot) { "completed STT attempt is missing snapshot" }
        check(snapshot.snapshotId == activeSnapshot.snapshotId &&
            manifest.activeSourceSnapshotRef == activeSnapshot.snapshotId) {
            "STT attempt does not match the active source snapshot"
        }
    }
}
