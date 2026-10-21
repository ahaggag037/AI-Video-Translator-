package com.clw.aivideotranslator.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullVideoSttWindowPlanTest {
    @Test fun shortVideoUsesOneWindow() {
        val windows = FullVideoSttWindowPlan.forDuration(45_000_000L)
        assertEquals(listOf(SttWindow(index = 0, startUs = 0L, endUs = 45_000_000L)), windows)
    }

    @Test fun longerVideoIsCoveredContiguouslyWithoutOverlapOrGap() {
        val durationUs = 155_500_000L
        val windows = FullVideoSttWindowPlan.forDuration(durationUs)

        assertEquals(3, windows.size)
        assertEquals(0L, windows.first().startUs)
        assertEquals(durationUs, windows.last().endUs)
        windows.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endUs, right.startUs)
        }
        assertTrue(windows.all { it.durationUs in 1L..FullVideoSttWindowPlan.MAX_WINDOW_US })
    }

    @Test fun exactMinuteMultipleDoesNotCreateEmptyTail() {
        val windows = FullVideoSttWindowPlan.forDuration(120_000_000L)
        assertEquals(2, windows.size)
        assertEquals(60_000_000L, windows[0].endUs)
        assertEquals(120_000_000L, windows[1].endUs)
    }

    @Test fun operationIdentityChangesWithWindowBoundsButNotRequestProfile() {
        val source = "source-abc"
        val first = FullVideoSttWindowPlan.attemptId("session-1", source, SttWindow(0, 0L, 60_000_000L))
        val same = FullVideoSttWindowPlan.attemptId("session-1", source, SttWindow(0, 0L, 60_000_000L))
        val second = FullVideoSttWindowPlan.attemptId("session-1", source, SttWindow(1, 60_000_000L, 120_000_000L))
        assertEquals(first, same)
        assertTrue(first != second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLengthSourceIsRejected() {
        FullVideoSttWindowPlan.forDuration(0L)
    }
}