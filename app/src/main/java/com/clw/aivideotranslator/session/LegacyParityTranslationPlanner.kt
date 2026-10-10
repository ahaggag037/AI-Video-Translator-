package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.semantic.AnchoredSourceWord
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.SourceSegmenter
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationProfile
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

internal data class LegacyParityTranslationUnit(
    val legacyUnit: SourceUnit,
    val semanticUnit: SemanticSourceUnit,
    val requestPlan: TranslationRequestPlan,
)

/**
 * Field-test adapter for translation-quality evaluation.
 *
 * The provider profile remains the same frozen nvidia-text-v1 profile. Only source segmentation is
 * changed on this non-canonical field-test branch: words are grouped by SourceSegmenter so requests
 * preserve stronger sentence/clause boundaries and more useful local context inside each unit.
 * Timing still comes only from the live STT word boundaries; request plans carry text/signatures and
 * no timing fields. This branch must not be promoted to canonical without X002 acceptance evidence.
 */
internal object LegacyParityTranslationPlanner {
    private val legacyProfile = TranslationProfile()

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
                requestPlan = TranslationPlanner.plan(semanticUnit, profile = legacyProfile),
            )
        }
    }

    /**
     * The field-test keeps the same provider profile and durable request-signature contract.
     * Segmentation identity changes through unit/source identity, so old incompatible entries are not
     * silently reused for a new semantic unit.
     */
    fun isLegacyAcceptanceSignatureValid(plan: TranslationRequestPlan): Boolean =
        plan.profile == legacyProfile &&
            plan.approvedExamples.isEmpty() &&
            plan.acceptanceSignature == TranslationPlanner.canonicalSha256(listOf(plan.requestSignature))
}
