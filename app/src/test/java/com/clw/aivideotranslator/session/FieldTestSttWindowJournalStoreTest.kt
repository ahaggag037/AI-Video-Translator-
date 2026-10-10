package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldTestSttWindowJournalStoreTest {
    @Test fun preparedSentReceivedSurvivesReopen() {
        val root = Files.createTempDirectory("field-stt-journal").toFile()
        try {
            val store = FieldTestSttWindowJournalStore(root)
            val prepared = prepared()
            store.persistPrepared(prepared)
            store.markSent(prepared.copy(phase = FieldTestSttWindowAttemptPhase.SENT))
            val received = prepared.copy(
                phase = FieldTestSttWindowAttemptPhase.RECEIVED,
                result = NvidiaSttResult(
                    transcript = "hello",
                    words = listOf(NvidiaWord("hello", 100L, 400L, 0.99)),
                    httpStatus = 200,
                ),
            )
            store.persistReceived(received)

            val reopened = FieldTestSttWindowJournalStore(root).readOrNull(received.sessionId, received.attemptId)
            assertEquals(received, reopened)
            assertEquals(FieldTestSttWindowRecoveryDisposition.RECEIVED_AVAILABLE, reopened!!.recoveryDisposition())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun preparedMayRefreshLocalSampleBeforeAnySubmission() {
        val root = Files.createTempDirectory("field-stt-refresh").toFile()
        try {
            val store = FieldTestSttWindowJournalStore(root)
            val first = prepared()
            val refreshed = first.copy(requestProfileId = "profile-b", sampleSha256 = "c".repeat(64))

            store.persistPrepared(first)
            store.persistPrepared(refreshed)

            assertEquals(refreshed, store.readOrNull(refreshed.sessionId, refreshed.attemptId))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun sentSampleCannotChangeBeforeReceived() {
        val root = Files.createTempDirectory("field-stt-fence").toFile()
        try {
            val store = FieldTestSttWindowJournalStore(root)
            val first = prepared()
            store.persistPrepared(first)
            store.markSent(first.copy(phase = FieldTestSttWindowAttemptPhase.SENT))
            store.persistReceived(
                first.copy(
                    sampleSha256 = "d".repeat(64),
                    phase = FieldTestSttWindowAttemptPhase.RECEIVED,
                    result = NvidiaSttResult(
                        "changed",
                        listOf(NvidiaWord("changed", 10L, 50L, 1.0)),
                        200,
                    ),
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun clearSessionRemovesOnlyThatFieldTestJournal() {
        val root = Files.createTempDirectory("field-stt-clear").toFile()
        try {
            val store = FieldTestSttWindowJournalStore(root)
            val receipt = prepared()
            store.persistPrepared(receipt)
            store.clearSession(receipt.sessionId)
            assertNull(store.readOrNull(receipt.sessionId, receipt.attemptId))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun prepared(): FieldTestSttWindowReceipt {
        val window = FieldTestSttWindow(0, 0L, 60_000_000L)
        return FieldTestSttWindowReceipt(
            attemptId = FieldTestSttWindowAttemptIdentity.forWindow("session-a", "source-a", window),
            sessionId = "session-a",
            sourceAttachmentId = "source-a",
            windowIndex = window.index,
            startUs = window.startUs,
            endUs = window.endUs,
            requestProfileId = "profile-a",
            sampleSha256 = "b".repeat(64),
            phase = FieldTestSttWindowAttemptPhase.PREPARED,
        )
    }
}
