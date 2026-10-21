package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata

/**
 * Converts probe observations into the small typed product contract used by Source Review.
 * Provider/storage exception details are deliberately not surfaced to UI state because source URIs
 * may contain private provider-specific data.
 */
internal object CandidateProbeClassifier {
    fun fromMetadata(metadata: VideoMetadata): CandidateProbeResult =
        if (metadata.hasAudio) {
            CandidateProbeResult.Ready(metadata.displayName)
        } else {
            CandidateProbeResult.NoAudio
        }

    fun fromFailure(error: Throwable): CandidateProbeResult =
        when (error) {
            is SecurityException -> CandidateProbeResult.AccessLimited
            is IllegalArgumentException -> CandidateProbeResult.Invalid
            else -> CandidateProbeResult.Failed("تعذر فحص الفيديو.")
        }
}
