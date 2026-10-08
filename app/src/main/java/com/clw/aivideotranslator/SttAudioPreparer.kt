package com.clw.aivideotranslator

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val STT_SAMPLE_DURATION_US = 60_000_000L
private const val CODEC_TIMEOUT_US = 10_000L

data class SttAudioProfile(
    val file: File,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val durationMs: Long,
) {
    val sizeBytes: Long get() = file.length()
    val durationLabel: String get() = formatDuration(durationMs)
    val sizeLabel: String get() = formatBytes(sizeBytes)
}

/** Exact container-track observations used by the decoder; no track-selection policy is inferred here. */
data class SttInputAudioTrack(
    val containerIndex: Int,
    val mime: String,
    val language: String?,
    val sampleRateHz: Int,
    val channelCount: Int,
) {
    init {
        require(containerIndex >= 0) { "negative input audio track index" }
        require(mime.startsWith("audio/")) { "input track is not audio" }
        require(sampleRateHz > 0) { "invalid input audio sample rate" }
        require(channelCount > 0) { "invalid input audio channel count" }
    }
}

data class SttAudioPreparationProvenance(
    val inputTrack: SttInputAudioTrack,
    /** Exact mono PCM frames written to the WAV data section. */
    val pcmFrameCount: Long,
) {
    init {
        require(pcmFrameCount > 0L) { "empty prepared PCM" }
    }
}

data class DetailedSttAudioPreparation(
    val profile: SttAudioProfile,
    val provenance: SttAudioPreparationProvenance,
)

object SttAudioPreparer {
    /** Legacy-compatible surface. Selection, WAV bytes and returned profile semantics are unchanged. */
    fun prepareFirstMinute(
        context: Context,
        sourceUri: Uri,
        durationUs: Long = STT_SAMPLE_DURATION_US,
    ): Result<SttAudioProfile> = prepareFirstMinuteDetailed(context, sourceUri, durationUs)
        .map(DetailedSttAudioPreparation::profile)

