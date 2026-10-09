package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

class UnknownTranslationRemoteOutcomeException(
    val attemptId: String,
) : IllegalStateException("translation remote outcome is unknown for attempt $attemptId")

class DurableTranslationAttemptExecutor private constructor(
    private val persistPrepared: (RequestReceipt) -> RequestReceipt,
    private val persistSentIfCurrent: (RequestReceipt) -> RequestReceipt,
    private val persistReceived: (RequestReceipt) -> RequestReceipt,
) {
    constructor(store: TranslationSessionStore) : this(
        persistPrepared = store::writeReceipt,
        persistSentIfCurrent = store::markSentIfCurrent,
        persistReceived = store::writeReceipt,
    )

    internal companion object {
        fun forTesting(
            persistReceipt: (RequestReceipt) -> RequestReceipt,
            persistSentIfCurrent: (RequestReceipt) -> RequestReceipt = persistReceipt,
        ): DurableTranslationAttemptExecutor = DurableTranslationAttemptExecutor(
            persistPrepared = persistReceipt,
            persistSentIfCurrent = persistSentIfCurrent,
            persistReceived = persistReceipt,
        )
    }

    suspend fun execute(
        prepared: RequestReceipt,
        requestPlan: TranslationRequestPlan,
        submit: suspend (TranslationRequestPlan) -> TranslationProviderOutcome,
    ): RequestReceipt {
        require(prepared.phase == RequestReceiptPhase.PREPARED) { "attempt must begin PREPARED" }
        require(prepared.outcome == null) { "prepared attempt cannot contain provider outcome" }
        require(requestPlan.unitId == prepared.unitId) { "request plan unit mismatch" }
        require(requestPlan.requestSignature == prepared.requestSignature) { "request plan signature mismatch" }
        require(TranslationPlanner.isRequestPlanSelfConsistent(requestPlan)) { "request plan is not self-consistent" }

        val persistedPrepared = persistPrepared(prepared)
        check(persistedPrepared == prepared) { "PREPARED receipt persistence mismatch" }

        val sent = prepared.copy(phase = RequestReceiptPhase.SENT)
        val persistedSent = persistSentIfCurrent(sent)
        check(persistedSent == sent) { "SENT receipt persistence mismatch" }

        // This is the first point at which caller-provided transport code can run.
        // The production constructor routes SENT through the store's atomic current-fence check.
        // If transport throws/cancels or says submission may already have happened, durable state
        // deliberately remains SENT. Recovery then exposes UNKNOWN_REMOTE_OUTCOME and never turns
        // uncertainty into a blind re-POST.
        val outcome = submit(requestPlan)
        if (outcome.transport == TransportOutcome.UNKNOWN_AFTER_SUBMISSION) {
            throw UnknownTranslationRemoteOutcomeException(prepared.attemptId)
        }

        val received = sent.copy(
            phase = RequestReceiptPhase.RECEIVED,
            outcome = outcome,
        )
        val persistedReceived = persistReceived(received)
        check(persistedReceived == received) { "RECEIVED receipt persistence mismatch" }
        return persistedReceived
    }
}
