package com.clw.aivideotranslator.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGeometryResolverTest {
    @Test fun quarterTurnRotationSwapsEncodedAxesOnlyInCanonicalFrame() {
        val r0 = FrameGeometryResolver.canonical(1920, 1080, 0)
        val r90 = FrameGeometryResolver.canonical(1920, 1080, 90)
        val r180 = FrameGeometryResolver.canonical(1920, 1080, 180)
        val r270 = FrameGeometryResolver.canonical(1920, 1080, 270)

        assertEquals(1920, r0.uprightWidthPx)
        assertEquals(1080, r0.uprightHeightPx)
        assertEquals(1080, r90.uprightWidthPx)
        assertEquals(1920, r90.uprightHeightPx)
        assertEquals(r0.uprightWidthPx, r180.uprightWidthPx)
        assertEquals(r0.uprightHeightPx, r180.uprightHeightPx)
        assertEquals(r90.uprightWidthPx, r270.uprightWidthPx)
        assertEquals(r90.uprightHeightPx, r270.uprightHeightPx)
    }

    @Test fun landscapeIntoSquareProducesOnlyTopBottomLetterboxOutsideVideoRect() {
        val frame = FrameGeometryResolver.canonical(1920, 1080, 0)
        val fit = FrameGeometryResolver.fitCenter(frame, 1000, 1000)
        assertEquals(PreviewContentRect(0, 219, 1000, 781), fit.contentRect)
        assertEquals(1000, fit.contentRect.widthPx)
        assertEquals(562, fit.contentRect.heightPx)
    }

    @Test fun portraitIntoWideViewportProducesOnlySidePillarsOutsideVideoRect() {
        val frame = FrameGeometryResolver.canonical(1920, 1080, 90)
        val fit = FrameGeometryResolver.fitCenter(frame, 1000, 500)
        assertEquals(PreviewContentRect(359, 0, 640, 500), fit.contentRect)
        assertEquals(281, fit.contentRect.widthPx)
        assertEquals(500, fit.contentRect.heightPx)
    }

    @Test fun matchingAspectRatioUsesEntireViewportWithoutInventedBars() {
        val frame = FrameGeometryResolver.canonical(1280, 720, 180)
        val fit = FrameGeometryResolver.fitCenter(frame, 640, 360)
        assertEquals(PreviewContentRect(0, 0, 640, 360), fit.contentRect)
    }

    @Test fun canonicalFrameDoesNotChangeWhenPreviewViewportChanges() {
        val frame = FrameGeometryResolver.canonical(1080, 1920, 0)
        FrameGeometryResolver.fitCenter(frame, 400, 400)
        FrameGeometryResolver.fitCenter(frame, 1920, 1080)
        assertEquals(1080, frame.uprightWidthPx)
        assertEquals(1920, frame.uprightHeightPx)
    }

    @Test fun invalidRotationAndNonpositiveGeometryFailClosed() {
        fun rejected(block: () -> Unit) {
            var failed = false
            try { block() } catch (_: IllegalArgumentException) { failed = true }
            assertTrue("must fail closed", failed)
        }
        rejected { FrameGeometryResolver.canonical(1920, 1080, 45) }
        rejected { FrameGeometryResolver.canonical(0, 1080, 0) }
        val frame = FrameGeometryResolver.canonical(1920, 1080, 0)
        rejected { FrameGeometryResolver.fitCenter(frame, 0, 1000) }
    }

    @Test fun largeLegalIntDimensionsDoNotOverflowAspectComparison() {
        val frame = FrameGeometryResolver.canonical(Int.MAX_VALUE, Int.MAX_VALUE - 1, 0)
        val fit = FrameGeometryResolver.fitCenter(frame, Int.MAX_VALUE - 2, Int.MAX_VALUE - 3)
        assertTrue(fit.contentRect.rightPx <= fit.viewportWidthPx)
        assertTrue(fit.contentRect.bottomPx <= fit.viewportHeightPx)
    }
}
