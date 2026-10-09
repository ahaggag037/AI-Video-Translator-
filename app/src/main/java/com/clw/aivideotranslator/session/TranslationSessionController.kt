package com.clw.aivideotranslator.session

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionControllerPhase {
    IDLE,
    OPENING,
    READY,
    BLOCKED,
    FAILED,
}

enum class SessionBlocker {
    NO_SOURCE,
    LEGACY_UNBOUND,
    CHECK_REQUIRED,
    SOURCE_PERMISSION_MISSING,
    SOURCE_MISSING,
    SOURCE_CHANGED,
    SOURCE_IO_FAILURE,
    UNSUPPORTED_SOURCE,
    CORRUPT_DURABLE_STATE,
    STALE_STATE,
    STT_UNKNOWN_REMOTE_OUTCOME,
}

enum class SessionControllerFailure {
    ACTIVE_POINTER_READ,
    ACTIVE_POINTER_WRITE,
    REOPEN,
}

data class TranslationSessionUiState(
    val phase: SessionControllerPhase,
    val sessionId: String? = null,
    val assessment: SessionReopenAssessment? = null,
    val blocker: SessionBlocker? = null,
    val failure: SessionControllerFailure? = null,
) {
    init {
        when (phase) {
            SessionControllerPhase.IDLE -> require(sessionId == null && assessment == null && blocker == null && failure == null) {
                "idle session state cannot carry active work"
            }
            SessionControllerPhase.OPENING -> require(assessment == null && blocker == null && failure == null) {
                "opening session state cannot carry a terminal result"
            }
            SessionControllerPhase.READY -> require(sessionId != null && assessment != null && blocker == null && failure == null) {
                "ready session state requires an assessment"
            }
            SessionControllerPhase.BLOCKED -> require(sessionId != null && assessment != null && blocker != null && failure == null) {
                "blocked session state requires a typed blocker"
            }
            SessionControllerPhase.FAILED -> require(assessment == null && blocker == null && failure != null) {
                "failed session state requires a typed failure"
            }
        }
    }

    companion object {
        fun idle() = TranslationSessionUiState(SessionControllerPhase.IDLE)
    }
}

internal fun interface SessionReopener {
    suspend fun reopen(sessionId: String): SessionReopenAssessment
}

internal class SessionOperationInProgressException : IllegalStateException("a session operation is already active")

/**
 * Task17 controller core. It owns UI-visible state and operation fencing, but deliberately owns no
 * CoroutineScope: the ViewModel owns cancellation/lifetime while disk remains recovery truth.
 *
 * Only one opening/activation operation may run at once. cancelCurrent() invalidates the generation
 * before a caller cancels its Job; a late completion from that generation can never publish state.
 * No provider/media API exists here, so reopen cannot accidentally turn an unknown SENT request into
 * a retry or make UI lifecycle responsible for media ownership.
 */
