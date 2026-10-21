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

internal enum class SourceRecoveryPhase {
    MISSING_WITH_TEXT,
    MISSING_NO_TEXT,
    RELINK_VERIFYING,
    RELINK_MISMATCH,
}

internal data class SourceRecoveryPresentation(
    val phase: SourceRecoveryPhase,
    val textOnlyAvailable: Boolean,
    val srtExportAvailable: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceRecoveryScreen(
    presentation: SourceRecoveryPresentation,
    onBackHome: () -> Unit,
    onContinueTextOnly: () -> Unit,
    onRelinkSource: () -> Unit,
    onExportSrt: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استرداد مصدر الفيديو") },
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
                    text = phaseTitle(presentation.phase),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = phaseBody(presentation.phase),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (presentation.textOnlyAvailable) {
                    Button(
                        onClick = onContinueTextOnly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("متابعة العمل على النص فقط")
                    }
                }

                if (presentation.srtExportAvailable) {
                    OutlinedButton(
                        onClick = onExportSrt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("تصدير SRT")
                    }
                }

                OutlinedButton(
                    onClick = onRelinkSource,
                    enabled = presentation.phase != SourceRecoveryPhase.RELINK_VERIFYING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        if (presentation.phase == SourceRecoveryPhase.RELINK_VERIFYING) {
                            "جارٍ التحقق من المصدر…"
                        } else {
                            "اختيار فيديو وبدء جلسة جديدة"
                        },
                    )
                }
            }
        }
    }
}

private fun phaseTitle(phase: SourceRecoveryPhase): String = when (phase) {
    SourceRecoveryPhase.MISSING_WITH_TEXT -> "الفيديو غير متاح، لكن النص محفوظ"
    SourceRecoveryPhase.MISSING_NO_TEXT -> "مصدر الفيديو غير متاح"
    SourceRecoveryPhase.RELINK_VERIFYING -> "جارٍ التحقق من الفيديو المحدد"
    SourceRecoveryPhase.RELINK_MISMATCH -> "الفيديو المحدد لا يطابق المصدر المحفوظ"
}

private fun phaseBody(phase: SourceRecoveryPhase): String = when (phase) {
    SourceRecoveryPhase.MISSING_WITH_TEXT ->
        "يمكنك مراجعة النص وتعديله وتصدير SRT. التشغيل والتوقيت والنمط وتصدير MP4 ستظل متوقفة حتى إعادة ربط مصدر متوافق."
    SourceRecoveryPhase.MISSING_NO_TEXT ->
        "لا يمكن متابعة المعالجة المعتمدة على الفيديو حتى إعادة ربط المصدر الصحيح."
    SourceRecoveryPhase.RELINK_VERIFYING ->
        "سيتم فحص الفيديو المحدد قبل بدء جلسة جديدة، وتبقى بيانات الجلسة السابقة محفوظة محليًا."
    SourceRecoveryPhase.RELINK_MISMATCH ->
        "لم يبدأ التطبيق جلسة جديدة بهذا الملف. اختر فيديو صالحًا وحاول مرة أخرى."
}
