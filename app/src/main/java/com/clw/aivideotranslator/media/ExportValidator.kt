package com.clw.aivideotranslator.media

enum class ExportMediaFailure {
    EMPTY_OUTPUT,
    UNREADABLE_OUTPUT,
    DURATION_MISMATCH,
    VIDEO_TRACK_MISSING,
    UNSUPPORTED_VIDEO_CODEC,
    AUDIO_TRACK_MISSING,
    UNSUPPORTED_AUDIO_CODEC,
}

data class MediaTrackObservation(
    val mime: String,
    val sampleCount: Long? = null,
    val firstPresentationTimeUs: Long? = null,
    val lastPresentationTimeUs: Long? = null,
) {
    init {
        require(mime.isNotBlank()) { "blank track mime" }
        require(sampleCount == null || sampleCount >= 0L) { "negative sample count" }
        require(firstPresentationTimeUs == null || firstPresentationTimeUs >= 0L) { "negative first PTS" }
        require(lastPresentationTimeUs == null || lastPresentationTimeUs >= 0L) { "negative last PTS" }
        if (firstPresentationTimeUs != null && lastPresentationTimeUs != null) {
            require(lastPresentationTimeUs >= firstPresentationTimeUs) { "track PTS moves backwards" }
        }
    }
}

data class ExportMediaObservation(
    val sizeBytes: Long,
    val durationUs: Long?,
    val readable: Boolean,
    val tracks: List<MediaTrackObservation>,
) {
    init {
        require(sizeBytes >= 0L) { "negative output size" }
        require(durationUs == null || durationUs >= 0L) { "negative output duration" }
    }
}

data class ExportMediaContractResult(
    val failures: Set<ExportMediaFailure>,
) {
    val isAccepted: Boolean get() = failures.isEmpty()
}

/**
 * Shadow-only B009/X004 media contract evaluator.
 *
 * It evaluates observed container/track evidence but does not probe files or publish exports. The
 * Android experiment layer owns MediaExtractor/decoder measurements (decoded samples, marker PTS,
 * rotation/frame parity). Production BurnedSubtitleExporter remains unchanged until X004 passes.
 */
object ExportValidator {
    const val VIDEO_H264_MIME = "video/avc"
    const val AUDIO_AAC_MIME = "audio/mp4a-latm"
    const val CONTAINER_DURATION_TOLERANCE_US = 250_000L

    fun evaluate(
        observation: ExportMediaObservation,
        expectedDurationUs: Long,
        sourceHasAudio: Boolean,
    ): ExportMediaContractResult {
        require(expectedDurationUs > 0L) { "expected duration must be positive" }
        val failures = buildSet {
            if (observation.sizeBytes == 0L) add(ExportMediaFailure.EMPTY_OUTPUT)
            if (!observation.readable) add(ExportMediaFailure.UNREADABLE_OUTPUT)

            val durationUs = observation.durationUs
            if (durationUs == null || absoluteDifference(durationUs, expectedDurationUs) > CONTAINER_DURATION_TOLERANCE_US) {
                add(ExportMediaFailure.DURATION_MISMATCH)
            }

            val videoTracks = observation.tracks.filter { it.mime.startsWith("video/") }
            when {
                videoTracks.isEmpty() -> add(ExportMediaFailure.VIDEO_TRACK_MISSING)
                videoTracks.any { it.mime != VIDEO_H264_MIME } -> add(ExportMediaFailure.UNSUPPORTED_VIDEO_CODEC)
            }

            val audioTracks = observation.tracks.filter { it.mime.startsWith("audio/") }
            if (sourceHasAudio && audioTracks.isEmpty()) add(ExportMediaFailure.AUDIO_TRACK_MISSING)
            if (audioTracks.any { it.mime != AUDIO_AAC_MIME }) add(ExportMediaFailure.UNSUPPORTED_AUDIO_CODEC)
        }
        return ExportMediaContractResult(failures)
    }

    private fun absoluteDifference(a: Long, b: Long): Long = if (a >= b) a - b else b - a
}
