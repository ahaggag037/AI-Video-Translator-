package com.clw.aivideotranslator.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class AiCredentialStatus {
    MISSING,
    UNVERIFIED,
    USABLE,
    REJECTED,
}

/**
 * Product-owned credential entry surface. It never validates by making a separate provider request;
 * the key is handed to the operation owner only after explicit user action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSetupScreen(
    credentialStatus: AiCredentialStatus,
    onBack: () -> Unit,
    onSaveAndStart: (String) -> Unit,
    onHelp: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعداد الذكاء الاصطناعي") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
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
                    text = "مفتاح NVIDIA",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "يُستخدم المفتاح لطلبات التحويل من الكلام إلى نص والترجمة. لا ينفّذ التطبيق طلبًا منفصلًا لمجرد اختبار المفتاح.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = credentialStatusMessage(credentialStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "إخفاء المفتاح" else "إظهار المفتاح")
                }

                Button(
                    onClick = { onSaveAndStart(apiKey.trim()) },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("حفظ المفتاح")
                }
            }
        }
    }
}

private fun credentialStatusMessage(status: AiCredentialStatus): String = when (status) {
    AiCredentialStatus.MISSING -> "لم يتم إدخال مفتاح بعد."
    AiCredentialStatus.UNVERIFIED -> "المفتاح محفوظ محليًا ولم يُستخدم بنجاح بعد."
    AiCredentialStatus.USABLE -> "سبق استخدام المفتاح بنجاح."
    AiCredentialStatus.REJECTED -> "رفض المزود المفتاح في آخر محاولة. أدخل مفتاحًا صالحًا قبل المتابعة."
}
