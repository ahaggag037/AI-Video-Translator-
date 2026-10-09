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
        return LegacyParityTranslationPlanner.plan(NvidiaSttResult(sourceText, words, 200)).single()
    }

    private fun twoUnits(): List<LegacyParityTranslationUnit> = LegacyParityTranslationPlanner.plan(
        NvidiaSttResult(
            transcript = "Order 5. Second unit.",
            words = listOf(
                NvidiaWord("Order", 0, 150, 0.95),
                NvidiaWord("5.", 170, 300, 0.95),
                NvidiaWord("Second", 900, 1_100, 0.95),
                NvidiaWord("unit.", 1_120, 1_300, 0.95),
            ),
            httpStatus = 200,
        )
    )

    private fun candidate(text: String = "مرحبًا بالعالم.") = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = text,
        httpStatus = 200,
    )

    private fun prepared(store: TranslationSessionStore, unit: LegacyParityTranslationUnit, attemptId: String): RequestReceipt {
        val manifest = store.readManifest("session-1")
        return RequestReceipt(
            attemptId = attemptId,
            sessionId = "session-1",
            unitId = unit.requestPlan.unitId,
            epoch = manifest.epoch,
            requestSignature = unit.requestPlan.requestSignature,
            expectedManifestRevision = manifest.revision,
            expectedActiveEntryRevisionId = manifest.activeEntryRefs[unit.requestPlan.unitId],
            phase = RequestReceiptPhase.PREPARED,
        )
    }

    @Test fun newSuccessUsesOnePostAndSecondOperationReusesWithoutAnotherPost() = withStore { _, store, planStore ->
        val unit = planned()
        val operation = DurableLegacyTranslationOperation(store, planStore)
        var submitCount = 0

        val first = runBlocking {
            operation.execute("session-1", listOf(unit)) { submitCount++; candidate() }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertTrue(first.completed)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, first.units.single().disposition)
        assertEquals(RequestReceiptPhase.RECEIVED, store.listReceipts("session-1").single().phase)
        assertEquals("مرحبًا بالعالم.", store.readActiveEntry("session-1", unit.requestPlan.unitId)!!.record.effectiveText())

        val second = runBlocking {
            operation.execute("session-1", listOf(unit)) {
                submitCount++
                error("compatible success must not submit again")
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertTrue(second.completed)
        assertEquals(DurableTranslationUnitDisposition.REUSED_ENTRY, second.units.single().disposition)
    }

    @Test fun sentReopenReturnsUnknownWithZeroProviderCalls() = withStore { _, store, planStore ->
        val unit = planned()
        planStore.publish("session-1", unit.requestPlan)
        val initial = prepared(store, unit, "attempt-sent")
        store.writeReceipt(initial)
        store.markSentIfCurrent(initial.copy(phase = RequestReceiptPhase.SENT))
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertEquals(0, submitCount)
        assertFalse(result.completed)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, result.units.single().disposition)
        assertEquals(RequestReceiptPhase.SENT, store.readReceipt("session-1", "attempt-sent").phase)
    }

    @Test fun receivedReopenAdoptsLocallyWithZeroProviderCalls() = withStore { _, store, planStore ->
        val unit = planned()
        planStore.publish("session-1", unit.requestPlan)
        val initial = prepared(store, unit, "attempt-received")
        store.writeReceipt(initial)
        val sent = store.markSentIfCurrent(initial.copy(phase = RequestReceiptPhase.SENT))
        store.writeReceipt(sent.copy(phase = RequestReceiptPhase.RECEIVED, outcome = candidate()))
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                error("RECEIVED must recover locally")
            }.getOrThrow()
        }
        assertEquals(0, submitCount)
        assertTrue(result.completed)
        assertEquals(DurableTranslationUnitDisposition.ADOPTED, result.units.single().disposition)
    }

    @Test fun crashAfterSentReopenNeverBlindlyReposts() = withStore { _, store, planStore ->
        val unit = planned()
        var submitCount = 0
        val first = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                error("simulated process death after possible POST")
            }
        }
        assertTrue(first.isFailure)
        assertEquals(1, submitCount)
        assertEquals(RequestReceiptPhase.SENT, store.listReceipts("session-1").single().phase)

        val reopened = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, reopened.units.single().disposition)
        assertEquals(RequestReceiptPhase.SENT, store.listReceipts("session-1").single().phase)
    }

    @Test fun unknownAfterSubmissionNeverPersistsReceivedAndNeverRepostsOnReopen() = withStore { _, store, planStore ->
        val unit = planned()
        var submitCount = 0
        val first = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                TranslationProviderOutcome(
                    transport = TransportOutcome.UNKNOWN_AFTER_SUBMISSION,
                    protocol = ProtocolOutcome.NO_RESPONSE,
                    diagnosticCode = "REMOTE_OUTCOME_UNCERTAIN",
                )
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, first.units.single().disposition)
        assertEquals(RequestReceiptPhase.SENT, store.listReceipts("session-1").single().phase)
        assertNull(store.listReceipts("session-1").single().outcome)

        val reopened = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, reopened.units.single().disposition)
    }

    @Test fun reviewRequiredStopsBatchBeforeSecondUnitSubmission() = withStore { _, store, planStore ->
        val units = twoUnits()
        var submitCount = 0
        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", units) { plan ->
                submitCount++
                if (plan.unitId == units.first().requestPlan.unitId) candidate("اطلب الآن") else candidate("الوحدة الثانية")
            }.getOrThrow()
        }
        assertEquals(1, submitCount)
        assertFalse(result.completed)
        assertEquals(1, result.units.size)
        assertEquals(DurableTranslationUnitDisposition.REVIEW_REQUIRED, result.units.single().disposition)
        assertNull(store.readActiveEntry("session-1", units.first().requestPlan.unitId))
        assertTrue(store.listReceipts("session-1").none { it.unitId == units[1].requestPlan.unitId })
    }

    @Test fun corruptDurablePlanFailsClosedBeforeAnyProviderCall() = withStore { root, store, planStore ->
        val unit = planned()
        planStore.publish("session-1", unit.requestPlan)
        File(root, "session-1/plans/${unit.requestPlan.unitId}/${unit.requestPlan.requestSignature}.json").writeText("{")
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }
        }
        assertTrue(result.isFailure)
        assertEquals(0, submitCount)
        assertTrue(store.listReceipts("session-1").isEmpty())
    }

    @Test fun oversizedDurablePlanFailsClosedBeforeAnyProviderCall() = withStore { root, store, planStore ->
        val unit = planned()
        planStore.publish("session-1", unit.requestPlan)
        File(root, "session-1/plans/${unit.requestPlan.unitId}/${unit.requestPlan.requestSignature}.json")
            .writeText("x".repeat(TranslationRequestPlanCodec.MAX_BYTES + 1))
        var submitCount = 0

        val result = runBlocking {
            DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                submitCount++
                candidate()
            }
        }
        assertTrue(result.isFailure)
        assertEquals(0, submitCount)
        assertTrue(store.listReceipts("session-1").isEmpty())
    }
}
