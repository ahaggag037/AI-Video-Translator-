package com.clw.aivideotranslator.semantic

import java.util.Locale

data class AnchoredSourceWord(
    val word: SourceWord,
    val presentationInterval: PresentationIntervalUs,
)

data class SegmentationResult(
    val units: List<SemanticSourceUnit>,
    val wordToUnitId: Map<String, String>,
)

object SourceSegmenter {
    private val abbreviations = setOf("mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "e.g.", "i.e.", "vs.")

    fun segment(
        words: List<AnchoredSourceWord>,
        config: SegmenterConfig = SegmenterConfig(),
    ): SegmentationResult {
        require(words.isNotEmpty()) { "source words are empty" }
        validateWords(words)
        val spans = mutableListOf<IntRange>()
        var start = 0
        while (start < words.size) {
            val hardEnd = hardEnd(words, start, config)
            require(hardEnd >= start) { "indivisible source token exceeds hard limits" }
            val chosen = chooseEnd(words, start, hardEnd, config)
            spans += start..chosen
            start = chosen + 1
        }

        val units = spans.mapIndexed { index, span -> buildUnit(words, span, index) }
        val flattened = units.flatMap { it.orderedWordIds }
        require(flattened == words.map { it.word.id }) { "segmentation must conserve word identity and order" }
        return SegmentationResult(
            units = units,
            wordToUnitId = units.flatMap { unit -> unit.orderedWordIds.map { it to unit.id } }.toMap(),
        )
    }

    private fun validateWords(words: List<AnchoredSourceWord>) {
        require(words.map { it.word.id }.distinct().size == words.size) { "duplicate source word id" }
        var previousAudioEnd = -1L
        var previousPresentationEnd = -1L
        words.forEach { anchored ->
            require(anchored.word.audioInterval.start.value >= previousAudioEnd) { "audio words overlap or are out of order" }
            require(anchored.presentationInterval.start.value >= previousPresentationEnd) { "presentation words overlap or are out of order" }
            previousAudioEnd = anchored.word.audioInterval.end.value
            previousPresentationEnd = anchored.presentationInterval.end.value
        }
    }

    private fun hardEnd(words: List<AnchoredSourceWord>, start: Int, config: SegmenterConfig): Int {
        var end = start - 1
        var scalars = 0
        for (index in start until words.size) {
            val candidateScalars = scalars + codePointCount(words[index].word.rawText) + if (index == start) 0 else 1
            val duration = words[index].presentationInterval.end.value - words[start].presentationInterval.start.value
            val count = index - start + 1
            if (count > config.hardWords || candidateScalars > config.hardScalars || duration > config.hardDurationUs) break
            scalars = candidateScalars
            end = index
        }
        return end
    }

    private fun chooseEnd(words: List<AnchoredSourceWord>, start: Int, hardEnd: Int, config: SegmenterConfig): Int {
        if (hardEnd == words.lastIndex) return hardEnd
        val candidates = (start..hardEnd).map { index -> index to boundaryScore(words, index, config) }
        val complete = candidates.firstOrNull { (_, score) -> score >= 400 && satisfiesMeaningfulStart(words, start, it.first) }
        if (complete != null) return complete.first

        val softTarget = (start..hardEnd).firstOrNull { index ->
            val count = index - start + 1
            val duration = words[index].presentationInterval.end.value - words[start].presentationInterval.start.value
            val scalars = sourceText(words, start..index).codePointCount(0, sourceText(words, start..index).length)
            count >= config.softWords || duration >= config.softDurationUs || scalars >= config.softScalars
        } ?: hardEnd

        return candidates
            .filter { it.first >= (softTarget - 3).coerceAtLeast(start) }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { -kotlin.math.abs(it.first - softTarget) })
            ?.first ?: hardEnd
    }

    private fun boundaryScore(words: List<AnchoredSourceWord>, index: Int, config: SegmenterConfig): Int {
        if (index >= words.lastIndex) return 1000
        val token = words[index].word.rawText.trim()
        val lower = token.lowercase(Locale.ROOT)
        val gap = words[index + 1].presentationInterval.start.value - words[index].presentationInterval.end.value
        val strongSentence = token.lastOrNull() in setOf('.', '!', '?') &&
            lower !in abbreviations && !looksLikeDecimalOrUrl(token)
        return when {
            strongSentence -> 500
            gap >= config.strongGapUs -> 400
            token.lastOrNull() in setOf(';', ':') -> 300
            gap >= config.clauseGapUs -> 250
            token.lastOrNull() == ',' -> 200
            else -> 100
        }
    }

    private fun satisfiesMeaningfulStart(words: List<AnchoredSourceWord>, start: Int, end: Int): Boolean = end >= start

    private fun buildUnit(words: List<AnchoredSourceWord>, span: IntRange, ordinal: Int): SemanticSourceUnit {
        val text = sourceText(words, span)
        val forced = span.last < words.lastIndex && boundaryScore(words, span.last, SegmenterConfig()) < 200
        return SemanticSourceUnit(
            id = "sv1-${ordinal + 1}-${sha256Utf8(words[span.first].word.id + ":" + words[span.last].word.id).take(12)}",
            orderedWordIds = span.map { words[it].word.id },
            sourceText = text,
            sourceTextHash = sha256Utf8(text),
            sourceInterval = PresentationIntervalUs(
                words[span.first].presentationInterval.start,
                words[span.last].presentationInterval.end,
            ),
            segmentationVersion = TranslationDefaults.SEGMENTATION_VERSION,
            warnings = if (forced) setOf("INCOMPLETE_SOURCE_UNIT") else emptySet(),
        )
    }

    private fun sourceText(words: List<AnchoredSourceWord>, span: IntRange): String =
        span.joinToString(" ") { words[it].word.rawText }.replace(Regex("\\s+([,.!?;:])"), "$1")

    private fun looksLikeDecimalOrUrl(token: String): Boolean =
        token.matches(Regex(".*\\d\\.\\d.*")) || token.contains("://") || token.contains('@')

    private fun codePointCount(value: String): Int = value.codePointCount(0, value.length)
}
