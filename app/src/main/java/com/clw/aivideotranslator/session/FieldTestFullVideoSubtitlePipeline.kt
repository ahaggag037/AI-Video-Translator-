package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.TranslationEntry

/**
 * Full-duration presentation adapter for round-2 only.
 *
 * The frozen [SubtitlePipeline] intentionally retains its first-60-second P0 guards. This adapter
 * accepts semantic field-test units that are already on the original video presentation timeline,
 * validates them against the actual video duration, and reuses only the text/SRT helpers that do not
 * impose the old sample cap.
 */
internal object FieldTestFullVideoSubtitlePipeline {
    fun cues(
        units: List<SourceUnit>,
        entries: List<TranslationEntry>,
        videoDurationMs: Long,
    ): List<ArabicSubtitleCue> {
        require(videoDurationMs > 0L) { "video duration must be positive" }
        require(units.isNotEmpty() && units.size == entries.size) {
            "full-video translation count does not match source units"
        }
        require(units.map { it.id }.toSet().size == units.size) { "duplicate full-video source unit IDs" }
        require(entries.map { it.sourceUnitId }.toSet().size == entries.size) {
            "duplicate full-video translation IDs"
        }
        val byId = entries.associateBy { it.sourceUnitId }
        require(byId.keys == units.map { it.id }.toSet()) {
            "full-video translation IDs do not match source units"
        }

        var previousEndMs = 0L
        return units.map { unit ->
            require(unit.startMs >= previousEndMs && unit.endMs > unit.startMs) {
                "full-video subtitle timing is overlapping or invalid"
            }
            require(unit.endMs <= videoDurationMs) { "full-video subtitle lies beyond the source video" }
            previousEndMs = unit.endMs
            ArabicSubtitleCue(
                sourceUnitId = unit.id,
                startMs = unit.startMs,
                endMs = unit.endMs,
                text = SubtitlePipeline.validateText(byId.getValue(unit.id).translatedText),
            )
        }
    }

    fun srt(cues: List<ArabicSubtitleCue>, videoDurationMs: Long): String =
        SubtitlePipeline.srt(cues, maxTimelineMs = videoDurationMs)
}
