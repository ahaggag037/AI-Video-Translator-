package com.clw.aivideotranslator.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.clw.aivideotranslator.ui.ai.AiCredentialStatus
import com.clw.aivideotranslator.ui.ai.AiSetupScreen
import com.clw.aivideotranslator.ui.editor.EditorTextPresentation
import com.clw.aivideotranslator.ui.editor.EditorTextScreen
import com.clw.aivideotranslator.ui.home.HomeScreen
import com.clw.aivideotranslator.ui.home.HomeStatus
import com.clw.aivideotranslator.ui.processing.ProcessingPresentation
import com.clw.aivideotranslator.ui.processing.ProcessingScreen
import com.clw.aivideotranslator.ui.processing.ProcessingStage
import com.clw.aivideotranslator.ui.recovery.ProviderRecoveryScreen
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
    onEditorTargetChange: (String) -> Unit = {},
    onCommitEditorDraft: () -> Unit = {},
    onEditorUndo: () -> Unit = {},
    onEditorRedo: () -> Unit = {},
    onRestoreMachineTranslation: () -> Unit = {},
    onEditorPlayPause: () -> Unit = {},
    onOpenTimingEditor: () -> Unit = {},
    onOpenStyleEditor: () -> Unit = {},
    onOpenFullscreenPreview: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onExportSrtOnly: () -> Unit = {},
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

                ProductRoute.EDITOR_TEXT -> EditorTextScreen(
                    presentation = editorTextPresentation,
                    onBack = onBackFromEditor,
                    onTargetChange = onEditorTargetChange,
                    onCommitDraft = onCommitEditorDraft,
                    onUndo = onEditorUndo,
                    onRedo = onEditorRedo,
                    onRestoreMachine = onRestoreMachineTranslation,
                    onPlayPause = onEditorPlayPause,
                    onOpenTiming = onOpenTimingEditor,
                    onOpenStyle = onOpenStyleEditor,
                    onOpenFullscreen = onOpenFullscreenPreview,
                    onOpenExport = onOpenExport,
                    onExportSrtOnly = onExportSrtOnly,
                )
            }
        }
    }
}
