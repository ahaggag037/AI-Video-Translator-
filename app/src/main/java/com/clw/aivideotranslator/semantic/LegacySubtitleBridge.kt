package com.clw.aivideotranslator.semantic

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SourceUnit
import java.util.Locale

object LegacySubtitleBridge {
    const val LEGACY_TIMING_PRECISION_US = 1_000L
    const val SEGMENTATION_VERSION = "legacy-parity-v1"

    fun legacyMsToUs(milliseconds: Long): Long {
        require(milliseconds >= 0L) { "legacy timestamp must be non-negative" }
        return Math.multiplyExact(milliseconds, 1_000L)
    }

    fun sourceWords(words: List<NvidiaWord>): List<SourceWord> {
        require(words.isNotEmpty()) { "legacy word list is empty" }
        return words.mapIndexed { index, word ->
            val startMs = requireNotNull(word.startMs) { "legacy word missing start" }
            val endMs = requireNotNull(word.endMs) { "legacy word missing end" }
            val startUs = legacyMsToUs(startMs)
            val endUs = legacyMsToUs(endMs)
            SourceWord(
                id = String.format(Locale.ROOT, "w%06d", index + 1),
                rawText = word.text,
                audioInterval = AudioIntervalUs(AudioTimeUs(startUs), AudioTimeUs(endUs)),
                confidence = word.confidence,
                timingPrecisionUs = LEGACY_TIMING_PRECISION_US,
            )
        }
    }

    fun semanticUnit(
        unit: SourceUnit,
        orderedWordIds: List<String>,
        sampleStartUs: Long,
    ): SemanticSourceUnit {
        require(sampleStartUs >= 0L) { "sample origin must be non-negative" }
        val startUs = Math.addExact(sampleStartUs, legacyMsToUs(unit.startMs))
        val endUs = Math.addExact(sampleStartUs, legacyMsToUs(unit.endMs))
        return SemanticSourceUnit(
            id = unit.id,
            orderedWordIds = orderedWordIds.toList(),
            sourceText = unit.sourceText,
            sourceTextHash = sha256Utf8(unit.sourceText),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(startUs), PresentationTimeUs(endUs)),
            segmentationVersion = SEGMENTATION_VERSION,
        )
    }

    fun semanticCue(
        cue: ArabicSubtitleCue,
        sampleStartUs: Long,
        effectiveRevisionId: String,
    ): SemanticCue {
        require(sampleStartUs >= 0L) { "sample origin must be non-negative" }
        val startUs = Math.addExact(sampleStartUs, legacyMsToUs(cue.startMs))
        val endUs = Math.addExact(sampleStartUs, legacyMsToUs(cue.endMs))
        return SemanticCue(
            id = cue.sourceUnitId,
            sourceUnitId = cue.sourceUnitId,
            effectiveTranslationRevisionId = effectiveRevisionId,
            text = cue.text,
            speechInterval = PresentationIntervalUs(PresentationTimeUs(startUs), PresentationTimeUs(endUs)),
        )
    }
}
