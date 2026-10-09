package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X004 true-device parity falsifier.
 *
 * The source is generated locally as a solid PNG, so there is no fixture/network/provider input.
 * Media3 turns the image into a real H.264 MP4 while [SnapshotBitmapOverlay] drives the GL overlay.
 * Decoded output frames before/inside/after the cue must prove a real cue switch rather than merely
 * exercising BitmapOverlay methods in isolation.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SnapshotTransformerParityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun transformerOutputShowsCueOnlyInsideHalfOpenSnapshotInterval() {
        val root = File(context.cacheDir, "x004-transformer-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
        val source = File(root, "solid-blue.png")
        val output = File(root, "snapshot-overlay.mp4")
        val geometry = FrameGeometry(426, 240)
        val font = SubtitleFonts.loadExperimentCandidate(context)
        val environment = "api=${android.os.Build.VERSION.SDK_INT};candidate=full-regular-2.012"
        val layout = SubtitleLayoutEngine().layout(
            rawText = "ترجمة X004",
            geometry = geometry,
            font = font,
            rendererEnvironment = environment,
        )
        assertTrue(layout is SubtitleLayoutResult.Fits)
        val descriptor = (layout as SubtitleLayoutResult.Fits).descriptor
        val request = RasterRequest("cue-a", descriptor, geometry, font)
        val cue = SnapshotRasterCue(
            cueId = "cue-a",
            startUs = 500_000L,
            endUs = 1_200_000L,
            request = request,
        )
        val coordinator = RasterCoordinator()
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(listOf(cue)),
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )

        try {
            createSolidPng(source, geometry.uprightWidthPx, geometry.uprightHeightPx)
            exportImageVideo(source, output, overlay)
            assertTrue(output.isFile && output.length() > 8_000L)

            val before = frameAt(output, 200_000L)
            val during = frameAt(output, 800_000L)
            val after = frameAt(output, 1_600_000L)
            try {
                assertEquals(geometry.uprightWidthPx, during.width)
                assertEquals(geometry.uprightHeightPx, during.height)

                val box = descriptor.boxBounds
                val beforeLuma = averageLuma(before, box.left, box.top, box.right, box.bottom)
                val duringLuma = averageLuma(during, box.left, box.top, box.right, box.bottom)
                val afterLuma = averageLuma(after, box.left, box.top, box.right, box.bottom)
                val beforeAfterDelta = kotlin.math.abs(beforeLuma - afterLuma)

                // The source is spatially uniform. Outside the cue, decoded frames should remain
                // nearly identical. Inside the cue, the black box + white glyphs must materially
                // alter the same frozen descriptor region.
                assertTrue("outside-cue frames drifted unexpectedly: $beforeAfterDelta", beforeAfterDelta < 8.0)
                assertTrue(
                    "cue did not materially change decoded descriptor region: before=$beforeLuma during=$duringLuma",
                    kotlin.math.abs(beforeLuma - duringLuma) > 18.0,
                )
            } finally {
                before.recycle()
                during.recycle()
                after.recycle()
            }
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    private fun createSolidPng(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(51, 102, 204))
            file.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun exportImageVideo(
        source: File,
        output: File,
        overlay: SnapshotBitmapOverlay,
    ) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setImageDurationMs(2_000L)
            .build()
        val edited = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(30)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(overlay))),
                )
            )
            .build()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                done.countDown()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                failure = exportException
                done.countDown()
            }
        }
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()
        transformer.start(edited, output.absolutePath)
        assertTrue("Transformer timed out", done.await(30, TimeUnit.SECONDS))
        failure?.let { throw AssertionError("Transformer failed", it) }
    }

    private fun frameAt(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            requireNotNull(frame) { "no decoded frame at ${timeUs}us" }
        } finally {
            retriever.release()
        }
    }

    private fun averageLuma(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Double {
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeTop = top.coerceIn(0, bitmap.height - 1)
        val safeRight = right.coerceIn(safeLeft + 1, bitmap.width)
        val safeBottom = bottom.coerceIn(safeTop + 1, bitmap.height)
        var sum = 0.0
        var count = 0L
        var y = safeTop
        while (y < safeBottom) {
            var x = safeLeft
            while (x < safeRight) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                count += 1
                x += 2
            }
            y += 2
        }
        return sum / count.toDouble()
    }
}
