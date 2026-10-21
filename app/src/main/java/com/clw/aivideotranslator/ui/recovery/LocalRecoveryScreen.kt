package com.clw.aivideotranslator.ui.recovery

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

internal enum class LocalRecoveryPhase { BASE, CLEAN_CONFIRM, BACKUP_FAILED }

/** Fail-closed local recovery. No destructive cleanup is exposed without a verified backup path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalRecoveryScreen(
    phase: LocalRecoveryPhase,
    onBackHome: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestCleanStart: () -> Unit,
    onConfirmCleanStart: () -> Unit,
    onCancelCleanStart: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استرداد الجلسة المحلية") },
                navigationIcon = { TextButton(onClick = onBackHome) { Text("الرئيسية") } },
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
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "تعذر فتح الجلسة بأمان",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "أوقف التطبيق الاسترداد بدل تخمين أو تعديل بيانات الجلسة. لن تُحذف البيانات ولن يُعاد إرسال أي طلب للمزود من هذه الشاشة.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("فتح التشخيص") }
                Button(
                    onClick = onRequestCleanStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("اختيار فيديو وبدء جلسة جديدة") }
            }
        }
    }
}
