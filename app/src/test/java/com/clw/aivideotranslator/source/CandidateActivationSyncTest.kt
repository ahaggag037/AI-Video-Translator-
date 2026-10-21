package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.session.DurableSourceFailure
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateActivationSyncTest {
    @Test fun matchingBoundSourceCompletesOnlyTheCurrentActivation() {
        val flow = readyActivatingFlow("content://video/a")

        val outcome = CandidateActivationSync.apply(
            flow,
            DurableSourceUiState(
                phase = DurableSourcePhase.BOUND,
                sessionId = "session-a",
                contentUri = "content://video/a",
            ),
        )

        assertEquals(CandidateActivationOutcome.ACTIVATED, outcome)
        assertTrue(flow.state is CandidateSourceState.Activated)
    }

    @Test fun staleBoundSourceForPriorUriIsIgnored() {
        val flow = readyActivatingFlow("content://video/new")

        val outcome = CandidateActivationSync.apply(
            flow,
            DurableSourceUiState(
                phase = DurableSourcePhase.BOUND,
                sessionId = "session-old",
                contentUri = "content://video/old",
            ),
        )

        assertEquals(CandidateActivationOutcome.IGNORED, outcome)
        assertTrue(flow.state is CandidateSourceState.Activating)
    }

    @Test fun matchingDurableFailureReturnsCandidateToReadyWithSafeMessage() {
        val flow = readyActivatingFlow("content://video/a")

        val outcome = CandidateActivationSync.apply(
            flow,
            DurableSourceUiState(
                phase = DurableSourcePhase.FAILED,
                contentUri = "content://video/a",
                failure = DurableSourceFailure.PERSISTENCE,
            ),
        )

        assertEquals(CandidateActivationOutcome.FAILED, outcome)
        val state = flow.state as CandidateSourceState.Ready
        assertEquals("تعذر اعتماد المصدر بأمان. حاول مرة أخرى.", state.lastActivationError)
    }

    @Test fun capturingStateDoesNotPrematurelyCompleteCandidateActivation() {
        val flow = readyActivatingFlow("content://video/a")

        val outcome = CandidateActivationSync.apply(
            flow,
            DurableSourceUiState(
                phase = DurableSourcePhase.CAPTURING,
                contentUri = "content://video/a",
            ),
        )

        assertEquals(CandidateActivationOutcome.WAITING, outcome)
        assertTrue(flow.state is CandidateSourceState.Activating)
    }

    private fun readyActivatingFlow(uri: String): CandidateSourceFlow {
        val flow = CandidateSourceFlow()
        val ticket = flow.select(uri)
        val metadata = VideoMetadata(
            displayName = "video.mp4",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1_000L,
            hasAudio = true,
        )
        check(flow.completeProbe(ticket, Result.success(metadata)))
        checkNotNull(flow.requestActivation())
        return flow
    }
}
