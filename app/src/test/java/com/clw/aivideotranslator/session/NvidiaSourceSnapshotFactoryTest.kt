package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.NvidiaSttDetailedEvidenceParser
import com.clw.aivideotranslator.NvidiaSttParserContract
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.SttAudioPreparationProvenance
import com.clw.aivideotranslator.SttAudioProfile
import com.clw.aivideotranslator.SttInputAudioTrack
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NvidiaSourceSnapshotFactoryTest {
    private fun attachment() = SourceAttachment(
        sessionId = "session-1",
        contentUri = "content://synthetic.documents/video/1",
        persistedReadGrantAtCapture = true,
        fingerprint = SourceFingerprint("a".repeat(64), 123_456),
        durationUs = 10_000_000,
        selectedRange = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(5_000_000)),
        audioTrack = SourceAudioTrack(
            containerIndex = 2,
            mime = "audio/mp4a-latm",
            language = "en",
            sampleRateHz = 48_000,
            channelCount = 2,
        ),
    )

    private fun detailedParse(status: Int = 200) = NvidiaSttDetailedEvidenceParser.parse(
        """
        {
          "text": "Hello world.",
          "words": [
            {"word":"Hello","start":0.10,"end":0.25,"confidence":0.9},
            {"word":"world.","start":0.30,"end":0.80,"confidence":0.8}
          ]
        }
        """.trimIndent(),
        status,
    )

    private fun preparation(
        file: File,
        frameCount: Long = 16_000,
        track: SttInputAudioTrack = SttInputAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    ) = DetailedSttAudioPreparation(
        profile = SttAudioProfile(
            file = file,
            sampleRateHz = 16_000,
            channelCount = 1,
            bitsPerSample = 16,
            sourceStartUs = 500_000,
            sourceEndUs = 1_500_000,
            durationMs = 1_000,
        ),
        provenance = SttAudioPreparationProvenance(track, frameCount),
    )

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("must fail closed")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test fun unverifiedSnapshotBindsExactEvidenceAndDropsLegacyNormalizedOffsets() {
        val wav = createPcmWav(sampleRate = 16_000, channels = 1, bits = 16, frames = 16_000)
        try {
            val parsed = detailedParse()
            assertEquals(100L, parsed.result.words.first().startMs)
            assertEquals(800L, parsed.result.words.last().endMs)

            val source = attachment()
            val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(source, preparation(wav), parsed)

            assertEquals(source.sessionId, snapshot.sessionId)
            assertEquals(source.attachmentId, snapshot.sourceAttachmentId)
            assertEquals("Hello world.", snapshot.transcript)
            assertEquals(listOf("Hello", "world."), snapshot.words.map { it.rawText })
            assertTrue(snapshot.words.all { it.audioInterval == null })
            assertEquals(ClockVerificationStatus.UNVERIFIED, snapshot.clock.verificationStatus)
            assertEquals(500_000L, snapshot.clock.observedPresentationOriginUs)
            assertEquals(16_000L, snapshot.pcmSample.pcmFrameCount)
            assertEquals(16_000, snapshot.pcmSample.sampleRateHz)
            assertEquals(1, snapshot.pcmSample.channelCount)
            assertEquals(16, snapshot.pcmSample.bitsPerSample)
            assertEquals(wav.length(), snapshot.pcmSample.wavSizeBytes)
            assertEquals(1_000_000L, snapshot.pcmSample.derivedPcmDurationUs)
            assertEquals(NvidiaSttWireContract.PROFILE.providerId, snapshot.stt.providerId)
            assertEquals(NvidiaSttWireContract.PROFILE.modelId, snapshot.stt.modelId)
            assertEquals(NvidiaSttWireContract.PROFILE.profileId, snapshot.stt.requestProfileId)
            assertEquals(NvidiaSttParserContract.ID, snapshot.stt.parserVersion)
            assertEquals(parsed.timingEvidence.rawResponseSha256, snapshot.stt.rawResponseSha256)
            assertEquals(200, snapshot.stt.httpStatus)
            assertNull(snapshot.clock.precisionUs)
            assertNull(snapshot.clock.evidenceProfile)
        } finally {
            wav.delete()
        }
    }

    @Test fun attachmentMustOwnTheExactInputTrackActuallyDecoded() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            val source = attachment()
            val parsed = detailedParse()
            listOf(
                SttInputAudioTrack(3, "audio/mp4a-latm", "en", 48_000, 2),
                SttInputAudioTrack(2, "audio/opus", "en", 48_000, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "fr", 48_000, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "en", 44_100, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 1),
            ).forEach { observed ->
                rejected { NvidiaSourceSnapshotFactory.buildUnverified(source, preparation(wav, track = observed), parsed) }
            }
        } finally {
            wav.delete()
        }
    }

    @Test fun unknownAttachmentTrackMetadataMayBeFilledByActualPreparationEvidence() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            val source = attachment().copy(audioTrack = attachment().audioTrack.copy(
                language = null,
                sampleRateHz = null,
                channelCount = null,
            ))
            val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(source, preparation(wav), detailedParse())
            assertEquals(source.attachmentId, snapshot.sourceAttachmentId)
        } finally {
            wav.delete()
        }
    }

    @Test fun wavHeaderFrameCountAndProfileArithmeticMustAllDescribeTheSameBytes() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            rejected { NvidiaSourceSnapshotFactory.buildUnverified(attachment(), preparation(wav, frameCount = 15_999), detailedParse()) }

            val bytes = wav.readBytes()
            bytes[8] = 'X'.code.toByte()
            wav.writeBytes(bytes)
            rejected { NvidiaSourceSnapshotFactory.buildUnverified(attachment(), preparation(wav), detailedParse()) }
        } finally {
            wav.delete()
        }
    }

    @Test fun onlySuccessfulAcceptedSttCanBecomeDurableSourceSnapshot() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            rejected { NvidiaSourceSnapshotFactory.buildUnverified(attachment(), preparation(wav), detailedParse(500)) }
        } finally {
            wav.delete()
        }
    }

    @Test fun sourceOriginMustBeInsideKnownSourceDurationButDoesNotInventSelectedRangeOwnership() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            val outsideSelectedRange = attachment().copy(
                selectedRange = PresentationIntervalUs(PresentationTimeUs(2_000_000), PresentationTimeUs(4_000_000))
            )
            val snapshot = NvidiaSourceSnapshotFactory.buildUnverified(outsideSelectedRange, preparation(wav), detailedParse())
            assertEquals(500_000L, snapshot.clock.observedPresentationOriginUs)

            val invalidOrigin = preparation(wav).copy(profile = preparation(wav).profile.copy(
                sourceStartUs = 10_000_000,
                sourceEndUs = 11_000_000,
            ))
            rejected { NvidiaSourceSnapshotFactory.buildUnverified(attachment(), invalidOrigin, detailedParse()) }
        } finally {
            wav.delete()
        }
    }

    private fun createPcmWav(
        sampleRate: Int,
        channels: Int,
        bits: Int,
        frames: Int,
    ): File {
        val bytesPerSample = bits / 8
        val dataBytes = frames * channels * bytesPerSample
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes + 36)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * bytesPerSample)
            putShort((channels * bytesPerSample).toShort())
            putShort(bits.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
        return File.createTempFile("source-snapshot-factory", ".wav").apply {
            outputStream().use { out ->
                out.write(header)
                out.write(ByteArray(dataBytes) { index -> (index and 0x7F).toByte() })
            }
        }
    }
}
