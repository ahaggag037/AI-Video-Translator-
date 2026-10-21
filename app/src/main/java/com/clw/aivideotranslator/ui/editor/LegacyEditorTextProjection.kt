package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.editor.UnitEditorTextState

/**
 * Temporary Path-A adapter from the existing translation engine to the final Editor/Text surface.
 * Manual truth comes from the durable editor sidecar; provider/machine truth stays immutable here.
 */
internal object LegacyEditorTextProjection {
    fun resolve(
        sourceUnit: SourceUnit?,
        machineEntry: TranslationEntry,
        editorState: UnitEditorTextState,
        sourceAvailable: Boolean,
        reviewRequired: Boolean = false,
    ): EditorTextPresentation {
        val committedExportText = editorState.exportText(machineEntry.translatedText)
        return EditorTextPresentation(
            sourceText = sourceUnit?.sourceText.orEmpty(),
            targetText = editorState.visibleText(machineEntry.translatedText),
            hasPendingDraft = editorState.pendingDraft != null,
            reviewRequired = reviewRequired,
            sourceMissing = !sourceAvailable,
            textEditingEnabled = true,
            playbackEnabled = sourceAvailable,
            fullscreenPreviewEnabled = sourceAvailable,
            timingEnabled = sourceAvailable,
            styleEnabled = sourceAvailable,
            srtExportEnabled = committedExportText.isNotBlank(),
            mp4ExportEnabled = sourceAvailable && committedExportText.isNotBlank(),
            hasProviderAction = false,
        )
    }
}
