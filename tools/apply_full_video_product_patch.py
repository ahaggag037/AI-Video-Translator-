from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


vm_path = Path("app/src/main/java/com/clw/aivideotranslator/session/TranslationSessionViewModel.kt")
vm = vm_path.read_text()

vm = replace_once(
    vm,
    """            DurableSttPhase.RECOVERED -> require(sessionId != null && !transcript.isNullOrBlank() && legacyResult == null && failure == null)\n""",
    """            DurableSttPhase.RECOVERED -> require(\n                sessionId != null && !transcript.isNullOrBlank() &&\n                    (legacyResult == null || transcript == legacyResult.transcript) && failure == null\n            )\n""",
    "allow timed durable STT recovery",
)

vm = replace_once(
    vm,
    """internal data class DurableTranslationExecution(\n""",
    """internal fun interface FullVideoSttRunner {\n    fun run(\n        context: Context,\n        store: TranslationSessionStore,\n        sessionId: String,\n        apiKey: String,\n    ): Result<FullVideoSttOperationResult>\n}\n\nprivate data class DurableSttRunOutcome(\n    val result: NvidiaSttResult? = null,\n    val transcriptOnly: String? = null,\n    val recoveredWithoutNetwork: Boolean = false,\n) {\n    init {\n        require((result != null) xor (transcriptOnly != null)) { \"STT outcome must contain timed result or transcript-only recovery\" }\n    }\n}\n\ninternal data class DurableTranslationExecution(\n""",
    "add full-video runner boundary",
)

vm = replace_once(
    vm,
    """    private val planStore: TranslationRequestPlanStore? = null,\n""",
    """    private val fullVideoSttRunner: FullVideoSttRunner? = null,\n    private val fullVideoSttAggregateStore: FullVideoSttAggregateStore? = null,\n    private val fullVideoSttWindowJournalStore: FullVideoSttWindowJournalStore? = null,\n    private val planStore: TranslationRequestPlanStore? = null,\n""",
    "inject full-video stores",
)

vm = replace_once(
    vm,
    """                        val result = sttRunner.run(appContext, store, sessionId, apiKey).getOrThrow()\n                        requireCurrentSourceGeneration(generation)\n                        check(activeSessionOwner.readActiveSessionId() == sessionId) { \"active session changed after STT\" }\n                        val snapshot = store.readActiveSourceSnapshot(sessionId)\n                        Pair(result, snapshot)\n""",
    """                        val fullRunner = fullVideoSttRunner\n                        val execution = if (fullRunner != null) {\n                            val full = fullRunner.run(appContext, store, sessionId, apiKey).getOrThrow()\n                            DurableSttRunOutcome(\n                                result = full.result,\n                                recoveredWithoutNetwork = full.recoveredWithoutNetwork,\n                            )\n                        } else {\n                            val result = sttRunner.run(appContext, store, sessionId, apiKey).getOrThrow()\n                            when (result.delivery) {\n                                SourceSnapshotDelivery.LIVE_PROVIDER -> DurableSttRunOutcome(\n                                    result = requireNotNull(result.legacyResult),\n                                    recoveredWithoutNetwork = false,\n                                )\n                                SourceSnapshotDelivery.LOCAL_RECOVERY,\n                                SourceSnapshotDelivery.ALREADY_BOUND,\n                                -> {\n                                    val snapshot = requireNotNull(store.readActiveSourceSnapshot(sessionId))\n                                    DurableSttRunOutcome(\n                                        transcriptOnly = snapshot.transcript,\n                                        recoveredWithoutNetwork = true,\n                                    )\n                                }\n                            }\n                        }\n                        requireCurrentSourceGeneration(generation)\n                        check(activeSessionOwner.readActiveSessionId() == sessionId) { \"active session changed after STT\" }\n                        execution\n""",
    "execute full-video STT",
)

