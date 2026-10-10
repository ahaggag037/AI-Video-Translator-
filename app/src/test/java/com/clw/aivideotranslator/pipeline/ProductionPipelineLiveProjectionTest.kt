package com.clw.aivideotranslator.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductionPipelineLiveProjectionTest {
    @Test fun providerWaitKeepsIncreasingBetweenBackendEvents() {
        val snapshot = ProductionPipelineProgress(
            stage = ProductionPipelineStage.STT_WAITING_PROVIDER,
            operationElapsedMs = 20_000L,
            stageElapsedMs = 5_000L,
            lastProgressMonotonicMs = 100_000L,
            ordinal = PipelineOrdinalProgress(completed = 2, total = 12, currentOneBased = 3),
            providerWaitElapsedMs = 5_000L,
            inFlightRequests = 1,
        )

        val live = ProductionPipelineLiveProjector.project(snapshot, nowMonotonicMs = 108_500L)

        assertEquals(28_500L, live.operationElapsedMs)
        assertEquals(13_500L, live.stageElapsedMs)
        assertEquals(13_500L, live.providerWaitElapsedMs)
        assertEquals(8_500L, live.sinceLastProgressMs)
        assertEquals(2, live.completed)
        assertEquals(12, live.total)
        assertEquals(3, live.currentOneBased)
    }

    @Test fun terminalProgressStopsElapsedClockButStillReportsAgeOfLastEvent() {
        val snapshot = ProductionPipelineProgress(
            stage = ProductionPipelineStage.COMPLETE,
            operationElapsedMs = 50_000L,
            stageElapsedMs = 1_000L,
            lastProgressMonotonicMs = 200_000L,
        )

        val live = ProductionPipelineLiveProjector.project(snapshot, nowMonotonicMs = 220_000L)

        assertEquals(50_000L, live.operationElapsedMs)
        assertEquals(1_000L, live.stageElapsedMs)
        assertEquals(20_000L, live.sinceLastProgressMs)
        assertNull(live.providerWaitElapsedMs)
    }
}
