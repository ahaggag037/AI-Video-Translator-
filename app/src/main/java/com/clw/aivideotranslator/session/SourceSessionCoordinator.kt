package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.semantic.PresentationIntervalUs

/**
 * Pure CAS retry policy for session-store bindings. Retries ONLY while the manifest revision
 * actually advanced between attempts (a genuine concurrent write); invariant violations with an
 * unchanged revision propagate immediately, and IllegalArgumentException (require failures) is
 * never retried. Bounded attempts keep a permanently contended store from spinning forever.
 */
internal object CasRetry {
    const val MAX_ATTEMPTS = 3

    fun <T> run(
        maxAttempts: Int = MAX_ATTEMPTS,
        readRevision: () -> Long,
        action: (expectedRevision: Long) -> T,
    ): T {
        require(maxAttempts > 0) { "nonpositive CAS attempt budget" }
        var attempts = 0
        while (true) {
            val expected = readRevision()
            try {
                return action(expected)
            } catch (stale: IllegalStateException) {
                attempts++
                if (attempts >= maxAttempts || readRevision() == expected) throw stale
            }
        }
    }
}

/**
 * Additive session-level composition for B012, still not wired to production UI. Connects the
 * Android capture boundary and the transport-bound STT observation to the existing manifest CAS
 * machinery without changing any store semantics: immutable object published before manifest
 * reference, revision+epoch advance on success, legacy/manual history protection untouched.
 * Snapshots are built OUTSIDE the store lock; identical evidence yields identical immutable
 * objects, so a CAS retry republishes the same bytes rather than forking identity.
 */
object SourceSessionCoordinator {
    fun captureAndBindInitialSource(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        contentUri: String,
        requestedRange: PresentationIntervalUs? = null,
    ): Result<SessionManifest> = runCatching {
        val attachment = SourceAttachmentBuilder
            .build(context, sessionId, contentUri, requestedRange)
            .getOrThrow()
        CasRetry.run(
            readRevision = { store.readManifest(sessionId).revision },
            action = { expected -> store.bindInitialSourceAttachment(sessionId, expected, attachment) },
        )
    }

    fun snapshotAndBindInitialSource(
        store: TranslationSessionStore,
        sessionId: String,
        preparation: DetailedSttAudioPreparation,
        observation: NvidiaSttTransportObservation,
    ): Result<SessionManifest> = runCatching {
        val attachment = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "no bound source attachment for snapshot"
        }
        val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(attachment, preparation, observation)
        CasRetry.run(
            readRevision = { store.readManifest(sessionId).revision },
            action = { expected -> store.bindInitialSourceSnapshot(sessionId, expected, snapshot) },
        )
    }
}
