package com.clw.aivideotranslator.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.clw.aivideotranslator.export.ExportMode
import com.clw.aivideotranslator.export.OutputKind
import com.clw.aivideotranslator.presentation.BuiltInStylePreset
import com.clw.aivideotranslator.presentation.StyleScope
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.ui.ai.AiCredentialStatus
import com.clw.aivideotranslator.ui.ai.AiSetupScreen
import com.clw.aivideotranslator.ui.editor.EditorTextPresentation
import com.clw.aivideotranslator.ui.editor.EditorTextScreen
import com.clw.aivideotranslator.ui.editor.StyleStudioPresentation
import com.clw.aivideotranslator.ui.editor.StyleStudioScreen
import com.clw.aivideotranslator.ui.editor.TimingEditorPresentation
import com.clw.aivideotranslator.ui.editor.TimingEditorScreen
import com.clw.aivideotranslator.ui.export.ExportCenterPresentation
import com.clw.aivideotranslator.ui.export.ExportCenterScreen
import com.clw.aivideotranslator.ui.export.ExportProgressPresentation
import com.clw.aivideotranslator.ui.export.ExportProgressScreen
import com.clw.aivideotranslator.ui.export.ResultPresentation
import com.clw.aivideotranslator.ui.export.ResultScreen
import com.clw.aivideotranslator.ui.home.HomeScreen
import com.clw.aivideotranslator.ui.home.HomeStatus
import com.clw.aivideotranslator.ui.preview.FullscreenPreviewPresentation
import com.clw.aivideotranslator.ui.preview.FullscreenPreviewScreen
import com.clw.aivideotranslator.ui.processing.ProcessingPresentation
import com.clw.aivideotranslator.ui.processing.ProcessingScreen
import com.clw.aivideotranslator.ui.processing.ProcessingStage
import com.clw.aivideotranslator.ui.recovery.LocalRecoveryPhase
import com.clw.aivideotranslator.ui.recovery.LocalRecoveryScreen
import com.clw.aivideotranslator.ui.recovery.ProviderRecoveryScreen
import com.clw.aivideotranslator.ui.recovery.SourceRecoveryPhase
import com.clw.aivideotranslator.ui.recovery.SourceRecoveryPresentation
import com.clw.aivideotranslator.ui.recovery.SourceRecoveryScreen
import com.clw.aivideotranslator.ui.settings.DiagnosticsPresentation
import com.clw.aivideotranslator.ui.settings.DiagnosticsScreen
import com.clw.aivideotranslator.ui.settings.SettingsPresentation
import com.clw.aivideotranslator.ui.settings.SettingsScreen
import com.clw.aivideotranslator.ui.settings.AppearancePreference
import com.clw.aivideotranslator.ui.source.SourceReviewPresentation
import com.clw.aivideotranslator.ui.source.SourceReviewScreen
import com.clw.aivideotranslator.ui.theme.QuietProTheme

/**
 * Final product root shell. It receives already-derived UI facts/callbacks rather than reading stores
 * directly; durable session/provider state remains the semantic source of truth outside UI.
 */
