package com.clw.aivideotranslator

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.clw.aivideotranslator.subtitle.android.RasterCoordinator
import com.clw.aivideotranslator.subtitle.android.RenderSnapshot
import com.clw.aivideotranslator.subtitle.android.SnapshotBitmapOverlay
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
internal class SnapshotBurnSession internal constructor(
    internal val transformer: Transformer,
    private val outputFile: File,
    private val cleanup: () -> Unit,
) {
    private val cancelled = AtomicBoolean(false)

    fun progress(): Int? {
        val holder = ProgressHolder()
        return if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress
        } else {
            null
        }
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        runCatching { transformer.cancel() }
        cleanup()
        outputFile.delete()
    }
}

/**
 * X004 production candidate: the burned video consumes the same immutable [RenderSnapshot] as the
 * preview. No text layout, font sizing, wrapping or cue selection is recomputed in this adapter.
 */
@OptIn(UnstableApi::class)
internal object SnapshotBurnedSubtitleExporter {
    fun start(
        context: Context,
        sourceUri: Uri,
        snapshot: RenderSnapshot,
        sampleStartMs: Long,
        sampleEndMs: Long,
        onCompleted: (BurnedVideoResult) -> Unit,
        onError: (String) -> Unit,
    ): SnapshotBurnSession {
        require(sampleStartMs >= 0L && sampleEndMs > sampleStartMs) { "نافذة الفيديو غير صالحة" }
        val startUs = Math.multiplyExact(sampleStartMs, 1_000L)
        val endUs = Math.multiplyExact(sampleEndMs, 1_000L)
        require(snapshot.cues.isNotEmpty()) { "لا توجد ترجمة في RenderSnapshot" }
        require(snapshot.cues.all { it.startUs >= startUs && it.endUs <= endUs }) {
            "بعض ترجمة RenderSnapshot خارج نافذة الفيديو"
        }

        val exportDir = File(context.cacheDir, "snapshot_exports/${UUID.randomUUID()}").apply {
            require(mkdirs() || isDirectory) { "تعذر إنشاء مجلد التصدير" }
        }
        val outputFile = File(exportDir, BurnedSubtitleExporter.DEFAULT_FILE_NAME)
        val coordinator = RasterCoordinator()
        val overlay = SnapshotBitmapOverlay(
            timeline = snapshot.timeline,
            expectedGeometry = snapshot.geometry,
            exportRangeStartUs = startUs,
            coordinator = coordinator,
        )
        val cleaned = AtomicBoolean(false)
        val cleanup = {
            if (cleaned.compareAndSet(false, true)) {
                runCatching { overlay.release() }
                coordinator.close()
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sampleStartMs)
                    .setEndPositionMs(sampleEndMs)
                    .build()
            )
            .build()
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(overlay))),
                )
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                cleanup()
                runCatching { validate(outputFile, sampleEndMs - sampleStartMs) }
                    .onSuccess(onCompleted)
                    .onFailure { error ->
                        outputFile.delete()
                        onError(error.message ?: "تم التصدير لكن فشل التحقق من ملف MP4")
                    }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                cleanup()
                outputFile.delete()
                onError(exportException.message ?: "فشل إنشاء فيديو MP4 من RenderSnapshot")
            }
        }

        return try {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            val session = SnapshotBurnSession(transformer, outputFile, cleanup)
            transformer.start(edited, outputFile.absolutePath)
            session
        } catch (error: Exception) {
            cleanup()
            outputFile.delete()
            throw error
        }
    }

    private fun validate(file: File, expectedDurationMs: Long): BurnedVideoResult {
        require(file.isFile && file.length() > 32 * 1024) { "ملف MP4 الناتج فارغ أو صغير بصورة غير متوقعة" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                "ملف الإخراج لا يحتوي مسار فيديو صالحًا"
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("تعذر قراءة مدة MP4 الناتج")
            require(kotlin.math.abs(durationMs - expectedDurationMs) <= 2_000L) {
                "مدة MP4 الناتج لا تطابق نافذة العينة"
            }
            return BurnedVideoResult(file, durationMs, file.length())
        } finally {
            retriever.release()
        }
    }
}
