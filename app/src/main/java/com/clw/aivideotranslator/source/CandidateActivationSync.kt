package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState

internal enum class CandidateActivationOutcome {
    IGNORED,
    WAITING,
    ACTIVATED,
    FAILED,
}

/**
 * Reconciles the transient candidate activation request with durable source truth. Only a durable
 * state carrying the current candidate URI may complete or fail that candidate; stale states from a
 * previous source are ignored.
 */
internal object CandidateActivationSync {
    private const val SAFE_ACTIVATION_FAILURE = "تعذر اعتماد المصدر بأمان. حاول مرة أخرى."

    fun apply(
        flow: CandidateSourceFlow,
        durable: DurableSourceUiState,
    ): CandidateActivationOutcome {
        val activating = flow.state as? CandidateSourceState.Activating
            ?: return CandidateActivationOutcome.IGNORED
        val ticket = activating.ticket

        if (durable.contentUri != ticket.contentUri) {
            return CandidateActivationOutcome.IGNORED
        }

        return when (durable.phase) {
            DurableSourcePhase.CAPTURING -> CandidateActivationOutcome.WAITING
            DurableSourcePhase.BOUND -> {
                if (flow.activationSucceeded(ticket)) {
                    CandidateActivationOutcome.ACTIVATED
                } else {
                    CandidateActivationOutcome.IGNORED
                }
            }
            DurableSourcePhase.FAILED -> {
                if (flow.activationFailed(ticket, SAFE_ACTIVATION_FAILURE)) {
                    CandidateActivationOutcome.FAILED
                } else {
                    CandidateActivationOutcome.IGNORED
                }
            }
            DurableSourcePhase.NONE -> CandidateActivationOutcome.IGNORED
        }
    }
}
