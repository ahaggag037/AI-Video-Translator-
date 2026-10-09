package com.clw.aivideotranslator.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaTranslationClient
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DurableSourcePhase {
    NONE,
    CAPTURING,
    BOUND,
    FAILED,
}

enum class DurableSourceFailure {
    CAPTURE,
    PERSISTENCE,
    ACTIVATION,
    RESTORE,
    CHECK_REQUIRED,
    PERMISSION_MISSING,
    SOURCE_MISSING,
    SOURCE_CHANGED,
    IO_FAILURE,
    UNSUPPORTED,
    CORRUPT,
    STALE,
}

data class DurableSourceUiState(
    val phase: DurableSourcePhase,
    val sessionId: String? = null,
    val contentUri: String? = null,
    val failure: DurableSourceFailure? = null,
) {
    init {
        when (phase) {
            DurableSourcePhase.NONE -> require(sessionId == null && contentUri == null && failure == null)
            DurableSourcePhase.CAPTURING -> require(sessionId == null && contentUri != null && failure == null)
            DurableSourcePhase.BOUND -> require(sessionId != null && contentUri != null && failure == null)
            DurableSourcePhase.FAILED -> require(failure != null)
        }
    }

    companion object {
        fun none() = DurableSourceUiState(DurableSourcePhase.NONE)
    }
}

enum class DurableSttPhase {
    IDLE,
    RUNNING,
    LIVE_SUCCESS,
    RECOVERED,
    UNKNOWN_REMOTE_OUTCOME,
    FAILED,
}

enum class DurableSttFailure {
    NO_BOUND_SOURCE,
    PROVIDER_OR_STORAGE,
    RESTORE,
}

data class DurableSttUiState(
    val phase: DurableSttPhase,
    val sessionId: String? = null,
    val transcript: String? = null,
    val legacyResult: NvidiaSttResult? = null,
    val failure: DurableSttFailure? = null,
) {
    init {
        when (phase) {
            DurableSttPhase.IDLE -> require(sessionId == null && transcript == null && legacyResult == null && failure == null)
            DurableSttPhase.RUNNING -> require(sessionId != null && legacyResult == null && failure == null)
            DurableSttPhase.LIVE_SUCCESS -> require(
                sessionId != null && legacyResult != null && transcript == legacyResult.transcript && failure == null
            )
            DurableSttPhase.RECOVERED -> require(sessionId != null && !transcript.isNullOrBlank() && legacyResult == null && failure == null)
            DurableSttPhase.UNKNOWN_REMOTE_OUTCOME -> require(sessionId != null && legacyResult == null && failure == null)
            DurableSttPhase.FAILED -> require(failure != null && legacyResult == null)
        }
    }

    companion object {
        fun idle() = DurableSttUiState(DurableSttPhase.IDLE)
    }
}

enum class DurableTranslationPhase {
    IDLE,
    RUNNING,
    LIVE_SUCCESS,
    RECOVERED_TEXT_ONLY,
    UNKNOWN_REMOTE_OUTCOME,
    BLOCKED,
    FAILED,
}

enum class DurableTranslationFailure {
    NO_LIVE_STT,
    PROVIDER_OR_STORAGE,
    RESTORE,
}

data class DurableTranslationUiState(
    val phase: DurableTranslationPhase,
    val sessionId: String? = null,
    val liveUnits: List<SourceUnit>? = null,
    val entries: List<TranslationEntry> = emptyList(),
    val blocker: DurableTranslationUnitDisposition? = null,
    val failure: DurableTranslationFailure? = null,
) {
    init {
        when (phase) {
            DurableTranslationPhase.IDLE -> require(
                sessionId == null && liveUnits == null && entries.isEmpty() && blocker == null && failure == null
            )
            DurableTranslationPhase.RUNNING -> require(
                sessionId != null && liveUnits == null && entries.isEmpty() && blocker == null && failure == null
            )
            DurableTranslationPhase.LIVE_SUCCESS -> {
                require(sessionId != null && !liveUnits.isNullOrEmpty() && entries.isNotEmpty())
                require(blocker == null && failure == null)
                require(liveUnits.size == entries.size)
                require(liveUnits.map { it.id } == entries.map { it.sourceUnitId })
            }
            DurableTranslationPhase.RECOVERED_TEXT_ONLY -> require(
                sessionId != null && liveUnits == null && entries.isNotEmpty() && blocker == null && failure == null
            )
            DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME -> require(
                sessionId != null && liveUnits == null && blocker == DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME &&
                    failure == null
            )
            DurableTranslationPhase.BLOCKED -> require(
                sessionId != null && liveUnits == null && blocker != null &&
                    blocker != DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME && failure == null
            )
            DurableTranslationPhase.FAILED -> require(liveUnits == null && blocker == null && failure != null)
        }
    }

    companion object {
        fun idle() = DurableTranslationUiState(DurableTranslationPhase.IDLE)
    }
}

