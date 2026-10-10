package com.clw.aivideotranslator.subtitle.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleDisplayTextTest {
    @Test
    fun explicitNewlineIsReusedInsteadOfDuplicated() {
        val text = "السطر الأول\nالسطر الثاني"
        val secondStart = text.indexOf('\n') + 1
        assertEquals(
            text,
            SubtitleDisplayText.fromSemanticLines(
                text,
                listOf(0 until secondStart, secondStart until text.length),
            ),
        )
    }

    @Test
    fun automaticTwoLineRangesReceiveExactlyOneSyntheticBreak() {
        val text = "مرحبا بالعالم من Android"
        val secondStart = text.indexOf("من")
        assertEquals(
            text.substring(0, secondStart) + "\n" + text.substring(secondStart),
            SubtitleDisplayText.fromSemanticLines(
                text,
                listOf(0 until secondStart, secondStart until text.length),
            ),
        )
    }

    @Test
    fun malformedNonContiguousRangesFailClosed() {
        val text = "سطر أول سطر ثان"
        assertNull(
            SubtitleDisplayText.fromSemanticLines(
                text,
                listOf(0 until 4, 5 until text.length),
            )
        )
    }
}
