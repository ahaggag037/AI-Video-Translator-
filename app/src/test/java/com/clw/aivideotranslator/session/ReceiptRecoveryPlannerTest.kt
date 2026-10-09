package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import com.clw.aivideotranslator.semantic.TranslationValidationState
import com.clw.aivideotranslator.semantic.sha256Utf8
import org.junit.Assert.*
import org.junit.Test

class ReceiptRecoveryPlannerTest {
    private val manifest = SessionManifest(
        sessionId = "session-1",
        revision = 4,
        epoch = 2,
        activeEntryRefs = mapOf("u1" to "entry-1"),
    )

    private fun requestPlan(sourceText: String): TranslationRequestPlan = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u1",
            orderedWordIds = listOf("w1"),
            sourceText = sourceText,
            sourceTextHash = sha256Utf8(sourceText),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "test",
        ),
    )

    private fun candidate(text: String) = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = text,
    )

    private fun receipt(
        phase: RequestReceiptPhase,
        plan: TranslationRequestPlan,
        outcome: TranslationProviderOutcome? = null,
        epoch: Long = 2,
        expectedRevision: Long = 4,
        expectedEntryRevision: String? = "entry-1",
    ) = RequestReceipt(
        attemptId = "attempt-1",
        sessionId = "session-1",
        unitId = "u1",
        epoch = epoch,
        requestSignature = plan.requestSignature,
        expectedManifestRevision = expectedRevision,
        expectedActiveEntryRevisionId = expectedEntryRevision,
        phase = phase,
        outcome = outcome,
    )

    @Test fun currentPreparedCanResumeButCurrentSentRequiresExplicitRetryDecision() {
        val request = requestPlan("Hello")
        assertEquals(
            ReceiptRecoveryAction.PLAN_NEW_ATTEMPT,
            ReceiptRecoveryPlanner.plan(receipt(RequestReceiptPhase.PREPARED, request), manifest, request).action,
        )
        val sent = ReceiptRecoveryPlanner.plan(receipt(RequestReceiptPhase.SENT, request), manifest, request)
        assertEquals(ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY, sent.action)
        assertEquals(AdoptionFenceResult.CURRENT, sent.fenceResult)
    }

    @Test fun receivedCandidateCanBeRevalidatedLocally() {
        val request = requestPlan("Hello world.")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("مرحبًا بالعالم.")),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, plan.action)
        assertNotNull(plan.validation)
    }

    @Test fun passWithWarningIsStillAdoptableUnderCurrentLegacyContract() {
        val source = "This deliberately long source sentence exists only to trigger the low-risk length ratio warning."
        val request = requestPlan(source)
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("ترجمة عربية قصيرة")),
            manifest,
            request,
        )
        assertEquals(TranslationValidationState.PASS_WITH_WARNING, plan.validation?.state)
        assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, plan.action)
    }

    @Test fun reviewRequiredIsNeverAutomaticallyAdopted() {
        val request = requestPlan("The value is 12.5%.")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("القيمة 125%.")),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.REVIEW_CANDIDATE, plan.action)
    }

    @Test fun staleEpochBlocksAdoption() {
        val request = requestPlan("Hello")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("مرحبًا"), epoch = 1),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.STALE_EPOCH, plan.fenceResult)
    }

    @Test fun staleManifestRevisionBlocksAdoption() {
        val request = requestPlan("Hello")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("مرحبًا"), expectedRevision = 3),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.STALE_MANIFEST_REVISION, plan.fenceResult)
    }

    @Test fun staleEntryRevisionBlocksAdoption() {
        val request = requestPlan("Hello")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, candidate("مرحبًا"), expectedEntryRevision = "entry-old"),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.STALE_ENTRY_REVISION, plan.fenceResult)
    }

    @Test fun requestSignatureMismatchBlocksAdoption() {
        val original = requestPlan("The value is 12.5%.")
        val tampered = original.copy(exactSourceText = "The value is 125%.")
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, original, candidate("القيمة 125%.")),
            manifest,
            tampered,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.SIGNATURE_MISMATCH, plan.fenceResult)
    }

    @Test fun acceptanceSignatureMismatchBlocksLegacyAdoption() {
        val original = requestPlan("Hello world.")
        val tampered = original.copy(acceptanceSignature = "0".repeat(64))
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, original, candidate("مرحبًا بالعالم.")),
            manifest,
            tampered,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.SIGNATURE_MISMATCH, plan.fenceResult)
    }

    @Test fun pendingReceiptIsHeldWithoutInventingRetry() {
        val request = requestPlan("Hello")
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.PENDING,
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, outcome),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.HOLD_PENDING, plan.action)
    }
}
