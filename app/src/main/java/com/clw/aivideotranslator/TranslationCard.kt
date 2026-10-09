package com.clw.aivideotranslator

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Transformer
import com.clw.aivideotranslator.session.DurableTranslationFailure
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class DurableTranslationPreview(
    val units: List<SourceUnit>,
    val cues: List<ArabicSubtitleCue>,
    val srt: String,
)

@OptIn(UnstableApi::class)
@Composable
internal fun TranslationCard(
    result: NvidiaSttResult,
    translationState: DurableTranslationUiState,
    canTranslate: Boolean,
    onTranslate: () -> Unit,
    sourceUri: Uri,
    videoDurationMs: Long,
    sampleStartMs: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }

    var renderingVideo by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf<Int?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    var burnedVideo by remember { mutableStateOf<BurnedVideoResult?>(null) }
    var pendingVideoSave by remember { mutableStateOf<File?>(null) }
    var savingVideo by remember { mutableStateOf(false) }
    var renderStatus by remember { mutableStateOf<String?>(null) }

    val previewResult = remember(result, translationState, sampleStartMs, videoDurationMs) {
        if (translationState.phase != DurableTranslationPhase.COMPLETE) {
            null
        } else {
            runCatching {
                val units = SubtitlePipeline.sourceUnits(result.words)
                val entries = translationState.texts.map { text ->
                    TranslationEntry(text.unitId, text.text)
                }
                val sampleCues = SubtitlePipeline.cues(units, entries)
                val presentationCues = SubtitlePipeline.toPresentationTimeline(
                    sampleCues = sampleCues,
                    sampleStartMs = sampleStartMs,
                    videoDurationMs = videoDurationMs,
                )
                DurableTranslationPreview(
                    units = units,
                    cues = presentationCues,
                    srt = SubtitlePipeline.srt(presentationCues, videoDurationMs),
                )
            }
        }
    }
    val preview = previewResult?.getOrNull()

    LaunchedEffect(translationState.texts) {
        if (translationState.phase != DurableTranslationPhase.COMPLETE) {
            burnedVideo = null
            renderStatus = null
        }
    }

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
                Toast.makeText(
                    context,
                    if (saved.isSuccess) "تم حفظ sample_ar_video_timeline.srt" else
                        "تعذر حفظ SRT؛ أعد المحاولة",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val saveVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val snapshot = pendingVideoSave
        pendingVideoSave = null
        if (uri != null && snapshot != null) {
            scope.launch {
                savingVideo = true
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("تعذر فتح ملف حفظ الفيديو")
                        output.use { target ->
                            snapshot.inputStream().use { source -> source.copyTo(target) }
                        }
                    }
                }
                savingVideo = false
                Toast.makeText(
                    context,
                    if (saved.isSuccess) "تم حفظ ${BurnedSubtitleExporter.DEFAULT_FILE_NAME} كفيديو MP4" else
                        "تعذر حفظ الفيديو؛ ملف الرندر ما زال موجودًا داخل التطبيق",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    LaunchedEffect(activeTransformer, renderingVideo) {
        while (isActive && renderingVideo) {
            activeTransformer?.let { renderProgress = BurnedSubtitleExporter.progress(it) }
            delay(500)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الترجمة العربية — جلسة دائمة")
            Text(NvidiaTranslationClient.MODEL_ID)
            Text(translationStatus(translationState, previewResult?.exceptionOrNull()))
            renderStatus?.let { Text(it) }

            val canStartTranslation = canTranslate &&
                (translationState.phase == DurableTranslationPhase.IDLE ||
                    translationState.phase == DurableTranslationPhase.FAILED) &&
                !exporting && !renderingVideo && pendingExport == null
            Button(
                enabled = canStartTranslation,
                onClick = {
                    burnedVideo = null
                    renderStatus = null
                    onTranslate()
                },
            ) {
                Text(
                    if (translationState.phase == DurableTranslationPhase.FAILED) {
                        "إعادة محاولة الترجمة"
                    } else {
                        "ترجمة العينة إلى العربية"
                    }
                )
            }

            if (translationState.phase == DurableTranslationPhase.RUNNING) {
                Text("يُحفظ كل طلب قبل الإرسال؛ لا تغلق الجلسة أثناء التنفيذ إلا إذا أردت الاسترداد لاحقًا.")
            }

            if (preview != null) {
                val completedSrt = preview.srt
                val cues = preview.cues
                val units = preview.units
                val sampleEndMs = minOf(videoDurationMs, sampleStartMs + SubtitlePipeline.SAMPLE_END_MS)
                Text("✓ النصوص الدائمة طابقت وحدات P0-F وأزمنة SRT الحالية على خط الفيديو الأصلي.")
                VideoSubtitlePreview(
                    sourceUri = sourceUri,
                    cues = cues,
                    sampleStartMs = sampleStartMs,
                    sampleEndMs = sampleEndMs,
                )

                Text("ملف SRT ترجمة نصية فقط. إنشاء MP4 أدناه يعيد استخدام النصوص المحفوظة ولا يرسل طلب AI جديدًا.")
                Button(
                    enabled = !renderingVideo && !savingVideo && cues.isNotEmpty(),
                    onClick = {
                        renderingVideo = true
                        renderProgress = null
                        burnedVideo = null
                        renderStatus = "جارٍ إنشاء MP4 مترجم لأول 60 ثانية…"
                        runCatching {
                            BurnedSubtitleExporter.start(
                                context = context,
                                sourceUri = sourceUri,
                                cues = cues,
                                sampleStartMs = sampleStartMs,
                                sampleEndMs = sampleEndMs,
                                onCompleted = { resultVideo ->
                                    burnedVideo = resultVideo
                                    renderingVideo = false
                                    activeTransformer = null
                                    renderProgress = 100
                                    renderStatus = "✓ تم إنشاء فيديو MP4 مترجم والتحقق من وجود مسار فيديو صالح."
                                },
                                onError = { message ->
                                    renderingVideo = false
                                    activeTransformer = null
                                    renderStatus = "فشل إنشاء MP4: $message"
                                },
                            )
                        }.onSuccess { activeTransformer = it }
                            .onFailure {
                                renderingVideo = false
                                activeTransformer = null
                                renderStatus = "فشل بدء إنشاء MP4: ${it.message ?: "خطأ غير معروف"}"
                            }
                    },
                ) { Text("إنشاء فيديو MP4 مترجم — أول 60 ثانية") }

                if (renderingVideo) {
                    Text(renderProgress?.let { "تقدم إنشاء الفيديو: $it%" } ?: "جارٍ تجهيز محرك الفيديو…")
                    Button(onClick = {
                        activeTransformer?.cancel()
                        activeTransformer = null
                        renderingVideo = false
                        renderProgress = null
                        renderStatus = "أُلغي إنشاء الفيديو. نصوص الترجمة ما زالت محفوظة في الجلسة."
                    }) { Text("إلغاء إنشاء الفيديو") }
                }

                burnedVideo?.let { rendered ->
                    Text(
                        "✓ MP4 جاهز: ${rendered.durationMs / 1000}s — " +
                            String.format(java.util.Locale.ROOT, "%.1f MB", rendered.sizeBytes / 1_048_576.0)
                    )
                    Button(onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.subtitles",
                            rendered.file,
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "لا يوجد مشغل فيديو متاح", Toast.LENGTH_LONG).show()
                            }
                    }) { Text("تشغيل الفيديو MP4 المترجم") }

                    Button(enabled = !savingVideo && pendingVideoSave == null, onClick = {
                        pendingVideoSave = rendered.file
                        saveVideo.launch(BurnedSubtitleExporter.DEFAULT_FILE_NAME)
                    }) { Text("حفظ الفيديو MP4") }

                    Button(enabled = !savingVideo, onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.subtitles",
                            rendered.file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(BurnedSubtitleExporter.DEFAULT_FILE_NAME, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "مشاركة الفيديو MP4"))
                        }.onFailure {
                            Toast.makeText(context, "تعذر مشاركة الفيديو", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("مشاركة الفيديو MP4") }
                }

                Button(enabled = !exporting && pendingExport == null, onClick = {
                    pendingExport = completedSrt
                    save.launch("sample_ar_video_timeline.srt")
                }) { Text("حفظ SRT فقط") }

                Button(enabled = !exporting, onClick = {
                    scope.launch {
                        exporting = true
                        val shared = runCatching {
                            val file = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "p0_subtitles").apply { mkdirs() }
                                val snapshot = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                                File(snapshot, "sample_ar_video_timeline.srt").apply {
                                    writeText(completedSrt, Charsets.UTF_8)
                                }
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.subtitles",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/x-subrip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("sample_ar_video_timeline.srt", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة SRT"))
                        }
                        exporting = false
                        if (shared.isFailure) {
                            Toast.makeText(context, "تعذر مشاركة SRT", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("مشاركة SRT فقط") }

                units.zip(cues).forEach { (unit, cue) ->
                    SelectionContainer {
                        Column {
                            Text(
                                "${unit.id}: ${SubtitlePipeline.timestamp(cue.startMs)} → ${SubtitlePipeline.timestamp(cue.endMs)}",
                                style = TextStyle(textDirection = TextDirection.Ltr),
                            )
                            Text(unit.sourceText, style = TextStyle(textDirection = TextDirection.Ltr))
                            Text(cue.text, style = TextStyle(textDirection = TextDirection.ContentOrRtl))
                        }
                    }
                }
                SelectionContainer {
                    Text(completedSrt, style = TextStyle(textDirection = TextDirection.Ltr))
                }
            }
        }
    }
}

