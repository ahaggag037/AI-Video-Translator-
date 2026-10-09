package com.clw.aivideotranslator.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X004 decoded-device audio evidence.
 *
 * The source-capture fixtures are synthetic 48 kHz mono AAC tones with known provenance:
 * tone_a = 440 Hz and tone_b = 880 Hz. This test deliberately uses the platform decoder rather
 * than container metadata, proving that Android can recover non-silent PCM markers and that the
 * two known markers remain distinguishable. It does not claim export parity by itself.
 */
@RunWith(AndroidJUnit4::class)
class DecodedAudioEvidenceInstrumentedTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(targetContext.cacheDir, "x004-decoded-audio-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun syntheticAacMarkersDecodeToDistinctExpectedFrequencies() {
        val tone440 = copyFixture("tone_a.m4a")
        val tone880 = copyFixture("tone_b.m4a")

        val decoded440 = decodePcm16(tone440)
        val decoded880 = decodePcm16(tone880)

        assertEquals(48_000, decoded440.sampleRateHz)
        assertEquals(48_000, decoded880.sampleRateHz)
        assertEquals(1, decoded440.channelCount)
        assertEquals(1, decoded880.channelCount)
        assertTrue(decoded440.samples.size >= 40_000)
        assertTrue(decoded880.samples.size >= 40_000)
        assertTrue(decoded440.rms > 500.0)
        assertTrue(decoded880.rms > 500.0)

        val frequency440 = estimateFrequencyHz(decoded440.samples, decoded440.sampleRateHz)
        val frequency880 = estimateFrequencyHz(decoded880.samples, decoded880.sampleRateHz)

        assertTrue("decoded 440 Hz marker drifted to $frequency440 Hz", abs(frequency440 - 440.0) <= 25.0)
        assertTrue("decoded 880 Hz marker drifted to $frequency880 Hz", abs(frequency880 - 880.0) <= 35.0)
        assertTrue("decoded markers must remain clearly distinct", frequency880 - frequency440 >= 350.0)
    }

    private fun copyFixture(name: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val output = File(root, name)
        testContext.assets.open("source_capture/$name").use { input ->
            output.outputStream().use { target -> input.copyTo(target) }
        }
        return output
    }

    private fun decodePcm16(file: File): DecodedPcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("fixture has no audio track")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val samples = ArrayList<Short>(64_000)
            var sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = requireNotNull(codec.getInputBuffer(inputIndex))
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputEnded) continue
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRateHz = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val pcm = output.slice().order(ByteOrder.LITTLE_ENDIAN)
                            while (pcm.remaining() >= 2) samples += pcm.short
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val array = ShortArray(samples.size) { samples[it] }
            return DecodedPcm(
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                samples = array,
                rms = rootMeanSquare(array),
            )
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun estimateFrequencyHz(samples: ShortArray, sampleRateHz: Int): Double {
        require(samples.size >= sampleRateHz / 2) { "not enough decoded PCM" }
        val start = minOf(samples.size / 10, sampleRateHz / 10)
        val end = maxOf(start + 2, samples.size - start)
        var crossings = 0
        var previous = samples[start].toInt()
        for (index in start + 1 until end) {
            val current = samples[index].toInt()
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) crossings += 1
            previous = current
        }
        val observedSamples = end - start
        return crossings.toDouble() * sampleRateHz.toDouble() / (2.0 * observedSamples.toDouble())
    }

    private fun rootMeanSquare(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        samples.forEach { sample ->
            val value = sample.toDouble()
            sum += value * value
        }
        return sqrt(sum / samples.size.toDouble())
    }

    private data class DecodedPcm(
        val sampleRateHz: Int,
        val channelCount: Int,
        val samples: ShortArray,
        val rms: Double,
    )

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
