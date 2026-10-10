package com.clw.aivideotranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.session.FieldTestRound2Phase
import com.clw.aivideotranslator.session.FieldTestRound2UiState
import com.clw.aivideotranslator.session.FieldTestRound2ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launcher only for build/field-test-round2. Canonical MainActivity remains untouched. */
class FieldTestRound2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AiVideoTranslatorApplication
        val viewModel = ViewModelProvider(
            this,
            FieldTestRound2ViewModel.Factory(
                context = this,
                store = app.translationSessions,
                planStore = app.translationRequestPlans,
            ),
        )[FieldTestRound2ViewModel::class.java]
        setContent { FieldTestRound2App(viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldTestRound2App(viewModel: FieldTestRound2ViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var metadata by remember { mutableStateOf<VideoMetadata?>(null) }
    var metadataError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.sourceUri) {
        val source = state.sourceUri
        if (source == null) {
            metadata = null
            metadataError = null
            return@LaunchedEffect
        }
        val uri = runCatching { Uri.parse(source) }.getOrNull()
        if (uri == null) {
            metadata = null
            metadataError = "تعذر قراءة URI للفيديو."
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) { VideoProbe.read(context.applicationContext, uri) }
        metadata = result.getOrNull()
        metadataError = result.exceptionOrNull()?.message
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        metadata = null
        metadataError = null
        viewModel.selectSource(uri.toString())
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Round 2 — Full Video Field Test") }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مسار اختبار معزول", fontWeight = FontWeight.Bold)
                        Text(
                            "هذا الـlauncher يشغّل STT للفيديو الكامل بالـwindow journal ثم semantic translation، " +
                                "ولا يغيّر SourceSnapshot أو active-session pointer أو planner الخاصين بالمسار القانوني."
                        )
                    }
                }

                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("اختيار فيديو للجولة الثانية")
                }

                metadata?.let { FieldTestVideoCard(it) }
                metadataError?.let { FieldTestInfoCard("بيانات الفيديو", "تعذر قراءتها: $it") }

                FieldTestStatusCard(state)

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("NVIDIA API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                )

                val canRunStt = state.sessionId != null &&
                    state.sourceUri != null &&
                    !state.busy &&
                    apiKey.isNotBlank() &&
                    state.phase != FieldTestRound2Phase.STT_UNKNOWN_REMOTE_OUTCOME
                Button(
                    onClick = { viewModel.runFullVideoStt(apiKey.trim()) },
                    enabled = canRunStt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.phase == FieldTestRound2Phase.STT_RUNNING) "جارٍ STT…" else "تشغيل STT — الفيديو الكامل")
                }

                state.sttResult?.let { result ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("نتيجة STT الكاملة", fontWeight = FontWeight.Bold)
                            FieldTestValue("الكلمات", result.words.size.toString())
                            result.firstWordStartMs?.let { FieldTestValue("أول توقيت", "${it}ms") }
                            result.lastWordEndMs?.let { FieldTestValue("آخر توقيت", "${it}ms") }
                            Text(result.transcript)
                        }
                    }
                }

                val canTranslate = state.sttResult != null &&
                    !state.busy &&
                    apiKey.isNotBlank() &&
                    state.phase != FieldTestRound2Phase.TRANSLATED &&
                    state.phase != FieldTestRound2Phase.TRANSLATION_BLOCKED
                Button(
                    onClick = { viewModel.runSemanticTranslation(apiKey.trim()) },
                    enabled = canTranslate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.phase == FieldTestRound2Phase.TRANSLATING) "جارٍ الترجمة…" else "ترجمة دلالية — الفيديو الكامل")
                }

                if (state.phase == FieldTestRound2Phase.TRANSLATED) {
                    val sourceUri = state.sourceUri?.let { Uri.parse(it) }
                    val durationMs = metadata?.durationMs
                    when {
                        sourceUri == null -> FieldTestInfoCard(
                            "التصدير",
                            "الترجمة اكتملت لكن URI المصدر غير متاح للمعاينة.",
                        )
                        durationMs == null || durationMs <= 0L -> FieldTestInfoCard(
                            "التصدير",
                            "الترجمة اكتملت لكن مدة الفيديو غير متاحة؛ لن نخمن حد التصدير.",
                        )
                        else -> FieldTestRound2PresentationCard(
                            sourceUri = sourceUri,
                            videoDurationMs = durationMs,
                            units = state.units,
                            entries = state.entries,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldTestStatusCard(state: FieldTestRound2UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("حالة الجولة الثانية", fontWeight = FontWeight.Bold)
            Text(
                when (state.phase) {
                    FieldTestRound2Phase.RESUMING -> "جارٍ فتح/تثبيت جلسة الاختبار…"
                    FieldTestRound2Phase.NO_SOURCE -> "لا يوجد فيديو مربوط حاليًا."
                    FieldTestRound2Phase.SOURCE_READY -> "✓ المصدر جاهز لـfull-video STT."
                    FieldTestRound2Phase.STT_RUNNING -> "جارٍ معالجة نوافذ STT للفيديو الكامل…"
                    FieldTestRound2Phase.STT_READY -> "✓ full-video STT جاهز للترجمة الدلالية."
                    FieldTestRound2Phase.TRANSLATING -> "جارٍ ترجمة semantic units…"
                    FieldTestRound2Phase.TRANSLATED -> "✓ الترجمة الكاملة جاهزة للمعاينة والتصدير."
                    FieldTestRound2Phase.STT_UNKNOWN_REMOTE_OUTCOME ->
                        "أُوقف STT لأن نتيجة نافذة مرسلة غير مؤكدة؛ لا يوجد resend تلقائي."
                    FieldTestRound2Phase.TRANSLATION_BLOCKED -> "توقفت الترجمة عند blocker آمن."
                    FieldTestRound2Phase.FAILED -> "تعذر إكمال الخطوة الحالية."
                }
            )
            state.message?.let { Text(it) }
        }
    }
}

@Composable
private fun FieldTestVideoCard(metadata: VideoMetadata) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الفيديو", fontWeight = FontWeight.Bold)
            FieldTestValue("الاسم", metadata.displayName)
            FieldTestValue("المدة", metadata.durationLabel)
            FieldTestValue("الدقة", metadata.resolutionLabel)
            FieldTestValue("الحجم", metadata.sizeLabel)
        }
    }
}

@Composable
private fun FieldTestInfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
private fun FieldTestValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
        Text(value)
    }
}
