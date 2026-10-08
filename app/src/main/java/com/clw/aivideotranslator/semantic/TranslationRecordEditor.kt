package com.clw.aivideotranslator.semantic

object TranslationRecordEditor {
    fun addMachineCandidate(
        record: TranslationRecord,
        candidate: MachineTranslationRevision,
        adoptAsActive: Boolean,
    ): TranslationRecord {
        require(record.machineRevisions.none { it.id == candidate.id }) { "duplicate machine revision id" }
        return record.copy(
            machineRevisions = record.machineRevisions + candidate,
            activeMachineRevisionId = if (adoptAsActive) candidate.id else record.activeMachineRevisionId,
            reviewState = when {
                record.manualRevision != null -> record.reviewState
                adoptAsActive -> TranslationReviewState.MACHINE_CANDIDATE
                else -> record.reviewState
            },
        )
    }

    fun applyManualRevision(
        record: TranslationRecord,
        manual: ManualTranslationRevision,
    ): TranslationRecord = record.copy(
        manualRevision = manual,
        reviewState = TranslationReviewState.APPROVED,
    )

    fun restoreMachine(record: TranslationRecord): TranslationRecord {
        require(record.activeMachineRevisionId != null) { "no active machine revision" }
        return record.copy(
            manualRevision = null,
            reviewState = TranslationReviewState.MACHINE_CANDIDATE,
        )
    }

    fun onSourceTextChanged(record: TranslationRecord): TranslationRecord =
        if (record.manualRevision != null) {
            record.copy(reviewState = TranslationReviewState.REBASE_REQUIRED)
        } else {
            record.copy(activeMachineRevisionId = null, reviewState = TranslationReviewState.REVIEW_REQUIRED)
        }

    fun onStyleOrLayoutChanged(record: TranslationRecord): TranslationRecord = record
}
