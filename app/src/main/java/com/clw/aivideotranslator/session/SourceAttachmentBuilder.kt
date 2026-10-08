package com.clw.aivideotranslator.session

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs

/**
 * Pure assembly of an immutable SourceAttachment from already-observed evidence.
 * Performs no I/O itself; refuses unreadable sources, unknown durations and ranges that
 * exceed the observed duration. The track descriptor is the extractor's first-audio-track
 * view at capture time; preparation-time provenance must still match it exactly when a
 * SourceSnapshot is built. This assembler does not bind the attachment into the session
 * manifest — store/epoch ownership remains a separate explicit step.
 */
object SourceAttachmentAssembler {
    fun assemble(
        sessionId: String,
        inspection: SourceContentInspection,
        durationMs: Long,
        audioTrack: SourceAudioTrack,
        requestedRange: PresentationIntervalUs? = null,
    ): SourceAttachment {
        require(inspection.status == SourceReadStatus.READABLE) {
            "source is not readable at capture: ${inspection.status}"
        }
        val fingerprint = requireNotNull(inspection.fingerprint) { "readable source without fingerprint" }
        require(durationMs > 0L) { "unknown source duration" }
        val durationUs = Math.multiplyExact(durationMs, 1_000L)
        val range = requestedRange
            ?: PresentationIntervalUs(PresentationTimeUs(0L), PresentationTimeUs(durationUs))
        require(range.end.value <= durationUs) { "selected range exceeds observed source duration" }
        return SourceAttachment(
            sessionId = sessionId,
            contentUri = inspection.observedContentUri,
            persistedReadGrantAtCapture = inspection.persistedReadGrantNow,
            fingerprint = fingerprint,
            durationUs = durationUs,
            selectedRange = range,
            audioTrack = audioTrack,
        )
    }
}

/**
 * Blocking Android capture boundary for a freshly selected source. Call from an I/O dispatcher.
 * Reads the complete byte stream once for identity evidence, then observes container duration
 * and the first audio track. No locator or digest is logged; failures surface as distinct
 * Result errors. Track selection policy mirrors SttAudioPreparer so capture-time ownership and
 * preparation-time provenance describe the same track. Legacy picker/probe behavior unchanged.
 */
object SourceAttachmentBuilder {
    fun build(
        context: Context,
        sessionId: String,
        contentUri: String,
        requestedRange: PresentationIntervalUs? = null,
    ): Result<SourceAttachment> = runCatching {
        val resolver = context.contentResolver
        val inspection = SourceContentProbe.inspect(resolver, contentUri)
        require(inspection.status == SourceReadStatus.READABLE) {
            "source is not readable at capture: ${inspection.status}"
        }
        val uri = Uri.parse(contentUri)
        val durationMs = readDurationMs(context, uri)
        val track = readFirstAudioTrack(context, uri)
        SourceAttachmentAssembler.assemble(
            sessionId = sessionId,
            inspection = inspection,
            durationMs = durationMs,
            audioTrack = track,
            requestedRange = requestedRange,
        )
    }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration > 0L) { "تعذر تحديد مدة الفيديو" }
            return duration
        } finally {
            retriever.release()
        }
    }

    /** Same first-audio-track policy as SttAudioPreparer; language metadata is fail-soft. */
    private fun readFirstAudioTrack(context: Context, uri: Uri): SourceAudioTrack {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val language = runCatching {
                    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                        format.getString(MediaFormat.KEY_LANGUAGE)
                    } else null
                }.getOrNull()
                return SourceAudioTrack(
                    containerIndex = index,
                    mime = mime,
                    language = language,
                    sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                )
            }
            error("الفيديو لا يحتوي على مسار صوت")
        } finally {
            extractor.release()
        }
    }
}
