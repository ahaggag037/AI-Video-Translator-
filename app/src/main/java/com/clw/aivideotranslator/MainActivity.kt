package com.clw.aivideotranslator

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.session.DurableSourceFailure
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState
import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableSttUiState
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.TranslationSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AiVideoTranslatorApplication
        val sessionViewModel = ViewModelProvider(
            this,
            TranslationSessionViewModel.Factory(
                context = this,
                activeSessionOwner = app.activeTranslationSession,
                store = app.translationSessions,
                planStore = app.translationRequestPlans,
            ),
        )[TranslationSessionViewModel::class.java]
        setContent { App(sessionViewModel) }
    }
}

private sealed interface SampleState {
    data object Idle : SampleState
    data object Extracting : SampleState
    data class Ready(val sample: AudioSampleResult) : SampleState
    data class Error(val message: String) : SampleState
}

private sealed interface HomeState {
    data object Empty : HomeState
    data object Loading : HomeState
    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val sampleState: SampleState = SampleState.Idle,
    ) : HomeState
    data class Error(val message: String) : HomeState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(sessionViewModel: TranslationSessionViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durableSource by sessionViewModel.durableSource.collectAsState()
    val durableStt by sessionViewModel.stt.collectAsState()
    val durableTranslation by sessionViewModel.translation.collectAsState()
    var state: HomeState by remember { mutableStateOf(HomeState.Empty) }
    var player: MediaPlayer? by remember { mutableStateOf(null) }
    var nvidiaApiKey by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    LaunchedEffect(durableSource.phase, durableSource.sessionId, durableSource.contentUri) {
        if (durableSource.phase != DurableSourcePhase.BOUND) return@LaunchedEffect
        val storedUri = durableSource.contentUri ?: return@LaunchedEffect
        val uri = runCatching { Uri.parse(storedUri) }.getOrNull() ?: return@LaunchedEffect
        val current = state
        if (current is HomeState.Ready && current.uri == uri) return@LaunchedEffect
        state = HomeState.Loading
        val metadata = withContext(Dispatchers.IO) { VideoProbe.read(context, uri) }
        val latest = sessionViewModel.durableSource.value
        if (latest.phase != DurableSourcePhase.BOUND || latest.contentUri != storedUri) return@LaunchedEffect
        state = metadata.fold(
            onSuccess = { HomeState.Ready(uri, it) },
            onFailure = { HomeState.Error("تعذر إعادة فتح الفيديو المحفوظ. اختر المصدر مرة أخرى.") },
        )
    }

    fun playSample(sample: AudioSampleResult) {
        runCatching {
            player?.release()
            val newPlayer = MediaPlayer().apply {
                setDataSource(sample.file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { completed ->
                    completed.release()
                    if (player === completed) player = null
                }
                setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (player === failed) player = null
                    Toast.makeText(context, "تعذر تشغيل عينة الصوت", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            player = newPlayer
        }.onFailure {
            Toast.makeText(context, "تعذر تشغيل عينة الصوت", Toast.LENGTH_SHORT).show()
        }
    }

    fun extractSample(ready: HomeState.Ready) {
        val sourceUri = ready.uri
        state = ready.copy(sampleState = SampleState.Extracting)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AudioSampleExtractor.extractFirstMinute(context, sourceUri)
            }
            val current = state
            if (current !is HomeState.Ready || current.uri != sourceUri) return@launch
            state = current.copy(
                sampleState = result.fold(
                    onSuccess = { SampleState.Ready(it) },
                    onFailure = { SampleState.Error(it.message ?: "تعذر استخراج عينة الصوت") },
                )
            )
        }
    }

    fun runNvidiaStt(ready: HomeState.Ready) {
        val apiKeySnapshot = nvidiaApiKey.trim()
        if (apiKeySnapshot.isEmpty()) {
            Toast.makeText(context, "أدخل NVIDIA API Key أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        if (durableSource.phase != DurableSourcePhase.BOUND || durableSource.contentUri != ready.uri.toString()) {
            Toast.makeText(context, "انتظر اكتمال حفظ جلسة الفيديو أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        sessionViewModel.runStt(apiKeySnapshot)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        player?.release()
        player = null
        state = HomeState.Loading
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        sessionViewModel.selectNewSource(uri.toString())
        scope.launch {
            val metadata = withContext(Dispatchers.IO) { VideoProbe.read(context, uri) }
            val latest = sessionViewModel.durableSource.value
            if (latest.contentUri != uri.toString()) return@launch
            state = metadata.fold(
                onSuccess = { HomeState.Ready(uri, it) },
                onFailure = { HomeState.Error(it.message ?: "تعذر قراءة بيانات الفيديو") },
            )
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("AI Video Translator") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "الترجمة والترجمة النصية — جلسة قابلة للاستئناف",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "اختيار الفيديو وSTT والترجمة المقبولة مرتبطة الآن بجلسة دائمة. الطلبات غير المؤكدة لا يعاد إرسالها تلقائيًا بعد إغلاق التطبيق."
                )

                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = durableSource.phase != DurableSourcePhase.CAPTURING &&
                        durableStt.phase != DurableSttPhase.RUNNING &&
                        durableTranslation.phase != DurableTranslationPhase.RUNNING &&
                        ((state as? HomeState.Ready)?.sampleState !is SampleState.Extracting),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("اختيار فيديو")
                }

                DurableSessionCard(durableSource, durableStt)

                when (val s = state) {
                    HomeState.Empty -> InfoCard("الحالة", "اختر فيديو لبدء جلسة جديدة، أو انتظر استرداد الجلسة السابقة إن وجدت.")
                    HomeState.Loading -> InfoCard("الحالة", "جارٍ فتح معلومات الفيديو وربط الجلسة…")
                    is HomeState.Error -> InfoCard("تعذر فتح الفيديو", s.message)
                    is HomeState.Ready -> {
                        VideoCard(s.metadata)
                        SampleCard(
                            state = s.sampleState,
                            onExtract = { extractSample(s) },
                            onPlay = ::playSample,
                        )
                        val sourceReady = durableSource.phase == DurableSourcePhase.BOUND &&
                            durableSource.contentUri == s.uri.toString()
                        SttCard(
                            state = durableStt,
                            apiKey = nvidiaApiKey,
                            sourceReady = sourceReady,
                            onApiKeyChange = { nvidiaApiKey = it },
                            onRun = { runNvidiaStt(s) },
                        )

                        val liveSttReady = durableStt.phase == DurableSttPhase.LIVE_SUCCESS &&
                            durableStt.legacyResult != null && sourceReady
                        if (liveSttReady || durableTranslation.phase != DurableTranslationPhase.IDLE) {
                            DurableTranslationCard(
                                state = durableTranslation,
                                apiKey = nvidiaApiKey,
                                canTranslate = liveSttReady,
                                onTranslate = { sessionViewModel.runTranslation(nvidiaApiKey.trim()) },
                                sourceUri = s.uri,
                                videoDurationMs = s.metadata.durationMs ?: SubtitlePipeline.SAMPLE_END_MS,
                                sampleStartMs = 0L,
                            )
                        }

                        if (durableStt.phase == DurableSttPhase.RECOVERED) {
                            InfoCard(
                                "تم استرداد STT",
                                "تم استرداد النص المقبول دون طلب جديد. إذا وُجدت ترجمات محفوظة فستظهر كنص فقط؛ لن نعيد إنشاء توقيت كلمات قديم من التخمين قبل إغلاق X001.",
                            )
                        }
                    }
                }

                InfoCard(
                    "الخصوصية والاسترداد",
                    "مفتاح NVIDIA يبقى في ذاكرة الشاشة فقط ولا يُكتب إلى ملفات الجلسة. تحفظ الجلسة هويات المصدر ونتائج STT وخطط/نتائج الترجمة اللازمة للاسترداد، لا الاستجابات الخام من المزود."
                )
            }
        }
    }
}

@Composable
private fun DurableSessionCard(source: DurableSourceUiState, stt: DurableSttUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("حالة الجلسة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                when (source.phase) {
                    DurableSourcePhase.NONE -> "لا توجد جلسة فيديو فعالة."
                    DurableSourcePhase.CAPTURING -> "جارٍ تثبيت هوية الفيديو وحفظ الجلسة…"
                    DurableSourcePhase.BOUND -> "✓ الفيديو مرتبط بجلسة دائمة ويمكن استئنافها."
                    DurableSourcePhase.FAILED -> sourceFailureMessage(source.failure)
                }
            )
            when (stt.phase) {
                DurableSttPhase.UNKNOWN_REMOTE_OUTCOME -> Text(
                    "حالة STT غير مؤكدة بعد الإرسال. لن يعاد الطلب تلقائيًا لتجنب تكرار التنفيذ أو التكلفة."
                )
                DurableSttPhase.RECOVERED -> Text("✓ تم استرداد نص STT المقبول محليًا دون اتصال جديد.")
                DurableSttPhase.LIVE_SUCCESS -> Text("✓ تم حفظ نتيجة STT وربطها بالجلسة.")
                else -> Unit
            }
        }
    }
}