old_success = """                    onSuccess = { (result, snapshot) ->\n                        when (result.delivery) {\n                            SourceSnapshotDelivery.LIVE_PROVIDER -> {\n                                val legacy = requireNotNull(result.legacyResult)\n                                mutableStt.value = DurableSttUiState(\n                                    phase = DurableSttPhase.LIVE_SUCCESS,\n                                    sessionId = sessionId,\n                                    transcript = legacy.transcript,\n                                    legacyResult = legacy,\n                                )\n                            }\n                            SourceSnapshotDelivery.LOCAL_RECOVERY,\n                            SourceSnapshotDelivery.ALREADY_BOUND,\n                            -> {\n                                val transcript = requireNotNull(snapshot).transcript\n                                mutableStt.value = DurableSttUiState(\n                                    phase = DurableSttPhase.RECOVERED,\n                                    sessionId = sessionId,\n                                    transcript = transcript,\n                                )\n                            }\n                        }\n                    },\n"""
new_success = """                    onSuccess = { execution ->\n                        val timed = execution.result\n                        mutableStt.value = if (timed != null) {\n                            DurableSttUiState(\n                                phase = if (execution.recoveredWithoutNetwork) DurableSttPhase.RECOVERED else DurableSttPhase.LIVE_SUCCESS,\n                                sessionId = sessionId,\n                                transcript = timed.transcript,\n                                legacyResult = timed,\n                            )\n                        } else {\n                            DurableSttUiState(\n                                phase = DurableSttPhase.RECOVERED,\n                                sessionId = sessionId,\n                                transcript = requireNotNull(execution.transcriptOnly),\n                            )\n                        }\n                    },\n"""
vm = replace_once(vm, old_success, new_success, "publish full-video STT state")

vm = replace_once(
    vm,
    """                        mutableStt.value = if (error is UnknownSttRemoteOutcomeException) {\n""",
    """                        mutableStt.value = if (error is UnknownSttRemoteOutcomeException ||\n                            error is UnknownFullVideoSttRemoteOutcomeException\n                        ) {\n""",
    "surface full-video unknown outcome",
)

vm = replace_once(
    vm,
    """            sttState.phase != DurableSttPhase.LIVE_SUCCESS ||\n""",
    """            (sttState.phase != DurableSttPhase.LIVE_SUCCESS && sttState.phase != DurableSttPhase.RECOVERED) ||\n""",
    "allow recovered timed STT translation",
)

vm = replace_once(
    vm,
    """            Pair(\n                runCatching { store.readActiveSourceAttachment(sessionId) }.getOrNull(),\n                runCatching { store.readActiveSourceSnapshot(sessionId) }.getOrNull(),\n            )\n""",
    """            Triple(\n                runCatching { store.readActiveSourceAttachment(sessionId) }.getOrNull(),\n                runCatching { store.readActiveSourceSnapshot(sessionId) }.getOrNull(),\n                Pair(\n                    runCatching { fullVideoSttAggregateStore?.readOrNull(sessionId) }.getOrNull(),\n                    runCatching { fullVideoSttWindowJournalStore?.findSentOrNull(sessionId) }.getOrNull(),\n                ),\n            )\n""",
    "restore full-video durability inputs",
)

vm = replace_once(
    vm,
    """        val snapshot = durable.second\n""",
    """        val snapshot = durable.second\n        val aggregate = durable.third.first\n        val fullVideoUnknownSent = durable.third.second\n""",
    "unpack full-video durability inputs",
)

vm = replace_once(
    vm,
    """            mutableStt.value = when {\n                controllerState.blocker == SessionBlocker.STT_UNKNOWN_REMOTE_OUTCOME -> DurableSttUiState(\n""",
    """            mutableStt.value = when {\n                fullVideoUnknownSent != null -> DurableSttUiState(\n                    phase = DurableSttPhase.UNKNOWN_REMOTE_OUTCOME,\n                    sessionId = sessionId,\n                )\n                controllerState.blocker == SessionBlocker.STT_UNKNOWN_REMOTE_OUTCOME -> DurableSttUiState(\n""",
    "restore full-video unknown outcome",
)

