package com.clw.aivideotranslator.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dedicated recovery for a durable provider request whose remote result is unknown.
 * A fresh attempt is never fired by entering the screen or by tapping the first secondary action;
 * the user must pass a separate duplicate/cost confirmation first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderRecoveryScreen(
    onKeepStateAndReturn: () -> Unit,
    onConfirmFreshAttempt: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    var showFreshAttemptConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("حالة الطلب غير مؤكدة") })
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
                    text = "لا يمكن تأكيد نتيجة الطلب السابق",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "الطلب سُجّل كمرسَل قبل فقدان النتيجة.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "قد يكون المزود نفّذ الطلب بالفعل. لذلك لن يعيد التطبيق الإرسال تلقائيًا، ولن يعتبر عدم وصول النتيجة فشلًا مؤكدًا.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = onKeepStateAndReturn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("الاحتفاظ بالحالة والعودة")
                }

                OutlinedButton(
                    onClick = { showFreshAttemptConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("بدء محاولة جديدة")
                }

                TextButton(onClick = onOpenDetails) {
                    Text("عرض التفاصيل")
                }
            }
        }
    }

    if (showFreshAttemptConfirm) {
        AlertDialog(
            onDismissRequest = { showFreshAttemptConfirm = false },
            title = { Text("تأكيد محاولة جديدة") },
            text = {
                Text(
                    "المحاولة السابقة قد تكون نُفذت بالفعل. إنشاء محاولة جديدة قد يكرر العمل أو التكلفة. لن تُحذف حالة الطلب السابق."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFreshAttemptConfirm = false
                        onConfirmFreshAttempt()
                    },
                ) {
                    Text("بدء محاولة جديدة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreshAttemptConfirm = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}
