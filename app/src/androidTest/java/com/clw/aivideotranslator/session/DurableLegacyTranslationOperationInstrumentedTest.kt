package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurableLegacyTranslationOperationInstrumentedTest {
    private fun withStore(block: (File, TranslationSessionStore, TranslationRequestPlanStore) -> Unit) {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "durable-translation-${UUID.randomUUID()}",
        )
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val planStore = TranslationRequestPlanStore(root) { sessionId -> store.readManifest(sessionId); Unit }
            block(root, store, planStore)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun planned(sourceText: String = "Hello world."): LegacyParityTranslationUnit {
        val words = sourceText.split(' ').mapIndexed { index, token ->
            val start = index * 220L
            NvidiaWord(token, start, start + 180L, 0.95)
        }
        val result = NvidiaSttResult(sourceText, words, 200)
        return LegacyParityTranslationPlanner.plan(result).single()
    }

    private fun candidate(text: String = "مرحبًا بالعالم.") = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = text,
        httpStatus = 200,
    )

    @Test fun freshCandidateIsAdoptedThenReusedWithoutSecondSubmission() = withStore { _, store, planStore ->
        val unit = planned()
        val operation = DurableLegacyTranslationOperation(store, planStore)
        var submitCount = 0

        val first = runBlocking {
            operation.execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertTrue(first.completed)
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, first.units.single().disposition)
        assertEquals("مرحبًا بالعالم.", store.readActiveEntry("session-1", unit.requestPlan.unitId)!!.record.effectiveText())

        val second = runBlocking {
            operation.execute("session-1", listOf(unit)) {
                submitCount++
                error("known matching success must not submit again")
            }.getOrThrow()
        }
        assertTrue(second.completed)
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.REUSED_ENTRY, second.units.single().disposition)
        assertEquals(1, store.listReceipts("session-1").size)
        assertEquals(unit.requestPlan, planStore.read("session-1", unit.requestPlan.unitId, unit.requestPlan.requestSignature))
    }

    @Test fun durableSentBlocksWithoutSubmission() = withStore { _, store, planStore ->
        val unit = planned()
        val plan = unit.requestPlan
        planStore.publish("session-1", plan)
        val manifest = store.readManifest("session-1")
        val prepared = RequestReceipt(
            attemptId = "attempt-sent",
            sessionId = "session-1",
            unitId = plan.unitId,
            epoch = manifest.epoch,
            requestSignature = plan.requestSignature,
            expectedManifestRevision = manifest.revision,
            expectedActiveEntryRevisionId = null,
            phase = RequestReceiptPhase.PREPARED,
        )
        store.writeReceipt(prepared)
        store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }

        assertFalse(result.completed)
        assertEquals(0, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, result.units.single().disposition)
        assertEquals(RequestReceiptPhase.SENT, store.readReceipt("session-1", "attempt-sent").phase)
    }

    @Test fun durableReceivedAdoptsLocallyWithoutSubmission() = withStore { _, store, planStore ->
        val unit = planned()
        val plan = unit.requestPlan
        planStore.publish("session-1", plan)
        val manifest = store.readManifest("session-1")
        val prepared = RequestReceipt(
            attemptId = "attempt-received",
            sessionId = "session-1",
            unitId = plan.unitId,
            epoch = manifest.epoch,
            requestSignature = plan.requestSignature,
            expectedManifestRevision = manifest.revision,
            expectedActiveEntryRevisionId = null,
            phase = RequestReceiptPhase.PREPARED,
        )
        store.writeReceipt(prepared)
        val sent = store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        store.writeReceipt(sent.copy(phase = RequestReceiptPhase.RECEIVED, outcome = candidate()))
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                error("durable RECEIVED must adopt locally")
            }.getOrThrow()
        }

        assertTrue(result.completed)
        assertEquals(0, submitCount)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, result.units.single().disposition)
        assertEquals("مرحبًا بالعالم.", store.readActiveEntry("session-1", plan.unitId)!!.record.effectiveText())
    }

    @Test fun structuredUnknownTransportStaysSentAndStopsBatch() = withStore { _, store, planStore ->
        val unit = planned()
        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                TranslationProviderOutcome(
                    transport = TransportOutcome.UNKNOWN_AFTER_SUBMISSION,
                    protocol = ProtocolOutcome.NO_RESPONSE,
                    diagnosticCode = "REMOTE_OUTCOME_UNCERTAIN",
                )
            }.getOrThrow()
        }

        assertFalse(result.completed)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, result.units.single().disposition)
        val receipt = store.listReceipts("session-1").single()
        assertEquals(RequestReceiptPhase.SENT, receipt.phase)
        assertNull(receipt.outcome)
    }

    @Test fun integrityReviewStopsWithoutAdoptingCandidate() = withStore { _, store, planStore ->
        val unit = planned("Order 5")
        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                candidate("اطلب الآن")
            }.getOrThrow()
        }

        assertFalse(result.completed)
        assertEquals(DurableTranslationUnitDisposition.REVIEW_REQUIRED, result.units.single().disposition)
        assertNull(store.readActiveEntry("session-1", unit.requestPlan.unitId))
        assertEquals(RequestReceiptPhase.RECEIVED, store.listReceipts("session-1").single().phase)
    }
}
