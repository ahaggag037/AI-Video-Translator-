package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttParserContract
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SttAudioProfile
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FullVideoSttCoordinatorTest {
    @Test fun mergesMultipleWindowsAndSkipsNoAudioTailWithoutTransport() {
        val root = Files.createTempDirectory("full-stt-coordinator").toFile()
        try {
            val journal = FullVideoSttWindowJournalStore(root) { }
            val aggregateStore = FullVideoSttAggregateStore(root) { }
            val coordinator = FullVideoSttCoordinator(journal, aggregateStore)
            val transported = mutableListOf<Int>()
            val preparedFiles = mutableListOf<File>()

            val aggregate = coordinator.execute(
                sessionId = "session-1",
                sourceAttachmentId = "source-${"a".repeat(64)}",
                sourceDurationUs = 130_000_000,
                prepare = { window ->
                    if (window.index == 2) null else prepared(window).also { preparedFiles += it.profile.file }
                },
                transcribe = { prepared ->
                    val index = if (prepared.sourceStartUs == 0L) 0 else 1
                    transported += index
                    observation(
                        prepared,
                        NvidiaSttResult(
                            transcript = if (index == 0) "Hello" else "world",
                            words = listOf(NvidiaWord(if (index == 0) "Hello" else "world", 20, 400, 0.9)),
                            httpStatus = 200,
                        ),
                    )
                },
            )

            assertEquals(listOf(0, 1), transported)
            assertEquals("Hello world", aggregate.result.transcript)
            assertEquals(listOf(20L, 60_020L), aggregate.result.words.map { it.startMs })
            assertEquals(listOf(400L, 60_400L), aggregate.result.words.map { it.endMs })
            assertEquals(2, aggregate.attemptIds.size)
            assertEquals(aggregate, aggregateStore.readOrNull("session-1"))
            preparedFiles.forEach(File::delete)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun durableAggregateIsReturnedWithoutPreparationOrNetwork() {
        val root = Files.createTempDirectory("full-stt-reuse").toFile()
        try {
            val journal = FullVideoSttWindowJournalStore(root) { }
            val aggregates = FullVideoSttAggregateStore(root) { }
            val coordinator = FullVideoSttCoordinator(journal, aggregates)
            val sourceId = "source-${"b".repeat(64)}"
            val first = coordinator.execute(
                sessionId = "session-1",
                sourceAttachmentId = sourceId,
                sourceDurationUs = 10_000_000,
                prepare = { prepared(it) },
                transcribe = { profile ->
                    observation(profile, NvidiaSttResult("hello", listOf(NvidiaWord("hello", 10, 200, null)), 200))
                },
            )

            var preparedAgain = false
            var transportedAgain = false
            val recovered = coordinator.execute(
                sessionId = "session-1",
                sourceAttachmentId = sourceId,
                sourceDurationUs = 10_000_000,
                prepare = { preparedAgain = true; prepared(it) },
                transcribe = { transportedAgain = true; error("must not call transport") },
            )
            assertEquals(first, recovered)
            assertTrue(!preparedAgain)
            assertTrue(!transportedAgain)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun sentWindowBlocksBlindRepostAfterUnknownRemoteOutcome() {
        val root = Files.createTempDirectory("full-stt-unknown").toFile()
        try {
            val journal = FullVideoSttWindowJournalStore(root) { }
            val aggregates = FullVideoSttAggregateStore(root) { }
            val coordinator = FullVideoSttCoordinator(journal, aggregates)
            val sourceId = "source-${"c".repeat(64)}"
            try {
                coordinator.execute(
                    sessionId = "session-1",
                    sourceAttachmentId = sourceId,
                    sourceDurationUs = 10_000_000,
                    prepare = { prepared(it) },
                    transcribe = { error("simulated transport ambiguity") },
                )
                fail("first attempt must fail")
            } catch (_: IllegalStateException) {
                // durable SENT remains
            }

            var preparedAgain = false
            var transportedAgain = false
            try {
                coordinator.execute(
                    sessionId = "session-1",
                    sourceAttachmentId = sourceId,
                    sourceDurationUs = 10_000_000,
                    prepare = { preparedAgain = true; prepared(it) },
                    transcribe = { transportedAgain = true; error("must not repost") },
                )
                fail("unknown remote outcome must block")
            } catch (error: UnknownFullVideoSttRemoteOutcomeException) {
                assertEquals(0, error.windowIndex)
            }
            assertTrue(!preparedAgain)
            assertTrue(!transportedAgain)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun prepared(window: SttWindow): FullVideoPreparedWindow {
        val file = File.createTempFile("stt-window-${window.index}", ".wav").apply {
            writeBytes(ByteArray(64) { (it + window.index).toByte() })
        }
        val sourceStartUs = window.startUs
        val sourceEndUs = minOf(window.endUs, sourceStartUs + 1_000_000L)
        return FullVideoPreparedWindow(
            profile = SttAudioProfile(
                file = file,
                sampleRateHz = 16_000,
                channelCount = 1,
                bitsPerSample = 16,
                sourceStartUs = sourceStartUs,
                sourceEndUs = sourceEndUs,
                durationMs = (sourceEndUs - sourceStartUs) / 1_000L,
            ),
            sampleSha256 = sha256(file),
        )
    }

    private fun observation(profile: SttAudioProfile, result: NvidiaSttResult) = NvidiaSttTransportObservation(
        requestProfile = NvidiaSttWireContract.PROFILE,
        result = result,
        parserVersion = NvidiaSttParserContract.ID,
        rawResponseSha256 = "d".repeat(64),
        sampleSha256 = sha256(profile.file),
    )

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
