package com.clw.aivideotranslator.tv1

/**
 * Canonical TV1 clock scalar. Core timing is represented as non-negative integer
 * microseconds; legacy millisecond APIs must cross an explicit bridge.
 */
@JvmInline
value class TimeUs(val value: Long) {
    init {
        require(value >= 0L) { "time must be non-negative" }
    }

    companion object {
        val ZERO = TimeUs(0L)
    }
}

/**
 * Maps STT/sample-relative audio time onto the selected video's presentation clock.
 *
 * This deliberately contains only an affine origin mapping. Activation for real media
 * remains gated by X001; the type exists now so later code cannot silently assume a
 * zero-origin sample.
 */
data class SampleClockMap(
    val presentationOriginUs: TimeUs,
    val sampleDurationUs: Long,
) {
    init {
        require(sampleDurationUs > 0L) { "sample duration must be positive" }
    }

    fun toPresentation(sampleRelativeUs: TimeUs): TimeUs {
        require(sampleRelativeUs.value <= sampleDurationUs) {
            "sample-relative time exceeds mapped sample duration"
        }
        return TimeUs(Math.addExact(presentationOriginUs.value, sampleRelativeUs.value))
    }

    fun toSampleRelative(presentationUs: TimeUs): TimeUs {
        require(presentationUs.value >= presentationOriginUs.value) {
            "presentation time precedes sample origin"
        }
        val relative = Math.subtractExact(presentationUs.value, presentationOriginUs.value)
        require(relative <= sampleDurationUs) {
            "presentation time exceeds mapped sample duration"
        }
        return TimeUs(relative)
    }
}

internal fun legacyMsToUs(ms: Long): TimeUs {
    require(ms >= 0L) { "legacy milliseconds must be non-negative" }
    return TimeUs(Math.multiplyExact(ms, 1_000L))
}

internal fun TimeUs.toLegacyMsExact(): Long {
    require(value % 1_000L == 0L) {
        "microsecond value cannot cross the legacy millisecond bridge without retiming"
    }
    return value / 1_000L
}
