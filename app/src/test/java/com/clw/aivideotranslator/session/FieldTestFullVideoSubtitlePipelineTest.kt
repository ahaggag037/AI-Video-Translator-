package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTestFullVideoSubtitlePipelineTest {
    @Test fun acceptsCueBeyondLegacyFirstMinuteWhenInsideVideo() {
        val units = listOf(
            SourceUnit("semantic-1", 65_000L, 67_500L, "A sentence after minute one."),
            SourceUnit("semantic-2", 67_500L, 71_250L, "Another sentence."),
        )
        val entries = listOf(
            TranslationEntry("semantic-1", "جملة بعد الدقيقة الأولى."),
            TranslationEntry("semantic-2", "جملة أخرى."),
        )

        val cues = FieldTestFullVideoSubtitlePipeline.cues(units, entries, videoDurationMs = 90_000L)
        assertEquals(65_000L, cues.first().startMs)
        assertEquals(71_250L, cues.last().endMs)

        val srt = FieldTestFullVideoSubtitlePipeline.srt(cues, videoDurationMs = 90_000L)
        assertTrue(srt.contains("00:01:05,000 --> 00:01:07,500"))
        assertTrue(srt.contains("جملة بعد الدقيقة الأولى."))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCueBeyondActualVideoDuration() {
        FieldTestFullVideoSubtitlePipeline.cues(
            units = listOf(SourceUnit("semantic-1", 88_000L, 91_000L, "too late")),
            entries = listOf(TranslationEntry("semantic-1", "متأخر")),
            videoDurationMs = 90_000L,
        )
    }
}
