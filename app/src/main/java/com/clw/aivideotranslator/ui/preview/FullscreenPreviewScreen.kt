package com.clw.aivideotranslator.ui.preview

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clw.aivideotranslator.RasterVideoSubtitlePreview
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot

internal data class FullscreenPreviewPresentation(
    val sourceUri: Uri,
    val rasterSnapshot: LivePresentationRasterSnapshot,
    val durationMs: Long,
) {
    init { require(durationMs > 0L) }
}

/** Full-video preview using the exact immutable raster snapshot later consumed by MP4 export. */
@Composable
internal fun FullscreenPreviewScreen(
    presentation: FullscreenPreviewPresentation,
    onToggleChrome: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekRequested: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        RasterVideoSubtitlePreview(
            sourceUri = presentation.sourceUri,
            snapshot = presentation.rasterSnapshot,
            sampleStartMs = 0L,
            sampleEndMs = presentation.durationMs,
        )

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Text("رجوع", color = Color.White)
        }
    }
}
