package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMetadataTest {
    @Test fun durationUnderHour() {
        assertEquals("02:05", formatDuration(125_000))
    }

    @Test fun durationOverHour() {
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test fun megabytesFormatting() {
        assertEquals("10.0 MB", formatBytes(10L * 1024L * 1024L))
    }
}
