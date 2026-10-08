package com.clw.aivideotranslator.semantic

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SourceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class LegacySubtitleBridgeTest {
    @Test fun legacyMillisecondsConvertExactlyWithoutInventingPrecision() {
        val words = LegacySubtitleBridge.sourceWords(
            listOf(NvidiaWord("Hello", 80, 400, 0.9), NvidiaWord("world", 500, 60_000, null))
        )
        assertEquals(80_000L, words.first().audioInterval.start.value)
        assertEquals(60_000_000L, words.last().audioInterval.end.value)
        assertEquals(1_000L, words.first().timingPrecisionUs)
        assertEquals("Hello", words.first().rawText)
    }

    @Test fun explicitSampleOriginMapsLegacyUnitsAndCuesWithoutRetiming() {
        val unit = LegacySubtitleBridge.semanticUnit(
            SourceUnit("u1", 80, 5_280, "Hello world."),
            orderedWordIds = listOf("w000001", "w000002"),
            sampleStartUs = 120_000_000L,
        )
        assertEquals(120_080_000L, unit.sourceInterval.start.value)
        assertEquals(125_280_000L, unit.sourceInterval.end.value)
        assertEquals("Hello world.", unit.sourceText)

        val cue = LegacySubtitleBridge.semanticCue(
            ArabicSubtitleCue("u1", 80, 5_280, "مرحبًا بالعالم."),
            sampleStartUs = 120_000_000L,
            effectiveRevisionId = "manual-1",
        )
        assertEquals(unit.sourceInterval, cue.speechInterval)
        assertEquals("مرحبًا بالعالم.", cue.text)
    }

    @Test fun checkedLegacyConversionRejectsOverflow() {
        try {
            LegacySubtitleBridge.legacyMsToUs(Long.MAX_VALUE)
            fail("Expected checked overflow")
        } catch (_: ArithmeticException) {
            // expected
        }
    }

    @Test fun manualRevisionIsEffectiveWithoutDestroyingMachineCandidate() {
        val machine = MachineTranslationRevision("machine-1", "ترجمة آلية", "sig-1")
        val manual = ManualTranslationRevision("manual-1", "تصحيح يدوي", "source-hash", machine.id)
        val record = TranslationRecord(
            unitId = "u1",
            machineRevisions = listOf(machine),
            activeMachineRevisionId = machine.id,
            manualRevision = manual,
            reviewState = TranslationReviewState.APPROVED,
        )
        assertEquals("تصحيح يدوي", record.effectiveText())
        assertEquals("ترجمة آلية", record.machineRevisions.single().text)
        assertNotEquals(record.manualRevision?.text, record.machineRevisions.single().text)
    }
}
