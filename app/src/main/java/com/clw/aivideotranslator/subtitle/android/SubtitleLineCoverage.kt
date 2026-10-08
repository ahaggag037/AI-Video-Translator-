package com.clw.aivideotranslator.subtitle.android

import com.clw.aivideotranslator.semantic.TextBoundaries

/** Validate native AUTO wrapping as well as deliberately selected breaks; never repair ranges. */
object SubtitleLineCoverage {
    fun coversExactly(textLength: Int, ranges: List<IntRange>): Boolean {
        if (textLength <= 0 || ranges.size !in 1..2) return false
        var cursor = 0
        for (range in ranges) {
            if (range.first != cursor || range.isEmpty() || range.last >= textLength) return false
            cursor = range.last + 1
        }
        return cursor == textLength
    }

    fun usesLegalBreaks(textLength: Int, ranges: List<IntRange>, boundaries: TextBoundaries): Boolean {
        if (!coversExactly(textLength, ranges)) return false
        return ranges.all { range ->
            val end = range.last + 1
            range.first in boundaries.graphemeOffsets && end in boundaries.graphemeOffsets &&
                (end == textLength || end in boundaries.legalLineBreakOffsets)
        }
    }
}
