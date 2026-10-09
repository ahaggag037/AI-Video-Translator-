package com.clw.aivideotranslator

import android.app.Application
import com.clw.aivideotranslator.session.ActiveSessionRegistry
import com.clw.aivideotranslator.session.SessionManifest
import com.clw.aivideotranslator.session.TranslationSessionStore
import java.io.File
import java.util.UUID

/**
 * One process-owned writer for the durable session root. Production callers must share this owner
 * instead of creating stores/active-session registries per Activity or operation. Multi-process
 * writing is unsupported.
 */
class AiVideoTranslatorApplication : Application() {
    private val translationSessionRoot: File by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        File(noBackupFilesDir, SESSION_DIRECTORY)
    }

    internal val translationSessions: TranslationSessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TranslationSessionStore(translationSessionRoot)
    }

    internal val activeTranslationSession: ActiveSessionRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ActiveSessionRegistry(
            sessionsRoot = translationSessionRoot,
            validateSession = { sessionId -> translationSessions.readManifest(sessionId); Unit },
        )
    }

    /** Explicit controller/UI opt-in; never binds old in-memory/manual work to new media. */
    internal fun createTranslationSession(): SessionManifest =
        translationSessions.createSession("session-${UUID.randomUUID()}")

    internal companion object {
        const val SESSION_DIRECTORY = "translation_sessions"
    }
}
