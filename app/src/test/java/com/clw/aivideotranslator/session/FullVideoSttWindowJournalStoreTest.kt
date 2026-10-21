package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullVideoSttWindowJournalStoreTest {
    private fun root(): File = Files.createTempDirectory("full-stt-journal").toFile().also {
        check(File(it, "s1").mkdirs())
    }

    private fun sent() = FullVideoSttWindowReceipt.sent(
        attemptId = "stt-window-abc",
        sessionId = "s1",
        sourceAttachmentId = "source-abc",
        window = SttWindow(0, 0L, 60_000_000L),
        requestProfileId = "profile-1",
        parserVersion = "parser-2",
        sampleSha256 = "a".repeat(64),
        sampleRateHz = 48_000,
        channelCount = 1,
        bitsPerSample = 16,
        sourceStartUs = 21_333L,
        sourceEndUs = 59_999_000L,
    )

    @Test fun sentReceiptSurvivesReopenAndDoesNotContainResult() {
        val root = root()
        try {
            val store = FullVideoSttWindowJournalStore(root) { _ -> Unit }
            store.persistSent(sent())
            val reopened = FullVideoSttWindowJournalStore(root) { _ -> Unit }
                .readOrNull("s1", "stt-window-abc")!!
            assertEquals(FullVideoSttWindowPhase.SENT, reopened.phase)
            assertNull(reopened.result)
        } finally { root.deleteRecursively() }
    }

    @Test(expected = IllegalStateException::class)
    fun sentReceiptCannotBeOverwrittenByAnotherSend() {
        val root = root()
        try {
            val store = FullVideoSttWindowJournalStore(root) { _ -> Unit }
            store.persistSent(sent())
            store.persistSent(sent())
        } finally { root.deleteRecursively() }
    }

    @Test fun receivedReceiptSurvivesWithAcceptedProviderObservation() {
        val root = root()
        try {
            val store = FullVideoSttWindowJournalStore(root) { _ -> Unit }
            val sent = sent()
            store.persistSent(sent)
            val result = NvidiaSttResult(
                transcript = "hello world",
                words = listOf(
                    NvidiaWord("hello", 80L, 400L, 0.9),
                    NvidiaWord("world", 500L, 900L, 0.8),
                ),
                httpStatus = 200,
            )
            store.persistReceived(
                sent = sent,
                rawResponseSha256 = "b".repeat(64),
                result = result,
            )

            val reopened = store.readOrNull("s1", sent.attemptId)!!
            assertEquals(FullVideoSttWindowPhase.RECEIVED, reopened.phase)
            assertEquals("b".repeat(64), reopened.rawResponseSha256)
            assertEquals(result, reopened.result)
        } finally { root.deleteRecursively() }
    }

    @Test(expected = IllegalStateException::class)
    fun receivedCannotBePersistedWithoutDurableSentFence() {
        val root = root()
        try {
            val store = FullVideoSttWindowJournalStore(root) { _ -> Unit }
            val sent = sent()
            store.persistReceived(
                sent,
                rawResponseSha256 = "b".repeat(64),
                result = NvidiaSttResult("x", listOf(NvidiaWord("x", 1L, 2L, null)), 200),
            )
        } finally { root.deleteRecursively() }
    }
}