vm = replace_once(
    vm,
    """                snapshot != null -> DurableSttUiState(\n                    phase = DurableSttPhase.RECOVERED,\n                    sessionId = sessionId,\n                    transcript = snapshot.transcript,\n                )\n""",
    """                aggregate != null && aggregate.sourceAttachmentId != attachment.attachmentId -> DurableSttUiState(\n                    phase = DurableSttPhase.FAILED,\n                    sessionId = sessionId,\n                    failure = DurableSttFailure.RESTORE,\n                )\n                aggregate != null -> DurableSttUiState(\n                    phase = DurableSttPhase.RECOVERED,\n                    sessionId = sessionId,\n                    transcript = aggregate.result.transcript,\n                    legacyResult = aggregate.result,\n                )\n                snapshot != null -> DurableSttUiState(\n                    phase = DurableSttPhase.RECOVERED,\n                    sessionId = sessionId,\n                    transcript = snapshot.transcript,\n                )\n""",
    "restore timed aggregate",
)

vm = replace_once(
    vm,
    """        private val planStore: TranslationRequestPlanStore,\n    ) : ViewModelProvider.Factory {\n""",
    """        private val planStore: TranslationRequestPlanStore,\n        private val fullVideoSttWindowJournal: FullVideoSttWindowJournalStore? = null,\n        private val fullVideoSttAggregateStore: FullVideoSttAggregateStore? = null,\n    ) : ViewModelProvider.Factory {\n""",
    "extend factory dependencies",
)

vm = replace_once(
    vm,
    """                store = store,\n                planStore = planStore,\n""",
    """                store = store,\n                fullVideoSttRunner = if (fullVideoSttWindowJournal != null && fullVideoSttAggregateStore != null) {\n                    FullVideoSttRunner { appContext, sessionStore, sessionId, apiKey ->\n                        FullVideoSttOperation(fullVideoSttWindowJournal, fullVideoSttAggregateStore)\n                            .run(appContext, sessionStore, sessionId, apiKey)\n                    }\n                } else null,\n                fullVideoSttAggregateStore = fullVideoSttAggregateStore,\n                fullVideoSttWindowJournalStore = fullVideoSttWindowJournal,\n                planStore = planStore,\n""",
    "wire full-video runner from factory",
)

vm_path.write_text(vm)

journal_path = Path("app/src/main/java/com/clw/aivideotranslator/session/FullVideoSttWindowJournalStore.kt")
journal = journal_path.read_text()
journal = replace_once(
    journal,
    """    fun persistSent(receipt: FullVideoSttWindowReceipt) = synchronized(lock) {\n""",
    """    fun findSentOrNull(sessionId: String): FullVideoSttWindowReceipt? = synchronized(lock) {\n        validateSession(sessionId)\n        require(isSafeId(sessionId)) { \"invalid STT window session id\" }\n        val directory = File(File(sessionsRoot, sessionId), DIRECTORY)\n        if (!directory.isDirectory) return@synchronized null\n        directory.listFiles()\n            .orEmpty()\n            .asSequence()\n            .filter { it.isFile && it.extension == \"properties\" }\n            .map(::decode)\n            .firstOrNull { it.phase == FullVideoSttWindowPhase.SENT }\n    }\n\n    fun persistSent(receipt: FullVideoSttWindowReceipt) = synchronized(lock) {\n""",
    "add full-video SENT recovery scan",
)
journal_path.write_text(journal)

activity_path = Path("app/src/main/java/com/clw/aivideotranslator/ProductMainActivity.kt")
activity = activity_path.read_text()
activity = replace_once(
    activity,
    """                planStore = app.translationRequestPlans,\n""",
    """                planStore = app.translationRequestPlans,\n                fullVideoSttWindowJournal = app.fullVideoSttWindowJournal,\n                fullVideoSttAggregateStore = app.fullVideoSttAggregates,\n""",
    "wire product launcher stores",
)
activity_path.write_text(activity)
