package com.clw.aivideotranslator

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clw.aivideotranslator.pipeline.ProductionPipelineLiveProjector
import com.clw.aivideotranslator.pipeline.ProductionPipelineProgress
import com.clw.aivideotranslator.pipeline.ProductionPipelineStage
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Final-product status surface for long-running media/provider work.
 *
 * It intentionally exposes operational state rather than an indefinite spinner. The input progress
 * contract contains no secret/private text, so this card never needs API keys, transcripts, source
 * locators, or raw provider bodies to explain what the app is doing.
 */
@Composable
internal fun ProductionPipelineStatusCard(
    progress: ProductionPipelineProgress,
    modifier: Modifier = Modifier,
) {
    var nowMonotonicMs by remember(progress.lastProgressMonotonicMs) {
        mutableLongStateOf(maxOf(SystemClock.elapsedRealtime(), progress.lastProgressMonotonicMs))
    }

    LaunchedEffect(progress.stage, progress.lastProgressMonotonicMs) {
        nowMonotonicMs = maxOf(SystemClock.elapsedRealtime(), progress.lastProgressMonotonicMs)
        while (isActive && !progress.isTerminal) {
            delay(1_000L)
            nowMonotonicMs = maxOf(SystemClock.elapsedRealtime(), progress.lastProgressMonotonicMs)
        }
    }

    val live = ProductionPipelineLiveProjector.project(progress, nowMonotonicMs)
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("حالة المعالجة", fontWeight = FontWeight.Bold)
            Text(stageLabel(live.stage), fontWeight = FontWeight.SemiBold)

            live.fraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("التقدم: ${String.format(Locale.ROOT, "%.1f", fraction * 100.0)}%")
            } ?: Text("النسبة الكلية: غير معروفة بعد — لن يتم عرض نسبة تخمينية.")

            if (live.completed != null && live.total != null) {
                val current = live.currentOneBased?.let { " — الحالي: $it/${live.total}" }.orEmpty()
                Text("المكتمل: ${live.completed}/${live.total}$current")
            }

            if (live.bytesProcessed != null) {
                val total = live.bytesTotal?.let { " / ${formatMetricBytes(it)}" }.orEmpty()
                Text("البيانات: ${formatMetricBytes(live.bytesProcessed)}$total")
            }
            live.bytesPerSecond?.let { rate ->
                Text("السرعة: ${formatMetricBytes(rate.toLong())}/s")
            }

            StatusRow("زمن العملية", formatElapsed(live.operationElapsedMs))
            StatusRow("زمن المرحلة", formatElapsed(live.stageElapsedMs))
            live.providerWaitElapsedMs?.let { wait ->
                StatusRow("انتظار NVIDIA", formatElapsed(wait))
            }
            live.inFlightRequests?.let { StatusRow("طلبات قيد التنفيذ", it.toString()) }
            live.queueDepth?.let { StatusRow("عمل منتظر", it.toString()) }
            if (live.reusedItems > 0) StatusRow("عمل مُعاد استخدامه", live.reusedItems.toString())

            Text("آخر تقدم فعلي منذ ${formatElapsed(live.sinceLastProgressMs)}")
            live.diagnosticCode?.let { code -> Text("رمز الحالة: $code") }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun stageLabel(stage: ProductionPipelineStage): String = when (stage) {
    ProductionPipelineStage.SOURCE_CAPTURING -> "جارٍ نسخ المصدر والتحقق من هويته"
    ProductionPipelineStage.SOURCE_READY -> "المصدر الموثق جاهز"
    ProductionPipelineStage.AUDIO_DECODING -> "جارٍ فك الصوت وتجهيز نوافذ STT"
    ProductionPipelineStage.STT_PREPARING -> "جارٍ تجهيز طلب STT"
    ProductionPipelineStage.STT_SENDING -> "جارٍ إرسال نافذة STT"
    ProductionPipelineStage.STT_WAITING_PROVIDER -> "تم الإرسال — جارٍ انتظار NVIDIA STT"
    ProductionPipelineStage.STT_RECEIVED -> "تم استلام نتيجة STT"
    ProductionPipelineStage.STT_ASSEMBLING -> "جارٍ تجميع نتائج STT"
    ProductionPipelineStage.TRANSLATION_PREPARING -> "جارٍ تجهيز وحدات الترجمة"
    ProductionPipelineStage.TRANSLATION_SENDING -> "جارٍ إرسال وحدة ترجمة"
    ProductionPipelineStage.TRANSLATION_WAITING_PROVIDER -> "تم الإرسال — جارٍ انتظار NVIDIA للترجمة"
    ProductionPipelineStage.TRANSLATION_RECEIVED -> "تم استلام نتيجة ترجمة"
    ProductionPipelineStage.PRESENTATION_BUILDING -> "جارٍ بناء التوقيت والتخطيط والرسم"
    ProductionPipelineStage.PREVIEW_READY -> "المعاينة جاهزة"
    ProductionPipelineStage.EXPORTING_VIDEO -> "جارٍ تصدير MP4"
    ProductionPipelineStage.EXPORT_VALIDATING -> "الرندر اكتمل — جارٍ فحص الفيديو النهائي"
    ProductionPipelineStage.COMPLETE -> "✓ اكتملت العملية"
    ProductionPipelineStage.BLOCKED_UNKNOWN_REMOTE_OUTCOME -> "توقفت بأمان: نتيجة بعيدة غير مؤكدة"
    ProductionPipelineStage.FAILED -> "تعذر إكمال المرحلة"
    ProductionPipelineStage.CANCELLED -> "تم إلغاء العملية"
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private fun formatMetricBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    return when {
        safe >= 1_073_741_824.0 -> String.format(Locale.ROOT, "%.2f GiB", safe / 1_073_741_824.0)
        safe >= 1_048_576.0 -> String.format(Locale.ROOT, "%.1f MiB", safe / 1_048_576.0)
        safe >= 1_024.0 -> String.format(Locale.ROOT, "%.1f KiB", safe / 1_024.0)
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}
