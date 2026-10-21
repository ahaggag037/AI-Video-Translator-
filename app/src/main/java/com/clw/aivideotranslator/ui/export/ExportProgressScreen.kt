package com.clw.aivideotranslator.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

internal enum class ExportProgressStage {
    PREPARING_RASTERS,
    RENDERING,
    FINALIZING,
    VALIDATING,
    CANCELLING,
    FAILED,
}

internal data class ExportProgressPresentation(
    val stage: ExportProgressStage,
    val srtAlreadyValidated: Boolean = false,
    val cancelAvailable: Boolean = false,
    val failureMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportProgressScreen(
    presentation: ExportProgressPresentation,
    onBack: () -> Unit,
    onRequestCancel: () -> Unit,
    onRetryFailedOutput: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تقدم التصدير") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stageTitle(presentation.stage),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "يعرض التطبيق مرحلة العمل الفعلية فقط ولا يستخدم نسبة تقدم مصطنعة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (presentation.srtAlreadyValidated) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "ملف SRT تم التحقق منه وحفظه بنجاح، وسيظل متاحًا حتى إذا تعذر إنشاء MP4.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                presentation.failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (presentation.cancelAvailable && presentation.stage != ExportProgressStage.CANCELLING) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("إلغاء التصدير")
                    }
                }

                if (presentation.stage == ExportProgressStage.FAILED) {
                    Button(
                        onClick = onRetryFailedOutput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("إعادة محاولة الملف الفاشل فقط")
                    }
                }
            }
        }
    }
}

private fun stageTitle(stage: ExportProgressStage): String = when (stage) {
    ExportProgressStage.PREPARING_RASTERS -> "جارٍ تجهيز رسومات الترجمة"
    ExportProgressStage.RENDERING -> "جارٍ إنشاء الفيديو"
    ExportProgressStage.FINALIZING -> "جارٍ إنهاء ملف الإخراج"
    ExportProgressStage.VALIDATING -> "جارٍ التحقق من الملف"
    ExportProgressStage.CANCELLING -> "جارٍ تنفيذ الإلغاء"
    ExportProgressStage.FAILED -> "تعذر إكمال أحد المخرجات"
}
