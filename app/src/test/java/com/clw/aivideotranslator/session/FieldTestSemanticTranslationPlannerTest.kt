package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.TranslationPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTestSemanticTranslationPlannerTest {
    private fun result() = NvidiaSttResult(
        transcript = "The first idea continues, with more context before it ends. Another idea follows clearly.",
        words = listOf(
            NvidiaWord("The", 0, 180, 0.9),
            NvidiaWord("first", 200, 420, 0.9),
            NvidiaWord("idea", 440, 650, 0.9),
            NvidiaWord("continues,", 670, 1_050, 0.9),
            NvidiaWord("with", 1_100, 1_300, 0.9),
            NvidiaWord("more", 1_320, 1_520, 0.9),
            NvidiaWord("context", 1_540, 1_820, 0.9),
            NvidiaWord("before", 1_840, 2_080, 0.9),
            NvidiaWord("it", 2_100, 2_200, 0.9),
            NvidiaWord("ends.", 2_220, 2_520, 0.9),
            NvidiaWord("Another", 2_900, 3_220, 0.9),
            NvidiaWord("idea", 3_240, 3_440, 0.9),
            NvidiaWord("follows", 3_460, 3_720, 0.9),
            NvidiaWord("clearly.", 3_740, 4_020, 0.9),
        ),
        httpStatus = 200,
    )

    @Test fun conservesEveryWordExactlyOnceAndInOrder() {
        val source = result()
        val planned = FieldTestSemanticTranslationPlanner.plan(source)
        val expectedIds = LegacySubtitleBridge.sourceWords(source.words).map { it.id }
        val actualIds = planned.flatMap { it.semanticUnit.orderedWordIds }

        assertEquals(expectedIds, actualIds)
        assertEquals(actualIds.size, actualIds.toSet().size)
    }

    @Test fun producesMillisecondAlignedPresentationUnitsAndSelfConsistentRequests() {
        val planned = FieldTestSemanticTranslationPlanner.plan(result())

        assertTrue(planned.isNotEmpty())
        planned.forEach { unit ->
            assertTrue(unit.legacyUnit.startMs >= 0L)
            assertTrue(unit.legacyUnit.endMs > unit.legacyUnit.startMs)
            assertEquals(unit.semanticUnit.sourceText, unit.legacyUnit.sourceText)
            assertEquals(unit.semanticUnit.id, unit.legacyUnit.id)
            assertTrue(TranslationPlanner.isRequestPlanSelfConsistent(unit.requestPlan))
            assertTrue(LegacyParityTranslationPlanner.isLegacyAcceptanceSignatureValid(unit.requestPlan))
        }
    }

    @Test fun keepsFrozenLegacyPlannerAvailableForRegressionComparison() {
        val source = result()
        val frozen = LegacyParityTranslationPlanner.plan(source)
        val field = FieldTestSemanticTranslationPlanner.plan(source)

        assertTrue(frozen.isNotEmpty())
        assertTrue(field.isNotEmpty())
        assertTrue(frozen.all { it.semanticUnit.segmentationVersion == LegacySubtitleBridge.SEGMENTATION_VERSION })
        assertTrue(field.all { it.semanticUnit.segmentationVersion != LegacySubtitleBridge.SEGMENTATION_VERSION })
    }
}
