package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.util.UUID

enum class DurableTranslationUnitDisposition {
    REUSED_ENTRY,
    ADOPTED,
    REVIEW_REQUIRED,
    REJECTED,
    PENDING,
    TERMINAL,
    UNKNOWN_REMOTE_OUTCOME,
    STALE_STATE,
    AMBIGUOUS_RECEIPTS,
}

data class DurableTranslationUnitResult(
    val unitId: String,
    val disposition: DurableTranslationUnitDisposition,
    val effectiveText: String? = null,
    val attemptId: String? = null,
    val recoveryAction: ReceiptRecoveryAction? = null,
)

data class DurableTranslationBatchResult(
    val units: List<DurableTranslationUnitResult>,
) {
    val completed: Boolean
        get() = units.all {
            it.disposition == DurableTranslationUnitDisposition.REUSED_ENTRY ||
                it.disposition == DurableTranslationUnitDisposition.ADOPTED
        }
}

fun interface TranslationPlanSubmitter {
    suspend fun submit(plan: TranslationRequestPlan): TranslationProviderOutcome
}

/**
 * Sequential durable execution bridge around the frozen P0-F units. It owns no segmentation policy:
 * callers supply plans produced by [LegacyParityTranslationPlanner]. A plan is published before any
 * new PREPARED receipt so a RECEIVED receipt can be recovered after process death without STT timing.
 * Existing compatible success is reused first. Any review, stale state, unresolved SENT or terminal
 * outcome stops the batch before a later provider submission.
 */
