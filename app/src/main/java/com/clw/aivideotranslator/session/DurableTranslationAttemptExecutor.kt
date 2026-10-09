package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import java.util.concurrent.CancellationException

class UnknownTranslationRemoteOutcomeException(
    val attemptId: String,
    val transport: TransportOutcome,
) : IllegalStateException("translation response is unresolved after SENT for attempt $attemptId: $transport")

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

        // This is the first point at which caller-provided transport code can run. Once SENT is
        // durable, an exception cannot safely prove that the remote side did not receive the request.
        // Preserve the last durable state at SENT and surface a typed unresolved outcome so no caller
        // can accidentally turn a transport crash into a blind retry.
        val outcome = try {
            submit(requestPlan)
        } catch (error: Exception) {
            val transport = if (error is CancellationException) {
                TransportOutcome.CANCELLED
            } else {
                TransportOutcome.UNKNOWN_AFTER_SUBMISSION
            }
            throw UnknownTranslationRemoteOutcomeException(prepared.attemptId, transport).apply {
                initCause(error)
            }
        }
        if (outcome.transport != TransportOutcome.RESPONSE_RECEIVED) {
            throw UnknownTranslationRemoteOutcomeException(prepared.attemptId, outcome.transport)
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
