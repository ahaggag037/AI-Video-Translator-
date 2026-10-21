package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.UnitDisplayTimingState
import java.util.Locale

/** Pure projection from durable timing truth to the editable timing surface. */
internal object TimingEditorProjection {
    fun resolve(
        sourceInterval: DisplayTimingInterval,
        state: UnitDisplayTimingState,
        videoDurationUs: Long?,
        previousEffectiveEndUs: Long?,
        nextEffectiveStartUs: Long?,
        sourceAvailable: Boolean,
    ): TimingEditorPresentation {
        val visible = state.visibleInterval(sourceInterval)
        val startField = formatTimecodeUs(visible.startUs)
        val endField = formatTimecodeUs(visible.endUs)
        val validation = TimingDraftValidator.validate(
            TimingDraft(
                startText = startField,
                endText = endField,
                durationMs = videoDurationUs?.floorDiv(1_000L),
                previousEffectiveEndMs = previousEffectiveEndUs?.floorDiv(1_000L),
                nextEffectiveStartMs = nextEffectiveStartUs?.floorDiv(1_000L),
            ),
        )
        return TimingEditorPresentation(
            startField = startField,
            endField = endField,
            dirty = state.pendingDraft != null,
            validation = validation,
            sourceAvailable = sourceAvailable,
        )
    }

    fun formatTimecodeUs(valueUs: Long): String {
        require(valueUs >= 0L) { "timecode cannot be negative" }
        val totalMs = valueUs / 1_000L
        val minutes = totalMs / 60_000L
        val seconds = (totalMs / 1_000L) % 60L
        val millis = totalMs % 1_000L
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    fun parseTimecodeUs(value: String): Long? {
        val match = TIMECODE.matchEntire(value.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val millis = match.groupValues[3].toLongOrNull() ?: return null
        if (seconds !in 0L..59L || millis !in 0L..999L) return null
        return try {
            Math.multiplyExact(
                Math.addExact(
                    Math.multiplyExact(minutes, 60_000L),
                    Math.addExact(Math.multiplyExact(seconds, 1_000L), millis),
                ),
                1_000L,
            )
        } catch (_: ArithmeticException) {
            null
        }
    }

    private val TIMECODE = Regex("^(\\d+):(\\d{2})\\.(\\d{3})$")
}
