from pathlib import Path
import re

ROOT = Path('.')


def text(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, value):
    (ROOT / path).write_text(value, encoding='utf-8')


def replace_once(path, old, new):
    value = text(path)
    count = value.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one exact match, found {count}')
    write(path, value.replace(old, new))


def regex_once(path, pattern, replacement, flags=0):
    value = text(path)
    updated, count = re.subn(pattern, replacement, value, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f'{path}: expected one regex match, found {count}: {pattern}')
    write(path, updated)


# 1) Missing media must not hide already recovered local translation text.
replace_once(
    'app/src/main/java/com/clw/aivideotranslator/session/TranslationSessionViewModel.kt',
    '''            mutableTranslation.value = if (sourceFailure == null) {
                restoreTranslationState(sessionId)
            } else {
                DurableTranslationUiState(
                    phase = DurableTranslationPhase.FAILED,
                    sessionId = sessionId,
                    failure = DurableTranslationFailure.RESTORE,
                )
            }
''',
    '''            mutableTranslation.value = restoreTranslationState(sessionId)
''',
)

# 2) Source-missing recovery permits only explicitly source-independent surfaces plus source review.
replace_once(
    'app/src/main/java/com/clw/aivideotranslator/app/ProductRouteSynchronizer.kt',
    '''        if (facts.hasActiveSession && !facts.sourceAvailable) {
            val textOnlyAllowed = facts.hasAcceptedText && current == ProductRoute.EDITOR_TEXT
            if (!textOnlyAllowed) return ProductRoute.SOURCE_RECOVERY
        }
''',
    '''        if (facts.hasActiveSession && !facts.sourceAvailable) {
            val sourceIndependent = facts.hasAcceptedText && current in setOf(
                ProductRoute.EDITOR_TEXT,
                ProductRoute.RESULT,
            )
            val replacingSource = current == ProductRoute.SOURCE_REVIEW
            if (!sourceIndependent && !replacingSource) return ProductRoute.SOURCE_RECOVERY
        }
''',
)

# 3) Surface bundle-build failures in the editor rather than swallowing a click.
replace_once(
    'app/src/main/java/com/clw/aivideotranslator/ui/editor/EditorTextPresentationResolver.kt',
    '''    val canPreviousCue: Boolean = false,
    val canNextCue: Boolean = false,
)
''',
    '''    val canPreviousCue: Boolean = false,
    val canNextCue: Boolean = false,
    val actionError: String? = null,
)
''',
)

