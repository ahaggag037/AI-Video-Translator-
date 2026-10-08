package com.clw.aivideotranslator.subtitle

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import java.util.Locale

enum class SrtClockPolicy {
    ORIGINAL_VIDEO,
    EXPORTED_RANGE,
}

data class SrtItem(
    val id: String,
    val semanticText: String,
    val displayInterval: PresentationIntervalUs,
)

object SrtExporter {
    fun export(
        items: List<SrtItem>,
        clockPolicy: SrtClockPolicy,
        exportRangeStartUs: Long = 0L,
    ): String {
        require(items.isNotEmpty()) { "no subtitle items" }
        require(exportRangeStartUs >= 0L)
        var previousEndUs = -1L
        return buildString {
            items.forEachIndexed { index, item ->
                require(item.semanticText.isNotBlank()) { "blank subtitle text" }
                require(!item.semanticText.contains('\u0000') && !item.semanticText.contains('�')) {
                    "invalid subtitle text"
                }
                require(!item.semanticText.contains("-->")) { "SRT_UNSAFE_TEXT" }
                val rawStart = item.displayInterval.start.value
                val rawEnd = item.displayInterval.end.value
                val startUs = when (clockPolicy) {
                    SrtClockPolicy.ORIGINAL_VIDEO -> rawStart
                    SrtClockPolicy.EXPORTED_RANGE -> Math.subtractExact(rawStart, exportRangeStartUs)
                }
                val endUs = when (clockPolicy) {
                    SrtClockPolicy.ORIGINAL_VIDEO -> rawEnd
                    SrtClockPolicy.EXPORTED_RANGE -> Math.subtractExact(rawEnd, exportRangeStartUs)
                }
                require(startUs >= 0L && endUs > startUs && startUs >= previousEndUs) { "invalid SRT timeline" }
                previousEndUs = endUs
                append(index + 1).append('\n')
                append(timestampUs(startUs)).append(" --> ").append(timestampUs(endUs)).append('\n')
                append(semanticUnwrapped(item.semanticText)).append("\n\n")
            }
        }
    }

    internal fun semanticUnwrapped(text: String): String =
        text.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex(" {2,}"), " ").trim()

    internal fun timestampUs(us: Long): String {
        require(us >= 0L)
        val ms = us / 1_000L
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d,%03d",
            ms / 3_600_000L,
            (ms / 60_000L) % 60L,
            (ms / 1_000L) % 60L,
            ms % 1_000L,
        )
    }
}
