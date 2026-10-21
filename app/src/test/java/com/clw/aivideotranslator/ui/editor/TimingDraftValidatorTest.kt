package com.clw.aivideotranslator.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingDraftValidatorTest {
    @Test fun acceptsValidNonOverlappingInterval() {
        val result = TimingDraftValidator.validate(
            draft = TimingDraft(startUs = 1_000_000L, endUs = 2_000_000L),
            videoDurationUs = 10_000_000L,
            previousEffectiveEndUs = 900_000L,
            nextEffectiveStartUs = 2_100_000L,
        )

        assertTrue(result.isValid)
        assertEquals(emptySet<TimingDraftError>(), result.errors)
    }

    @Test fun rejectsEqualOrReversedEndpoints() {
        val equal = TimingDraftValidator.validate(TimingDraft(5L, 5L))
        val reversed = TimingDraftValidator.validate(TimingDraft(6L, 5L))

        assertFalse(equal.isValid)
        assertTrue(TimingDraftError.START_NOT_BEFORE_END in equal.errors)
        assertFalse(reversed.isValid)
        assertTrue(TimingDraftError.START_NOT_BEFORE_END in reversed.errors)
    }

    @Test fun rejectsNegativeStart() {
        val result = TimingDraftValidator.validate(TimingDraft(-1L, 10L))

        assertFalse(result.isValid)
        assertTrue(TimingDraftError.NEGATIVE_START in result.errors)
    }

    @Test fun rejectsEndBeyondKnownVideoDuration() {
        val result = TimingDraftValidator.validate(
            draft = TimingDraft(0L, 1_000_001L),
            videoDurationUs = 1_000_000L,
        )

        assertFalse(result.isValid)
        assertTrue(TimingDraftError.AFTER_VIDEO_END in result.errors)
    }

    @Test fun rejectsOverlapWithPreviousEffectiveCue() {
        val result = TimingDraftValidator.validate(
            draft = TimingDraft(999L, 2_000L),
            previousEffectiveEndUs = 1_000L,
        )

        assertFalse(result.isValid)
        assertTrue(TimingDraftError.OVERLAPS_PREVIOUS in result.errors)
    }

    @Test fun rejectsOverlapWithNextEffectiveCue() {
        val result = TimingDraftValidator.validate(
            draft = TimingDraft(1_000L, 2_001L),
            nextEffectiveStartUs = 2_000L,
        )

        assertFalse(result.isValid)
        assertTrue(TimingDraftError.OVERLAPS_NEXT in result.errors)
    }

    @Test fun doesNotInventMinimumCueDuration() {
        val result = TimingDraftValidator.validate(
            draft = TimingDraft(123L, 124L),
            videoDurationUs = 1_000L,
        )

        assertTrue(result.isValid)
    }
}
