package com.clw.aivideotranslator.semantic

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CueIndexTest {
    data class C(val id: Int, val interval: PresentationIntervalUs)

    @Test fun halfOpenBoundariesAndGapsAreExact() {
        val cues = listOf(
            C(1, PresentationIntervalUs(PresentationTimeUs(100), PresentationTimeUs(200))),
            C(2, PresentationIntervalUs(PresentationTimeUs(300), PresentationTimeUs(400))),
        )
        val index = CueIndex(cues) { it.interval }
        assertNull(index.activeAt(PresentationTimeUs(99)))
        assertEquals(1, index.activeAt(PresentationTimeUs(100))?.id)
        assertNull(index.activeAt(PresentationTimeUs(200)))
        assertNull(index.activeAt(PresentationTimeUs(250)))
        assertEquals(2, index.activeAt(PresentationTimeUs(399))?.id)
        assertNull(index.activeAt(PresentationTimeUs(400)))
    }

    @Test fun binaryLookupMatchesReferenceLinearSearchAcrossRandomSeeks() {
        val cues = (0 until 100).map { i ->
            C(i, PresentationIntervalUs(PresentationTimeUs(i * 1_000L), PresentationTimeUs(i * 1_000L + 700L)))
        }
        val index = CueIndex(cues) { it.interval }
        val random = Random(42)
        repeat(1000) {
            val t = random.nextLong(0L, 100_000L)
            val expected = cues.firstOrNull { t >= it.interval.start.value && t < it.interval.end.value }
            assertEquals(expected?.id, index.activeAt(PresentationTimeUs(t))?.id)
        }
    }
}
