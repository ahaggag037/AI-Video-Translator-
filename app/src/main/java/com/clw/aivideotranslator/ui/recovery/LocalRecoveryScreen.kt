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

internal enum class LocalRecoveryPhase {
    BASE,
    CLEAN_CONFIRM,
    BACKUP_FAILED,
}

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
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = when (phase) {
                        LocalRecoveryPhase.BASE -> "تعذر فتح الجلسة بأمان"
                        LocalRecoveryPhase.CLEAN_CONFIRM -> "تأكيد بدء جلسة نظيفة"
                        LocalRecoveryPhase.BACKUP_FAILED -> "تعذر حفظ نسخة الاسترداد المحلية"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = when (phase) {
                            LocalRecoveryPhase.BASE ->
                                "أوقف التطبيق الاسترداد بدل تخمين أو تعديل بيانات الجلسة. لا يوجد أي إعادة إرسال تلقائية للمزود من هذه الشاشة."
                            LocalRecoveryPhase.CLEAN_CONFIRM ->
                                "هذه الخطوة تمسح الجلسة المحلية الحالية وتبدأ من جديد. لن تُنفذ إلا بعد التأكيد الصريح."
                            LocalRecoveryPhase.BACKUP_FAILED ->
                                "لم يبدأ التنظيف لأن خطوة الحماية المحلية لم تكتمل. بيانات الجلسة الحالية لم تُعتبر قابلة للحذف بأمان."
                        },
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (phase == LocalRecoveryPhase.CLEAN_CONFIRM) {
                    Button(
                        onClick = onConfirmCleanStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("تأكيد بدء جلسة نظيفة")
                    }
                    OutlinedButton(
                        onClick = onCancelCleanStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("إلغاء")
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenDiagnostics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("فتح التشخيص")
                    }
                    Button(
                        onClick = onRequestCleanStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("بدء جلسة نظيفة")
                    }
                }
            }
        }
    }
}
