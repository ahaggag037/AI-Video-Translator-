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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
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
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state: HomeState by remember { mutableStateOf(HomeState.Empty) }
    var player: MediaPlayer? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        player?.release()
        player = null
        state = HomeState.Loading
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        state = VideoProbe.read(context, uri).fold(
            onSuccess = { HomeState.Ready(uri, it) },
            onFailure = { HomeState.Error(it.message ?: "تعذر قراءة بيانات الفيديو") }
        )
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
                    "P0-B — استخراج عينة الصوت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "هذه المرحلة تعمل محليًا بالكامل. لا يتم رفع الفيديو أو الصوت أو إرسال أي بيانات إلى NVIDIA بعد."
                )

                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اختيار فيديو")
                }

                when (val s = state) {
                    HomeState.Empty -> InfoCard("الحالة", "اختر فيديو لقراءة بياناته ثم استخراج أول دقيقة من مسار الصوت.")
                    HomeState.Loading -> InfoCard("الحالة", "جارٍ قراءة معلومات الفيديو…")
                    is HomeState.Error -> InfoCard("تعذر الفحص", s.message)
                    is HomeState.Ready -> {
                        VideoCard(s.metadata)
                        SampleCard(
                            state = s.sampleState,
                            onExtract = { extractSample(s) },
                            onPlay = ::playSample,
                        )
                    }
                }

                InfoCard(
                    "ما الذي نثبته الآن؟",
                    "أن الهاتف يستطيع أخذ مقطع صوت محلي مدته نحو 60 ثانية من الفيديو، وأن الملف الناتج قابل للقراءة والتشغيل قبل إدخال أي API."
                )
                InfoCard(
                    "المرحلة التالية بعد نجاح العينة",
                    "ربط عينة الصوت بـ NVIDIA NIM لاختبار STT الفعلي والتوقيت، ثم اختيار نموذج التفريغ المناسب بناءً على النتيجة لا على الافتراض."
                )
            }
        }
    }
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
            Text("✓ تم حفظ إذن القراءة الدائم عندما يسمح مزود الملفات بذلك.")
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
                    Text("لم يتم استخراج العينة بعد. سنأخذ أول دقيقة من مسار الصوت بدون رفع أي شيء للشبكة.")
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
                    Text("الملف المؤقت: ${sample.file.name}")
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
        Text(key, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
        Text(value)
    }
}
