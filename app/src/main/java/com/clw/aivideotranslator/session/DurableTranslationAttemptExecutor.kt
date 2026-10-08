package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

class DurableTranslationAttemptExecutor(
    private val persistReceipt: (RequestReceipt) -> RequestReceipt,
) {
    constructor(store: TranslationSessionStore) : this(store::writeReceipt)

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

        val persistedPrepared = persistReceipt(prepared)
        check(persistedPrepared == prepared) { "PREPARED receipt persistence mismatch" }

        val sent = prepared.copy(phase = RequestReceiptPhase.SENT)
        val persistedSent = persistReceipt(sent)
        check(persistedSent == sent) { "SENT receipt persistence mismatch" }

        // This is the first point at which caller-provided transport code can run.
        // If transport throws/cancels or RECEIVED persistence fails, durable state remains SENT
        // and recovery must treat the remote outcome as unknown rather than blindly reposting.
        val outcome = submit(requestPlan)
        val received = sent.copy(
            phase = RequestReceiptPhase.RECEIVED,
            outcome = outcome,
        )
        val persistedReceived = persistReceipt(received)
        check(persistedReceived == received) { "RECEIVED receipt persistence mismatch" }
        return persistedReceived
    }
}
