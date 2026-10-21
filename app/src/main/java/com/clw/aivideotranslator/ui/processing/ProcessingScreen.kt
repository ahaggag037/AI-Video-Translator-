package com.clw.aivideotranslator.ui.processing

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class ProcessingStage {
    AUDIO_PREPARATION,
    STT,
    TEXT_PREPARATION,
    TRANSLATION,
    REVIEW_PREPARATION,
}

internal data class ProcessingPresentation(
    val stage: ProcessingStage,
    val providerRequestSent: Boolean = false,
    val safeStopAvailable: Boolean = false,
    val detail: String? = null,
)

/** Truthful stage-based progress only: no invented percentage and no false remote cancellation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProcessingScreen(
    presentation: ProcessingPresentation,
    onBack: () -> Unit,
    onSafeStop: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المعالجة") },
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
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stageHeadline(presentation),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (presentation.providerRequestSent) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "أُرسل الطلب إلى المزود بالفعل. مغادرة هذه الشاشة لا تعني إلغاء الطلب البعيد، ولن يعيد التطبيق إرساله تلقائيًا.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ProcessingStage.entries.forEach { stage ->
                    StageRow(
                        label = stageLabel(stage),
                        state = when {
                            stage.ordinal < presentation.stage.ordinal -> "مكتمل"
                            stage == presentation.stage -> "جارٍ الآن"
                            else -> "لاحقًا"
                        },
                        active = stage == presentation.stage,
                    )
                }

                presentation.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (presentation.safeStopAvailable) {
                    Button(
                        onClick = onSafeStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("إيقاف آمن")
                    }
                }

                TextButton(onClick = onOpenDetails) {
                    Text("تفاصيل المعالجة")
                }
            }
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    state: String,
    active: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = state,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun stageHeadline(presentation: ProcessingPresentation): String = when (presentation.stage) {
    ProcessingStage.AUDIO_PREPARATION -> "جارٍ إعداد الصوت"
    ProcessingStage.STT -> if (presentation.providerRequestSent) {
        "بانتظار نتيجة تحويل الكلام إلى نص"
    } else {
        "جارٍ تجهيز طلب تحويل الكلام إلى نص"
    }
    ProcessingStage.TEXT_PREPARATION -> "جارٍ تجهيز النص"
    ProcessingStage.TRANSLATION -> "جارٍ الترجمة"
    ProcessingStage.REVIEW_PREPARATION -> "جارٍ تجهيز المراجعة"
}

private fun stageLabel(stage: ProcessingStage): String = when (stage) {
    ProcessingStage.AUDIO_PREPARATION -> "إعداد الصوت"
    ProcessingStage.STT -> "تحويل الكلام إلى نص"
    ProcessingStage.TEXT_PREPARATION -> "تجهيز النص"
    ProcessingStage.TRANSLATION -> "الترجمة"
    ProcessingStage.REVIEW_PREPARATION -> "تجهيز المراجعة"
}
