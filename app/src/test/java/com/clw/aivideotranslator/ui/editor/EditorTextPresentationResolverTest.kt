package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextPresentationResolverTest {
    private val source = SemanticSourceUnit(
        id = "u1",
        orderedWordIds = listOf("w1"),
        sourceText = "hello world",
        sourceTextHash = "source-hash",
        sourceInterval = PresentationIntervalUs(
            start = PresentationTimeUs(0L),
            end = PresentationTimeUs(1_000_000L),
        ),
        segmentationVersion = "v1",
    )

    private fun record(
        manualText: String? = null,
        reviewState: TranslationReviewState = TranslationReviewState.MACHINE_CANDIDATE,
    ): TranslationRecord = TranslationRecord(
        unitId = "u1",
        machineRevisions = listOf(
            MachineTranslationRevision("m1", "مرحبا بالعالم", "sig1"),
        ),
        activeMachineRevisionId = "m1",
        manualRevision = manualText?.let {
            ManualTranslationRevision(
                id = "manual-1",
                text = it,
                basedOnSourceTextHash = "source-hash",
                basedOnMachineRevisionId = "m1",
            )
        },
        reviewState = reviewState,
    )

    @Test fun committedManualRevisionIsDisplayedAsAuthoritativeTarget() {
        val presentation = EditorTextPresentationResolver.resolve(
            sourceUnit = source,
            record = record(
                manualText = "تصحيح يدوي",
                reviewState = TranslationReviewState.APPROVED,
            ),
            pendingDraftText = null,
            sourceAvailable = true,
        )

        assertEquals("hello world", presentation.sourceText)
        assertEquals("تصحيح يدوي", presentation.targetText)
        assertFalse(presentation.hasPendingDraft)
        assertFalse(presentation.reviewRequired)
    }

    @Test fun pendingDraftIsVisibleWithoutBecomingCommittedTruth() {
        val durableRecord = record(
            manualText = "النص المحفوظ",
            reviewState = TranslationReviewState.APPROVED,
        )

        val presentation = EditorTextPresentationResolver.resolve(
            sourceUnit = source,
            record = durableRecord,
            pendingDraftText = "مسودة لم تحفظ بعد",
            sourceAvailable = true,
        )

        assertEquals("مسودة لم تحفظ بعد", presentation.targetText)
        assertTrue(presentation.hasPendingDraft)
        assertEquals("النص المحفوظ", durableRecord.effectiveText())
    }

    @Test fun missingSourceKeepsTextAndSrtProductiveButDisablesMediaTools() {
        val presentation = EditorTextPresentationResolver.resolve(
            sourceUnit = source,
            record = record(
                manualText = "ترجمة محفوظة",
                reviewState = TranslationReviewState.APPROVED,
            ),
            pendingDraftText = null,
            sourceAvailable = false,
        )

        assertTrue(presentation.textEditingEnabled)
        assertTrue(presentation.srtExportEnabled)
        assertFalse(presentation.playbackEnabled)
        assertFalse(presentation.fullscreenPreviewEnabled)
        assertFalse(presentation.timingEnabled)
        assertFalse(presentation.styleEnabled)
        assertFalse(presentation.mp4ExportEnabled)
        assertTrue(presentation.sourceMissing)
    }

    @Test fun machineCandidateRemainsReviewRequiredButDoesNotTriggerProviderAction() {
        val presentation = EditorTextPresentationResolver.resolve(
            sourceUnit = source,
            record = record(),
            pendingDraftText = null,
            sourceAvailable = true,
        )

        assertEquals("مرحبا بالعالم", presentation.targetText)
        assertTrue(presentation.reviewRequired)
        assertFalse(presentation.hasProviderAction)
    }
}
