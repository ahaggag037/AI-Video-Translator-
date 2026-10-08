package com.clw.aivideotranslator.subtitle.android

import android.icu.text.BreakIterator
import com.clw.aivideotranslator.semantic.TextBoundaries
import com.clw.aivideotranslator.semantic.TextBoundaryProvider
import com.clw.aivideotranslator.semantic.TextRange
import java.util.Locale

class AndroidTextBoundaryProvider(
    private val locale: Locale = Locale.forLanguageTag("ar"),
) : TextBoundaryProvider {
    override fun boundaries(text: String, protectedRanges: List<TextRange>): TextBoundaries {
        val grapheme = offsets(BreakIterator.getCharacterInstance(locale), text)
        val lines = offsets(BreakIterator.getLineInstance(locale), text)
        val legal = lines.intersect(grapheme).filterTo(mutableSetOf()) { offset ->
            offset > 0 && offset < text.length && protectedRanges.none { range ->
                offset > range.start && offset < range.endExclusive
            }
        }
        return TextBoundaries(graphemeOffsets = grapheme, legalLineBreakOffsets = legal)
    }

    private fun offsets(iterator: BreakIterator, text: String): Set<Int> {
        iterator.setText(text)
        val result = linkedSetOf<Int>()
        var value = iterator.first()
        while (value != BreakIterator.DONE) {
            result += value
            value = iterator.next()
        }
        return result
    }
}
