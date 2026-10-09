package com.clw.aivideotranslator.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.NvidiaSttResult
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        // The exclusive-operation gate must be evaluated on the ViewModel dispatcher, not on this
        // caller thread. BOUND is published from inside the still-running source-binding coroutine,
        // whose completion (which releases activeJob) is only queued behind that publication on the
        // main dispatcher. Gating on the caller thread raced with that completion bookkeeping and
        // could silently drop the STT attempt, leaving `stt` in IDLE forever — observed as the 30s
        // API35 timeout in liveSttResultIsPublishedFromOneRunnerInvocationAndSameDurableSnapshot.
        scope.launch {
            if (sourceGeneration.get() != generation) return@launch
            // A genuinely active operation (e.g. a duplicate STT tap) still owns the ViewModel;
            // refuse exactly as the previous caller-thread gate did.
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
            return
        }
        val availability = controllerState.assessment?.source?.availability
        if (availability == SourceAvailability.UNBOUND || availability == SourceAvailability.LEGACY_UNBOUND) {
            mutableDurableSource.value = DurableSourceUiState.none()
            mutableStt.value = DurableSttUiState.idle()
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == TranslationSessionViewModel::class.java) { "unsupported ViewModel class" }
            return TranslationSessionViewModel(context, activeSessionOwner, store) as T
        }
    }
}
