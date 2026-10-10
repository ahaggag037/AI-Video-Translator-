package com.clw.aivideotranslator

import android.net.Uri
import android.os.SystemClock
import android.view.Choreographer
import android.widget.VideoView
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot
import com.clw.aivideotranslator.subtitle.android.RasterCoordinator
import com.clw.aivideotranslator.subtitle.android.RasterLease

/**
 * X004 production preview adapter.
 *
 * VideoView remains the playback control surface, but the visible subtitle is the exact immutable
 * full-frame raster snapshot also consumed by export. Position is sampled from display callbacks,
 * not a fixed timer. A missing raster shows loading/transparent state rather than a stale cue.
 */
@Composable
internal fun RasterVideoSubtitlePreview(
    sourceUri: Uri,
    snapshot: LivePresentationRasterSnapshot,
    sampleStartMs: Long,
    sampleEndMs: Long,
) {
    require(sampleStartMs >= 0L && sampleEndMs > sampleStartMs)
    val coordinator = remember(snapshot) { RasterCoordinator() }
    var videoView by remember(sourceUri, snapshot) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(sourceUri, snapshot) { mutableStateOf(false) }
    var playing by remember(sourceUri, snapshot) { mutableStateOf(false) }
    var positionMs by remember(sourceUri, snapshot) { mutableStateOf(sampleStartMs) }
    var playbackError by remember(sourceUri, snapshot) { mutableStateOf<String?>(null) }
    var rasterError by remember(sourceUri, snapshot) { mutableStateOf<String?>(null) }
    var displayLease by remember(sourceUri, snapshot) { mutableStateOf<RasterLease?>(null) }
    var displayedRequestId by remember(sourceUri, snapshot) { mutableStateOf<String?>(null) }
    var activeSemanticText by remember(sourceUri, snapshot) { mutableStateOf<String?>(null) }

    val timelineEndMs = snapshot.timeline.cues.lastOrNull()?.endUs?.div(1_000L)
    val previewEndMs = PreviewPlaybackWindow.endMs(sampleStartMs, sampleEndMs, timelineEndMs)

    displayLease?.let { lease ->
        DisposableEffect(lease) {
            onDispose { lease.close() }
        }
    }

    DisposableEffect(sourceUri, snapshot, sampleStartMs, previewEndMs) {
        val choreographer = Choreographer.getInstance()
        var disposed = false
        var pendingCueId: String? = null
        var pendingSinceMs: Long? = null

        fun prepareFor(sourcePositionMs: Long) {
            val sourceUs = runCatching { Math.multiplyExact(sourcePositionMs, 1_000L) }.getOrNull() ?: return
            val located = snapshot.timeline.locate(sourceUs)
            val active = located.active
            if (active == null) {
                located.next?.let { coordinator.prepareWindow(it.request) }
                displayedRequestId = null
                activeSemanticText = null
                displayLease = null
                pendingCueId = null
                pendingSinceMs = null
                return
            }

            coordinator.prepareWindow(active.request, located.next?.request)
            if (displayedRequestId == active.request.requestId && displayLease != null) {
                pendingCueId = null
                pendingSinceMs = null
                rasterError = null
                return
            }

            val ready = coordinator.peekPrepared(active.request.requestId)
            if (ready != null) {
                displayLease = ready
                displayedRequestId = active.request.requestId
                activeSemanticText = active.request.descriptor.text
                pendingCueId = null
                pendingSinceMs = null
                rasterError = null
                return
            }

            // Never keep a prior cue visible while a new active raster is pending.
            displayLease = null
            displayedRequestId = null
            activeSemanticText = null
            if (playing) {
                val now = SystemClock.uptimeMillis()
                if (pendingCueId != active.cueId) {
                    pendingCueId = active.cueId
                    pendingSinceMs = now
                } else {
                    val since = pendingSinceMs
                    if (since != null && now - since > MAX_PREVIEW_CUE_LAG_MS) {
                        rasterError = "تجاوز تجهيز الترجمة حد المعاينة 100ms؛ أوقفت الترجمة المرئية بدل عرض توقيت خاطئ."
                    }
                }
            }
        }

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (disposed) return
                val view = videoView
                if (view != null && prepared) {
                    val current = runCatching { view.currentPosition.toLong() }.getOrNull()
                    if (current != null) {
                        positionMs = current
                        prepareFor(current)
                        if (view.isPlaying && current >= previewEndMs) {
                            view.pause()
                            playing = false
                            positionMs = previewEndMs
                            prepareFor(previewEndMs)
                        }
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)

        onDispose {
            disposed = true
            choreographer.removeFrameCallback(frameCallback)
            displayLease = null
            displayedRequestId = null
            activeSemanticText = null
            videoView?.stopPlayback()
            videoView = null
            coordinator.close()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("معاينة التزامن — نفس raster المستخدم في التصدير", style = MaterialTheme.typography.titleMedium)
        Text("المعاينة لا تعيد تخطيط النص؛ تستخدم descriptor/raster نفسه وتحدّث موضعها مع display frames.")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black),
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
                            val sourceUs = Math.multiplyExact(sampleStartMs, 1_000L)
                            val located = snapshot.timeline.locate(sourceUs)
                            val current = located.active ?: located.next
                            if (current != null) {
                                coordinator.prepareWindow(current.request, located.next?.takeIf { it !== current }?.request)
                            }
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

            displayLease?.let { lease ->
                Image(
                    bitmap = lease.raster.bitmap.asImageBitmap(),
                    contentDescription = activeSemanticText,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(),
                )
            }
        }

        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        rasterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "الموضع: ${SubtitlePipeline.timestamp(positionMs.coerceAtLeast(0))}",
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
                            displayLease = null
                            displayedRequestId = null
                            activeSemanticText = null
                        }
                        rasterError = null
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
                    rasterError = null
                    displayLease = null
                    displayedRequestId = null
                    activeSemanticText = null
                },
            ) { Text("من البداية") }
        }
    }
}

internal const val MAX_PREVIEW_CUE_LAG_MS: Long = 100L
