package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.NvidiaSttDetailedEvidenceParser
import com.clw.aivideotranslator.NvidiaSttParserContract
import com.clw.aivideotranslator.NvidiaSttRequestProfile
import com.clw.aivideotranslator.NvidiaSttTransportObservation
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
import java.security.MessageDigest
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Fake transport: binds parts exactly the way the real response handler does. */
    private fun observation(
        wav: File,
        status: Int = 200,
        requestProfile: NvidiaSttRequestProfile = NvidiaSttWireContract.PROFILE,
        sampleSha256: String = sha256(wav),
    ) = NvidiaSttTransportObservation(
        requestProfile = requestProfile,
        parsed = detailedParse(status),
        sampleSha256 = sampleSha256,
    )

    private fun build(
        source: SourceAttachment,
        preparation: DetailedSttAudioPreparation,
        observation: NvidiaSttTransportObservation,
    ) = NvidiaSourceSnapshotFactory.buildUnverified(
        attachment = source,
        preparation = preparation,
        observation = observation,
    )

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("must fail closed")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test fun unverifiedSnapshotBindsExactTransportEvidenceAndDropsLegacyNormalizedOffsets() {
        val wav = createPcmWav(sampleRate = 16_000, channels = 1, bits = 16, frames = 16_000)
        try {
            val observed = observation(wav)
            assertEquals(100L, observed.parsed.result.words.first().startMs)
            assertEquals(800L, observed.parsed.result.words.last().endMs)
            assertEquals(NvidiaSttParserContract.ID, observed.parserVersion)

            val source = attachment()
            val snapshot = build(source, preparation(wav), observed)

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
            assertEquals(sha256(wav), snapshot.pcmSample.wavSha256)
            assertEquals(1_000_000L, snapshot.pcmSample.derivedPcmDurationUs)
            assertEquals(NvidiaSttWireContract.PROFILE.providerId, snapshot.stt.providerId)
            assertEquals(NvidiaSttWireContract.PROFILE.modelId, snapshot.stt.modelId)
            assertEquals(NvidiaSttWireContract.PROFILE.profileId, snapshot.stt.requestProfileId)
            assertEquals(observed.parserVersion, snapshot.stt.parserVersion)
            assertEquals(observed.rawResponseSha256, snapshot.stt.rawResponseSha256)
            assertEquals(200, snapshot.stt.httpStatus)
            assertNull(snapshot.clock.precisionUs)
            assertNull(snapshot.clock.evidenceProfile)
        } finally {
            wav.delete()
        }
    }

    @Test fun responseObservedUnderNonCurrentProfileCannotMintDurableSnapshot() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            val foreignProfile = NvidiaSttWireContract.PROFILE.copy(language = "en-GB")
            rejected { build(attachment(), preparation(wav), observation(wav, requestProfile = foreignProfile)) }
        } finally {
            wav.delete()
        }
    }

    @Test fun responseForOneSampleCannotBePairedWithAnotherSamplesProvenance() {
        val wavA = createPcmWav(16_000, 1, 16, 16_000)
        val wavB = createPcmWav(16_000, 1, 16, 8_000)
        try {
            // Observation truthfully bound to sample B must not mint a snapshot over preparation A.
            val observationOfB = observation(wavB, sampleSha256 = sha256(wavB))
            rejected { build(attachment(), preparation(wavA), observationOfB) }

            // A caller asserting A's bytes while carrying B's actual digest must also fail closed.
            rejected { build(attachment(), preparation(wavB), observationOfB) }

            // Garbage digests never pass the content binding either.
            rejected { build(attachment(), preparation(wavA), observation(wavA, sampleSha256 = "d".repeat(64))) }
        } finally {
            wavA.delete()
            wavB.delete()
        }
    }

    @Test fun attachmentMustOwnTheExactInputTrackActuallyDecoded() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            val source = attachment()
            listOf(
                SttInputAudioTrack(3, "audio/mp4a-latm", "en", 48_000, 2),
                SttInputAudioTrack(2, "audio/opus", "en", 48_000, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "fr", 48_000, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "en", 44_100, 2),
                SttInputAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 1),
            ).forEach { observed ->
                rejected { build(source, preparation(wav, track = observed), observation(wav)) }
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
            val snapshot = build(source, preparation(wav), observation(wav))
            assertEquals(source.attachmentId, snapshot.sourceAttachmentId)
        } finally {
            wav.delete()
        }
    }

    @Test fun wavHeaderFrameCountAndProfileArithmeticMustAllDescribeTheSameBytes() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            rejected { build(attachment(), preparation(wav, frameCount = 15_999), observation(wav)) }

            val bytes = wav.readBytes()
            bytes[8] = 'X'.code.toByte()
            wav.writeBytes(bytes)
            rejected { build(attachment(), preparation(wav), observation(wav)) }
        } finally {
            wav.delete()
        }
    }

    @Test fun onlySuccessfulAcceptedSttCanBecomeDurableSourceSnapshot() {
        val wav = createPcmWav(16_000, 1, 16, 16_000)
        try {
            rejected { build(attachment(), preparation(wav), observation(wav, status = 500)) }
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
            val snapshot = build(outsideSelectedRange, preparation(wav), observation(wav))
            assertEquals(500_000L, snapshot.clock.observedPresentationOriginUs)

            val basePreparation = preparation(wav)
            val invalidOrigin = basePreparation.copy(profile = basePreparation.profile.copy(
                sourceStartUs = 10_000_000,
                sourceEndUs = 11_000_000,
            ))
            rejected { build(attachment(), invalidOrigin, observation(wav)) }
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
            putShort(1.toShort())
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
