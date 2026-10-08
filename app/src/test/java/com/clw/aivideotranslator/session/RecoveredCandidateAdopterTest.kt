package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.junit.Assert.*
import org.junit.Test

class RecoveredCandidateAdopterTest {
    @Test fun createsMachineHistoryWhenNoPriorRecord() {
        val merged = RecoveredCandidateAdopter.mergeIntoHistory(
            current = null,
            unitId = "u1",
            rawProviderText = "ترجمة جديدة",
            requestSignature = "sig-new",
            machineRevisionId = "m-new",
        )
        assertEquals("ترجمة جديدة", merged.effectiveText())
        assertEquals(TranslationReviewState.MACHINE_CANDIDATE, merged.reviewState)
    }

    @Test fun recoveredCandidateNeverOverwritesManualTruth() {
        val oldMachine = MachineTranslationRevision("m-old", "ترجمة قديمة", "sig-old")
        val manual = ManualTranslationRevision("manual-1", "تصحيح يدوي", "source-hash", "m-old")
        val current = TranslationRecord(
            unitId = "u1",
            machineRevisions = listOf(oldMachine),
            activeMachineRevisionId = oldMachine.id,
            manualRevision = manual,
            reviewState = TranslationReviewState.APPROVED,
        )
        val merged = RecoveredCandidateAdopter.mergeIntoHistory(
            current = current,
            unitId = "u1",
            rawProviderText = "ترجمة جديدة",
            requestSignature = "sig-new",
            machineRevisionId = "m-new",
        )
        assertEquals(2, merged.machineRevisions.size)
        assertEquals("m-new", merged.activeMachineRevisionId)
        assertEquals("تصحيح يدوي", merged.effectiveText())
        assertEquals(manual, merged.manualRevision)
        assertEquals(TranslationReviewState.APPROVED, merged.reviewState)
    }
}
