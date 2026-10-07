package com.clw.aivideotranslator

import java.util.Locale

// P0 boundary: integer milliseconds supplied by the existing STT adapter.
// SourceUnit offsets are relative to the extracted WAV sample. P0-E maps final cues
// onto the selected video's presentation timeline before preview/export.
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
    const val SAMPLE_END_MS = 60_000L

    fun sourceUnits(words: List<NvidiaWord>): List<SourceUnit> {
        require(words.isNotEmpty()) { "لا توجد كلمات موقّتة لبناء الترجمة" }
        require(words.size <= 2_000) { "عدد كلمات العينة يتجاوز حد الاختبار" }
        var previousEnd = 0L
        words.forEach { word ->
            val start = requireNotNull(word.startMs) { "كلمة بلا توقيت بداية" }
            val end = requireNotNull(word.endMs) { "كلمة بلا توقيت نهاية" }
            require(word.text.isNotBlank() && word.text.length <= 500) { "نص كلمة غير صالح" }
            require(start >= previousEnd && end > start && end <= SAMPLE_END_MS) {
                "توقيت غير صالح أو متداخل أو خارج أول 60 ثانية؛ لن يتم تعديله تلقائيًا"
            }
            previousEnd = end
        }
        val result = mutableListOf<SourceUnit>()
        val pending = mutableListOf<NvidiaWord>()
        fun flush() {
            if (pending.isEmpty()) return
            result += SourceUnit(
                id = String.format(Locale.ROOT, "u%04d", result.size + 1),
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
        // Flatten line breaks so model output cannot inject SRT blocks.
        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun cues(units: List<SourceUnit>, entries: List<TranslationEntry>): List<ArabicSubtitleCue> {
        require(units.isNotEmpty() && units.size == entries.size) { "عدد الترجمات لا يطابق الوحدات" }
        require(units.map { it.id }.toSet().size == units.size) { "IDs مصدر مكررة" }
        require(entries.map { it.sourceUnitId }.toSet().size == entries.size) { "IDs ترجمة مكررة" }
        val byId = entries.associateBy { it.sourceUnitId }
        require(byId.keys == units.map { it.id }.toSet()) { "IDs الترجمة لا تطابق المصدر" }
        var previousEnd = 0L
        return units.map { unit ->
            require(unit.startMs >= previousEnd && unit.endMs > unit.startMs &&
                unit.endMs <= SAMPLE_END_MS) { "توقيت الوحدة غير صالح" }
            previousEnd = unit.endMs
            ArabicSubtitleCue(
                unit.id,
                unit.startMs,
                unit.endMs,
                validateText(byId.getValue(unit.id).translatedText),
            )
        }
    }

    /**
     * Converts sample-relative cues to the selected video's presentation timeline.
     * P0 currently extracts the first minute, so sampleStartMs is 0 in the UI, but the
     * explicit mapping is kept here so later non-zero samples cannot silently export
     * incorrect SRT clocks.
     */
    fun toPresentationTimeline(
        sampleCues: List<ArabicSubtitleCue>,
        sampleStartMs: Long,
        videoDurationMs: Long,
    ): List<ArabicSubtitleCue> {
        require(sampleCues.isNotEmpty()) { "لا توجد ترجمة لربطها بالفيديو" }
        require(sampleStartMs >= 0) { "بداية العينة سالبة" }
        require(videoDurationMs > sampleStartMs) { "مدة الفيديو لا تسمح بهذه العينة" }
        var previousEnd = sampleStartMs
        return sampleCues.map { cue ->
            require(cue.startMs >= 0 && cue.endMs > cue.startMs && cue.endMs <= SAMPLE_END_MS) {
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
}
