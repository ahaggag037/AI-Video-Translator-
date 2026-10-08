package com.clw.aivideotranslator.semantic

import java.text.BreakIterator
import java.util.Locale

data class DisplayTimingConfig(
    val maxEndExtensionUs: Long = 500_000L,
    val nextSpeechGuardUs: Long = 80_000L,
    val readingTargetEgcPerSecond: Double = 15.0,
    val fastReadingWarningEgcPerSecond: Double = 20.0,
) {
    init {
        require(maxEndExtensionUs >= 0L)
        require(nextSpeechGuardUs >= 0L)
        require(readingTargetEgcPerSecond > 0.0)
        require(fastReadingWarningEgcPerSecond >= readingTargetEgcPerSecond)
    }
}

data class DisplayCue(
    val cueId: String,
    val speechInterval: PresentationIntervalUs,
    val visibleInterval: PresentationIntervalUs,
    val timingPolicyVersion: String = "display-v1",
    val warnings: Set<String> = emptySet(),
)

object DisplayTimingPlanner {
    fun plan(
        cue: SemanticCue,
        rangeEnd: PresentationTimeUs,
        nextKnownSpeechStart: PresentationTimeUs? = null,
        config: DisplayTimingConfig = DisplayTimingConfig(),
    ): DisplayCue {
        require(rangeEnd.value >= cue.speechInterval.end.value) { "cue exceeds selected range" }
        if (nextKnownSpeechStart != null) {
            require(nextKnownSpeechStart.value >= cue.speechInterval.end.value) {
                "overlapping source speech is unsupported by this timing profile"
            }
        }
        val visibleEgc = visibleEgcCount(cue.text)
        val targetDurationUs = ((visibleEgc / config.readingTargetEgcPerSecond) * 1_000_000.0)
            .toLong().coerceAtLeast(1L)
        val desiredEnd = Math.addExact(cue.speechInterval.start.value, targetDurationUs)
        val maxExtendedEnd = Math.addExact(cue.speechInterval.end.value, config.maxEndExtensionUs)
        val guardedNext = nextKnownSpeechStart?.let {
            (it.value - config.nextSpeechGuardUs).coerceAtLeast(cue.speechInterval.end.value)
        }
        val safeEnd = listOfNotNull(rangeEnd.value, maxExtendedEnd, guardedNext).minOrNull()
            ?: cue.speechInterval.end.value
        val visibleEnd = maxOf(cue.speechInterval.end.value, minOf(desiredEnd, safeEnd))
        val durationSeconds = (visibleEnd - cue.speechInterval.start.value) / 1_000_000.0
        val cps = if (durationSeconds > 0.0) visibleEgc / durationSeconds else Double.POSITIVE_INFINITY
        val warnings = buildSet {
            if (cps > config.fastReadingWarningEgcPerSecond) add("FAST_READING")
        }
        return DisplayCue(
            cueId = cue.id,
            speechInterval = cue.speechInterval,
            visibleInterval = PresentationIntervalUs(
                cue.speechInterval.start,
                PresentationTimeUs(visibleEnd),
            ),
            warnings = warnings,
        )
    }

    internal fun visibleEgcCount(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance(Locale("ar"))
        iterator.setText(text)
        var count = 0
        var current = iterator.first()
        while (true) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) break
            val cluster = text.substring(current, next)
            if (cluster.any { !it.isISOControl() }) count++
            current = next
        }
        return count
    }
}