internal class DurableLegacyTranslationOperation(
    private val store: TranslationSessionStore,
    private val planStore: TranslationRequestPlanStore,
) {
    suspend fun execute(
        sessionId: String,
        units: List<LegacyParityTranslationUnit>,
        submitter: TranslationPlanSubmitter,
    ): Result<DurableTranslationBatchResult> = runCatching {
        require(units.isNotEmpty()) { "translation batch is empty" }
        require(units.map { it.requestPlan.unitId }.distinct().size == units.size) {
            "translation batch contains duplicate units"
        }

        val results = mutableListOf<DurableTranslationUnitResult>()
        for (unit in units) {
            val result = executeUnit(sessionId, unit.requestPlan, submitter)
            results += result
            if (result.disposition != DurableTranslationUnitDisposition.REUSED_ENTRY &&
                result.disposition != DurableTranslationUnitDisposition.ADOPTED) {
                break
            }
        }
        DurableTranslationBatchResult(results.toList())
    }

    /**
     * Process-death recovery for a single durable attempt. The exact request is reconstructed only
     * from the receipt identity plus the persisted request-plan file; STT timing or a media locator is
     * neither required nor consulted. SENT therefore reopens without a POST, RECEIVED can adopt
     * locally, and PREPARED may continue under the same durable executor policy.
     */
    suspend fun resumeAttempt(
        sessionId: String,
        attemptId: String,
        submitter: TranslationPlanSubmitter,
    ): Result<DurableTranslationUnitResult> = runCatching {
        val receipt = store.readReceipt(sessionId, attemptId)
        val plan = planStore.read(sessionId, receipt.unitId, receipt.requestSignature)
        executeUnit(sessionId, plan, submitter)
    }

    private suspend fun executeUnit(
        sessionId: String,
        plan: TranslationRequestPlan,
        submitter: TranslationPlanSubmitter,
    ): DurableTranslationUnitResult {
        require(TranslationPlanner.isRequestPlanSelfConsistent(plan)) {
            "legacy request plan is not self-consistent"
        }
        require(LegacyParityTranslationPlanner.isLegacyAcceptanceSignatureValid(plan)) {
            "legacy request-plan acceptance signature mismatch"
        }

        val manifest = store.readManifest(sessionId)
        val activeEntry = store.readActiveEntry(sessionId, plan.unitId)
        if (activeEntry != null) {
            val sourceHash = sha256Utf8(plan.exactSourceText)
            val activeMachine = activeEntry.record.activeMachineRevisionId?.let { activeId ->
                activeEntry.record.machineRevisions.firstOrNull { it.id == activeId }
            }
            val matchingMachine = activeMachine?.requestSignature == plan.requestSignature
            val matchingManual = activeEntry.record.manualRevision?.basedOnSourceTextHash == sourceHash
            if (matchingMachine || matchingManual) {
                return DurableTranslationUnitResult(
                    unitId = plan.unitId,
                    disposition = DurableTranslationUnitDisposition.REUSED_ENTRY,
                    effectiveText = activeEntry.record.effectiveText(),
                )
            }
            return DurableTranslationUnitResult(
                unitId = plan.unitId,
                disposition = DurableTranslationUnitDisposition.STALE_STATE,
            )
        }

        // Only execution/recovery that may need durable request evidence requires the plan file.
        // This keeps a previously accepted compatible entry reusable even if an unrelated orphan
        // plan file is damaged, while every provider-capable path remains fail-closed on plan I/O.
        planStore.publish(sessionId, plan)

        val allReceipts = store.listReceipts(sessionId)
        val matchingReceipts = allReceipts
            .asSequence()
            .filter { it.unitId == plan.unitId && it.requestSignature == plan.requestSignature }
            .map { receipt -> receipt to ReceiptRecoveryPlanner.plan(receipt, manifest, plan) }
            .filter { (_, recovery) -> recovery.action != ReceiptRecoveryAction.STALE_RECEIPT }
            .toList()

        if (matchingReceipts.size > 1) {
            return DurableTranslationUnitResult(
                unitId = plan.unitId,
                disposition = DurableTranslationUnitDisposition.AMBIGUOUS_RECEIPTS,
            )
        }

        if (matchingReceipts.size == 1) {
            val (receipt, recovery) = matchingReceipts.single()
            return when (recovery.action) {
                ReceiptRecoveryAction.PLAN_NEW_ATTEMPT -> executePrepared(
                    sessionId = sessionId,
                    prepared = receipt,
                    plan = plan,
                    submitter = submitter,
                )
                ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY -> DurableTranslationUnitResult(
                    unitId = plan.unitId,
                    disposition = DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME,
                    attemptId = receipt.attemptId,
                    recoveryAction = recovery.action,
                )
                else -> resolveReceived(sessionId, receipt, plan, recovery)
            }
        }

        val staleReceiptExists = allReceipts.any {
            it.unitId == plan.unitId && it.requestSignature == plan.requestSignature
        }
        if (staleReceiptExists) {
            return DurableTranslationUnitResult(
                unitId = plan.unitId,
                disposition = DurableTranslationUnitDisposition.STALE_STATE,
            )
        }

        val current = store.readManifest(sessionId)
        val prepared = RequestReceipt(
            attemptId = "attempt-${UUID.randomUUID()}",
            sessionId = sessionId,
            unitId = plan.unitId,
            epoch = current.epoch,
            requestSignature = plan.requestSignature,
            expectedManifestRevision = current.revision,
            expectedActiveEntryRevisionId = current.activeEntryRefs[plan.unitId],
            phase = RequestReceiptPhase.PREPARED,
        )
        return executePrepared(sessionId, prepared, plan, submitter)
    }

    private suspend fun executePrepared(
        sessionId: String,
        prepared: RequestReceipt,
        plan: TranslationRequestPlan,
        submitter: TranslationPlanSubmitter,
    ): DurableTranslationUnitResult {
        val received = try {
            DurableTranslationAttemptExecutor(store).execute(prepared, plan) { request ->
                submitter.submit(request)
            }
        } catch (unknown: UnknownTranslationRemoteOutcomeException) {
            return DurableTranslationUnitResult(
                unitId = plan.unitId,
                disposition = DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME,
                attemptId = unknown.attemptId,
                recoveryAction = ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY,
            )
        }
        val recovery = ReceiptRecoveryPlanner.plan(received, store.readManifest(sessionId), plan)
        return resolveReceived(sessionId, received, plan, recovery)
    }

    private fun resolveReceived(
        sessionId: String,
        receipt: RequestReceipt,
        plan: TranslationRequestPlan,
        recovery: ReceiptRecoveryPlan,
    ): DurableTranslationUnitResult = when (recovery.action) {
        ReceiptRecoveryAction.READY_TO_ADOPT -> {
            val committed = store.adoptRecoveredCandidate(sessionId, receipt.attemptId, plan)
            val entry = requireNotNull(committed.committedEntry) { "adoptable translation did not commit" }
            DurableTranslationUnitResult(
                unitId = plan.unitId,
                disposition = DurableTranslationUnitDisposition.ADOPTED,
                effectiveText = entry.record.effectiveText(),
                attemptId = receipt.attemptId,
                recoveryAction = recovery.action,
            )
        }
        ReceiptRecoveryAction.REVIEW_CANDIDATE -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.REVIEW_REQUIRED,
            effectiveText = receipt.outcome?.candidateText,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.REJECT_CANDIDATE -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.REJECTED,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.HOLD_PENDING -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.PENDING,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.TERMINAL_OUTCOME -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.TERMINAL,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.STALE_RECEIPT -> DurableTranslationUnitResult(
            unitId = plan.unitId,
            disposition = DurableTranslationUnitDisposition.STALE_STATE,
            attemptId = receipt.attemptId,
            recoveryAction = recovery.action,
        )
        ReceiptRecoveryAction.PLAN_NEW_ATTEMPT -> error("PREPARED recovery must execute before resolution")
    }
}
