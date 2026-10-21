package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.TimingFieldDraft
import com.clw.aivideotranslator.editor.UnitDisplayTimingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingEditorProjectionTest {
    private val source = DisplayTimingInterval(
        startUs = 1_000_000L,
        endUs = 2_500_000L,
    )

    @Test fun committedOverrideIsShownWhenNoDraftExists() {
        val committed = DisplayTimingInterval(1_100_000L, 2_400_000L)

        val presentation = TimingEditorProjection.resolve(
            sourceInterval = source,
            state = UnitDisplayTimingState(committedOverride = committed),
            videoDurationUs = 5_000_000L,
            previousEffectiveEndUs = 900_000L,
            nextEffectiveStartUs = 3_000_000L,
            sourceAvailable = true,
        )

        assertEquals("00:01.100", presentation.startField)
        assertEquals("00:02.400", presentation.endField)
        assertFalse(presentation.dirty)
        assertTrue(presentation.validation.isValid)
    }

    @Test fun pendingDraftIsShownAndMarkedDirty() {
        val draft = DisplayTimingInterval(1_200_000L, 2_300_000L)

        val presentation = TimingEditorProjection.resolve(
            sourceInterval = source,
            state = UnitDisplayTimingState(pendingDraft = draft),
            videoDurationUs = 5_000_000L,
            previousEffectiveEndUs = 900_000L,
            nextEffectiveStartUs = 3_000_000L,
            sourceAvailable = true,
        )

        assertEquals("00:01.200", presentation.startField)
        assertEquals("00:02.300", presentation.endField)
        assertTrue(presentation.dirty)
        assertTrue(presentation.validation.isValid)
    }

    @Test fun rawInvalidFieldDraftIsShownExactlyAndValidatedWithoutBecomingIntervalTruth() {
        val presentation = TimingEditorProjection.resolve(
            sourceInterval = source,
            state = UnitDisplayTimingState(
                pendingFieldDraft = TimingFieldDraft(
                    startText = "00:03.000",
                    endText = "bad",
                ),
            ),
            videoDurationUs = 5_000_000L,
            previousEffectiveEndUs = 900_000L,
            nextEffectiveStartUs = 4_000_000L,
            sourceAvailable = true,
        )

        assertEquals("00:03.000", presentation.startField)
        assertEquals("bad", presentation.endField)
        assertTrue(presentation.dirty)
        assertFalse(presentation.validation.isValid)
        assertTrue(TimingDraftError.END_INVALID in presentation.validation.errors)
    }

    @Test fun neighborOverlapIsReflectedInPresentationValidation() {
        val draft = DisplayTimingInterval(800_000L, 2_300_000L)

        val presentation = TimingEditorProjection.resolve(
            sourceInterval = source,
            state = UnitDisplayTimingState(pendingDraft = draft),
            videoDurationUs = 5_000_000L,
            previousEffectiveEndUs = 900_000L,
            nextEffectiveStartUs = 3_000_000L,
            sourceAvailable = true,
        )

        assertTrue(TimingDraftError.OVERLAPS_PREVIOUS in presentation.validation.errors)
        assertFalse(presentation.validation.isValid)
    }

    @Test fun sourceAvailabilityDisablesTimingSurfaceWithoutChangingFields() {
        val presentation = TimingEditorProjection.resolve(
            sourceInterval = source,
            state = UnitDisplayTimingState(),
            videoDurationUs = null,
            previousEffectiveEndUs = null,
            nextEffectiveStartUs = null,
            sourceAvailable = false,
        )

        assertEquals("00:01.000", presentation.startField)
        assertEquals("00:02.500", presentation.endField)
        assertFalse(presentation.sourceAvailable)
    }

    @Test fun parserRoundTripsUiTimecodeToMicroseconds() {
        assertEquals(62_345_000L, TimingEditorProjection.parseTimecodeUs("01:02.345"))
        assertEquals("01:02.345", TimingEditorProjection.formatTimecodeUs(62_345_000L))
    }
}
