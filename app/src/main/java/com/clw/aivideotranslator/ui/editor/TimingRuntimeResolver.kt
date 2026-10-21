package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.UnitDisplayTimingState

internal data class TimingRuntime(
    val sourceInterval: DisplayTimingInterval,
    val selectedState: UnitDisplayTimingState,
    val previousEffectiveEndUs: Long?,
    val nextEffectiveStartUs: Long?,
)

/**
 * Derives timing-editor bounds from ordered source units plus committed display overrides. Pending
 * drafts on adjacent cues are deliberately ignored because they are recovery work, not display truth.
 */
internal object TimingRuntimeResolver {
    fun resolve(
        units: List<SourceUnit>,
        selectedUnitId: String,
        states: Map<String, UnitDisplayTimingState>,
    ): TimingRuntime? {
        val index = units.indexOfFirst { it.id == selectedUnitId }
        if (index < 0) return null

        val current = units[index]
        val sourceInterval = current.toDisplayInterval()
        val previousEffectiveEndUs = units.getOrNull(index - 1)?.let { previous ->
            states[previous.id].orEmpty().exportInterval(previous.toDisplayInterval()).endUs
        }
        val nextEffectiveStartUs = units.getOrNull(index + 1)?.let { next ->
            states[next.id].orEmpty().exportInterval(next.toDisplayInterval()).startUs
        }

        return TimingRuntime(
            sourceInterval = sourceInterval,
            selectedState = states[current.id].orEmpty(),
            previousEffectiveEndUs = previousEffectiveEndUs,
            nextEffectiveStartUs = nextEffectiveStartUs,
        )
    }

    private fun SourceUnit.toDisplayInterval(): DisplayTimingInterval = DisplayTimingInterval(
        startUs = Math.multiplyExact(startMs, 1_000L),
        endUs = Math.multiplyExact(endMs, 1_000L),
    )

    private fun UnitDisplayTimingState?.orEmpty(): UnitDisplayTimingState =
        this ?: UnitDisplayTimingState()
}
