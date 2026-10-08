package com.clw.aivideotranslator.tv1

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SourceUnit

/**
 * Explicit compatibility boundary between the P0 millisecond model and TV1 typed clocks.
 *
 * This bridge is intentionally loss-intolerant: it never clamps, guesses, or silently
 * rounds timing. Production activation of non-zero media clock mapping remains gated by X001.
 */
object LegacySubtitleBridge {
    fun sourceWords(words: List<NvidiaWord>): List<SourceWord> {
        require(words.isNotEmpty()) { "legacy word list must not be empty" }
        var previousEndUs = -1L
        return words.mapIndexed { index, word ->
            val startMs = requireNotNull(word.startMs) { "legacy word missing startMs" }
            val endMs = requireNotNull(word.endMs) { "legacy word missing endMs" }
            val startUs = legacyMsToUs(startMs)
            val endUs = legacyMsToUs(endMs)
            require(startUs.value >= previousEndUs) { "legacy words overlap or are unordered" }
            require(endUs.value > startUs.value) { "legacy word timing must be positive" }
            previousEndUs = endUs.value
            SourceWord(
                id = "w%06d".format(index + 1),
                text = word.text.trim(),
                sampleStartUs = startUs,
                sampleEndUs = endUs,
                confidence = word.confidence,
            )
        }
    }

    fun semanticUnits(
        units: List<SourceUnit>,
        clockMap: SampleClockMap,
    ): List<SemanticSourceUnit> {
        require(units.isNotEmpty()) { "legacy source units must not be empty" }
        var previousEndUs = clockMap.presentationOriginUs.value
        return units.map { unit ->
            val start = clockMap.toPresentation(legacyMsToUs(unit.startMs))
            val end = clockMap.toPresentation(legacyMsToUs(unit.endMs))
            require(start.value >= previousEndUs) { "legacy semantic units overlap or are unordered" }
            require(end.value > start.value) { "legacy semantic unit timing must be positive" }
            previousEndUs = end.value
            SemanticSourceUnit(
                id = unit.id,
                sourceText = unit.sourceText,
                presentationStartUs = start,
                presentationEndUs = end,
                sourceWordIds = null,
            )
        }
    }

    /**
     * Bridges an already-authored P0 cue into semantic TV1 state. The caller supplies the
     * translation revision identity because P0 did not persist one.
     */
    fun semanticCue(
        cue: ArabicSubtitleCue,
        translationRevisionId: String,
    ): SemanticCue = SemanticCue(
        sourceUnitId = cue.sourceUnitId,
        translationRevisionId = translationRevisionId,
        speechStartUs = legacyMsToUs(cue.startMs),
        speechEndUs = legacyMsToUs(cue.endMs),
        text = cue.text,
    )

    /**
     * Legacy export/preview code only understands milliseconds. Reject non-integral
     * millisecond values rather than retiming a TV1 cue silently.
     */
    fun legacyCue(cue: SemanticCue): ArabicSubtitleCue = ArabicSubtitleCue(
        sourceUnitId = cue.sourceUnitId,
        startMs = cue.speechStartUs.toLegacyMsExact(),
        endMs = cue.speechEndUs.toLegacyMsExact(),
        text = cue.text,
    )
}