private fun translationStatus(
    state: DurableTranslationUiState,
    previewError: Throwable?,
): String = when (state.phase) {
    DurableTranslationPhase.IDLE ->
        "الترجمة تستخدم نفس NVIDIA/P0-F الحالي، لكن الطلب والنتيجة المقبولة سيُحفظان في الجلسة."
    DurableTranslationPhase.RUNNING -> "جارٍ تنفيذ الترجمة الدائمة بالتتابع…"
    DurableTranslationPhase.COMPLETE -> if (previewError == null) {
        "✓ اكتملت الترجمة وحُفظت النصوص المقبولة. إعادة التصدير لا تعيد طلب AI."
    } else {
        "النصوص محفوظة، لكن تعذر مطابقتها بأمان مع توقيت المعاينة الحالي؛ لن يتم إنشاء SRT أو MP4."
    }
    DurableTranslationPhase.REVIEW_REQUIRED ->
        "توقفت الترجمة عند ${state.blockingUnitId}: النتيجة تحتاج مراجعة قبل الاعتماد."
    DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME ->
        "توقفت الترجمة عند ${state.blockingUnitId}: نتيجة الإرسال غير مؤكدة ولن يُعاد الطلب تلقائيًا."
    DurableTranslationPhase.STALE ->
        "توقفت الترجمة عند ${state.blockingUnitId}: الحالة الدائمة تغيّرت ولن تُستخدم نتيجة قديمة بصمت."
    DurableTranslationPhase.FAILED -> when (state.failure) {
        DurableTranslationFailure.NO_LIVE_STT -> "يلزم STT حي موثوق بالتوقيت الحالي قبل إنشاء ترجمة مرتبطة بالمعاينة."
        DurableTranslationFailure.NO_BOUND_SOURCE -> "اربط فيديو صالحًا بالجلسة أولًا."
        DurableTranslationFailure.RESTORE -> "تعذر استرداد حالة الترجمة الدائمة بأمان."
        DurableTranslationFailure.PROVIDER_REJECTED_OR_PENDING -> "لم يعتمد المزود نتيجة قابلة للاستخدام؛ لم يُنشأ SRT."
        DurableTranslationFailure.PROVIDER_OR_STORAGE, null -> "تعذر إكمال الترجمة أو حفظها بأمان."
    }
}
