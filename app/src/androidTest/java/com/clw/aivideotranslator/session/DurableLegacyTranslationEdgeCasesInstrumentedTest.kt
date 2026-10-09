package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurableLegacyTranslationEdgeCasesInstrumentedTest {
    private fun withStore(block: (File, TranslationSessionStore, TranslationRequestPlanStore) -> Unit) {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "durable-translation-edge-${UUID.randomUUID()}",
        )
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            block(root, store, TranslationRequestPlanStore(root) { store.readManifest(it); Unit })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun unit(): LegacyParityTranslationUnit = LegacyParityTranslationPlanner.plan(
        NvidiaSttResult(
            transcript = "Hello world.",
            words = listOf(
                NvidiaWord("Hello", 0, 180, 0.95),
                NvidiaWord("world.", 200, 400, 0.95),
            ),
            httpStatus = 200,
        )
    ).single()

    private fun candidate() = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = "مرحبًا بالعالم.",
    )

    @Test fun durablePreparedReopenSubmitsExactlyOnce() = withStore { _, store, planStore ->
        val unit = unit()
        planStore.publish("session-1", unit.requestPlan)
        val manifest = store.readManifest("session-1")
        store.writeReceipt(
            RequestReceipt(
                attemptId = "attempt-prepared",
                sessionId = "session-1",
                unitId = unit.requestPlan.unitId,
                epoch = manifest.epoch,
                requestSignature = unit.requestPlan.requestSignature,
                expectedManifestRevision = manifest.revision,
                expectedActiveEntryRevisionId = null,
                phase = RequestReceiptPhase.PREPARED,
            )
        )
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertTrue(result.completed)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, result.units.single().disposition)
        assertEquals(RequestReceiptPhase.RECEIVED, store.readReceipt("session-1", "attempt-prepared").phase)
    }

    @Test fun structuredCancellationStaysSentAndReopenDoesNotPostAgain() = withStore { _, store, planStore ->
        val unit = unit()
        var submitCount = 0
        val first = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                TranslationProviderOutcome(
                    transport = TransportOutcome.CANCELLED,
                    protocol = ProtocolOutcome.NO_RESPONSE,
                    diagnosticCode = "CANCELLED_AFTER_SENT",
                )
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, first.units.single().disposition)
        assertEquals(RequestReceiptPhase.SENT, store.listReceipts("session-1").single().phase)

        val reopened = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, reopened.units.single().disposition)
    }

    @Test fun historicalMatchingMachineRevisionDoesNotReuseInactiveText() = withStore { _, store, planStore ->
        val unit = unit()
        val record = TranslationRecord(
            unitId = unit.requestPlan.unitId,
            machineRevisions = listOf(
                MachineTranslationRevision(
                    id = "machine-old",
                    text = "old matching text",
                    requestSignature = unit.requestPlan.requestSignature,
                ),
                MachineTranslationRevision(
                    id = "machine-current",
                    text = "current different text",
                    requestSignature = "different-request-signature",
                ),
            ),
            activeMachineRevisionId = "machine-current",
            manualRevision = null,
            reviewState = TranslationReviewState.MACHINE_CANDIDATE,
        )
        store.commitEntry(
            sessionId = "session-1",
            expectedRevision = 0L,
            entry = StoredTranslationEntry(
                revisionId = "entry-current",
                record = record,
            ),
        )
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }

        assertEquals(0, submitCount)
        assertFalse(result.completed)
        assertEquals(DurableTranslationUnitDisposition.STALE_STATE, result.units.single().disposition)
        assertEquals(
            "current different text",
            store.readActiveEntry("session-1", unit.requestPlan.unitId)!!.record.effectiveText(),
        )
        assertTrue(store.listReceipts("session-1").isEmpty())
    }

    @Test fun receivedAttemptResumesFromPersistedPlanAfterProcessDeathWithoutLegacyTiming() = withStore { root, store, planStore ->
        val unit = unit()
        planStore.publish("session-1", unit.requestPlan)
        val manifest = store.readManifest("session-1")
        val prepared = store.writeReceipt(
            RequestReceipt(
                attemptId = "attempt-received-restart",
                sessionId = "session-1",
                unitId = unit.requestPlan.unitId,
                epoch = manifest.epoch,
                requestSignature = unit.requestPlan.requestSignature,
                expectedManifestRevision = manifest.revision,
                expectedActiveEntryRevisionId = null,
                phase = RequestReceiptPhase.PREPARED,
            )
        )
        val sent = store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        store.writeReceipt(sent.copy(phase = RequestReceiptPhase.RECEIVED, outcome = candidate()))

        // Simulate a fresh process: no NvidiaSttResult/SourceUnit object is carried across this fence.
        val reopenedStore = TranslationSessionStore(root)
        val reopenedPlanStore = TranslationRequestPlanStore(root) { reopenedStore.readManifest(it); Unit }
        var submitCount = 0
        val result = runBlocking {
            DurableLegacyTranslationOperation(reopenedStore, reopenedPlanStore).resumeAttempt(
                sessionId = "session-1",
                attemptId = "attempt-received-restart",
            ) {
                submitCount++
                error("RECEIVED restart must adopt from durable plan without provider POST")
            }.getOrThrow()
        }

        assertEquals(0, submitCount)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, result.disposition)
        assertEquals("مرحبًا بالعالم.", result.effectiveText)
        assertEquals(
            "مرحبًا بالعالم.",
            reopenedStore.readActiveEntry("session-1", unit.requestPlan.unitId)!!.record.effectiveText(),
        )
    }
}
