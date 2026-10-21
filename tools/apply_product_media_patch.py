from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


path = Path("app/src/main/java/com/clw/aivideotranslator/app/ProductAppHostStyleBridge.kt")
text = path.read_text()

text = replace_once(
    text,
    """import android.content.Intent\nimport android.net.Uri\n""",
    """import android.content.Intent\nimport android.net.Uri\nimport com.clw.aivideotranslator.SnapshotBurnedExportSession\nimport com.clw.aivideotranslator.SnapshotBurnedSubtitleExporter\nimport com.clw.aivideotranslator.SubtitlePipeline\n""",
    "root media imports",
)

text = replace_once(
    text,
    """import com.clw.aivideotranslator.editor.UnitEditorTextState\n""",
    """import com.clw.aivideotranslator.editor.UnitEditorTextState\nimport com.clw.aivideotranslator.export.ExportEligibilityResolver\nimport com.clw.aivideotranslator.export.ExportFacts\nimport com.clw.aivideotranslator.export.ExportMode\nimport com.clw.aivideotranslator.export.ExportResultTruth\nimport com.clw.aivideotranslator.export.OutputKind\nimport com.clw.aivideotranslator.export.OutputSettlement\nimport com.clw.aivideotranslator.export.PersistedProductOutput\nimport com.clw.aivideotranslator.export.ProductOutputNames\nimport com.clw.aivideotranslator.export.ProductOutputStore\n""",
    "export imports",
)

text = replace_once(
    text,
    """import com.clw.aivideotranslator.ui.editor.TimingRuntimeResolver\n""",
    """import com.clw.aivideotranslator.ui.editor.TimingRuntimeResolver\nimport com.clw.aivideotranslator.ui.export.ExportCenterPresentation\nimport com.clw.aivideotranslator.ui.export.ExportProgressPresentation\nimport com.clw.aivideotranslator.ui.export.ExportProgressStage\nimport com.clw.aivideotranslator.ui.export.ResultPresentation\nimport com.clw.aivideotranslator.ui.preview.FullscreenPreviewPresentation\n""",
    "presentation imports",
)

text = replace_once(
    text,
    """    var cueSelection by remember { mutableStateOf(cueNavigator.reconcile(emptyList())) }\n""",
    """    var cueSelection by remember { mutableStateOf(cueNavigator.reconcile(emptyList())) }\n    var productPresentationBundle by remember { mutableStateOf<ProductPresentationBundle?>(null) }\n    var presentationBuildError by remember { mutableStateOf<String?>(null) }\n    var exportMode by remember { mutableStateOf(ExportMode.BOTH) }\n    var exportBaseName by remember { mutableStateOf(\"translated_video\") }\n    var exportProgress by remember { mutableStateOf<ExportProgressPresentation?>(null) }\n    var srtOutput by remember { mutableStateOf<PersistedProductOutput?>(null) }\n    var mp4Output by remember { mutableStateOf<PersistedProductOutput?>(null) }\n    var srtSettlement by remember { mutableStateOf(OutputSettlement.NOT_REQUESTED) }\n    var mp4Settlement by remember { mutableStateOf(OutputSettlement.NOT_REQUESTED) }\n    var activeExportSession by remember { mutableStateOf<SnapshotBurnedExportSession?>(null) }\n""",
    "product media state",
)

text = replace_once(
    text,
    """        activeVideoMetadata = if (\n            durableSource.phase == DurableSourcePhase.BOUND &&\n            !contentUri.isNullOrBlank()\n        ) {\n            withContext(Dispatchers.IO) {\n                runCatching { Uri.parse(contentUri) }\n                    .getOrNull()\n                    ?.let { uri -> VideoProbe.read(context, uri).getOrNull() }\n            }\n        } else {\n            null\n        }\n""",
    """        activeVideoMetadata = if (\n            durableSource.phase == DurableSourcePhase.BOUND &&\n            !contentUri.isNullOrBlank()\n        ) {\n            withContext(Dispatchers.IO) {\n                runCatching { Uri.parse(contentUri) }\n                    .getOrNull()\n                    ?.let { uri -> VideoProbe.read(context, uri).getOrNull() }\n            }\n        } else {\n            null\n        }\n        activeVideoMetadata?.displayName\n            ?.substringBeforeLast('.', missingDelimiterValue = activeVideoMetadata?.displayName.orEmpty())\n            ?.let(ProductOutputNames::base)\n            ?.takeIf(String::isNotBlank)\n            ?.let { exportBaseName = it }\n        productPresentationBundle = null\n        presentationBuildError = null\n""",
    "derive safe export base name",
)

