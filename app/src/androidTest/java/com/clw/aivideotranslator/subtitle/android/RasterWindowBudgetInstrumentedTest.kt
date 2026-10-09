package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X006 / N23 real-raster retention falsifier.
 *
 * The N28 5,000-cue lookup scale is covered separately by SubtitlePerformanceInstrumentedTest.
 * This test targets the expensive side of the pipeline instead: real 1080p immutable subtitle
 * rasters are advanced through a sequential current+next window. The coordinator must retain no
 * more than two entries and must stay within the 16 MiB app-owned raster-cache budget independent
 * of how many cue windows have already been visited.
 */
@RunWith(AndroidJUnit4::class)
class RasterWindowBudgetInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(1920, 1080)
    private val environment =
        "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Test
    fun sequential1080pWindowsStayInsideN23CacheBudgetWithoutTimelineGrowth() {
        val requests = (0 until 12).map { index ->
            request("n23-$index", "ترجمة ${index + 1}")
        }
        val coordinator = RasterCoordinator()
        try {
            var expectedSingleRasterBytes: Long? = null
            for (index in 0 until requests.lastIndex) {
                coordinator.prepareWindow(requests[index], requests[index + 1])
                val current = coordinator.awaitPrepared(requests[index].requestId)
                val next = coordinator.awaitPrepared(requests[index + 1].requestId)
                assertTrue("current 1080p raster was not admitted: $current", current is RasterAwaitResult.Ready)
                assertTrue("next 1080p raster was not admitted: $next", next is RasterAwaitResult.Ready)

                val currentLease = (current as RasterAwaitResult.Ready).lease
                val nextLease = (next as RasterAwaitResult.Ready).lease
                try {
                    val currentBytes = currentLease.raster.byteCount
                    val nextBytes = nextLease.raster.byteCount
                    if (expectedSingleRasterBytes == null) expectedSingleRasterBytes = currentBytes
                    assertEquals(expectedSingleRasterBytes, currentBytes)
                    assertEquals(expectedSingleRasterBytes, nextBytes)
                    assertTrue(
                        "N23 cache budget exceeded at window $index: ${coordinator.cachedByteCount}",
                        coordinator.cachedByteCount <= RasterCoordinator.MAX_CACHE_BYTES,
                    )
                    assertTrue(
                        "timeline history leaked into retained raster window at $index",
                        coordinator.retainedEntryCount <= 2,
                    )
                } finally {
                    currentLease.close()
                    nextLease.close()
                }
            }

            val one = requireNotNull(expectedSingleRasterBytes)
            assertEquals(1920L * 1080L * 4L, one)
            assertTrue(
                "two 1080p rasters must fit the declared N23 cache",
                one * 2L <= RasterCoordinator.MAX_CACHE_BYTES,
            )
        } finally {
            coordinator.close()
        }
    }

    private fun request(id: String, text: String): RasterRequest {
        val result = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("1080p test text must fit", result is SubtitleLayoutResult.Fits)
        return RasterRequest(
            requestId = id,
            descriptor = (result as SubtitleLayoutResult.Fits).descriptor,
            geometry = geometry,
            font = font,
        )
    }
}
