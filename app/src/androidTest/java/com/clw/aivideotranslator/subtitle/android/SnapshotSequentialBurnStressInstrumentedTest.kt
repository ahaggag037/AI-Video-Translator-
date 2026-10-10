package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.icu.util.VersionInfo
import android.os.Build
import androidx.media3.common.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X006 / N28 sequential-burn structural stress.
 *
 * The real 1080p raster byte cost is covered by [RasterWindowBudgetInstrumentedTest]. This test
 * isolates the long-timeline burn access pattern: 5,000 unique snapshot cues spanning 42 minutes are
 * consumed in order through the production [SnapshotBitmapOverlay] and [RasterCoordinator]. A
 * deterministic lightweight producer keeps the test about retention/scheduling rather than device
 * font throughput. Every request may render once, and timeline history must never become resident
 * raster history.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotSequentialBurnStressInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(160, 90)
    private val environment =
        "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};x006=sequential-burn"

    @Test
    fun n28FiveThousandCueSequentialBurnStaysBoundedAndRendersEachRequestOnce() {
        val cueCount = 5_000
        val durationUs = 42L * 60L * 1_000_000L
        val slotUs = durationUs / cueCount
        val activeUs = slotUs * 3L / 4L
        val descriptor = descriptor("س")
        val renderCounts = ConcurrentHashMap<String, AtomicInteger>()
        val producerCalls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                producerCalls.incrementAndGet()
                renderCounts.computeIfAbsent(request.requestId) { AtomicInteger(0) }.incrementAndGet()
                readyRaster(request)
            },
        )
        val cues = (0 until cueCount).map { index ->
            val startUs = index * slotUs
            val requestId = "n28-burn-$index"
            SnapshotRasterCue(
                cueId = requestId,
                startUs = startUs,
                endUs = startUs + activeUs,
                request = RasterRequest(
                    requestId = requestId,
                    descriptor = descriptor,
                    geometry = geometry,
                    font = font,
                ),
            )
        }
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(cues),
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )

        try {
            overlay.configure(Size(geometry.uprightWidthPx, geometry.uprightHeightPx))
            cues.forEachIndexed { index, cue ->
                // Access one representative frame inside every active half-open interval. The
                // overlay prefetches current+next exactly as production export does.
                val bitmap = overlay.getBitmap(cue.startUs + activeUs / 2L)
                assertEquals(geometry.uprightWidthPx, bitmap.width)
                assertEquals(geometry.uprightHeightPx, bitmap.height)

                if (index % 250 == 0 || index == cues.lastIndex) {
                    assertTrue(
                        "N23 cache bytes grew with timeline history at cue $index: ${coordinator.cachedByteCount}",
                        coordinator.cachedByteCount <= RasterCoordinator.MAX_CACHE_BYTES,
                    )
                    assertTrue(
                        "retained raster entries grew with timeline history at cue $index",
                        coordinator.retainedEntryCount <= 2,
                    )
                }
            }

            assertEquals("every N28 cue should be prepared exactly once", cueCount, producerCalls.get())
            assertEquals(cueCount, renderCounts.size)
            renderCounts.forEach { (requestId, count) ->
                assertEquals("$requestId rendered more than once", 1, count.get())
            }
            assertTrue(coordinator.cachedByteCount <= RasterCoordinator.MAX_CACHE_BYTES)
            assertTrue(coordinator.retainedEntryCount <= 2)
        } finally {
            runCatching { overlay.release() }
            coordinator.close()
        }
    }

    private fun descriptor(text: String): SubtitleLayoutDescriptor {
        val result = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("stress fixture text must fit", result is SubtitleLayoutResult.Fits)
        return (result as SubtitleLayoutResult.Fits).descriptor
    }

    private fun readyRaster(request: RasterRequest): SubtitleRasterResult.Ready {
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
