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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationAttemptLedgerInstrumentedTest {
    private fun root(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "x005-ledger-$name-${UUID.randomUUID()}")
    }

    private fun plan(): TranslationRequestPlan {
        val source = "Hello world."
        return TranslationPlanner.plan(
            SemanticSourceUnit(
                id = "u1",
                orderedWordIds = listOf("w1"),
                sourceText = source,
                sourceTextHash = sha256Utf8(source),
                sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
                segmentationVersion = "android-x005-ledger",
            ),
        )
    }

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

    @Test fun knownReceivedSuccessAdoptsAfterRestartWithZeroAdditionalPosts() = runBlocking {
        val root = root("known-success")
        try {
            val requestPlan = plan()
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            var postCount = 0

            DurableTranslationAttemptExecutor(store).execute(prepared(requestPlan), requestPlan) {
                postCount += 1
                candidate()
            }
            assertEquals(1, postCount)
            assertEquals(RequestReceiptPhase.RECEIVED, store.readReceipt("session-1", "attempt-1").phase)

            val restarted = TranslationSessionStore(root)
            val receipt = restarted.readReceipt("session-1", "attempt-1")
            val recovery = ReceiptRecoveryPlanner.plan(receipt, restarted.readManifest("session-1"), requestPlan)
            assertEquals(ReceiptRecoveryAction.READY_TO_ADOPT, recovery.action)
            assertEquals(1, postCount)

            val committed = restarted.adoptRecoveredCandidate("session-1", "attempt-1", requestPlan)
            assertNotNull(committed.committedEntry)
            assertEquals(1, postCount)
            assertEquals("مرحبًا بالعالم.", restarted.readActiveEntry("session-1", "u1")?.record?.effectiveText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun unknownRemoteOutcomeRemainsSentAndDoesNotBlindlyPostAgain() = runBlocking {
        val root = root("unknown")
        try {
            val requestPlan = plan()
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            var postCount = 0
            var failed = false

            try {
                DurableTranslationAttemptExecutor(store).execute(prepared(requestPlan), requestPlan) {
                    postCount += 1
                    error("simulated connection loss after possible submission")
                }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals(1, postCount)

            val restarted = TranslationSessionStore(root)
            val receipt = restarted.readReceipt("session-1", "attempt-1")
            assertEquals(RequestReceiptPhase.SENT, receipt.phase)
            val recovery = ReceiptRecoveryPlanner.plan(receipt, restarted.readManifest("session-1"), requestPlan)
            assertEquals(ReceiptRecoveryAction.REQUIRE_EXPLICIT_RETRY, recovery.action)
            assertEquals(AdoptionFenceResult.CURRENT, recovery.fenceResult)
            assertEquals(1, postCount)
        } finally {
            root.deleteRecursively()
        }
    }
}
