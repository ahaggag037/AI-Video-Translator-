package com.clw.aivideotranslator.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionPipelineProgressTest {
    @Test fun exposesWindowFractionAndProviderWaitWithoutPrivateContent() {
        val progress = ProductionPipelineProgress(
            stage = ProductionPipelineStage.STT_WAITING_PROVIDER,
            operationElapsedMs = 91_000L,
            stageElapsedMs = 11_000L,
            lastProgressMonotonicMs = 123_456L,
            ordinal = PipelineOrdinalProgress(
                completed = 2,
                total = 12,
                currentOneBased = 3,
            ),
            providerWaitElapsedMs = 11_000L,
            queueDepth = 2,
            inFlightRequests = 1,
            diagnosticCode = "STT_PROVIDER_WAIT",
        )

        assertEquals(2.0 / 12.0, progress.fractionOrNull!!, 0.000001)
        assertFalse(progress.isTerminal)
        assertTrue(progress.stage.isProviderWait)
    }

    @Test fun byteProgressCarriesThroughputAndFraction() {
        val progress = ProductionPipelineProgress(
            stage = ProductionPipelineStage.SOURCE_CAPTURING,
            operationElapsedMs = 2_000L,
            stageElapsedMs = 2_000L,
            lastProgressMonotonicMs = 10_000L,
            bytes = PipelineByteProgress(
                processed = 25L,
                total = 100L,
                bytesPerSecond = 12.5,
            ),
        )

        assertEquals(0.25, progress.fractionOrNull!!, 0.0)
        assertEquals(12.5, progress.bytes!!.bytesPerSecond!!, 0.0)
    }

    @Test fun unknownTotalHasNoFabricatedPercentage() {
        val progress = ProductionPipelineProgress(
            stage = ProductionPipelineStage.AUDIO_DECODING,
            operationElapsedMs = 1_000L,
            stageElapsedMs = 500L,
            lastProgressMonotonicMs = 2_000L,
            bytes = PipelineByteProgress(processed = 4_096L),
        )

        assertNull(progress.fractionOrNull)
    }

    @Test fun terminalStageIsExplicit() {
        val complete = ProductionPipelineProgress(
            stage = ProductionPipelineStage.COMPLETE,
            operationElapsedMs = 5_000L,
            stageElapsedMs = 100L,
            lastProgressMonotonicMs = 9_000L,
        )
        assertTrue(complete.isTerminal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun providerWaitTimeCannotAppearOnLocalStage() {
        ProductionPipelineProgress(
            stage = ProductionPipelineStage.AUDIO_DECODING,
            operationElapsedMs = 1_000L,
            stageElapsedMs = 100L,
            lastProgressMonotonicMs = 1_000L,
            providerWaitElapsedMs = 100L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun providerWaitStageCannotHideItsWaitTime() {
        ProductionPipelineProgress(
            stage = ProductionPipelineStage.TRANSLATION_WAITING_PROVIDER,
            operationElapsedMs = 1_000L,
            stageElapsedMs = 100L,
            lastProgressMonotonicMs = 1_000L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun progressCannotClaimMoreCompletedWindowsThanExist() {
        PipelineOrdinalProgress(completed = 13, total = 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun diagnosticCodeCannotLeakArbitraryText() {
        ProductionPipelineProgress(
            stage = ProductionPipelineStage.FAILED,
            operationElapsedMs = 1_000L,
            stageElapsedMs = 100L,
            lastProgressMonotonicMs = 1_000L,
            diagnosticCode = "provider said: private transcript",
        )
    }
}
