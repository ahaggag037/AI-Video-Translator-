package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.NvidiaSttTransportObservation
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.SttInputAudioTrack
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Pure fail-closed adapter from already-observed source/audio/STT evidence into durable semantic truth.
 * This does not perform I/O against the source URI, submit a provider request, choose a track/range,
 * or authorize timing. Legacy normalized word offsets are deliberately discarded while X001 is open.
 * STT provenance is consumed as ONE transport-bound observation (profile + accepted parse + raw
 * response SHA-256 + transported-sample digest bound at the response-handling point); the factory
 * never accepts independently supplied provenance parts and re-verifies the transported sample
 * digest against its own independent inspection of the prepared WAV bytes.
 */
internal object NvidiaSourceSnapshotFactory {
    fun buildUnverified(
        attachment: SourceAttachment,
        preparation: DetailedSttAudioPreparation,
        observation: NvidiaSttTransportObservation,
    ): SourceSnapshot {
        val requestProfile = observation.requestProfile
        require(requestProfile == NvidiaSttWireContract.PROFILE) {
            "unsupported STT request profile for current snapshot adapter"
        }
        val parsed = observation.parsed
        require(parsed.parserVersion.isNotBlank()) { "missing accepted STT parser identity" }
        requireTrackOwnership(attachment.audioTrack, preparation.provenance.inputTrack)

        val profile = preparation.profile
        require(profile.sourceStartUs >= 0L && profile.sourceStartUs < attachment.durationUs) {
            "prepared sample origin is outside source duration"
        }
        require(profile.sampleRateHz > 0 && profile.channelCount > 0 && profile.bitsPerSample > 0) {
            "invalid prepared PCM profile"
        }
        require(profile.bitsPerSample % 8 == 0) { "non-byte-aligned prepared PCM is unsupported" }

        val pcmSample = inspectPreparedWav(
            file = profile.file,
            pcmFrameCount = preparation.provenance.pcmFrameCount,
            sampleRateHz = profile.sampleRateHz,
            channelCount = profile.channelCount,
            bitsPerSample = profile.bitsPerSample,
        )
        require(observation.sampleSha256 == pcmSample.wavSha256) {
            "transported STT sample does not match inspected WAV bytes"
        }
        val expectedEndUs = Math.addExact(profile.sourceStartUs, pcmSample.derivedPcmDurationUs)
        require(profile.sourceEndUs == expectedEndUs) { "prepared sample end does not match PCM duration" }
        val expectedDurationMs = Math.multiplyExact(pcmSample.pcmFrameCount, 1_000L) / pcmSample.sampleRateHz
        require(profile.durationMs == expectedDurationMs) { "prepared sample duration does not match PCM frames" }

        require(parsed.result.httpStatus in 200..299) { "accepted source snapshot requires successful STT HTTP status" }
        val words = parsed.result.words.mapIndexed { ordinal, word ->
            // startMs/endMs intentionally do not cross this boundary until X001 verifies the clock map.
            SourceSnapshotWord(
                ordinal = ordinal,
                rawText = word.text,
                confidence = word.confidence,
                audioInterval = null,
            )
        }

        return SourceSnapshot(
            sessionId = attachment.sessionId,
            sourceAttachmentId = attachment.attachmentId,
            transcript = parsed.result.transcript,
            words = words,
            pcmSample = pcmSample,
            stt = SourceSttProvenance(
                providerId = requestProfile.providerId,
                modelId = requestProfile.modelId,
                requestProfileId = requestProfile.profileId,
                parserVersion = parsed.parserVersion,
                rawResponseSha256 = parsed.timingEvidence.rawResponseSha256,
                httpStatus = parsed.result.httpStatus,
            ),
            clock = SourceClockProvenance(
                observedPresentationOriginUs = profile.sourceStartUs,
                verificationStatus = ClockVerificationStatus.UNVERIFIED,
            ),
        )
    }

