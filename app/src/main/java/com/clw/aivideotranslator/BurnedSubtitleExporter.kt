package com.clw.aivideotranslator

import android.content.Context
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID

internal data class BurnedVideoResult(
    val file: File,
    val durationMs: Long,
    val sizeBytes: Long,
)

/**
 * P0-F hard-burn gate.
 *
 * The export intentionally renders only the translated sample window. The source video is never
 * modified. Subtitle text is selected from the same presentation-timeline cues that already passed
 * the P0-E on-device synchronization gate.
 */
@OptIn(UnstableApi::class)
internal object BurnedSubtitleExporter {
    const val DEFAULT_FILE_NAME = "sample_ar_burned.mp4"

    fun start(
        context: Context,
        sourceUri: Uri,
        cues: List<ArabicSubtitleCue>,
        sampleStartMs: Long,
        sampleEndMs: Long,
        onCompleted: (BurnedVideoResult) -> Unit,
        onError: (String) -> Unit,
    ): Transformer {
        require(cues.isNotEmpty()) { "لا توجد ترجمة لحرقها على الفيديو" }
        require(sampleStartMs >= 0 && sampleEndMs > sampleStartMs) { "نافذة الفيديو غير صالحة" }
        require(cues.all { it.startMs >= sampleStartMs && it.endMs <= sampleEndMs }) {
            "بعض الترجمة خارج نافذة الفيديو المترجم"
        }

        val exportDir = File(context.cacheDir, "p0_exports/${UUID.randomUUID()}").apply { mkdirs() }
        val outputFile = File(exportDir, DEFAULT_FILE_NAME)

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sampleStartMs)
                    .setEndPositionMs(sampleEndMs)
                    .build()
            )
            .build()

        val textOverlay = TimedArabicTextOverlay(cues, sampleStartMs)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(textOverlay))),
                )
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                runCatching { validate(outputFile, sampleEndMs - sampleStartMs) }
                    .onSuccess(onCompleted)
                    .onFailure {
                        outputFile.delete()
                        onError(it.message ?: "تم التصدير لكن فشل التحقق من ملف MP4")
                    }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                outputFile.delete()
                onError(exportException.message ?: "فشل إنشاء فيديو MP4 المترجم")
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        transformer.start(editedMediaItem, outputFile.absolutePath)
        return transformer
    }

    fun progress(transformer: Transformer): Int? {
        val holder = ProgressHolder()
        return if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress
        } else {
            null
        }
    }

    private fun validate(file: File, expectedDurationMs: Long): BurnedVideoResult {
        require(file.isFile && file.length() > 32 * 1024) { "ملف MP4 الناتج فارغ أو صغير بصورة غير متوقعة" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            require(hasVideo == "yes") { "ملف الإخراج لا يحتوي مسار فيديو صالحًا" }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("تعذر قراءة مدة MP4 الناتج")
            // A small container/codec tolerance is fine; seconds of drift are not.
            require(kotlin.math.abs(durationMs - expectedDurationMs) <= 2_000L) {
                "مدة MP4 الناتج لا تطابق نافذة العينة"
            }
            return BurnedVideoResult(file, durationMs, file.length())
        } finally {
            retriever.release()
        }
    }
}

@OptIn(UnstableApi::class)
private class TimedArabicTextOverlay(
    private val cues: List<ArabicSubtitleCue>,
    private val sampleStartMs: Long,
) : TextOverlay() {
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, -0.78f)
        .setOverlayFrameAnchor(0f, -1f)
        .setScale(0.42f, 0.42f)
        .build()

    override fun getText(presentationTimeUs: Long): SpannableString {
        // Transformer clipping resets the output presentation timeline to zero. Map it back to the
        // original video's presentation clock before selecting a cue.
        val sourcePositionMs = sampleStartMs + (presentationTimeUs / 1_000L)
        val cue = SubtitlePipeline.activeCue(cues, sourcePositionMs)
        val value = cue?.text ?: " "
        return SpannableString(value).apply {
            if (cue == null) {
                setSpan(
                    ForegroundColorSpan(Color.TRANSPARENT),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    BackgroundColorSpan(Color.argb(190, 0, 0, 0)),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    AbsoluteSizeSpan(52),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
