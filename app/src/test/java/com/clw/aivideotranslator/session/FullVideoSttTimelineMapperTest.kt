package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SttAudioProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FullVideoSttTimelineMapperTest {
    private fun profile(startUs: Long, endUs: Long) = SttAudioProfile(
        file = File("synthetic.wav"),
        sampleRateHz = 48_000,
        channelCount = 1,
        bitsPerSample = 16,
        sourceStartUs = startUs,
        sourceEndUs = endUs,
        durationMs = (endUs - startUs) / 1_000L,
    )

    @Test fun providerMillisecondOffsetsAreAddedToObservedMediaOrigin() {
        val mapped = FullVideoSttTimelineMapper.absoluteWords(
            profile = profile(60_021_333L, 120_000_000L),
            result = NvidiaSttResult(
                transcript = "hello",
                words = listOf(NvidiaWord("hello", 80L, 600L, 0.9)),
                httpStatus = 200,
            ),
        )
        assertEquals(60_101_333L, mapped.single().startUs)
        assertEquals(60_621_333L, mapped.single().endUs)
    }

    @Test fun legacyMillisecondBoundaryFloorsStartAndCeilsEndWithoutZeroOriginAssumption() {
        val legacy = FullVideoSttTimelineMapper.toLegacyMilliseconds(
            listOf(AbsoluteNvidiaWord("hello", 60_101_333L, 60_621_333L, null)),
        )
        assertEquals(60_101L, legacy.single().startMs)
        assertEquals(60_622L, legacy.single().endMs)
    }

    @Test fun adjacentWindowsMergeMonotonically() {
        val left = listOf(AbsoluteNvidiaWord("one", 59_000_000L, 59_900_000L, null))
        val right = listOf(AbsoluteNvidiaWord("two", 60_021_333L, 60_500_000L, null))
        val merged = FullVideoSttTimelineMapper.mergeWindows(listOf(left, right))
        assertEquals(listOf("one", "two"), merged.map { it.text })
    }

    @Test(expected = IllegalArgumentException::class)
    fun providerWordOutsidePreparedAudioFailsClosed() {
        FullVideoSttTimelineMapper.absoluteWords(
            profile = profile(60_000_000L, 61_000_000L),
            result = NvidiaSttResult(
                transcript = "bad",
                words = listOf(NvidiaWord("bad", 900L, 1_500L, null)),
                httpStatus = 200,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlapBetweenWindowsFailsClosed() {
        FullVideoSttTimelineMapper.mergeWindows(
            listOf(
                listOf(AbsoluteNvidiaWord("one", 1_000_000L, 2_000_000L, null)),
                listOf(AbsoluteNvidiaWord("two", 1_900_000L, 2_500_000L, null)),
            ),
        )
    }
}