# 4) Active product host: deterministic restart units, truthful recovery/settings/diagnostics,
#    text-only SRT, and real navigation for visible detail/settings actions.
host = 'app/src/main/java/com/clw/aivideotranslator/app/ProductAppHostStyleBridge.kt'
replace_once(host, 'import android.net.Uri\n', 'import android.net.Uri\nimport com.clw.aivideotranslator.BuildConfig\n')
replace_once(
    host,
    'import com.clw.aivideotranslator.presentation.CueStyleOverrideDiff\n',
    'import com.clw.aivideotranslator.presentation.CueStyleOverrideDiff\nimport com.clw.aivideotranslator.presentation.CommittedPresentationAssembler\n',
)
replace_once(
    host,
    'import com.clw.aivideotranslator.session.DurableSourcePhase\n',
    'import com.clw.aivideotranslator.session.DurableSourceFailure\nimport com.clw.aivideotranslator.session.DurableSourcePhase\n',
)
replace_once(
    host,
    'import com.clw.aivideotranslator.ui.processing.ProcessingStage\n',
    '''import com.clw.aivideotranslator.ui.processing.ProcessingStage
import com.clw.aivideotranslator.ui.recovery.SourceRecoveryPhase
import com.clw.aivideotranslator.ui.recovery.SourceRecoveryPresentation
import com.clw.aivideotranslator.ui.settings.AppearancePreference
import com.clw.aivideotranslator.ui.settings.DiagnosticsPresentation
import com.clw.aivideotranslator.ui.settings.SettingsPresentation
''',
)
replace_once(
    host,
    '''    var recoveryAfterCredentialSetup by remember { mutableStateOf(false) }
    var editorUnitState by remember { mutableStateOf(UnitEditorTextState()) }
''',
    '''    var recoveryAfterCredentialSetup by remember { mutableStateOf(false) }
    var aiSetupFromSettings by remember { mutableStateOf(false) }
    var diagnosticsAdvanced by remember { mutableStateOf(false) }
    var editorUnitState by remember { mutableStateOf(UnitEditorTextState()) }
''',
)
replace_once(
    host,
    '''    val runtimeFacts = ProductRuntimeFactsResolver.resolve(
        source = durableSource,
        stt = durableStt,
        translation = durableTranslation,
        credentialUsable = providerApiKey.isNotBlank(),
    )
''',
    '''    val runtimeFacts = ProductRuntimeFactsResolver.resolve(
        source = durableSource,
        stt = durableStt,
        translation = durableTranslation,
        credentialUsable = providerApiKey.isNotBlank(),
        sessionReadable = durableSource.failure != DurableSourceFailure.CORRUPT,
    )
''',
)
replace_once(
    host,
    '''    val activeUnit = cueSelection.unitId?.let { selectedId ->
        durableTranslation.liveUnits?.firstOrNull { it.id == selectedId }
    }
    val activeSessionId = durableTranslation.sessionId ?: durableSource.sessionId
''',
    '''    val activeSessionId = durableTranslation.sessionId ?: durableSource.sessionId
    val presentationUnits = ProductPresentationUnitResolver.resolve(
        liveUnits = durableTranslation.liveUnits,
        entries = durableTranslation.entries,
        timedStt = durableStt.legacyResult,
    )
    val activeUnit = cueSelection.unitId?.let { selectedId ->
        presentationUnits?.firstOrNull { it.id == selectedId }
    }
''',
)
replace_once(host, '            units = durableTranslation.liveUnits.orEmpty(),\n', '            units = presentationUnits.orEmpty(),\n')
replace_once(host, '        val units = durableTranslation.liveUnits ?: return\n', '        val units = presentationUnits ?: return\n')
replace_once(
    host,
    '''    val credentialStatus = when {
''',
    '''    val sourceRecoveryPresentation = SourceRecoveryPresentation(
        phase = if (durableTranslation.entries.isNotEmpty()) {
            SourceRecoveryPhase.MISSING_WITH_TEXT
        } else {
            SourceRecoveryPhase.MISSING_NO_TEXT
        },
        textOnlyAvailable = durableTranslation.entries.isNotEmpty(),
        srtExportAvailable = durableTranslation.entries.isNotEmpty() && presentationUnits != null,
    )
    val settingsPresentation = SettingsPresentation(
        appearance = AppearancePreference.SYSTEM,
        aiCredentialConfigured = credentialRecord != null && credentialRecord?.status != CredentialStatus.REJECTED,
        cacheSummary = "يُدار تلقائيًا",
    )
    val diagnosticsPresentation = DiagnosticsPresentation(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        sessionStateLabel = if (activeSessionId == null) "لا توجد جلسة" else "جلسة محلية نشطة",
        sourceStateLabel = durableSource.phase.name,
        providerStateLabel = "${durableStt.phase.name} / ${durableTranslation.phase.name}",
        advancedExpanded = diagnosticsAdvanced,
    )

    val credentialStatus = when {
''',
)
replace_once(
    host,
    '''    fun persistedOutput(kind: OutputKind): PersistedProductOutput? = when (kind) {
''',
    '''    fun exportCommittedSrtOnly() {
        val sessionId = activeSessionId ?: return
        val units = presentationUnits ?: return
        val entries = durableTranslation.entries
        if (units.isEmpty() || entries.isEmpty()) return
        scope.launch {
            srtOutput = null
            srtSettlement = OutputSettlement.NOT_REQUESTED
            mp4Output = null
            mp4Settlement = OutputSettlement.NOT_REQUESTED
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val cues = CommittedPresentationAssembler.cues(
                        units = units,
                        entries = entries,
                        textState = editorTextStore.read(sessionId),
                        timingState = displayTimingStore.read(sessionId),
                    )
                    val maxTimelineMs = cues.maxOf { it.endMs }
                    ProductOutputStore.saveSrt(
                        context = context,
                        baseName = exportBaseName,
                        srtUtf8 = SubtitlePipeline.srt(cues, maxTimelineMs),
                    )
                }
            }
            saved.onSuccess { output ->
                srtOutput = output
                srtSettlement = OutputSettlement.SUCCESS
            }.onFailure {
                srtSettlement = OutputSettlement.FAILED
            }
            navigate(ProductNavigationEvent.ExportSettled)
        }
    }

    fun persistedOutput(kind: OutputKind): PersistedProductOutput? = when (kind) {
''',
)
replace_once(
    host,
    '''        onBackFromAiSetup = {
            if (recoveryAfterCredentialSetup) {
                recoveryAfterCredentialSetup = false
                route = ProductRoute.PROVIDER_RECOVERY
            } else {
                navigate(ProductNavigationEvent.BackFromAiSetup)
            }
        },
''',
    '''        onBackFromAiSetup = {
            if (aiSetupFromSettings) {
                aiSetupFromSettings = false
                route = ProductRoute.SETTINGS
            } else if (recoveryAfterCredentialSetup) {
                recoveryAfterCredentialSetup = false
                route = ProductRoute.PROVIDER_RECOVERY
            } else {
                navigate(ProductNavigationEvent.BackFromAiSetup)
            }
        },
''',
)
replace_once(
    host,
    '''                    if (saved != null) {
                        credentialRecord = saved
                        if (recoveryAfterCredentialSetup) {
                            recoveryAfterCredentialSetup = false
                            ProviderRecoveryFreshAttempt.armOrNull(
                                priorSessionId = durableStt.sessionId,
                                sourceUri = durableSource.contentUri,
                                apiKey = saved.apiKey,
                            )?.let(::startFreshProviderRecovery)
                        } else {
                            navigate(ProductNavigationEvent.AiCredentialAccepted)
                            sessionViewModel.runStt(saved.apiKey)
                        }
                    }
''',
    '''                    if (saved != null) {
                        credentialRecord = saved
                        if (aiSetupFromSettings) {
                            aiSetupFromSettings = false
                            route = ProductRoute.SETTINGS
                        } else if (recoveryAfterCredentialSetup) {
                            recoveryAfterCredentialSetup = false
                            ProviderRecoveryFreshAttempt.armOrNull(
                                priorSessionId = durableStt.sessionId,
                                sourceUri = durableSource.contentUri,
                                apiKey = saved.apiKey,
                            )?.let(::startFreshProviderRecovery)
                        } else {
                            navigate(ProductNavigationEvent.AiCredentialAccepted)
                            sessionViewModel.runStt(saved.apiKey)
                        }
                    }
''',
)
replace_once(
    host,
    '''        processingPresentation = processingPresentation,
        onBackFromProcessing = { route = ProductRoute.HOME },
''',
    '''        processingPresentation = processingPresentation,
        onBackFromProcessing = { route = ProductRoute.HOME },
        onOpenProcessingDetails = { navigate(ProductNavigationEvent.OpenDiagnostics) },
''',
)
replace_once(
    host,
    '''        onConfirmFreshProviderAttempt = {
''',
    '''        onOpenProviderRecoveryDetails = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        onConfirmFreshProviderAttempt = {
''',
)
replace_once(
    host,
    '''        editorTextPresentation = editorPresentation,
''',
    '''        sourceRecoveryPresentation = sourceRecoveryPresentation,
        onBackHomeFromSourceRecovery = { navigate(ProductNavigationEvent.BackHomeFromRecovery) },
        onContinueTextOnly = { route = ProductRoute.EDITOR_TEXT },
        onRelinkSource = {
            navigate(ProductNavigationEvent.ChooseVideo)
            picker.launch(arrayOf("video/*"))
        },
        onSourceRecoveryExportSrt = ::exportCommittedSrtOnly,
        onBackHomeFromLocalRecovery = { navigate(ProductNavigationEvent.BackHomeFromRecovery) },
        onOpenLocalDiagnostics = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        onRequestCleanStart = {
            navigate(ProductNavigationEvent.ChooseVideo)
            picker.launch(arrayOf("video/*"))
        },
        editorTextPresentation = editorPresentation.copy(actionError = presentationBuildError),
''',
)
replace_once(
    host,
    '''        onExportSrtOnly = {
            exportMode = ExportMode.SRT_ONLY
            prepareCommittedBundleAndNavigate(ProductNavigationEvent.OpenExportCenter)
        },
''',
    '''        onExportSrtOnly = ::exportCommittedSrtOnly,
''',
)
replace_once(
    host,
    '''        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
        onOpenDiagnostics = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        onBackFromDiagnostics = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
''',
    '''        settingsPresentation = settingsPresentation,
        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
        onOpenAiCredentialSettings = {
            aiSetupFromSettings = true
            route = ProductRoute.AI_SETUP
        },
        onOpenDiagnostics = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        diagnosticsPresentation = diagnosticsPresentation,
        onBackFromDiagnostics = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
        onToggleAdvancedDiagnostics = { diagnosticsAdvanced = !diagnosticsAdvanced },
''',
)

