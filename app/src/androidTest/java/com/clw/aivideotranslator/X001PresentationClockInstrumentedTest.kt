package com.clw.aivideotranslator

import android.media.MediaCodec
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
 * as the first audio track with a +500 ms presentation offset. A second one-packet audio anchor at
 * presentation zero prevents MediaMuxer from normalizing the only track back to zero. The test
 * observes the delayed first-track offset independently through MediaExtractor, runs the production
 * STT preparer, and proves that sample-zero in the prepared WAV maps back to the nonzero presentation
 * origin rather than silently assuming presentation zero.
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
        val delayed = File(root, "tone_a_delayed.m4a")
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
            evidenceProfile = "android-mediamuxer-mediaextractor-delayed-aac-v2",
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
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(source.absolutePath)
            val inputTrack = findAudioTrack(extractor)
            val format = extractor.getTrackFormat(inputTrack)
            extractor.selectTrack(inputTrack)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // Keep the delayed source as container track zero because SttAudioPreparer deliberately
            // selects the first audio track. The anchor track exists only to establish container time
            // zero so MediaMuxer cannot rebase the delayed track's first sample back to zero.
            val delayedTrack = muxer.addTrack(format)
            val anchorTrack = muxer.addTrack(format)
            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(MAX_ENCODED_SAMPLE_BYTES)
            val delayedInfo = MediaCodec.BufferInfo()
            val anchorInfo = MediaCodec.BufferInfo()
            var anchorWritten = false
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val sourcePtsUs = extractor.sampleTime
                require(sourcePtsUs >= 0L) { "fixture packet has no presentation time" }
                val flags = extractor.sampleFlags

                if (!anchorWritten) {
                    buffer.position(0)
                    buffer.limit(size)
                    anchorInfo.set(0, size, 0L, flags)
                    muxer.writeSampleData(anchorTrack, buffer, anchorInfo)
                    anchorWritten = true
                }

                buffer.position(0)
                buffer.limit(size)
                delayedInfo.set(
                    0,
                    size,
                    Math.addExact(sourcePtsUs, offsetUs),
                    flags,
                )
                muxer.writeSampleData(delayedTrack, buffer, delayedInfo)
                if (!extractor.advance()) break
            }
            require(anchorWritten) { "fixture had no encoded packet for the presentation-zero anchor" }
        } finally {
            extractor.release()
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        require(output.isFile && output.length() > 0L) { "failed to build delayed AAC fixture" }
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

    private companion object {
        const val DELAY_US = 500_000L
        const val WAV_HEADER_BYTES = 44
        const val MAX_ENCODED_SAMPLE_BYTES = 256 * 1024
    }
}
