package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationValidationResult
import com.clw.aivideotranslator.semantic.TranslationValidationState
import com.clw.aivideotranslator.semantic.TranslationValidator

enum class ReceiptRecoveryAction {
    PLAN_NEW_ATTEMPT,
    REQUIRE_EXPLICIT_RETRY,
    READY_TO_ADOPT,
    REVIEW_CANDIDATE,
    REJECT_CANDIDATE,
    HOLD_PENDING,
    TERMINAL_OUTCOME,
    STALE_RECEIPT,
}

data class ReceiptRecoveryPlan(
    val action: ReceiptRecoveryAction,
    val fenceResult: AdoptionFenceResult? = null,
    val validation: TranslationValidationResult? = null,
)

object ReceiptRecoveryPlanner {
    fun plan(
        receipt: RequestReceipt,
        manifest: SessionManifest,
        requestPlan: TranslationRequestPlan,
    ): ReceiptRecoveryPlan {
        if (
            requestPlan.unitId != receipt.unitId ||
            requestPlan.requestSignature != receipt.requestSignature ||
            !TranslationPlanner.isRequestPlanSelfConsistent(requestPlan)
        ) {
            return ReceiptRecoveryPlan(
                ReceiptRecoveryAction.STALE_RECEIPT,
                fenceResult = AdoptionFenceResult.SIGNATURE_MISMATCH,
            )
        }
        return planBoundSource(receipt, manifest, requestPlan.exactSourceText)
    }

    private fun planBoundSource(
        receipt: RequestReceipt,
        manifest: SessionManifest,
        sourceText: String,
    ): ReceiptRecoveryPlan {
        return when (receipt.phase) {
            RequestReceiptPhase.PREPARED -> {
                val fence = SessionFencing.check(receipt.adoptionFence(), manifest, receipt.requestSignature)
                if (fence == AdoptionFenceResult.CURRENT) {
                    ReceiptRecoveryPlan(ReceiptRecoveryAction.PLAN_NEW_ATTEMPT, fenceResult = fence)
                } else {
                    ReceiptRecoveryPlan(ReceiptRecoveryAction.STALE_RECEIPT, fenceResult = fence)
                }
            }
            RequestReceiptPhase.SENT -> ReceiptRecoveryPlan(
                ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY,
                fenceResult = SessionFencing.check(receipt.adoptionFence(), manifest, receipt.requestSignature),
            )
            RequestReceiptPhase.RECEIVED -> planReceived(receipt, manifest, sourceText)
        }
    }

    private fun planReceived(
        receipt: RequestReceipt,
        manifest: SessionManifest,
        sourceText: String,
    ): ReceiptRecoveryPlan {
        val fence = SessionFencing.check(receipt.adoptionFence(), manifest, receipt.requestSignature)
        if (fence != AdoptionFenceResult.CURRENT) {
            return ReceiptRecoveryPlan(ReceiptRecoveryAction.STALE_RECEIPT, fenceResult = fence)
        }
        val outcome = requireNotNull(receipt.outcome)
        return when (outcome.protocol) {
            ProtocolOutcome.CANDIDATE -> {
                val candidate = requireNotNull(outcome.candidateText)
                val validation = TranslationValidator.validate(sourceText, candidate)
                val action = when (validation.state) {
                    TranslationValidationState.PASS -> ReceiptRecoveryAction.READY_TO_ADOPT
                    TranslationValidationState.PASS_WITH_WARNING,
                    TranslationValidationState.REVIEW_REQUIRED -> ReceiptRecoveryAction.REVIEW_CANDIDATE
                    TranslationValidationState.NON_RETRYABLE_FAILURE -> ReceiptRecoveryAction.REJECT_CANDIDATE
                }
                ReceiptRecoveryPlan(action, fenceResult = fence, validation = validation)
            }
            ProtocolOutcome.PENDING -> ReceiptRecoveryPlan(ReceiptRecoveryAction.HOLD_PENDING, fenceResult = fence)
            else -> ReceiptRecoveryPlan(ReceiptRecoveryAction.TERMINAL_OUTCOME, fenceResult = fence)
        }
    }
}
