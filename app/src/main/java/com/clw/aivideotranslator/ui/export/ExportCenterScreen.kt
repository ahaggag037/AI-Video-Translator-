package com.clw.aivideotranslator.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.clw.aivideotranslator.export.ExportEligibility
import com.clw.aivideotranslator.export.ExportMode
import com.clw.aivideotranslator.presentation.LayoutStatus

internal data class ExportCenterPresentation(
    val mode: ExportMode,
    val eligibility: ExportEligibility,
    val layoutStatus: LayoutStatus,
    val baseFileName: String,
    val srtLocationLabel: String = "Downloads / AI Video Translator",
    val mp4LocationLabel: String = "Movies / AI Video Translator",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportCenterScreen(
    presentation: ExportCenterPresentation,
    onBack: () -> Unit,
    onModeChange: (ExportMode) -> Unit,
    onEditFilename: () -> Unit,
    onExport: () -> Unit,
) {
    val selectedEligible = when (presentation.mode) {
        ExportMode.SRT_ONLY -> presentation.eligibility.srtEligible
        ExportMode.MP4_ONLY -> presentation.eligibility.mp4Eligible
        ExportMode.BOTH -> presentation.eligibility.srtEligible && presentation.eligibility.mp4Eligible
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التصدير") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "اختر نوع الملف",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                modeButton(
                    label = "ملف ترجمة فقط (SRT)",
                    selected = presentation.mode == ExportMode.SRT_ONLY,
                    enabled = presentation.eligibility.srtEligible,
                    onClick = { onModeChange(ExportMode.SRT_ONLY) },
                )
                modeButton(
                    label = "فيديو مع ترجمة مدمجة (MP4)",
                    selected = presentation.mode == ExportMode.MP4_ONLY,
                    enabled = presentation.eligibility.mp4Eligible,
                    onClick = { onModeChange(ExportMode.MP4_ONLY) },
                )
                modeButton(
                    label = "الفيديو + ملف الترجمة",
                    selected = presentation.mode == ExportMode.BOTH,
                    enabled = presentation.eligibility.srtEligible && presentation.eligibility.mp4Eligible,
                    onClick = { onModeChange(ExportMode.BOTH) },
                )

                if (!presentation.eligibility.mp4Eligible && presentation.eligibility.srtEligible) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when (presentation.layoutStatus) {
                                LayoutStatus.OVERFLOW -> "تصدير MP4 متوقف لأن الترجمة تتجاوز المساحة الآمنة. يمكنك تصدير SRT الآن."
                                LayoutStatus.REVIEW_REQUIRED -> "تصدير MP4 يحتاج مراجعة التخطيط أولًا. يمكنك تصدير SRT الآن."
                                LayoutStatus.FITS -> "تصدير MP4 غير متاح حتى يصبح مصدر الفيديو متاحًا. يمكنك تصدير SRT الآن."
                            },
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("اسم الإخراج", fontWeight = FontWeight.Bold)
                        Text(presentation.baseFileName)
                        TextButton(onClick = onEditFilename) { Text("تعديل الاسم") }
                    }
                }

                if (presentation.mode != ExportMode.MP4_ONLY) {
                    outputLocation("SRT", presentation.srtLocationLabel)
                }
                if (presentation.mode != ExportMode.SRT_ONLY) {
                    outputLocation("MP4", presentation.mp4LocationLabel)
                }

                if (presentation.mode == ExportMode.SRT_ONLY) {
                    Text(
                        text = "مسار SRT سريع ولا يشغّل فك ترميز أو حرق أو ترميز الفيديو.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (presentation.mode == ExportMode.BOTH) {
                    Text(
                        text = "سيتم تثبيت نتيجة SRT بشكل مستقل أولًا، ثم يستمر إنشاء MP4. نجاح أحدهما لا يختفي إذا فشل الآخر.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = onExport,
                    enabled = selectedEligible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("بدء التصدير")
                }
            }
        }
    }
}

@Composable
private fun modeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(label) }
    }
}

@Composable
private fun outputLocation(kind: String, location: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(kind, fontWeight = FontWeight.Bold)
            Text(location, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
