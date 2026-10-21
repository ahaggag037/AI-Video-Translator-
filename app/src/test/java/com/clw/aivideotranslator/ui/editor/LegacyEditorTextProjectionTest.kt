package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.editor.ManualTextRevision
import com.clw.aivideotranslator.editor.UnitEditorTextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyEditorTextProjectionTest {
    private val unit = SourceUnit(
        id = "u1",
        startMs = 1000L,
        endMs = 2500L,
        sourceText = "hello",
        wordStartIndex = 0,
        wordEndIndexExclusive = 1,
    )
    private val machine = TranslationEntry("u1", "مرحبا")

    @Test fun activeManualRevisionOverridesMachineText() {
        val manual = UnitEditorTextState(
            revisions = listOf(
                ManualTextRevision("m1", "تصحيح", "source-hash", "machine-hash"),
            ),
            activeRevisionIndex = 0,
        )

        val presentation = LegacyEditorTextProjection.resolve(
            sourceUnit = unit,
            machineEntry = machine,
            editorState = manual,
            sourceAvailable = true,
        )

        assertEquals("hello", presentation.sourceText)
        assertEquals("تصحيح", presentation.targetText)
        assertFalse(presentation.hasPendingDraft)
        assertTrue(presentation.srtExportEnabled)
        assertTrue(presentation.mp4ExportEnabled)
    }

    @Test fun pendingDraftIsVisibleButDoesNotChangeEligibilityFacts() {
        val state = UnitEditorTextState(pendingDraft = "مسودة")
        val presentation = LegacyEditorTextProjection.resolve(
            sourceUnit = unit,
            machineEntry = machine,
            editorState = state,
            sourceAvailable = true,
        )

        assertEquals("مسودة", presentation.targetText)
        assertTrue(presentation.hasPendingDraft)
        assertTrue(presentation.srtExportEnabled)
        assertFalse(presentation.hasProviderAction)
    }

    @Test fun recoveredTextWithoutSourceEntersTextOnlyDegradedMode() {
        val presentation = LegacyEditorTextProjection.resolve(
            sourceUnit = null,
            machineEntry = machine,
            editorState = UnitEditorTextState(),
            sourceAvailable = false,
        )

        assertEquals("مرحبا", presentation.targetText)
        assertTrue(presentation.sourceMissing)
        assertTrue(presentation.textEditingEnabled)
        assertTrue(presentation.srtExportEnabled)
        assertFalse(presentation.playbackEnabled)
        assertFalse(presentation.timingEnabled)
        assertFalse(presentation.styleEnabled)
        assertFalse(presentation.fullscreenPreviewEnabled)
        assertFalse(presentation.mp4ExportEnabled)
    }
}
