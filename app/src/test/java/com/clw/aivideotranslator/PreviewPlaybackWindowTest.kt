package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPlaybackWindowTest {
    @Test
    fun trailingSubtitleGapDoesNotShortenPreviewBelowExportWindow() {
        assertEquals(
            5_000L,
            PreviewPlaybackWindow.endMs(
                sampleStartMs = 0L,
                sampleEndMs = 5_000L,
                lastCueEndMs = 3_000L,
            ),
        )
    }

    @Test
    fun sampleEndStillBoundsPreviewWhenCueTimelineExtendsPastIt() {
        assertEquals(
            5_000L,
            PreviewPlaybackWindow.endMs(
                sampleStartMs = 1_000L,
                sampleEndMs = 5_000L,
                lastCueEndMs = 7_000L,
            ),
        )
    }
}
