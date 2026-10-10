package com.clw.aivideotranslator.semantic

import java.text.Normalizer

data class TextRange(val start: Int, val endExclusive: Int) {
    init { require(start >= 0 && endExclusive > start) }
    operator fun contains(offset: Int): Boolean = offset >= start && offset < endExclusive
}

data class CanonicalTextView(
    val raw: String,
    val displayCanonical: String,
    val protectedRanges: List<TextRange>,
    val warnings: Set<String>,
    val requiresReview: Boolean,
)

object TextPolicy {
    private val protectedPattern = Regex(
        "(?i)(https?://\\S+|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|`[^`]+`|/[A-Za-z0-9._~!&'()*+,;=:@%/\\-]+)"
    )
    private val bidiReviewCodePoints = setOf(
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
    )

    fun canonicalView(raw: String): CanonicalTextView {
        require(raw.isNotEmpty()) { "text is empty" }
        require(!raw.contains('\u0000')) { "NUL is not accepted" }
        requireValidUtf16(raw)
        val rawProtected = protectedPattern.findAll(raw)
            .map { TextRange(it.range.first, it.range.last + 1) }
            .toList()
        val untrimmedProtected = mutableListOf<TextRange>()
        val untrimmedCanonical = buildString(raw.length) {
            var cursor = 0
            rawProtected.forEach { range ->
                if (cursor < range.start) append(normalizeOrdinary(raw.substring(cursor, range.start)))
                val canonicalStart = length
                append(raw.substring(range.start, range.endExclusive))
                untrimmedProtected += TextRange(canonicalStart, length)
                cursor = range.endExclusive
            }
            if (cursor < raw.length) append(normalizeOrdinary(raw.substring(cursor)))
        }
        val trimStart = untrimmedCanonical.indexOfFirst { !it.isWhitespace() }
            .let { if (it < 0) untrimmedCanonical.length else it }
        val trimEndExclusive = untrimmedCanonical.indexOfLast { !it.isWhitespace() }
            .let { if (it < 0) trimStart else it + 1 }
        val canonical = untrimmedCanonical.substring(trimStart, trimEndExclusive)
        val canonicalProtected = untrimmedProtected.map { range ->
            check(range.start >= trimStart && range.endExclusive <= trimEndExclusive) {
                "protected text cannot be removed by canonical whitespace trimming"
            }
            TextRange(range.start - trimStart, range.endExclusive - trimStart)
        }
        val codePoints = raw.codePoints().toArray().toSet()
        val bidiReview = codePoints.any { it in bidiReviewCodePoints }
        val warnings = buildSet {
            if (bidiReview) add("BIDI_CONTROL_REVIEW")
            if (raw.any { Character.getType(it) == Character.FORMAT.toInt() && it != '\u200C' && it != '\u200D' }) {
                add("FORMAT_CONTROL_PRESENT")
            }
        }
        return CanonicalTextView(
            raw = raw,
            displayCanonical = canonical,
            protectedRanges = canonicalProtected,
            warnings = warnings,
            requiresReview = bidiReview,
        )
    }

    private fun normalizeOrdinary(value: String): String =
        Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC)

    private fun requireValidUtf16(value: String) {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            when {
                Character.isHighSurrogate(ch) -> {
                    require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                        "unpaired high surrogate"
                    }
                    index += 2
                }
                Character.isLowSurrogate(ch) -> throw IllegalArgumentException("unpaired low surrogate")
                else -> index++
            }
        }
    }
}
