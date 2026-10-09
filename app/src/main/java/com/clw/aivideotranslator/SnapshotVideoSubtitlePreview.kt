package com.clw.aivideotranslator

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clw.aivideotranslator.media.CanonicalVideoFrame
import com.clw.aivideotranslator.media.FrameGeometryResolver
import com.clw.aivideotranslator.subtitle.android.RasterCoordinator
import com.clw.aivideotranslator.subtitle.android.RasterLease
import com.clw.aivideotranslator.subtitle.android.RenderSnapshot
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Production candidate for X004 preview parity.
 *
 * Playback remains VideoView for now, but subtitle pixels come from the exact immutable raster
 * request stored in [RenderSnapshot]. No Compose text layout occurs here. The source-video frame is
 * fit exactly once into the measured preview content rect, then the full-frame raster is scaled by
 * the same transform. Cache misses clear the old subtitle instead of displaying a stale cue.
 */
@Composable
internal fun SnapshotVideoSubtitlePreview(
    sourceUri: Uri,
    snapshot: RenderSnapshot,
    canonicalFrame: CanonicalVideoFrame,
    sampleStartMs: Long,
    sampleEndMs: Long,
) {
    require(sampleStartMs >= 0L && sampleEndMs > sampleStartMs)
    require(snapshot.geometry.uprightWidthPx == canonicalFrame.uprightWidthPx)
    require(snapshot.geometry.uprightHeightPx == canonicalFrame.uprightHeightPx)

    var videoView by remember(sourceUri) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(sourceUri) { mutableStateOf(false) }
    var playing by remember(sourceUri) { mutableStateOf(false) }
    var positionMs by remember(sourceUri) { mutableLongStateOf(sampleStartMs) }
    var playbackError by remember(sourceUri) { mutableStateOf<String?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var displayedLease by remember(snapshot.snapshotId) { mutableStateOf<RasterLease?>(null) }
    var displayedRequestId by remember(snapshot.snapshotId) { mutableStateOf<String?>(null) }
    val coordinator = remember(snapshot.snapshotId) { RasterCoordinator() }

    val previewEndMs = minOf(
        sampleEndMs,
        snapshot.cues.lastOrNull()?.endUs?.div(1_000L) ?: sampleEndMs,
    )
    val fit = remember(canonicalFrame, viewportSize) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            FrameGeometryResolver.fitCenter(
                frame = canonicalFrame,
                viewportWidthPx = viewportSize.width,
                viewportHeightPx = viewportSize.height,
            )
        } else {
            null
        }
    }

    DisposableEffect(sourceUri, snapshot.snapshotId) {
        onDispose {
            displayedLease?.close()
            displayedLease = null
            displayedRequestId = null
            coordinator.close()
            videoView?.stopPlayback()
            videoView = null
        }
    }

    LaunchedEffect(videoView, prepared, previewEndMs, snapshot.snapshotId) {
        while (isActive) {
            withFrameNanos { }
            val view = videoView ?: continue
            if (!prepared) continue
            val current = runCatching { view.currentPosition.toLong() }.getOrNull() ?: continue
            if (abs(current - positionMs) >= POSITION_LABEL_STEP_MS || !view.isPlaying) {
                positionMs = current
            }

            val sourceTimeUs = try {
                Math.multiplyExact(current.coerceAtLeast(0L), 1_000L)
            } catch (_: ArithmeticException) {
                playbackError = "موضع تشغيل الفيديو خارج النطاق المدعوم"
                continue
            }
            val located = snapshot.timeline.locate(sourceTimeUs)
            val active = located.active
            if (active == null) {
                located.next?.let { coordinator.prepareWindow(it.request) }
                displayedLease?.close()
                displayedLease = null
                displayedRequestId = null
            } else {
                coordinator.prepareWindow(active.request, located.next?.request)
                if (displayedRequestId != active.request.requestId || displayedLease == null) {
                    val replacement = coordinator.peekPrepared(active.request.requestId)
                    displayedLease?.close()
                    displayedLease = replacement
                    displayedRequestId = replacement?.let { active.request.requestId }
                }
            }

            if (view.isPlaying && current >= previewEndMs) {
                view.pause()
                playing = false
                positionMs = previewEndMs
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("معاينة الترجمة من نفس RenderSnapshot", style = MaterialTheme.typography.titleMedium)
        Text("المعاينة والتصدير يستهلكان نفس descriptor/raster؛ لا يوجد تخطيط نص مستقل داخل Compose.")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black)
                .onSizeChanged { viewportSize = it },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).also { view ->
                        videoView = view
                        view.setVideoURI(sourceUri)
                        view.setOnPreparedListener {
                            prepared = true
                            playbackError = null
                            view.seekTo(sampleStartMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            positionMs = sampleStartMs
                        }
                        view.setOnCompletionListener {
                            playing = false
                            positionMs = previewEndMs
                        }
                        view.setOnErrorListener { _, _, _ ->
                            playbackError = "تعذر تشغيل الفيديو في معاينة التوقيت"
                            playing = false
                            true
                        }
                    }
                },
            )

            val raster = displayedLease?.raster
            val content = fit?.contentRect
            if (raster != null && content != null && !raster.bitmap.isRecycled) {
                val image = raster.bitmap.asImageBitmap()
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(content.leftPx, content.topPx),
                        dstSize = IntSize(content.widthPx, content.heightPx),
                    )
                }
            }
        }

        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "الموضع: ${SubtitlePipeline.timestamp(positionMs.coerceAtLeast(0L))}",
            style = TextStyle(textDirection = TextDirection.Ltr),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = prepared,
                onClick = {
                    val view = videoView ?: return@Button
                    if (view.isPlaying) {
                        view.pause()
                        playing = false
                        positionMs = view.currentPosition.toLong()
                    } else {
                        if (positionMs >= previewEndMs - 50L) {
                            view.seekTo(sampleStartMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            positionMs = sampleStartMs
                        }
                        view.start()
                        playing = true
                    }
                },
            ) { Text(if (playing) "إيقاف" else "تشغيل") }

            Button(
                enabled = prepared,
                onClick = {
                    val view = videoView ?: return@Button
                    view.pause()
                    view.seekTo(sampleStartMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    positionMs = sampleStartMs
                    playing = false
                },
            ) { Text("من البداية") }
        }
    }
}

private const val POSITION_LABEL_STEP_MS = 200L