# 5) Settings exposes only working product actions.
write(
    'app/src/main/java/com/clw/aivideotranslator/ui/settings/SettingsScreen.kt',
    '''package com.clw.aivideotranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class AppearancePreference { SYSTEM, LIGHT, DARK }

internal data class SettingsPresentation(
    val appearance: AppearancePreference,
    val aiCredentialConfigured: Boolean,
    val cacheSummary: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    presentation: SettingsPresentation,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onAiCredential: () -> Unit,
    onDefaultStyle: () -> Unit,
    onClearCache: () -> Unit,
    onClearProject: () -> Unit,
    onAboutDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                settingRow(
                    title = "إعداد الذكاء الاصطناعي",
                    detail = if (presentation.aiCredentialConfigured) "تم إعداد مفتاح NVIDIA" else "لم يتم إعداد المفتاح",
                    onClick = onAiCredential,
                )
                settingRow(
                    title = "حول وتشخيص",
                    detail = "هوية الإصدار وحالة الجلسة والمصدر والمزود",
                    onClick = onAboutDiagnostics,
                )
                Text(
                    "تظهر هنا فقط الإعدادات المكتملة فعليًا في الإصدار الحالي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun settingRow(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
''',
)

# 6) Diagnostics shows real values and no dead license button.
write(
    'app/src/main/java/com/clw/aivideotranslator/ui/settings/DiagnosticsScreen.kt',
    '''package com.clw.aivideotranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class DiagnosticsPresentation(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val sessionStateLabel: String,
    val sourceStateLabel: String,
    val providerStateLabel: String,
    val advancedExpanded: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsScreen(
    presentation: DiagnosticsPresentation,
    onBack: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حول وتشخيص") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AI Video Translator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الإصدار ${presentation.versionName} (${presentation.versionCode})")
                        Text("نوع البناء: ${presentation.buildType}")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("حالة آمنة مختصرة", fontWeight = FontWeight.Bold)
                        Text("الجلسة: ${presentation.sessionStateLabel}")
                        Text("المصدر: ${presentation.sourceStateLabel}")
                        Text("المزود: ${presentation.providerStateLabel}")
                    }
                }
                TextButton(onClick = onToggleAdvanced) {
                    Text(if (presentation.advancedExpanded) "إخفاء التشخيص المتقدم" else "عرض التشخيص المتقدم")
                }
                if (presentation.advancedExpanded) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "لا يعرض هذا القسم API Key أو نص الترجمة أو عنوان ملف الفيديو أو أي محتوى خاص.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "الخط المضمّن Noto Sans Arabic مرخّص وفق SIL Open Font License 1.1، ونص الترخيص مضمّن داخل التطبيق.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
''',
)

