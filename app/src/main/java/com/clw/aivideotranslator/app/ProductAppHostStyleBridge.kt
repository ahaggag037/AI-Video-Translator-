package com.clw.aivideotranslator.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.clw.aivideotranslator.credential.AndroidAiCredentialStore
import com.clw.aivideotranslator.editor.DisplayTimingStateStore
import com.clw.aivideotranslator.editor.EditorTextStateStore
import com.clw.aivideotranslator.editor.SubtitleStyleStateStore
import com.clw.aivideotranslator.session.TranslationSessionViewModel
import com.clw.aivideotranslator.source.SourceSelectionViewModel

/**
 * Transitional product boundary while Style Studio wiring is integrated into the main host.
 * Keeping the durable store injected here prevents Activity-owned style state or accidental
 * recreation of a second writer.
 */
@Composable
internal fun ProductAppHost(
    sessionViewModel: TranslationSessionViewModel,
    sourceSelectionViewModel: SourceSelectionViewModel,
    editorTextStore: EditorTextStateStore,
    displayTimingStore: DisplayTimingStateStore,
    subtitleStyleStore: SubtitleStyleStateStore,
    credentialStore: AndroidAiCredentialStore,
) {
    remember(subtitleStyleStore) { subtitleStyleStore }
    ProductAppHost(
        sessionViewModel = sessionViewModel,
        sourceSelectionViewModel = sourceSelectionViewModel,
        editorTextStore = editorTextStore,
        displayTimingStore = displayTimingStore,
        credentialStore = credentialStore,
    )
}
