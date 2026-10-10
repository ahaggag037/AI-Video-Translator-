package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.semantic.AnchoredSourceWord
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SourceSegmenter
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationProfile

/**
 * Non-canonical field-test planner for translation-quality evaluation only.
 *
 * The NVIDIA model/profile remains unchanged. Only segmentation changes: live STT words are grouped
 * by the semantic source segmenter, which favors sentence/clause boundaries while preserving exact
 * word order and timing. The frozen LegacyParityTranslationPlanner remains untouched so production
 * recovery and regression tests keep their original contract.
 */
internal object FieldTestSemanticTranslationPlanner {
    private val providerProfile = TranslationProfile()

    fun plan(result: NvidiaSttResult): List<LegacyParityTranslationUnit> {
        val sourceWords = LegacySubtitleBridge.sourceWords(result.words)
        val anchored = sourceWords.map { word ->
            AnchoredSourceWord(
                word = word,
                presentationInterval = PresentationIntervalUs(
                    start = PresentationTimeUs(word.audioInterval.start.value),
                    end = PresentationTimeUs(word.audioInterval.end.value),
                ),
            )
        }
        val semanticUnits = SourceSegmenter.segment(anchored).units
        require(semanticUnits.isNotEmpty()) { "semantic field-test planner produced no source units" }

        return semanticUnits.map { semanticUnit ->
            val startUs = semanticUnit.sourceInterval.start.value
            val endUs = semanticUnit.sourceInterval.end.value
            require(startUs % LegacySubtitleBridge.LEGACY_TIMING_PRECISION_US == 0L) {
                "semantic unit start is not millisecond-aligned"
            }
            require(endUs % LegacySubtitleBridge.LEGACY_TIMING_PRECISION_US == 0L) {
                "semantic unit end is not millisecond-aligned"
            }
            val presentationUnit = SourceUnit(
                id = semanticUnit.id,
                startMs = startUs / LegacySubtitleBridge.LEGACY_TIMING_PRECISION_US,
                endMs = endUs / LegacySubtitleBridge.LEGACY_TIMING_PRECISION_US,
                sourceText = semanticUnit.sourceText,
            )
            LegacyParityTranslationUnit(
                legacyUnit = presentationUnit,
                semanticUnit = semanticUnit,
                requestPlan = TranslationPlanner.plan(semanticUnit, profile = providerProfile),
            )
        }
    }
}
