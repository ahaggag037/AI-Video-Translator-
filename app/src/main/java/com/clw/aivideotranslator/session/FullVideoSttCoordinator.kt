package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttParserContract
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.SttAudioProfile

data class FullVideoPreparedWindow(
    val profile: SttAudioProfile,
    val sampleSha256: String,
) {
    init {
        require(sampleSha256.length == 64 && sampleSha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "invalid prepared STT window digest"
        }
        require(profile.sourceStartUs >= 0L && profile.sourceEndUs > profile.sourceStartUs) {
            "invalid prepared STT window media bounds"
        }
    }
}

internal class UnknownFullVideoSttRemoteOutcomeException(
    val attemptId: String,
    val windowIndex: Int,
) : IllegalStateException(
    "full-video STT window may already have been submitted; automatic repost is forbidden: $attemptId"
)

/**
 * Provider-agnostic orchestration over deterministic source windows. Every network-eligible window
 * is fenced by a durable SENT receipt before transport. RECEIVED windows and the final aggregate are
 * reusable after process death; missing-audio windows are local-only and never create provider work.
 */
internal class FullVideoSttCoordinator(
    private val journal: FullVideoSttWindowJournalStore,
    private val aggregates: FullVideoSttAggregateStore,
) {
    fun execute(
        sessionId: String,
        sourceAttachmentId: String,
        sourceDurationUs: Long,
        prepare: (SttWindow) -> FullVideoPreparedWindow?,
        transcribe: (SttAudioProfile) -> NvidiaSttTransportObservation,
        releasePrepared: () -> Unit = {},
    ): FullVideoSttAggregate {
        val existingAggregate = aggregates.readOrNull(sessionId)
        if (existingAggregate != null) {
            check(existingAggregate.sourceAttachmentId == sourceAttachmentId &&
                existingAggregate.sourceDurationUs == sourceDurationUs) {
                "full-video STT aggregate belongs to another source revision"
            }
            return existingAggregate
        }

        val absoluteWindows = mutableListOf<List<AbsoluteNvidiaWord>>()
        val transcriptParts = mutableListOf<String>()
        val receivedAttemptIds = mutableListOf<String>()

        FullVideoSttWindowPlan.forDuration(sourceDurationUs).forEach { window ->
            val attemptId = FullVideoSttWindowPlan.attemptId(sessionId, sourceAttachmentId, window)
            val existing = journal.readOrNull(sessionId, attemptId)
            if (existing != null) {
                requireReceiptIdentity(existing, sourceAttachmentId, window)
                when (existing.phase) {
                    FullVideoSttWindowPhase.SENT -> throw UnknownFullVideoSttRemoteOutcomeException(
                        attemptId = existing.attemptId,
                        windowIndex = window.index,
                    )
                    FullVideoSttWindowPhase.RECEIVED -> {
                        val result = requireNotNull(existing.result)
                        transcriptParts += result.transcript.trim().takeIf(String::isNotEmpty).orEmpty()
                        if (result.words.isNotEmpty()) {
                            absoluteWindows += FullVideoSttTimelineMapper.absoluteWords(
                                profile = profileFrom(existing),
                                result = result,
                            )
                        }
                        receivedAttemptIds += existing.attemptId
                        return@forEach
                    }
                }
            }

            val prepared = try {
                prepare(window)
            } finally {
                // The caller owns cleanup only after a successful preparation is consumed below.
            }
            if (prepared == null) {
                releasePrepared()
                return@forEach
            }
            try {
                require(prepared.profile.sourceStartUs >= window.startUs &&
                    prepared.profile.sourceEndUs <= window.endUs + 100_000L) {
                    "prepared STT window escaped deterministic source bounds"
                }
                val sent = FullVideoSttWindowReceipt.sent(
                    attemptId = attemptId,
                    sessionId = sessionId,
                    sourceAttachmentId = sourceAttachmentId,
                    window = window,
                    requestProfileId = NvidiaSttWireContract.PROFILE.profileId,
                    parserVersion = NvidiaSttParserContract.ID,
                    sampleSha256 = prepared.sampleSha256,
                    sampleRateHz = prepared.profile.sampleRateHz,
                    channelCount = prepared.profile.channelCount,
                    bitsPerSample = prepared.profile.bitsPerSample,
                    sourceStartUs = prepared.profile.sourceStartUs,
                    sourceEndUs = prepared.profile.sourceEndUs,
                )
                journal.persistSent(sent)

                val observation = transcribe(prepared.profile)
                require(observation.requestProfile == NvidiaSttWireContract.PROFILE) {
                    "full-video STT response used unexpected request profile"
                }
                require(observation.parserVersion == NvidiaSttParserContract.ID) {
                    "full-video STT response used unexpected parser semantics"
                }
                require(observation.sampleSha256 == prepared.sampleSha256) {
                    "full-video STT response does not match submitted sample"
                }
                val received = journal.persistReceived(
                    sent = sent,
                    rawResponseSha256 = observation.rawResponseSha256,
                    result = observation.result,
                )
                transcriptParts += observation.result.transcript.trim().takeIf(String::isNotEmpty).orEmpty()
                if (observation.result.words.isNotEmpty()) {
                    absoluteWindows += FullVideoSttTimelineMapper.absoluteWords(
                        profile = prepared.profile,
                        result = observation.result,
                    )
                }
                receivedAttemptIds += received.attemptId
            } finally {
                releasePrepared()
            }
        }

        require(absoluteWindows.isNotEmpty() && receivedAttemptIds.isNotEmpty()) {
            "لم يتم العثور على كلام قابل للتوقيت في الفيديو"
        }
        val mergedAbsolute = FullVideoSttTimelineMapper.mergeWindows(absoluteWindows)
        val mergedWords = FullVideoSttTimelineMapper.toLegacyMilliseconds(mergedAbsolute)
        val transcript = transcriptParts.filter(String::isNotBlank).joinToString(" ").trim()
        require(transcript.isNotBlank()) { "لم ينتج التعرف على الكلام نصًا صالحًا" }
        val aggregate = FullVideoSttAggregate(
            sessionId = sessionId,
            sourceAttachmentId = sourceAttachmentId,
            sourceDurationUs = sourceDurationUs,
            attemptIds = receivedAttemptIds,
            result = NvidiaSttResult(
                transcript = transcript,
                words = mergedWords,
                httpStatus = 200,
            ),
        )
        aggregates.persist(aggregate)
        return aggregate
    }

    private fun requireReceiptIdentity(
        receipt: FullVideoSttWindowReceipt,
        sourceAttachmentId: String,
        window: SttWindow,
    ) {
        check(receipt.sourceAttachmentId == sourceAttachmentId && receipt.window == window) {
            "durable STT window receipt belongs to another source/window"
        }
        check(receipt.requestProfileId == NvidiaSttWireContract.PROFILE.profileId &&
            receipt.parserVersion == NvidiaSttParserContract.ID) {
            "durable STT window receipt uses obsolete provider/parser semantics"
        }
    }

    private fun profileFrom(receipt: FullVideoSttWindowReceipt) = SttAudioProfile(
        file = java.io.File("received-${receipt.attemptId}.wav"),
        sampleRateHz = receipt.sampleRateHz,
        channelCount = receipt.channelCount,
        bitsPerSample = receipt.bitsPerSample,
        sourceStartUs = receipt.sourceStartUs,
        sourceEndUs = receipt.sourceEndUs,
        durationMs = (receipt.sourceEndUs - receipt.sourceStartUs) / 1_000L,
    )
}
