package com.clw.aivideotranslator

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X001 Android presentation-origin falsifier.
 *
 * Reuses the existing synthetic 440 Hz AAC source-capture fixture, then remuxes its encoded packets
 * as the only audio track with a +500 ms presentation offset. A real AVC video sample at presentation
 * zero anchors the container timeline without introducing a second audio track that MediaExtractor or
 * SttAudioPreparer could select. The test observes the delayed audio origin independently through
 * MediaExtractor, runs the production STT preparer, and proves that sample-zero in the prepared WAV
 * maps back to the nonzero presentation origin rather than silently assuming presentation zero.
 */
@RunWith(AndroidJUnit4::class)
class X001PresentationClockInstrumentedTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(targetContext.cacheDir, "x001-clock-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun delayedContainerPtsBecomesPreparedSamplePresentationOrigin() {
        val original = copyFixture("tone_a.m4a")
        val delayed = File(root, "tone_a_delayed.mp4")
        remuxAudioWithPresentationOffset(original, delayed, DELAY_US)

        val independentlyObservedStartUs = firstAudioSampleTimeUs(delayed)
        assertEquals(DELAY_US, independentlyObservedStartUs)

        val wav = File(root, "prepared.wav")
        val preparation = SttAudioPreparer.preparePrivateSourceFirstMinute(delayed, wav).getOrThrow()
        val profile = preparation.profile

        assertEquals(independentlyObservedStartUs, profile.sourceStartUs)
        assertEquals(48_000, profile.sampleRateHz)
        assertEquals(1, profile.channelCount)
        assertEquals(
            profile.sourceStartUs + preparation.provenance.pcmFrameCount * 1_000_000L / profile.sampleRateHz,
            profile.sourceEndUs,
        )

        // If the preparer had preserved the 500 ms container offset as leading PCM silence, this
        // first 100 ms window would be quiet. Instead, decode rebases the first packet to sample-zero.
        assertTrue("prepared sample must begin with the synthetic tone", firstWindowRms(wav, profile.sampleRateHz) > 500.0)

        val verifiedClock = SampleClockMap(
            presentationOrigin = PresentationTimeUs(independentlyObservedStartUs),
            precisionUs = 1L,
            status = ClockVerificationStatus.VERIFIED_AFFINE,
            evidenceProfile = "android-mediamuxer-video-anchor-delayed-aac-v3",
        )
        val mapped = verifiedClock.mapVerified(
            AudioIntervalUs(AudioTimeUs(0L), AudioTimeUs(100_000L))
        )
        assertEquals(DELAY_US, mapped.start.value)
        assertEquals(DELAY_US + 100_000L, mapped.end.value)
    }

    private fun copyFixture(name: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val output = File(root, name)
        testContext.assets.open("source_capture/$name").use { input ->
            output.outputStream().use { target -> input.copyTo(target) }
        }
        return output
    }

    private fun firstAudioSampleTimeUs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = findAudioTrack(extractor)
            extractor.selectTrack(trackIndex)
            return extractor.sampleTime
        } finally {
            extractor.release()
        }
    }

    private fun remuxAudioWithPresentationOffset(source: File, output: File, offsetUs: Long) {
        val extractor = MediaExtractor()
        val videoAnchor = encodeVideoAnchor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(source.absolutePath)
            val inputTrack = findAudioTrack(extractor)
            val audioFormat = extractor.getTrackFormat(inputTrack)
            extractor.selectTrack(inputTrack)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val delayedAudioTrack = muxer.addTrack(audioFormat)
            val videoTrack = muxer.addTrack(videoAnchor.format)
            muxer.start()
            started = true

            val videoBuffer = ByteBuffer.wrap(videoAnchor.bytes)
            val videoInfo = MediaCodec.BufferInfo().apply {
                set(0, videoAnchor.bytes.size, 0L, videoAnchor.flags)
            }
            muxer.writeSampleData(videoTrack, videoBuffer, videoInfo)

            val buffer = ByteBuffer.allocate(MAX_ENCODED_SAMPLE_BYTES)
            val audioInfo = MediaCodec.BufferInfo()
            var audioPackets = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val sourcePtsUs = extractor.sampleTime
                require(sourcePtsUs >= 0L) { "fixture packet has no presentation time" }
                buffer.position(0)
                buffer.limit(size)
                audioInfo.set(
                    0,
                    size,
                    Math.addExact(sourcePtsUs, offsetUs),
                    extractor.sampleFlags,
                )
                muxer.writeSampleData(delayedAudioTrack, buffer, audioInfo)
                audioPackets += 1
                if (!extractor.advance()) break
            }
            require(audioPackets > 0) { "fixture had no encoded audio packets" }
        } finally {
            extractor.release()
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        require(output.isFile && output.length() > 0L) { "failed to build delayed audio/video fixture" }
    }

    private fun encodeVideoAnchor(): EncodedVideoAnchor {
        val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
        try {
            val capabilities = codec.codecInfo.getCapabilitiesForType(VIDEO_MIME)
            val colorFormat = listOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            ).firstOrNull { it in capabilities.colorFormats }
                ?: error("device AVC encoder has no byte-buffer YUV420 input format")

            val inputFormat = MediaFormat.createVideoFormat(VIDEO_MIME, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(inputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val inputIndex = awaitInputBuffer(codec)
            val input = requireNotNull(codec.getInputBuffer(inputIndex))
            input.clear()
            val frameBytes = VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2
            repeat(VIDEO_WIDTH * VIDEO_HEIGHT) { input.put(16.toByte()) }
            repeat(frameBytes - VIDEO_WIDTH * VIDEO_HEIGHT) { input.put(128.toByte()) }
            codec.queueInputBuffer(inputIndex, 0, frameBytes, 0L, 0)

            val eosIndex = awaitInputBuffer(codec)
            codec.queueInputBuffer(
                eosIndex,
                0,
                0,
                33_333L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )

            var outputFormat: MediaFormat? = null
            var encodedSample: ByteArray? = null
            var encodedFlags = 0
            val info = MediaCodec.BufferInfo()
            repeat(MAX_CODEC_DRAIN_LOOPS) {
                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val encoded = requireNotNull(codec.getOutputBuffer(outputIndex))
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && encodedSample == null) {
                            val bytes = ByteArray(info.size)
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            encoded.get(bytes)
                            encodedSample = bytes
                            encodedFlags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
                val format = outputFormat
                val sample = encodedSample
                if (format != null && sample != null) {
                    return EncodedVideoAnchor(format, sample, encodedFlags)
                }
            }
            error("AVC anchor encoder did not emit a format and sample")
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun awaitInputBuffer(codec: MediaCodec): Int {
        repeat(MAX_CODEC_DRAIN_LOOPS) {
            val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (index >= 0) return index
        }
        error("AVC anchor encoder did not expose an input buffer")
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("fixture has no audio track")

    private fun firstWindowRms(wav: File, sampleRateHz: Int): Double {
        val bytes = wav.readBytes()
        require(bytes.size > WAV_HEADER_BYTES) { "prepared WAV is empty" }
        val wantedSamples = sampleRateHz / 10
        val availableSamples = (bytes.size - WAV_HEADER_BYTES) / 2
        val count = minOf(wantedSamples, availableSamples)
        require(count > 0) { "prepared WAV has no PCM samples" }
        val pcm = ByteBuffer.wrap(bytes, WAV_HEADER_BYTES, count * 2).order(ByteOrder.LITTLE_ENDIAN)
        var sumSquares = 0.0
        repeat(count) {
            val sample = pcm.short.toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count.toDouble())
    }

    private data class EncodedVideoAnchor(
        val format: MediaFormat,
        val bytes: ByteArray,
        val flags: Int,
    )

    private companion object {
        const val DELAY_US = 500_000L
        const val WAV_HEADER_BYTES = 44
        const val MAX_ENCODED_SAMPLE_BYTES = 256 * 1024
        const val VIDEO_MIME = "video/avc"
        const val VIDEO_WIDTH = 16
        const val VIDEO_HEIGHT = 16
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_CODEC_DRAIN_LOOPS = 500
    }
}
