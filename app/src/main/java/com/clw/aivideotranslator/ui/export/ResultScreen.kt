package com.clw.aivideotranslator.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clw.aivideotranslator.export.ExportResultTruth
import com.clw.aivideotranslator.export.OutputKind
import com.clw.aivideotranslator.export.OutputSettlement

internal data class ResultPresentation(
    val truth: ExportResultTruth,
    val srtFileName: String? = null,
    val mp4FileName: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultScreen(
    presentation: ResultPresentation,
    onBackHome: () -> Unit,
    onOpen: (OutputKind) -> Unit,
    onShare: (OutputKind) -> Unit,
    onLocate: (OutputKind) -> Unit,
    onRetry: (OutputKind) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النتيجة") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) { Text("الرئيسية") }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "ملفات التصدير",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                outputCard(
                    label = "SRT",
                    fileName = presentation.srtFileName,
                    settlement = presentation.truth.srt,
                    kind = OutputKind.SRT,
                    onOpen = onOpen,
                    onShare = onShare,
                    onRetry = onRetry,
                )

                outputCard(
                    label = "MP4",
                    fileName = presentation.mp4FileName,
                    settlement = presentation.truth.mp4,
                    kind = OutputKind.MP4,
                    onOpen = onOpen,
                    onShare = onShare,
                    onRetry = onRetry,
                )

                Button(
                    onClick = onBackHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("العودة إلى الرئيسية")
                }
            }
        }
    }
}

@Composable
private fun outputCard(
    label: String,
    fileName: String?,
    settlement: OutputSettlement,
    kind: OutputKind,
    onOpen: (OutputKind) -> Unit,
    onShare: (OutputKind) -> Unit,
    onRetry: (OutputKind) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, fontWeight = FontWeight.Bold)
            fileName?.let { Text(it) }
            Text(
                text = when (settlement) {
                    OutputSettlement.NOT_REQUESTED -> "لم يُطلب"
                    OutputSettlement.SUCCESS -> "تم بنجاح"
                    OutputSettlement.FAILED -> "فشل"
                    OutputSettlement.CANCELLED -> "أُلغي"
                },
                color = if (settlement == OutputSettlement.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (settlement == OutputSettlement.SUCCESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onOpen(kind) }, modifier = Modifier.weight(1f)) {
                        Text("فتح")
                    }
                    OutlinedButton(onClick = { onShare(kind) }, modifier = Modifier.weight(1f)) {
                        Text("مشاركة")
                    }
                }
            }

            if (settlement == OutputSettlement.FAILED || settlement == OutputSettlement.CANCELLED) {
                Button(
                    onClick = { onRetry(kind) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("إعادة محاولة $label فقط")
                }
            }
        }
    }
}
