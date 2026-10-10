package com.clw.aivideotranslator.subtitle.android

/**
 * Reconstructs the exact StaticLayout input from semantic line ranges.
 *
 * A two-line descriptor can come from either a native/forced synthetic wrap or an explicit newline
 * already present in canonical text. Explicit newlines must be reused rather than duplicated.
 */
internal object SubtitleDisplayText {
    fun fromSemanticLines(text: String, lineRanges: List<IntRange>): String? {
        if (lineRanges.size == 1) return text
        if (lineRanges.size != 2 || text.isEmpty()) return null

        val first = lineRanges[0]
        val second = lineRanges[1]
        val breakOffset = first.last + 1
        if (
            first.first != 0 ||
            breakOffset <= 0 ||
            breakOffset >= text.length ||
            second.first != breakOffset ||
            second.last != text.lastIndex
        ) {
            return null
        }

        return if (text[breakOffset - 1] == '\n') {
            text
        } else {
            text.substring(0, breakOffset) + "\n" + text.substring(breakOffset)
        }
    }
}