    /**
     * Additive provenance surface for durable-session/X001 evidence plumbing.
     * It exposes what the existing preparer actually decoded; it does not verify clock mapping,
     * reinterpret provider timing units, or change first-audio-track selection.
     */
    fun prepareFirstMinuteDetailed(
        context: Context,
        sourceUri: Uri,
        durationUs: Long = STT_SAMPLE_DURATION_US,
    ): Result<DetailedSttAudioPreparation> = runCatching {
        require(durationUs > 0L) { "مدة عينة STT يجب أن تكون أكبر من صفر" }

        val outputDir = File(context.cacheDir, "p0_stt_audio").apply { mkdirs() }
        val outputFile = File(outputDir, "stt_sample.wav")
        if (outputFile.exists() && !outputFile.delete()) {
            error("تعذر استبدال ملف WAV السابق")
        }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        var raf: RandomAccessFile? = null

        var outputSampleRate = 0
        var outputChannelCount = 0
        var outputPcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var sourceStartUs = 0L
        var sourceEndUs = 0L
        var pcmBytesWritten = 0L
        var monoFramesWritten = 0L
        var inputTrack: SttInputAudioTrack? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            val audioTrackIndex = findAudioTrack(extractor)
            require(audioTrackIndex >= 0) { "الفيديو لا يحتوي على مسار صوت قابل للتحويل إلى WAV" }

            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("تعذر معرفة ترميز مسار الصوت")
            val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Language is additive provenance only. Malformed/unsupported language metadata must not
            // turn a source that the legacy preparer could decode into a new preparation failure.
            val inputLanguage = runCatching {
                if (inputFormat.containsKey(MediaFormat.KEY_LANGUAGE)) {
                    inputFormat.getString(MediaFormat.KEY_LANGUAGE)
                } else null
            }.getOrNull()
            inputTrack = SttInputAudioTrack(
                containerIndex = audioTrackIndex,
                mime = mime,
                language = inputLanguage,
                sampleRateHz = inputSampleRate,
                channelCount = inputChannelCount,
            )

            extractor.selectTrack(audioTrackIndex)
            sourceStartUs = extractor.sampleTime
            require(sourceStartUs >= 0L) { "تعذر الوصول إلى أول عينة صوت" }
            val sourceStopUs = sourceStartUs + durationUs

            outputSampleRate = inputSampleRate
            outputChannelCount = inputChannelCount

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            raf = RandomAccessFile(outputFile, "rw").apply {
                setLength(0L)
                write(ByteArray(44))
            }

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var sourceExhausted = false
            var outputEnded = false
            var lastQueuedPresentationTimeUs = 0L

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: error("تعذر الوصول إلى بافر فك الصوت")
                        inputBuffer.clear()

                        val sampleTimeUs = extractor.sampleTime
                        val shouldEnd = sourceExhausted || sampleTimeUs < 0L || sampleTimeUs >= sourceStopUs
                        if (shouldEnd) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                lastQueuedPresentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    lastQueuedPresentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                val presentationTimeUs = sampleTimeUs - sourceStartUs
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0,
                                )
                                lastQueuedPresentationTimeUs = presentationTimeUs
                                if (!extractor.advance()) sourceExhausted = true
                            }
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        outputPcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            require(outputSampleRate > 0) { "معدل عينة الصوت غير صالح" }
                            require(outputChannelCount > 0) { "عدد قنوات الصوت غير صالح" }
                            val writtenFrames = writeMonoPcm16(
                                source = outputBuffer,
                                offset = info.offset,
                                size = info.size,
                                channelCount = outputChannelCount,
                                pcmEncoding = outputPcmEncoding,
                                target = raf,
                            )
                            monoFramesWritten += writtenFrames
                            pcmBytesWritten += writtenFrames * 2L
                        }

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEnded = true
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            require(monoFramesWritten > 0L) { "لم ينتج مفكك الصوت أي PCM صالح" }
            sourceEndUs = sourceStartUs + (monoFramesWritten * 1_000_000L / outputSampleRate)
            raf.seek(0L)
            raf.write(buildWavHeader(outputSampleRate, pcmBytesWritten))
        } finally {
            runCatching { raf?.close() }
            extractor.release()
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
        }

        require(outputFile.exists() && outputFile.length() > 44L) { "لم يتم إنشاء ملف WAV صالح" }
        val exactInputTrack = requireNotNull(inputTrack) { "تعذر إثبات مسار الصوت المستخدم" }

        DetailedSttAudioPreparation(
            profile = SttAudioProfile(
                file = outputFile,
                sampleRateHz = outputSampleRate,
                channelCount = 1,
                bitsPerSample = 16,
                sourceStartUs = sourceStartUs,
                sourceEndUs = sourceEndUs,
                durationMs = (monoFramesWritten * 1_000L / outputSampleRate),
            ),
            provenance = SttAudioPreparationProvenance(
                inputTrack = exactInputTrack,
                pcmFrameCount = monoFramesWritten,
            ),
        )
    }

    private fun writeMonoPcm16(
        source: ByteBuffer,
        offset: Int,
        size: Int,
        channelCount: Int,
        pcmEncoding: Int,
        target: RandomAccessFile?,
    ): Int {
        val destination = target ?: error("ملف WAV غير مفتوح")
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> error("ترميز PCM غير مدعوم على هذا الجهاز: $pcmEncoding")
        }
        val frameSize = bytesPerSample * channelCount
        if (frameSize <= 0 || size < frameSize) return 0

        val frames = size / frameSize
        val output = ByteArray(frames * 2)
        val input = source.duplicate().apply {
            position(offset)
            limit(offset + frames * frameSize)
            order(ByteOrder.LITTLE_ENDIAN)
        }

        var outIndex = 0
        repeat(frames) {
            var sum = 0.0
            repeat(channelCount) {
                val normalized = when (pcmEncoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> input.short.toDouble() / 32768.0
                    AudioFormat.ENCODING_PCM_FLOAT -> input.float.toDouble().coerceIn(-1.0, 1.0)
                    else -> 0.0
                }
                sum += normalized
            }
            val mono = (sum / channelCount).coerceIn(-1.0, 1.0)
            val sample = (mono * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[outIndex++] = (sample and 0xFF).toByte()
            output[outIndex++] = ((sample ushr 8) and 0xFF).toByte()
        }
        destination.write(output)
        return frames
    }

    private fun buildWavHeader(sampleRateHz: Int, pcmBytes: Long): ByteArray {
        require(sampleRateHz > 0)
        require(pcmBytes in 1..0xFFFF_FFFFL)
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRateHz * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffSize = pcmBytes + 36L

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(riffSize.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.toInt())
        }.array()
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