text = replace_once(
    text,
    """            durableStt.phase == DurableSttPhase.LIVE_SUCCESS &&\n            durableTranslation.phase == DurableTranslationPhase.IDLE &&\n""",
    """            (durableStt.phase == DurableSttPhase.LIVE_SUCCESS ||\n                (durableStt.phase == DurableSttPhase.RECOVERED && durableStt.legacyResult != null)) &&\n            durableTranslation.phase == DurableTranslationPhase.IDLE &&\n""",
    "auto translate timed recovered STT",
)

text = replace_once(
    text,
    """    val sourceReview = SourceReviewPresentation.from(candidate.state, candidate.metadata)\n""",
    """    val fullscreenPreviewPresentation = productPresentationBundle?.let { bundle ->\n        FullscreenPreviewPresentation(\n            sourceUri = bundle.sourceUri,\n            rasterSnapshot = bundle.rasterSnapshot,\n            durationMs = bundle.durationMs,\n        )\n    }\n    val exportLayoutStatus = productPresentationBundle?.layoutStatus ?: LayoutStatus.REVIEW_REQUIRED\n    val exportEligibility = ExportEligibilityResolver.resolve(\n        ExportFacts(\n            hasCommittedText = productPresentationBundle?.cues?.isNotEmpty() == true,\n            timingValid = productPresentationBundle != null,\n            sourceAvailable = durableSource.phase == DurableSourcePhase.BOUND,\n            layoutStatus = exportLayoutStatus,\n        )\n    )\n    val exportCenterPresentation = productPresentationBundle?.let {\n        ExportCenterPresentation(\n            mode = exportMode,\n            eligibility = exportEligibility,\n            layoutStatus = exportLayoutStatus,\n            baseFileName = exportBaseName,\n        )\n    }\n    val resultPresentation = if (\n        srtSettlement != OutputSettlement.NOT_REQUESTED || mp4Settlement != OutputSettlement.NOT_REQUESTED\n    ) {\n        ResultPresentation(\n            truth = ExportResultTruth(srt = srtSettlement, mp4 = mp4Settlement),\n            srtFileName = srtOutput?.displayName,\n            mp4FileName = mp4Output?.displayName,\n        )\n    } else null\n\n    val sourceReview = SourceReviewPresentation.from(candidate.state, candidate.metadata)\n""",
    "derive product preview and export presentations",
)

