package com.clw.aivideotranslator

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

private const val DEFAULT_SAMPLE_DURATION_US = 60_000_000L
private const val DEFAULT_BUFFER_BYTES = 512 * 1024

data class AudioSampleResult(
    val file: File,
    val mimeType: String,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val measuredDurationMs: Long?,
) {
    val sizeBytes: Long get() = file.length()
    val copiedDurationUs: Long get() = (sourceEndUs - sourceStartUs).coerceAtLeast(0L)
    val copiedDurationLabel: String get() = formatDuration(copiedDurationUs / 1_000L)
    val measuredDurationLabel: String get() = measuredDurationMs?.let(::formatDuration) ?: "غير معروف"
    val sizeLabel: String get() = formatBytes(sizeBytes)
}

object AudioSampleExtractor {
    fun extractFirstMinute(
        context: Context,
        sourceUri: Uri,
        durationUs: Long = DEFAULT_SAMPLE_DURATION_US,
    ): Result<AudioSampleResult> = runCatching {
        require(durationUs > 0L) { "مدة العينة يجب أن تكون أكبر من صفر" }

        val outputDir = File(context.cacheDir, "p0_audio_samples").apply { mkdirs() }
        val outputFile = File(outputDir, "sample.m4a")
        if (outputFile.exists() && !outputFile.delete()) {
            error("تعذر استبدال ملف العينة السابق")
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(context, sourceUri, null)
            val audioTrackIndex = findAudioTrack(extractor)
            require(audioTrackIndex >= 0) { "الفيديو لا يحتوي على مسار صوت قابل للاستخراج" }

            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("تعذر معرفة ترميز مسار الصوت")

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val firstSourceTimeUs = extractor.sampleTime
            require(firstSourceTimeUs >= 0L) { "تعذر الوصول إلى أول عينة صوت" }
            val sourceStopUs = firstSourceTimeUs + durationUs

            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                val sourceDurationUs = inputFormat.getLong(MediaFormat.KEY_DURATION)
                inputFormat.setLong(
                    MediaFormat.KEY_DURATION,
                    minOf(durationUs, (sourceDurationUs - firstSourceTimeUs).coerceAtLeast(0L)),
                )
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrackIndex = muxer.addTrack(inputFormat)
            muxer.start()
            muxerStarted = true

            val maxInputSize = if (inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                DEFAULT_BUFFER_BYTES
            }
            val buffer = ByteBuffer.allocateDirect(maxOf(DEFAULT_BUFFER_BYTES, maxInputSize))
            val info = MediaCodec.BufferInfo()
            var lastSourceTimeUs = firstSourceTimeUs
            var wroteAnySample = false

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs >= sourceStopUs) break

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = sampleTimeUs - firstSourceTimeUs
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outputTrackIndex, buffer, info)
                wroteAnySample = true
                lastSourceTimeUs = sampleTimeUs

                if (!extractor.advance()) break
            }

            require(wroteAnySample) { "لم يتم العثور على عينات صوت داخل المقطع المطلوب" }
        } finally {
            extractor.release()
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }

        require(outputFile.exists() && outputFile.length() > 0L) { "لم يتم إنشاء ملف العينة" }

        val retriever = MediaMetadataRetriever()
        val measuredDurationMs = try {
            retriever.setDataSource(outputFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }

        // Re-open only to obtain source timing information from the selected track.
        val timingExtractor = MediaExtractor()
        val (mimeType, sourceStartUs, sourceEndUs) = try {
            timingExtractor.setDataSource(context, sourceUri, null)
            val audioTrackIndex = findAudioTrack(timingExtractor)
            require(audioTrackIndex >= 0)
            val format = timingExtractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/unknown"
            timingExtractor.selectTrack(audioTrackIndex)
            timingExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val start = timingExtractor.sampleTime.coerceAtLeast(0L)
            val end = start + minOf(durationUs, measuredDurationMs?.times(1_000L) ?: durationUs)
            Triple(mime, start, end)
        } finally {
            timingExtractor.release()
        }

        AudioSampleResult(
            file = outputFile,
            mimeType = mimeType,
            sourceStartUs = sourceStartUs,
            sourceEndUs = sourceEndUs,
            measuredDurationMs = measuredDurationMs,
        )
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return index
        }
        return -1
    }
}
