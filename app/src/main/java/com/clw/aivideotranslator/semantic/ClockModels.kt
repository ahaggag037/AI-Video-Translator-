package com.clw.aivideotranslator.semantic

@JvmInline
value class AudioTimeUs(val value: Long) {
    init { require(value >= 0L) { "audio time must be non-negative" } }
}

@JvmInline
value class PresentationTimeUs(val value: Long) {
    init { require(value >= 0L) { "presentation time must be non-negative" } }
}

data class AudioIntervalUs(
    val start: AudioTimeUs,
    val end: AudioTimeUs,
) {
    init { require(end.value > start.value) { "audio interval must be positive" } }
}

data class PresentationIntervalUs(
    val start: PresentationTimeUs,
    val end: PresentationTimeUs,
) {
    init { require(end.value > start.value) { "presentation interval must be positive" } }
}

enum class ClockVerificationStatus {
    UNVERIFIED,
    VERIFIED_AFFINE,
}

data class SampleClockMap(
    val presentationOrigin: PresentationTimeUs,
    val precisionUs: Long,
    val status: ClockVerificationStatus,
    val evidenceProfile: String? = null,
) {
    init { require(precisionUs > 0L) { "clock precision must be positive" } }

    fun mapVerified(interval: AudioIntervalUs): PresentationIntervalUs {
        require(status == ClockVerificationStatus.VERIFIED_AFFINE) {
            "sample clock mapping is not verified"
        }
        return PresentationIntervalUs(
            start = PresentationTimeUs(Math.addExact(presentationOrigin.value, interval.start.value)),
            end = PresentationTimeUs(Math.addExact(presentationOrigin.value, interval.end.value)),
        )
    }
}
