package com.clw.aivideotranslator.semantic

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * X006 / N28 structural performance falsifier.
 *
 * Wall-clock time is reported as device evidence, but correctness is not gated on a synthetic
 * cross-device speed number. The hard scalability assertion is architectural: construction may
 * inspect each cue once and each seek may resolve only the single binary-search candidate interval.
 */
@RunWith(AndroidJUnit4::class)
class SubtitlePerformanceInstrumentedTest {
    private data class Cue(
        val id: Int,
        val interval: PresentationIntervalUs,
    )

    @Test
    fun n28RandomSeeksStayExactWithoutLinearIntervalResolution() {
        val durationUs = 42L * 60L * 1_000_000L
        val cueCount = 5_000
        val seekCount = 1_000
        val slotUs = durationUs / cueCount
        val activeUs = slotUs * 3L / 4L

        val cues = (0 until cueCount).map { index ->
            val start = index * slotUs
            Cue(
                id = index,
                interval = PresentationIntervalUs(
                    start = PresentationTimeUs(start),
                    end = PresentationTimeUs(start + activeUs),
                ),
            )
        }

        var intervalResolutions = 0
        val index = CueIndex(cues) { cue ->
            intervalResolutions += 1
            cue.interval
        }
        assertEquals(cueCount, index.size)
        assertEquals("index construction should inspect every cue exactly once", cueCount, intervalResolutions)

        val startedNs = SystemClock.elapsedRealtimeNanos()
        val random = Random(0x58_30_30_36)
        repeat(seekCount) {
            val timeUs = random.nextLong(0L, durationUs)
            val slot = (timeUs / slotUs).toInt().coerceAtMost(cueCount - 1)
            val expected = cues[slot].takeIf {
                timeUs >= it.interval.start.value && timeUs < it.interval.end.value
            }
            val actual = index.activeAt(PresentationTimeUs(timeUs))
            assertEquals(expected?.id, actual?.id)
        }
        val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs

        assertEquals(
            "each seek may resolve only the binary-search candidate interval; a linear scan would grow with cue count",
            cueCount + seekCount,
            intervalResolutions,
        )
        println(
            "X006_METRIC kind=cue_index_random " +
                "manufacturer=${metricToken(Build.MANUFACTURER)} model=${metricToken(Build.MODEL)} " +
                "api=${Build.VERSION.SDK_INT} cueCount=$cueCount timelineDurationUs=$durationUs " +
                "seekCount=$seekCount intervalResolutions=$intervalResolutions " +
                "elapsedMs=${elapsedNs / 1_000_000.0}",
        )
    }

    @Test
    fun n28BoundarySeeksRemainHalfOpenAtScale() {
        val cueCount = 5_000
        val slotUs = (42L * 60L * 1_000_000L) / cueCount
        val cues = (0 until cueCount).map { index ->
            val start = index * slotUs
            Cue(
                index,
                PresentationIntervalUs(
                    PresentationTimeUs(start),
                    PresentationTimeUs(start + slotUs / 2L),
                ),
            )
        }
        val index = CueIndex(cues) { it.interval }

        listOf(0, 1, cueCount / 2, cueCount - 2, cueCount - 1).forEach { cueId ->
            val cue = cues[cueId]
            assertEquals(cueId, index.activeAt(cue.interval.start)?.id)
            assertEquals(null, index.activeAt(cue.interval.end)?.id)
            val gapProbe = PresentationTimeUs(cue.interval.end.value + slotUs / 8L)
            if (gapProbe.value < (cueId + 1L) * slotUs) {
                assertEquals(null, index.activeAt(gapProbe)?.id)
            }
        }
    }

    @Test
    fun n28AdjacentAndRepeatedSeeksKeepFirstLastAndBoundariesExact() {
        val cueCount = 5_000
        val durationUs = 42L * 60L * 1_000_000L
        val slotUs = durationUs / cueCount
        val cues = (0 until cueCount).map { index ->
            val start = index * slotUs
            Cue(
                index,
                PresentationIntervalUs(
                    PresentationTimeUs(start),
                    PresentationTimeUs(start + slotUs),
                ),
            )
        }

        var intervalResolutions = 0
        val index = CueIndex(cues) { cue ->
            intervalResolutions += 1
            cue.interval
        }
        val probes = mutableListOf<Pair<Long, Int?>>()
        probes += 0L to 0
        probes += (slotUs - 1L) to 0
        probes += slotUs to 1
        probes += ((cueCount - 1L) * slotUs) to (cueCount - 1)
        probes += (cueCount.toLong() * slotUs) to null
        repeat(1_000) {
            probes += ((cueCount / 2L) * slotUs + slotUs / 2L) to (cueCount / 2)
        }

        probes.forEach { (timeUs, expectedId) ->
            assertEquals(expectedId, index.activeAt(PresentationTimeUs(timeUs))?.id)
        }
        assertEquals(
            "repeated/adjacent seeks must still resolve one candidate interval per lookup",
            cueCount + probes.size,
            intervalResolutions,
        )
        println(
            "X006_METRIC kind=cue_index_boundaries " +
                "manufacturer=${metricToken(Build.MANUFACTURER)} model=${metricToken(Build.MODEL)} " +
                "api=${Build.VERSION.SDK_INT} cueCount=$cueCount lookupCount=${probes.size} " +
                "intervalResolutions=$intervalResolutions",
        )
    }

    private fun metricToken(value: String): String = value.trim().replace(Regex("\\s+"), "_")
}
