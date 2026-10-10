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
                    if (saved.isSuccess) "تم حفظ sample_ar_video_timeline.srt" else "تعذر حفظ SRT؛ أعد المحاولة",
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

    LaunchedEffect(activeExportSession, renderingVideo) {
        while (isActive && renderingVideo) {
            activeExportSession?.let { renderProgress = it.progress() }
            delay(500)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الترجمة العربية — جلسة دائمة")
            Text(NvidiaTranslationClient.MODEL_ID)

            when (state.phase) {
                DurableTranslationPhase.IDLE -> Text(
                    "لم تبدأ الترجمة بعد. كل وحدة ستُحفظ قبل الإرسال ولن يعاد POST تلقائيًا إذا أصبحت النتيجة البعيدة غير مؤكدة."
                )
                DurableTranslationPhase.RUNNING -> Text("جارٍ ترجمة وحدات P0-F بالتتابع وحفظ تقدم كل طلب…")
                DurableTranslationPhase.LIVE_SUCCESS -> Text(
                    "✓ اكتملت الترجمة وحُفظت النتائج المقبولة. المعاينة والتصدير يستخدمان توقيت P0-F الحي ونفس raster snapshot."
                )
                DurableTranslationPhase.RECOVERED_TEXT_ONLY -> Text(
                    "✓ تم استرداد نصوص الترجمة المحفوظة دون طلبات جديدة. توقيت الكلمات الحي غير محفوظ قبل X001، لذلك لن ينشئ التطبيق معاينة أو SRT أو MP4 من توقيت مخمّن بعد restart."
                )
                DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME -> Text(
                    "نتيجة إحدى وحدات الترجمة غير مؤكدة بعد SENT. لن يعيد التطبيق إرسالها تلقائيًا لتجنب تكرار التنفيذ أو التكلفة."
                )
                DurableTranslationPhase.BLOCKED -> Text(translationBlockerMessage(state.blocker))
                DurableTranslationPhase.FAILED -> Text(
                    when (state.failure) {
                        com.clw.aivideotranslator.session.DurableTranslationFailure.NO_LIVE_STT ->
                            "يلزم نجاح STT حي في هذه العملية حتى نملك توقيت P0-F الموثوق للمعاينة والتصدير."
                        com.clw.aivideotranslator.session.DurableTranslationFailure.RESTORE ->
                            "تعذر استرداد حالة الترجمة الدائمة بأمان. لن يستخدم التطبيق بيانات مشتبهًا بها."
                        else -> "تعذر إكمال الترجمة. راجع الاتصال/المفتاح ثم أعد المحاولة؛ السجل الدائم سيمنع إعادة POST غير الآمنة."
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
                Text(if (state.phase == DurableTranslationPhase.RUNNING) "جارٍ الترجمة…" else "ترجمة العينة إلى العربية")
            }

            if (state.entries.isNotEmpty() && state.phase != DurableTranslationPhase.LIVE_SUCCESS) {
                Text("الترجمات المحفوظة المتاحة:")
                state.entries.forEach { entry ->
                    SelectionContainer {
                        Text("${entry.sourceUnitId}: ${entry.translatedText}")
                    }
                }
            }

            when (val presentation = livePresentation) {
                null -> if (state.phase == DurableTranslationPhase.LIVE_SUCCESS) {
                    Text("تعذر بناء معاينة التوقيت من النتائج الحية؛ لن يتم التصدير.")
                }
                else -> presentation.fold(
                    onSuccess = { ready ->
                        val sampleEndMs = minOf(videoDurationMs, sampleStartMs + SubtitlePipeline.SAMPLE_END_MS)
                        Text(
                            "✓ أزمنة SRT على خط الفيديو الأصلي. بداية العينة = ${SubtitlePipeline.timestamp(sampleStartMs)}"
                        )

                        val snapshotResult = rasterSnapshotResult
                        when {
                            snapshotResult == null -> Text("جارٍ تجهيز raster snapshot المشترك للمعاينة والتصدير…")
                            snapshotResult.isFailure -> Text(
                                "تعذر تجهيز layout/raster بأمان؛ SRT متاح لكن preview وMP4 متوقفان: " +
                                    (snapshotResult.exceptionOrNull()?.message ?: "خطأ في renderer")
                            )
                            else -> {
                                val snapshot = snapshotResult.getOrThrow()
                                RasterVideoSubtitlePreview(
                                    sourceUri = sourceUri,
                                    snapshot = snapshot,
                                    sampleStartMs = sampleStartMs,
                                    sampleEndMs = sampleEndMs,
                                )

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

                        ready.units.zip(ready.cues).forEach { (unit, cue) ->
                            val entry = state.entries.first { it.sourceUnitId == unit.id }
                            SelectionContainer {
                                Column {
                                    Text(
                                        "${unit.id}: ${SubtitlePipeline.timestamp(cue.startMs)} → ${SubtitlePipeline.timestamp(cue.endMs)}",
                                        style = TextStyle(textDirection = TextDirection.Ltr),
                                    )
                                    Text(unit.sourceText, style = TextStyle(textDirection = TextDirection.Ltr))
                                    Text(entry.translatedText, style = TextStyle(textDirection = TextDirection.ContentOrRtl))
                                }
                            }
                        }
                        SelectionContainer {
                            Text(ready.srt, style = TextStyle(textDirection = TextDirection.Ltr))
                        }
                    },
                    onFailure = { error ->
                        Text("تعذر بناء preview/export بأمان: ${error.message ?: "خطأ في التوقيت"}")
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