private fun sourceFailureMessage(failure: DurableSourceFailure?): String = when (failure) {
    DurableSourceFailure.CAPTURE -> "تعذر قراءة الفيديو المختار وحفظ هويته. اختر الملف مرة أخرى."
    DurableSourceFailure.PERSISTENCE -> "تعذر حفظ جلسة الفيديو بأمان."
    DurableSourceFailure.ACTIVATION -> "تعذر جعل الجلسة الجديدة هي الجلسة الفعالة."
    DurableSourceFailure.RESTORE -> "تعذر استرداد الجلسة المحفوظة."
    DurableSourceFailure.CHECK_REQUIRED -> "يجب إعادة التحقق من المصدر قبل المتابعة."
    DurableSourceFailure.PERMISSION_MISSING -> "فقد التطبيق إذن قراءة الفيديو. اختر المصدر مرة أخرى."
    DurableSourceFailure.SOURCE_MISSING -> "ملف الفيديو المحفوظ لم يعد موجودًا. اختر موقعه من جديد."
    DurableSourceFailure.SOURCE_CHANGED -> "محتوى الفيديو تغيّر منذ حفظ الجلسة. اختر المصدر الصحيح من جديد."
    DurableSourceFailure.IO_FAILURE -> "تعذر قراءة الفيديو حاليًا."
    DurableSourceFailure.UNSUPPORTED -> "مصدر الفيديو الحالي غير مدعوم للاسترداد."
    DurableSourceFailure.CORRUPT -> "بيانات الجلسة المرتبطة بالمصدر تالفة؛ لن يستخدمها التطبيق بصمت."
    DurableSourceFailure.STALE -> "حالة المصدر تغيّرت أثناء الاسترداد؛ أعد المحاولة."
    null -> "تعذر استخدام المصدر المحفوظ."
}

