package com.clw.aivideotranslator.pipeline

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.clw.aivideotranslator.SttInputAudioTrack
import com.clw.aivideotranslator.session.RetainedSessionSource
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class AudioWindowProducerProgress(
    val decodedFrames: Long,
    val outputSampleRateHz: Int,
    val finalizedWindows: Int,
    val elapsedMs: Long,
) {
    init {
        require(decodedFrames >= 0L)
        require(outputSampleRateHz > 0)
        require(finalizedWindows >= 0)
        require(elapsedMs >= 0L)
    }

    val decodedAudioMs: Long get() = decodedFrames * 1_000L / outputSampleRateHz
}

internal data class StreamingAudioDecodeSummary(
    val inputTrack: SttInputAudioTrack,
    val outputSampleRateHz: Int,
    val observedFirstTrackPresentationUs: Long,
    val decodedFrames: Long,
    val finalizedWindows: Int,
    val elapsedMs: Long,
) {
    init {
        require(outputSampleRateHz > 0)
        require(observedFirstTrackPresentationUs >= 0L)
        require(decodedFrames > 0L)
        require(finalizedWindows > 0)
        require(elapsedMs >= 0L)
    }
}

/**
 * One-pass decoder from a verified app-private source into bounded STT windows.
 *
 * It never creates a full-video WAV and never interprets provider timing. The first compressed-track
 * PTS is preserved only as an observation for X001 clock evidence; window identity itself is sample
 * frame based. The finalized-window callback is synchronous so the caller can enforce bounded queue
 * backpressure before this decoder produces more temporary media.
 */
internal object StreamingSttAudioWindowProducer {
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val DEFAULT_WINDOW_US = 60_000_000L
    private const val PROGRESS_MIN_INTERVAL_MS = 250L

    fun produce(
        retainedSource: RetainedSessionSource,
        outputDir: File,
        windowDurationUs: Long = DEFAULT_WINDOW_US,
        onWindowFinalized: (ProductionSttAudioWindow) -> Unit,
        onProgress: (AudioWindowProducerProgress) -> Unit = {},
    ): Result<StreamingAudioDecodeSummary> = runCatching {
        val attachment = retainedSource.attachment
        require(attachment.selectedRange.start.value == 0L &&
            attachment.selectedRange.end.value == attachment.durationUs
        ) { "streaming STT producer currently requires the complete source range" }
        require(retainedSource.file.length() == attachment.fingerprint.sizeBytes) {
            "retained source size changed before decode"
        }
        require(outputDir.mkdirs() || outputDir.isDirectory) { "cannot create streaming STT directory" }

        val startedAt = SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        var writer: PcmWindowFileWriter? = null
        var outputSampleRate = 0
        var outputChannels = 0
        var outputPcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var observedTrack: SttInputAudioTrack? = null
        var firstTrackPtsUs = -1L
        var decodedFrames = 0L
        var finalizedWindows = 0
        var lastProgressAt = startedAt

        fun publishProgress(force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastProgressAt < PROGRESS_MIN_INTERVAL_MS) return
            if (outputSampleRate <= 0) return
            lastProgressAt = now
            onProgress(
                AudioWindowProducerProgress(
                    decodedFrames = decodedFrames,
                    outputSampleRateHz = outputSampleRate,
                    finalizedWindows = finalizedWindows,
                    elapsedMs = now - startedAt,
                ),
            )
        }

