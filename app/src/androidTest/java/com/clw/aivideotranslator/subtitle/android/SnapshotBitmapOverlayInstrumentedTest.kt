package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.icu.util.VersionInfo
import android.os.Build
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotBitmapOverlayInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(426, 240)
    private val environment = "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Test
    fun outputClockMapsToSourceHalfOpenCueAndGapUsesTransparencyOnly() {
        val calls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                calls.incrementAndGet()
                fakeReady(request)
            },
        )
        val first = cue("a", 1_000_000L, 1_400_000L, "الترجمة الأولى")
        val second = cue("b", 1_600_000L, 2_000_000L, "الترجمة الثانية")
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(listOf(first, second)),
            expectedGeometry = geometry,
            exportRangeStartUs = 1_000_000L,
            coordinator = coordinator,
        )
        try {
            overlay.configure(Size(426, 240))

            val firstBitmap = overlay.getBitmap(0L)
            assertEquals(426, firstBitmap.width)
            assertEquals(240, firstBitmap.height)

            // source=1.4s is exactly the half-open end of the first cue and before the second.
            val gap = overlay.getBitmap(400_000L)
            assertEquals(1, gap.width)
            assertEquals(1, gap.height)
            assertEquals(0, gap.getPixel(0, 0) ushr 24)
            assertNotSame(firstBitmap, gap)

            val secondBitmap = overlay.getBitmap(600_000L)
            assertEquals(426, secondBitmap.width)
            assertEquals(240, secondBitmap.height)
            assertTrue(calls.get() >= 2)
        } finally {
            overlay.release()
            coordinator.close()
        }
    }

    @Test
    fun touchingCueBoundarySwitchesAtTheExactHalfOpenTimestamp() {
        val calls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                calls.incrementAndGet()
                fakeReady(request)
            },
        )
        val first = cue("a", 0L, 500_000L, "الأول")
        val second = cue("b", 500_000L, 1_000_000L, "الثاني")
        val timeline = SnapshotRasterTimeline(listOf(first, second))
        val overlay = SnapshotBitmapOverlay(
            timeline = timeline,
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )
        try {
            assertEquals("a", timeline.locate(499_999L).active?.cueId)
            assertEquals("b", timeline.locate(500_000L).active?.cueId)
            overlay.configure(Size(426, 240))
            val before = overlay.getBitmap(499_999L)
            val atBoundary = overlay.getBitmap(500_000L)
            assertNotSame("touching cues must not reuse the stale prior raster", before, atBoundary)
            assertTrue(calls.get() >= 2)
        } finally {
            overlay.release()
            coordinator.close()
        }
    }

    @Test
    fun activeCueRejectionFailsFrameInsteadOfSubstitutingTransparency() {
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { SubtitleRasterResult.Rejected("SYNTHETIC_REJECTION") },
        )
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(listOf(cue("a", 0L, 1_000_000L, "يجب ألا تختفي"))),
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )
        try {
            overlay.configure(Size(426, 240))
            try {
                overlay.getBitmap(100_000L)
                throw AssertionError("nonempty rejected cue must fail the frame")
            } catch (error: VideoFrameProcessingException) {
                assertTrue(error.message.orEmpty().contains("SYNTHETIC_REJECTION"))
            }
        } finally {
            overlay.release()
            coordinator.close()
        }
    }

    @Test
    fun configureRejectsVideoSizeDifferentFromFrozenSnapshot() {
        val coordinator = RasterCoordinator(producer = SubtitleRasterProducer(::fakeReady))
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(emptyList()),
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )
        try {
            try {
                overlay.configure(Size(640, 360))
                throw AssertionError("video size mismatch must fail closed")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        } finally {
            overlay.release()
            coordinator.close()
        }
    }

    @Test
    fun sourceClockAdditionOverflowFailsClosed() {
        val coordinator = RasterCoordinator(producer = SubtitleRasterProducer(::fakeReady))
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(emptyList()),
            expectedGeometry = geometry,
            exportRangeStartUs = Long.MAX_VALUE - 5L,
            coordinator = coordinator,
        )
        try {
            overlay.configure(Size(426, 240))
            try {
                overlay.getBitmap(10L)
                throw AssertionError("clock overflow must not wrap")
            } catch (error: VideoFrameProcessingException) {
                assertTrue(error.message.orEmpty().contains("overflow"))
            }
        } finally {
            overlay.release()
            coordinator.close()
        }
    }

    @Test
    fun timelineRejectsOverlapAndKeepsGapNextCueDeterministic() {
        val a = cue("a", 100L, 200L, "أ")
        val b = cue("b", 300L, 400L, "ب")
        val timeline = SnapshotRasterTimeline(listOf(a, b))
        assertEquals(null, timeline.locate(250L).active)
        assertEquals("b", timeline.locate(250L).next?.cueId)
        assertEquals("a", timeline.locate(100L).active?.cueId)
        assertEquals(null, timeline.locate(200L).active)

        try {
            SnapshotRasterTimeline(listOf(a, cue("c", 150L, 250L, "ج")))
            throw AssertionError("overlapping snapshot cues must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun cue(
        id: String,
        startUs: Long,
        endUs: Long,
        text: String,
    ): SnapshotRasterCue = SnapshotRasterCue(
        cueId = id,
        startUs = startUs,
        endUs = endUs,
        request = request(id, text),
    )

    private fun request(id: String, text: String): RasterRequest {
        val layout = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("test text must fit", layout is SubtitleLayoutResult.Fits)
        return RasterRequest(id, (layout as SubtitleLayoutResult.Fits).descriptor, geometry, font)
    }

    private fun fakeReady(request: RasterRequest): SubtitleRasterResult.Ready {
        val mutable = Bitmap.createBitmap(
            request.geometry.uprightWidthPx,
            request.geometry.uprightHeightPx,
            Bitmap.Config.ARGB_8888,
        )
        val immutable = requireNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false))
        mutable.recycle()
        return SubtitleRasterResult.Ready(
            ImmutableSubtitleRaster(
                bitmap = immutable,
                descriptor = request.descriptor,
                frameWidthPx = request.geometry.uprightWidthPx,
                frameHeightPx = request.geometry.uprightHeightPx,
            )
        )
    }
}
