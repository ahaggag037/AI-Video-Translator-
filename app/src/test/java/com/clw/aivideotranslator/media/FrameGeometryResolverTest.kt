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

    @Test fun continuousPreviewTransformMapsVideoCornersCenterAndRoundTripsWithoutRoundingPolicy() {
        val frame = FrameGeometryResolver.canonical(1920, 1080, 0)
        val preview = FrameGeometryResolver.fitCenter(frame, 1000, 1000)
        val transform = FrameGeometryResolver.transform(frame, preview)

        assertEquals(PreviewPointPx(0.0, 219.0), transform.videoToPreview(VideoPointPx(0.0, 0.0)))
        assertEquals(PreviewPointPx(1000.0, 781.0), transform.videoToPreview(VideoPointPx(1920.0, 1080.0)))
        assertEquals(PreviewPointPx(500.0, 500.0), transform.videoToPreview(VideoPointPx(960.0, 540.0)))

        val source = VideoPointPx(321.25, 777.75)
        val roundTrip = transform.previewToVideo(transform.videoToPreview(source))
        assertEquals(source.x, roundTrip.x, 1e-9)
        assertEquals(source.y, roundTrip.y, 1e-9)
    }

    @Test fun previewBarsCannotBeMappedBackIntoCanonicalVideoTruth() {
        val frame = FrameGeometryResolver.canonical(1920, 1080, 0)
        val preview = FrameGeometryResolver.fitCenter(frame, 1000, 1000)
        val transform = FrameGeometryResolver.transform(frame, preview)
        fun rejected(point: PreviewPointPx) {
            var failed = false
            try { transform.previewToVideo(point) } catch (_: IllegalArgumentException) { failed = true }
            assertTrue("preview UI bars must be outside transform domain", failed)
        }
        rejected(PreviewPointPx(500.0, 100.0))
        rejected(PreviewPointPx(500.0, 900.0))
    }

    @Test fun rotationIsResolvedBeforePreviewTransformAndNeverAppliedTwice() {
        val frame = FrameGeometryResolver.canonical(1920, 1080, 90)
        val preview = FrameGeometryResolver.fitCenter(frame, 540, 960)
        val transform = FrameGeometryResolver.transform(frame, preview)
        assertEquals(PreviewContentRect(0, 0, 540, 960), preview.contentRect)
        assertEquals(PreviewPointPx(540.0, 960.0),
            transform.videoToPreview(VideoPointPx(frame.uprightWidthPx.toDouble(), frame.uprightHeightPx.toDouble())))
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
        val preview = FrameGeometryResolver.fitCenter(frame, 1000, 1000)
        val transform = FrameGeometryResolver.transform(frame, preview)
        rejected { transform.videoToPreview(VideoPointPx(-0.01, 0.0)) }
        rejected { transform.videoToPreview(VideoPointPx(1920.01, 0.0)) }
    }

    @Test fun largeLegalIntDimensionsDoNotOverflowAspectComparison() {
        val frame = FrameGeometryResolver.canonical(Int.MAX_VALUE, Int.MAX_VALUE - 1, 0)
        val fit = FrameGeometryResolver.fitCenter(frame, Int.MAX_VALUE - 2, Int.MAX_VALUE - 3)
        assertTrue(fit.contentRect.rightPx <= fit.viewportWidthPx)
        assertTrue(fit.contentRect.bottomPx <= fit.viewportHeightPx)
    }
}
