package com.clw.aivideotranslator.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TranslationSessionControllerTest {
    private class FakeOwner(initial: String? = null) : ActiveSessionOwner {
        var current: String? = initial
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        val activations = mutableListOf<Pair<String, String?>>()

        override fun readActiveSessionId(): String? {
            readFailure?.let { throw it }
            return current
        }

        override fun activateSession(sessionId: String, expectedActiveSessionId: String?): String {
            writeFailure?.let { throw it }
            check(current == expectedActiveSessionId) { "stale fake pointer" }
            current = sessionId
            activations += sessionId to expectedActiveSessionId
            return sessionId
        }
    }

    private fun assessment(
        source: SourceAvailability = SourceAvailability.AVAILABLE,
        snapshot: SourceSnapshotAvailability = SourceSnapshotAvailability.NOT_BOUND,
        stt: SttReopenDisposition = SttReopenDisposition.SAFE_TO_SUBMIT,
    ) = SessionReopenAssessment(
        source = SourceResumeAssessment(source),
        snapshotAvailability = snapshot,
        sttDisposition = stt,
    )

    @Test fun noPointerResumesToIdleWithoutCallingReopener() = runBlocking {
        val owner = FakeOwner()
        var reopens = 0
        val controller = TranslationSessionController(owner) {
            reopens++
            assessment()
        }

        val result = controller.resumeActiveSession()

        assertEquals(SessionControllerPhase.IDLE, result.phase)
        assertEquals(result, controller.state.value)
        assertEquals(0, reopens)
    }

    @Test fun validActiveSessionResumesReady() = runBlocking {
        val owner = FakeOwner("session-a")
        val controller = TranslationSessionController(owner) { id ->
            assertEquals("session-a", id)
            assessment()
        }

        val result = controller.resumeActiveSession()

        assertEquals(SessionControllerPhase.READY, result.phase)
        assertEquals("session-a", result.sessionId)
        assertNull(result.blocker)
        assertEquals(result, controller.state.value)
    }

    @Test fun sentAttemptBecomesTypedBlockerNotRetry() = runBlocking {
        val owner = FakeOwner("session-a")
        var reopens = 0
        val controller = TranslationSessionController(owner) {
            reopens++
            assessment(stt = SttReopenDisposition.UNKNOWN_REMOTE_OUTCOME)
        }

        val result = controller.resumeActiveSession()

        assertEquals(1, reopens)
        assertEquals(SessionControllerPhase.BLOCKED, result.phase)
        assertEquals(SessionBlocker.STT_UNKNOWN_REMOTE_OUTCOME, result.blocker)
    }

    @Test fun unadoptedReceivedStateIsNotReportedReady() = runBlocking {
        val controller = TranslationSessionController(FakeOwner("session-a")) {
            assessment(stt = SttReopenDisposition.RECEIVED_AVAILABLE)
        }

        val result = controller.resumeActiveSession()

        assertEquals(SessionControllerPhase.BLOCKED, result.phase)
        assertEquals(SessionBlocker.STT_LOCAL_RECOVERY_REQUIRED, result.blocker)
    }

    @Test fun activateUsesExactPointerCasBeforeReopen() = runBlocking {
        val owner = FakeOwner("session-a")
        var reopened: String? = null
        val controller = TranslationSessionController(owner) { id ->
            reopened = id
            assessment()
        }

        val result = controller.activateAndResume("session-b", expectedActiveSessionId = "session-a")

        assertEquals(listOf("session-b" to "session-a"), owner.activations)
        assertEquals("session-b", owner.current)
        assertEquals("session-b", reopened)
        assertEquals(SessionControllerPhase.READY, result.phase)
        assertEquals("session-b", result.sessionId)
    }

    @Test fun secondOperationIsRejectedWhileFirstIsSuspended() = runBlocking {
        val owner = FakeOwner("session-a")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = TranslationSessionController(owner) {
            started.complete(Unit)
            release.await()
            assessment()
        }

        val first = async { controller.resumeActiveSession() }
        started.await()
        val second = runCatching { controller.resumeActiveSession() }

        assertTrue(second.exceptionOrNull() is SessionOperationInProgressException)
        release.complete(Unit)
        assertEquals(SessionControllerPhase.READY, first.await().phase)
    }

    @Test fun cancelFencePreventsLateResultFromPublishing() = runBlocking {
        val owner = FakeOwner("session-a")
        var delayed = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = TranslationSessionController(owner) {
            if (delayed) {
                started.complete(Unit)
                release.await()
                assessment(source = SourceAvailability.SOURCE_CHANGED)
            } else {
                assessment()
            }
        }

        val initial = controller.resumeActiveSession()
        assertEquals(SessionControllerPhase.READY, initial.phase)

        delayed = true
        val late = async { controller.resumeActiveSession() }
        started.await()
        assertEquals(SessionControllerPhase.OPENING, controller.state.value.phase)

        val restored = controller.cancelCurrent()
        assertEquals(initial, restored)
        assertEquals(initial, controller.state.value)

        release.complete(Unit)
        assertEquals(initial, late.await())
        assertEquals(initial, controller.state.value)
    }

    @Test fun coroutineCancellationPropagatesAndDoesNotPublishFailure() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val controller = TranslationSessionController(FakeOwner("session-a")) {
            started.complete(Unit)
            awaitCancellation()
        }

        val opening = async { controller.resumeActiveSession() }
        started.await()
        controller.cancelCurrent()
        opening.cancel()
        runCatching { opening.await() }

        assertTrue(opening.isCancelled)
        assertEquals(SessionControllerPhase.IDLE, controller.state.value.phase)
        assertNull(controller.state.value.failure)
    }

    @Test fun reopenAndPointerFailuresAreTypedWithoutRawExceptionLeakage() = runBlocking {
        val owner = FakeOwner("session-a")
        owner.readFailure = IllegalStateException("sensitive pointer detail")
        val readController = TranslationSessionController(owner) { assessment() }
        val read = readController.resumeActiveSession()
        assertEquals(SessionControllerPhase.FAILED, read.phase)
        assertEquals(SessionControllerFailure.ACTIVE_POINTER_READ, read.failure)

        val reopenOwner = FakeOwner("session-a")
        val reopenController = TranslationSessionController(reopenOwner) {
            throw IllegalStateException("sensitive reopen detail")
        }
        val reopen = reopenController.resumeActiveSession()
        assertEquals(SessionControllerPhase.FAILED, reopen.phase)
        assertEquals(SessionControllerFailure.REOPEN, reopen.failure)
    }
}
