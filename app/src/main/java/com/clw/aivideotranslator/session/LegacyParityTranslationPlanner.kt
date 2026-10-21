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
 * Adapter for the already-approved segmentation and nvidia-text-v1 request profile. The input may
 * now be a full-video STT aggregate: segmentation still runs once over the globally ordered words,
 * so unit IDs stay unique/deterministic and no chunk boundary becomes a subtitle boundary by fiat.
 */
internal object LegacyParityTranslationPlanner {
    private val legacyProfile = TranslationProfile()

    fun plan(result: NvidiaSttResult): List<LegacyParityTranslationUnit> {
        val lastWordEndMs = requireNotNull(result.words.lastOrNull()?.endMs) {
            "translation requires timed STT words"
        }
        val maxTimelineMs = maxOf(SubtitlePipeline.SAMPLE_END_MS, lastWordEndMs)
        val legacyUnits = SubtitlePipeline.sourceUnits(result.words, maxTimelineMs = maxTimelineMs)
        val semanticWords = LegacySubtitleBridge.sourceWords(result.words)
        var wordCursor = 0
        val planned = legacyUnits.map { unit ->
            val startCursor = wordCursor
            require(wordCursor < result.words.size) { "legacy unit has no remaining source words" }
            require(result.words[wordCursor].startMs == unit.startMs) {
                "legacy unit start no longer matches source word boundary"
            }
            while (wordCursor < result.words.size) {
                val wordEnd = requireNotNull(result.words[wordCursor].endMs) { "legacy word missing end" }
                if (wordEnd > unit.endMs) break
                wordCursor++
                if (wordEnd == unit.endMs) break
            }
            require(wordCursor > startCursor) { "legacy unit consumed no words" }
            require(result.words[wordCursor - 1].endMs == unit.endMs) {
                "legacy unit end no longer matches source word boundary"
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
        require(wordCursor == result.words.size) { "translation planner did not consume every source word" }
        return planned
    }

    /**
     * This adapter has no acceptance dependencies. A persisted acceptance signature is therefore only
     * valid when it is the canonical digest of the request signature itself.
     */
    fun isLegacyAcceptanceSignatureValid(plan: TranslationRequestPlan): Boolean =
        plan.profile == legacyProfile &&
            plan.approvedExamples.isEmpty() &&
            plan.acceptanceSignature == TranslationPlanner.canonicalSha256(listOf(plan.requestSignature))
}
