package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.session.LegacyParityTranslationPlanner

/**
 * Restores the exact deterministic source-unit identity needed by editor/preview/export after a
 * process restart. Timed STT may be recovered locally from the durable full-video aggregate; no
 * provider call is made here. Any identity drift fails closed instead of rebasing accepted text.
 */
internal object ProductPresentationUnitResolver {
    fun resolve(
        liveUnits: List<SourceUnit>?,
        entries: List<TranslationEntry>,
        timedStt: NvidiaSttResult?,
    ): List<SourceUnit>? {
        if (entries.isEmpty()) return null
        val expectedIds = entries.map { it.sourceUnitId }

        liveUnits
            ?.takeIf { it.isNotEmpty() }
            ?.let { live ->
                return live.takeIf { units -> units.map { it.id } == expectedIds }
            }

        val recovered = timedStt?.let { result ->
            runCatching {
                LegacyParityTranslationPlanner.plan(result).map { it.legacyUnit }
            }.getOrNull()
        } ?: return null

        return recovered.takeIf { units -> units.map { it.id } == expectedIds }
    }
}
