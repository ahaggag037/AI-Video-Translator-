package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TranslationRequestPlanStoreTest {
    private fun plan() = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u0001",
            orderedWordIds = listOf("w000001", "w000002"),
            sourceText = "Hello world.",
            sourceTextHash = sha256Utf8("Hello world."),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "legacy-parity-v1",
        )
    )

    private fun withRoot(block: (java.io.File, TranslationRequestPlanStore) -> Unit) {
        val root = Files.createTempDirectory("request-plan-store").toFile()
        try {
            val store = TranslationRequestPlanStore(root) { sessionId ->
                require(sessionId == "session-1") { "unknown session" }
            }
            block(root, store)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun roundTripPreservesExactWirePlanWithoutTimingOrCredentialFields() = withRoot { root, store ->
        val expected = plan()
        assertEquals(expected, store.publish("session-1", expected))
        assertEquals(expected, store.read("session-1", expected.unitId, expected.requestSignature))

        val file = java.io.File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        val json = file.readText()
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("authorization", ignoreCase = true))
        assertFalse(json.contains("startMs", ignoreCase = true))
        assertFalse(json.contains("endMs", ignoreCase = true))
        assertFalse(json.contains("audioInterval", ignoreCase = true))
        assertTrue(json.contains("Hello world."))
    }

    @Test fun identicalRepublishIsIdempotentButCollisionFailsClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        assertEquals(expected, store.publish("session-1", expected))

        val file = java.io.File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        file.writeText(file.readText().replace("Hello world.", "Tampered source"))
        assertTrue(runCatching { store.publish("session-1", expected) }.isFailure)
    }

    @Test fun corruptOversizedAndPathMismatchedPlanFailClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        val file = java.io.File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")

        file.writeText("{")
        assertTrue(runCatching { store.read("session-1", expected.unitId, expected.requestSignature) }.isFailure)

        file.writeText("x".repeat(TranslationRequestPlanCodec.MAX_BYTES + 1))
        assertTrue(runCatching { store.read("session-1", expected.unitId, expected.requestSignature) }.isFailure)
    }

    @Test fun codecRejectsSignatureTamperAndUnknownFields() {
        val expected = plan()
        val encoded = TranslationRequestPlanCodec.encode(expected)
        val signatureTamper = encoded.replace(expected.requestSignature, "0".repeat(64))
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(signatureTamper) }.isFailure)

        val root = org.json.JSONObject(encoded).put("authorization", "must-not-be-accepted")
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(root.toString()) }.isFailure)
    }
}