text = replace_once(
    text,
    """    fun startFreshProviderRecovery(attempt: ProviderRecoveryFreshAttempt) {\n        val transition = attempt.confirm()\n        providerRecoveryAttempt = transition.state\n        val action = transition.action\n        if (action is ProviderRecoveryFreshAttemptAction.StartFreshSession) {\n            route = ProductRoute.PROCESSING\n            sessionViewModel.selectNewSource(action.sourceUri)\n        }\n    }\n\n    AppRoot(\n""",
    """    fun startFreshProviderRecovery(attempt: ProviderRecoveryFreshAttempt) {\n        val transition = attempt.confirm()\n        providerRecoveryAttempt = transition.state\n        val action = transition.action\n        if (action is ProviderRecoveryFreshAttemptAction.StartFreshSession) {\n            route = ProductRoute.PROCESSING\n            sessionViewModel.selectNewSource(action.sourceUri)\n        }\n    }\n\n    fun prepareCommittedBundleAndNavigate(event: ProductNavigationEvent) {\n        val sessionId = activeSessionId ?: return\n        val sourceUri = durableSource.contentUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return\n        val metadata = activeVideoMetadata ?: return\n        val units = durableTranslation.liveUnits ?: return\n        val entries = durableTranslation.entries\n        if (units.isEmpty() || entries.isEmpty()) return\n        scope.launch {\n            val built = withContext(Dispatchers.IO) {\n                runCatching {\n                    ProductPresentationBundleFactory.build(\n                        context = context,\n                        sourceUri = sourceUri,\n                        metadata = metadata,\n                        units = units,\n                        entries = entries,\n                        textState = editorTextStore.read(sessionId),\n                        timingState = displayTimingStore.read(sessionId),\n                        styleState = subtitleStyleStore.read(sessionId),\n                    ).getOrThrow()\n                }\n            }\n            built.onSuccess { bundle ->\n                productPresentationBundle = bundle\n                presentationBuildError = null\n                navigate(event)\n            }.onFailure { error ->\n                productPresentationBundle = null\n                presentationBuildError = error.message ?: \"تعذر تجهيز المعاينة/التصدير من الحالة الملتزمة\"\n            }\n        }\n    }\n\n    fun persistedOutput(kind: OutputKind): PersistedProductOutput? = when (kind) {\n        OutputKind.SRT -> srtOutput\n        OutputKind.MP4 -> mp4Output\n    }\n\n    fun outputMime(kind: OutputKind): String = when (kind) {\n        OutputKind.SRT -> \"application/x-subrip\"\n        OutputKind.MP4 -> \"video/mp4\"\n    }\n\n    fun openOutput(kind: OutputKind) {\n        val output = persistedOutput(kind) ?: return\n        runCatching {\n            context.startActivity(\n                Intent(Intent.ACTION_VIEW)\n                    .setDataAndType(output.uri, outputMime(kind))\n                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n            )\n        }\n    }\n\n    fun shareOutput(kind: OutputKind) {\n        val output = persistedOutput(kind) ?: return\n        runCatching {\n            val share = Intent(Intent.ACTION_SEND)\n                .setType(outputMime(kind))\n                .putExtra(Intent.EXTRA_STREAM, output.uri)\n                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n            context.startActivity(Intent.createChooser(share, \"مشاركة الملف\"))\n        }\n    }\n\n    fun settleExport() {\n        exportProgress = null\n        activeExportSession = null\n        navigate(ProductNavigationEvent.ExportSettled)\n    }\n\n    fun startProductExport(retryKind: OutputKind? = null) {\n        val bundle = productPresentationBundle ?: return\n        val wantsSrt = retryKind == OutputKind.SRT ||\n            (retryKind == null && exportMode != ExportMode.MP4_ONLY)\n        val wantsMp4 = retryKind == OutputKind.MP4 ||\n            (retryKind == null && exportMode != ExportMode.SRT_ONLY)\n        if (!wantsSrt && !wantsMp4) return\n\n        if (retryKind == null) {\n            if (wantsSrt) { srtSettlement = OutputSettlement.NOT_REQUESTED; srtOutput = null }\n            if (wantsMp4) { mp4Settlement = OutputSettlement.NOT_REQUESTED; mp4Output = null }\n        }\n        navigate(ProductNavigationEvent.ExportStarted)\n        exportProgress = ExportProgressPresentation(\n            stage = ExportProgressStage.PREPARING_RASTERS,\n            srtAlreadyValidated = srtSettlement == OutputSettlement.SUCCESS,\n        )\n\n        scope.launch {\n            if (wantsSrt) {\n                val srtResult = withContext(Dispatchers.IO) {\n                    runCatching {\n                        ProductOutputStore.saveSrt(\n                            context = context,\n                            baseName = exportBaseName,\n                            srtUtf8 = SubtitlePipeline.srt(bundle.cues, bundle.durationMs),\n                        )\n                    }\n                }\n                srtResult.onSuccess { output ->\n                    srtOutput = output\n                    srtSettlement = OutputSettlement.SUCCESS\n                }.onFailure {\n                    srtSettlement = OutputSettlement.FAILED\n                }\n            }\n\n            if (!wantsMp4) {\n                settleExport()\n                return@launch\n            }\n\n            exportProgress = ExportProgressPresentation(\n                stage = ExportProgressStage.RENDERING,\n                srtAlreadyValidated = srtSettlement == OutputSettlement.SUCCESS,\n                cancelAvailable = true,\n            )\n            val started = runCatching {\n                SnapshotBurnedSubtitleExporter.start(\n                    context = context,\n                    sourceUri = bundle.sourceUri,\n                    snapshot = bundle.rasterSnapshot,\n                    sampleStartMs = 0L,\n                    sampleEndMs = bundle.durationMs,\n                    onCompleted = { burned ->\n                        activeExportSession = null\n                        exportProgress = ExportProgressPresentation(\n                            stage = ExportProgressStage.FINALIZING,\n                            srtAlreadyValidated = srtSettlement == OutputSettlement.SUCCESS,\n                        )\n                        scope.launch {\n                            val persisted = withContext(Dispatchers.IO) {\n                                runCatching { ProductOutputStore.saveValidatedMp4(context, exportBaseName, burned.file) }\n                            }\n                            runCatching { burned.file.delete() }\n                            persisted.onSuccess { output ->\n                                mp4Output = output\n                                mp4Settlement = OutputSettlement.SUCCESS\n                            }.onFailure {\n                                mp4Settlement = OutputSettlement.FAILED\n                            }\n                            settleExport()\n                        }\n                    },\n                    onError = {\n                        activeExportSession = null\n                        mp4Settlement = OutputSettlement.FAILED\n                        settleExport()\n                    },\n                )\n            }\n            started.onSuccess { session -> activeExportSession = session }\n                .onFailure {\n                    mp4Settlement = OutputSettlement.FAILED\n                    settleExport()\n                }\n        }\n    }\n\n    AppRoot(\n""",
    "wire bundle/export operations",
)