# 7) Remove visible dead help control; guidance remains inline.
regex_once(
    'app/src/main/java/com/clw/aivideotranslator/ui/ai/AiSetupScreen.kt',
    r'''\n\s*TextButton\(onClick = onHelp\) \{\n\s*Text\("مساعدة"\)\n\s*\}\n''',
    '\n',
)

# 8) Remove fake editor/timing/style preview controls. Fullscreen preview is the real media surface.
editor = 'app/src/main/java/com/clw/aivideotranslator/ui/editor/EditorTextScreen.kt'
for imp in [
    'import androidx.compose.foundation.background\n',
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.ui.graphics.Color\n',
]:
    replace_once(editor, imp, '')
regex_once(
    editor,
    r'''\n\s*else \{\n\s*Box\(.*?Text\("تشغيل / إيقاف"\)\n\s*\}\n\s*\}\n''',
    '\n                }\n',
    flags=re.S,
)
replace_once(
    editor,
    '''                if (presentation.reviewRequired) {
''',
    '''                presentation.actionError?.takeIf { it.isNotBlank() }?.let { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    }
                }

                if (presentation.reviewRequired) {
''',
)

timing = 'app/src/main/java/com/clw/aivideotranslator/ui/editor/TimingEditorScreen.kt'
for imp in [
    'import androidx.compose.foundation.background\n',
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.ui.graphics.Color\n',
]:
    replace_once(timing, imp, '')
