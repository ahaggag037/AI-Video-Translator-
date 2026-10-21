package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttResult

internal data class FullVideoSttOperationResult(
    val aggregate: FullVideoSttAggregate,
    val providerSubmissions: Int,
) {
    init { require(providerSubmissions >= 0) }

    val result: NvidiaSttResult get() = aggregate.result
    val recoveredWithoutNetwork: Boolean get() = providerSubmissions == 0
}

/**
 * Android/session boundary for the product full-video STT path. The source is re-captured into one
 * operation-owned private copy only when a missing window actually needs local preparation. Every
 * window remains fenced by [SourceBindingToken] before and after provider transport.
 */
internal class FullVideoSttOperation(
    private val journal: FullVideoSttWindowJournalStore,
    private val aggregates: FullVideoSttAggregateStore,
) {
    fun run(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<FullVideoSttOperationResult> = runCatching {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        val manifest = store.readManifest(sessionId)
        val expected = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "full-video STT requires a bound source attachment"
        }
        check(expected.selectedRange.start.value == 0L && expected.selectedRange.end.value == expected.durationUs) {
            "full-video product STT requires the complete selected video"
        }
        val token = SourceBindingToken.from(manifest)
        check(manifest.sourceBindingState == SourceBindingState.ATTACHMENT_BOUND && manifest.activeEntryRefs.isEmpty()) {
            "full-video STT requires an attachment-bound untranslated session"
        }

        val coordinator = FullVideoSttCoordinator(journal, aggregates)
        var captured: CapturedSource? = null
        var providerSubmissions = 0

        fun capturedSource(): CapturedSource {
            val current = captured
            if (current != null) return current
            store.requireSourceTokenCurrent(token)
            return SourceAttachmentBuilder.capture(
                context = context,
                sessionId = sessionId,
                contentUri = expected.contentUri,
                requestedRange = expected.selectedRange,
            ).getOrThrow().also { owner ->
                owner.requireMatches(expected)
                store.requireSourceTokenCurrent(token)
                captured = owner
            }
        }

        try {
            val aggregate = coordinator.execute(
                sessionId = sessionId,
                sourceAttachmentId = expected.attachmentId,
                sourceDurationUs = expected.durationUs,
                prepare = { window ->
                    store.requireSourceTokenCurrent(token)
                    val preparation = capturedSource().prepareWindowOrNull(window) ?: return@execute null
                    store.requireSourceTokenCurrent(token)
                    FullVideoPreparedWindow(
                        profile = preparation.profile,
                        sampleSha256 = SttAttemptSampleDigest.sha256(preparation.profile.file),
                    )
                },
                transcribe = { profile ->
                    store.requireSourceTokenCurrent(token)
                    providerSubmissions = Math.addExact(providerSubmissions, 1)
                    NvidiaSttClient.transcribeEnglishSampleDetailed(cleanKey, profile.file).getOrThrow().also {
                        store.requireSourceTokenCurrent(token)
                    }
                },
                releasePrepared = { captured?.releasePreparedWindow() },
            )
            store.requireSourceTokenCurrent(token)
            FullVideoSttOperationResult(aggregate, providerSubmissions)
        } finally {
            captured?.close()
        }
    }
}
