package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldTestSttBatchAssemblerTest {
    @Test fun combinesWindowRelativeWordsOnOriginalTimeline() {
        val result = FieldTestSttBatchAssembler.assemble(
            listOf(
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(0, 0L, 60_000_000L),
                    NvidiaSttResult(
                        transcript = "Hello there",
                        words = listOf(NvidiaWord("Hello", 80L, 400L, 0.9)),
                        httpStatus = 200,
                    ),
                ),
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(1, 60_000_000L, 120_000_000L),
                    NvidiaSttResult(
                        transcript = "general Kenobi",
                        words = listOf(NvidiaWord("general", 250L, 700L, 0.9)),
                        httpStatus = 200,
                    ),
                ),
            ),
        )

        assertEquals("Hello there general Kenobi", result.transcript)
        assertEquals(listOf(80L, 60_250L), result.words.map { it.startMs })
        assertEquals(listOf(400L, 60_700L), result.words.map { it.endMs })
        assertEquals(200, result.httpStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingWindowIndex() {
        FieldTestSttBatchAssembler.assemble(
            listOf(
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(1, 60_000_000L, 120_000_000L),
                    NvidiaSttResult("late", listOf(NvidiaWord("late", 0L, 100L, 1.0)), 200),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUntimedProviderWord() {
        FieldTestSttBatchAssembler.assemble(
            listOf(
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(0, 0L, 60_000_000L),
                    NvidiaSttResult("hello", listOf(NvidiaWord("hello", null, null, 1.0)), 200),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGapBetweenWindows() {
        FieldTestSttBatchAssembler.assemble(
            listOf(
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(0, 0L, 60_000_000L),
                    NvidiaSttResult("one", listOf(NvidiaWord("one", 0L, 100L, 1.0)), 200),
                ),
                FieldTestSttBatchAssembler.WindowResult(
                    FieldTestSttWindow(1, 61_000_000L, 120_000_000L),
                    NvidiaSttResult("two", listOf(NvidiaWord("two", 0L, 100L, 1.0)), 200),
                ),
            ),
        )
    }
}