text = replace_once(
    text,
    """        onOpenFullscreenPreview = { navigate(ProductNavigationEvent.OpenFullscreenPreview) },\n        onOpenExport = { navigate(ProductNavigationEvent.OpenExportCenter) },\n""",
    """        onOpenFullscreenPreview = {\n            prepareCommittedBundleAndNavigate(ProductNavigationEvent.OpenFullscreenPreview)\n        },\n        onOpenExport = {\n            prepareCommittedBundleAndNavigate(ProductNavigationEvent.OpenExportCenter)\n        },\n        onExportSrtOnly = {\n            exportMode = ExportMode.SRT_ONLY\n            prepareCommittedBundleAndNavigate(ProductNavigationEvent.OpenExportCenter)\n        },\n""",
    "route preview/export through frozen bundle",
)

text = replace_once(
    text,
    """        onIncreaseMaxLines = {\n            updateStyleDraft { style -> style.copy(maxLines = minOf(4, style.maxLines + 1)) }\n        },\n""",
    """        onIncreaseMaxLines = {\n            updateStyleDraft { style -> style.copy(maxLines = minOf(2, style.maxLines + 1)) }\n        },\n""",
    "enforce two-line product contract",
)

text = replace_once(
    text,
    """        onCancelStyleDraft = {\n            styleDraft = committedStyle\n            styleDirty = false\n        },\n        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },\n""",
    """        onCancelStyleDraft = {\n            styleDraft = committedStyle\n            styleDirty = false\n        },\n        fullscreenPreviewPresentation = fullscreenPreviewPresentation,\n        onBackFromFullscreenPreview = { navigate(ProductNavigationEvent.BackFromFullscreenPreview) },\n        exportCenterPresentation = exportCenterPresentation,\n        onExportModeChange = { exportMode = it },\n        onEditExportFilename = {},\n        onStartExport = { startProductExport() },\n        exportProgressPresentation = exportProgress,\n        onBackFromExportProgress = {\n            if (activeExportSession == null) navigate(ProductNavigationEvent.OpenExportCenter)\n        },\n        onRequestExportCancel = {\n            activeExportSession?.cancel()\n            activeExportSession = null\n            if (mp4Settlement != OutputSettlement.SUCCESS) mp4Settlement = OutputSettlement.CANCELLED\n            settleExport()\n        },\n        onRetryFailedOutput = {\n            val retry = ExportResultTruth(srtSettlement, mp4Settlement).retryableOutputs().firstOrNull()\n            if (retry != null) startProductExport(retry)\n        },\n        resultPresentation = resultPresentation,\n        onBackHomeFromResult = { navigate(ProductNavigationEvent.BackHomeFromResult) },\n        onOpenResultOutput = ::openOutput,\n        onShareResultOutput = ::shareOutput,\n        onLocateResultOutput = ::openOutput,\n        onRetryResultOutput = { startProductExport(it) },\n        onBackFromSettings = { navigate(ProductNavigationEvent.BackHomeFromSecondary) },\n""",
    "supply preview/export/result surfaces",
)

path.write_text(text)
