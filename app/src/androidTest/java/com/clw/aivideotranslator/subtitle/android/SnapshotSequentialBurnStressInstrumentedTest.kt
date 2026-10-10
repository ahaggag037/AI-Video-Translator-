package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.icu.util.VersionInfo
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X006 long sequential export-consumption stress.
 *
 * Real 1080p byte cost is qualified separately by RasterWindowBudgetInstrumentedTest. This test
 * isolates the production scheduling/retention path over the full N28 workload: 5,000 unique cues
 * across 42 minutes through SnapshotBitmapOverlay and RasterCoordinator. A lightweight immutable
 * producer keeps runtime focused on window ownership rather than repeating font rasterization 5,000
 * times. Production must retain current+next only and must never accumulate timeline raster history.
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
        val renderCounts = IntArray(cueCount)
        val producerCalls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                producerCalls.incrementAndGet()
                val index = request.requestId.substringAfterLast('-').toInt()
                renderCounts[index] += 1
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

        var maxCachedBytes = 0L
        var maxRetainedEntries = 0
        val startedNs = SystemClock.elapsedRealtimeNanos()
        try {
            overlay.configure(Size(geometry.uprightWidthPx, geometry.uprightHeightPx))
            cues.forEachIndexed { index, cue ->
                val bitmap = overlay.getBitmap(cue.startUs + activeUs / 2L)
                assertEquals(geometry.uprightWidthPx, bitmap.width)
                assertEquals(geometry.uprightHeightPx, bitmap.height)

                if (index % 250 == 0 || index == cues.lastIndex) {
                    val cached = coordinator.cachedByteCount
                    val retained = coordinator.retainedEntryCount
                    maxCachedBytes = maxOf(maxCachedBytes, cached)
                    maxRetainedEntries = maxOf(maxRetainedEntries, retained)
                    assertTrue(
                        "N23 cache bytes grew with timeline history at cue $index: $cached",
                        cached <= RasterCoordinator.MAX_CACHE_BYTES,
                    )
                    assertTrue(
                        "retained raster entries grew with timeline history at cue $index: $retained",
                        retained <= 2,
                    )
                }
            }

            assertEquals("every N28 cue should be prepared exactly once", cueCount, producerCalls.get())
            renderCounts.forEachIndexed { index, count ->
                assertEquals("n28-burn-$index rendered more than once", 1, count)
            }
            assertTrue(coordinator.cachedByteCount <= RasterCoordinator.MAX_CACHE_BYTES)
            assertTrue(coordinator.retainedEntryCount <= 2)

            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs
            println(
                "X006_METRIC kind=sequential_burn " +
                    "manufacturer=${metricToken(Build.MANUFACTURER)} model=${metricToken(Build.MODEL)} " +
                    "api=${Build.VERSION.SDK_INT} cueCount=$cueCount timelineDurationUs=$durationUs " +
                    "producerCalls=${producerCalls.get()} maxCachedBytes=$maxCachedBytes " +
                    "maxRetainedEntries=$maxRetainedEntries elapsedMs=${elapsedNs / 1_000_000.0}",
            )
        } finally {
            runCatching { overlay.release() }
            coordinator.close()
        }
        assertEquals("closed coordinator must not retain raster bytes", 0L, coordinator.cachedByteCount)
        assertEquals("closed coordinator must not retain active entries", 0, coordinator.retainedEntryCount)
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
            ),
        )
    }

    private fun metricToken(value: String): String = value.trim().replace(Regex("\\s+"), "_")
}
