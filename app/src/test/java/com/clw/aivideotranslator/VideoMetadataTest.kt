package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun ninetyDegreeContainerRotationProducesUprightSwappedFrame() {
        val metadata = VideoMetadata(
            displayName = "portrait.mp4",
            durationMs = 5_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1_000L,
            rotationDegrees = 90,
        )
        val frame = requireNotNull(metadata.canonicalFrameOrNull())
        assertEquals(1920, frame.encodedWidthPx)
        assertEquals(1080, frame.encodedHeightPx)
        assertEquals(90, frame.rotationDegrees)
        assertEquals(1080, frame.uprightWidthPx)
        assertEquals(1920, frame.uprightHeightPx)
    }

    @Test fun missingDimensionsDoNotInventRenderGeometry() {
        val metadata = VideoMetadata(
            displayName = "unknown.mp4",
            durationMs = null,
            width = null,
            height = null,
            sizeBytes = null,
            rotationDegrees = 0,
        )
        assertNull(metadata.canonicalFrameOrNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRotationFailsClosed() {
        VideoMetadata(
            displayName = "bad.mp4",
            durationMs = 1_000L,
            width = 640,
            height = 360,
            sizeBytes = 1_000L,
            rotationDegrees = 45,
        )
    }
}
