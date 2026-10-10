package com.clw.aivideotranslator

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.clw.aivideotranslator.media.AndroidExportMediaInspector
import com.clw.aivideotranslator.media.ExportValidator
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot
import com.clw.aivideotranslator.subtitle.android.RasterCoordinator
import com.clw.aivideotranslator.subtitle.android.SnapshotBitmapOverlay
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
internal class SnapshotBurnedExportSession(
    private val transformer: Transformer,
    private val outputFile: File,
    private val finished: AtomicBoolean,
    private val cleanup: () -> Unit,
    private val validationExecutor: java.util.concurrent.ExecutorService,
) {
    fun progress(): Int? {
        val holder = ProgressHolder()
        return if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress
        } else {
            null
        }
    }

    fun cancel() {
        if (!finished.compareAndSet(false, true)) return
        runCatching { transformer.cancel() }
        cleanup()
        outputFile.delete()
        validationExecutor.shutdownNow()
    }
}

/** Production Task17 hard-burn path using the same immutable raster snapshot as preview. */
@OptIn(UnstableApi::class)
internal object SnapshotBurnedSubtitleExporter {
    fun start(
        context: Context,
        sourceUri: Uri,
        snapshot: LivePresentationRasterSnapshot,
        sampleStartMs: Long,
        sampleEndMs: Long,
        onCompleted: (BurnedVideoResult) -> Unit,
        onError: (String) -> Unit,
    ): SnapshotBurnedExportSession {
        require(sampleStartMs >= 0L && sampleEndMs > sampleStartMs) { "نافذة الفيديو غير صالحة" }
        val sampleStartUs = Math.multiplyExact(sampleStartMs, 1_000L)
        val sampleEndUs = Math.multiplyExact(sampleEndMs, 1_000L)
        require(snapshot.timeline.cues.isNotEmpty()) { "لا توجد ترجمة لحرقها على الفيديو" }
        require(snapshot.timeline.cues.all { it.startUs >= sampleStartUs && it.endUs <= sampleEndUs }) {
            "بعض الترجمة خارج نافذة الفيديو المترجم"
        }

        val exportDir = File(context.cacheDir, "snapshot_exports/${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory) { "تعذر إنشاء مجلد التصدير" }
        }
        val outputFile = File(exportDir, BurnedSubtitleExporter.DEFAULT_FILE_NAME)
        val coordinator = RasterCoordinator()
        val overlay = SnapshotBitmapOverlay(
            timeline = snapshot.timeline,
            expectedGeometry = snapshot.geometry,
            exportRangeStartUs = sampleStartUs,
            coordinator = coordinator,
        )
        val cleanupOnce = AtomicBoolean(false)
        val cleanup = {
            if (cleanupOnce.compareAndSet(false, true)) {
                runCatching { overlay.release() }
                coordinator.close()
            }
        }
        val finished = AtomicBoolean(false)
        val validationExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "subtitle-export-validator").apply { isDaemon = true }
        }
        val mainHandler = Handler(Looper.getMainLooper())

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sampleStartMs)
                    .setEndPositionMs(sampleEndMs)
                    .build()
            )
            .build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(overlay))),
                )
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!finished.compareAndSet(false, true)) return
                cleanup()
                validationExecutor.execute {
                    val validated = runCatching {
                        validate(
                            file = outputFile,
                            expectedDurationUs = sampleEndUs - sampleStartUs,
                            sourceHasAudio = snapshot.sourceHasAudio,
                        )
                    }
                    validationExecutor.shutdown()
                    mainHandler.post {
                        validated.onSuccess(onCompleted).onFailure { error ->
                            outputFile.delete()
                            onError(error.message ?: "تم التصدير لكن فشل التحقق من ملف MP4")
                        }
                    }
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                if (!finished.compareAndSet(false, true)) return
                cleanup()
                validationExecutor.shutdownNow()
                outputFile.delete()
                onError(exportException.message ?: "فشل إنشاء فيديو MP4 المترجم")
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        return try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
            SnapshotBurnedExportSession(
                transformer = transformer,
                outputFile = outputFile,
                finished = finished,
                cleanup = cleanup,
                validationExecutor = validationExecutor,
            )
        } catch (error: Throwable) {
            finished.set(true)
            cleanup()
            validationExecutor.shutdownNow()
            outputFile.delete()
            throw error
        }
    }

    private fun validate(
        file: File,
        expectedDurationUs: Long,
        sourceHasAudio: Boolean,
    ): BurnedVideoResult {
        val observation = AndroidExportMediaInspector.inspect(file)
        val contract = ExportValidator.evaluate(
            observation = observation,
            expectedDurationUs = expectedDurationUs,
            sourceHasAudio = sourceHasAudio,
        )
        require(contract.isAccepted) {
            "فشل عقد MP4: ${contract.failures.sortedBy { it.name }.joinToString { it.name }}"
        }
        val durationUs = requireNotNull(observation.durationUs) { "تعذر قراءة مدة MP4 الناتج" }
        return BurnedVideoResult(
            file = file,
            durationMs = durationUs / 1_000L,
            sizeBytes = observation.sizeBytes,
        )
    }
}
