package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationProfile
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

internal data class LegacyParityTranslationUnit(
    val legacyUnit: SourceUnit,
    val semanticUnit: SemanticSourceUnit,
    val requestPlan: TranslationRequestPlan,
)

/**
 * Round-2 field-test switch point.
 *
 * Runtime calls [plan], which intentionally routes only this non-canonical branch through the
 * isolated semantic planner for device A/B quality testing. [planFrozen] preserves the exact P0-F
 * comparator and remains covered by the legacy regression tests. The provider profile and durable
 * request/adoption signature contract are unchanged.
 */
internal object LegacyParityTranslationPlanner {
    private val legacyProfile = TranslationProfile()

    fun plan(result: NvidiaSttResult): List<LegacyParityTranslationUnit> =
        FieldTestSemanticTranslationPlanner.plan(result)

    internal fun planFrozen(result: NvidiaSttResult): List<LegacyParityTranslationUnit> {
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
                requestPlan = TranslationPlanner.plan(semanticUnit, profile = legacyProfile),
            )
        }
        require(wordCursor == result.words.size) { "legacy parity planner did not consume every source word" }
        return planned
    }

    fun isLegacyAcceptanceSignatureValid(plan: TranslationRequestPlan): Boolean =
        plan.profile == legacyProfile &&
            plan.approvedExamples.isEmpty() &&
            plan.acceptanceSignature == TranslationPlanner.canonicalSha256(listOf(plan.requestSignature))
}
