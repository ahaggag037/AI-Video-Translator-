package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Color
import android.icu.util.VersionInfo
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X004 decoded-device frame parity falsifier.
 *
 * Creates a deterministic CFR AVC diagnostic grid on-device, burns two real native Arabic raster
 * snapshots through Media3 Transformer/BitmapOverlay, then decodes the resulting MP4. This closes
 * the gap between bitmap-level shadow tests and actual encoded frames: active cues must occupy the
 * same descriptor box (N27 <= 2 px displacement), gaps must stay visually empty, and cue switches
 * around the half-open boundaries may not leave a stale prior raster for more than one 30 fps frame.
 *
 * Audio parity remains owned by DecodedAudioEvidenceInstrumentedTest; this fixture is intentionally
 * video-only so a frame failure cannot be hidden by mux/audio setup noise.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class DecodedFrameParityInstrumentedTest {
    private lateinit var root: File
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(WIDTH, HEIGHT)
    private val environment =
        "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Before
    fun setUp() {
        root = File(context.cacheDir, "x004-decoded-frame-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun transformerOutputPreservesDescriptorPositionAndCueSwitchesWithinOneFrame() {
        val source = File(root, "diagnostic-source.mp4")
        val output = File(root, "snapshot-burned.mp4")
        createDiagnosticVideo(source)

        val cueA = cue("cue-a", 500_000L, 1_000_000L, "أولاً")
        val cueB = cue("cue-b", 1_500_000L, 2_000_000L, "ثانيًا")
        val coordinator = RasterCoordinator()
        val overlay = SnapshotBitmapOverlay(
            timeline = SnapshotRasterTimeline(listOf(cueA, cueB)),
            expectedGeometry = geometry,
            exportRangeStartUs = 0L,
            coordinator = coordinator,
        )

        try {
            transform(source, output, overlay)
            assertTrue("Transformer did not produce a non-empty MP4", output.isFile && output.length() > 8_192L)

            val gap = frameAt(output, 1_250_000L)
            val activeA = frameAt(output, 750_000L)
            val activeB = frameAt(output, 1_750_000L)
            try {
                assertEquals(WIDTH, gap.width)
                assertEquals(HEIGHT, gap.height)

                assertMaskMatchesDescriptor(activeA, gap, cueA.request.descriptor.boxBounds, "cue A")
                assertMaskMatchesDescriptor(activeB, gap, cueB.request.descriptor.boxBounds, "cue B")

                // The source grid is constant over time. Outside the subtitle boxes, burn-in must
                // not materially change the decoded image merely because a cue is active.
                assertTrue(
                    "cue A changed too much of the diagnostic frame outside its descriptor box",
                    outsideBoxChangedFraction(activeA, gap, cueA.request.descriptor.boxBounds) < 0.02,
                )
                assertTrue(
                    "cue B changed too much of the diagnostic frame outside its descriptor box",
                    outsideBoxChangedFraction(activeB, gap, cueB.request.descriptor.boxBounds) < 0.02,
                )
            } finally {
                gap.recycle()
                activeA.recycle()
                activeB.recycle()
            }

            // N27: a burned cue switch may lag at most one frame. Probe half a frame on each side of
            // both half-open boundaries. MediaMetadataRetriever may choose the nearest decoded CFR
            // frame, so the tolerance is intentionally one complete 30 fps frame, not a tighter
            // synthetic timer assumption.
            assertBoundarySwitch(
                output = output,
                boundaryUs = cueA.endUs,
                beforeShouldContain = cueA.request.descriptor.boxBounds,
                afterShouldContain = null,
                label = "cue A end",
            )
            assertBoundarySwitch(
                output = output,
                boundaryUs = cueB.startUs,
                beforeShouldContain = null,
                afterShouldContain = cueB.request.descriptor.boxBounds,
                label = "cue B start",
            )
        } finally {
            runCatching { overlay.release() }
            coordinator.close()
        }
    }

    private fun cue(
        id: String,
        startUs: Long,
        endUs: Long,
        text: String,
    ): SnapshotRasterCue {
        val result = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("test cue must fit native layout", result is SubtitleLayoutResult.Fits)
        val descriptor = (result as SubtitleLayoutResult.Fits).descriptor
        return SnapshotRasterCue(
            cueId = id,
            startUs = startUs,
            endUs = endUs,
            request = RasterRequest(id, descriptor, geometry, font),
        )
    }

    private fun transform(source: File, output: File, overlay: SnapshotBitmapOverlay) {
        val completed = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)
        val mediaItem = MediaItem.fromUri(Uri.fromFile(source))
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(overlay))),
                )
            )
            .build()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            completed.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            error.set(exportException)
                            completed.countDown()
                        }
                    }
                )
                .build()
            transformer.start(edited, output.absolutePath)
        }

        assertTrue("Media3 Transformer timed out", completed.await(60L, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Media3 Transformer failed", it) }
    }

    private fun assertBoundarySwitch(
        output: File,
        boundaryUs: Long,
        beforeShouldContain: PixelRect?,
        afterShouldContain: PixelRect?,
        label: String,
    ) {
        val reference = frameAt(output, 1_250_000L)
        val before = frameAt(output, boundaryUs - FRAME_US / 2L)
        val after = frameAt(output, boundaryUs + FRAME_US / 2L)
        try {
            if (beforeShouldContain == null) {
                assertTrue(
                    "$label retained a stale subtitle before the boundary",
                    changedFraction(before, reference) < GAP_CHANGED_FRACTION_MAX,
                )
            } else {
                assertMaskPresent(before, reference, beforeShouldContain, "$label before")
            }

            if (afterShouldContain == null) {
                assertTrue(
                    "$label retained a stale subtitle beyond the N27 one-frame allowance",
                    changedFraction(after, reference) < GAP_CHANGED_FRACTION_MAX,
                )
            } else {
                assertMaskPresent(after, reference, afterShouldContain, "$label after")
            }
        } finally {
            reference.recycle()
            before.recycle()
            after.recycle()
        }
    }

    private fun assertMaskPresent(
        active: Bitmap,
        reference: Bitmap,
        expected: PixelRect,
        label: String,
    ) {
        val bounds = differenceBounds(active, reference)
        assertNotNull("$label produced no decoded subtitle mask", bounds)
        val actual = requireNotNull(bounds)
        assertRectNear(expected, actual, MAX_POSITION_ERROR_PX, label)
    }

    private fun assertMaskMatchesDescriptor(
        active: Bitmap,
        reference: Bitmap,
        expected: PixelRect,
        label: String,
    ) {
        assertMaskPresent(active, reference, expected, label)
        val inside = insideBoxChangedFraction(active, reference, expected)
        assertTrue("$label did not materially alter its descriptor box: $inside", inside >= 0.10)
    }

    private fun differenceBounds(active: Bitmap, reference: Bitmap): PixelRect? {
        require(active.width == reference.width && active.height == reference.height)
        var left = active.width
        var top = active.height
        var right = -1
        var bottom = -1
        for (y in 0 until active.height) {
            for (x in 0 until active.width) {
                if (pixelDifference(active.getPixel(x, y), reference.getPixel(x, y)) >= MASK_THRESHOLD) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < left || bottom < top) null else PixelRect(left, top, right + 1, bottom + 1)
    }

    private fun assertRectNear(expected: PixelRect, actual: PixelRect, tolerancePx: Int, label: String) {
        val errors = listOf(
            abs(expected.left - actual.left),
            abs(expected.top - actual.top),
            abs(expected.right - actual.right),
            abs(expected.bottom - actual.bottom),
        )
        assertTrue(
            "$label decoded mask $actual displaced from descriptor $expected by ${errors.maxOrNull()} px",
            errors.all { it <= tolerancePx },
        )
    }

    private fun changedFraction(a: Bitmap, b: Bitmap): Double {
        var changed = 0L
        val total = a.width.toLong() * a.height.toLong()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (pixelDifference(a.getPixel(x, y), b.getPixel(x, y)) >= MASK_THRESHOLD) changed += 1L
            }
        }
        return changed.toDouble() / total.toDouble()
    }

    private fun insideBoxChangedFraction(a: Bitmap, b: Bitmap, box: PixelRect): Double {
        var changed = 0L
        var total = 0L
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) {
                total += 1L
                if (pixelDifference(a.getPixel(x, y), b.getPixel(x, y)) >= MASK_THRESHOLD) changed += 1L
            }
        }
        return changed.toDouble() / total.toDouble()
    }

    private fun outsideBoxChangedFraction(a: Bitmap, b: Bitmap, box: PixelRect): Double {
        var changed = 0L
        var total = 0L
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (x in box.left until box.right && y in box.top until box.bottom) continue
                total += 1L
                if (pixelDifference(a.getPixel(x, y), b.getPixel(x, y)) >= MASK_THRESHOLD) changed += 1L
            }
        }
        return changed.toDouble() / total.toDouble()
    }

    private fun pixelDifference(a: Int, b: Int): Int = maxOf(
        abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)),
        abs(Color.blue(a) - Color.blue(b)),
    )

    private fun frameAt(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            requireNotNull(
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ) { "no decoded frame near ${timeUs}us" }
        } finally {
            retriever.release()
        }
    }

    private fun createDiagnosticVideo(file: File) {
        val codec = MediaCodec.createEncoderByType(MimeTypes.VIDEO_H264)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            val capabilities = codec.codecInfo.getCapabilitiesForType(MimeTypes.VIDEO_H264)
            val colorFormat = listOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            ).firstOrNull { it in capabilities.colorFormats }
                ?: error("device AVC encoder has no byte-buffer YUV420 input format")

            val format = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 900_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            var trackIndex = -1
            var nextFrame = 0
            var inputEnded = false
            var outputEnded = false
            var idleLoops = 0

            while (!outputEnded) {
                var progressed = false
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        if (nextFrame < FRAME_COUNT) {
                            fillDiagnosticYuv(buffer)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                FRAME_BYTES,
                                frameTimeUs(nextFrame),
                                0,
                            )
                            nextFrame += 1
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                frameTimeUs(FRAME_COUNT),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        }
                        progressed = true
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "encoder output format changed twice" }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        progressed = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val encoded = requireNotNull(codec.getOutputBuffer(outputIndex))
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted && trackIndex >= 0) { "encoded sample before muxer format" }
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        progressed = true
                    }
                }

                if (progressed) {
                    idleLoops = 0
                } else {
                    idleLoops += 1
                    check(idleLoops < MAX_IDLE_LOOPS) { "AVC encoder made no progress" }
                }
            }
            check(muxerStarted) { "AVC muxer never started" }
            check(nextFrame == FRAME_COUNT) { "not all diagnostic frames were queued" }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    private fun fillDiagnosticYuv(buffer: java.nio.ByteBuffer) {
        require(buffer.capacity() >= FRAME_BYTES) { "encoder input buffer too small" }
        buffer.clear()
        // Neutral-chroma diagnostic checkerboard. Keeping U and V equal makes the fixture valid for
        // both planar and semiplanar YUV420 encoder inputs; luma geometry remains deterministic.
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val dark = ((x / GRID_CELL_W) + (y / GRID_CELL_H)) and 1 == 0
                buffer.put((if (dark) 64 else 192).toByte())
            }
        }
        repeat(WIDTH * HEIGHT / 2) { buffer.put(128.toByte()) }
        buffer.flip()
    }

    private fun frameTimeUs(index: Int): Long = index.toLong() * 1_000_000L / FPS.toLong()

    private companion object {
        const val WIDTH = 426
        const val HEIGHT = 240
        const val FPS = 30
        const val DURATION_SECONDS = 3
        const val FRAME_COUNT = FPS * DURATION_SECONDS
        const val FRAME_BYTES = WIDTH * HEIGHT * 3 / 2
        const val FRAME_US = 1_000_000L / FPS
        const val GRID_CELL_W = 53
        const val GRID_CELL_H = 30
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_IDLE_LOOPS = 500
        const val MASK_THRESHOLD = 50
        const val MAX_POSITION_ERROR_PX = 2
        const val GAP_CHANGED_FRACTION_MAX = 0.02
    }
}
