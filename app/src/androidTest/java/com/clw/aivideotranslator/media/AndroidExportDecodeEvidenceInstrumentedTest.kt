package com.clw.aivideotranslator.media

import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidExportDecodeEvidenceInstrumentedTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "x004-decode-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun syntheticH264AacFixtureHasAcceptedContainerAndDecodedColorTransition() {
        val file = materializeFixture()
        val observation = AndroidExportMediaInspector.inspect(file)
        val validation = ExportValidator.evaluate(
            observation = observation,
            expectedDurationUs = 1_200_000L,
            sourceHasAudio = true,
        )

        assertTrue(observation.readable)
        assertTrue(validation.failures.toString(), validation.isAccepted)
        assertEquals(1, observation.tracks.count { it.mime == ExportValidator.VIDEO_H264_MIME })
        assertEquals(1, observation.tracks.count { it.mime == ExportValidator.AUDIO_AAC_MIME })

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val redFrame = requireNotNull(
                retriever.getFrameAtTime(100_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ) { "red frame did not decode" }
            val blueFrame = requireNotNull(
                retriever.getFrameAtTime(900_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ) { "blue frame did not decode" }
            try {
                assertEquals(64, redFrame.width)
                assertEquals(64, redFrame.height)
                assertEquals(64, blueFrame.width)
                assertEquals(64, blueFrame.height)
                val redPixel = redFrame.getPixel(redFrame.width / 2, redFrame.height / 2)
                val bluePixel = blueFrame.getPixel(blueFrame.width / 2, blueFrame.height / 2)
                assertTrue("first decoded frame is not red-dominant", Color.red(redPixel) > Color.blue(redPixel) + 80)
                assertTrue("later decoded frame is not blue-dominant", Color.blue(bluePixel) > Color.red(bluePixel) + 80)
            } finally {
                redFrame.recycle()
                blueFrame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    @Test
    fun syntheticAacTrackDecodesToPcmOnDevice() {
        val file = materializeFixture()
        val evidence = decodeAudio(file)

        assertTrue("AAC decoder produced no PCM", evidence.decodedBytes > 0L)
        assertTrue("AAC decoder never reached EOS", evidence.outputEos)
        assertNotNull(evidence.outputFormat)
        assertEquals(16_000, evidence.outputFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertEquals(1, evidence.outputFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
    }

    private fun materializeFixture(): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val encoded = testContext.assets.open("x004/red_blue_tone_64.mp4.b64")
            .bufferedReader(Charsets.US_ASCII)
            .use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        return File(root, "red_blue_tone_64.mp4").apply { writeBytes(bytes) }
    }

    private fun decodeAudio(file: File): AudioDecodeEvidence {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("fixture has no audio track")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var inputEos = false
            var outputEos = false
            var decodedBytes = 0L
            var outputFormat: MediaFormat? = null
            val info = MediaCodec.BufferInfo()
            val deadlineNs = System.nanoTime() + 5_000_000_000L

            while (!outputEos && System.nanoTime() < deadlineNs) {
                if (!inputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        decodedBytes += info.size.toLong()
                        outputEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            return AudioDecodeEvidence(
                decodedBytes = decodedBytes,
                outputEos = outputEos,
                outputFormat = outputFormat,
            )
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private data class AudioDecodeEvidence(
        val decodedBytes: Long,
        val outputEos: Boolean,
        val outputFormat: MediaFormat?,
    )
}
