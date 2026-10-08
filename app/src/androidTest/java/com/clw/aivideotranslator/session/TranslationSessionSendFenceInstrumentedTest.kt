package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionSendFenceInstrumentedTest {
    @Test fun manifestMutationBetweenPreparedAndSentPreventsSendTransition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "x005-send-fence-${UUID.randomUUID()}")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val source = "Hello world."
            val plan = TranslationPlanner.plan(
                SemanticSourceUnit(
                    id = "u1",
                    orderedWordIds = listOf("w1"),
                    sourceText = source,
                    sourceTextHash = sha256Utf8(source),
                    sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
                    segmentationVersion = "android-x005-send-fence",
                ),
            )
            val prepared = RequestReceipt(
                attemptId = "attempt-1",
                sessionId = "session-1",
                unitId = plan.unitId,
                epoch = 0,
                requestSignature = plan.requestSignature,
                expectedManifestRevision = 0,
                expectedActiveEntryRevisionId = null,
                phase = RequestReceiptPhase.PREPARED,
            )
            store.writeReceipt(prepared)
            store.bumpEpoch("session-1", expectedRevision = 0)

            var blocked = false
            try {
                store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
            } catch (_: IllegalStateException) {
                blocked = true
            }

            assertTrue(blocked)
            assertEquals(RequestReceiptPhase.PREPARED, store.readReceipt("session-1", "attempt-1").phase)
            assertEquals(1L, store.readManifest("session-1").revision)
            assertEquals(1L, store.readManifest("session-1").epoch)
        } finally {
            root.deleteRecursively()
        }
    }
}
