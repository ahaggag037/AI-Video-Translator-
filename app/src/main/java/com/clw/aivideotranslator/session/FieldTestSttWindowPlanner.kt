package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaWord

internal data class FieldTestSttWindow(
    val index: Int,
    val startUs: Long,
    val endUs: Long,
) {
    init {
        require(index >= 0)
        require(startUs >= 0L)
        require(endUs > startUs)
    }

    val durationUs: Long get() = endUs - startUs
    val startMs: Long get() = startUs / 1_000L
}

/**
 * Pure planning/mapping layer for the round-2 full-video field test.
 *
 * It intentionally does not submit provider calls or mutate durable session state. The existing
 * durable first-window operation remains the only live STT writer until the multi-window journal is
 * implemented. This planner establishes deterministic, gap-free source windows and the only allowed
 * relative->presentation timestamp mapping for later window execution.
 */
internal object FieldTestSttWindowPlanner {
    const val WINDOW_US = 60_000_000L

    fun plan(videoDurationUs: Long, windowUs: Long = WINDOW_US): List<FieldTestSttWindow> {
        require(videoDurationUs > 0L) { "video duration must be positive" }
        require(windowUs > 0L && windowUs % 1_000L == 0L) { "STT window must be positive and millisecond-aligned" }

        val windows = mutableListOf<FieldTestSttWindow>()
        var start = 0L
        var index = 0
        while (start < videoDurationUs) {
            val end = minOf(videoDurationUs, Math.addExact(start, windowUs))
            windows += FieldTestSttWindow(index = index, startUs = start, endUs = end)
            start = end
            index += 1
        }
        require(windows.first().startUs == 0L)
        require(windows.last().endUs == videoDurationUs)
        windows.zipWithNext().forEach { (left, right) ->
            require(left.endUs == right.startUs) { "STT windows must be contiguous" }
        }
        return windows
    }

    fun toPresentationWord(window: FieldTestSttWindow, relative: NvidiaWord): NvidiaWord {
        val relativeStartMs = requireNotNull(relative.startMs) { "window word missing start" }
        val relativeEndMs = requireNotNull(relative.endMs) { "window word missing end" }
        require(relativeStartMs >= 0L && relativeEndMs > relativeStartMs) { "invalid window word timing" }
        val windowDurationMs = (window.durationUs + 999L) / 1_000L
        require(relativeEndMs <= windowDurationMs) { "window word exceeds source window" }
        val absoluteStartMs = Math.addExact(window.startMs, relativeStartMs)
        val absoluteEndMs = Math.addExact(window.startMs, relativeEndMs)
        return relative.copy(startMs = absoluteStartMs, endMs = absoluteEndMs)
    }
}