regex_once(
    timing,
    r'''\n\s*Box\(\n.*?Text\("معاينة الفيديو", color = Color.White\)\n\s*\}\n''',
    '\n',
    flags=re.S,
)
regex_once(
    timing,
    r'''\n\s*OutlinedButton\(\n\s*onClick = onPlayCue,.*?Text\("تشغيل هذا المقطع"\)\n\s*\}\n''',
    '\n',
    flags=re.S,
)

style = 'app/src/main/java/com/clw/aivideotranslator/ui/editor/StyleStudioScreen.kt'
for imp in [
    'import androidx.compose.foundation.background\n',
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.ui.graphics.Color\n',
]:
    replace_once(style, imp, '')
regex_once(
    style,
    r'''\n\s*Box\(\n.*?Text\(\n\s*text = "معاينة الترجمة",.*?\n\s*\}\n''',
    '''
                Text(
                    "طبّق التغييرات ثم افتح «ملء الشاشة» من محرر النص لمعاينة الفيديو بنفس رسومات التصدير.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
''',
    flags=re.S,
)

# 9) Recovery copy/actions are truthful: replacement starts a new session; local corruption stays fail-closed.
source_recovery = 'app/src/main/java/com/clw/aivideotranslator/ui/recovery/SourceRecoveryScreen.kt'
replace_once(source_recovery, '"إعادة ربط الفيديو"', '"اختيار فيديو وبدء جلسة جديدة"')
replace_once(
    source_recovery,
    '"لن يعتمد التطبيق الفيديو الجديد قبل التحقق من هويته وتوافقه مع الجلسة المحفوظة."',
    '"سيتم فحص الفيديو المحدد قبل بدء جلسة جديدة، وتبقى بيانات الجلسة السابقة محفوظة محليًا."',
)
replace_once(
    source_recovery,
    '"لم يتم تغيير المصدر المحفوظ. اختر الفيديو الأصلي أو مصدرًا يطابق هوية الجلسة."',
    '"لم يبدأ التطبيق جلسة جديدة بهذا الملف. اختر فيديو صالحًا وحاول مرة أخرى."',
)

write(
    'app/src/main/java/com/clw/aivideotranslator/ui/recovery/LocalRecoveryScreen.kt',
    '''package com.clw.aivideotranslator.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

internal enum class LocalRecoveryPhase { BASE, CLEAN_CONFIRM, BACKUP_FAILED }

/** Fail-closed local recovery. No destructive cleanup is exposed without a verified backup path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalRecoveryScreen(
    phase: LocalRecoveryPhase,
    onBackHome: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestCleanStart: () -> Unit,
    onConfirmCleanStart: () -> Unit,
    onCancelCleanStart: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استرداد الجلسة المحلية") },
                navigationIcon = { TextButton(onClick = onBackHome) { Text("الرئيسية") } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("تعذر فتح الجلسة بأمان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "أوقف التطبيق الاسترداد بدل تخمين أو تعديل بيانات الجلسة. لن تُحذف البيانات ولن يُعاد إرسال أي طلب للمزود من هذه الشاشة.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("فتح التشخيص")
                }
                Button(onClick = onRequestCleanStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("اختيار فيديو وبدء جلسة جديدة")
                }
            }
        }
    }
}
''',
)

print('release-readiness patch applied successfully')
