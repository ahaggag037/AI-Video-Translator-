package com.clw.aivideotranslator.semantic

data class TextBoundaries(
    val graphemeOffsets: Set<Int>,
    val legalLineBreakOffsets: Set<Int>,
) {
    init { require(legalLineBreakOffsets.all { it in graphemeOffsets }) }
}

interface TextBoundaryProvider {
    fun boundaries(text: String, protectedRanges: List<TextRange>): TextBoundaries
}
