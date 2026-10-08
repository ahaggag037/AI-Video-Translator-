package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
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

    private fun receipt(
        phase: RequestReceiptPhase,
        outcome: TranslationProviderOutcome? = null,
    ) = RequestReceipt(
        attemptId = "attempt-1",
        sessionId = "session-1",
        unitId = "u1",
        epoch = 2,
        requestSignature = "sig-1",
        expectedManifestRevision = 4,
        expectedActiveEntryRevisionId = "entry-1",
        phase = phase,
        outcome = outcome,
    )

    @Test fun sentReceiptAlwaysRequiresExplicitRetryDecision() {
        val plan = ReceiptRecoveryPlanner.plan(receipt(RequestReceiptPhase.SENT), manifest, "Hello")
        assertEquals(ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY, plan.action)
    }

    @Test fun matchingReceivedCandidateCanBeRevalidatedWithoutNetwork() {
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "مرحبًا بالعالم.",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, outcome),
            manifest,
            "Hello world.",
        )
        assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, plan.action)
        assertNotNull(plan.validation)
    }

    @Test fun changedManifestMakesReceivedReceiptStaleInsteadOfCurrent() {
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "مرحبًا",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, outcome),
            manifest.copy(revision = 5),
            "Hello",
        )
        assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, plan.action)
        assertEquals(AdoptionFenceResult.STALE_MANIFEST_REVISION, plan.fenceResult)
    }

    @Test fun integrityMismatchRequiresReviewNotAutomaticAdoption() {
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "القيمة 125%.",
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, outcome),
            manifest,
            "The value is 12.5%.",
        )
        assertEquals(ReceiptRecoveryAction.REVIEW_CANDIDATE, plan.action)
    }

    @Test fun pendingReceiptIsHeldWithoutNewPost() {
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.PENDING,
        )
        val plan = ReceiptRecoveryPlanner.plan(
            receipt(RequestReceiptPhase.RECEIVED, outcome),
            manifest,
            "Hello",
        )
        assertEquals(ReceiptRecoveryAction.HOLD_PENDING, plan.action)
    }
}
