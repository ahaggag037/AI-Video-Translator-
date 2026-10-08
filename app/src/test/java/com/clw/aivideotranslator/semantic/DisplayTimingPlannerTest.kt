package com.clw.aivideotranslator.semantic

import org.junit.Assert.*
import org.junit.Test

class DisplayTimingPlannerTest {
    private fun cue(start: Long, end: Long, text: String = "مرحبا بالعالم") = SemanticCue(
        id = "c1",
        sourceUnitId = "u1",
        effectiveTranslationRevisionId = "r1",
        text = text,
        speechInterval = PresentationIntervalUs(PresentationTimeUs(start), PresentationTimeUs(end)),
    )

    @Test fun extensionNeverCrossesNextKnownSpeechGuard() {
        val result = DisplayTimingPlanner.plan(
            cue(1_000_000, 2_000_000, "هذه ترجمة عربية طويلة نسبيًا للاختبار"),
            rangeEnd = PresentationTimeUs(5_000_000),
            nextKnownSpeechStart = PresentationTimeUs(2_300_000),
        )
        assertEquals(2_220_000L, result.visibleInterval.end.value)
        assertEquals(2_000_000L, result.speechInterval.end.value)
    }

    @Test fun tinyGapDoesNotShortenSpeechToManufactureGuard() {
        val result = DisplayTimingPlanner.plan(
            cue(0, 1_000_000, "نص طويل يحتاج وقتا إضافيا"),
            rangeEnd = PresentationTimeUs(2_000_000),
            nextKnownSpeechStart = PresentationTimeUs(1_020_000),
        )
        assertEquals(1_000_000L, result.visibleInterval.end.value)
    }

    @Test fun overlappingSourceSpeechFailsClosed() {
        try {
            DisplayTimingPlanner.plan(
                cue(0, 1_000_000),
                rangeEnd = PresentationTimeUs(2_000_000),
                nextKnownSpeechStart = PresentationTimeUs(900_000),
            )
            fail("overlap must be explicit")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