@Composable
private fun VideoCard(metadata: VideoMetadata) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الفيديو المختار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            KeyValue("الاسم", metadata.displayName)
            KeyValue("المدة", metadata.durationLabel)
            KeyValue("الدقة", metadata.resolutionLabel)
            KeyValue("الحجم", metadata.sizeLabel)
            Spacer(Modifier.height(4.dp))
            Text("✓ يتحقق التطبيق من الوصول الحالي وهوية المحتوى عند الاستئناف.")
        }
    }
}

@Composable
private fun SampleCard(
    state: SampleState,
    onExtract: () -> Unit,
    onPlay: (AudioSampleResult) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("عينة الصوت المحلية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (state) {
                SampleState.Idle -> {
                    Text("يمكن استخراج أول دقيقة محليًا للاستماع والتحقق من الصوت دون شبكة.")
                    Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                        Text("استخراج عينة 60 ثانية")
                    }
                }
                SampleState.Extracting -> {
                    Text("جارٍ نسخ عينات الصوت محليًا…")
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("جارٍ الاستخراج…")
                    }
                }
                is SampleState.Error -> {
                    Text("تعذر استخراج العينة: ${state.message}")
                    Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                        Text("إعادة المحاولة")
                    }
                }
                is SampleState.Ready -> {
                    val sample = state.sample
                    Text("✓ تم إنشاء عينة صوت محلية بنجاح.")
                    KeyValue("النوع", sample.mimeType)
                    KeyValue("المدة", sample.measuredDurationLabel)
                    KeyValue("الحجم", sample.sizeLabel)
                    Button(onClick = { onPlay(sample) }, modifier = Modifier.fillMaxWidth()) {
                        Text("تشغيل عينة الصوت")
                    }
                    Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                        Text("إعادة استخراج العينة")
                    }
                }
            }
        }
    }
}

@Composable
private fun SttCard(
    state: DurableSttUiState,
    apiKey: String,
    sourceReady: Boolean,
    onApiKeyChange: (String) -> Unit,
    onRun: () -> Unit,
) {
    val busy = state.phase == DurableSttPhase.RUNNING
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NVIDIA STT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(NvidiaSttClient.MODEL_LABEL)
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("NVIDIA API Key") },
                visualTransformation = PasswordVisualTransformation(),
            )

            when (state.phase) {
                DurableSttPhase.IDLE -> Text("سيُحفظ intent قبل الإرسال؛ نجاح STT لن يعاد تلقائيًا بعد restart.")
                DurableSttPhase.RUNNING -> Text("جارٍ تجهيز الصوت وإرسال طلب STT واحد…")
                DurableSttPhase.LIVE_SUCCESS -> {
                    val result = state.legacyResult!!
                    Text("✓ نجح STT وحُفظت النتيجة في الجلسة.")
                    KeyValue("الكلمات", result.words.size.toString())
                    result.firstWordStartMs?.let { KeyValue("أول توقيت", "${it}ms") }
                    result.lastWordEndMs?.let { KeyValue("آخر توقيت", "${it}ms") }
                    Text("النص:", fontWeight = FontWeight.SemiBold)
                    Text(result.transcript)
                }
                DurableSttPhase.RECOVERED -> {
                    Text("✓ النص مستعاد من الجلسة دون إعادة طلب STT.")
                    state.transcript?.let { Text(it) }
                }
                DurableSttPhase.UNKNOWN_REMOTE_OUTCOME -> Text(
                    "لا يمكن تأكيد نتيجة الطلب السابق. لن يعيد التطبيق الإرسال تلقائيًا."
                )
                DurableSttPhase.FAILED -> Text(
                    when (state.failure) {
                        com.clw.aivideotranslator.session.DurableSttFailure.NO_BOUND_SOURCE -> "اربط فيديو صالحًا بالجلسة أولًا."
                        com.clw.aivideotranslator.session.DurableSttFailure.RESTORE -> "تعذر استرداد نتيجة STT المحفوظة بأمان."
                        else -> "تعذر إكمال STT. لم تُعتبر النتيجة نجاحًا محفوظًا."
                    }
                )
            }

            val canSubmit = sourceReady && state.phase == DurableSttPhase.IDLE && apiKey.isNotBlank()
            Button(
                onClick = onRun,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "جارٍ STT…" else "تشغيل NVIDIA STT")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(key, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(86.dp))
        Text(value)
    }
}
