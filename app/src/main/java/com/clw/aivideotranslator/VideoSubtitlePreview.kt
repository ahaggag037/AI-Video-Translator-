package com.clw.aivideotranslator

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * P0-E synchronization gate: play the original selected video and render the Arabic cue
 * selected from the SAME presentation-timeline timestamps used to write the SRT.
 *
 * VideoView is intentionally sufficient for this prototype. Media3 is introduced only after
 * this timing contract is device-verified, rather than adding a new dependency before the gate.
 */
@Composable
internal fun VideoSubtitlePreview(
    sourceUri: Uri,
    cues: List<ArabicSubtitleCue>,
    sampleStartMs: Long,
    sampleEndMs: Long,
) {
    var videoView by remember(sourceUri) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(sourceUri) { mutableStateOf(false) }
    var playing by remember(sourceUri) { mutableStateOf(false) }
    var positionMs by remember(sourceUri) { mutableStateOf(sampleStartMs) }
    var playbackError by remember(sourceUri) { mutableStateOf<String?>(null) }

    val previewEndMs = minOf(sampleEndMs, cues.lastOrNull()?.endMs ?: sampleEndMs)
    val activeCue = SubtitlePipeline.activeCue(cues, positionMs)

    DisposableEffect(sourceUri) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    LaunchedEffect(videoView, prepared, previewEndMs) {
        while (isActive) {
            val view = videoView
            if (view != null && prepared) {
                runCatching { view.currentPosition.toLong() }.getOrNull()?.let { current ->
                    positionMs = current
                    if (view.isPlaying && current >= previewEndMs) {
                        view.pause()
                        playing = false
                        positionMs = previewEndMs
                    }
                }
            }
            delay(50)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("معاينة التزامن على الفيديو الأصلي", style = MaterialTheme.typography.titleMedium)
        Text(
            "المشغل يستخدم نفس timestamps التي ستُكتب في SRT؛ هذه البوابة تختبر التطابق بصريًا على الهاتف."
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(230.dp),
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

            if (activeCue != null) {
                Text(
                    text = activeCue.text,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(textDirection = TextDirection.ContentOrRtl),
                )
            }
        }

        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                        if (positionMs >= previewEndMs - 50) {
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
