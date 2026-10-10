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
import androidx.compose.runtime.DisposableEffect
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
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState
import com.clw.aivideotranslator.session.DurableTranslationUnitDisposition
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshotFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Presentation/export surface for Task17 durable translation. Provider execution is deliberately
 * absent from this composable: [TranslationSessionViewModel] owns submission/recovery and publishes
 * only accepted text plus live P0-F timing when that timing still exists in the current process.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun DurableTranslationCard(
    state: DurableTranslationUiState,
    apiKey: String,
    canTranslate: Boolean,
    onTranslate: () -> Unit,
    sourceUri: Uri,
    videoDurationMs: Long,
    sampleStartMs: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSrt by remember { mutableStateOf<String?>(null) }
    var savingSrt by remember { mutableStateOf(false) }
    var renderingVideo by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf<Int?>(null) }
    var activeExportSession by remember { mutableStateOf<SnapshotBurnedExportSession?>(null) }
    var burnedVideo by remember { mutableStateOf<BurnedVideoResult?>(null) }
    var pendingVideoSave by remember { mutableStateOf<File?>(null) }
    var savingVideo by remember { mutableStateOf(false) }
    var rasterSnapshotResult by remember(sourceUri, state.sessionId) {
        mutableStateOf<Result<LivePresentationRasterSnapshot>?>(null)
    }

    val livePresentation = remember(
        state.phase,
        state.sessionId,
        state.liveUnits,
        state.entries,
        sampleStartMs,
        videoDurationMs,
    ) {
        if (state.phase != DurableTranslationPhase.LIVE_SUCCESS) return@remember null
        runCatching {
            val units = requireNotNull(state.liveUnits)
            val sampleCues = SubtitlePipeline.cues(units, state.entries)
            val cues = SubtitlePipeline.toPresentationTimeline(
                sampleCues = sampleCues,
                sampleStartMs = sampleStartMs,
                videoDurationMs = videoDurationMs,
            )
            LiveTranslationPresentation(
                units = units,
                cues = cues,
                srt = SubtitlePipeline.srt(cues, videoDurationMs),
            )
        }
    }
    val liveReady = livePresentation?.getOrNull()

    LaunchedEffect(sourceUri, state.sessionId, liveReady) {
        rasterSnapshotResult = null
        if (liveReady != null) {
            rasterSnapshotResult = withContext(Dispatchers.IO) {
                LivePresentationRasterSnapshotFactory.build(
                    context = context.applicationContext,
                    sourceUri = sourceUri,
                    cues = liveReady.cues,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeExportSession?.cancel()
        }
    }

    val saveSrt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        val snapshot = pendingSrt
        pendingSrt = null
        if (uri != null && snapshot != null) {
            scope.launch {
                savingSrt = true
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("تعذر فتح ملف الحفظ")
                        output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                    }
                }
                savingSrt = false
                Toast.makeText(
                    context,
                    if (saved.isSuccess) "تم حفظ ملف الترجمة SRT" else "تعذر حفظ SRT؛ أعد المحاولة",
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
                    if (saved.isSuccess) "تم حفظ الفيديو المترجم بصيغة MP4" else
                        "تعذر حفظ الفيديو؛ ملف الرندر ما زال موجودًا داخل التطبيق",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    LaunchedEffect(activeExportSession, renderingVideo) {
        while (isActive && renderingVideo) {
            activeExportSession?.let { renderProgress = it.progress() }
            delay(500)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الترجمة العربية")
            Text("هذه النسخة التجريبية تعالج أول 60 ثانية فقط. توسيع المعالجة إلى الفيديو الكامل هو الخطوة التالية في جولة الاختبار.")

            when (state.phase) {
                DurableTranslationPhase.IDLE -> Text(
                    "الترجمة جاهزة للبدء بعد نجاح تفريغ الصوت."
                )
                DurableTranslationPhase.RUNNING -> Text(
                    "جارٍ ترجمة أول 60 ثانية. بعد اكتمال الترجمة وتجهيز المعاينة سيظهر زر إنشاء MP4 هنا تلقائيًا."
                )
                DurableTranslationPhase.LIVE_SUCCESS -> Text(
                    "✓ اكتملت الترجمة. جارٍ تجهيز المعاينة وخيارات التصدير."
                )
                DurableTranslationPhase.RECOVERED_TEXT_ONLY -> Text(
                    "✓ تم استرداد نص الترجمة، لكن توقيت الكلمات الحي غير متاح بعد إعادة فتح التطبيق؛ لذلك المعاينة وMP4 غير متاحين لهذه الجلسة المستردة."
                )
                DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME -> Text(
                    "حالة طلب الترجمة السابق غير مؤكدة. لن يعيد التطبيق إرسال الطلب تلقائيًا لتجنب التكرار."
                )
                DurableTranslationPhase.BLOCKED -> Text(translationBlockerMessage(state.blocker))
                DurableTranslationPhase.FAILED -> Text(
                    when (state.failure) {
                        com.clw.aivideotranslator.session.DurableTranslationFailure.NO_LIVE_STT ->
                            "يلزم نجاح تفريغ الصوت في الجلسة الحالية قبل الترجمة والمعاينة والتصدير."
                        com.clw.aivideotranslator.session.DurableTranslationFailure.RESTORE ->
                            "تعذر استرداد حالة الترجمة بأمان."
                        else -> "تعذر إكمال الترجمة. راجع الاتصال والمفتاح ثم أعد المحاولة."
                    }
                )
            }

            val mayStart = canTranslate && apiKey.isNotBlank() &&
                state.phase != DurableTranslationPhase.RUNNING &&
                state.phase != DurableTranslationPhase.LIVE_SUCCESS &&
                state.phase != DurableTranslationPhase.RECOVERED_TEXT_ONLY &&
                state.phase != DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME &&
                state.phase != DurableTranslationPhase.BLOCKED
            Button(
                enabled = mayStart,
                onClick = onTranslate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.phase == DurableTranslationPhase.RUNNING) "جارٍ الترجمة…" else "ترجمة أول 60 ثانية إلى العربية")
            }

            if (state.entries.isNotEmpty() && state.phase != DurableTranslationPhase.LIVE_SUCCESS) {
                Text("الترجمة المحفوظة:")
                state.entries.forEach { entry ->
                    SelectionContainer {
                        Text(entry.translatedText, style = TextStyle(textDirection = TextDirection.ContentOrRtl))
                    }
                }
            }

            when (val presentation = livePresentation) {
                null -> if (state.phase == DurableTranslationPhase.LIVE_SUCCESS) {
                    Text("تعذر تجهيز المعاينة من توقيت الجلسة الحالية؛ لذلك لن يظهر تصدير MP4 حتى تُحل مشكلة التوقيت.")
                }
                else -> presentation.fold(
                    onSuccess = { ready ->
                        val sampleEndMs = minOf(videoDurationMs, sampleStartMs + SubtitlePipeline.SAMPLE_END_MS)
                        Text("المعاينة والتصدير — أول 60 ثانية")

                        val snapshotResult = rasterSnapshotResult
                        when {
                            snapshotResult == null -> Text("جارٍ تجهيز المعاينة قبل إتاحة تصدير MP4…")
                            snapshotResult.isFailure -> Text(
                                "تعذر تجهيز المعاينة المرئية؛ SRT متاح لكن MP4 متوقف: " +
                                    (snapshotResult.exceptionOrNull()?.message ?: "خطأ في الرسم")
                            )
                            else -> {
                                val snapshot = snapshotResult.getOrThrow()
                                RasterVideoSubtitlePreview(
                                    sourceUri = sourceUri,
                                    snapshot = snapshot,
                                    sampleStartMs = sampleStartMs,
                                    sampleEndMs = sampleEndMs,
                                )

                                Text("✓ المعاينة جاهزة. يمكنك الآن إنشاء فيديو MP4 مترجم لأول 60 ثانية.")
                                Button(
                                    enabled = !renderingVideo && !savingVideo,
                                    onClick = {
                                        renderingVideo = true
                                        renderProgress = null
                                        burnedVideo = null
                                        runCatching {
                                            SnapshotBurnedSubtitleExporter.start(
                                                context = context,
                                                sourceUri = sourceUri,
                                                snapshot = snapshot,
                                                sampleStartMs = sampleStartMs,
                                                sampleEndMs = sampleEndMs,
                                                onCompleted = { resultVideo ->
                                                    burnedVideo = resultVideo
                                                    renderingVideo = false
                                                    activeExportSession = null
                                                    renderProgress = 100
                                                },
                                                onError = { message ->
                                                    renderingVideo = false
                                                    activeExportSession = null
                                                    Toast.makeText(context, "فشل إنشاء MP4: $message", Toast.LENGTH_LONG).show()
                                                },
                                            )
                                        }.onSuccess { activeExportSession = it }
                                            .onFailure {
                                                renderingVideo = false
                                                activeExportSession = null
                                                Toast.makeText(
                                                    context,
                                                    "فشل بدء إنشاء MP4: ${it.message ?: "خطأ غير معروف"}",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("إنشاء فيديو MP4 مترجم — أول 60 ثانية") }
                            }
                        }

                        if (renderingVideo) {
                            Text(renderProgress?.let { "تقدم إنشاء الفيديو: $it%" } ?: "جارٍ تجهيز محرك الفيديو…")
                            Button(onClick = {
                                activeExportSession?.cancel()
                                activeExportSession = null
                                renderingVideo = false
                                renderProgress = null
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

                        Button(enabled = !savingSrt && pendingSrt == null, onClick = {
                            pendingSrt = ready.srt
                            saveSrt.launch("sample_ar_video_timeline.srt")
                        }) { Text("حفظ SRT فقط") }

                        Button(enabled = !savingSrt, onClick = {
                            scope.launch {
                                savingSrt = true
                                val shared = runCatching {
                                    val file = withContext(Dispatchers.IO) {
                                        val directory = File(context.cacheDir, "p0_subtitles").apply { mkdirs() }
                                        val snapshot = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                                        File(snapshot, "sample_ar_video_timeline.srt").apply {
                                            writeText(ready.srt, Charsets.UTF_8)
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
                                savingSrt = false
                                if (shared.isFailure) {
                                    Toast.makeText(context, "تعذر مشاركة SRT", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Text("مشاركة SRT فقط") }

                        ready.units.zip(ready.cues).forEachIndexed { index, (unit, _) ->
                            val entry = state.entries.first { it.sourceUnitId == unit.id }
                            SelectionContainer {
                                Column {
                                    Text("المقطع ${index + 1}")
                                    Text(unit.sourceText, style = TextStyle(textDirection = TextDirection.Ltr))
                                    Text(entry.translatedText, style = TextStyle(textDirection = TextDirection.ContentOrRtl))
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        Text("تعذر بناء المعاينة والتصدير بأمان: ${error.message ?: "خطأ في التوقيت"}")
                    },
                )
            }
        }
    }
}

private data class LiveTranslationPresentation(
    val units: List<SourceUnit>,
    val cues: List<ArabicSubtitleCue>,
    val srt: String,
)

private fun translationBlockerMessage(blocker: DurableTranslationUnitDisposition?): String = when (blocker) {
    DurableTranslationUnitDisposition.REVIEW_REQUIRED ->
        "تحتاج إحدى الترجمات مراجعة قبل اعتمادها؛ لن يكمل التطبيق بقية الوحدات تلقائيًا."
    DurableTranslationUnitDisposition.REJECTED ->
        "رُفضت إحدى نتائج الترجمة بواسطة التحقق؛ لم تُعتمد ولن تستمر الدفعة تلقائيًا."
    DurableTranslationUnitDisposition.PENDING ->
        "إحدى نتائج المزود ما زالت معلقة؛ لن تعتبر نجاحًا ولن تُرسل الوحدات اللاحقة تلقائيًا."
    DurableTranslationUnitDisposition.TERMINAL ->
        "أعاد المزود نتيجة نهائية غير قابلة للاعتماد لهذه الوحدة؛ توقفت الدفعة بأمان."
    DurableTranslationUnitDisposition.STALE_STATE ->
        "تغيرت حالة الجلسة أثناء الترجمة؛ لم تُعتمد النتيجة القديمة."
    DurableTranslationUnitDisposition.AMBIGUOUS_RECEIPTS ->
        "وجد التطبيق أكثر من سجل محاولة صالح لنفس الوحدة؛ أوقف الاعتماد بدل التخمين."
    DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME ->
        "نتيجة بعيدة غير مؤكدة؛ لن يعاد الإرسال تلقائيًا."
    DurableTranslationUnitDisposition.REUSED_ENTRY,
    DurableTranslationUnitDisposition.ADOPTED,
    null,
    -> "توقفت الترجمة قبل اكتمال جميع الوحدات."
}
