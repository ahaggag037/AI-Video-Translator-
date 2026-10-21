package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState

/**
 * UI-only projection for Editor Text mode. It never mutates semantic records and intentionally
 * exposes no provider/retranslation action: Text mode is local review/manual editing only.
 */
internal data class EditorTextPresentation(
    val sourceText: String,
    val targetText: String,
    val hasPendingDraft: Boolean,
    val reviewRequired: Boolean,
    val sourceMissing: Boolean,
    val textEditingEnabled: Boolean,
    val playbackEnabled: Boolean,
    val fullscreenPreviewEnabled: Boolean,
    val timingEnabled: Boolean,
    val styleEnabled: Boolean,
    val srtExportEnabled: Boolean,
    val mp4ExportEnabled: Boolean,
    val hasProviderAction: Boolean = false,
)

internal object EditorTextPresentationResolver {
    fun resolve(
        sourceUnit: SemanticSourceUnit,
        record: TranslationRecord,
        pendingDraftText: String?,
        sourceAvailable: Boolean,
    ): EditorTextPresentation {
        require(record.unitId == sourceUnit.id) {
            "translation record and source unit must share identity"
        }

        val committedText = record.effectiveText().orEmpty()
        val pending = pendingDraftText?.takeIf { it != committedText }
        val visibleTarget = pending ?: committedText
        val hasExportableText = committedText.isNotBlank()

        return EditorTextPresentation(
            sourceText = sourceUnit.sourceText,
            targetText = visibleTarget,
            hasPendingDraft = pending != null,
            reviewRequired = record.reviewState != TranslationReviewState.APPROVED,
            sourceMissing = !sourceAvailable,
            textEditingEnabled = true,
            playbackEnabled = sourceAvailable,
            fullscreenPreviewEnabled = sourceAvailable,
            timingEnabled = sourceAvailable,
            styleEnabled = sourceAvailable,
            srtExportEnabled = hasExportableText,
            mp4ExportEnabled = sourceAvailable && hasExportableText,
        )
    }
}