internal fun interface DurableSttRunner {
    fun run(
        context: Context,
        store: TranslationSessionStore,
        sessionId: String,
        apiKey: String,
    ): Result<SourceSnapshotOperationResult>
}

internal data class DurableTranslationExecution(
    val units: List<LegacyParityTranslationUnit>,
    val batch: DurableTranslationBatchResult,
)

internal fun interface DurableTranslationRunner {
    suspend fun run(
        store: TranslationSessionStore,
        planStore: TranslationRequestPlanStore,
        sessionId: String,
        apiKey: String,
        liveStt: NvidiaSttResult,
    ): Result<DurableTranslationExecution>
}

/**
 * Activity configuration-change owner for Task17. Disk remains recovery truth; this ViewModel owns
 * only cancellable in-process work and delegates reopen/status semantics to
 * [TranslationSessionController]. Credentials are accepted only as call arguments and never stored.
 */
internal class TranslationSessionViewModel(
    context: Context,
    private val activeSessionOwner: ActiveSessionOwner,
    private val store: TranslationSessionStore,
    private val sttRunner: DurableSttRunner = DurableSttRunner { appContext, sessionStore, sessionId, apiKey ->
        SourceSnapshotOperation.transcribeAndBindDetailed(appContext, sessionStore, sessionId, apiKey)
    },
    private val planStore: TranslationRequestPlanStore? = null,
    private val translationRunner: DurableTranslationRunner = DurableTranslationRunner {
            sessionStore,
            requestPlanStore,
            sessionId,
            apiKey,
            liveStt,
        ->
        runCatching {
            require(apiKey.trim().isNotEmpty()) { "translation API key is blank" }
            val units = LegacyParityTranslationPlanner.plan(liveStt)
            var providerSubmissions = 0
            val batch = DurableLegacyTranslationOperation(sessionStore, requestPlanStore)
                .execute(sessionId, units) { plan ->
                    if (providerSubmissions > 0) delay(1_500L)
                    providerSubmissions += 1
                    NvidiaTranslationClient.translateDetailed(apiKey, plan)
                }
                .getOrThrow()
            DurableTranslationExecution(units, batch)
        }
    },
) : ViewModel() {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobLock = Any()
    private var activeJob: Job? = null
    private val sourceGeneration = AtomicLong(0L)
    private val controller = TranslationSessionController(
        activeSessionOwner = activeSessionOwner,
        reopener = SessionReopener { sessionId ->
            SourceSessionCoordinator.reopenSession(appContext.contentResolver, store, sessionId).getOrThrow()
        },
    )

    val state = controller.state

    private val mutableDurableSource = MutableStateFlow(DurableSourceUiState.none())
    val durableSource = mutableDurableSource.asStateFlow()

    private val mutableStt = MutableStateFlow(DurableSttUiState.idle())
    val stt = mutableStt.asStateFlow()

    private val mutableTranslation = MutableStateFlow(DurableTranslationUiState.idle())
    val translation = mutableTranslation.asStateFlow()

    init {
        resumeActiveSession()
    }

    fun resumeActiveSession() {
        launchExclusive {
            val result = withContext(Dispatchers.IO) { controller.resumeActiveSession() }
            refreshDurableState(result)
        }
    }

    /**
     * Explicit user source selection supersedes a launch-time resume. The provider URI is retained
     * only in private durable source state and in this in-memory UI state; it is never logged.
     */
    fun selectNewSource(contentUri: String) {
        require(contentUri.isNotBlank()) { "source URI is blank" }
        cancelCurrent()
        val generation = sourceGeneration.incrementAndGet()
        mutableStt.value = DurableSttUiState.idle()
        mutableTranslation.value = DurableTranslationUiState.idle()
        mutableDurableSource.value = DurableSourceUiState(
            phase = DurableSourcePhase.CAPTURING,
            contentUri = contentUri,
        )
        check(launchExclusive {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val expectedActiveSessionId = activeSessionOwner.readActiveSessionId()
                    val sessionId = "session-${UUID.randomUUID()}"
                    val captured = SourceAttachmentBuilder.capture(
                        context = appContext,
                        sessionId = sessionId,
                        contentUri = contentUri,
                    ).getOrThrow()
                    captured.use { source ->
                        requireCurrentSourceGeneration(generation)
                        store.createSession(sessionId)
                        store.bindInitialSourceAttachment(sessionId, 0L, source.attachment)
                        requireCurrentSourceGeneration(generation)
                    }
                    val controllerState = controller.activateAndResume(sessionId, expectedActiveSessionId)
                    requireCurrentSourceGeneration(generation)
                    Triple(sessionId, controllerState, store.readActiveSourceAttachment(sessionId))
                }
            }

            if (sourceGeneration.get() != generation) return@launchExclusive
            outcome.fold(
                onSuccess = { (sessionId, controllerState, attachment) ->
                    if (controllerState.phase == SessionControllerPhase.FAILED) {
                        mutableDurableSource.value = DurableSourceUiState(
                            phase = DurableSourcePhase.FAILED,
                            sessionId = sessionId,
                            contentUri = contentUri,
                            failure = DurableSourceFailure.ACTIVATION,
                        )
                    } else {
                        val exact = requireNotNull(attachment) { "bound source attachment missing" }
                        val sourceFailure = sourceFailureFor(controllerState.assessment?.source?.availability)
                        mutableDurableSource.value = if (sourceFailure == null) {
                            DurableSourceUiState(
                                phase = DurableSourcePhase.BOUND,
                                sessionId = sessionId,
                                contentUri = exact.contentUri,
                            )
                        } else {
                            DurableSourceUiState(
                                phase = DurableSourcePhase.FAILED,
                                sessionId = sessionId,
                                contentUri = exact.contentUri,
                                failure = sourceFailure,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) return@fold
                    val failure = when (error) {
                        is SourceCaptureException -> DurableSourceFailure.CAPTURE
                        else -> DurableSourceFailure.PERSISTENCE
                    }
                    mutableDurableSource.value = DurableSourceUiState(
                        phase = DurableSourcePhase.FAILED,
                        contentUri = contentUri,
                        failure = failure,
                    )
                },
            )
        }) { "source selection could not acquire ViewModel operation ownership" }
    }

    /**
     * One durable STT attempt. LIVE_SUCCESS exposes the exact parser result from the same provider
     * response that produced the durable snapshot. Local recovery never performs another request and
     * therefore exposes transcript-only RECOVERED state while X001 timing remains unverified.
     */
    fun runStt(apiKey: String) {
        val source = mutableDurableSource.value
        val sessionId = source.sessionId
        if (source.phase != DurableSourcePhase.BOUND || sessionId == null) {
            mutableStt.value = DurableSttUiState(
                phase = DurableSttPhase.FAILED,
                failure = DurableSttFailure.NO_BOUND_SOURCE,
            )
            return
        }
        val generation = sourceGeneration.get()
        scope.launch {
            if (sourceGeneration.get() != generation) return@launch
            if (!launchExclusive {
                mutableStt.value = DurableSttUiState(DurableSttPhase.RUNNING, sessionId = sessionId)
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        check(activeSessionOwner.readActiveSessionId() == sessionId) { "active session changed before STT" }
                        requireCurrentSourceGeneration(generation)
                        val result = sttRunner.run(appContext, store, sessionId, apiKey).getOrThrow()
                        requireCurrentSourceGeneration(generation)
                        check(activeSessionOwner.readActiveSessionId() == sessionId) { "active session changed after STT" }
                        val snapshot = store.readActiveSourceSnapshot(sessionId)
                        Pair(result, snapshot)
                    }
                }
                if (sourceGeneration.get() != generation) return@launchExclusive
                outcome.fold(
                    onSuccess = { (result, snapshot) ->
                        when (result.delivery) {
                            SourceSnapshotDelivery.LIVE_PROVIDER -> {
                                val legacy = requireNotNull(result.legacyResult)
                                mutableStt.value = DurableSttUiState(
                                    phase = DurableSttPhase.LIVE_SUCCESS,
                                    sessionId = sessionId,
                                    transcript = legacy.transcript,
                                    legacyResult = legacy,
                                )
                            }
                            SourceSnapshotDelivery.LOCAL_RECOVERY,
                            SourceSnapshotDelivery.ALREADY_BOUND,
                            -> {
                                val transcript = requireNotNull(snapshot).transcript
                                mutableStt.value = DurableSttUiState(
                                    phase = DurableSttPhase.RECOVERED,
                                    sessionId = sessionId,
                                    transcript = transcript,
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) return@fold
                        mutableStt.value = if (error is UnknownSttRemoteOutcomeException) {
                            DurableSttUiState(
                                phase = DurableSttPhase.UNKNOWN_REMOTE_OUTCOME,
                                sessionId = sessionId,
                            )
                        } else {
                            DurableSttUiState(
                                phase = DurableSttPhase.FAILED,
                                sessionId = sessionId,
                                failure = DurableSttFailure.PROVIDER_OR_STORAGE,
                            )
                        }
                    },
                )
            }) return@launch
        }
    }

    /**
     * Durable P0-F translation activation. The exact live STT result supplies presentation timing
     * only for this process; request plans/text and accepted translation entries are durable. A
     * restart may recover text, but it never reconstructs word timing before X001 is verified.
     */
    fun runTranslation(apiKey: String) {
        val source = mutableDurableSource.value
        val sttState = mutableStt.value
        val sessionId = source.sessionId
        val liveStt = sttState.legacyResult
        val durablePlanStore = planStore
        if (
            source.phase != DurableSourcePhase.BOUND ||
            sessionId == null ||
            sttState.phase != DurableSttPhase.LIVE_SUCCESS ||
            sttState.sessionId != sessionId ||
            liveStt == null ||
            durablePlanStore == null
        ) {
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                failure = DurableTranslationFailure.NO_LIVE_STT,
            )
            return
        }
        if (apiKey.isBlank()) {
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                failure = DurableTranslationFailure.PROVIDER_OR_STORAGE,
            )
            return
        }

        val generation = sourceGeneration.get()
        scope.launch {
            if (sourceGeneration.get() != generation) return@launch
            if (!launchExclusive {
                mutableTranslation.value = DurableTranslationUiState(
                    phase = DurableTranslationPhase.RUNNING,
                    sessionId = sessionId,
                )
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        check(activeSessionOwner.readActiveSessionId() == sessionId) {
                            "active session changed before translation"
                        }
                        requireCurrentSourceGeneration(generation)
                        val result = translationRunner.run(
                            store = store,
                            planStore = durablePlanStore,
                            sessionId = sessionId,
                            apiKey = apiKey,
                            liveStt = liveStt,
                        ).getOrThrow()
                        requireCurrentSourceGeneration(generation)
                        check(activeSessionOwner.readActiveSessionId() == sessionId) {
                            "active session changed after translation"
                        }
                        result
                    }
                }
                if (sourceGeneration.get() != generation) return@launchExclusive
                outcome.fold(
                    onSuccess = { execution -> publishTranslationExecution(sessionId, execution) },
                    onFailure = { error ->
                        if (error is CancellationException) return@fold
                        mutableTranslation.value = DurableTranslationUiState(
                            phase = DurableTranslationPhase.FAILED,
                            sessionId = sessionId,
                            failure = DurableTranslationFailure.PROVIDER_OR_STORAGE,
                        )
                    },
                )
            }) return@launch
        }
    }

    private fun publishTranslationExecution(
        sessionId: String,
        execution: DurableTranslationExecution,
    ) {
        val successfulEntries = execution.batch.units
            .takeWhile {
                it.disposition == DurableTranslationUnitDisposition.REUSED_ENTRY ||
                    it.disposition == DurableTranslationUnitDisposition.ADOPTED
            }
            .map { result ->
                TranslationEntry(
                    sourceUnitId = result.unitId,
                    translatedText = requireNotNull(result.effectiveText) {
                        "accepted translation unit missing effective text"
                    },
                )
            }

        if (execution.batch.completed) {
            val liveUnits = execution.units.map { it.legacyUnit }
            require(liveUnits.size == successfulEntries.size) { "completed translation batch lost units" }
            require(liveUnits.map { it.id } == successfulEntries.map { it.sourceUnitId }) {
                "completed translation batch changed P0-F unit identity"
            }
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.LIVE_SUCCESS,
                sessionId = sessionId,
                liveUnits = liveUnits,
                entries = successfulEntries,
            )
            return
        }

        val blocker = requireNotNull(execution.batch.units.lastOrNull()?.disposition) {
            "incomplete translation batch has no blocker"
        }
        mutableTranslation.value = if (blocker == DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME) {
            DurableTranslationUiState(
                phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = sessionId,
                entries = successfulEntries,
                blocker = blocker,
            )
        } else {
            DurableTranslationUiState(
                phase = DurableTranslationPhase.BLOCKED,
                sessionId = sessionId,
                entries = successfulEntries,
                blocker = blocker,
            )
        }
    }

    /** Fence controller/source publication before cancelling the owning coroutine. */
    fun cancelCurrent() {
        sourceGeneration.incrementAndGet()
        controller.cancelCurrent()
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
    }

    private suspend fun refreshDurableState(controllerState: TranslationSessionUiState) {
        val sessionId = controllerState.sessionId
        if (sessionId == null) {
            mutableDurableSource.value = DurableSourceUiState.none()
            mutableStt.value = DurableSttUiState.idle()
            mutableTranslation.value = DurableTranslationUiState.idle()
            return
        }
        val durable = withContext(Dispatchers.IO) {
            Pair(
                runCatching { store.readActiveSourceAttachment(sessionId) }.getOrNull(),
                runCatching { store.readActiveSourceSnapshot(sessionId) }.getOrNull(),
            )
        }
        val attachment = durable.first
        val snapshot = durable.second
        if (attachment != null) {
            val sourceFailure = sourceFailureFor(controllerState.assessment?.source?.availability)
            mutableDurableSource.value = if (sourceFailure == null) {
                DurableSourceUiState(
                    phase = DurableSourcePhase.BOUND,
                    sessionId = sessionId,
                    contentUri = attachment.contentUri,
                )
            } else {
                DurableSourceUiState(
                    phase = DurableSourcePhase.FAILED,
                    sessionId = sessionId,
                    contentUri = attachment.contentUri,
                    failure = sourceFailure,
                )
            }
            mutableStt.value = when {
                controllerState.blocker == SessionBlocker.STT_UNKNOWN_REMOTE_OUTCOME -> DurableSttUiState(
                    phase = DurableSttPhase.UNKNOWN_REMOTE_OUTCOME,
                    sessionId = sessionId,
                )
                controllerState.blocker == SessionBlocker.CORRUPT_DURABLE_STATE ||
                    controllerState.blocker == SessionBlocker.STALE_STATE -> DurableSttUiState(
                    phase = DurableSttPhase.FAILED,
                    sessionId = sessionId,
                    failure = DurableSttFailure.RESTORE,
                )
                snapshot != null -> DurableSttUiState(
                    phase = DurableSttPhase.RECOVERED,
                    sessionId = sessionId,
                    transcript = snapshot.transcript,
                )
                else -> DurableSttUiState.idle()
            }
            mutableTranslation.value = if (sourceFailure == null) {
                restoreTranslationState(sessionId)
            } else {
                DurableTranslationUiState(
                    phase = DurableTranslationPhase.FAILED,
                    sessionId = sessionId,
                    failure = DurableTranslationFailure.RESTORE,
                )
            }
            return
        }
        val availability = controllerState.assessment?.source?.availability
        if (availability == SourceAvailability.UNBOUND || availability == SourceAvailability.LEGACY_UNBOUND) {
            mutableDurableSource.value = DurableSourceUiState.none()
            mutableStt.value = DurableSttUiState.idle()
            mutableTranslation.value = DurableTranslationUiState.idle()
        } else if (controllerState.phase == SessionControllerPhase.FAILED || controllerState.assessment != null) {
            mutableDurableSource.value = DurableSourceUiState(
                phase = DurableSourcePhase.FAILED,
                sessionId = sessionId,
                failure = sourceFailureFor(availability) ?: DurableSourceFailure.RESTORE,
            )
            mutableStt.value = DurableSttUiState(
                phase = DurableSttPhase.FAILED,
                sessionId = sessionId,
                failure = DurableSttFailure.RESTORE,
            )
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                failure = DurableTranslationFailure.RESTORE,
            )
        }
    }

    private suspend fun restoreTranslationState(sessionId: String): DurableTranslationUiState {
        val durablePlanStore = planStore ?: return DurableTranslationUiState.idle()
        return runCatching {
            withContext(Dispatchers.IO) {
                val operation = DurableLegacyTranslationOperation(store, durablePlanStore)
                val initialManifest = store.readManifest(sessionId)
                var blocker: DurableTranslationUnitDisposition? = null

                for (receipt in store.listReceipts(sessionId)) {
                    val currentManifest = store.readManifest(sessionId)
                    val plan = runCatching {
                        durablePlanStore.read(sessionId, receipt.unitId, receipt.requestSignature)
                    }.getOrElse { error ->
                        val fence = SessionFencing.check(
                            receipt.adoptionFence(),
                            currentManifest,
                            receipt.requestSignature,
                        )
                        if (fence == AdoptionFenceResult.CURRENT) throw error
                        return@getOrElse null
                    } ?: continue

                    when (val recovery = ReceiptRecoveryPlanner.plan(receipt, currentManifest, plan).action) {
                        ReceiptRecoveryAction.READY_TO_ADOPT -> {
                            val resumed = operation.resumeAttempt(sessionId, receipt.attemptId) {
                                error("passive translation recovery must not call provider")
                            }.getOrThrow()
                            if (
                                resumed.disposition != DurableTranslationUnitDisposition.ADOPTED &&
                                resumed.disposition != DurableTranslationUnitDisposition.REUSED_ENTRY
                            ) {
                                blocker = preferTranslationBlocker(blocker, resumed.disposition)
                            }
                        }
                        ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY -> blocker = preferTranslationBlocker(
                            blocker,
                            DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME,
                        )
                        ReceiptRecoveryAction.REVIEW_CANDIDATE -> blocker = preferTranslationBlocker(
                            blocker,
                            DurableTranslationUnitDisposition.REVIEW_REQUIRED,
                        )
                        ReceiptRecoveryAction.REJECT_CANDIDATE -> blocker = preferTranslationBlocker(
                            blocker,
                            DurableTranslationUnitDisposition.REJECTED,
                        )
                        ReceiptRecoveryAction.HOLD_PENDING -> blocker = preferTranslationBlocker(
                            blocker,
                            DurableTranslationUnitDisposition.PENDING,
                        )
                        ReceiptRecoveryAction.TERMINAL_OUTCOME -> blocker = preferTranslationBlocker(
                            blocker,
                            DurableTranslationUnitDisposition.TERMINAL,
                        )
                        ReceiptRecoveryAction.PLAN_NEW_ATTEMPT,
                        ReceiptRecoveryAction.STALE_RECEIPT,
                        -> Unit
                    }
                }

                val manifest = store.readManifest(sessionId)
                val entries = manifest.activeEntryRefs.keys.sorted().map { unitId ->
                    val active = requireNotNull(store.readActiveEntry(sessionId, unitId)) {
                        "active translation entry missing during restore"
                    }
                    TranslationEntry(
                        unitId,
                        requireNotNull(active.record.effectiveText()) {
                            "active translation entry has no effective text"
                        },
                    )
                }

                when {
                    blocker == DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME -> DurableTranslationUiState(
                        phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                        sessionId = sessionId,
                        entries = entries,
                        blocker = blocker,
                    )
                    blocker != null -> DurableTranslationUiState(
                        phase = DurableTranslationPhase.BLOCKED,
                        sessionId = sessionId,
                        entries = entries,
                        blocker = blocker,
                    )
                    entries.isNotEmpty() -> DurableTranslationUiState(
                        phase = DurableTranslationPhase.RECOVERED_TEXT_ONLY,
                        sessionId = sessionId,
                        entries = entries,
                    )
                    else -> DurableTranslationUiState.idle()
                }
            }
        }.getOrElse {
            DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                failure = DurableTranslationFailure.RESTORE,
            )
        }
    }

    private fun preferTranslationBlocker(
        current: DurableTranslationUnitDisposition?,
        candidate: DurableTranslationUnitDisposition,
    ): DurableTranslationUnitDisposition {
        fun priority(value: DurableTranslationUnitDisposition): Int = when (value) {
            DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME -> 100
            DurableTranslationUnitDisposition.REVIEW_REQUIRED -> 90
            DurableTranslationUnitDisposition.AMBIGUOUS_RECEIPTS -> 80
            DurableTranslationUnitDisposition.STALE_STATE -> 70
            DurableTranslationUnitDisposition.REJECTED -> 60
            DurableTranslationUnitDisposition.TERMINAL -> 50
            DurableTranslationUnitDisposition.PENDING -> 40
            DurableTranslationUnitDisposition.REUSED_ENTRY,
            DurableTranslationUnitDisposition.ADOPTED,
            -> 0
        }
        return if (current == null || priority(candidate) > priority(current)) candidate else current
    }

    private fun sourceFailureFor(availability: SourceAvailability?): DurableSourceFailure? = when (availability) {
        null, SourceAvailability.AVAILABLE, SourceAvailability.UNBOUND, SourceAvailability.LEGACY_UNBOUND -> null
        SourceAvailability.CHECK_REQUIRED -> DurableSourceFailure.CHECK_REQUIRED
        SourceAvailability.PERMISSION_MISSING -> DurableSourceFailure.PERMISSION_MISSING
        SourceAvailability.SOURCE_MISSING -> DurableSourceFailure.SOURCE_MISSING
        SourceAvailability.SOURCE_CHANGED -> DurableSourceFailure.SOURCE_CHANGED
        SourceAvailability.IO_FAILURE -> DurableSourceFailure.IO_FAILURE
        SourceAvailability.UNSUPPORTED -> DurableSourceFailure.UNSUPPORTED
        SourceAvailability.CORRUPT_BINDING -> DurableSourceFailure.CORRUPT
        SourceAvailability.STALE_OBSERVATION -> DurableSourceFailure.STALE
    }

    private fun requireCurrentSourceGeneration(expected: Long) {
        if (sourceGeneration.get() != expected) throw CancellationException("source selection superseded")
    }

    private fun launchExclusive(block: suspend () -> Unit): Boolean = synchronized(jobLock) {
        if (activeJob?.isActive == true) return@synchronized false
        val job = scope.launch { block() }
        activeJob = job
        job.invokeOnCompletion {
            synchronized(jobLock) {
                if (activeJob === job) activeJob = null
            }
        }
        true
    }

    override fun onCleared() {
        sourceGeneration.incrementAndGet()
        controller.cancelCurrent()
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
        scope.cancel()
        super.onCleared()
    }

    internal class Factory(
        private val context: Context,
        private val activeSessionOwner: ActiveSessionOwner,
        private val store: TranslationSessionStore,
        private val planStore: TranslationRequestPlanStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == TranslationSessionViewModel::class.java) { "unsupported ViewModel class" }
            return TranslationSessionViewModel(
                context = context,
                activeSessionOwner = activeSessionOwner,
                store = store,
                planStore = planStore,
            ) as T
        }
    }
}
