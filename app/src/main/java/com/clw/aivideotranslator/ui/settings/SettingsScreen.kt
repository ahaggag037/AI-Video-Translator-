package com.clw.aivideotranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

internal enum class AppearancePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

internal data class SettingsPresentation(
    val appearance: AppearancePreference,
    val aiCredentialConfigured: Boolean,
    val cacheSummary: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    presentation: SettingsPresentation,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onAiCredential: () -> Unit,
    onDefaultStyle: () -> Unit,
    onClearCache: () -> Unit,
    onClearProject: () -> Unit,
    onAboutDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                settingRow(
                    title = "المظهر",
                    detail = when (presentation.appearance) {
                        AppearancePreference.SYSTEM -> "حسب النظام"
                        AppearancePreference.LIGHT -> "فاتح"
                        AppearancePreference.DARK -> "داكن"
                    },
                    onClick = onAppearance,
                )
                settingRow(
                    title = "إعداد الذكاء الاصطناعي",
                    detail = if (presentation.aiCredentialConfigured) "تم إعداد المفتاح" else "لم يتم إعداد المفتاح",
                    onClick = onAiCredential,
                )
                settingRow(
                    title = "النمط الافتراضي",
                    detail = "يُنسخ إلى المشاريع الجديدة فقط",
                    onClick = onDefaultStyle,
                )
                settingRow(
                    title = "التخزين المؤقت",
                    detail = presentation.cacheSummary,
                    onClick = onClearCache,
                )
                settingRow(
                    title = "بيانات المشروع",
                    detail = "مسح الجلسة المحلية يتطلب تأكيدًا",
                    onClick = onClearProject,
                )
                settingRow(
                    title = "حول وتشخيص",
                    detail = "هوية الإصدار والحالة التقنية الآمنة والتراخيص",
                    onClick = onAboutDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun settingRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