@Composable
internal fun AppRoot(
    route: ProductRoute,
    homeStatus: HomeStatus,
    sourceSelectionEnabled: Boolean,
    sourceReviewPresentation: SourceReviewPresentation,
    onChooseVideo: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackFromSourceReview: () -> Unit,
    onChangeVideo: () -> Unit,
    onAdoptVideo: () -> Unit,
    aiCredentialStatus: AiCredentialStatus = AiCredentialStatus.MISSING,
    onBackFromAiSetup: () -> Unit = {},
    onSaveAiKeyAndStart: (String) -> Unit = {},
    onOpenAiHelp: () -> Unit = {},
    processingPresentation: ProcessingPresentation = ProcessingPresentation(
        stage = ProcessingStage.AUDIO_PREPARATION,
    ),
    onBackFromProcessing: () -> Unit = {},
    onSafeStopProcessing: () -> Unit = {},
    onOpenProcessingDetails: () -> Unit = {},
    onKeepProviderStateAndReturn: () -> Unit = {},
    onConfirmFreshProviderAttempt: () -> Unit = {},
    onOpenProviderRecoveryDetails: () -> Unit = {},
    sourceRecoveryPresentation: SourceRecoveryPresentation = SourceRecoveryPresentation(
        phase = SourceRecoveryPhase.MISSING_NO_TEXT,
        textOnlyAvailable = false,
        srtExportAvailable = false,
    ),
    onBackHomeFromSourceRecovery: () -> Unit = {},
    onContinueTextOnly: () -> Unit = {},
    onRelinkSource: () -> Unit = {},
    onSourceRecoveryExportSrt: () -> Unit = {},
    localRecoveryPhase: LocalRecoveryPhase = LocalRecoveryPhase.BASE,
    onBackHomeFromLocalRecovery: () -> Unit = {},
    onOpenLocalDiagnostics: () -> Unit = {},
    onRequestCleanStart: () -> Unit = {},
    onConfirmCleanStart: () -> Unit = {},
    onCancelCleanStart: () -> Unit = {},
    editorTextPresentation: EditorTextPresentation = EditorTextPresentation(
        sourceText = "",
        targetText = "",
        hasPendingDraft = false,
        reviewRequired = false,
        sourceMissing = false,
        textEditingEnabled = true,
        playbackEnabled = false,
        fullscreenPreviewEnabled = false,
        timingEnabled = false,
        styleEnabled = false,
        srtExportEnabled = false,
        mp4ExportEnabled = false,
    ),
    onBackFromEditor: () -> Unit = {},
    onOpenTextEditor: () -> Unit = {},
    onEditorTargetChange: (String) -> Unit = {},
    onCommitEditorDraft: () -> Unit = {},
    onEditorUndo: () -> Unit = {},
    onEditorRedo: () -> Unit = {},
    onRestoreMachineTranslation: () -> Unit = {},
    onEditorPlayPause: () -> Unit = {},
    onPreviousEditorCue: () -> Unit = {},
    onNextEditorCue: () -> Unit = {},
    onOpenTimingEditor: () -> Unit = {},
    onOpenStyleEditor: () -> Unit = {},
    onOpenFullscreenPreview: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onExportSrtOnly: () -> Unit = {},
    timingEditorPresentation: TimingEditorPresentation? = null,
    onTimingStartChange: (String) -> Unit = {},
    onTimingEndChange: (String) -> Unit = {},
    onNudgeStartBackward: () -> Unit = {},
    onNudgeStartForward: () -> Unit = {},
    onNudgeEndBackward: () -> Unit = {},
    onNudgeEndForward: () -> Unit = {},
    onPlayTimingCue: () -> Unit = {},
    onResetTimingToSource: () -> Unit = {},
    onCommitTiming: () -> Unit = {},
    styleStudioPresentation: StyleStudioPresentation? = null,
    onStyleScopeChange: (StyleScope) -> Unit = {},
    onStylePreset: (BuiltInStylePreset) -> Unit = {},
    onFontSizeChange: (Float) -> Unit = {},
    onLineHeightChange: (Float) -> Unit = {},
    onOpacityChange: (Float) -> Unit = {},
    onAnchorChange: (SubtitleAnchor) -> Unit = {},
    onToggleOutline: () -> Unit = {},
    onToggleShadow: () -> Unit = {},
    onToggleBackground: () -> Unit = {},
    onDecreaseMaxLines: () -> Unit = {},
    onIncreaseMaxLines: () -> Unit = {},
    onApplyStyle: () -> Unit = {},
    onCancelStyleDraft: () -> Unit = {},
    fullscreenPreviewPresentation: FullscreenPreviewPresentation? = null,
    onTogglePreviewChrome: () -> Unit = {},
    onBackFromFullscreenPreview: () -> Unit = {},
    onPreviewPlayPause: () -> Unit = {},
    onPreviewSeekRequested: () -> Unit = {},
    exportCenterPresentation: ExportCenterPresentation? = null,
    onExportModeChange: (ExportMode) -> Unit = {},
    onEditExportFilename: () -> Unit = {},
    onStartExport: () -> Unit = {},
    exportProgressPresentation: ExportProgressPresentation? = null,
    onBackFromExportProgress: () -> Unit = {},
    onRequestExportCancel: () -> Unit = {},
    onRetryFailedOutput: () -> Unit = {},
    resultPresentation: ResultPresentation? = null,
    onBackHomeFromResult: () -> Unit = {},
    onOpenResultOutput: (OutputKind) -> Unit = {},
    onShareResultOutput: (OutputKind) -> Unit = {},
    onLocateResultOutput: (OutputKind) -> Unit = {},
    onRetryResultOutput: (OutputKind) -> Unit = {},
    settingsPresentation: SettingsPresentation = SettingsPresentation(
        appearance = AppearancePreference.SYSTEM,
        aiCredentialConfigured = false,
        cacheSummary = "لم يتم القياس بعد",
    ),
    onBackFromSettings: () -> Unit = {},
    onOpenAppearanceSettings: () -> Unit = {},
    onOpenAiCredentialSettings: () -> Unit = {},
    onOpenDefaultStyleSettings: () -> Unit = {},
    onClearCacheSettings: () -> Unit = {},
    onClearProjectSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    diagnosticsPresentation: DiagnosticsPresentation = DiagnosticsPresentation(
        versionName = "",
        versionCode = 0,
        buildType = "",
        sessionStateLabel = "غير معروف",
        sourceStateLabel = "غير معروف",
        providerStateLabel = "غير معروف",
    ),
    onBackFromDiagnostics: () -> Unit = {},
    onToggleAdvancedDiagnostics: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    showDeveloperTools: Boolean = false,
    onOpenDeveloperTools: () -> Unit = {},
) {
    QuietProTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            when (route) {
                ProductRoute.HOME -> HomeScreen(
                    status = homeStatus,
                    sourceSelectionEnabled = sourceSelectionEnabled,
                    onChooseVideo = onChooseVideo,
                    onContinue = onContinue,
                    onOpenSettings = onOpenSettings,
                    showDeveloperTools = showDeveloperTools,
                    onOpenDeveloperTools = onOpenDeveloperTools,
                )

                ProductRoute.SOURCE_REVIEW -> SourceReviewScreen(
                    presentation = sourceReviewPresentation,
                    onBack = onBackFromSourceReview,
                    onChangeVideo = onChangeVideo,
                    onAdoptVideo = onAdoptVideo,
                )

                ProductRoute.AI_SETUP -> AiSetupScreen(
                    credentialStatus = aiCredentialStatus,
                    onBack = onBackFromAiSetup,
                    onSaveAndStart = onSaveAiKeyAndStart,
                    onHelp = onOpenAiHelp,
                )

                ProductRoute.PROCESSING -> ProcessingScreen(
                    presentation = processingPresentation,
                    onBack = onBackFromProcessing,
                    onSafeStop = onSafeStopProcessing,
                    onOpenDetails = onOpenProcessingDetails,
                )

                ProductRoute.PROVIDER_RECOVERY -> ProviderRecoveryScreen(
                    onKeepStateAndReturn = onKeepProviderStateAndReturn,
                    onConfirmFreshAttempt = onConfirmFreshProviderAttempt,
                    onOpenDetails = onOpenProviderRecoveryDetails,
                )

                ProductRoute.SOURCE_RECOVERY -> SourceRecoveryScreen(
                    presentation = sourceRecoveryPresentation,
                    onBackHome = onBackHomeFromSourceRecovery,
                    onContinueTextOnly = onContinueTextOnly,
                    onRelinkSource = onRelinkSource,
                    onExportSrt = onSourceRecoveryExportSrt,
                )

                ProductRoute.LOCAL_RECOVERY -> LocalRecoveryScreen(
                    phase = localRecoveryPhase,
                    onBackHome = onBackHomeFromLocalRecovery,
                    onOpenDiagnostics = onOpenLocalDiagnostics,
                    onRequestCleanStart = onRequestCleanStart,
                    onConfirmCleanStart = onConfirmCleanStart,
                    onCancelCleanStart = onCancelCleanStart,
                )

                ProductRoute.EDITOR_TEXT -> EditorTextScreen(
                    presentation = editorTextPresentation,
                    onBack = onBackFromEditor,
                    onTargetChange = onEditorTargetChange,
                    onCommitDraft = onCommitEditorDraft,
                    onUndo = onEditorUndo,
                    onRedo = onEditorRedo,
                    onRestoreMachine = onRestoreMachineTranslation,
                    onPlayPause = onEditorPlayPause,
                    onPreviousCue = onPreviousEditorCue,
                    onNextCue = onNextEditorCue,
                    onOpenTiming = onOpenTimingEditor,
                    onOpenStyle = onOpenStyleEditor,
                    onOpenFullscreen = onOpenFullscreenPreview,
                    onOpenExport = onOpenExport,
                    onExportSrtOnly = onExportSrtOnly,
                )

                ProductRoute.EDITOR_TIMING -> timingEditorPresentation?.let { presentation ->
                    TimingEditorScreen(
                        presentation = presentation,
                        onBack = onOpenTextEditor,
                        onOpenText = onOpenTextEditor,
                        onOpenStyle = onOpenStyleEditor,
                        onStartChange = onTimingStartChange,
                        onEndChange = onTimingEndChange,
                        onNudgeStartBackward = onNudgeStartBackward,
                        onNudgeStartForward = onNudgeStartForward,
                        onNudgeEndBackward = onNudgeEndBackward,
                        onNudgeEndForward = onNudgeEndForward,
                        onPlayCue = onPlayTimingCue,
                        onResetToSource = onResetTimingToSource,
                        onCommit = onCommitTiming,
                    )
                } ?: MissingRouteState("تعذر تحميل حالة التوقيت")

                ProductRoute.STYLE_STUDIO -> styleStudioPresentation?.let { presentation ->
                    StyleStudioScreen(
                        presentation = presentation,
                        onBack = onOpenTextEditor,
                        onOpenText = onOpenTextEditor,
                        onOpenTiming = onOpenTimingEditor,
                        onScopeChange = onStyleScopeChange,
                        onPreset = onStylePreset,
                        onFontSizeChange = onFontSizeChange,
                        onLineHeightChange = onLineHeightChange,
                        onOpacityChange = onOpacityChange,
                        onAnchorChange = onAnchorChange,
                        onToggleOutline = onToggleOutline,
                        onToggleShadow = onToggleShadow,
                        onToggleBackground = onToggleBackground,
                        onDecreaseMaxLines = onDecreaseMaxLines,
                        onIncreaseMaxLines = onIncreaseMaxLines,
                        onApply = onApplyStyle,
                        onCancelDraft = onCancelStyleDraft,
                    )
                } ?: MissingRouteState("تعذر تحميل حالة النمط")

                ProductRoute.FULLSCREEN_PREVIEW -> fullscreenPreviewPresentation?.let { presentation ->
                    FullscreenPreviewScreen(
                        presentation = presentation,
                        onToggleChrome = onTogglePreviewChrome,
                        onBack = onBackFromFullscreenPreview,
                        onPlayPause = onPreviewPlayPause,
                        onSeekRequested = onPreviewSeekRequested,
                    )
                } ?: MissingRouteState("تعذر تحميل المعاينة")

                ProductRoute.EXPORT_CENTER -> exportCenterPresentation?.let { presentation ->
                    ExportCenterScreen(
                        presentation = presentation,
                        onBack = onOpenTextEditor,
                        onModeChange = onExportModeChange,
                        onEditFilename = onEditExportFilename,
                        onExport = onStartExport,
                    )
                } ?: MissingRouteState("تعذر تحميل إعدادات التصدير")

                ProductRoute.EXPORT_PROGRESS -> exportProgressPresentation?.let { presentation ->
                    ExportProgressScreen(
                        presentation = presentation,
                        onBack = onBackFromExportProgress,
                        onRequestCancel = onRequestExportCancel,
                        onRetryFailedOutput = onRetryFailedOutput,
                    )
                } ?: MissingRouteState("تعذر تحميل تقدم التصدير")

                ProductRoute.RESULT -> resultPresentation?.let { presentation ->
                    ResultScreen(
                        presentation = presentation,
                        onBackHome = onBackHomeFromResult,
                        onOpen = onOpenResultOutput,
                        onShare = onShareResultOutput,
                        onLocate = onLocateResultOutput,
                        onRetry = onRetryResultOutput,
                    )
                } ?: MissingRouteState("تعذر تحميل نتيجة التصدير")

                ProductRoute.SETTINGS -> SettingsScreen(
                    presentation = settingsPresentation,
                    onBack = onBackFromSettings,
                    onAppearance = onOpenAppearanceSettings,
                    onAiCredential = onOpenAiCredentialSettings,
                    onDefaultStyle = onOpenDefaultStyleSettings,
                    onClearCache = onClearCacheSettings,
                    onClearProject = onClearProjectSettings,
                    onAboutDiagnostics = onOpenDiagnostics,
                )

                ProductRoute.DIAGNOSTICS -> DiagnosticsScreen(
                    presentation = diagnosticsPresentation,
                    onBack = onBackFromDiagnostics,
                    onToggleAdvanced = onToggleAdvancedDiagnostics,
                    onOpenLicenses = onOpenLicenses,
                )
            }
        }
    }
}

@Composable
private fun MissingRouteState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message)
    }
}
