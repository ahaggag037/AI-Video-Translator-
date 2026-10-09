package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TranslationRequestPlanStoreTest {
    private fun plan(
        unitId: String = "u0001",
        text: String = "Hello world.",
    ) = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = unitId,
            orderedWordIds = listOf("w000001", "w000002"),
            sourceText = text,
            sourceTextHash = sha256Utf8(text),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "legacy-parity-v1",
        )
    )

    private fun withRoot(block: (File, TranslationRequestPlanStore) -> Unit) {
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

        val file = File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        val json = file.readText()
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("authorization", ignoreCase = true))
        assertFalse(json.contains("mediaUri", ignoreCase = true))
        assertFalse(json.contains("contentUri", ignoreCase = true))
        assertFalse(json.contains("startMs", ignoreCase = true))
        assertFalse(json.contains("endMs", ignoreCase = true))
        assertFalse(json.contains("audioInterval", ignoreCase = true))
        assertFalse(json.contains("candidateText", ignoreCase = true))
        assertTrue(json.contains("Hello world."))
    }

    @Test fun restartIndexListsPublishedPlansDeterministicallyAndIgnoresOnlyTempFiles() = withRoot { root, store ->
        val second = plan("u0002", "Second unit.")
        val first = plan("u0001", "First unit.")
        store.publish("session-1", second)
        store.publish("session-1", first)
        val unitDirectory = File(root, "session-1/plans/u0001")
        File(unitDirectory, ".orphan.tmp").writeText("partial")

        assertEquals(listOf(first, second), store.list("session-1"))

        File(unitDirectory, "unexpected.bin").writeText("x")
        assertTrue(runCatching { store.list("session-1") }.isFailure)
    }

    @Test fun identicalRepublishIsIdempotentButCollisionFailsClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        assertEquals(expected, store.publish("session-1", expected))

        val file = File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        file.writeText(file.readText().replace("Hello world.", "Tampered source"))
        assertTrue(runCatching { store.publish("session-1", expected) }.isFailure)
    }

    @Test fun corruptPlanFailsClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        val file = File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        file.writeText("{")
        assertTrue(runCatching { store.read("session-1", expected.unitId, expected.requestSignature) }.isFailure)
    }

    @Test fun oversizedPlanFailsClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        val file = File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        file.writeText("x".repeat(TranslationRequestPlanCodec.MAX_BYTES + 1))
        assertTrue(runCatching { store.read("session-1", expected.unitId, expected.requestSignature) }.isFailure)
    }

    @Test fun pathIdentityMismatchFailsClosed() = withRoot { root, store ->
        val expected = plan()
        store.publish("session-1", expected)
        val original = File(root, "session-1/plans/${expected.unitId}/${expected.requestSignature}.json")
        val wrongUnit = "u9999"
        val wrongDir = File(root, "session-1/plans/$wrongUnit").apply { mkdirs() }
        original.copyTo(File(wrongDir, "${expected.requestSignature}.json"))

        assertTrue(runCatching {
            store.read("session-1", wrongUnit, expected.requestSignature)
        }.isFailure)
    }

    @Test fun codecRejectsMalformedUnknownWrongTypeUnsafeIdentityAndUnsupportedSchema() {
        val expected = plan()
        val encoded = TranslationRequestPlanCodec.encode(expected)
        assertTrue(runCatching { TranslationRequestPlanCodec.decode("{") }.isFailure)

        val unknown = JSONObject(encoded).put("authorization", "must-not-be-accepted")
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(unknown.toString()) }.isFailure)

        val wrongType = JSONObject(encoded)
        wrongType.getJSONObject("profile").put("maxTokens", "1024")
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(wrongType.toString()) }.isFailure)

        val unsafe = JSONObject(encoded).put("unitId", "../escape")
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(unsafe.toString()) }.isFailure)

        val unsupported = JSONObject(encoded).put("schemaVersion", 2)
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(unsupported.toString()) }.isFailure)
    }

    @Test fun codecRejectsRequestSignatureTamper() {
        val expected = plan()
        val encoded = TranslationRequestPlanCodec.encode(expected)
        val signatureTamper = encoded.replace(expected.requestSignature, "0".repeat(64))
        assertTrue(runCatching { TranslationRequestPlanCodec.decode(signatureTamper) }.isFailure)
    }
}
