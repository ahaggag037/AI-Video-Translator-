package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FullVideoSttAggregateStoreTest {
    private fun result() = NvidiaSttResult(
        transcript = "Hello world",
        words = listOf(
            NvidiaWord("Hello", 20, 300, 0.9),
            NvidiaWord("world", 61_050, 61_400, 0.8),
        ),
        httpStatus = 200,
    )

    @Test fun aggregateRoundTripsWithExactSourceAndAttemptIdentity() {
        val root = Files.createTempDirectory("full-stt-aggregate").toFile()
        try {
            val store = FullVideoSttAggregateStore(root) { }
            assertNull(store.readOrNull("session-1"))
            val aggregate = FullVideoSttAggregate(
                sessionId = "session-1",
                sourceAttachmentId = "source-${"a".repeat(64)}",
                sourceDurationUs = 120_000_000,
                attemptIds = listOf("attempt-1", "attempt-2"),
                result = result(),
            )
            store.persist(aggregate)
            assertEquals(aggregate, store.readOrNull("session-1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun aggregateRejectsUntimedOrOutOfRangeWords() {
        fun rejected(block: () -> Unit) {
            try { block(); fail("expected rejection") } catch (_: IllegalArgumentException) { }
        }
        rejected {
            FullVideoSttAggregate(
                sessionId = "session-1",
                sourceAttachmentId = "source-${"a".repeat(64)}",
                sourceDurationUs = 1_000_000,
                attemptIds = listOf("attempt-1"),
                result = NvidiaSttResult("x", listOf(NvidiaWord("x", null, 20, null)), 200),
            )
        }
        rejected {
            FullVideoSttAggregate(
                sessionId = "session-1",
                sourceAttachmentId = "source-${"a".repeat(64)}",
                sourceDurationUs = 1_000_000,
                attemptIds = listOf("attempt-1"),
                result = NvidiaSttResult("x", listOf(NvidiaWord("x", 900, 1_001, null)), 200),
            )
        }
    }
}
