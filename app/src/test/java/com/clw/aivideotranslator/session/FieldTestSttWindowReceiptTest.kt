package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FieldTestSttWindowReceiptTest {
    @Test fun receivedReceiptRoundTripsTimedProviderResult() {
        val receipt = received(window = FieldTestSttWindow(2, 120_000_000L, 180_000_000L))

        val decoded = FieldTestSttWindowReceiptCodec.decode(FieldTestSttWindowReceiptCodec.encode(receipt))

        assertEquals(receipt, decoded)
        assertEquals(FieldTestSttWindowRecoveryDisposition.RECEIVED_AVAILABLE, decoded.recoveryDisposition())
    }

    @Test fun operationIdentitySurvivesRequestProfileUpgrade() {
        val window = FieldTestSttWindow(1, 60_000_000L, 120_000_000L)
        val id = FieldTestSttWindowAttemptIdentity.forWindow("session-a", "source-a", window)
        val old = prepared(id, window, "profile-old")
        val upgraded = prepared(id, window, "profile-new")

        assertEquals(old.attemptId, upgraded.attemptId)
        assertNotEquals(old.requestProfileId, upgraded.requestProfileId)
    }

    @Test fun sentReceiptBlocksAutomaticReplay() {
        val window = FieldTestSttWindow(0, 0L, 60_000_000L)
        val prepared = prepared(
            FieldTestSttWindowAttemptIdentity.forWindow("session-a", "source-a", window),
            window,
            "profile-a",
        )
        val sent = prepared.copy(phase = FieldTestSttWindowAttemptPhase.SENT)

        assertEquals(FieldTestSttWindowRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME, sent.recoveryDisposition())
    }

    @Test(expected = IllegalArgumentException::class)
    fun receivedReceiptRejectsWordOutsideWindow() {
        val window = FieldTestSttWindow(0, 0L, 10_000_000L)
        received(window).copy(
            result = NvidiaSttResult(
                transcript = "too late",
                words = listOf(NvidiaWord("late", 9_900L, 10_100L, 0.9)),
                httpStatus = 200,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun receivedReceiptRejectsUntimedWord() {
        val window = FieldTestSttWindow(0, 0L, 60_000_000L)
        received(window).copy(
            result = NvidiaSttResult(
                transcript = "untimed",
                words = listOf(NvidiaWord("untimed", null, null, 0.9)),
                httpStatus = 200,
            ),
        )
    }

    private fun prepared(
        attemptId: String,
        window: FieldTestSttWindow,
        profile: String,
    ) = FieldTestSttWindowReceipt(
        attemptId = attemptId,
        sessionId = "session-a",
        sourceAttachmentId = "source-a",
        windowIndex = window.index,
        startUs = window.startUs,
        endUs = window.endUs,
        requestProfileId = profile,
        sampleSha256 = "a".repeat(64),
        phase = FieldTestSttWindowAttemptPhase.PREPARED,
    )

    private fun received(window: FieldTestSttWindow): FieldTestSttWindowReceipt {
        val attemptId = FieldTestSttWindowAttemptIdentity.forWindow("session-a", "source-a", window)
        return FieldTestSttWindowReceipt(
            attemptId = attemptId,
            sessionId = "session-a",
            sourceAttachmentId = "source-a",
            windowIndex = window.index,
            startUs = window.startUs,
            endUs = window.endUs,
            requestProfileId = "profile-a",
            sampleSha256 = "b".repeat(64),
            phase = FieldTestSttWindowAttemptPhase.RECEIVED,
            result = NvidiaSttResult(
                transcript = "hello",
                words = listOf(NvidiaWord("hello", 250L, 700L, 0.99)),
                httpStatus = 200,
            ),
        )
    }
}
