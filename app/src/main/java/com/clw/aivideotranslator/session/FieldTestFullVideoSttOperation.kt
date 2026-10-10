package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaSttWireContract
import java.io.File

/**
 * Round-2 field-test execution path for full-video STT.
 *
 * This deliberately does not bind a canonical SourceSnapshot. Each deterministic source window has
 * its own durable PREPARED -> SENT -> RECEIVED receipt, so an app/process restart cannot blindly
 * resubmit a window whose remote outcome is unknown. RECEIVED results are reusable without another
 * provider call and are assembled onto the original presentation timeline only after every window
 * is accounted for.
 */
internal object FieldTestFullVideoSttOperation {
    fun transcribe(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<NvidiaSttResult> = runCatching {
        require(apiKey.trim().isNotEmpty()) { "STT API key is blank" }
        val attachment = requireNotNull(store.readActiveSourceAttachment(sessionId)) {
            "field-test full-video STT requires a bound source attachment"
        }
        val plannedWindows = FieldTestSttWindowPlanner.plan(attachment.durationUs)
        val journal = FieldTestSttWindowJournalStore(
            File(context.filesDir, FIELD_TEST_JOURNAL_DIRECTORY),
        )

        // Preflight every deterministic attempt before touching local media. A durable SENT receipt
        // means the provider may already have accepted that exact window, so automatic repost is
        // forbidden even if app/profile code changed after the crash.
        val preflight = plannedWindows.associateWith { window ->
            val attemptId = FieldTestSttWindowAttemptIdentity.forWindow(
                sessionId = sessionId,
                sourceAttachmentId = attachment.attachmentId,
                window = window,
            )
            journal.readOrNull(sessionId, attemptId)?.also { existing ->
                requireReceiptMatches(existing, attachment, window)
                if (existing.recoveryDisposition() == FieldTestSttWindowRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME) {
                    throw UnknownSttRemoteOutcomeException(existing.attemptId)
                }
            }
        }

        val alreadyReceived = preflight.mapNotNull { (window, receipt) ->
            receipt?.takeIf {
                it.recoveryDisposition() == FieldTestSttWindowRecoveryDisposition.RECEIVED_AVAILABLE
            }?.let { received ->
                FieldTestSttBatchAssembler.WindowResult(
                    window = window,
                    result = requireNotNull(received.result),
                )
            }
        }
        if (alreadyReceived.size == plannedWindows.size) {
            requireCurrentSource(context, attachment, "before recovered full-video STT assembly")
            return@runCatching FieldTestSttBatchAssembler.assemble(alreadyReceived)
        }

        val workDir = File(
            context.cacheDir,
            "$FIELD_TEST_WINDOW_DIRECTORY/${attachment.attachmentId}",
        )
        if (workDir.exists()) {
            require(workDir.deleteRecursively()) { "cannot reset field-test STT window directory" }
        }
        val prepared = FieldTestFullAudioWindowPreparation.prepare(
            context = context,
            attachment = attachment,
            outputDir = workDir,
        ).getOrThrow()
        require(prepared.windows.map { it.window } == plannedWindows) {
            "prepared STT windows do not match deterministic source plan"
        }

        val requestProfileId = NvidiaSttWireContract.PROFILE.profileId
        val results = mutableListOf<FieldTestSttBatchAssembler.WindowResult>()
        for (preparedWindow in prepared.windows) {
            val window = preparedWindow.window
            val attemptId = FieldTestSttWindowAttemptIdentity.forWindow(
                sessionId = sessionId,
                sourceAttachmentId = attachment.attachmentId,
                window = window,
            )
            val existing = journal.readOrNull(sessionId, attemptId)
            if (existing != null) {
                requireReceiptMatches(existing, attachment, window)
                when (existing.recoveryDisposition()) {
                    FieldTestSttWindowRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME ->
                        throw UnknownSttRemoteOutcomeException(existing.attemptId)
                    FieldTestSttWindowRecoveryDisposition.RECEIVED_AVAILABLE -> {
                        results += FieldTestSttBatchAssembler.WindowResult(
                            window = window,
                            result = requireNotNull(existing.result),
                        )
                        continue
                    }
                    FieldTestSttWindowRecoveryDisposition.SAFE_TO_SUBMIT -> Unit
                }
            }

            val sampleSha256 = SttAttemptSampleDigest.sha256(preparedWindow.profile.file)
            val preparedReceipt = FieldTestSttWindowReceipt(
                attemptId = attemptId,
                sessionId = sessionId,
                sourceAttachmentId = attachment.attachmentId,
                windowIndex = window.index,
                startUs = window.startUs,
                endUs = window.endUs,
                requestProfileId = requestProfileId,
                sampleSha256 = sampleSha256,
                phase = FieldTestSttWindowAttemptPhase.PREPARED,
            )
            journal.persistPrepared(preparedReceipt)

            // SENT is durable before transport can run. Any exception/process death after this write
            // intentionally leaves UNKNOWN_REMOTE_OUTCOME for the next invocation.
            val sent = journal.markSent(
                preparedReceipt.copy(phase = FieldTestSttWindowAttemptPhase.SENT),
            )
            val observation = NvidiaSttClient.transcribeEnglishSampleDetailed(
                apiKey = apiKey,
                wavFile = preparedWindow.profile.file,
            ).getOrThrow()
            require(observation.requestProfile.profileId == sent.requestProfileId) {
                "field-test STT request profile changed during transport"
            }
            require(observation.sampleSha256 == sent.sampleSha256) {
                "field-test transported sample differs from durable SENT sample"
            }

            val received = journal.persistReceived(
                sent.copy(
                    phase = FieldTestSttWindowAttemptPhase.RECEIVED,
                    result = observation.result,
                ),
            )
            results += FieldTestSttBatchAssembler.WindowResult(
                window = window,
                result = requireNotNull(received.result),
            )
        }

        requireCurrentSource(context, attachment, "after full-video STT transport")
        require(results.size == plannedWindows.size) { "full-video STT did not account for every source window" }
        FieldTestSttBatchAssembler.assemble(results)
    }

    private fun requireReceiptMatches(
        receipt: FieldTestSttWindowReceipt,
        attachment: SourceAttachment,
        window: FieldTestSttWindow,
    ) {
        require(receipt.sourceAttachmentId == attachment.attachmentId &&
            receipt.windowIndex == window.index &&
            receipt.startUs == window.startUs &&
            receipt.endUs == window.endUs
        ) { "field-test STT receipt belongs to different source/window evidence" }
    }

    private fun requireCurrentSource(
        context: Context,
        attachment: SourceAttachment,
        stage: String,
    ) {
        val inspection = SourceContentProbe.inspect(context.contentResolver, attachment.contentUri)
        FieldTestFullAudioWindowPreparation.requireCurrentSource(inspection, attachment, stage)
    }

    private const val FIELD_TEST_JOURNAL_DIRECTORY = "field-test-stt-window-journal-v1"
    private const val FIELD_TEST_WINDOW_DIRECTORY = "field-test-stt-windows-v1"
}
