package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

internal data class LegacyParityTranslationUnit(
    val legacyUnit: SourceUnit,
    val semanticUnit: SemanticSourceUnit,
    val requestPlan: TranslationRequestPlan,
)

/**
 * Task17 activation bridge for the already-approved legacy generation profile only. This does not
 * enable SourceSegmenter/X002. It calls the exact P0-F SubtitlePipeline segmentation and then wraps
 * each resulting unit in the signed nvidia-text-v1 request contract. The semantic interval exists
 * only in this live in-process bridge; persisted request plans contain no timing.
 */
internal object LegacyParityTranslationPlanner {
    fun plan(result: NvidiaSttResult): List<LegacyParityTranslationUnit> {
        val legacyUnits = SubtitlePipeline.sourceUnits(result.words)
        val semanticWords = LegacySubtitleBridge.sourceWords(result.words)
        var wordCursor = 0
        val planned = legacyUnits.map { unit ->
            val startCursor = wordCursor
            require(wordCursor < result.words.size) { "legacy unit has no remaining source words" }
            require(result.words[wordCursor].startMs == unit.startMs) {
                "legacy unit start no longer matches P0-F word boundary"
            }
            while (wordCursor < result.words.size) {
                val wordEnd = requireNotNull(result.words[wordCursor].endMs) { "legacy word missing end" }
                if (wordEnd > unit.endMs) break
                wordCursor++
                if (wordEnd == unit.endMs) break
            }
            require(wordCursor > startCursor) { "legacy unit consumed no words" }
            require(result.words[wordCursor - 1].endMs == unit.endMs) {
                "legacy unit end no longer matches P0-F word boundary"
            }
            val ids = semanticWords.subList(startCursor, wordCursor).map { it.id }
            val semanticUnit = LegacySubtitleBridge.semanticUnit(
                unit = unit,
                orderedWordIds = ids,
                sampleStartUs = 0L,
            )
            require(semanticUnit.sourceText == unit.sourceText) {
                "legacy parity bridge changed exact source text"
            }
            LegacyParityTranslationUnit(
                legacyUnit = unit,
                semanticUnit = semanticUnit,
                requestPlan = TranslationPlanner.plan(semanticUnit),
            )
        }
        require(wordCursor == result.words.size) { "legacy parity planner did not consume every source word" }
        return planned
    }
}