internal class TranslationSessionController(
    private val activeSessionOwner: ActiveSessionOwner,
    private val reopener: SessionReopener,
) {
    private val operationInFlight = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val stateLock = Any()

    private val mutableState = MutableStateFlow(TranslationSessionUiState.idle())
    val state: StateFlow<TranslationSessionUiState> = mutableState.asStateFlow()

    @Volatile
    private var stableState: TranslationSessionUiState = TranslationSessionUiState.idle()

    suspend fun resumeActiveSession(): TranslationSessionUiState {
        val operation = beginOperation()
        return try {
            val sessionId = try {
                activeSessionOwner.readActiveSessionId()
            } catch (_: Exception) {
                return publishFailureIfCurrent(operation, null, SessionControllerFailure.ACTIVE_POINTER_READ)
            }

            if (sessionId == null) {
                return publishStableIfCurrent(operation, TranslationSessionUiState.idle())
            }
            publishOpeningIfCurrent(operation, sessionId)
            reopenAndPublish(operation, sessionId)
        } finally {
            operationInFlight.set(false)
        }
    }

    suspend fun activateAndResume(
        sessionId: String,
        expectedActiveSessionId: String?,
    ): TranslationSessionUiState {
        val operation = beginOperation()
        return try {
            publishOpeningIfCurrent(operation, sessionId)
            try {
                activeSessionOwner.activateSession(sessionId, expectedActiveSessionId)
            } catch (_: Exception) {
                return publishFailureIfCurrent(operation, sessionId, SessionControllerFailure.ACTIVE_POINTER_WRITE)
            }
            reopenAndPublish(operation, sessionId)
        } finally {
            operationInFlight.set(false)
        }
    }

    /**
     * Invalidates the current operation before the owning ViewModel cancels its Job. This method is
     * safe to call even when no operation is active; it never mutates durable state.
     */
    fun cancelCurrent(): TranslationSessionUiState = synchronized(stateLock) {
        generation.incrementAndGet()
        mutableState.value = stableState
        stableState
    }

    private fun beginOperation(): Long {
        if (!operationInFlight.compareAndSet(false, true)) throw SessionOperationInProgressException()
        val operation = generation.incrementAndGet()
        synchronized(stateLock) {
            if (generation.get() == operation) {
                mutableState.value = TranslationSessionUiState(
                    phase = SessionControllerPhase.OPENING,
                    sessionId = stableState.sessionId,
                )
            }
        }
        return operation
    }

    private fun publishOpeningIfCurrent(operation: Long, sessionId: String) = synchronized(stateLock) {
        if (generation.get() == operation) {
            mutableState.value = TranslationSessionUiState(
                phase = SessionControllerPhase.OPENING,
                sessionId = sessionId,
            )
        }
    }

    private suspend fun reopenAndPublish(operation: Long, sessionId: String): TranslationSessionUiState {
        val assessment = try {
            reopener.reopen(sessionId)
        } catch (_: Exception) {
            return publishFailureIfCurrent(operation, sessionId, SessionControllerFailure.REOPEN)
        }
        return publishStableIfCurrent(operation, stateForAssessment(sessionId, assessment))
    }

    private fun publishFailureIfCurrent(
        operation: Long,
        sessionId: String?,
        failure: SessionControllerFailure,
    ): TranslationSessionUiState = publishStableIfCurrent(
        operation,
        TranslationSessionUiState(
            phase = SessionControllerPhase.FAILED,
            sessionId = sessionId,
            failure = failure,
        ),
    )

    private fun publishStableIfCurrent(
        operation: Long,
        candidate: TranslationSessionUiState,
    ): TranslationSessionUiState = synchronized(stateLock) {
        if (generation.get() != operation) return@synchronized stableState
        stableState = candidate
        mutableState.value = candidate
        candidate
    }

    private fun stateForAssessment(
        sessionId: String,
        assessment: SessionReopenAssessment,
    ): TranslationSessionUiState {
        val blocker = blockerFor(assessment)
        return if (blocker == null) {
            TranslationSessionUiState(
                phase = SessionControllerPhase.READY,
                sessionId = sessionId,
                assessment = assessment,
            )
        } else {
            TranslationSessionUiState(
                phase = SessionControllerPhase.BLOCKED,
                sessionId = sessionId,
                assessment = assessment,
                blocker = blocker,
            )
        }
    }

    private fun blockerFor(assessment: SessionReopenAssessment): SessionBlocker? {
        when (assessment.sttDisposition) {
            SttReopenDisposition.UNKNOWN_REMOTE_OUTCOME -> return SessionBlocker.STT_UNKNOWN_REMOTE_OUTCOME
            SttReopenDisposition.CORRUPT_JOURNAL -> return SessionBlocker.CORRUPT_DURABLE_STATE
            SttReopenDisposition.STALE_ATTEMPT -> return SessionBlocker.STALE_STATE
            else -> Unit
        }
        when (assessment.snapshotAvailability) {
            SourceSnapshotAvailability.CORRUPT_BINDING -> return SessionBlocker.CORRUPT_DURABLE_STATE
            SourceSnapshotAvailability.STALE_OBSERVATION -> return SessionBlocker.STALE_STATE
            SourceSnapshotAvailability.NOT_BOUND,
            SourceSnapshotAvailability.AVAILABLE,
            -> Unit
        }
        return when (assessment.source.availability) {
            SourceAvailability.UNBOUND -> SessionBlocker.NO_SOURCE
            SourceAvailability.LEGACY_UNBOUND -> SessionBlocker.LEGACY_UNBOUND
            SourceAvailability.CHECK_REQUIRED -> SessionBlocker.CHECK_REQUIRED
            SourceAvailability.STALE_OBSERVATION -> SessionBlocker.STALE_STATE
            SourceAvailability.CORRUPT_BINDING -> SessionBlocker.CORRUPT_DURABLE_STATE
            SourceAvailability.PERMISSION_MISSING -> SessionBlocker.SOURCE_PERMISSION_MISSING
            SourceAvailability.SOURCE_MISSING -> SessionBlocker.SOURCE_MISSING
            SourceAvailability.SOURCE_CHANGED -> SessionBlocker.SOURCE_CHANGED
            SourceAvailability.IO_FAILURE -> SessionBlocker.SOURCE_IO_FAILURE
            SourceAvailability.UNSUPPORTED -> SessionBlocker.UNSUPPORTED_SOURCE
            SourceAvailability.AVAILABLE -> null
        }
    }
}
