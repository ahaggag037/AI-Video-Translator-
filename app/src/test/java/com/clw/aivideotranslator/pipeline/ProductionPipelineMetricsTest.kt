package com.clw.aivideotranslator.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductionPipelineMetricsTest {
    @Test fun firstLatencyFieldsAreFirstWriteWins() {
        val recorder = ProductionPipelineMetricsRecorder(operationStartedMonotonicMs = 1_000L)
        recorder.recordSourceCapture(nowMs = 2_000L, bytes = 2_000L)
        recorder.recordSourceCapture(nowMs = 3_000L, bytes = 9_999L)
        recorder.recordFirstAudioWindow(4_000L)
        recorder.recordFirstAudioWindow(5_000L)
        recorder.recordFirstSttSent(4_100L)
        recorder.recordFirstSttSent(6_000L)
        recorder.recordFirstSttReceived(4_900L)
        recorder.recordFirstSttReceived(7_000L)

        val metrics = recorder.snapshot()
        assertEquals(2_000L, metrics.sourceBytes)
        assertEquals(1_000L, metrics.sourceCaptureElapsedMs)
        assertEquals(3_000L, metrics.firstAudioWindowElapsedMs)
        assertEquals(3_100L, metrics.firstSttSentElapsedMs)
        assertEquals(3_900L, metrics.firstSttReceivedElapsedMs)
        assertEquals(100L, metrics.timeFromFirstWindowToFirstSttSentMs)
        assertEquals(800L, metrics.firstSttRoundTripMs)
        assertEquals(2_000.0, metrics.sourceCaptureBytesPerSecond!!, 0.0)
    }

    @Test fun resourcePeaksOnlyMoveUp() {
        val recorder = ProductionPipelineMetricsRecorder(0L)
        recorder.observeResourceUsage(temporaryBytes = 100L, javaHeapBytes = 200L, nativeHeapBytes = 300L)
        recorder.observeResourceUsage(temporaryBytes = 90L, javaHeapBytes = 250L, nativeHeapBytes = 275L)

        val metrics = recorder.snapshot()
        assertEquals(100L, metrics.peakTemporaryBytes)
        assertEquals(250L, metrics.peakJavaHeapBytes)
        assertEquals(300L, metrics.peakNativeHeapBytes)
    }

    @Test fun unavailableDeltasStayUnknownInsteadOfBeingInvented() {
        val metrics = ProductionPipelineMetrics(firstAudioWindowElapsedMs = 5_000L)
        assertNull(metrics.firstSttRoundTripMs)
        assertNull(metrics.exportValidationElapsedMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleFirstSttOrdering() {
        ProductionPipelineMetrics(
            firstAudioWindowElapsedMs = 5_000L,
            firstSttSentElapsedMs = 4_000L,
        )
    }
}
