package com.clw.aivideotranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class DiagnosticsPresentation(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val sessionStateLabel: String,
    val sourceStateLabel: String,
    val providerStateLabel: String,
    val advancedExpanded: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsScreen(
    presentation: DiagnosticsPresentation,
    onBack: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حول وتشخيص") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
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
                    text = "AI Video Translator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("الإصدار ${presentation.versionName} (${presentation.versionCode})")
                        Text("نوع البناء: ${presentation.buildType}")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("حالة آمنة مختصرة", fontWeight = FontWeight.Bold)
                        Text("الجلسة: ${presentation.sessionStateLabel}")
                        Text("المصدر: ${presentation.sourceStateLabel}")
                        Text("المزود: ${presentation.providerStateLabel}")
                    }
                }
                TextButton(onClick = onToggleAdvanced) {
                    Text(if (presentation.advancedExpanded) "إخفاء التشخيص المتقدم" else "عرض التشخيص المتقدم")
                }
                if (presentation.advancedExpanded) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "لا يعرض هذا القسم API Key أو نص الترجمة أو عنوان ملف الفيديو أو أي محتوى خاص.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "الخط المضمّن Noto Sans Arabic مرخّص وفق SIL Open Font License 1.1، ونص الترخيص مضمّن داخل التطبيق.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
