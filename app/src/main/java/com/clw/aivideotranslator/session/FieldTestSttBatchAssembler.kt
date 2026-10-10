package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult

/**
 * Pure field-test assembler for full-video STT.
 *
 * Every provider result remains window-relative until this boundary. This is the only place where
 * round-2 window results are lifted onto the original presentation timeline. No provider calls,
 * durable writes, or recovery decisions happen here.
 */
internal object FieldTestSttBatchAssembler {
    data class WindowResult(
        val window: FieldTestSttWindow,
        val result: NvidiaSttResult,
    )

    fun assemble(results: List<WindowResult>): NvidiaSttResult {
        require(results.isNotEmpty()) { "full-video STT requires at least one window result" }

        val ordered = results.sortedBy { it.window.index }
        ordered.forEachIndexed { expectedIndex, item ->
            require(item.window.index == expectedIndex) { "STT window results must be contiguous from index zero" }
            require(item.result.httpStatus in 200..299) { "STT window result is not successful" }
        }
        ordered.zipWithNext().forEach { (left, right) ->
            require(left.window.endUs == right.window.startUs) { "STT window results contain a gap or overlap" }
        }

        val absoluteWords = ordered.flatMap { item ->
            item.result.words.map { relative ->
                FieldTestSttWindowPlanner.toPresentationWord(item.window, relative)
            }
        }
        absoluteWords.zipWithNext().forEach { (left, right) ->
            val leftStart = requireNotNull(left.startMs)
            val rightStart = requireNotNull(right.startMs)
            require(rightStart >= leftStart) { "assembled STT words are not monotonic" }
        }

        val transcript = ordered.asSequence()
            .map { it.result.transcript.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        return NvidiaSttResult(
            transcript = transcript,
            words = absoluteWords,
            httpStatus = 200,
        )
    }
}
