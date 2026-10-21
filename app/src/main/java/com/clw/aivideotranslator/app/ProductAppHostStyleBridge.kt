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
import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.VideoProbe
import com.clw.aivideotranslator.credential.AndroidAiCredentialStore
import com.clw.aivideotranslator.credential.CredentialStatus
import com.clw.aivideotranslator.credential.StoredCredential
import com.clw.aivideotranslator.editor.DisplayTimingStateStore
import com.clw.aivideotranslator.editor.DurableDisplayTimingState
import com.clw.aivideotranslator.editor.DurableSubtitleStyleState
import com.clw.aivideotranslator.editor.EditorTextStateStore
import com.clw.aivideotranslator.editor.SubtitleStyleDefaults
import com.clw.aivideotranslator.editor.SubtitleStyleStateStore
import com.clw.aivideotranslator.editor.UnitEditorTextState
import com.clw.aivideotranslator.presentation.BuiltInStylePreset
import com.clw.aivideotranslator.presentation.BuiltInSubtitleStyleResolver
import com.clw.aivideotranslator.presentation.CueStyleOverride
import com.clw.aivideotranslator.presentation.CueStyleOverrideDiff
import com.clw.aivideotranslator.presentation.LayoutStatus
import com.clw.aivideotranslator.presentation.StyleScope
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.presentation.SubtitleStyle
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
import com.clw.aivideotranslator.ui.editor.StyleStudioPresentation
import com.clw.aivideotranslator.ui.editor.TimingEditorPresentation
import com.clw.aivideotranslator.ui.editor.TimingEditorProjection
import com.clw.aivideotranslator.ui.editor.TimingRuntimeResolver
import com.clw.aivideotranslator.ui.processing.ProcessingPresentation
import com.clw.aivideotranslator.ui.processing.ProcessingPresentationResolver
import com.clw.aivideotranslator.ui.processing.ProcessingResolution
import com.clw.aivideotranslator.ui.processing.ProcessingStage
import com.clw.aivideotranslator.ui.source.SourceReviewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRODUCT_TIMING_NUDGE_US = 100_000L

/**
 * Product host with durable credential, text, timing and committed style ownership. Pending style
 * edits stay UI-local and never become preview/export truth until Apply persists them.
 */
