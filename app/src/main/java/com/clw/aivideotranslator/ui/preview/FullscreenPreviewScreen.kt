package com.clw.aivideotranslator.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clw.aivideotranslator.presentation.AuthoritativeLayoutSnapshot

internal data class FullscreenPreviewPresentation(
    val layoutSnapshot: AuthoritativeLayoutSnapshot,
    val chromeVisible: Boolean,
    val currentTimeLabel: String,
    val durationLabel: String,
)

/**
 * Media-first preview. Subtitle geometry is rendered in the media layer and is intentionally
 * independent from control chrome visibility so hiding chrome cannot reflow the subtitle.
 */
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
            .background(Color.Black)
            .clickable(onClick = onToggleChrome),
    ) {
        SubtitleLayer(
            snapshot = presentation.layoutSnapshot,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 72.dp),
        )

        if (presentation.chromeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text("رجوع", color = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPlayPause) {
                    Text("تشغيل / إيقاف", color = Color.White)
                }
                TextButton(onClick = onSeekRequested) {
                    Text("${presentation.currentTimeLabel} / ${presentation.durationLabel}", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SubtitleLayer(
    snapshot: AuthoritativeLayoutSnapshot,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        snapshot.lines.forEach { line ->
            Text(
                text = line,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
