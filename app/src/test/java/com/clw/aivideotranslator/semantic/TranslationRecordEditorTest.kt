package com.clw.aivideotranslator.semantic

import org.junit.Assert.*
import org.junit.Test

class TranslationRecordEditorTest {
    private val machine1 = MachineTranslationRevision("m1", "ترجمة آلية", "sig1")
    private val manual = ManualTranslationRevision("manual1", "تصحيح يدوي", "source-hash", "m1")

    private fun record(withManual: Boolean = true) = TranslationRecord(
        unitId = "u1",
        machineRevisions = listOf(machine1),
        activeMachineRevisionId = "m1",
        manualRevision = if (withManual) manual else null,
        reviewState = if (withManual) TranslationReviewState.APPROVED else TranslationReviewState.MACHINE_CANDIDATE,
    )

    @Test fun retranslateCreatesCandidateWithoutOverwritingManualTruth() {
        val original = record()
        val revised = TranslationRecordEditor.addMachineCandidate(
            original,
            MachineTranslationRevision("m2", "مرشح جديد", "sig2"),
            adoptAsActive = true,
        )
        assertEquals("تصحيح يدوي", revised.effectiveText())
        assertEquals("m2", revised.activeMachineRevisionId)
        assertEquals(TranslationReviewState.APPROVED, revised.reviewState)
        assertEquals(2, revised.machineRevisions.size)
    }

    @Test fun styleChangeCannotInvalidateTranslationOrManualEdit() {
        val original = record()
        assertSame(original, TranslationRecordEditor.onStyleOrLayoutChanged(original))
        assertEquals("تصحيح يدوي", original.effectiveText())
    }

    @Test fun sourceTextChangeRetainsManualRevisionButMarksRebaseRequired() {
        val revised = TranslationRecordEditor.onSourceTextChanged(record())
        assertEquals(manual, revised.manualRevision)
        assertEquals("تصحيح يدوي", revised.effectiveText())
        assertEquals(TranslationReviewState.REBASE_REQUIRED, revised.reviewState)
    }

    @Test fun sourceTextChangeWithoutManualInvalidatesActiveMachineCandidate() {
        val revised = TranslationRecordEditor.onSourceTextChanged(record(withManual = false))
        assertNull(revised.activeMachineRevisionId)
        assertEquals(TranslationReviewState.REVIEW_REQUIRED, revised.reviewState)
    }
}
