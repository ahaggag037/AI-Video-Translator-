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
import kotlin.random.Random

/** X006 seek/export stale-state and lifecycle stress over the production snapshot overlay path. */
@RunWith(AndroidJUnit4::class)
class SnapshotSeekStressInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(160, 90)
    private val environment =
        "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};x006=seek-stress"

    @Test
    fun randomSeeksAcrossFiveThousandCuesStayExactAndWindowBounded() {
        val cueCount = 5_000
        val seekCount = 1_500
        val durationUs = 42L * 60L * 1_000_000L
        val slotUs = durationUs / cueCount
        val activeUs = slotUs * 3L / 4L
        val descriptor = descriptor("س")
        val producerCalls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                producerCalls.incrementAndGet()
                readyRaster(request, request.requestId.substringAfterLast('-').toInt())
            },
        )
        val cues = (0 until cueCount).map { index ->
            val startUs = index * slotUs
            val id = "seek-$index"
            SnapshotRasterCue(
                cueId = id,
                startUs = startUs,
                endUs = startUs + activeUs,
                request = RasterRequest(id, descriptor, geometry, font),
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
            val fixedProbes = listOf(
                0L,
                activeUs - 1L,
                activeUs,
                slotUs,
                (cueCount - 1L) * slotUs,
                (cueCount - 1L) * slotUs + activeUs,
            )
            val random = Random(0x58_30_30_36)
            val probes = fixedProbes + List(seekCount) { random.nextLong(0L, durationUs) }
            probes.forEachIndexed { probeIndex, timeUs ->
                val slot = (timeUs / slotUs).toInt().coerceAtMost(cueCount - 1)
                val cueStart = slot * slotUs
                val expectedCue = slot.takeIf { timeUs >= cueStart && timeUs < cueStart + activeUs }
                val bitmap = overlay.getBitmap(timeUs)
                if (expectedCue == null) {
                    assertEquals("gap must be transparent sentinel", 1, bitmap.width)
                    assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
                } else {
                    assertEquals(geometry.uprightWidthPx, bitmap.width)
                    assertEquals(expectedColor(expectedCue), bitmap.getPixel(0, 0))
                }

                if (probeIndex % 50 == 0 || probeIndex == probes.lastIndex) {
                    val cached = coordinator.cachedByteCount
                    val retained = coordinator.retainedEntryCount
                    maxCachedBytes = maxOf(maxCachedBytes, cached)
                    maxRetainedEntries = maxOf(maxRetainedEntries, retained)
                    assertTrue(cached <= RasterCoordinator.MAX_CACHE_BYTES)
                    assertTrue("random seek retained $retained entries", retained <= 2)
                }
            }

            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs
            println(
                "X006_METRIC kind=snapshot_random_seek " +
                    "manufacturer=${metricToken(Build.MANUFACTURER)} model=${metricToken(Build.MODEL)} " +
                    "api=${Build.VERSION.SDK_INT} cueCount=$cueCount timelineDurationUs=$durationUs " +
                    "seekCount=${seekCount + fixedProbes.size} producerCalls=${producerCalls.get()} " +
                    "maxCachedBytes=$maxCachedBytes maxRetainedEntries=$maxRetainedEntries " +
                    "elapsedMs=${elapsedNs / 1_000_000.0}",
            )
        } finally {
            runCatching { overlay.release() }
            coordinator.close()
        }
        assertEquals(0L, coordinator.cachedByteCount)
        assertEquals(0, coordinator.retainedEntryCount)
    }

    @Test
    fun repeatedOverlayOpenCloseDoesNotRetainRasterStateAcrossSessions() {
        val descriptor = descriptor("س")
        repeat(20) { session ->
            val coordinator = RasterCoordinator(
                producer = SubtitleRasterProducer { request -> readyRaster(request, session) },
            )
            val id = "session-$session-0"
            val overlay = SnapshotBitmapOverlay(
                timeline = SnapshotRasterTimeline(
                    listOf(
                        SnapshotRasterCue(
                            cueId = id,
                            startUs = 0L,
                            endUs = 1_000_000L,
                            request = RasterRequest(id, descriptor, geometry, font),
                        ),
                    ),
                ),
                expectedGeometry = geometry,
                exportRangeStartUs = 0L,
                coordinator = coordinator,
            )
            try {
                overlay.configure(Size(geometry.uprightWidthPx, geometry.uprightHeightPx))
                assertEquals(expectedColor(session), overlay.getBitmap(500_000L).getPixel(0, 0))
                assertTrue(coordinator.retainedEntryCount <= 1)
            } finally {
                runCatching { overlay.release() }
                coordinator.close()
            }
            assertEquals("session $session leaked raster bytes", 0L, coordinator.cachedByteCount)
            assertEquals("session $session retained entries after close", 0, coordinator.retainedEntryCount)
        }
    }

    private fun descriptor(text: String): SubtitleLayoutDescriptor {
        val result = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("stress fixture text must fit", result is SubtitleLayoutResult.Fits)
        return (result as SubtitleLayoutResult.Fits).descriptor
    }

    private fun readyRaster(request: RasterRequest, value: Int): SubtitleRasterResult.Ready {
        val mutable = Bitmap.createBitmap(
            request.geometry.uprightWidthPx,
            request.geometry.uprightHeightPx,
            Bitmap.Config.ARGB_8888,
        )
        mutable.eraseColor(expectedColor(value))
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

    private fun expectedColor(value: Int): Int = (0xff000000L or (value.toLong() and 0x00ffffffL)).toInt()

    private fun metricToken(value: String): String = value.trim().replace(Regex("\\s+"), "_")
}
