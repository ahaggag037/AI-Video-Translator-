package com.clw.aivideotranslator.semantic

import org.junit.Assert.*
import org.junit.Test

class SourceSegmenterTest {
    private fun words(tokens: List<String>, gapAfter: Map<Int, Long> = emptyMap()): List<AnchoredSourceWord> {
        var cursor = 0L
        return tokens.mapIndexed { index, token ->
            val start = cursor
            val end = start + 200_000L
            cursor = end + (gapAfter[index] ?: 50_000L)
            AnchoredSourceWord(
                SourceWord("w${index + 1}", token, AudioIntervalUs(AudioTimeUs(start), AudioTimeUs(end)), null),
                PresentationIntervalUs(PresentationTimeUs(start), PresentationTimeUs(end)),
            )
        }
    }

    @Test fun conservesEveryWordExactlyOnceAndPrefersSentenceBoundary() {
        val input = words(listOf("The", "foundation", "that", "works.", "Next", "idea", "starts", "here."))
        val result = SourceSegmenter.segment(input, SegmenterConfig(softWords = 3, hardWords = 6))
        assertEquals(input.map { it.word.id }, result.units.flatMap { it.orderedWordIds })
        assertEquals("The foundation that works.", result.units.first().sourceText)
        assertEquals("Next idea starts here.", result.units.last().sourceText)
    }

    @Test fun abbreviationAndDecimalDoNotCreateFalseSentenceBoundary() {
        val input = words(listOf("Dr.", "Smith", "paid", "1.25", "dollars.", "Then", "left."))
        val result = SourceSegmenter.segment(input, SegmenterConfig(softWords = 2, hardWords = 6))
        assertTrue(result.units.first().sourceText.contains("Dr. Smith"))
        assertTrue(result.units.first().sourceText.contains("1.25"))
        assertEquals(input.map { it.word.id }, result.units.flatMap { it.orderedWordIds })
    }

    @Test fun strongGapIsAValidBoundaryWithoutClaimingVadSilence() {
        val input = words(listOf("First", "thought", "Second", "thought"), gapAfter = mapOf(1 to 800_000L))
        val result = SourceSegmenter.segment(input, SegmenterConfig(softWords = 2, hardWords = 4))
        assertEquals(2, result.units.size)
        assertEquals(listOf("w1", "w2"), result.units[0].orderedWordIds)
        assertEquals(listOf("w3", "w4"), result.units[1].orderedWordIds)
    }

    @Test fun rejectsOverlappingPresentationWordsInsteadOfRepairingTiming() {
        val base = words(listOf("one", "two"))
        val broken = listOf(
            base[0],
            base[1].copy(presentationInterval = PresentationIntervalUs(PresentationTimeUs(100_000), PresentationTimeUs(400_000))),
        )
        try {
            SourceSegmenter.segment(broken)
            fail("overlap must not be silently repaired")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
