package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionStoreInstrumentedTest {
    private class SimulatedStop : RuntimeException("simulated stop after immutable entry publication")

    private fun root(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "x005-$name-${UUID.randomUUID()}")
    }

    private fun requestPlan(source: String = "Hello world."): TranslationRequestPlan = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u1",
            orderedWordIds = listOf("w1"),
            sourceText = source,
            sourceTextHash = sha256Utf8(source),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "android-x005-test",
        ),
    )

    private fun prepared(plan: TranslationRequestPlan) = RequestReceipt(
        attemptId = "attempt-1",
        sessionId = "session-1",
        unitId = plan.unitId,
        epoch = 0,
        requestSignature = plan.requestSignature,
        expectedManifestRevision = 0,
        expectedActiveEntryRevisionId = null,
        phase = RequestReceiptPhase.PREPARED,
    )

    private fun candidate() = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = "مرحبًا بالعالم.",
    )

    private fun persistReceived(store: TranslationSessionStore, plan: TranslationRequestPlan): RequestReceipt {
        val prepared = prepared(plan)
        store.writeReceipt(prepared)
        val sent = prepared.copy(phase = RequestReceiptPhase.SENT)
        store.writeReceipt(sent)
        val received = sent.copy(phase = RequestReceiptPhase.RECEIVED, outcome = candidate())
        store.writeReceipt(received)
        return received
    }

    @Test fun faultAfterImmutablePublishRetriesSameRevisionAndAdoptsOnce() {
        val root = root("publish-window")
        try {
            val plan = requestPlan()
            var injected = false
            val crashingStore = TranslationSessionStore(
                root,
                SessionStoreFaultInjector { _, _, _ ->
                    if (!injected) {
                        injected = true
                        throw SimulatedStop()
                    }
                },
            )
            crashingStore.createSession("session-1")
            val received = persistReceived(crashingStore, plan)
            val expectedIds = RecoveryRevisionIdentity.forReceipt(received)

            var stopped = false
            try {
                crashingStore.adoptRecoveredCandidate("session-1", "attempt-1", plan)
            } catch (_: SimulatedStop) {
                stopped = true
            }
            assertTrue(stopped)
            assertEquals(0L, crashingStore.readManifest("session-1").revision)
            assertNull(crashingStore.readActiveEntry("session-1", "u1"))

            val immutableDir = File(root, "session-1/entries/u1")
            val publishedBeforeRetry = immutableDir.listFiles { file -> file.extension == "json" }?.toList().orEmpty()
            assertEquals(1, publishedBeforeRetry.size)
            assertEquals("${expectedIds.entryRevisionId}.json", publishedBeforeRetry.single().name)

            val restartedStore = TranslationSessionStore(root)
            val committed = restartedStore.adoptRecoveredCandidate("session-1", "attempt-1", plan)
            assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, committed.recoveryPlan.action)
            assertEquals(1L, committed.manifest.revision)
            assertEquals(expectedIds.entryRevisionId, committed.manifest.activeEntryRefs["u1"])
            assertEquals(expectedIds.entryRevisionId, committed.committedEntry?.revisionId)
            assertEquals("مرحبًا بالعالم.", restartedStore.readActiveEntry("session-1", "u1")?.record?.effectiveText())

            val publishedAfterRetry = immutableDir.listFiles { file -> file.extension == "json" }?.toList().orEmpty()
            assertEquals(1, publishedAfterRetry.size)

            val secondRetry = restartedStore.adoptRecoveredCandidate("session-1", "attempt-1", plan)
            assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, secondRetry.recoveryPlan.action)
            assertEquals(1L, secondRetry.manifest.revision)
            assertNull(secondRetry.committedEntry)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun lateReceivedAfterEpochBumpIsPersistedForAuditButCannotAdopt() {
        val root = root("late-callback")
        try {
            val store = TranslationSessionStore(root)
            val plan = requestPlan()
            store.createSession("session-1")
            val prepared = prepared(plan)
            store.writeReceipt(prepared)
            val sent = prepared.copy(phase = RequestReceiptPhase.SENT)
            store.writeReceipt(sent)

            val bumped = store.bumpEpoch("session-1", expectedRevision = 0)
            assertEquals(1L, bumped.revision)
            assertEquals(1L, bumped.epoch)

            val lateReceived = sent.copy(phase = RequestReceiptPhase.RECEIVED, outcome = candidate())
            store.writeReceipt(lateReceived)
            assertEquals(RequestReceiptPhase.RECEIVED, store.readReceipt("session-1", "attempt-1").phase)

            val result = store.adoptRecoveredCandidate("session-1", "attempt-1", plan)
            assertEquals(ReceiptRecoveryAction.STALE_RECEIPT, result.recoveryPlan.action)
            assertEquals(AdoptionFenceResult.STALE_EPOCH, result.recoveryPlan.fenceResult)
            assertEquals(1L, result.manifest.revision)
            assertFalse(result.manifest.activeEntryRefs.containsKey("u1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun corruptReceiptFailsClosedWithoutManifestMutation() {
        val root = root("corrupt-receipt")
        try {
            val store = TranslationSessionStore(root)
            val plan = requestPlan()
            store.createSession("session-1")
            val prepared = prepared(plan)
            store.writeReceipt(prepared)
            File(root, "session-1/requests/attempt-1.json").writeText("{not-json", Charsets.UTF_8)

            var failedClosed = false
            try {
                store.readReceipt("session-1", "attempt-1")
            } catch (_: Throwable) {
                failedClosed = true
            }
            assertTrue(failedClosed)
            val manifest = store.readManifest("session-1")
            assertEquals(0L, manifest.revision)
            assertNotNull(manifest.activeEntryRefs)
            assertTrue(manifest.activeEntryRefs.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
