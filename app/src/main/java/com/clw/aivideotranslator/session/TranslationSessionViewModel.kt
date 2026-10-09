package com.clw.aivideotranslator.session

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity configuration-change owner for Task17. Disk remains recovery truth; this ViewModel owns
 * only cancellable in-process work and delegates UI-visible state to [TranslationSessionController].
 * It has no credential field and performs no provider submission.
 */
internal class TranslationSessionViewModel(
    activeSessionOwner: ActiveSessionOwner,
    store: TranslationSessionStore,
    contentResolver: ContentResolver,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobLock = Any()
    private var activeJob: Job? = null
    private val controller = TranslationSessionController(
        activeSessionOwner = activeSessionOwner,
        reopener = SessionReopener { sessionId ->
            withContext(Dispatchers.IO) {
                SourceSessionCoordinator.reopenSession(contentResolver, store, sessionId).getOrThrow()
            }
        },
    )

    val state = controller.state

    init {
        resumeActiveSession()
    }

    fun resumeActiveSession() {
        launchExclusive { controller.resumeActiveSession() }
    }

    fun activateAndResume(sessionId: String, expectedActiveSessionId: String?) {
        launchExclusive { controller.activateAndResume(sessionId, expectedActiveSessionId) }
    }

    /** Fence publication before cancelling the owning coroutine. */
    fun cancelCurrent() {
        controller.cancelCurrent()
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        synchronized(jobLock) {
            if (activeJob?.isActive == true) return
            val job = scope.launch { block() }
            activeJob = job
            job.invokeOnCompletion {
                synchronized(jobLock) {
                    if (activeJob === job) activeJob = null
                }
            }
        }
    }

    override fun onCleared() {
        controller.cancelCurrent()
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
        scope.cancel()
        super.onCleared()
    }

    internal class Factory(
        private val activeSessionOwner: ActiveSessionOwner,
        private val store: TranslationSessionStore,
        private val contentResolver: ContentResolver,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == TranslationSessionViewModel::class.java) { "unsupported ViewModel class" }
            return TranslationSessionViewModel(activeSessionOwner, store, contentResolver) as T
        }
    }
}
