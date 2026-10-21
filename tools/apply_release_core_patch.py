from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, value):
    (ROOT / path).write_text(value, encoding='utf-8')


def replace_once(path, old, new):
    value = read(path)
    count = value.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one exact match, found {count}')
    write(path, value.replace(old, new))


# Preserve accepted local translation even when the source media cannot be reopened.
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

# Missing media may still use text-only editor/result and explicit replacement source review.
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

# Give editor a truthful surface for preview/export preparation failures.
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

host = 'app/src/main/java/com/clw/aivideotranslator/app/ProductAppHostStyleBridge.kt'
replace_once(host, 'import android.net.Uri\n', 'import android.net.Uri\nimport com.clw.aivideotranslator.BuildConfig\n')
replace_once(host, 'import com.clw.aivideotranslator.presentation.CueStyleOverrideDiff\n', 'import com.clw.aivideotranslator.presentation.CueStyleOverrideDiff\nimport com.clw.aivideotranslator.presentation.CommittedPresentationAssembler\n')
replace_once(host, 'import com.clw.aivideotranslator.session.DurableSourcePhase\n', 'import com.clw.aivideotranslator.session.DurableSourceFailure\nimport com.clw.aivideotranslator.session.DurableSourcePhase\n')
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
replace_once(host, '        onConfirmFreshProviderAttempt = {\n', '        onOpenProviderRecoveryDetails = { navigate(ProductNavigationEvent.OpenDiagnostics) },\n        onConfirmFreshProviderAttempt = {\n')
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

# Recovery wording must match actual behavior: replacement starts a new session.
replace_once('app/src/main/java/com/clw/aivideotranslator/ui/recovery/SourceRecoveryScreen.kt', '"إعادة ربط الفيديو"', '"اختيار فيديو وبدء جلسة جديدة"')
replace_once(
    'app/src/main/java/com/clw/aivideotranslator/ui/recovery/SourceRecoveryScreen.kt',
    '"لن يعتمد التطبيق الفيديو الجديد قبل التحقق من هويته وتوافقه مع الجلسة المحفوظة."',
    '"سيتم فحص الفيديو المحدد قبل بدء جلسة جديدة، وتبقى بيانات الجلسة السابقة محفوظة محليًا."',
)
replace_once(
    'app/src/main/java/com/clw/aivideotranslator/ui/recovery/SourceRecoveryScreen.kt',
    '"لم يتم تغيير المصدر المحفوظ. اختر الفيديو الأصلي أو مصدرًا يطابق هوية الجلسة."',
    '"لم يبدأ التطبيق جلسة جديدة بهذا الملف. اختر فيديو صالحًا وحاول مرة أخرى."',
)

print('release core patch applied successfully')
