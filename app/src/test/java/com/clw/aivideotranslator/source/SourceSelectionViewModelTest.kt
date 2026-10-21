package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSelectionViewModelTest {
    @Test fun selectionPublishesProbeThenReadyWithoutDurableActivation() {
        val owner = SourceSelectionViewModel()

        val ticket = owner.select("content://video/a")
        assertTrue(owner.snapshot.value.state is CandidateSourceState.Probing)
        assertNull(owner.snapshot.value.metadata)

        val metadata = metadata("a.mp4")
        assertTrue(owner.completeProbe(ticket, Result.success(metadata)))

        assertTrue(owner.snapshot.value.state is CandidateSourceState.Ready)
        assertEquals(metadata, owner.snapshot.value.metadata)
    }

    @Test fun explicitActivationAndMatchingDurableBoundPublishActivated() {
        val owner = SourceSelectionViewModel()
        val ticket = owner.select("content://video/a")
        owner.completeProbe(ticket, Result.success(metadata("a.mp4")))

        val request = requireNotNull(owner.requestActivation())
        assertEquals("content://video/a", request.contentUri)
        assertTrue(owner.snapshot.value.state is CandidateSourceState.Activating)

        val outcome = owner.reconcileDurable(
            DurableSourceUiState(
                phase = DurableSourcePhase.BOUND,
                sessionId = "session-a",
                contentUri = "content://video/a",
            ),
        )

        assertEquals(CandidateActivationOutcome.ACTIVATED, outcome)
        assertTrue(owner.snapshot.value.state is CandidateSourceState.Activated)
    }

    @Test fun activationPreparationFailureReturnsReadyAndPreservesMetadata() {
        val owner = SourceSelectionViewModel()
        val ticket = owner.select("content://video/a")
        val metadata = metadata("a.mp4")
        owner.completeProbe(ticket, Result.success(metadata))
        val request = requireNotNull(owner.requestActivation())

        assertTrue(owner.failActivation(request.ticket, "تعذر تثبيت إذن قراءة الفيديو"))

        val state = owner.snapshot.value.state as CandidateSourceState.Ready
        assertEquals("تعذر تثبيت إذن قراءة الفيديو", state.lastActivationError)
        assertEquals(metadata, owner.snapshot.value.metadata)
    }

    @Test fun staleProbeDoesNotRepublishSupersededMetadata() {
        val owner = SourceSelectionViewModel()
        val oldTicket = owner.select("content://video/old")
        val newTicket = owner.select("content://video/new")

        assertTrue(!owner.completeProbe(oldTicket, Result.success(metadata("old.mp4"))))
        assertTrue(owner.completeProbe(newTicket, Result.success(metadata("new.mp4"))))

        assertEquals("new.mp4", owner.snapshot.value.metadata?.displayName)
    }

    private fun metadata(name: String) = VideoMetadata(
        displayName = name,
        durationMs = 10_000L,
        width = 1920,
        height = 1080,
        sizeBytes = 1_000L,
        hasAudio = true,
    )
}
