package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSourceFlowTest {
    @Test fun pickedSourceCannotRequestDurableActivationBeforeSuccessfulProbe() {
        val flow = CandidateSourceFlow()

        flow.select("content://video/a")

        assertNull(flow.requestActivation())
        assertTrue(flow.state is CandidateSourceState.Probing)
    }

    @Test fun readyCandidateProducesExplicitActivationRequestForCurrentUriOnly() {
        val flow = CandidateSourceFlow()
        val ticket = flow.select("content://video/a")
        val metadata = VideoMetadata(
            displayName = "a.mp4",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1_000L,
            hasAudio = true,
        )
        assertTrue(flow.completeProbe(ticket, Result.success(metadata)))

        val request = flow.requestActivation()

        assertEquals(ticket, request?.ticket)
        assertEquals("content://video/a", request?.contentUri)
        assertEquals(metadata, flow.metadata)
        assertTrue(flow.state is CandidateSourceState.Activating)
    }

    @Test fun lateProbeFromSupersededPickCannotReplaceCurrentCandidateMetadata() {
        val flow = CandidateSourceFlow()
        val oldTicket = flow.select("content://video/old")
        val newTicket = flow.select("content://video/new")
        val oldMetadata = VideoMetadata("old.mp4", 1_000L, 100, 100, 10L, hasAudio = true)
        val newMetadata = VideoMetadata("new.mp4", 2_000L, 200, 200, 20L, hasAudio = true)

        assertTrue(!flow.completeProbe(oldTicket, Result.success(oldMetadata)))
        assertTrue(flow.completeProbe(newTicket, Result.success(newMetadata)))

        assertEquals(newMetadata, flow.metadata)
        assertEquals("content://video/new", flow.requestActivation()?.contentUri)
    }

    @Test fun activationFailureReturnsToReadyWithoutLosingProbeMetadata() {
        val flow = CandidateSourceFlow()
        val ticket = flow.select("content://video/a")
        val metadata = VideoMetadata("a.mp4", 1_000L, 100, 100, 10L, hasAudio = true)
        flow.completeProbe(ticket, Result.success(metadata))
        val request = requireNotNull(flow.requestActivation())

        assertTrue(flow.activationFailed(request.ticket, "تعذر اعتماد المصدر"))

        assertTrue(flow.state is CandidateSourceState.Ready)
        assertEquals(metadata, flow.metadata)
        assertEquals("تعذر اعتماد المصدر", (flow.state as CandidateSourceState.Ready).lastActivationError)
    }
}
