package com.clw.aivideotranslator.ui.editor

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Product Editor/Text surface. Provider actions are deliberately absent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTextScreen(
    presentation: EditorTextPresentation,
    onBack: () -> Unit,
    onTargetChange: (String) -> Unit,
    onCommitDraft: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRestoreMachine: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenTiming: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenExport: () -> Unit,
    onExportSrtOnly: () -> Unit,
    onPreviousCue: () -> Unit = {},
    onNextCue: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المحرر") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
                },
                actions = {
                    TextButton(
                        onClick = onOpenFullscreen,
                        enabled = presentation.fullscreenPreviewEnabled,
                    ) { Text("ملء الشاشة") }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (presentation.sourceMissing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "مصدر الفيديو غير متاح",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "يمكنك مراجعة النص وتعديله وتصدير SRT. التشغيل والتوقيت والنمط والفيديو المدمج متوقفة حتى اختيار فيديو جديد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                presentation.actionError?.takeIf { it.isNotBlank() }?.let { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPreviousCue,
                        enabled = presentation.canPreviousCue,
                        modifier = Modifier.weight(1f),
                    ) { Text("السابق") }
                    Text(
                        text = presentation.cuePositionLabel.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(
                        onClick = onNextCue,
                        enabled = presentation.canNextCue,
                        modifier = Modifier.weight(1f),
                    ) { Text("التالي") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("النص") }
                    OutlinedButton(
                        onClick = onOpenTiming,
                        enabled = presentation.timingEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("التوقيت") }
                    OutlinedButton(
                        onClick = onOpenStyle,
                        enabled = presentation.styleEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("النمط") }
                }

                if (presentation.reviewRequired) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "هذه الترجمة تحتاج مراجعة قبل اعتمادها كنسخة نهائية.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "النص الأصلي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = presentation.sourceText.ifBlank { "النص الأصلي غير متاح في وضع الاسترداد الحالي." },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Text(
                    text = "الترجمة العربية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = presentation.targetText,
                    onValueChange = onTargetChange,
                    enabled = presentation.textEditingEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    supportingText = if (presentation.hasPendingDraft) {
                        { Text("مسودة محفوظة للاسترداد ولم تُعتمد بعد للتصدير") }
                    } else {
                        null
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("تراجع") }
                    OutlinedButton(onClick = onRedo, modifier = Modifier.weight(1f)) { Text("إعادة") }
                }

                OutlinedButton(
                    onClick = onRestoreMachine,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("استعادة الترجمة الآلية") }

                Button(
                    onClick = onCommitDraft,
                    enabled = presentation.hasPendingDraft && presentation.targetText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("حفظ التعديل") }

                if (presentation.sourceMissing) {
                    Button(
                        onClick = onExportSrtOnly,
                        enabled = presentation.srtExportEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text("تصدير ملف SRT") }
                } else {
                    Button(
                        onClick = onOpenExport,
                        enabled = presentation.srtExportEnabled || presentation.mp4ExportEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text("تصدير") }
                }
            }
        }
    }
}
