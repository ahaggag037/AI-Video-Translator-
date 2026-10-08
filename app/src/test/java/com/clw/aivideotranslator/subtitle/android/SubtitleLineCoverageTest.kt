package com.clw.aivideotranslator.subtitle.android

import com.clw.aivideotranslator.semantic.TextBoundaries
import org.junit.Assert.*
import org.junit.Test

class SubtitleLineCoverageTest {
    @Test fun exactOneLineAndLegalTwoLineCoverageRemainValid() {
        val boundaries = TextBoundaries((0..7).toSet(), setOf(4))
        assertTrue(SubtitleLineCoverage.usesLegalBreaks(7, listOf(0..6), boundaries))
        assertTrue(SubtitleLineCoverage.usesLegalBreaks(7, listOf(0..3, 4..6), boundaries))
    }

    @Test fun nativeEmergencyBreakInsideProtectedTokenIsNotAccepted() {
        val text = "https://example.com/averylongpath"
        // All code points are grapheme boundaries; none inside the protected URL are legal lines.
        val boundaries = TextBoundaries((0..text.length).toSet(), emptySet())
        assertFalse(SubtitleLineCoverage.usesLegalBreaks(text.length, listOf(0..14, 15..text.lastIndex), boundaries))
        assertTrue(SubtitleLineCoverage.usesLegalBreaks(text.length, listOf(0..text.lastIndex), boundaries))
    }

    @Test fun combiningMarkAndEmojiGraphemeCannotBeCutToFit() {
        val arabic = "بَ"
        assertFalse(SubtitleLineCoverage.usesLegalBreaks(arabic.length, listOf(0..0, 1..1),
            TextBoundaries(setOf(0, 2), emptySet())))
        val emoji = "👨‍👩‍👧‍👦"
        assertFalse(SubtitleLineCoverage.usesLegalBreaks(emoji.length, listOf(0..1, 2..emoji.lastIndex),
            TextBoundaries(setOf(0, emoji.length), emptySet())))
    }

    @Test fun equalTotalLengthDoesNotHideDuplicatedDroppedOrReorderedText() {
        listOf(listOf(0..2, 2..4), listOf(0..1, 3..5), listOf(3..5, 0..2),
            listOf(1..5), listOf(0..4), listOf(0..6), listOf(0..Int.MAX_VALUE), listOf(0..-1))
            .forEach { assertFalse("invalid ranges $it", SubtitleLineCoverage.coversExactly(6, it)) }
    }

    @Test fun invalidRangeCoverageCannotConstructRenderableDescriptor() {
        try {
            SubtitleLayoutDescriptor("مرحبا", 24, listOf(0..2, 2..3), PixelRect(20, 20, 60, 40),
                PixelRect(15, 15, 65, 45), PixelRect(10, 10, 100, 100), "a".repeat(64), "synthetic")
            fail("duplicate/drop must not become accepted layout")
        } catch (_: IllegalArgumentException) { }
    }
}
