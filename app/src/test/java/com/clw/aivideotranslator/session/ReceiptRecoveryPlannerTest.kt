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
import com.clw.aivideotranslator.semantic.sha256Utf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun receipt(
        phase: RequestReceiptPhase,
        plan: TranslationRequestPlan,
        outcome: TranslationProviderOutcome? = null,
    ) = RequestReceipt(
        attemptId = "attempt-1",
        sessionId = "session-1",
        unitId = "u1",
        epoch = 2,
        requestSignature = plan.requestSignature,
        expectedManifestRevision = 4,
        expectedActiveEntryRevisionId = "entry-1",
        phase = phase,
        outcome = outcome,
    )

    @Test fun sentReceiptAlwaysRequiresExplicitRetryDecision() {
        val request = requestPlan("Hello")
        val plan = ReceiptRecoveryPlanner.plan(receipt(RequestReceiptPhase.SENT, request), manifest, request)
        assertEquals(ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY, plan.action)
    }

    @Test fun matchingReceivedCandidateCanBeRevalidatedWithoutNetwork() {
        val request = requestPlan("Hello world.")
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "مرحبًا بالعالم.",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, outcome),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, plan.action)
        assertNotNull(plan.validation)
    }

    @Test fun changedManifestMakesReceivedReceiptStaleInsteadOfCurrent() {
        val request = requestPlan("Hello")
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "مرحبًا",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, outcome),
            manifest.copy(revision = 5),
            request,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.STALE_MANIFEST_REVISION, plan.fenceResult)
    }

    @Test fun integrityMismatchRequiresReviewNotAutomaticAdoption() {
        val request = requestPlan("The value is 12.5%.")
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "القيمة 125%.",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, request, outcome),
            manifest,
            request,
        )
        assertEquals(ReceiptRecoveryAction.REVIEW_CANDIDATE, plan.action)
    }

    @Test fun pendingReceiptIsHeldWithoutNewPost() {
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

    @Test fun tamperedSourceCannotBeValidatedUnderOriginalReceiptSignature() {
        val original = requestPlan("The value is 12.5%.")
        val tampered = original.copy(exactSourceText = "The value is 125%.")
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "القيمة 125%.",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, original, outcome),
            manifest,
            tampered,
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.SIGNATURE_MISMATCH, plan.fenceResult)
    }
}
