package com.clw.aivideotranslator

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun TranslationCard(result: NvidiaSttResult, apiKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("ستُرسل النصوص الإنجليزية فقط إلى NVIDIA.") }
    var units by remember { mutableStateOf<List<SourceUnit>>(emptyList()) }
    var cues by remember { mutableStateOf<List<ArabicSubtitleCue>>(emptyList()) }
    var srt by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null && snapshot != null) {
            scope.launch {
                exporting = true
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("تعذر فتح ملف الحفظ")
                        output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                    }
                }
                exporting = false
                Toast.makeText(context, if (saved.isSuccess) "تم حفظ sample_ar.srt" else
                    "تعذر حفظ SRT؛ أعد المحاولة", Toast.LENGTH_LONG).show()
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الترجمة العربية — أول 60 ثانية · HF2")
            Text("الإصدار 0.1.1-p0d-hf2")
            Text(NvidiaTranslationClient.MODEL_ID)
            Text(status)
            Button(
                enabled = !busy && !exporting && pendingExport == null && apiKey.isNotBlank(),
                onClick = {
                    val keySnapshot = apiKey
                    busy = true
                    srt = null
                    cues = emptyList()
                    units = emptyList()
                    job = scope.launch {
                        try {
                            val source = SubtitlePipeline.sourceUnits(result.words)
                            units = source
                            val translated = mutableListOf<TranslationEntry>()
                            source.forEachIndexed { index, unit ->
                                status = "جارٍ ترجمة الوحدة ${index + 1} / ${source.size}…"
                                val text = NvidiaTranslationClient.translate(keySnapshot, unit.sourceText)
                                translated += TranslationEntry(unit.id, text)
                                if (index < source.lastIndex) delay(1_500)
                            }
                            val complete = SubtitlePipeline.cues(source, translated)
                            val text = SubtitlePipeline.srt(complete)
                            // Publish only a completely validated translation snapshot.
                            cues = complete
                            srt = text
                            status = "✓ اكتملت الترجمة وإنشاء sample_ar.srt؛ التوقيت مطابق للمصدر."
                        } catch (e: CancellationException) {
                            status = "أُلغيت الترجمة؛ لم يتم إنشاء SRT."
                            throw e
                        } catch (e: Exception) {
                            status = e.message ?: "تعذر إكمال الترجمة"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("ترجمة العينة إلى العربية") }
            if (busy) {
                Button(onClick = { job?.cancel() }) { Text("إلغاء الترجمة") }
            }
            val completedSrt = srt
            if (completedSrt != null) {
                Text("الأزمنة نسبةً إلى بداية عينة WAV، وليست إزاحة للفيديو الكامل.")
                Button(enabled = !exporting && pendingExport == null, onClick = {
                    pendingExport = completedSrt
                    save.launch("sample_ar.srt")
                }) { Text("حفظ sample_ar.srt") }
                Button(enabled = !exporting, onClick = {
                    scope.launch {
                        exporting = true
                        val shared = runCatching {
                            val file = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "p0_subtitles").apply { mkdirs() }
                                // Keep each shared snapshot separate so a later translation cannot overwrite it.
                                val snapshot = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                                File(snapshot, "sample_ar.srt").apply { writeText(completedSrt, Charsets.UTF_8) }
                            }
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.subtitles", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/x-subrip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("sample_ar.srt", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة SRT"))
                        }
                        exporting = false
                        if (shared.isFailure) Toast.makeText(context, "تعذر مشاركة SRT",
                            Toast.LENGTH_LONG).show()
                    }
                }) { Text("مشاركة sample_ar.srt") }
                units.zip(cues).forEach { (unit, cue) ->
                    SelectionContainer {
                        Column {
                            Text("${unit.id}: ${SubtitlePipeline.timestamp(unit.startMs)} → ${SubtitlePipeline.timestamp(unit.endMs)}",
                                style = TextStyle(textDirection = TextDirection.Ltr))
                            Text(unit.sourceText, style = TextStyle(textDirection = TextDirection.Ltr))
                            Text(cue.text, style = TextStyle(textDirection = TextDirection.ContentOrRtl))
                        }
                    }
                }
                SelectionContainer { Text(completedSrt, style = TextStyle(textDirection = TextDirection.Ltr)) }
            }
        }
    }
}