    private fun requireTrackOwnership(expected: SourceAudioTrack, observed: SttInputAudioTrack) {
        require(expected.containerIndex == observed.containerIndex) { "prepared audio track index does not match attachment" }
        require(expected.mime == observed.mime) { "prepared audio MIME does not match attachment" }
        if (expected.language != null) {
            require(expected.language == observed.language) { "prepared audio language does not match attachment" }
        }
        if (expected.sampleRateHz != null) {
            require(expected.sampleRateHz == observed.sampleRateHz) { "prepared input sample rate does not match attachment" }
        }
        if (expected.channelCount != null) {
            require(expected.channelCount == observed.channelCount) { "prepared input channels do not match attachment" }
        }
    }

    private fun inspectPreparedWav(
        file: File,
        pcmFrameCount: Long,
        sampleRateHz: Int,
        channelCount: Int,
        bitsPerSample: Int,
    ): SourcePcmSampleIdentity {
        require(file.isFile) { "prepared WAV is missing" }
        val bytesPerSample = bitsPerSample / 8
        val bytesPerFrame = Math.multiplyExact(channelCount.toLong(), bytesPerSample.toLong())
        val dataBytes = Math.multiplyExact(pcmFrameCount, bytesPerFrame)
        require(dataBytes in 1..(0xFFFF_FFFFL - 36L)) { "prepared WAV exceeds RIFF32 bounds" }
        val expectedSize = Math.addExact(44L, dataBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(44)
        var headerCopied = 0
        var totalBytes = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                if (headerCopied < header.size) {
                    val copy = minOf(read, header.size - headerCopied)
                    buffer.copyInto(header, headerCopied, 0, copy)
                    headerCopied += copy
                }
                totalBytes = Math.addExact(totalBytes, read.toLong())
            }
        }
        require(headerCopied == header.size && totalBytes == expectedSize) { "prepared WAV size does not match PCM frames" }
        require(file.length() == totalBytes) { "prepared WAV changed while being inspected" }
        requirePcmWavHeader(header, dataBytes, sampleRateHz, channelCount, bitsPerSample)

        return SourcePcmSampleIdentity(
            wavSha256 = digest.digest().toHex(),
            wavSizeBytes = totalBytes,
            pcmFrameCount = pcmFrameCount,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
        )
    }

    private fun requirePcmWavHeader(
        header: ByteArray,
        dataBytes: Long,
        sampleRateHz: Int,
        channelCount: Int,
        bitsPerSample: Int,
    ) {
        fun ascii(offset: Int, size: Int) = String(header, offset, size, StandardCharsets.US_ASCII)
        val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(offset: Int): Int = bytes.getShort(offset).toInt() and 0xFFFF
        fun u32(offset: Int): Long = bytes.getInt(offset).toLong() and 0xFFFF_FFFFL

        val bytesPerFrame = Math.multiplyExact(channelCount.toLong(), (bitsPerSample / 8).toLong())
        val byteRate = Math.multiplyExact(sampleRateHz.toLong(), bytesPerFrame)
        require(byteRate <= 0xFFFF_FFFFL && bytesPerFrame <= 0xFFFF) {
            "prepared WAV header values exceed RIFF32 bounds"
        }
        require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE") { "prepared file is not RIFF/WAVE" }
        require(ascii(12, 4) == "fmt " && u32(16) == 16L) { "prepared WAV fmt chunk is unsupported" }
        require(u16(20) == 1) { "prepared WAV is not PCM" }
        require(u16(22) == channelCount) { "prepared WAV channel count mismatch" }
        require(u32(24) == sampleRateHz.toLong()) { "prepared WAV sample rate mismatch" }
        require(u32(28) == byteRate) { "prepared WAV byte rate mismatch" }
        require(u16(32).toLong() == bytesPerFrame) { "prepared WAV block alignment mismatch" }
        require(u16(34) == bitsPerSample) { "prepared WAV bit depth mismatch" }
        require(ascii(36, 4) == "data" && u32(40) == dataBytes) { "prepared WAV data chunk mismatch" }
        require(u32(4) == dataBytes + 36L) { "prepared WAV RIFF size mismatch" }
    }

    private fun ByteArray.toHex(): String {
        val alphabet = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = alphabet[value ushr 4]
            chars[index * 2 + 1] = alphabet[value and 0x0F]
        }
        return String(chars)
    }
}
