package com.clw.aivideotranslator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    status: HomeStatus,
    sourceSelectionEnabled: Boolean,
    onChooseVideo: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    showDeveloperTools: Boolean = false,
    onOpenDeveloperTools: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مترجم الفيديو") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("الإعدادات") }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "حوّل كلام الفيديو إلى ترجمة عربية قابلة للمراجعة والتصدير.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "اختَر فيديو، راجع النص والتوقيت والنمط، ثم صدّر ملف SRT أو فيديو بترجمة مدمجة أو الاثنين معًا.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (status) {
                    HomeStatus.Empty -> EmptyHomeCard(
                        sourceSelectionEnabled = sourceSelectionEnabled,
                        onChooseVideo = onChooseVideo,
                    )
                    HomeStatus.ActiveReady -> ActiveHomeCard(
                        title = "المشروع جاهز للمتابعة",
                        body = "يوجد عمل محفوظ يمكن استئنافه دون إعادة الخطوات المكتملة.",
                        primaryLabel = "متابعة المشروع",
                        onPrimary = onContinue,
                        sourceSelectionEnabled = sourceSelectionEnabled,
                        onChooseVideo = onChooseVideo,
                    )
                    HomeStatus.ActiveProcessing -> ActiveHomeCard(
                        title = "المعالجة مستمرة",
                        body = "الجلسة الحالية محفوظة. افتحها لرؤية المرحلة الحالية بدون بدء طلب جديد تلقائيًا.",
                        primaryLabel = "عرض المعالجة",
                        onPrimary = onContinue,
                        sourceSelectionEnabled = false,
                        onChooseVideo = onChooseVideo,
                    )
                    HomeStatus.ActionUnknownProvider -> ActionCard(
                        title = "النتيجة البعيدة غير مؤكدة",
                        body = "يوجد طلب أُرسل بالفعل ولم تُحسم نتيجته. لن يعيد التطبيق الإرسال تلقائيًا.",
                        action = "مراجعة حالة الطلب",
                        onAction = onContinue,
                    )
                    HomeStatus.ActionSourceMissing -> ActionCard(
                        title = "مصدر الفيديو يحتاج مراجعة",
                        body = "العمل النصي المحفوظ ما زال آمنًا، لكن الفيديو نفسه يحتاج إعادة ربط قبل تشغيل الميزات المعتمدة عليه.",
                        action = "فتح استرداد المصدر",
                        onAction = onContinue,
                    )
                }

                if (showDeveloperTools) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenDeveloperTools) {
                        Text("أدوات الهندسة المؤقتة")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(
    sourceSelectionEnabled: Boolean,
    onChooseVideo: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ابدأ بفيديو", style = MaterialTheme.typography.titleLarge)
            Text(
                "لن يستبدل التطبيق أي عمل محفوظ قبل فحص المصدر الجديد واعتماده بأمان.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onChooseVideo,
                enabled = sourceSelectionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("اختيار فيديو")
            }
        }
    }
}

@Composable
private fun ActiveHomeCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    sourceSelectionEnabled: Boolean,
    onChooseVideo: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(primaryLabel)
            }
            OutlinedButton(
                onClick = onChooseVideo,
                enabled = sourceSelectionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("اختيار فيديو جديد")
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(action)
            }
        }
    }
}
