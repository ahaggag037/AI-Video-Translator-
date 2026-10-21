package com.clw.aivideotranslator.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clw.aivideotranslator.presentation.BuiltInStylePreset
import com.clw.aivideotranslator.presentation.LayoutStatus
import com.clw.aivideotranslator.presentation.StyleScope
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.presentation.SubtitleStyle

internal data class StyleStudioPresentation(
    val scope: StyleScope,
    val style: SubtitleStyle,
    val layoutStatus: LayoutStatus,
    val dirty: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StyleStudioScreen(
    presentation: StyleStudioPresentation,
    onBack: () -> Unit,
    onOpenText: () -> Unit,
    onOpenTiming: () -> Unit,
    onScopeChange: (StyleScope) -> Unit,
    onPreset: (BuiltInStylePreset) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onAnchorChange: (SubtitleAnchor) -> Unit,
    onToggleOutline: () -> Unit,
    onToggleShadow: () -> Unit,
    onToggleBackground: () -> Unit,
    onDecreaseMaxLines: () -> Unit,
    onIncreaseMaxLines: () -> Unit,
    onApply: () -> Unit,
    onCancelDraft: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نمط الترجمة") },
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "معاينة الترجمة",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (presentation.layoutStatus != LayoutStatus.FITS) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when (presentation.layoutStatus) {
                                LayoutStatus.OVERFLOW -> "النص يتجاوز المساحة الآمنة. صحح النمط قبل تصدير MP4؛ تصدير SRT لا يتوقف بسبب ذلك."
                                LayoutStatus.REVIEW_REQUIRED -> "يحتاج تخطيط الترجمة إلى مراجعة قبل تصدير MP4."
                                LayoutStatus.FITS -> ""
                            },
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onOpenText, modifier = Modifier.weight(1f)) {
                        Text("النص")
                    }
                    OutlinedButton(onClick = onOpenTiming, modifier = Modifier.weight(1f)) {
                        Text("التوقيت")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("النمط")
                    }
                }

                Text("نطاق التعديل", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    scopeButton(
                        label = "كل الترجمة",
                        selected = presentation.scope == StyleScope.PROJECT,
                        onClick = { onScopeChange(StyleScope.PROJECT) },
                        modifier = Modifier.weight(1f),
                    )
                    scopeButton(
                        label = "هذا المقطع فقط",
                        selected = presentation.scope == StyleScope.CURRENT_CUE,
                        onClick = { onScopeChange(StyleScope.CURRENT_CUE) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("أنماط جاهزة", fontWeight = FontWeight.Bold)
                BuiltInStylePreset.entries.forEach { preset ->
                    OutlinedButton(
                        onClick = { onPreset(preset) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (preset) {
                                BuiltInStylePreset.CLEAR -> "واضح"
                                BuiltInStylePreset.SIMPLE -> "بسيط"
                                BuiltInStylePreset.CINEMA -> "سينمائي"
                                BuiltInStylePreset.HIGH_CONTRAST -> "تباين عالٍ"
                                BuiltInStylePreset.BOX -> "صندوق"
                            },
                        )
                    }
                }

                Text("حجم الخط: ${presentation.style.fontSizeSp.toInt()}")
                Slider(
                    value = presentation.style.fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 20f..80f,
                )

                Text("ارتفاع السطر: ${presentation.style.lineHeightSp.toInt()}")
                Slider(
                    value = presentation.style.lineHeightSp,
                    onValueChange = onLineHeightChange,
                    valueRange = presentation.style.fontSizeSp..100f,
                )

                Text("شفافية النص: ${(presentation.style.opacity * 100).toInt()}%")
                Slider(
                    value = presentation.style.opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.2f..1f,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onToggleOutline, modifier = Modifier.weight(1f)) {
                        Text("الحد الخارجي")
                    }
                    OutlinedButton(onClick = onToggleShadow, modifier = Modifier.weight(1f)) {
                        Text("الظل")
                    }
                    OutlinedButton(onClick = onToggleBackground, modifier = Modifier.weight(1f)) {
                        Text("الخلفية")
                    }
                }

                Text("موضع الترجمة", fontWeight = FontWeight.Bold)
                SubtitleAnchor.entries.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { anchor ->
                            OutlinedButton(
                                onClick = { onAnchorChange(anchor) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (anchor == presentation.style.anchor) "●" else "○")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDecreaseMaxLines) { Text("−") }
                    Text("الحد الأقصى للأسطر: ${presentation.style.maxLines}")
                    OutlinedButton(onClick = onIncreaseMaxLines) { Text("+") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancelDraft,
                        enabled = presentation.dirty,
                        modifier = Modifier.weight(1f),
                    ) { Text("إلغاء") }
                    Button(
                        onClick = onApply,
                        enabled = presentation.dirty,
                        modifier = Modifier.weight(1f),
                    ) { Text("تطبيق") }
                }
            }
        }
    }
}

@Composable
private fun scopeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
