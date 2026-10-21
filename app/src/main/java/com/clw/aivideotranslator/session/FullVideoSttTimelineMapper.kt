package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SttAudioProfile

data class AbsoluteNvidiaWord(
    val text: String,
    val startUs: Long,
    val endUs: Long,
    val confidence: Double?,
) {
    init {
        require(text.isNotBlank()) { "blank STT word" }
        require(startUs >= 0L && endUs > startUs) { "invalid absolute STT word timing" }
    }
}

/** Maps NVIDIA's documented ms-from-submitted-audio offsets onto observed media PTS. */
internal object FullVideoSttTimelineMapper {
    private const val PREPARATION_END_TOLERANCE_US = 1_000L

    fun absoluteWords(
        profile: SttAudioProfile,
        result: NvidiaSttResult,
    ): List<AbsoluteNvidiaWord> {
        require(result.httpStatus in 200..299) { "STT response was not successful" }
        require(profile.sourceStartUs >= 0L && profile.sourceEndUs > profile.sourceStartUs) {
            "invalid prepared-audio media origin"
        }
        require(result.words.isNotEmpty()) { "provider response contains no word offsets" }
        var previousEndUs = profile.sourceStartUs
        return result.words.map { word ->
            val relativeStartMs = requireNotNull(word.startMs) { "provider word missing start offset" }
            val relativeEndMs = requireNotNull(word.endMs) { "provider word missing end offset" }
            require(relativeStartMs >= 0L && relativeEndMs > relativeStartMs) { "invalid provider word offset" }
            val startUs = Math.addExact(profile.sourceStartUs, Math.multiplyExact(relativeStartMs, 1_000L))
            val endUs = Math.addExact(profile.sourceStartUs, Math.multiplyExact(relativeEndMs, 1_000L))
            require(startUs >= previousEndUs) { "provider word offsets overlap or move backward" }
            require(endUs <= profile.sourceEndUs + PREPARATION_END_TOLERANCE_US) {
                "provider word offset escapes prepared audio window"
            }
            previousEndUs = endUs
            AbsoluteNvidiaWord(word.text.trim(), startUs, endUs, word.confidence)
        }
    }

    fun mergeWindows(windows: List<List<AbsoluteNvidiaWord>>): List<AbsoluteNvidiaWord> {
        require(windows.isNotEmpty()) { "no STT windows to merge" }
        val merged = ArrayList<AbsoluteNvidiaWord>()
        var previousEndUs = 0L
        windows.forEach { window ->
            require(window.isNotEmpty()) { "empty STT word window" }
            window.forEach { word ->
                require(word.startUs >= previousEndUs) { "STT windows overlap or move backward" }
                merged += word
                previousEndUs = word.endUs
            }
        }
        return merged
    }

    /**
     * Existing semantic/translation code is millisecond-addressed. Quantization happens exactly once
     * at this boundary: floor start, ceil end. Expansion is <1 ms per edge and never shortens speech.
     */
    fun toLegacyMilliseconds(words: List<AbsoluteNvidiaWord>): List<NvidiaWord> {
        require(words.isNotEmpty()) { "no absolute STT words" }
        var previousEndMs = 0L
        return words.map { word ->
            val startMs = Math.floorDiv(word.startUs, 1_000L)
            val endMs = ceilDivPositive(word.endUs, 1_000L)
            require(startMs >= previousEndMs && endMs > startMs) {
                "millisecond projection overlaps or collapses a provider word"
            }
            previousEndMs = endMs
            NvidiaWord(word.text, startMs, endMs, word.confidence)
        }
    }

    private fun ceilDivPositive(value: Long, divisor: Long): Long {
        require(value >= 0L && divisor > 0L)
        return if (value == 0L) 0L else Math.addExact(Math.floorDiv(value - 1L, divisor), 1L)
    }
}