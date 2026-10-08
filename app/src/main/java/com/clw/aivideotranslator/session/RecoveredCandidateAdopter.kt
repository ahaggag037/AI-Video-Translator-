package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationRecordEditor
import com.clw.aivideotranslator.semantic.TranslationReviewState

object RecoveredCandidateAdopter {
    fun mergeIntoHistory(
        current: TranslationRecord?,
        unitId: String,
        rawProviderText: String,
        requestSignature: String,
        machineRevisionId: String,
    ): TranslationRecord {
        require(unitId.isNotBlank() && rawProviderText.isNotBlank())
        require(requestSignature.isNotBlank() && machineRevisionId.isNotBlank())
        require(current == null || current.unitId == unitId) { "candidate unit mismatch" }
        val machine = MachineTranslationRevision(
            id = machineRevisionId,
            text = rawProviderText,
            requestSignature = requestSignature,
        )
        return if (current == null) {
            TranslationRecord(
                unitId = unitId,
                machineRevisions = listOf(machine),
                activeMachineRevisionId = machine.id,
                manualRevision = null,
                reviewState = TranslationReviewState.MACHINE_CANDIDATE,
            )
        } else {
            TranslationRecordEditor.addMachineCandidate(current, machine, adoptAsActive = true)
        }
    }
}
