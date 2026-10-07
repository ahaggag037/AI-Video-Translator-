package com.clw.aivideotranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private sealed interface HomeState {
    data object Empty : HomeState
    data object Loading : HomeState
    data class Ready(val uri: Uri, val metadata: VideoMetadata) : HomeState
    data class Error(val message: String) : HomeState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val context = LocalContext.current
    var state: HomeState by remember { mutableStateOf(HomeState.Empty) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
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
                    "P0 — اختيار الفيديو وفحصه",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "هذه أول شريحة تنفيذية. لا يتم رفع الفيديو أو إرسال أي بيانات إلى NVIDIA في هذا الإصدار."
                )

                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اختيار فيديو")
                }

                when (val s = state) {
                    HomeState.Empty -> InfoCard("الحالة", "اختر فيديو محليًا لقراءة المدة والحجم والدقة.")
                    HomeState.Loading -> InfoCard("الحالة", "جارٍ قراءة معلومات الفيديو…")
                    is HomeState.Error -> InfoCard("تعذر الفحص", s.message)
                    is HomeState.Ready -> VideoCard(s.metadata)
                }

                InfoCard(
                    "مزود الذكاء الاصطناعي",
                    "NVIDIA NIM — سيُضاف الاتصال الفعلي في بوابة STT/Translation بعد تثبيت مسار الصوت والتوقيت."
                )
                InfoCard(
                    "المرحلة التالية",
                    "استخراج عينة صوت 45–60 ثانية، ثم اختبار STT والتوقيت قبل بناء المشاريع والاستئناف."
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
