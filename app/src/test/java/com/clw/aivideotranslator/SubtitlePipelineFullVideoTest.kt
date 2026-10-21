package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlePipelineFullVideoTest {
    @Test fun explicitTimelineAllowsWordsBeyondLegacyFirstMinute() {
        val words = listOf(
            NvidiaWord("later", 120_000L, 120_500L, null),
            NvidiaWord("speech.", 120_600L, 121_000L, null),
        )
        val units = SubtitlePipeline.sourceUnits(words, maxTimelineMs = 180_000L)
        assertEquals(120_000L, units.single().startMs)
        assertEquals(121_000L, units.single().endMs)
    }

    @Test fun explicitTimelineStillRejectsPastVideoDuration() {
        val words = listOf(NvidiaWord("x", 120_000L, 181_000L, null))
        try {
            SubtitlePipeline.sourceUnits(words, maxTimelineMs = 180_000L)
            throw AssertionError("expected bounds rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun cueAssemblyUsesExplicitFullVideoBound() {
        val units = listOf(SourceUnit("u0001", 120_000L, 121_000L, "later speech"))
        val cues = SubtitlePipeline.cues(
            units = units,
            entries = listOf(TranslationEntry("u0001", "كلام لاحق")),
            maxTimelineMs = 180_000L,
        )
        assertEquals(121_000L, cues.single().endMs)
    }
}