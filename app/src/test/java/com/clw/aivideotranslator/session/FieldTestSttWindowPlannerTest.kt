package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTestSttWindowPlannerTest {
    @Test fun elevenMinuteThirteenSecondVideoIsCoveredWithoutGapsOrOverlap() {
        val durationUs = 673_000_000L
        val windows = FieldTestSttWindowPlanner.plan(durationUs)

        assertEquals(12, windows.size)
        assertEquals(0L, windows.first().startUs)
        assertEquals(durationUs, windows.last().endUs)
        windows.zipWithNext().forEach { (left, right) -> assertEquals(left.endUs, right.startUs) }
        assertTrue(windows.dropLast(1).all { it.durationUs == FieldTestSttWindowPlanner.WINDOW_US })
        assertEquals(13_000_000L, windows.last().durationUs)
    }

    @Test fun providerRelativeWordTimeMapsToOriginalVideoTimeline() {
        val window = FieldTestSttWindow(index = 3, startUs = 180_000_000L, endUs = 240_000_000L)
        val mapped = FieldTestSttWindowPlanner.toPresentationWord(
            window,
            NvidiaWord("hello", startMs = 1_250L, endMs = 1_600L, confidence = 0.9),
        )

        assertEquals(181_250L, mapped.startMs)
        assertEquals(181_600L, mapped.endMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWordOutsideItsWindow() {
        val window = FieldTestSttWindow(index = 0, startUs = 0L, endUs = 13_000_000L)
        FieldTestSttWindowPlanner.toPresentationWord(
            window,
            NvidiaWord("late", startMs = 12_900L, endMs = 13_100L, confidence = 0.9),
        )
    }
}
