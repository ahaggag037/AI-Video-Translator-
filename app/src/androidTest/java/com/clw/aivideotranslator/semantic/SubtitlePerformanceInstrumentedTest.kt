package com.clw.aivideotranslator.semantic

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * X006 / N28 structural performance falsifier.
 *
 * This deliberately avoids wall-clock thresholds, which are noisy across emulator/phone hardware.
 * Instead it exercises the full N28 timeline size on Android and verifies the lookup contract that
 * matters for scalability: 5,000 cues over 42 minutes, 1,000 fixed-seed random seeks, exact results,
 * and only one interval resolution after each binary search rather than a linear cue walk.
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

        assertEquals(
            "each seek may resolve only the binary-search candidate interval; a linear scan would grow with cue count",
            cueCount + seekCount,
            intervalResolutions,
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
        }
    }
}
