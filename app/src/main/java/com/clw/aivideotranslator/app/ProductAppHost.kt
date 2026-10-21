package com.clw.aivideotranslator.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.clw.aivideotranslator.VideoProbe
import com.clw.aivideotranslator.editor.EditorTextStateStore
import com.clw.aivideotranslator.editor.UnitEditorTextState
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.TranslationSessionViewModel
import com.clw.aivideotranslator.source.CandidateActivationOutcome
import com.clw.aivideotranslator.source.SourceSelectionViewModel
import com.clw.aivideotranslator.ui.ai.AiCredentialStatus
import com.clw.aivideotranslator.ui.editor.EditorCueNavigator
import com.clw.aivideotranslator.ui.editor.EditorTextPresentation
import com.clw.aivideotranslator.ui.editor.LegacyEditorTextProjection
import com.clw.aivideotranslator.ui.processing.ProcessingPresentation
import com.clw.aivideotranslator.ui.processing.ProcessingPresentationResolver
import com.clw.aivideotranslator.ui.processing.ProcessingResolution
import com.clw.aivideotranslator.ui.processing.ProcessingStage
import com.clw.aivideotranslator.ui.source.SourceReviewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Product-first Activity host for Path A. The durable ViewModels/stores stay authoritative; this
 * layer owns only transient navigation, the in-memory credential, and projections for Compose.
 */
