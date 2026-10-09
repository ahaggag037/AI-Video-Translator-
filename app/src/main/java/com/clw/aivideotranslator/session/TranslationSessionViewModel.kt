package com.clw.aivideotranslator.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaTranslationClient
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
    COMPLETE,
    REVIEW_REQUIRED,
    UNKNOWN_REMOTE_OUTCOME,
    STALE,
    FAILED,
}

data class DurableTranslationText(
    val unitId: String,
    val text: String,
) {
    init {
        require(unitId.isNotBlank()) { "blank translation unit id" }
        require(text.isNotBlank()) { "blank durable translation text" }
    }
}

enum class DurableTranslationFailure {
    NO_LIVE_STT,
    NO_BOUND_SOURCE,
    PROVIDER_OR_STORAGE,
    RESTORE,
    PROVIDER_REJECTED_OR_PENDING,
}

data class DurableTranslationUiState(
    val phase: DurableTranslationPhase,
    val sessionId: String? = null,
    val texts: List<DurableTranslationText> = emptyList(),
    val blockingUnitId: String? = null,
    val failure: DurableTranslationFailure? = null,
) {
    init {
        require(texts.map { it.unitId }.distinct().size == texts.size) { "duplicate durable translation unit" }
        when (phase) {
            DurableTranslationPhase.IDLE -> require(
                sessionId == null && texts.isEmpty() && blockingUnitId == null && failure == null
            )
            DurableTranslationPhase.RUNNING -> require(
                sessionId != null && blockingUnitId == null && failure == null
            )
            DurableTranslationPhase.COMPLETE -> require(
                sessionId != null && texts.isNotEmpty() && blockingUnitId == null && failure == null
            )
            DurableTranslationPhase.REVIEW_REQUIRED,
            DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
            DurableTranslationPhase.STALE,
            -> require(sessionId != null && blockingUnitId != null && failure == null)
            DurableTranslationPhase.FAILED -> require(failure != null)
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
    private val translationSubmitterFactory: (String) -> TranslationPlanSubmitter = { apiKey ->
        var submittedOne = false
        TranslationPlanSubmitter { plan ->
            if (submittedOne) delay(1_500L)
            val outcome = NvidiaTranslationClient.translateDetailed(apiKey, plan)
            submittedOne = true
            outcome
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
     * Executes the frozen P0-F translation request profile through the durable request/receipt path.
     * Model output owns only translated text. The live legacy STT result remains the timing comparator
     * until X001; this method never derives or persists subtitle timing.
     */
    fun runTranslation(apiKey: String) {
        val source = mutableDurableSource.value
        val currentStt = mutableStt.value
        val sessionId = source.sessionId
        if (source.phase != DurableSourcePhase.BOUND || sessionId == null) {
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                failure = DurableTranslationFailure.NO_BOUND_SOURCE,
            )
            return
        }
        val legacyResult = currentStt.legacyResult
        if (currentStt.phase != DurableSttPhase.LIVE_SUCCESS || currentStt.sessionId != sessionId || legacyResult == null) {
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                failure = DurableTranslationFailure.NO_LIVE_STT,
            )
            return
        }
        val requestPlanStore = planStore
        if (requestPlanStore == null) {
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
                        val units = LegacyParityTranslationPlanner.plan(legacyResult)
                        // Publish the whole local plan set before the first POST. A crash after unit N
                        // can then distinguish a complete batch from a durable prefix after restart.
                        units.forEach { unit -> requestPlanStore.publish(sessionId, unit.requestPlan) }
                        val batch = DurableLegacyTranslationOperation(store, requestPlanStore).execute(
                            sessionId = sessionId,
                            units = units,
                            submitter = translationSubmitterFactory(apiKey),
                        ).getOrThrow()
                        requireCurrentSourceGeneration(generation)
                        check(activeSessionOwner.readActiveSessionId() == sessionId) {
                            "active session changed after translation"
                        }
                        batch
                    }
                }
                if (sourceGeneration.get() != generation) return@launchExclusive
                outcome.fold(
                    onSuccess = { batch -> publishTranslationBatch(sessionId, batch) },
                    onFailure = { error ->
                        if (error is CancellationException) return@fold
                        mutableTranslation.value = DurableTranslationUiState(
                            phase = DurableTranslationPhase.FAILED,
                            sessionId = sessionId,
                            texts = durableTranslationTextsOrEmpty(sessionId),
                            failure = DurableTranslationFailure.PROVIDER_OR_STORAGE,
                        )
                    },
                )
            }) return@launch
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
        val restoredTranslation = withContext(Dispatchers.IO) {
            restoreTranslationState(sessionId)
        }
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
            mutableTranslation.value = restoredTranslation
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
            mutableTranslation.value = restoredTranslation
        }
    }

    private fun restoreTranslationState(sessionId: String): DurableTranslationUiState = runCatching {
        val requestPlanStore = planStore
        if (requestPlanStore != null) {
            recoverReceivedTranslationState(sessionId, requestPlanStore)?.let { return@runCatching it }
        }

        val manifest = store.readManifest(sessionId)
        val texts = durableTranslationTexts(sessionId, manifest)
        val activeIds = texts.mapTo(mutableSetOf()) { it.unitId }
        val receipts = store.listReceipts(sessionId)
        val unresolved = receipts.firstOrNull { receipt ->
            receipt.phase == RequestReceiptPhase.SENT &&
                receipt.epoch == manifest.epoch &&
                receipt.unitId !in activeIds
        }
        if (unresolved != null) {
            return@runCatching DurableTranslationUiState(
                phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = unresolved.unitId,
            )
        }

        val expectedUnitIds = requestPlanStore
            ?.list(sessionId)
            ?.map { it.unitId }
            ?.toSortedSet()
            ?: sortedSetOf()
        when {
            expectedUnitIds.isEmpty() && texts.isEmpty() -> DurableTranslationUiState.idle()
            expectedUnitIds.isNotEmpty() && activeIds == expectedUnitIds -> DurableTranslationUiState(
                phase = DurableTranslationPhase.COMPLETE,
                sessionId = sessionId,
                texts = texts,
            )
            else -> DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = expectedUnitIds.firstOrNull { it !in activeIds },
                failure = DurableTranslationFailure.RESTORE,
            )
        }
    }.getOrElse {
        DurableTranslationUiState(
            phase = DurableTranslationPhase.FAILED,
            sessionId = sessionId,
            failure = DurableTranslationFailure.RESTORE,
        )
    }

    /**
     * Passive reopen may adopt a durable RECEIVED candidate because no transport is required. It
     * never advances PREPARED and never submits SENT again. Non-adoptable outcomes stay typed.
     */
    private fun recoverReceivedTranslationState(
        sessionId: String,
        requestPlanStore: TranslationRequestPlanStore,
    ): DurableTranslationUiState? {
        val manifest = store.readManifest(sessionId)
        val activeIds = manifest.activeEntryRefs.keys
        val received = store.listReceipts(sessionId).filter { receipt ->
            receipt.phase == RequestReceiptPhase.RECEIVED &&
                receipt.epoch == manifest.epoch &&
                receipt.unitId !in activeIds
        }
        if (received.isEmpty()) return null
        if (received.size > 1) {
            return DurableTranslationUiState(
                phase = DurableTranslationPhase.STALE,
                sessionId = sessionId,
                texts = durableTranslationTexts(sessionId, manifest),
                blockingUnitId = received.first().unitId,
            )
        }
        val receipt = received.single()
        val plan = requestPlanStore.read(sessionId, receipt.unitId, receipt.requestSignature)
        val recovery = ReceiptRecoveryPlanner.plan(receipt, manifest, plan)
        val texts = durableTranslationTexts(sessionId, manifest)
        return when (recovery.action) {
            ReceiptRecoveryAction.READY_TO_ADOPT -> {
                store.adoptRecoveredCandidate(sessionId, receipt.attemptId, plan)
                null
            }
            ReceiptRecoveryAction.REVIEW_CANDIDATE -> DurableTranslationUiState(
                phase = DurableTranslationPhase.REVIEW_REQUIRED,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = receipt.unitId,
            )
            ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY -> DurableTranslationUiState(
                phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = receipt.unitId,
            )
            ReceiptRecoveryAction.STALE_RECEIPT,
            ReceiptRecoveryAction.PLAN_NEW_ATTEMPT,
            -> DurableTranslationUiState(
                phase = DurableTranslationPhase.STALE,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = receipt.unitId,
            )
            ReceiptRecoveryAction.REJECT_CANDIDATE,
            ReceiptRecoveryAction.HOLD_PENDING,
            ReceiptRecoveryAction.TERMINAL_OUTCOME,
            -> DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = receipt.unitId,
                failure = DurableTranslationFailure.PROVIDER_REJECTED_OR_PENDING,
            )
        }
    }

    private fun durableTranslationTexts(
        sessionId: String,
        manifest: SessionManifest = store.readManifest(sessionId),
    ): List<DurableTranslationText> = manifest.activeEntryRefs.keys.sorted().map { unitId ->
        val entry = requireNotNull(store.readActiveEntry(sessionId, unitId)) { "active translation entry missing" }
        DurableTranslationText(
            unitId = unitId,
            text = requireNotNull(entry.record.effectiveText()) { "active translation entry has no effective text" },
        )
    }

    private fun durableTranslationTextsOrEmpty(sessionId: String): List<DurableTranslationText> =
        runCatching { durableTranslationTexts(sessionId) }.getOrDefault(emptyList())

    private fun publishTranslationBatch(sessionId: String, batch: DurableTranslationBatchResult) {
        val texts = batch.units.mapNotNull { unit ->
            unit.effectiveText?.let { DurableTranslationText(unit.unitId, it) }
        }
        if (batch.completed) {
            require(texts.size == batch.units.size) { "completed translation batch is missing effective text" }
            mutableTranslation.value = DurableTranslationUiState(
                phase = DurableTranslationPhase.COMPLETE,
                sessionId = sessionId,
                texts = texts,
            )
            return
        }
        val blocker = requireNotNull(batch.units.lastOrNull()) { "incomplete translation batch has no blocker" }
        mutableTranslation.value = when (blocker.disposition) {
            DurableTranslationUnitDisposition.REVIEW_REQUIRED -> DurableTranslationUiState(
                phase = DurableTranslationPhase.REVIEW_REQUIRED,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = blocker.unitId,
            )
            DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME -> DurableTranslationUiState(
                phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = blocker.unitId,
            )
            DurableTranslationUnitDisposition.STALE_STATE,
            DurableTranslationUnitDisposition.AMBIGUOUS_RECEIPTS,
            -> DurableTranslationUiState(
                phase = DurableTranslationPhase.STALE,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = blocker.unitId,
            )
            DurableTranslationUnitDisposition.REJECTED,
            DurableTranslationUnitDisposition.PENDING,
            DurableTranslationUnitDisposition.TERMINAL,
            -> DurableTranslationUiState(
                phase = DurableTranslationPhase.FAILED,
                sessionId = sessionId,
                texts = texts,
                blockingUnitId = blocker.unitId,
                failure = DurableTranslationFailure.PROVIDER_REJECTED_OR_PENDING,
            )
            DurableTranslationUnitDisposition.REUSED_ENTRY,
            DurableTranslationUnitDisposition.ADOPTED,
            -> error("completed translation disposition cannot block an incomplete batch")
        }
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
