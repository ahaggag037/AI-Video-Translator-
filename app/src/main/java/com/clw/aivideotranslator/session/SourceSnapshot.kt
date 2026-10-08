package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.sha256Utf8

const val SOURCE_SNAPSHOT_SCHEMA_VERSION = 1

data class SourceSnapshotWord(
    val ordinal: Int,
    val rawText: String,
    val confidence: Double?,
    val audioInterval: AudioIntervalUs? = null,
) {
    init {
        require(ordinal >= 0) { "negative source word ordinal" }
        require(rawText.isNotBlank() && rawText.length <= 4_096) { "invalid source word text" }
        require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0) {
            "invalid source word confidence"
        }
    }
}

data class SourcePcmSampleIdentity(
    val wavSha256: String,
    val wavSizeBytes: Long,
    val pcmFrameCount: Long,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
) {
    init {
        require(isLowerSha256(wavSha256)) { "invalid WAV SHA-256" }
        require(wavSizeBytes > 44L) { "invalid WAV size" }
        require(pcmFrameCount > 0L && pcmFrameCount <= Long.MAX_VALUE / 1_000_000L) { "invalid PCM frame count" }
        require(sampleRateHz > 0) { "invalid sample rate" }
        require(channelCount > 0) { "invalid channel count" }
        require(bitsPerSample in 1..64) { "invalid PCM bit depth" }
    }

    val derivedPcmDurationUs: Long
        get() = Math.multiplyExact(pcmFrameCount, 1_000_000L) / sampleRateHz
}

data class SourceSttProvenance(
    val providerId: String,
    val modelId: String,
    val requestProfileId: String,
    val parserVersion: String,
    val rawResponseSha256: String,
    val httpStatus: Int,
) {
    init {
        listOf(providerId, modelId, requestProfileId, parserVersion).forEach {
            require(it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl)) { "invalid STT provenance" }
        }
        require(isLowerSha256(rawResponseSha256)) { "invalid STT response SHA-256" }
        require(httpStatus in 100..599) { "invalid STT HTTP status" }
    }
}

data class SourceClockProvenance(
    /** Direct extractor observation for the prepared sample; this alone does not verify provider offset units. */
    val observedPresentationOriginUs: Long,
    val verificationStatus: ClockVerificationStatus,
    val precisionUs: Long? = null,
    val evidenceProfile: String? = null,
) {
    init {
        require(observedPresentationOriginUs >= 0L) { "negative observed presentation origin" }
        when (verificationStatus) {
            ClockVerificationStatus.UNVERIFIED -> require(precisionUs == null && evidenceProfile == null) {
                "unverified clock cannot claim precision/evidence profile"
            }
            ClockVerificationStatus.VERIFIED_AFFINE -> {
                require(precisionUs != null && precisionUs > 0L) { "verified clock requires positive precision" }
                require(!evidenceProfile.isNullOrBlank() && evidenceProfile.length <= 512 &&
                    evidenceProfile.none(Char::isISOControl)) { "verified clock requires evidence profile" }
            }
        }
    }
}

/**
 * Immutable accepted STT/source-text snapshot. It deliberately separates durable semantic text from
 * clock authorization: UNVERIFIED snapshots may preserve words but MUST NOT carry mapped word times.
 * This lets Task17 durability progress without laundering X001's still-unknown provider offset units.
 * Raw provider response bytes are not persisted; only their SHA-256 provenance is retained.
 */
data class SourceSnapshot(
    val sessionId: String,
    val sourceAttachmentId: String,
    val transcript: String,
    val words: List<SourceSnapshotWord>,
    val pcmSample: SourcePcmSampleIdentity,
    val stt: SourceSttProvenance,
    val clock: SourceClockProvenance,
) {
    init {
        require(isSafeId(sessionId)) { "invalid snapshot session id" }
        require(isSafeId(sourceAttachmentId)) { "invalid snapshot source attachment id" }
        require(transcript.isNotBlank()) { "blank source transcript" }
        require(words.isNotEmpty()) { "source snapshot requires words" }
        require(words.map { it.ordinal } == words.indices.toList()) { "source word ordinals must be contiguous" }
        when (clock.verificationStatus) {
            ClockVerificationStatus.UNVERIFIED -> require(words.all { it.audioInterval == null }) {
                "unverified clock cannot persist interpreted word intervals"
            }
            ClockVerificationStatus.VERIFIED_AFFINE -> require(words.all { it.audioInterval != null }) {
                "verified clock requires every word interval"
            }
        }
    }

    val sourceTextHash: String get() = sha256Utf8(transcript)

    val snapshotId: String
        get() {
            val fields = mutableListOf<String?>()
            fields += "source-snapshot-v1"
            fields += sessionId
            fields += sourceAttachmentId
            fields += transcript
            fields += pcmSample.wavSha256
            fields += pcmSample.wavSizeBytes.toString()
            fields += pcmSample.pcmFrameCount.toString()
            fields += pcmSample.sampleRateHz.toString()
            fields += pcmSample.channelCount.toString()
            fields += pcmSample.bitsPerSample.toString()
            fields += stt.providerId
            fields += stt.modelId
            fields += stt.requestProfileId
            fields += stt.parserVersion
            fields += stt.rawResponseSha256
            fields += stt.httpStatus.toString()
            fields += clock.observedPresentationOriginUs.toString()
            fields += clock.verificationStatus.name
            fields += clock.precisionUs?.toString()
            fields += clock.evidenceProfile
            words.forEach { word ->
                fields += word.ordinal.toString()
                fields += word.rawText
                fields += word.confidence?.toRawBits()?.toString()
                fields += word.audioInterval?.start?.value?.toString()
                fields += word.audioInterval?.end?.value?.toString()
            }
            val canonical = fields.joinToString("") { canonicalField(it) }
            return "snapshot-${sha256Utf8(canonical)}"
        }
}

private fun canonicalField(value: String?): String =
    if (value == null) "-1:" else "${value.toByteArray(Charsets.UTF_8).size}:$value"

private fun isLowerSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
