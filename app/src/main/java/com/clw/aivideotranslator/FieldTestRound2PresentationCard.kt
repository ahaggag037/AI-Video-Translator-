package com.clw.aivideotranslator

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.clw.aivideotranslator.session.FieldTestFullVideoSubtitlePipeline
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshotFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FieldTestRound2Presentation(
    val cues: List<ArabicSubtitleCue>,
    val srt: String,
)

/** Full-duration round-2 presentation. It never uses the frozen first-minute adapter. */
@OptIn(UnstableApi::class)
@Composable
internal fun FieldTestRound2PresentationCard(
    sourceUri: Uri,
    videoDurationMs: Long,
    units: List<SourceUnit>,
    entries: List<TranslationEntry>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presentation = remember(sourceUri, videoDurationMs, units, entries) {
        runCatching {
            require(videoDurationMs > 0L) { "مدة الفيديو غير متاحة للتصدير" }
            val cues = FieldTestFullVideoSubtitlePipeline.cues(
                units = units,
                entries = entries,
                videoDurationMs = videoDurationMs,
            )
            FieldTestRound2Presentation(
                cues = cues,
                srt = FieldTestFullVideoSubtitlePipeline.srt(cues, videoDurationMs),
            )
        }
    }
    val ready = presentation.getOrNull()

    var rasterSnapshot by remember(sourceUri, videoDurationMs, units, entries) {
        mutableStateOf<Result<LivePresentationRasterSnapshot>?>(null)
    }
    var renderingVideo by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf<Int?>(null) }
    var activeExportSession by remember { mutableStateOf<SnapshotBurnedExportSession?>(null) }
    var renderedVideo by remember { mutableStateOf<BurnedVideoResult?>(null) }
    var pendingVideoSave by remember { mutableStateOf<File?>(null) }
    var pendingSrt by remember { mutableStateOf<String?>(null) }
    var savingVideo by remember { mutableStateOf(false) }
    var savingSrt by remember { mutableStateOf(false) }

    LaunchedEffect(sourceUri, ready) {
        rasterSnapshot = null
        if (ready != null) {
            rasterSnapshot = withContext(Dispatchers.IO) {
                LivePresentationRasterSnapshotFactory.build(
                    context = context.applicationContext,
                    sourceUri = sourceUri,
                    cues = ready.cues,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { activeExportSession?.cancel() }
    }

    LaunchedEffect(activeExportSession, renderingVideo) {
        while (isActive && renderingVideo) {
            renderProgress = activeExportSession?.progress()
            delay(500L)
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
                            ?: error("تعذر فتح ملف حفظ MP4")
                        output.use { target -> snapshot.inputStream().use { source -> source.copyTo(target) } }
                    }
                }
                savingVideo = false
                Toast.makeText(
                    context,
                    if (saved.isSuccess) "تم حفظ MP4 الكامل" else "تعذر حفظ MP4",
                    Toast.LENGTH_LONG,
                ).show()
            }
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
                            ?: error("تعذر فتح ملف حفظ SRT")
                        output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                    }
                }
                savingSrt = false
                Toast.makeText(
                    context,
                    if (saved.isSuccess) "تم حفظ SRT الكامل" else "تعذر حفظ SRT",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المعاينة والتصدير — الفيديو الكامل")
            when {
                presentation.isFailure -> Text(
                    "تعذر بناء timeline الكامل: ${presentation.exceptionOrNull()?.message ?: "خطأ في التوقيت"}"
                )
                ready == null -> Text("لا توجد ترجمة كاملة جاهزة للعرض.")
                rasterSnapshot == null -> Text("جارٍ تجهيز raster موحد للمعاينة والتصدير…")
                rasterSnapshot?.isFailure == true -> Text(
                    "تعذر تجهيز raster الكامل: ${rasterSnapshot?.exceptionOrNull()?.message ?: "خطأ في الرسم"}"
                )
                else -> {
                    val snapshot = rasterSnapshot!!.getOrThrow()
                    Text("✓ نفس raster/timeline سيُستخدم في المعاينة وفي الحرق داخل MP4.")
                    RasterVideoSubtitlePreview(
                        sourceUri = sourceUri,
                        snapshot = snapshot,
                        sampleStartMs = 0L,
                        sampleEndMs = videoDurationMs,
                    )

                    Button(
                        enabled = !renderingVideo && !savingVideo,
                        onClick = {
                            renderingVideo = true
                            renderProgress = null
                            renderedVideo = null
                            runCatching {
                                SnapshotBurnedSubtitleExporter.start(
                                    context = context,
                                    sourceUri = sourceUri,
                                    snapshot = snapshot,
                                    sampleStartMs = 0L,
                                    sampleEndMs = videoDurationMs,
                                    onCompleted = { result ->
                                        renderedVideo = result
                                        renderingVideo = false
                                        renderProgress = 100
                                        activeExportSession = null
                                    },
                                    onError = { message ->
                                        renderingVideo = false
                                        activeExportSession = null
                                        Toast.makeText(context, "فشل إنشاء MP4 الكامل: $message", Toast.LENGTH_LONG).show()
                                    },
                                )
                            }.onSuccess { activeExportSession = it }
                                .onFailure { error ->
                                    renderingVideo = false
                                    activeExportSession = null
                                    Toast.makeText(
                                        context,
                                        "فشل بدء MP4 الكامل: ${error.message ?: "خطأ غير معروف"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("إنشاء MP4 مترجم — الفيديو الكامل")
                    }
                }
            }

            if (renderingVideo) {
                Text(renderProgress?.let { "تقدم التصدير: $it%" } ?: "جارٍ تجهيز محرك التصدير…")
                Button(
                    onClick = {
                        activeExportSession?.cancel()
                        activeExportSession = null
                        renderingVideo = false
                        renderProgress = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("إلغاء تصدير MP4")
                }
            }

            renderedVideo?.let { rendered ->
                Text(
                    "✓ MP4 الكامل اجتاز فحص المدة/المسارات: ${formatDuration(rendered.durationMs)} — ${formatBytes(rendered.sizeBytes)}"
                )
                Button(
                    enabled = !savingVideo && pendingVideoSave == null,
                    onClick = {
                        pendingVideoSave = rendered.file
                        saveVideo.launch("translated_full_video.mp4")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (savingVideo) "جارٍ الحفظ…" else "حفظ MP4 الكامل")
                }
            }

            if (ready != null) {
                Button(
                    enabled = !savingSrt && pendingSrt == null,
                    onClick = {
                        pendingSrt = ready.srt
                        saveSrt.launch("translated_full_video_ar.srt")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (savingSrt) "جارٍ الحفظ…" else "حفظ SRT الكامل")
                }
            }
        }
    }
}
