package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableTranslationAttemptExecutorTest {
    private fun requestPlan() = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u1",
            orderedWordIds = listOf("w1"),
            sourceText = "Hello world.",
            sourceTextHash = sha256Utf8("Hello world."),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "test",
        ),
    )

    private fun prepared() = requestPlan().let { plan ->
        plan to RequestReceipt(
            attemptId = "attempt-1",
            sessionId = "session-1",
            unitId = plan.unitId,
            epoch = 2,
            requestSignature = plan.requestSignature,
            expectedManifestRevision = 4,
            expectedActiveEntryRevisionId = "entry-1",
            phase = RequestReceiptPhase.PREPARED,
        )
    }

    private fun candidate() = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = "مرحبًا بالعالم.",
    )

    @Test fun sentIsPersistedBeforeTransportCanRun() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor { receipt ->
            persisted += receipt.phase
            receipt
        }

        val received = executor.execute(prepared, plan) {
            submitCount += 1
            assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
            candidate()
        }

        assertEquals(1, submitCount)
        assertEquals(
            listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT, RequestReceiptPhase.RECEIVED),
            persisted,
        )
        assertEquals(RequestReceiptPhase.RECEIVED, received.phase)
    }

    @Test fun sentPersistenceFailurePreventsTransportInvocation() = runBlocking {
        val (plan, prepared) = prepared()
        var submitCalled = false
        val executor = DurableTranslationAttemptExecutor { receipt ->
            if (receipt.phase == RequestReceiptPhase.SENT) error("disk failure")
            receipt
        }

        var failed = false
        try {
            executor.execute(prepared, plan) {
                submitCalled = true
                candidate()
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertFalse(submitCalled)
    }

    @Test fun transportFailureLeavesLastDurablePhaseSent() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        val executor = DurableTranslationAttemptExecutor { receipt ->
            persisted += receipt.phase
            receipt
        }

        var failed = false
        try {
            executor.execute(prepared, plan) {
                error("transport crashed after possible submission")
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
    }
}
