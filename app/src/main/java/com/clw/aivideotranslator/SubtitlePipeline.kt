package com.clw.aivideotranslator

import java.util.Locale
import kotlin.math.ceil

data class SourceUnit(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
)

data class TranslationEntry(val sourceUnitId: String, val translatedText: String)

data class ArabicSubtitleCue(
    val sourceUnitId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object SubtitlePipeline {
    /** Legacy qualification boundary retained as the default only. Product full-video callers pass an explicit duration. */
    const val SAMPLE_END_MS = 60_000L
    private const val LEGACY_MAX_WORDS_PER_MINUTE = 2_000L
    private const val ABSOLUTE_MAX_WORDS = 200_000L

    fun sourceUnits(
        words: List<NvidiaWord>,
        maxTimelineMs: Long = SAMPLE_END_MS,
    ): List<SourceUnit> {
        require(maxTimelineMs > 0L) { "حد الخط الزمني غير صالح" }
        require(words.isNotEmpty()) { "لا توجد كلمات موقّتة لبناء الترجمة" }
        require(words.size.toLong() <= wordLimitFor(maxTimelineMs)) { "عدد كلمات المصدر يتجاوز حد الحماية" }
        var previousEnd = 0L
        words.forEach { word ->
            val start = requireNotNull(word.startMs) { "كلمة بلا توقيت بداية" }
            val end = requireNotNull(word.endMs) { "كلمة بلا توقيت نهاية" }
            require(word.text.isNotBlank() && word.text.length <= 500) { "نص كلمة غير صالح" }
            require(start >= previousEnd && end > start && end <= maxTimelineMs) {
                "توقيت غير صالح أو متداخل أو خارج الخط الزمني المصرح؛ لن يتم تعديله تلقائيًا"
            }
            previousEnd = end
        }
        val result = mutableListOf<SourceUnit>()
        val pending = mutableListOf<NvidiaWord>()
        fun flush() {
            if (pending.isEmpty()) return
            result += SourceUnit(
                id = String.format(Locale.ROOT, "u%06d", result.size + 1),
                startMs = pending.first().startMs!!,
                endMs = pending.last().endMs!!,
                sourceText = pending.joinToString(" ") { it.text.trim() },
            )
            pending.clear()
        }
        words.forEach { word ->
            if (pending.isNotEmpty()) {
                val gap = word.startMs!! - pending.last().endMs!!
                val duration = word.endMs!! - pending.first().startMs!!
                val chars = pending.sumOf { it.text.trim().length + 1 } + word.text.trim().length
                if (gap >= 700 || duration > 6_000 || pending.size >= 16 || chars > 160) flush()
            }
            pending += word
            if (Regex("[.!?][\"')]*$").containsMatchIn(word.text.trim())) flush()
        }
        flush()
        return result
    }

    fun validateText(text: String): String {
        require(text.isNotBlank() && text.length <= 4_000) { "نص الترجمة فارغ أو طويل جدًا" }
        require(!text.contains("-->") && !text.contains('\u0000') && !text.contains('�')) {
            "الترجمة تحتوي بنية توقيت أو نصًا غير صالح"
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun cues(
        units: List<SourceUnit>,
        entries: List<TranslationEntry>,
        maxTimelineMs: Long = SAMPLE_END_MS,
    ): List<ArabicSubtitleCue> {
        require(maxTimelineMs > 0L) { "حد الخط الزمني غير صالح" }
        require(units.isNotEmpty() && units.size == entries.size) { "عدد الترجمات لا يطابق الوحدات" }
        require(units.map { it.id }.toSet().size == units.size) { "IDs مصدر مكررة" }
        require(entries.map { it.sourceUnitId }.toSet().size == entries.size) { "IDs ترجمة مكررة" }
        val byId = entries.associateBy { it.sourceUnitId }
        require(byId.keys == units.map { it.id }.toSet()) { "IDs الترجمة لا تطابق المصدر" }
        var previousEnd = 0L
        return units.map { unit ->
            require(unit.startMs >= previousEnd && unit.endMs > unit.startMs && unit.endMs <= maxTimelineMs) {
                "توقيت الوحدة غير صالح"
            }
            previousEnd = unit.endMs
            ArabicSubtitleCue(
                unit.id,
                unit.startMs,
                unit.endMs,
                validateText(byId.getValue(unit.id).translatedText),
            )
        }
    }

    fun toPresentationTimeline(
        sampleCues: List<ArabicSubtitleCue>,
        sampleStartMs: Long,
        videoDurationMs: Long,
        maxSampleDurationMs: Long = SAMPLE_END_MS,
    ): List<ArabicSubtitleCue> {
        require(sampleCues.isNotEmpty()) { "لا توجد ترجمة لربطها بالفيديو" }
        require(sampleStartMs >= 0) { "بداية العينة سالبة" }
        require(videoDurationMs > sampleStartMs) { "مدة الفيديو لا تسمح بهذه العينة" }
        require(maxSampleDurationMs > 0L) { "مدة العينة غير صالحة" }
        var previousEnd = sampleStartMs
        return sampleCues.map { cue ->
            require(cue.startMs >= 0 && cue.endMs > cue.startMs && cue.endMs <= maxSampleDurationMs) {
                "توقيت العينة غير صالح"
            }
            val start = Math.addExact(sampleStartMs, cue.startMs)
            val end = Math.addExact(sampleStartMs, cue.endMs)
            require(start >= previousEnd && end <= videoDurationMs) {
                "توقيت الترجمة يتجاوز خط الفيديو أو غير رتيب"
            }
            previousEnd = end
            cue.copy(startMs = start, endMs = end)
        }
    }

    fun activeCue(cues: List<ArabicSubtitleCue>, positionMs: Long): ArabicSubtitleCue? =
        cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }

    fun srt(cues: List<ArabicSubtitleCue>, maxTimelineMs: Long? = null): String {
        require(cues.isNotEmpty()) { "لا توجد ترجمة للتصدير" }
        var previousEnd = 0L
        return cues.mapIndexed { index, cue ->
            require(cue.startMs >= previousEnd && cue.endMs > cue.startMs &&
                (maxTimelineMs == null || cue.endMs <= maxTimelineMs)) { "توقيت SRT غير صالح" }
            previousEnd = cue.endMs
            "${index + 1}\n${timestamp(cue.startMs)} --> ${timestamp(cue.endMs)}\n${validateText(cue.text)}\n\n"
        }.joinToString("")
    }

    fun timestamp(ms: Long): String {
        require(ms >= 0)
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d,%03d",
            ms / 3_600_000,
            (ms / 60_000) % 60,
            (ms / 1_000) % 60,
            ms % 1_000,
        )
    }

    private fun wordLimitFor(maxTimelineMs: Long): Long {
        val minutes = ceil(maxTimelineMs.toDouble() / SAMPLE_END_MS.toDouble()).toLong().coerceAtLeast(1L)
        return minOf(ABSOLUTE_MAX_WORDS, Math.multiplyExact(minutes, LEGACY_MAX_WORDS_PER_MINUTE))
    }
}