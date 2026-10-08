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
    private fun unit() = SemanticSourceUnit(
        id = "u1",
        orderedWordIds = listOf("w1"),
        sourceText = "Hello world.",
        sourceTextHash = sha256Utf8("Hello world."),
        sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
        segmentationVersion = "test",
    )

    private fun plan() = TranslationPlanner.plan(unit())

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
        assertEquals(NvidiaTranslationWireContract.ID, plan.profile.transportContractId)
    }

    @Test fun serializedBodyHasNoUnsignedFieldsOrMessages() {
        val body = JSONObject(NvidiaTranslationClient.requestBody(plan()))
        assertEquals(setOf("model", "messages", "temperature", "max_tokens", "stream"), body.keys().asSequence().toSet())

        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        for (index in 0 until messages.length()) {
            assertEquals(setOf("role", "content"), messages.getJSONObject(index).keys().asSequence().toSet())
        }
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("en-ar", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("Hello world.", messages.getJSONObject(1).getString("content"))
    }

    @Test fun transportContractVersionBindsMethodMediaTypesAndRetrySemantics() {
        val plan = plan()
        assertEquals("nvidia-chat-http-v1", NvidiaTranslationWireContract.ID)
        assertEquals(NvidiaTranslationWireContract.ID, plan.profile.transportContractId)
        assertEquals("POST", NvidiaTranslationWireContract.METHOD)
        assertEquals("application/json", NvidiaTranslationWireContract.ACCEPT_MEDIA_TYPE)
        assertEquals("application/json; charset=utf-8", NvidiaTranslationWireContract.REQUEST_MEDIA_TYPE)
        assertFalse(NvidiaTranslationWireContract.FOLLOW_REDIRECTS)
        assertFalse(NvidiaTranslationWireContract.RETRY_ON_CONNECTION_FAILURE)
    }

    @Test fun endpointDriftChangesSignatureAndIsRejected() {
        val original = plan()
        val changed = TranslationPlanner.plan(unit(), profile = original.profile.copy(endpoint = "https://example.invalid/v1/chat/completions"))
        assertFalse(original.requestSignature == changed.requestSignature)
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("endpoint drift must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun transportContractDriftChangesSignatureAndIsRejected() {
        val original = plan()
        val changed = TranslationPlanner.plan(unit(), profile = original.profile.copy(transportContractId = "nvidia-chat-http-v2"))
        assertFalse(original.requestSignature == changed.requestSignature)
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("transport contract drift must be rejected")
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
