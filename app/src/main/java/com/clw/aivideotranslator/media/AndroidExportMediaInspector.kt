package com.clw.aivideotranslator.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Android-side X004 evidence collector for a rendered media file.
 *
 * This class is intentionally observational: it never mutates, decodes, publishes, or adopts an
 * export. Callers can feed the returned observation into [ExportValidator] or retain it as device
 * evidence. Full frame/audio decode parity remains a separate X004 gate.
 *
 * Track sample scanning may perform file I/O and must not run on the main thread.
 */
internal object AndroidExportMediaInspector {
    fun inspect(file: File): ExportMediaObservation {
        val sizeBytes = if (file.isFile) file.length() else 0L
        if (!file.isFile) {
            return ExportMediaObservation(
                sizeBytes = sizeBytes,
                durationUs = null,
                readable = false,
                tracks = emptyList(),
            )
        }

        return runCatching {
            val tracks = readTrackObservations(file)
            ExportMediaObservation(
                sizeBytes = sizeBytes,
                durationUs = readContainerDurationUs(file),
                readable = true,
                tracks = tracks,
            )
        }.getOrElse {
            ExportMediaObservation(
                sizeBytes = sizeBytes,
                durationUs = null,
                readable = false,
                tracks = emptyList(),
            )
        }
    }

    private fun readTrackObservations(file: File): List<MediaTrackObservation> {
        val metadataExtractor = MediaExtractor()
        return try {
            metadataExtractor.setDataSource(file.absolutePath)
            buildList(metadataExtractor.trackCount) {
                for (trackIndex in 0 until metadataExtractor.trackCount) {
                    val format = metadataExtractor.getTrackFormat(trackIndex)
                    val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME)) {
                        "media track has no MIME type"
                    }
                    val timeline = readTrackTimeline(file, trackIndex)
                    add(
                        MediaTrackObservation(
                            mime = mime,
                            sampleCount = timeline.sampleCount,
                            firstPresentationTimeUs = timeline.firstPresentationTimeUs,
                            lastPresentationTimeUs = timeline.lastPresentationTimeUs,
                        )
                    )
                }
            }
        } finally {
            metadataExtractor.release()
        }
    }

    private fun readTrackTimeline(file: File, trackIndex: Int): TrackTimelineObservation {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            require(trackIndex in 0 until extractor.trackCount) { "track index out of range" }
            extractor.selectTrack(trackIndex)

            var sampleCount = 0L
            var firstPresentationTimeUs: Long? = null
            var lastPresentationTimeUs: Long? = null

            while (true) {
                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs < 0L) break
                if (firstPresentationTimeUs == null) firstPresentationTimeUs = presentationTimeUs
                lastPresentationTimeUs = presentationTimeUs
                sampleCount += 1L
                if (!extractor.advance()) break
            }

            TrackTimelineObservation(
                sampleCount = sampleCount,
                firstPresentationTimeUs = firstPresentationTimeUs,
                lastPresentationTimeUs = lastPresentationTimeUs,
            )
        } finally {
            extractor.release()
        }
    }

    private fun readContainerDurationUs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?.times(1_000L)
        } finally {
            retriever.release()
        }
    }

    private data class TrackTimelineObservation(
        val sampleCount: Long,
        val firstPresentationTimeUs: Long?,
        val lastPresentationTimeUs: Long?,
    )
}
