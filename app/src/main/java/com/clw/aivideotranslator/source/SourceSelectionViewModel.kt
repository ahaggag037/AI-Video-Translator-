package com.clw.aivideotranslator.source

import androidx.lifecycle.ViewModel
import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.session.DurableSourceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SourceSelectionSnapshot(
    val state: CandidateSourceState = CandidateSourceState.Idle,
    val metadata: VideoMetadata? = null,
)

/**
 * Lifecycle owner for transient candidate-source state. It deliberately persists nothing and never
 * mutates the durable session by itself; durable activation is still an explicit external action.
 */
internal class SourceSelectionViewModel : ViewModel() {
    private val flow = CandidateSourceFlow()
    private val _snapshot = MutableStateFlow(snapshotOfFlow())
    val snapshot = _snapshot.asStateFlow()

    fun select(contentUri: String): CandidateProbeTicket {
        val ticket = flow.select(contentUri)
        publish()
        return ticket
    }

    fun completeProbe(
        ticket: CandidateProbeTicket,
        result: Result<VideoMetadata>,
    ): Boolean {
        val accepted = flow.completeProbe(ticket, result)
        if (accepted) publish()
        return accepted
    }

    fun requestActivation(): CandidateActivationRequest? {
        val request = flow.requestActivation()
        if (request != null) publish()
        return request
    }

    fun reconcileDurable(durable: DurableSourceUiState): CandidateActivationOutcome {
        val before = flow.state
        val outcome = CandidateActivationSync.apply(flow, durable)
        if (flow.state != before) publish()
        return outcome
    }

    fun clear() {
        flow.clear()
        publish()
    }

    private fun publish() {
        _snapshot.value = snapshotOfFlow()
    }

    private fun snapshotOfFlow() = SourceSelectionSnapshot(
        state = flow.state,
        metadata = flow.metadata,
    )
}
