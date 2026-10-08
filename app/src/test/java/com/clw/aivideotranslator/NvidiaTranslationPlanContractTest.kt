package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class NvidiaTranslationPlanContractTest {
    private fun plan() = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u1",
            orderedWordIds = listOf("w1"),
            sourceText = "Hello world.",
            sourceTextHash = sha256Utf8("Hello world."),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "test",
        ),
    )

    @Test fun defaultV1PlanMatchesFixedNvidiaRequestBodyContract() {
        NvidiaTranslationPlanContract.requireSupported(plan())
    }

    @Test fun requestSignatureContractMatchesSerializedNvidiaBody() {
        val plan = plan()
        NvidiaTranslationPlanContract.requireSupported(plan)
        val body = JSONObject(NvidiaTranslationClient.requestBody(plan))
        val messages = body.getJSONArray("messages")
        val system = messages.getJSONObject(0)
        val user = messages.getJSONObject(1)

        assertEquals(plan.profile.model, body.getString("model"))
        assertEquals(plan.profile.systemContent, system.getString("content"))
        assertEquals("system", system.getString("role"))
        assertEquals(plan.exactSourceText, user.getString("content"))
        assertEquals("user", user.getString("role"))
        assertEquals(plan.profile.temperature, body.getInt("temperature"))
        assertEquals(plan.profile.maxTokens, body.getInt("max_tokens"))
        assertEquals(plan.profile.stream, body.getBoolean("stream"))
        assertEquals(NvidiaTranslationClient.ENDPOINT, plan.profile.endpoint)
    }

    @Test fun endpointDriftChangesSignatureAndIsRejected() {
        val original = plan()
        val changedProfile = original.profile.copy(endpoint = "https://example.invalid/v1/chat/completions")
        val changed = TranslationPlanner.plan(
            SemanticSourceUnit(
                id = "u1",
                orderedWordIds = listOf("w1"),
                sourceText = "Hello world.",
                sourceTextHash = sha256Utf8("Hello world."),
                sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
                segmentationVersion = "test",
            ),
            profile = changedProfile,
        )
        assertFalse(original.requestSignature == changed.requestSignature)
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("endpoint drift must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun profileDriftIsRejectedBeforeDurableSendBoundary() {
        val changed = plan().let { it.copy(profile = it.profile.copy(maxTokens = 2048)) }
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("profile drift must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun tamperedExactSourceIsRejectedBeforeDurableSendBoundary() {
        val changed = plan().copy(exactSourceText = "Different source")
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("tampered request plan must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
