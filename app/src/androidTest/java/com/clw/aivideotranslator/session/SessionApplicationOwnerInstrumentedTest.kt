package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.AiVideoTranslatorApplication
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionApplicationOwnerInstrumentedTest {
    private fun application(): AiVideoTranslatorApplication =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AiVideoTranslatorApplication

    @Test fun concurrentCallersShareTheManifestRegisteredApplicationStore() {
        val app = application()
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val results = (1..4).map {
                executor.submit(Callable {
                    check(start.await(10, TimeUnit.SECONDS))
                    app.translationSessions
                })
            }
            start.countDown()
            val stores = results.map { it.get(10, TimeUnit.SECONDS) }
            stores.forEach { assertSame(stores.first(), it) }
            assertSame(app.translationSessions, stores.first())
        } finally { executor.shutdownNow() }
    }

    @Test fun explicitCreationUsesUniquePrivateNonBackupSessionsWithoutSourceActivation() {
        val app = application()
        val created = mutableListOf<SessionManifest>()
        val root = File(app.noBackupFilesDir, AiVideoTranslatorApplication.SESSION_DIRECTORY)
        try {
            repeat(2) { created += app.createTranslationSession() }
            assertNotEquals(created[0].sessionId, created[1].sessionId)
            created.forEach { manifest ->
                assertEquals(SourceBindingState.UNBOUND, manifest.sourceBindingState)
                assertEquals(0L, manifest.epoch)
                assertEquals(0L, manifest.revision)
                assertTrue(manifest.activeEntryRefs.isEmpty())
                assertNull(manifest.activeSourceAttachmentRef)
                assertNull(manifest.activeSourceSnapshotRef)
                assertTrue(File(root, "${manifest.sessionId}/manifest.json").isFile)
                assertEquals(manifest, app.translationSessions.readManifest(manifest.sessionId))
            }
        } finally {
            // Delete only this test's unique sessions; never sweep a live application root.
            created.forEach { File(root, it.sessionId).deleteRecursively() }
        }
    }
}
