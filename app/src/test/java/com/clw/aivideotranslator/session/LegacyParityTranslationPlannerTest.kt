package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.semantic.LegacySubtitleBridge
import com.clw.aivideotranslator.semantic.TranslationPlanner
import org.junit.Assert.*
import org.junit.Test

class LegacyParityTranslationPlannerTest {
    private fun result() = NvidiaSttResult(
        transcript = "Hello world. This is a durable test.",
        words = listOf(
            NvidiaWord("Hello", 0, 200, 0.9),
            NvidiaWord("world.", 220, 500, 0.9),
            NvidiaWord("This", 900, 1_050, 0.9),
            NvidiaWord("is", 1_070, 1_150, 0.9),
            NvidiaWord("a", 1_170, 1_220, 0.9),
            NvidiaWord("durable", 1_240, 1_500, 0.9),
            NvidiaWord("test.", 1_520, 1_800, 0.9),
        ),
        httpStatus = 200,
    )

    @Test fun exactP0fSegmentationOrderTextAndIdentityArePreserved() {
        val source = result()
        val legacy = SubtitlePipeline.sourceUnits(source.words)
        val planned = LegacyParityTranslationPlanner.plan(source)

        assertEquals(legacy.size, planned.size)
        assertEquals(legacy, planned.map { it.legacyUnit })
        assertEquals(legacy.map { it.id }, planned.map { it.requestPlan.unitId })
        assertEquals(legacy.map { it.sourceText }, planned.map { it.requestPlan.exactSourceText })
        assertTrue(planned.all { it.semanticUnit.segmentationVersion == LegacySubtitleBridge.SEGMENTATION_VERSION })
        assertTrue(planned.all { TranslationPlanner.isRequestPlanSelfConsistent(it.requestPlan) })
        assertTrue(planned.all { LegacyParityTranslationPlanner.isLegacyAcceptanceSignatureValid(it.requestPlan) })
    }

    @Test fun everyLegacyWordIdIsConsumedOnceAndInOrder() {
        val source = result()
        val planned = LegacyParityTranslationPlanner.plan(source)
        val ids = planned.flatMap { it.semanticUnit.orderedWordIds }
        assertEquals(LegacySubtitleBridge.sourceWords(source.words).map { it.id }, ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun plannerDoesNotReplaceLegacyGapOrSentenceBoundaries() {
        val planned = LegacyParityTranslationPlanner.plan(result())
        assertEquals(2, planned.size)
        assertEquals("Hello world.", planned[0].requestPlan.exactSourceText)
        assertEquals("This is a durable test.", planned[1].requestPlan.exactSourceText)
        assertEquals(0L, planned[0].legacyUnit.startMs)
        assertEquals(500L, planned[0].legacyUnit.endMs)
        assertEquals(900L, planned[1].legacyUnit.startMs)
        assertEquals(1_800L, planned[1].legacyUnit.endMs)
    }

    @Test fun durablePlanCarriesNoTimingFieldsAndNoSemanticSegmentationSwitch() {
        val planned = LegacyParityTranslationPlanner.plan(result())
        planned.forEach { unit ->
            val encoded = TranslationRequestPlanCodec.encode(unit.requestPlan)
            assertFalse(encoded.contains("startMs"))
            assertFalse(encoded.contains("endMs"))
            assertFalse(encoded.contains("sourceInterval"))
            assertFalse(encoded.contains("audioInterval"))
            assertEquals(unit.legacyUnit.id, unit.requestPlan.unitId)
        }
    }

    @Test fun acceptanceSignatureTamperIsRejectedByLegacyAdoptionPolicy() {
        val plan = LegacyParityTranslationPlanner.plan(result()).first().requestPlan
        assertFalse(
            LegacyParityTranslationPlanner.isLegacyAcceptanceSignatureValid(
                plan.copy(acceptanceSignature = "0".repeat(64)),
            )
        )
    }
}
