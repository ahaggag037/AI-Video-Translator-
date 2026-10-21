package com.clw.aivideotranslator.presentation

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.DurableDisplayTimingState
import com.clw.aivideotranslator.editor.DurableEditorTextState
import com.clw.aivideotranslator.editor.UnitDisplayTimingState
import com.clw.aivideotranslator.editor.UnitEditorTextState

/**
 * Single committed presentation assembler for preview, SRT and MP4. Pending editor work is never
 * observable here. V1 timecodes are millisecond-addressable, so a non-ms-aligned durable override
 * fails closed instead of being rounded differently by different consumers.
 */
internal object CommittedPresentationAssembler {
    fun cues(
        units: List<SourceUnit>,
        entries: List<TranslationEntry>,
        textState: DurableEditorTextState,
        timingState: DurableDisplayTimingState,
    ): List<ArabicSubtitleCue> {
        require(units.isNotEmpty()) { "no source units" }
        require(units.size == entries.size) { "translation count does not match source units" }
        require(units.map { it.id } == entries.map { it.sourceUnitId }) {
            "translation ids do not match source unit order"
        }
        require(textState.sessionId == timingState.sessionId) { "presentation sidecars belong to different sessions" }

        var previousEndUs = 0L
        return units.indices.map { index ->
            val unit = units[index]
            val entry = entries[index]
            val sourceInterval = DisplayTimingInterval(
                startUs = Math.multiplyExact(unit.startMs, 1_000L),
                endUs = Math.multiplyExact(unit.endMs, 1_000L),
            )
            val timing = timingState.units[unit.id]
                .orDefault()
                .exportInterval(sourceInterval)
            require(timing.startUs >= previousEndUs) { "committed display timing overlaps previous cue" }
            previousEndUs = timing.endUs
            require(timing.startUs % 1_000L == 0L && timing.endUs % 1_000L == 0L) {
                "committed display timing is not millisecond-addressable"
            }

            val text = textState.units[unit.id]
                .orDefault()
                .exportText(entry.translatedText)
            ArabicSubtitleCue(
                sourceUnitId = unit.id,
                startMs = timing.startUs / 1_000L,
                endMs = timing.endUs / 1_000L,
                text = SubtitlePipeline.validateText(text),
            )
        }
    }

    private fun UnitEditorTextState?.orDefault(): UnitEditorTextState = this ?: UnitEditorTextState()
    private fun UnitDisplayTimingState?.orDefault(): UnitDisplayTimingState = this ?: UnitDisplayTimingState()
}