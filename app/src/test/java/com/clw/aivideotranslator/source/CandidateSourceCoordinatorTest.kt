package com.clw.aivideotranslator.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSourceCoordinatorTest {
    @Test fun newerPickSupersedesOlderProbeAndDropsLateResult() {
        val coordinator = CandidateSourceCoordinator()
        val a = coordinator.beginProbe("content://video/a")
        val b = coordinator.beginProbe("content://video/b")

        assertFalse(
            coordinator.completeProbe(
                a,
                CandidateProbeResult.Ready(displayName = "a.mp4"),
            )
        )
        assertEquals(
            CandidateSourceState.Probing(b),
            coordinator.state,
        )

        assertTrue(
            coordinator.completeProbe(
                b,
                CandidateProbeResult.Ready(displayName = "b.mp4"),
            )
        )
        assertEquals(
            CandidateSourceState.Ready(
                ticket = b,
                displayName = "b.mp4",
                lastActivationError = null,
            ),
            coordinator.state,
        )
    }

    @Test fun activationCanBeginOnlyFromCurrentReadyCandidate() {
        val coordinator = CandidateSourceCoordinator()
        val a = coordinator.beginProbe("content://video/a")

        assertNull(coordinator.beginActivation())

        coordinator.completeProbe(a, CandidateProbeResult.Ready("a.mp4"))
        val activation = coordinator.beginActivation()

        assertEquals(
            CandidateSourceState.Activating(a),
            coordinator.state,
        )
        assertEquals(a, activation)
    }

    @Test fun activationFailureRestoresReadyCandidateInsteadOfDiscardingIt() {
        val coordinator = CandidateSourceCoordinator()
        val ticket = coordinator.beginProbe("content://video/a")
        coordinator.completeProbe(ticket, CandidateProbeResult.Ready("a.mp4"))
        coordinator.beginActivation()

        assertTrue(coordinator.activationFailed(ticket, "تعذر اعتماد المصدر"))
        assertEquals(
            CandidateSourceState.Ready(
                ticket = ticket,
                displayName = "a.mp4",
                lastActivationError = "تعذر اعتماد المصدر",
            ),
            coordinator.state,
        )
    }

    @Test fun staleActivationCallbackCannotMutateNewerCandidate() {
        val coordinator = CandidateSourceCoordinator()
        val a = coordinator.beginProbe("content://video/a")
        coordinator.completeProbe(a, CandidateProbeResult.Ready("a.mp4"))
        coordinator.beginActivation()

        val b = coordinator.beginProbe("content://video/b")
        assertFalse(coordinator.activationSucceeded(a))
        assertEquals(CandidateSourceState.Probing(b), coordinator.state)
    }

    @Test fun typedProbeFailureRemainsCandidateLocal() {
        val coordinator = CandidateSourceCoordinator()
        val ticket = coordinator.beginProbe("content://video/a")

        assertTrue(coordinator.completeProbe(ticket, CandidateProbeResult.NoAudio))
        assertEquals(
            CandidateSourceState.Rejected(ticket, CandidateRejectReason.NO_AUDIO),
            coordinator.state,
        )
    }

    @Test fun dismissingCandidateInvalidatesOutstandingWorkAndReturnsIdle() {
        val coordinator = CandidateSourceCoordinator()
        val ticket = coordinator.beginProbe("content://video/a")

        coordinator.clearCandidate()

        assertEquals(CandidateSourceState.Idle, coordinator.state)
        assertFalse(coordinator.completeProbe(ticket, CandidateProbeResult.Ready("a.mp4")))
        assertFalse(coordinator.activationSucceeded(ticket))
    }
}
