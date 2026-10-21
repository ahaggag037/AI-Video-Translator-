package com.clw.aivideotranslator.export

import com.clw.aivideotranslator.presentation.LayoutStatus

internal enum class OutputKind(
    val requiresVideoPipeline: Boolean,
) {
    SRT(requiresVideoPipeline = false),
    MP4(requiresVideoPipeline = true),
}

internal enum class ExportMode {
    SRT_ONLY,
    MP4_ONLY,
    BOTH,
}

internal data class ExportFacts(
    val hasCommittedText: Boolean,
    val timingValid: Boolean,
    val sourceAvailable: Boolean,
    val layoutStatus: LayoutStatus,
)

internal data class ExportEligibility(
    val srtEligible: Boolean,
    val mp4Eligible: Boolean,
)

internal object ExportEligibilityResolver {
    fun resolve(facts: ExportFacts): ExportEligibility {
        val semanticReady = facts.hasCommittedText && facts.timingValid
        return ExportEligibility(
            srtEligible = semanticReady,
            mp4Eligible = semanticReady && facts.sourceAvailable && facts.layoutStatus == LayoutStatus.FITS,
        )
    }
}
