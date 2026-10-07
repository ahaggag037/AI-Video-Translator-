package com.clw.aivideotranslator

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SubtitlePipelineTest {
    private fun word(text: String, start: Long?, end: Long?) = NvidiaWord(text, start, end, null)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }

    @Test fun boundariesAndIdsAreDeterministicAndPreserveWords() {
        val words = listOf(word("Hello", 80, 200), word("world.", 220, 800),
            word("Next", 1500, 1800), word("sentence", 1800, 2200), word("ends.", 2200, 2600))
        val units = SubtitlePipeline.sourceUnits(words)
        assertEquals(listOf(SourceUnit("u0001", 80, 800, "Hello world."),
            SourceUnit("u0002", 1500, 2600, "Next sentence ends.")), units)
        assertEquals(units, SubtitlePipeline.sourceUnits(words))
        assertEquals(words.joinToString(" ") { it.text }, units.joinToString(" ") { it.sourceText })
    }

    @Test fun pauseAndWordLimitSplitWithoutRetiming() {
        val pause = SubtitlePipeline.sourceUnits(listOf(word("a", 80, 100), word("b", 800, 1000)))
        assertEquals(2, pause.size)
        val words = (0..16).map { word("word", it * 100L, it * 100L + 80) }
        val units = SubtitlePipeline.sourceUnits(words)
        assertEquals(2, units.size)
        assertEquals(1580L, units.first().endMs)
        assertEquals(1600L, units.last().startMs)
    }

    @Test fun durationAndTextLimitsSplitAtOriginalWordBoundaries() {
        val duration = SubtitlePipeline.sourceUnits((0..6).map { word("x", it * 1000L, it * 1000L + 900) })
        assertEquals(5900L, duration.first().endMs)
        assertEquals(6000L, duration.last().startMs)
        val chars = SubtitlePipeline.sourceUnits(listOf(word("x".repeat(90), 0, 100),
            word("y".repeat(90), 100, 200)))
        assertEquals(2, chars.size)
    }

    @Test fun invalidTimingsFailInsteadOfInventingOrClamping() {
        rejects { SubtitlePipeline.sourceUnits(emptyList()) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", null, 100))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", 80, null))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", -1, 100))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", 100, 100))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", 80, 60_001))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word("x", 80, 200), word("y", 150, 300))) }
        rejects { SubtitlePipeline.sourceUnits(listOf(word(" ", 80, 200))) }
    }

    @Test fun completeSampleKeepsFirstAndLastOffsetsThroughTranslationAndSrt() {
        // Synthetic timing fixture, NOT the user's real 217-word transcript.
        val words = (0..216).map { i ->
            word("word", 80 + i * 270L, if (i == 216) 60_000 else 80 + i * 270L + 200)
        }
        val units = SubtitlePipeline.sourceUnits(words)
        val entries = units.map { TranslationEntry(it.id, "نص عربي") }.reversed()
        val cues = SubtitlePipeline.cues(units, entries)
        units.zip(cues).forEach { (unit, cue) ->
            assertEquals(unit.id, cue.sourceUnitId)
            assertEquals(unit.startMs, cue.startMs)
            assertEquals(unit.endMs, cue.endMs)
        }
        assertEquals(80L, cues.first().startMs)
        assertEquals(60_000L, cues.last().endMs)
        assertTrue(SubtitlePipeline.srt(cues).contains("00:01:00,000"))
    }

    @Test fun missingDuplicateAndUnknownIdsAreRejected() {
        val units = listOf(SourceUnit("u1", 80, 1000, "Hello"), SourceUnit("u2", 1100, 2000, "World"))
        rejects { SubtitlePipeline.cues(units, listOf(TranslationEntry("u1", "أهلًا"))) }
        rejects { SubtitlePipeline.cues(units, listOf(TranslationEntry("u1", "أهلًا"), TranslationEntry("u1", "أهلًا"))) }
        rejects { SubtitlePipeline.cues(units, listOf(TranslationEntry("u1", "أهلًا"), TranslationEntry("other", "أهلًا"))) }
    }

    @Test fun srtUsesAsciiClockAndUtf8ArabicAndNoInjectedBlocks() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val srt = SubtitlePipeline.srt(listOf(ArabicSubtitleCue("u1", 80, 60_000, "مرحبًا\n\nبكم")))
            assertEquals("1\n00:00:00,080 --> 00:01:00,000\nمرحبًا بكم\n\n", srt)
            assertEquals(srt, srt.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
            rejects { SubtitlePipeline.validateText("00:00:01,000 --> 00:00:02,000") }
            rejects { SubtitlePipeline.validateText(" ") }
        } finally { Locale.setDefault(old) }
    }
}