        try {
            extractor.setDataSource(retainedSource.file.absolutePath)
            val audioTrackIndex = findAudioTrack(extractor)
            require(audioTrackIndex >= 0) { "retained source has no decodable audio track" }
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME)) {
                "retained source audio track has no MIME"
            }
            val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val language = runCatching {
                if (inputFormat.containsKey(MediaFormat.KEY_LANGUAGE)) inputFormat.getString(MediaFormat.KEY_LANGUAGE) else null
            }.getOrNull()
            val track = SttInputAudioTrack(
                containerIndex = audioTrackIndex,
                mime = mime,
                language = language,
                sampleRateHz = inputSampleRate,
                channelCount = inputChannelCount,
            )
            requireTrackMatchesAttachment(track, attachment.audioTrack)
            observedTrack = track

            extractor.selectTrack(audioTrackIndex)
            firstTrackPtsUs = extractor.sampleTime
            require(firstTrackPtsUs >= 0L) { "retained source has no first audio sample" }

            outputSampleRate = inputSampleRate
            outputChannels = inputChannelCount
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var sourceExhausted = false
            var outputEnded = false
            var lastQueuedRelativePtsUs = 0L

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(decoder.getInputBuffer(inputIndex)) {
                            "cannot access STT decoder input buffer"
                        }
                        inputBuffer.clear()
                        val sampleTimeUs = extractor.sampleTime
                        if (sourceExhausted || sampleTimeUs < 0L) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                lastQueuedRelativePtsUs,
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
                                    lastQueuedRelativePtsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                val relativePtsUs = sampleTimeUs - firstTrackPtsUs
                                require(relativePtsUs >= 0L) { "audio sample PTS moved before observed origin" }
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, relativePtsUs, 0)
                                lastQueuedRelativePtsUs = relativePtsUs
                                if (!extractor.advance()) sourceExhausted = true
                            }
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> publishProgress()
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        val nextRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val nextChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val nextEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        if (decodedFrames > 0L) {
                            require(nextRate == outputSampleRate && nextChannels == outputChannels &&
                                nextEncoding == outputPcmEncoding
                            ) { "decoder PCM format changed after streaming output began" }
                        }
                        outputSampleRate = nextRate
                        outputChannels = nextChannels
                        outputPcmEncoding = nextEncoding
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            require(outputSampleRate > 0 && outputChannels > 0) { "invalid decoder PCM format" }
                            val monoBytes = downmixToMonoPcm16(
                                source = outputBuffer,
                                offset = info.offset,
                                size = info.size,
                                channelCount = outputChannels,
                                pcmEncoding = outputPcmEncoding,
                            )
                            if (monoBytes.isNotEmpty()) {
                                if (writer == null) {
                                    writer = PcmWindowFileWriter(
                                        outputDir = outputDir,
                                        sampleRateHz = outputSampleRate,
                                        windowDurationUs = windowDurationUs,
                                        observedFirstTrackPresentationUs = firstTrackPtsUs,
                                    ) { window ->
                                        finalizedWindows += 1
                                        onWindowFinalized(window)
                                        publishProgress(force = true)
                                    }
                                }
                                writer!!.append(monoBytes)
                                decodedFrames = Math.addExact(decodedFrames, monoBytes.size.toLong() / 2L)
                                publishProgress()
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val exactWriter = requireNotNull(writer) { "decoder produced no PCM for STT" }
            exactWriter.finish()
            require(decodedFrames == exactWriter.totalFramesWritten()) {
                "streaming STT writer frame count mismatch"
            }
            publishProgress(force = true)
        } catch (error: Throwable) {
            runCatching { writer?.close() }
            throw error
        } finally {
            extractor.release()
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
        }

        require(decodedFrames > 0L && finalizedWindows > 0) { "streaming STT decode produced no windows" }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        StreamingAudioDecodeSummary(
            inputTrack = requireNotNull(observedTrack),
            outputSampleRateHz = outputSampleRate,
            observedFirstTrackPresentationUs = firstTrackPtsUs,
            decodedFrames = decodedFrames,
            finalizedWindows = finalizedWindows,
            elapsedMs = elapsed,
        )
    }

    private fun requireTrackMatchesAttachment(
        observed: SttInputAudioTrack,
        expected: com.clw.aivideotranslator.session.SourceAudioTrack,
    ) {
        require(observed.containerIndex == expected.containerIndex) { "retained audio track index changed" }
        require(observed.mime == expected.mime) { "retained audio MIME changed" }
        expected.sampleRateHz?.let { require(observed.sampleRateHz == it) { "retained audio sample rate changed" } }
        expected.channelCount?.let { require(observed.channelCount == it) { "retained audio channel count changed" } }
    }

    private fun downmixToMonoPcm16(
        source: ByteBuffer,
        offset: Int,
        size: Int,
        channelCount: Int,
        pcmEncoding: Int,
    ): ByteArray {
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> error("unsupported decoder PCM encoding: $pcmEncoding")
        }
        val frameSize = Math.multiplyExact(bytesPerSample, channelCount)
        if (frameSize <= 0 || size < frameSize) return ByteArray(0)
        val frames = size / frameSize
        val input = source.duplicate().apply {
            position(offset)
            limit(offset + frames * frameSize)
            order(ByteOrder.LITTLE_ENDIAN)
        }
        val output = ByteArray(Math.multiplyExact(frames, 2))
        var out = 0
        repeat(frames) {
            var sum = 0.0
            repeat(channelCount) {
                sum += when (pcmEncoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> input.short.toDouble() / 32768.0
                    AudioFormat.ENCODING_PCM_FLOAT -> input.float.toDouble().coerceIn(-1.0, 1.0)
                    else -> 0.0
                }
            }
            val mono = (sum / channelCount).coerceIn(-1.0, 1.0)
            val sample = (mono * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[out++] = (sample and 0xff).toByte()
            output[out++] = ((sample ushr 8) and 0xff).toByte()
        }
        return output
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
