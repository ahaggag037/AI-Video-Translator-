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
 * A one-track MP4 is allowed to normalize its first sample to presentation zero, so this fixture is
 * built with two copies of the same synthetic AAC track. The first/output track under test is shifted
 * by +500 ms; a second anchor track starts at zero and prevents the container timeline from rebasing
 * the delayed track. The test independently observes the first track PTS through MediaExtractor, then
 * runs the production STT preparer and proves that prepared-sample zero maps back to that nonzero
 * source presentation origin.
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
        val source = copyFixture("tone_a.m4a")
        val delayed = File(root, "tone_a_delayed_with_anchor.m4a")
        remuxDelayedTrackWithZeroAnchor(source, delayed, DELAY_US)

        assertTrue("fixture must contain both delayed and anchor audio tracks", audioTrackCount(delayed) >= 2)
        val independentlyObservedStartUs = firstAudioSampleTimeUs(delayed)
        assertTrue(
            "fixture must expose a clearly nonzero first-track presentation origin, observed=$independentlyObservedStartUs",
            independentlyObservedStartUs in MIN_EXPECTED_DELAY_US..MAX_EXPECTED_DELAY_US,
        )

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

        // Decoder output is sample-relative: the container delay must be represented by sourceStartUs,
        // not prepended as synthetic PCM silence.
        assertTrue(
            "prepared sample must begin with the synthetic tone",
            firstWindowRms(wav, profile.sampleRateHz) > 500.0,
        )

        val verifiedClock = SampleClockMap(
            presentationOrigin = PresentationTimeUs(independentlyObservedStartUs),
            precisionUs = 1L,
            status = ClockVerificationStatus.VERIFIED_AFFINE,
            evidenceProfile = "android-mediamuxer-dual-audio-anchor-v1",
        )
        val mapped = verifiedClock.mapVerified(
            AudioIntervalUs(AudioTimeUs(0L), AudioTimeUs(100_000L))
        )
        assertEquals(independentlyObservedStartUs, mapped.start.value)
        assertEquals(independentlyObservedStartUs + 100_000L, mapped.end.value)
    }

    private fun copyFixture(name: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        return File(root, name).also { output ->
            testContext.assets.open("source_capture/$name").use { input ->
                output.outputStream().use { target -> input.copyTo(target) }
            }
        }
    }

    private fun remuxDelayedTrackWithZeroAnchor(source: File, output: File, delayUs: Long) {
        val probe = MediaExtractor()
        val format: MediaFormat
        try {
            probe.setDataSource(source.absolutePath)
            val inputTrack = findAudioTrack(probe)
            format = probe.getTrackFormat(inputTrack)
        } finally {
            probe.release()
        }

        var muxer: MediaMuxer? = null
        var started = false
        try {
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val delayedTrack = muxer.addTrack(format)
            val anchorTrack = muxer.addTrack(format)
            muxer.start()
            started = true

            writeEncodedTrack(source, muxer, delayedTrack, delayUs)
            writeEncodedTrack(source, muxer, anchorTrack, 0L)
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        require(output.isFile && output.length() > 0L) { "failed to build dual-track delayed AAC fixture" }
    }

    private fun writeEncodedTrack(source: File, muxer: MediaMuxer, outputTrack: Int, offsetUs: Long) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val inputTrack = findAudioTrack(extractor)
            extractor.selectTrack(inputTrack)
            val buffer = ByteBuffer.allocate(MAX_ENCODED_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val sourcePtsUs = extractor.sampleTime
                require(sourcePtsUs >= 0L) { "fixture packet has no presentation time" }
                info.set(
                    0,
                    size,
                    Math.addExact(sourcePtsUs, offsetUs),
                    extractor.sampleFlags,
                )
                muxer.writeSampleData(outputTrack, buffer, info)
                if (!extractor.advance()) break
            }
        } finally {
            extractor.release()
        }
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

    private fun audioTrackCount(file: File): Int {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return (0 until extractor.trackCount).count { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } finally {
            extractor.release()
        }
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
        const val MIN_EXPECTED_DELAY_US = 400_000L
        const val MAX_EXPECTED_DELAY_US = 600_000L
        const val WAV_HEADER_BYTES = 44
        const val MAX_ENCODED_SAMPLE_BYTES = 256 * 1024
    }
}
