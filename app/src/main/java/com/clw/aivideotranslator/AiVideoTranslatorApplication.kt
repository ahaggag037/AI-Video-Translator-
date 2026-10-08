package com.clw.aivideotranslator

import android.app.Application
import com.clw.aivideotranslator.session.SessionManifest
import com.clw.aivideotranslator.session.TranslationSessionStore
import java.io.File
import java.util.UUID

/**
 * One process-owned writer for the durable session root. Lazy construction does not activate
 * durable UI resume or create/migrate a session on app launch. Production callers must share this
 * owner instead of creating stores per Activity/operation. Multi-process writing is unsupported.
 */
class AiVideoTranslatorApplication : Application() {
    internal val translationSessions: TranslationSessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TranslationSessionStore(File(noBackupFilesDir, SESSION_DIRECTORY))
    }

    /** Explicit opt-in by a future controller; never binds old in-memory/manual work to new media. */
    internal fun createTranslationSession(): SessionManifest =
        translationSessions.createSession("session-${UUID.randomUUID()}")

    internal companion object {
        const val SESSION_DIRECTORY = "translation_sessions"
    }
}