@Composable
internal fun ProductAppHost(
    sessionViewModel: TranslationSessionViewModel,
    sourceSelectionViewModel: SourceSelectionViewModel,
    editorTextStore: EditorTextStateStore,
    displayTimingStore: DisplayTimingStateStore,
    subtitleStyleStore: SubtitleStyleStateStore,
    credentialStore: AndroidAiCredentialStore,
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
    var credentialRecord by remember { mutableStateOf<StoredCredential?>(null) }
    var credentialLoaded by remember { mutableStateOf(false) }
    var providerRecoveryAttempt by remember { mutableStateOf<ProviderRecoveryFreshAttempt?>(null) }
    var recoveryAfterCredentialSetup by remember { mutableStateOf(false) }
    var editorUnitState by remember { mutableStateOf(UnitEditorTextState()) }
    var displayTimingState by remember { mutableStateOf<DurableDisplayTimingState?>(null) }
    var subtitleStyleState by remember { mutableStateOf<DurableSubtitleStyleState?>(null) }
    var styleScope by remember { mutableStateOf(StyleScope.PROJECT) }
    var styleDraft by remember { mutableStateOf<SubtitleStyle?>(null) }
    var styleDirty by remember { mutableStateOf(false) }
    var activeVideoMetadata by remember { mutableStateOf<VideoMetadata?>(null) }
    var cueSelection by remember { mutableStateOf(cueNavigator.reconcile(emptyList())) }

    val providerApiKey = credentialRecord
        ?.takeIf { it.status != CredentialStatus.REJECTED }
        ?.apiKey
        .orEmpty()

    fun navigate(event: ProductNavigationEvent) {
        navigator.onEvent(event)
        route = navigator.route
    }

    LaunchedEffect(credentialStore) {
        credentialRecord = withContext(Dispatchers.IO) {
            runCatching { credentialStore.read() }.getOrNull()
        }
        credentialLoaded = true
    }

    LaunchedEffect(durableSource.phase, durableSource.contentUri) {
        val contentUri = durableSource.contentUri
        activeVideoMetadata = if (
            durableSource.phase == DurableSourcePhase.BOUND &&
            !contentUri.isNullOrBlank()
        ) {
            withContext(Dispatchers.IO) {
                runCatching { Uri.parse(contentUri) }
                    .getOrNull()
                    ?.let { uri -> VideoProbe.read(context, uri).getOrNull() }
            }
        } else {
            null
        }
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
        credentialLoaded,
        providerRecoveryAttempt,
    ) {
        if (!credentialLoaded) return@LaunchedEffect
        when (sourceSelectionViewModel.reconcileDurable(durableSource)) {
            CandidateActivationOutcome.ACTIVATED -> {
                if (providerRecoveryAttempt == null) {
                    navigate(ProductNavigationEvent.SourceActivated(needsAiSetup = providerApiKey.isBlank()))
                }
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
        credentialUsable = providerApiKey.isNotBlank(),
    )

    LaunchedEffect(runtimeFacts.reopenFacts, route) {
        route = ProductRouteSynchronizer.resolve(route, runtimeFacts.reopenFacts)
    }

    val processingResolution = ProcessingPresentationResolver.resolve(durableStt, durableTranslation)
    LaunchedEffect(
        processingResolution,
        durableStt.sessionId,
        durableSource.contentUri,
        providerApiKey,
    ) {
        if (processingResolution == ProcessingResolution.ProviderRecovery) {
            providerRecoveryAttempt = ProviderRecoveryFreshAttempt.armOrNull(
                priorSessionId = durableStt.sessionId,
                sourceUri = durableSource.contentUri,
                apiKey = providerApiKey,
            )
            route = ProductRoute.PROVIDER_RECOVERY
        }
    }

    LaunchedEffect(
        durableSource.phase,
        durableSource.sessionId,
        providerRecoveryAttempt,
    ) {
        if (durableSource.phase != DurableSourcePhase.BOUND) return@LaunchedEffect
        val attempt = providerRecoveryAttempt ?: return@LaunchedEffect
        val transition = attempt.onBoundSession(durableSource.sessionId)
        providerRecoveryAttempt = transition.state
        when (val action = transition.action) {
            is ProviderRecoveryFreshAttemptAction.SubmitStt -> {
                route = ProductRoute.PROCESSING
                sessionViewModel.runStt(action.apiKey)
            }
            is ProviderRecoveryFreshAttemptAction.StartFreshSession,
            null,
            -> Unit
        }
    }

    LaunchedEffect(durableStt.phase, credentialRecord?.status) {
        if (
            durableStt.phase == DurableSttPhase.LIVE_SUCCESS &&
            credentialRecord != null &&
            credentialRecord?.status != CredentialStatus.USABLE
        ) {
            val refreshed = withContext(Dispatchers.IO) {
                runCatching {
                    credentialStore.markUsable()
                    credentialStore.read()
                }.getOrNull()
            }
            if (refreshed != null) credentialRecord = refreshed
        }
    }

    LaunchedEffect(durableStt.phase, durableTranslation.phase, providerApiKey) {
        if (
            durableStt.phase == DurableSttPhase.LIVE_SUCCESS &&
            durableTranslation.phase == DurableTranslationPhase.IDLE &&
            providerApiKey.isNotBlank()
        ) {
            sessionViewModel.runTranslation(providerApiKey)
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

    LaunchedEffect(activeSessionId) {
        displayTimingState = activeSessionId?.let { sessionId ->
            withContext(Dispatchers.IO) {
                runCatching { displayTimingStore.read(sessionId) }.getOrNull()
            }
        }
        subtitleStyleState = activeSessionId?.let { sessionId ->
            withContext(Dispatchers.IO) {
                runCatching { subtitleStyleStore.read(sessionId) }.getOrNull()
            }
        }
        styleDirty = false
    }

    val committedStyle = when (styleScope) {
        StyleScope.PROJECT -> subtitleStyleState?.projectStyle ?: SubtitleStyleDefaults.PROJECT
        StyleScope.CURRENT_CUE -> cueSelection.unitId?.let { cueId ->
            subtitleStyleState?.effective(cueId)
        } ?: subtitleStyleState?.projectStyle ?: SubtitleStyleDefaults.PROJECT
    }

    LaunchedEffect(
        activeSessionId,
        cueSelection.unitId,
        styleScope,
        subtitleStyleState,
    ) {
        if (!styleDirty) styleDraft = committedStyle
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

    val timingRuntime = cueSelection.unitId?.let { selectedUnitId ->
        TimingRuntimeResolver.resolve(
            units = durableTranslation.liveUnits.orEmpty(),
            selectedUnitId = selectedUnitId,
            states = displayTimingState?.units.orEmpty(),
        )
    }
    val videoDurationUs = activeVideoMetadata?.durationMs?.let { durationMs ->
        runCatching { Math.multiplyExact(durationMs, 1_000L) }.getOrNull()
    }
    val timingEditorPresentation: TimingEditorPresentation? = timingRuntime?.let { runtime ->
        TimingEditorProjection.resolve(
            sourceInterval = runtime.sourceInterval,
            state = runtime.selectedState,
            videoDurationUs = videoDurationUs,
            previousEffectiveEndUs = runtime.previousEffectiveEndUs,
            nextEffectiveStartUs = runtime.nextEffectiveStartUs,
            sourceAvailable = durableSource.phase == DurableSourcePhase.BOUND,
        )
    }

    val styleStudioPresentation = activeEntry?.let {
        StyleStudioPresentation(
            scope = styleScope,
            style = styleDraft ?: committedStyle,
            layoutStatus = if (durableSource.phase == DurableSourcePhase.BOUND) {
                LayoutStatus.REVIEW_REQUIRED
            } else {
                LayoutStatus.REVIEW_REQUIRED
            },
            dirty = styleDirty,
        )
    }

    val sourceReview = SourceReviewPresentation.from(candidate.state, candidate.metadata)
    val processingPresentation = when (processingResolution) {
        is ProcessingResolution.Show -> processingResolution.presentation
        ProcessingResolution.ProviderRecovery -> ProcessingPresentation(stage = ProcessingStage.STT)
    }

    val credentialStatus = when {
        durableStt.phase == DurableSttPhase.LIVE_SUCCESS ||
            durableTranslation.phase == DurableTranslationPhase.LIVE_SUCCESS -> AiCredentialStatus.USABLE
        credentialRecord?.status == CredentialStatus.USABLE -> AiCredentialStatus.USABLE
        credentialRecord?.status == CredentialStatus.REJECTED -> AiCredentialStatus.REJECTED
        credentialRecord?.status == CredentialStatus.UNVERIFIED -> AiCredentialStatus.UNVERIFIED
        else -> AiCredentialStatus.MISSING
    }

    fun adoptCandidate() {
        val request = sourceSelectionViewModel.requestActivation() ?: return
        val uri = runCatching { Uri.parse(request.contentUri) }.getOrNull()
        if (uri == null) {
            sourceSelectionViewModel.failActivation(request.ticket, "تعذر قراءة عنوان الفيديو المرشح.")
            return
        }
        val permission = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        scope.launch { transform()?.let { editorUnitState = it } }
    }

    fun mutateTimingState(transform: suspend () -> DurableDisplayTimingState?) {
        scope.launch { transform()?.let { displayTimingState = it } }
    }

    fun setTimingFields(startText: String, endText: String) {
        val sessionId = activeSessionId ?: return
        val unitId = activeEntry?.sourceUnitId ?: return
        mutateTimingState {
            withContext(Dispatchers.IO) {
                runCatching {
                    displayTimingStore.setPendingFieldDraft(sessionId, unitId, startText, endText)
                }.getOrNull()
            }
        }
    }

    fun nudgeTiming(startDeltaUs: Long = 0L, endDeltaUs: Long = 0L) {
        val presentation = timingEditorPresentation ?: return
        val currentStart = TimingEditorProjection.parseTimecodeUs(presentation.startField) ?: return
        val currentEnd = TimingEditorProjection.parseTimecodeUs(presentation.endField) ?: return
        val nextStart = runCatching { Math.addExact(currentStart, startDeltaUs) }.getOrNull() ?: return
        val nextEnd = runCatching { Math.addExact(currentEnd, endDeltaUs) }.getOrNull() ?: return
        if (nextStart < 0L || nextEnd < 0L) return
        setTimingFields(
            TimingEditorProjection.formatTimecodeUs(nextStart),
            TimingEditorProjection.formatTimecodeUs(nextEnd),
        )
    }

    fun updateStyleDraft(transform: (SubtitleStyle) -> SubtitleStyle) {
        val base = styleDraft ?: committedStyle
        styleDraft = transform(base)
        styleDirty = styleDraft != committedStyle
    }

    fun applyStyleDraft() {
        val sessionId = activeSessionId ?: return
        val draft = styleDraft ?: return
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching {
                    when (styleScope) {
                        StyleScope.PROJECT -> subtitleStyleStore.setProjectStyle(sessionId, draft)
                        StyleScope.CURRENT_CUE -> {
                            val cueId = cueSelection.unitId ?: return@runCatching null
                            val project = subtitleStyleState?.projectStyle ?: SubtitleStyleDefaults.PROJECT
                            val delta = CueStyleOverrideDiff.between(project, draft)
                            if (delta == CueStyleOverride()) {
                                subtitleStyleStore.clearCueOverride(sessionId, cueId)
                            } else {
                                subtitleStyleStore.setCueOverride(sessionId, cueId, delta)
                            }
                        }
                    }
                }.getOrNull()
            }
            if (next != null) {
                subtitleStyleState = next
                styleDirty = false
                styleDraft = when (styleScope) {
                    StyleScope.PROJECT -> next.projectStyle
                    StyleScope.CURRENT_CUE -> cueSelection.unitId?.let(next::effective) ?: next.projectStyle
                }
            }
        }
    }

    fun startFreshProviderRecovery(attempt: ProviderRecoveryFreshAttempt) {
        val transition = attempt.confirm()
        providerRecoveryAttempt = transition.state
        val action = transition.action
        if (action is ProviderRecoveryFreshAttemptAction.StartFreshSession) {
            route = ProductRoute.PROCESSING
            sessionViewModel.selectNewSource(action.sourceUri)
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
        onBackFromAiSetup = {
            if (recoveryAfterCredentialSetup) {
                recoveryAfterCredentialSetup = false
                route = ProductRoute.PROVIDER_RECOVERY
            } else {
                navigate(ProductNavigationEvent.BackFromAiSetup)
            }
        },
        onSaveAiKeyAndStart = { key ->
            val normalized = key.trim()
            if (normalized.isNotEmpty()) {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { credentialStore.save(normalized) }.getOrNull()
                    }
                    if (saved != null) {
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
                }
            }
        },
        processingPresentation = processingPresentation,
        onBackFromProcessing = { route = ProductRoute.HOME },
        onKeepProviderStateAndReturn = {
            providerRecoveryAttempt = null
            recoveryAfterCredentialSetup = false
            navigate(ProductNavigationEvent.KeepProviderStateAndReturn)
        },
        onConfirmFreshProviderAttempt = {
            val attempt = providerRecoveryAttempt
            if (attempt != null) {
                startFreshProviderRecovery(attempt)
            } else if (
                durableStt.phase == DurableSttPhase.UNKNOWN_REMOTE_OUTCOME &&
                !durableSource.contentUri.isNullOrBlank()
            ) {
                recoveryAfterCredentialSetup = true
                route = ProductRoute.AI_SETUP
            }
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
        onPreviousEditorCue = { cueSelection = cueNavigator.previous(unitIds) },
        onNextEditorCue = { cueSelection = cueNavigator.next(unitIds) },
        onOpenTextEditor = { navigate(ProductNavigationEvent.OpenTextEditor) },
        onOpenTimingEditor = { navigate(ProductNavigationEvent.OpenTimingEditor) },
        onOpenStyleEditor = {
            styleDirty = false
            styleDraft = committedStyle
            navigate(ProductNavigationEvent.OpenStyleStudio)
        },
        onOpenFullscreenPreview = { navigate(ProductNavigationEvent.OpenFullscreenPreview) },
        onOpenExport = { navigate(ProductNavigationEvent.OpenExportCenter) },
        timingEditorPresentation = timingEditorPresentation,
        onTimingStartChange = { startText ->
            timingEditorPresentation?.let { setTimingFields(startText, it.endField) }
        },
        onTimingEndChange = { endText ->
            timingEditorPresentation?.let { setTimingFields(it.startField, endText) }
        },
        onNudgeStartBackward = { nudgeTiming(startDeltaUs = -PRODUCT_TIMING_NUDGE_US) },
        onNudgeStartForward = { nudgeTiming(startDeltaUs = PRODUCT_TIMING_NUDGE_US) },
        onNudgeEndBackward = { nudgeTiming(endDeltaUs = -PRODUCT_TIMING_NUDGE_US) },
        onNudgeEndForward = { nudgeTiming(endDeltaUs = PRODUCT_TIMING_NUDGE_US) },
        onResetTimingToSource = {
            val sessionId = activeSessionId
            val unitId = activeEntry?.sourceUnitId
            if (sessionId != null && unitId != null) {
                mutateTimingState {
                    withContext(Dispatchers.IO) {
                        runCatching { displayTimingStore.resetToSource(sessionId, unitId) }.getOrNull()
                    }
                }
            }
        },
        onCommitTiming = {
            val sessionId = activeSessionId
            val unitId = activeEntry?.sourceUnitId
            val runtime = timingRuntime
            val presentation = timingEditorPresentation
            if (
                sessionId != null && unitId != null && runtime != null &&
                presentation?.validation?.isValid == true
            ) {
                mutateTimingState {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            displayTimingStore.commitPendingFieldDraft(
                                sessionId = sessionId,
                                unitId = unitId,
                                sourceInterval = runtime.sourceInterval,
                                videoDurationUs = videoDurationUs,
                                previousEffectiveEndUs = runtime.previousEffectiveEndUs,
                                nextEffectiveStartUs = runtime.nextEffectiveStartUs,
                            )
                        }.getOrNull()
                    }
                }
            }
        },
        styleStudioPresentation = styleStudioPresentation,
        onStyleScopeChange = { nextScope ->
            styleScope = nextScope
            styleDirty = false
            styleDraft = when (nextScope) {
                StyleScope.PROJECT -> subtitleStyleState?.projectStyle ?: SubtitleStyleDefaults.PROJECT
                StyleScope.CURRENT_CUE -> cueSelection.unitId?.let { cueId ->
                    subtitleStyleState?.effective(cueId)
                } ?: subtitleStyleState?.projectStyle ?: SubtitleStyleDefaults.PROJECT
            }
        },
        onStylePreset = { preset: BuiltInStylePreset ->
            updateStyleDraft { BuiltInSubtitleStyleResolver.apply(preset, it) }
        },
        onFontSizeChange = { value ->
            updateStyleDraft { style ->
                style.copy(fontSizeSp = value, lineHeightSp = maxOf(style.lineHeightSp, value))
            }
        },
        onLineHeightChange = { value ->
            updateStyleDraft { style -> style.copy(lineHeightSp = maxOf(value, style.fontSizeSp)) }
        },
        onOpacityChange = { value ->
            updateStyleDraft { style -> style.copy(opacity = value.coerceIn(0.2f, 1f)) }
        },
        onAnchorChange = { anchor: SubtitleAnchor ->
            updateStyleDraft { style -> style.copy(anchor = anchor) }
        },
        onToggleOutline = {
            updateStyleDraft { style ->
                style.copy(outlineWidthDp = if (style.outlineWidthDp > 0f) 0f else 2f)
            }
        },
        onToggleShadow = {
            updateStyleDraft { style -> style.copy(shadowEnabled = !style.shadowEnabled) }
        },
        onToggleBackground = {
            updateStyleDraft { style -> style.copy(backgroundEnabled = !style.backgroundEnabled) }
        },
        onDecreaseMaxLines = {
            updateStyleDraft { style -> style.copy(maxLines = maxOf(1, style.maxLines - 1)) }
        },
        onIncreaseMaxLines = {
            updateStyleDraft { style -> style.copy(maxLines = minOf(4, style.maxLines + 1)) }
        },
        onApplyStyle = ::applyStyleDraft,
        onCancelStyleDraft = {
            styleDraft = committedStyle
            styleDirty = false
        },
        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
        onOpenDiagnostics = { navigate(ProductNavigationEvent.OpenDiagnostics) },
        onBackFromDiagnostics = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },
    )
}
