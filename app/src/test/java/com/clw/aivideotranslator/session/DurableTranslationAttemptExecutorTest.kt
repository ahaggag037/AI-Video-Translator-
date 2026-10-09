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
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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

    @Test fun newSuccessPersistsPreparedThenSentThenReceivedWithOneSubmission() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt -> persisted += receipt.phase; receipt },
        )

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
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt ->
                if (receipt.phase == RequestReceiptPhase.SENT) error("disk failure")
                receipt
            },
        )

        assertTrue(runCatching {
            executor.execute(prepared, plan) { submitCount++; candidate() }
        }.isFailure)
        assertEquals(0, submitCount)
    }

    @Test fun crashAfterSentBecomesTypedUnknownAndLeavesLastDurablePhaseSent() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt -> persisted += receipt.phase; receipt },
        )

        val failure = runCatching {
            executor.execute(prepared, plan) {
                submitCount++
                error("transport crashed after possible submission")
            }
        }.exceptionOrNull()

        assertTrue(failure is UnknownTranslationRemoteOutcomeException)
        val unknown = failure as UnknownTranslationRemoteOutcomeException
        assertEquals(TransportOutcome.UNKNOWN_AFTER_SUBMISSION, unknown.transport)
        assertEquals("transport crashed after possible submission", unknown.cause?.message)
        assertEquals(1, submitCount)
        assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
    }

    @Test fun thrownCancellationAfterSentBecomesTypedCancelledUnknown() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt -> persisted += receipt.phase; receipt },
        )

        val failure = runCatching {
            executor.execute(prepared, plan) {
                throw CancellationException("cancelled after durable SENT")
            }
        }.exceptionOrNull()

        assertTrue(failure is UnknownTranslationRemoteOutcomeException)
        val unknown = failure as UnknownTranslationRemoteOutcomeException
        assertEquals(TransportOutcome.CANCELLED, unknown.transport)
        assertTrue(unknown.cause is CancellationException)
        assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
    }

    @Test fun unknownAfterSubmissionLeavesDurableSentWithoutReceived() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt -> persisted += receipt.phase; receipt },
        )

        val failure = runCatching {
            executor.execute(prepared, plan) {
                submitCount++
                TranslationProviderOutcome(
                    transport = TransportOutcome.UNKNOWN_AFTER_SUBMISSION,
                    protocol = ProtocolOutcome.NO_RESPONSE,
                    diagnosticCode = "REMOTE_OUTCOME_UNCERTAIN",
                )
            }
        }.exceptionOrNull()

        assertTrue(failure is UnknownTranslationRemoteOutcomeException)
        assertEquals(TransportOutcome.UNKNOWN_AFTER_SUBMISSION, (failure as UnknownTranslationRemoteOutcomeException).transport)
        assertEquals(1, submitCount)
        assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
    }

    @Test fun structuredCancellationAfterSentAlsoLeavesDurableSent() = runBlocking {
        val (plan, prepared) = prepared()
        val persisted = mutableListOf<RequestReceiptPhase>()
        var submitCount = 0
        val executor = DurableTranslationAttemptExecutor.forTesting(
            persistReceipt = { receipt: RequestReceipt -> persisted += receipt.phase; receipt },
        )

        val failure = runCatching {
            executor.execute(prepared, plan) {
                submitCount++
                TranslationProviderOutcome(
                    transport = TransportOutcome.CANCELLED,
                    protocol = ProtocolOutcome.NO_RESPONSE,
                    diagnosticCode = "CANCELLED_AFTER_SENT",
                )
            }
        }.exceptionOrNull()

        assertTrue(failure is UnknownTranslationRemoteOutcomeException)
        assertEquals(TransportOutcome.CANCELLED, (failure as UnknownTranslationRemoteOutcomeException).transport)
        assertEquals(1, submitCount)
        assertEquals(listOf(RequestReceiptPhase.PREPARED, RequestReceiptPhase.SENT), persisted)
    }
}