@Composable
internal fun ProductAppHost(
    sessionViewModel: TranslationSessionViewModel,
    sourceSelectionViewModel: SourceSelectionViewModel,
    editorTextStore: EditorTextStateStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durableSource by sessionViewModel.durableSource.collectAsState()
    val durableStt by sessionViewModel.stt.collectAsState()
    val durableTranslation by sessionViewModel.translation.collectAsState()
    val candidate by sourceSelectionViewModel.snapshot.collectAsState()

    val navigator = remember { ProductNavigator() }
    val cueNavigator = remember { EditorCueNavigator() }
    var route by remember { mutableStateOf(navigator.route) }
    var apiKey by remember { mutableStateOf("") }
    var editorUnitState by remember { mutableStateOf(UnitEditorTextState()) }
    var cueSelection by remember { mutableStateOf(cueNavigator.reconcile(emptyList())) }

    fun navigate(event: ProductNavigationEvent) {
        navigator.onEvent(event)
        route = navigator.route
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ticket = sourceSelectionViewModel.select(uri.toString())
        scope.launch {
            val result = withContext(Dispatchers.IO) { VideoProbe.read(context, uri) }
            sourceSelectionViewModel.completeProbe(ticket, result)
        }
    }

    LaunchedEffect(
        durableSource.phase,
        durableSource.sessionId,
        durableSource.contentUri,
    ) {
        when (sourceSelectionViewModel.reconcileDurable(durableSource)) {
            CandidateActivationOutcome.ACTIVATED -> {
                navigate(ProductNavigationEvent.SourceActivated(needsAiSetup = apiKey.isBlank()))
            }
            CandidateActivationOutcome.FAILED,
            CandidateActivationOutcome.IGNORED,
            CandidateActivationOutcome.WAITING,
            -> Unit
        }
    }

    val runtimeFacts = ProductRuntimeFactsResolver.resolve(
        source = durableSource,
        stt = durableStt,
        translation = durableTranslation,
        credentialUsable = apiKey.isNotBlank(),
    )

    LaunchedEffect(runtimeFacts.reopenFacts, route) {
        route = ProductRouteSynchronizer.resolve(route, runtimeFacts.reopenFacts)
    }

    val processingResolution = ProcessingPresentationResolver.resolve(durableStt, durableTranslation)
    LaunchedEffect(processingResolution) {
        if (processingResolution == ProcessingResolution.ProviderRecovery) {
            route = ProductRoute.PROVIDER_RECOVERY
        }
    }

    LaunchedEffect(durableStt.phase, durableTranslation.phase, apiKey) {
        if (
            durableStt.phase == DurableSttPhase.LIVE_SUCCESS &&
            durableTranslation.phase == DurableTranslationPhase.IDLE &&
            apiKey.isNotBlank()
        ) {
            sessionViewModel.runTranslation(apiKey)
        }
    }

    val unitIds = durableTranslation.entries.map { it.sourceUnitId }
    LaunchedEffect(unitIds) {
        cueSelection = cueNavigator.reconcile(unitIds)
    }

    val activeEntry = cueSelection.unitId?.let { selectedId ->
        durableTranslation.entries.firstOrNull { it.sourceUnitId == selectedId }
    }
    val activeUnit = cueSelection.unitId?.let { selectedId ->
        durableTranslation.liveUnits?.firstOrNull { it.id == selectedId }
    }
    val activeSessionId = durableTranslation.sessionId ?: durableSource.sessionId

    LaunchedEffect(activeSessionId, activeEntry?.sourceUnitId) {
        val sessionId = activeSessionId ?: return@LaunchedEffect
        val unitId = activeEntry?.sourceUnitId ?: return@LaunchedEffect
        editorUnitState = withContext(Dispatchers.IO) {
            runCatching { editorTextStore.readUnit(sessionId, unitId) }
                .getOrDefault(UnitEditorTextState())
        }
    }

    val editorPresentation = if (activeEntry != null) {
        LegacyEditorTextProjection.resolve(
            sourceUnit = activeUnit,
            machineEntry = activeEntry,
            editorState = editorUnitState,
            sourceAvailable = durableSource.phase == DurableSourcePhase.BOUND,
            reviewRequired = durableTranslation.phase == DurableTranslationPhase.BLOCKED,
            cueIndex = cueSelection.index,
            cueCount = cueSelection.count.coerceAtLeast(1),
        )
    } else {
        EditorTextPresentation(
            sourceText = "",
            targetText = "",
            hasPendingDraft = false,
            reviewRequired = false,
            sourceMissing = durableSource.phase != DurableSourcePhase.BOUND,
            textEditingEnabled = false,
            playbackEnabled = false,
            fullscreenPreviewEnabled = false,
            timingEnabled = false,
            styleEnabled = false,
            srtExportEnabled = false,
            mp4ExportEnabled = false,
        )
    }

    val sourceReview = SourceReviewPresentation.from(candidate.state, candidate.metadata)
    val processingPresentation = when (processingResolution) {
        is ProcessingResolution.Show -> processingResolution.presentation
        ProcessingResolution.ProviderRecovery -> ProcessingPresentation(
            stage = ProcessingStage.STT,
        )
    }

    val credentialStatus = when {
        apiKey.isBlank() -> AiCredentialStatus.MISSING
        durableStt.phase == DurableSttPhase.LIVE_SUCCESS ||
            durableTranslation.phase == DurableTranslationPhase.LIVE_SUCCESS -> AiCredentialStatus.USABLE
        else -> AiCredentialStatus.UNVERIFIED
    }

    fun adoptCandidate() {
        val request = sourceSelectionViewModel.requestActivation() ?: return
        val uri = runCatching { Uri.parse(request.contentUri) }.getOrNull()
        if (uri == null) {
            sourceSelectionViewModel.failActivation(request.ticket, "تعذر قراءة عنوان الفيديو المرشح.")
            return
        }
        val permission = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (permission.isFailure) {
            sourceSelectionViewModel.failActivation(
                request.ticket,
                "تعذر تثبيت إذن قراءة الفيديو. اختر الفيديو مرة أخرى.",
            )
            return
        }
        sessionViewModel.selectNewSource(request.contentUri)
    }

    fun mutateEditorState(transform: suspend () -> UnitEditorTextState?) {
        scope.launch {
            transform()?.let { editorUnitState = it }
        }
    }

    AppRoot(
        route = route,
        homeStatus = runtimeFacts.homeStatus,
        sourceSelectionEnabled = durableSource.phase != DurableSourcePhase.CAPTURING &&
            durableStt.phase != DurableSttPhase.RUNNING &&
            durableTranslation.phase != DurableTranslationPhase.RUNNING,
        sourceReviewPresentation = sourceReview,
        onChooseVideo = {
            navigate(ProductNavigationEvent.ChooseVideo)
            picker.launch(arrayOf("video/*"))
        },
        onContinue = {
            route = ProductRouteSynchronizer.resolve(ProductRoute.HOME, runtimeFacts.reopenFacts)
        },
        onOpenSettings = { navigate(ProductNavigationEvent.OpenSettings) },
        onBackFromSourceReview = { navigate(ProductNavigationEvent.BackFromSourceReview) },
        onChangeVideo = { picker.launch(arrayOf("video/*")) },
        onAdoptVideo = ::adoptCandidate,
        aiCredentialStatus = credentialStatus,
        onBackFromAiSetup = { navigate(ProductNavigationEvent.BackFromAiSetup) },
        onSaveAiKeyAndStart = { key ->
            if (key.isNotBlank()) {
                apiKey = key
                navigate(ProductNavigationEvent.AiCredentialAccepted)
                sessionViewModel.runStt(key)
            }
        },
        processingPresentation = processingPresentation,
        onBackFromProcessing = { route = ProductRoute.HOME },
        onKeepProviderStateAndReturn = {
            navigate(ProductNavigationEvent.KeepProviderStateAndReturn)
        },
        editorTextPresentation = editorPresentation,
        onBackFromEditor = { navigate(ProductNavigationEvent.BackFromEditor) },
        onEditorTargetChange = { text ->
            val sessionId = activeSessionId
            val entry = activeEntry
            if (sessionId != null && entry != null) {
                mutateEditorState {
                    withContext(Dispatchers.IO) {
                        editorTextStore.setPendingDraft(
                            sessionId = sessionId,
                            unitId = entry.sourceUnitId,
                            machineText = entry.translatedText,
                            draftText = text,
                        ).units[entry.sourceUnitId]
                    }
                }
            }
        },
        onCommitEditorDraft = {
            val sessionId = activeSessionId
            val entry = activeEntry
            val sourceUnit = activeUnit
            if (sessionId != null && entry != null && sourceUnit != null) {
                mutateEditorState {
                    withContext(Dispatchers.IO) {
                        editorTextStore.commitPendingDraft(
                            sessionId = sessionId,
                            unitId = entry.sourceUnitId,
                            sourceText = sourceUnit.sourceText,
                            machineText = entry.translatedText,
                        ).units[entry.sourceUnitId]
                    }
                }
            }
        },
        onEditorUndo = {
            val sessionId = activeSessionId
            val entry = activeEntry
            if (sessionId != null && entry != null) {
                mutateEditorState {
                    withContext(Dispatchers.IO) {
                        editorTextStore.undo(sessionId, entry.sourceUnitId)
                        editorTextStore.readUnit(sessionId, entry.sourceUnitId)
                    }
                }
            }
        },
        onEditorRedo = {
            val sessionId = activeSessionId
            val entry = activeEntry
            if (sessionId != null && entry != null) {
                mutateEditorState {
                    withContext(Dispatchers.IO) {
                        editorTextStore.redo(sessionId, entry.sourceUnitId)
                        editorTextStore.readUnit(sessionId, entry.sourceUnitId)
                    }
                }
            }
        },
        onRestoreMachineTranslation = {
            val sessionId = activeSessionId
            val entry = activeEntry
            if (sessionId != null && entry != null) {
                mutateEditorState {
                    withContext(Dispatchers.IO) {
                        editorTextStore.restoreMachine(sessionId, entry.sourceUnitId)
                            .units[entry.sourceUnitId]
                            ?: UnitEditorTextState()
                    }
                }
            }
        },
        onPreviousEditorCue = {
            cueSelection = cueNavigator.previous(unitIds)
        },
        onNextEditorCue = {
            cueSelection = cueNavigator.next(unitIds)
        },
        onOpenTextEditor = { navigate(ProductNavigationEvent.OpenTextEditor) },
        onOpenTimingEditor = { navigate(ProductNavigationEvent.OpenTimingEditor) },
        onOpenStyleEditor = { navigate(ProductNavigationEvent.OpenStyleStudio) },
        onOpenFullscreenPreview = { navigate(ProductNavigationEvent.OpenFullscreenPreview) },
        onOpenExport = { navigate(ProductNavigationEvent.OpenExportCenter) },
        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
        onOpenDiagnostics = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        onBackFromDiagnostics = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
    )
}
