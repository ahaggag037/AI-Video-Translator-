package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.SttAudioProfile

/**
 * Blocking, opt-in B012 operation; not wired to UI. No automatic retry, resume, clock activation or
 * paid request deduplication is claimed. A lost response can still be billed; don't blindly retry.
 * The initial attachment must already be bound. Capture, decode, request and adoption all belong
 * to one start token. Source changes before capture fail; URI changes AFTER capture cannot change
 * the privately owned bytes decoded. Epoch drift always prevents adoption.
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
        val token = SourceBindingToken.from(before)
        check(before.sourceBindingState == SourceBindingState.ATTACHMENT_BOUND && before.activeEntryRefs.isEmpty()) {
            "initial snapshot operation requires attachment-bound empty session"
        }
        val expected = requireNotNull(store.readActiveSourceAttachment(sessionId))
        // The shared legacy decoder owns only the first window; don't invent arbitrary-range support.
        require(expected.selectedRange.start.value == 0L && expected.selectedRange.end.value == expected.durationUs) {
            "selected-range preparation is not supported by this first-window operation"
        }
        capture(expected).use { owned ->
            owned.requireMatches(expected)
            store.requireSourceTokenCurrent(token)
            val preparation = owned.prepareFirstMinute()
            store.requireSourceTokenCurrent(token)
            // A concurrent cancellation may happen after this check; no network check can promise
            // zero remote billing. The same original token is checked atomically at adoption.
            val observation = transcribe(preparation.profile)
            val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(expected, preparation, observation)
            store.bindInitialSourceSnapshotIfCurrent(token, snapshot)
        }
    }
}
