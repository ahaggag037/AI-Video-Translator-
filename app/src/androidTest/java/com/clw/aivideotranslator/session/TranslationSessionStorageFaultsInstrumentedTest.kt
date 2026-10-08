package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionStorageFaultsInstrumentedTest {
    private fun root(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "x005-storage-$name-${UUID.randomUUID()}")
    }

    @Test fun enospcDuringManifestAtomicCommitPreservesLastValidManifest() {
        val root = root("manifest-enospc")
        try {
            var failManifest = false
            val injector = object : SessionStoreFaultInjector {
                override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit

                override fun afterAtomicPayloadWritten(file: File) {
                    if (failManifest && file.name == "manifest.json") throw IOException("ENOSPC")
                }
            }
            val store = TranslationSessionStore(root, injector)
            store.createSession("session-1")
            failManifest = true

            var failed = false
            try {
                store.bumpEpoch("session-1", expectedRevision = 0)
            } catch (_: IOException) {
                failed = true
            }
            assertTrue(failed)

            val restarted = TranslationSessionStore(root)
            assertEquals(0L, restarted.readManifest("session-1").revision)
            assertEquals(0L, restarted.readManifest("session-1").epoch)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun enospcDuringSentAtomicCommitPreservesPreparedReceipt() {
        val root = root("receipt-enospc")
        try {
            var failAttempt = false
            val injector = object : SessionStoreFaultInjector {
                override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit

                override fun afterAtomicPayloadWritten(file: File) {
                    if (failAttempt && file.name == "attempt-1.json") throw IOException("ENOSPC")
                }
            }
            val store = TranslationSessionStore(root, injector)
            val manifest = store.createSession("session-1")
            val prepared = RequestReceipt(
                attemptId = "attempt-1",
                sessionId = "session-1",
                unitId = "u1",
                epoch = manifest.epoch,
                requestSignature = "sig-1",
                expectedManifestRevision = manifest.revision,
                expectedActiveEntryRevisionId = null,
                phase = RequestReceiptPhase.PREPARED,
            )
            store.writeReceipt(prepared)
            failAttempt = true

            var failed = false
            try {
                store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
            } catch (_: IOException) {
                failed = true
            }
            assertTrue(failed)

            val restarted = TranslationSessionStore(root)
            assertEquals(RequestReceiptPhase.PREPARED, restarted.readReceipt("session-1", "attempt-1").phase)
            assertEquals(0L, restarted.readManifest("session-1").revision)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun corruptManifestFailsClosedBeforeSessionMutation() {
        val root = root("corrupt-manifest")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            File(root, "session-1/manifest.json").writeText("{not-json", Charsets.UTF_8)

            var readFailedClosed = false
            try {
                store.readManifest("session-1")
            } catch (_: Throwable) {
                readFailedClosed = true
            }
            assertTrue(readFailedClosed)

            var mutationFailedClosed = false
            try {
                store.bumpEpoch("session-1", expectedRevision = 0)
            } catch (_: Throwable) {
                mutationFailedClosed = true
            }
            assertTrue(mutationFailedClosed)
            assertTrue(File(root, "session-1/entries").listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun corruptActiveEntryFailsClosedWithoutManifestRewrite() {
        val root = root("corrupt-entry")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val machine = MachineTranslationRevision("machine-1", "ترجمة آلية", "sig-1")
            val entry = StoredTranslationEntry(
                revisionId = "entry-1",
                record = TranslationRecord(
                    unitId = "u1",
                    machineRevisions = listOf(machine),
                    activeMachineRevisionId = machine.id,
                    manualRevision = null,
                    reviewState = TranslationReviewState.MACHINE_CANDIDATE,
                ),
            )
            val committed = store.commitEntry("session-1", expectedRevision = 0, entry = entry)
            assertEquals(1L, committed.revision)
            assertEquals("entry-1", committed.activeEntryRefs["u1"])

            File(root, "session-1/entries/u1/entry-1.json").writeText("{not-json", Charsets.UTF_8)

            var failedClosed = false
            try {
                store.readActiveEntry("session-1", "u1")
            } catch (_: Throwable) {
                failedClosed = true
            }
            assertTrue(failedClosed)

            val manifestAfterFailure = store.readManifest("session-1")
            assertEquals(1L, manifestAfterFailure.revision)
            assertEquals("entry-1", manifestAfterFailure.activeEntryRefs["u1"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun schemaV1RestoreAfterRestartPreservesManualAndMachineHistory() {
        val root = root("schema-restore")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val machine = MachineTranslationRevision("machine-1", "ترجمة آلية", "sig-1")
            val manual = ManualTranslationRevision(
                id = "manual-1",
                text = "تصحيح يدوي",
                basedOnSourceTextHash = "source-hash",
                basedOnMachineRevisionId = machine.id,
            )
            val entry = StoredTranslationEntry(
                revisionId = "entry-1",
                record = TranslationRecord(
                    unitId = "u1",
                    machineRevisions = listOf(machine),
                    activeMachineRevisionId = machine.id,
                    manualRevision = manual,
                    reviewState = TranslationReviewState.APPROVED,
                ),
            )
            store.commitEntry("session-1", expectedRevision = 0, entry = entry)

            val restarted = TranslationSessionStore(root)
            val restoredManifest = restarted.readManifest("session-1")
            val restoredEntry = restarted.readActiveEntry("session-1", "u1")
            assertEquals(1L, restoredManifest.revision)
            assertEquals("entry-1", restoredManifest.activeEntryRefs["u1"])
            assertEquals(entry, restoredEntry)
            assertEquals("تصحيح يدوي", restoredEntry?.record?.effectiveText())
            assertEquals("ترجمة آلية", restoredEntry?.record?.machineRevisions?.single()?.text)
        } finally {
            root.deleteRecursively()
        }
    }
}
