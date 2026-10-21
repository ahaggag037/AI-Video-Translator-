package com.clw.aivideotranslator.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportResultTruthTest {
    @Test fun srtSuccessSurvivesMp4Failure() {
        val result = ExportResultTruth(
            srt = OutputSettlement.SUCCESS,
            mp4 = OutputSettlement.FAILED,
        )

        assertEquals(OutputSettlement.SUCCESS, result.srt)
        assertEquals(OutputSettlement.FAILED, result.mp4)
        assertEquals(setOf(OutputKind.MP4), result.retryableOutputs())
    }

    @Test fun srtSuccessSurvivesMp4Cancellation() {
        val result = ExportResultTruth(
            srt = OutputSettlement.SUCCESS,
            mp4 = OutputSettlement.CANCELLED,
        )

        assertEquals(OutputSettlement.SUCCESS, result.srt)
        assertTrue(OutputKind.MP4 in result.retryableOutputs())
    }

    @Test fun mp4SuccessSurvivesSrtFailure() {
        val result = ExportResultTruth(
            srt = OutputSettlement.FAILED,
            mp4 = OutputSettlement.SUCCESS,
        )

        assertEquals(OutputSettlement.SUCCESS, result.mp4)
        assertEquals(setOf(OutputKind.SRT), result.retryableOutputs())
    }

    @Test fun bothSuccessHasNoRetryableSibling() {
        val result = ExportResultTruth(
            srt = OutputSettlement.SUCCESS,
            mp4 = OutputSettlement.SUCCESS,
        )

        assertTrue(result.retryableOutputs().isEmpty())
    }
}
