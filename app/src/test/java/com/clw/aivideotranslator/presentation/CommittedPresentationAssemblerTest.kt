package com.clw.aivideotranslator.presentation

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.DurableDisplayTimingState
import com.clw.aivideotranslator.editor.DurableEditorTextState
import com.clw.aivideotranslator.editor.ManualTextRevision
import com.clw.aivideotranslator.editor.TimingFieldDraft
import com.clw.aivideotranslator.editor.UnitDisplayTimingState
import com.clw.aivideotranslator.editor.UnitEditorTextState
import org.junit.Assert.assertEquals
import org.junit.Test

class CommittedPresentationAssemblerTest {
    private val units = listOf(
        SourceUnit("u0001", 1_000L, 2_000L, "hello"),
        SourceUnit("u0002", 2_500L, 3_500L, "world"),
    )
    private val entries = listOf(
        TranslationEntry("u0001", "مرحبا"),
        TranslationEntry("u0002", "بالعالم"),
    )

    @Test fun pendingTextAndTimingNeverLeakIntoPresentationTruth() {
        val text = DurableEditorTextState(
            sessionId = "s1",
            units = mapOf(
                "u0001" to UnitEditorTextState(pendingDraft = "مسودة غير ملتزمة"),
            ),
        )
        val timing = DurableDisplayTimingState(
            sessionId = "s1",
            units = mapOf(
                "u0001" to UnitDisplayTimingState(
                    pendingDraft = DisplayTimingInterval(1_200_000L, 2_200_000L),
                    pendingFieldDraft = TimingFieldDraft("00:00:01,200", "00:00:02,200"),
                ),
            ),
        )

        val cues = CommittedPresentationAssembler.cues(units, entries, text, timing)
        assertEquals("مرحبا", cues[0].text)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(2_000L, cues[0].endMs)
    }

    @Test fun committedManualRevisionAndTimingOverrideBecomePresentationTruth() {
        val revision = ManualTextRevision(
            id = "manual-1",
            text = "النص المحرر",
            basedOnSourceTextHash = "source-hash",
            basedOnMachineTextHash = "machine-hash",
        )
        val text = DurableEditorTextState(
            sessionId = "s1",
            units = mapOf(
                "u0001" to UnitEditorTextState(
                    revisions = listOf(revision),
                    activeRevisionIndex = 0,
                ),
            ),
        )
        val timing = DurableDisplayTimingState(
            sessionId = "s1",
            units = mapOf(
                "u0001" to UnitDisplayTimingState(
                    committedOverride = DisplayTimingInterval(1_100_000L, 2_100_000L),
                ),
            ),
        )

        val cues = CommittedPresentationAssembler.cues(units, entries, text, timing)
        assertEquals("النص المحرر", cues[0].text)
        assertEquals(1_100L, cues[0].startMs)
        assertEquals(2_100L, cues[0].endMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlappingCommittedIntervalsFailClosed() {
        val timing = DurableDisplayTimingState(
            sessionId = "s1",
            units = mapOf(
                "u0002" to UnitDisplayTimingState(
                    committedOverride = DisplayTimingInterval(1_900_000L, 3_500_000L),
                ),
            ),
        )
        CommittedPresentationAssembler.cues(
            units,
            entries,
            DurableEditorTextState("s1"),
            timing,
        )
    }
}