package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveSessionRegistryInstrumentedTest {
    private fun withRoot(block: (File, TranslationSessionStore) -> Unit) {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "active-session-${UUID.randomUUID()}",
        )
        try {
            val store = TranslationSessionStore(root)
            block(root, store)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun registry(root: File, store: TranslationSessionStore, afterWrite: (File) -> Unit = {}) =
        ActiveSessionRegistry(root, validateSession = { store.readManifest(it); Unit }, afterPayloadWritten = afterWrite)

    @Test fun pointerSurvivesRestartAndSwitchRequiresExactCas() = withRoot { root, store ->
        store.createSession("session-a")
        store.createSession("session-b")
        val first = registry(root, store)
        assertNull(first.readActiveSessionId())
        assertEquals("session-a", first.activateSession("session-a", expectedActiveSessionId = null))
        assertEquals("session-a", ActiveSessionRegistry(root, { store.readManifest(it); Unit }).readActiveSessionId())

        val stale = runCatching { first.activateSession("session-b", expectedActiveSessionId = null) }
        assertTrue(stale.isFailure)
        assertEquals("session-a", first.readActiveSessionId())

        assertEquals("session-b", first.activateSession("session-b", expectedActiveSessionId = "session-a"))
        assertEquals("session-b", first.readActiveSessionId())
    }

    @Test fun failedAtomicSwitchPreservesPreviousPointer() = withRoot { root, store ->
        store.createSession("session-a")
        store.createSession("session-b")
        var fail = false
        val registry = registry(root, store) { file ->
            if (fail && file.name == "active_session.json") throw IOException("synthetic pointer crash")
        }
        registry.activateSession("session-a", null)
        fail = true
        val switched = runCatching { registry.activateSession("session-b", "session-a") }
        assertTrue(switched.isFailure)
        fail = false
        assertEquals("session-a", registry.readActiveSessionId())
    }

    @Test fun corruptPointerFailsClosedInsteadOfGuessingFromDirectories() = withRoot { root, store ->
        store.createSession("session-a")
        store.createSession("session-b")
        val registry = registry(root, store)
        registry.activateSession("session-a", null)
        File(root, "active_session.json").writeText("{")
        assertTrue(runCatching { registry.readActiveSessionId() }.isFailure)
    }

    @Test fun pointerToMissingSessionFailsClosed() = withRoot { root, store ->
        store.createSession("session-a")
        val registry = registry(root, store)
        registry.activateSession("session-a", null)
        assertTrue(File(root, "session-a/manifest.json").delete())
        assertTrue(runCatching { registry.readActiveSessionId() }.isFailure)
    }

    @Test fun cannotActivateMissingTargetOrEraseCurrentOwner() = withRoot { root, store ->
        store.createSession("session-a")
        val registry = registry(root, store)
        registry.activateSession("session-a", null)
        val missing = runCatching { registry.activateSession("session-missing", "session-a") }
        assertTrue(missing.isFailure)
        assertEquals("session-a", registry.readActiveSessionId())
    }
}
