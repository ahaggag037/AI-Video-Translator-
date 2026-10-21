package com.clw.aivideotranslator.export

internal enum class OutputSettlement {
    NOT_REQUESTED,
    SUCCESS,
    FAILED,
    CANCELLED,
}

internal data class ExportResultTruth(
    val srt: OutputSettlement,
    val mp4: OutputSettlement,
) {
    fun retryableOutputs(): Set<OutputKind> = buildSet {
        if (srt == OutputSettlement.FAILED || srt == OutputSettlement.CANCELLED) add(OutputKind.SRT)
        if (mp4 == OutputSettlement.FAILED || mp4 == OutputSettlement.CANCELLED) add(OutputKind.MP4)
    }
}
