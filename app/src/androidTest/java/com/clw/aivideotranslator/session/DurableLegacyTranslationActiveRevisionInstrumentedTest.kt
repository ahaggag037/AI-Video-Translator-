package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurableLegacyTranslationActiveRevisionInstrumentedTest {
    @Test
    fun historicalCompatibleMachineRevisionCannotReuseDifferentActiveMachineText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "durable-active-revision-${UUID.randomUUID()}")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val planStore = TranslationRequestPlanStore(root) { sessionId -> store.readManifest(sessionId); Unit }
            val unit = LegacyParityTranslationPlanner.plan(
                NvidiaSttResult(
                    transcript = "Hello world.",
                    words = listOf(
                        NvidiaWord("Hello", 0, 180, 0.95),
                        NvidiaWord("world.", 220, 400, 0.95),
                    ),
                    httpStatus = 200,
                )
            ).single()

            val record = TranslationRecord(
                unitId = unit.requestPlan.unitId,
                machineRevisions = listOf(
                    MachineTranslationRevision(
                        id = "machine-historical",
                        text = "ترجمة تاريخية متوافقة",
                        requestSignature = unit.requestPlan.requestSignature,
                    ),
                    MachineTranslationRevision(
                        id = "machine-active",
                        text = "ترجمة فعالة من طلب مختلف",
                        requestSignature = "different-request-signature",
                    ),
                ),
                activeMachineRevisionId = "machine-active",
                manualRevision = null,
                reviewState = TranslationReviewState.APPROVED,
            )
            store.commitEntry(
                sessionId = "session-1",
                expectedRevision = store.readManifest("session-1").revision,
                entry = StoredTranslationEntry(
                    revisionId = "entry-active",
                    record = record,
                ),
            )

            var submitCount = 0
            val result = runBlocking {
                DurableLegacyTranslationOperation(store, planStore).execute("session-1", listOf(unit)) {
                    submitCount++
                    error("stale active machine state must not submit or reuse historical success")
                }.getOrThrow()
            }

            assertEquals(0, submitCount)
            assertFalse(result.completed)
            assertEquals(DurableTranslationUnitDisposition.STALE_STATE, result.units.single().disposition)
            assertNull(result.units.single().effectiveText)
            assertEquals("ترجمة فعالة من طلب مختلف", store.readActiveEntry("session-1", unit.requestPlan.unitId)!!.record.effectiveText())
        } finally {
            root.deleteRecursively()
        }
    }
}
