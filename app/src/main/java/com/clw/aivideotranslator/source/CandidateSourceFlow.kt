package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata

internal data class CandidateActivationRequest(
    val ticket: CandidateProbeTicket,
    val contentUri: String,
)

/**
 * Small integration boundary around [CandidateSourceCoordinator]. It owns only transient candidate
 * facts and never mutates the durable session by itself. Callers may request durable activation only
 * after the current candidate has completed probing successfully.
 */
internal class CandidateSourceFlow(
    private val coordinator: CandidateSourceCoordinator = CandidateSourceCoordinator(),
) {
    var metadata: VideoMetadata? = null
        private set

    val state: CandidateSourceState
        get() = coordinator.state

    fun select(contentUri: String): CandidateProbeTicket {
        metadata = null
        return coordinator.beginProbe(contentUri)
    }

    fun completeProbe(
        ticket: CandidateProbeTicket,
        result: Result<VideoMetadata>,
    ): Boolean {
        val probeResult = result.fold(
            onSuccess = CandidateProbeClassifier::fromMetadata,
            onFailure = CandidateProbeClassifier::fromFailure,
        )
        val accepted = coordinator.completeProbe(ticket, probeResult)
        if (!accepted) return false

        metadata = if (probeResult is CandidateProbeResult.Ready) result.getOrNull() else null
        return true
    }

    fun requestActivation(): CandidateActivationRequest? {
        val ticket = coordinator.beginActivation() ?: return null
        return CandidateActivationRequest(
            ticket = ticket,
            contentUri = ticket.contentUri,
        )
    }

    fun activationFailed(ticket: CandidateProbeTicket, message: String): Boolean =
        coordinator.activationFailed(ticket, message)

    fun activationSucceeded(ticket: CandidateProbeTicket): Boolean =
        coordinator.activationSucceeded(ticket)

    fun clear() {
        metadata = null
        coordinator.clearCandidate()
    }
}
