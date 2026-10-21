package com.clw.aivideotranslator.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal data class TimingEditorPresentation(
    val startField: String,
    val endField: String,
    val dirty: Boolean,
    val validation: TimingDraftValidation,
    val sourceAvailable: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimingEditorScreen(
    presentation: TimingEditorPresentation,
    onBack: () -> Unit,
    onOpenText: () -> Unit,
    onOpenStyle: () -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onNudgeStartBackward: () -> Unit,
    onNudgeStartForward: () -> Unit,
    onNudgeEndBackward: () -> Unit,
    onNudgeEndForward: () -> Unit,
    onPlayCue: () -> Unit,
    onResetToSource: () -> Unit,
    onCommit: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("توقيت الترجمة") },
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("معاينة الفيديو", color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onOpenText, modifier = Modifier.weight(1f)) {
                        Text("النص")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("التوقيت")
                    }
                    OutlinedButton(onClick = onOpenStyle, modifier = Modifier.weight(1f)) {
                        Text("النمط")
                    }
                }

                if (!presentation.sourceAvailable) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "التوقيت غير متاح حتى إعادة ربط مصدر الفيديو.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = presentation.startField,
                            onValueChange = onStartChange,
                            label = { Text("Start") },
                            enabled = presentation.sourceAvailable,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = presentation.endField,
                            onValueChange = onEndChange,
                            label = { Text("End") },
                            enabled = presentation.sourceAvailable,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }

                if (!presentation.validation.isValid) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "يجب أن يكون وقت البدء قبل وقت الانتهاء وألا يتداخل المقطع مع المقطع السابق أو التالي.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Text(
                    text = "ضبط البداية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onNudgeStartBackward,
                        enabled = presentation.sourceAvailable,
                        modifier = Modifier.weight(1f),
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = onNudgeStartForward,
                        enabled = presentation.sourceAvailable,
                        modifier = Modifier.weight(1f),
                    ) { Text("+") }
                }

                Text(
                    text = "ضبط النهاية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onNudgeEndBackward,
                        enabled = presentation.sourceAvailable,
                        modifier = Modifier.weight(1f),
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = onNudgeEndForward,
                        enabled = presentation.sourceAvailable,
                        modifier = Modifier.weight(1f),
                    ) { Text("+") }
                }

                OutlinedButton(
                    onClick = onPlayCue,
                    enabled = presentation.sourceAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("تشغيل هذا المقطع") }

                OutlinedButton(
                    onClick = onResetToSource,
                    enabled = presentation.sourceAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("إعادة إلى توقيت المصدر") }

                Button(
                    onClick = onCommit,
                    enabled = presentation.sourceAvailable && presentation.dirty && presentation.validation.isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("حفظ التوقيت") }
            }
        }
    }
}
