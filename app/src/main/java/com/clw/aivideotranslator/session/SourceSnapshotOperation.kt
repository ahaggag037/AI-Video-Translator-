package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.SttAudioProfile

/**
 * Blocking, opt-in B012 operation; not wired to UI. The STT attempt lifecycle is durably journaled
 * before transport can run, so a process death never turns an already-possible remote submission
 * into a blind repost. RECEIVED snapshots can be adopted after restart without another provider
 * call. Clock activation and Task17/UI activation remain separately gated.
 */
internal object SourceSnapshotOperation {
    fun transcribeAndBind(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<SessionManifest> = run(context, store, sessionId, transcribe = { profile ->
        NvidiaSttClient.transcribeEnglishSampleDetailed(apiKey, profile.file).getOrThrow()
    })

    internal fun run(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        transcribe: (SttAudioProfile) -> NvidiaSttTransportObservation,
        capture: (SourceAttachment) -> CapturedSource = { expected ->
            SourceAttachmentBuilder.capture(context, sessionId, expected.contentUri, expected.selectedRange).getOrThrow()
        },
    ): Result<SessionManifest> = runCatching {
        val before = store.readManifest(sessionId)
        val expected = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "initial snapshot operation requires a bound source attachment"
        }
        val requestProfileId = NvidiaSttWireContract.PROFILE.profileId
        val attemptId = SttAttemptIdentity.forInitialSnapshot(
            sessionId = sessionId,
            sourceAttachmentId = expected.attachmentId,
            requestProfileId = requestProfileId,
        )
        val existing = store.readSttAttemptOrNull(sessionId, attemptId)

        // Recovery after the manifest write but before the journal could advance RECEIVED -> ADOPTED.
        if (before.sourceBindingState == SourceBindingState.SNAPSHOT_BOUND) {
            val activeSnapshot = requireNotNull(store.readActiveSourceSnapshot(sessionId)) {
                "snapshot-bound manifest is missing its active snapshot"
            }
            if (existing == null) {
                // Pre-journal sessions that already completed remain valid and need no provider work.
                return@runCatching before
            }
            requireAttemptMatchesBoundSnapshot(existing, before, expected, requestProfileId, activeSnapshot)
            when (existing.phase) {
                SttAttemptPhase.RECEIVED -> {
                    store.markSttAttemptAdopted(existing.copy(phase = SttAttemptPhase.ADOPTED))
                    return@runCatching before
                }
                SttAttemptPhase.ADOPTED -> return@runCatching before
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
            requireAttemptMatchesCurrentToken(existing, token, expected, requestProfileId)
            when (existing.phase) {
                SttAttemptPhase.SENT -> throw UnknownSttRemoteOutcomeException(existing.attemptId)
                SttAttemptPhase.RECEIVED -> {
                    val snapshot = requireNotNull(existing.snapshot)
                    val manifest = store.bindInitialSourceSnapshotIfCurrent(token, snapshot)
                    store.markSttAttemptAdopted(existing.copy(phase = SttAttemptPhase.ADOPTED))
                    return@runCatching manifest
                }
                SttAttemptPhase.ADOPTED -> error("ADOPTED STT attempt without snapshot-bound manifest")
                SttAttemptPhase.PREPARED -> Unit // No submission happened; safe to rebuild local evidence.
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
            manifest
        }
    }

    private fun requireAttemptMatchesCurrentToken(
        attempt: SttAttemptReceipt,
        token: SourceBindingToken,
        expected: SourceAttachment,
        requestProfileId: String,
    ) {
        check(attempt.sessionId == token.sessionId &&
            attempt.epoch == token.epoch &&
            attempt.expectedManifestRevision == token.revision &&
            attempt.sourceAttachmentId == expected.attachmentId &&
            attempt.requestProfileId == requestProfileId) {
            "durable STT attempt belongs to stale source/session evidence"
        }
    }

    private fun requireAttemptMatchesBoundSnapshot(
        attempt: SttAttemptReceipt,
        manifest: SessionManifest,
        expected: SourceAttachment,
        requestProfileId: String,
        activeSnapshot: SourceSnapshot,
    ) {
        check(attempt.sessionId == manifest.sessionId &&
            attempt.sourceAttachmentId == expected.attachmentId &&
            attempt.requestProfileId == requestProfileId) {
            "snapshot-bound STT attempt identity mismatch"
        }
        val snapshot = requireNotNull(attempt.snapshot) { "completed STT attempt is missing snapshot" }
        check(snapshot.snapshotId == activeSnapshot.snapshotId &&
            manifest.activeSourceSnapshotRef == activeSnapshot.snapshotId) {
            "STT attempt does not match the active source snapshot"
        }
    }
}
