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

internal enum class AppearancePreference { SYSTEM, LIGHT, DARK }

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
                settingRow(
                    title = "إعداد الذكاء الاصطناعي",
                    detail = if (presentation.aiCredentialConfigured) {
                        "تم إعداد مفتاح NVIDIA"
                    } else {
                        "لم يتم إعداد المفتاح"
                    },
                    onClick = onAiCredential,
                )
                settingRow(
                    title = "حول وتشخيص",
                    detail = "هوية الإصدار وحالة الجلسة والمصدر والمزود",
                    onClick = onAboutDiagnostics,
                )
                Text(
                    text = "تظهر هنا فقط الإعدادات المكتملة فعليًا في الإصدار الحالي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun settingRow(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
