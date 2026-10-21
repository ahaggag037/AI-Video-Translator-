package com.clw.aivideotranslator.source

/**
 * Identity for one transient source probe. A newer pick increments generation so late probe or
 * activation callbacks from an older candidate cannot mutate current product state.
 */
internal data class CandidateProbeTicket(
    val generation: Long,
    val contentUri: String,
)

internal enum class CandidateRejectReason {
    NO_AUDIO,
    ACCESS_LIMITED,
    INVALID,
    PROBE_FAILED,
}

internal sealed interface CandidateProbeResult {
    data class Ready(val displayName: String) : CandidateProbeResult
    data object NoAudio : CandidateProbeResult
    data object AccessLimited : CandidateProbeResult
    data object Invalid : CandidateProbeResult
    data class Failed(val message: String) : CandidateProbeResult
}

internal sealed interface CandidateSourceState {
    data object Idle : CandidateSourceState
    data class Probing(val ticket: CandidateProbeTicket) : CandidateSourceState
    data class Ready(
        val ticket: CandidateProbeTicket,
        val displayName: String,
        val lastActivationError: String? = null,
    ) : CandidateSourceState
    data class Rejected(
        val ticket: CandidateProbeTicket,
        val reason: CandidateRejectReason,
        val detail: String? = null,
    ) : CandidateSourceState
    data class Activating(val ticket: CandidateProbeTicket) : CandidateSourceState
    data class Activated(val ticket: CandidateProbeTicket) : CandidateSourceState
}

/**
 * Pure transient candidate state machine. It deliberately owns no ContentResolver, session store,
 * or provider work. Durable source replacement happens only after [beginActivation] succeeds and an
 * external activation owner commits the candidate atomically.
 */
internal class CandidateSourceCoordinator {
    private var nextGeneration = 0L
    private var activationSource: CandidateSourceState.Ready? = null

    var state: CandidateSourceState = CandidateSourceState.Idle
        private set

    fun beginProbe(contentUri: String): CandidateProbeTicket {
        require(contentUri.isNotBlank()) { "candidate source URI is blank" }
        activationSource = null
        val ticket = CandidateProbeTicket(
            generation = ++nextGeneration,
            contentUri = contentUri,
        )
        state = CandidateSourceState.Probing(ticket)
        return ticket
    }

    fun completeProbe(ticket: CandidateProbeTicket, result: CandidateProbeResult): Boolean {
        val probing = state as? CandidateSourceState.Probing ?: return false
        if (probing.ticket != ticket) return false

        state = when (result) {
            is CandidateProbeResult.Ready -> CandidateSourceState.Ready(
                ticket = ticket,
                displayName = result.displayName,
            )
            CandidateProbeResult.NoAudio -> CandidateSourceState.Rejected(
                ticket = ticket,
                reason = CandidateRejectReason.NO_AUDIO,
            )
            CandidateProbeResult.AccessLimited -> CandidateSourceState.Rejected(
                ticket = ticket,
                reason = CandidateRejectReason.ACCESS_LIMITED,
            )
            CandidateProbeResult.Invalid -> CandidateSourceState.Rejected(
                ticket = ticket,
                reason = CandidateRejectReason.INVALID,
            )
            is CandidateProbeResult.Failed -> CandidateSourceState.Rejected(
                ticket = ticket,
                reason = CandidateRejectReason.PROBE_FAILED,
                detail = result.message,
            )
        }
        return true
    }

    fun beginActivation(): CandidateProbeTicket? {
        val ready = state as? CandidateSourceState.Ready ?: return null
        activationSource = ready
        state = CandidateSourceState.Activating(ready.ticket)
        return ready.ticket
    }

    fun activationFailed(ticket: CandidateProbeTicket, message: String): Boolean {
        val activating = state as? CandidateSourceState.Activating ?: return false
        val ready = activationSource ?: return false
        if (activating.ticket != ticket || ready.ticket != ticket) return false

        state = ready.copy(lastActivationError = message)
        activationSource = null
        return true
    }

    fun activationSucceeded(ticket: CandidateProbeTicket): Boolean {
        val activating = state as? CandidateSourceState.Activating ?: return false
        if (activating.ticket != ticket) return false

        state = CandidateSourceState.Activated(ticket)
        activationSource = null
        return true
    }
}
