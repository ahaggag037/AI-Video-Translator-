package com.clw.aivideotranslator

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NvidiaTranslationClientTest {
    @Test fun requestContainsTextOnlyAndDocumentedTranslationContract() {
        val root = JSONObject(NvidiaTranslationClient.requestBody("Hello world."))
        assertEquals("nvidia/riva-translate-4b-instruct-v2", root.getString("model"))
        val messages = root.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("en-ar", messages.getJSONObject(0).getString("content"))
        assertEquals("Hello world.", messages.getJSONObject(1).getString("content"))
        assertFalse(root.has("response_format"))
        assertEquals(1024, root.getInt("max_tokens"))
        assertFalse(root.getBoolean("stream"))
        val body = root.toString()
        listOf("startMs", "endMs", "sourceUnitId", "apiKey").forEach { assertFalse(body.contains(it)) }
    }

    private fun response(
        content: Any,
        finish: Any? = "stop",
        includeRole: Boolean = true,
        toolCalls: Any? = null,
    ): String {
        val message = JSONObject().put("content", content)
        if (includeRole) message.put("role", "assistant")
        if (toolCalls != null) message.put("tool_calls", toolCalls)
        val choice = JSONObject().put("message", message)
        if (finish != null) choice.put("finish_reason", finish)
        return JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    @Test fun readsCompletedScalarText() {
        assertEquals("مرحبًا بالعالم.", NvidiaTranslationClient.parseResponse(response("مرحبًا بالعالم.")))
    }

    @Test fun acceptsProviderEnvelopeWithoutFinishReasonOrRole() {
        assertEquals(
            "مرحبًا بالعالم.",
            NvidiaTranslationClient.parseResponse(response("مرحبًا بالعالم.", finish = null, includeRole = false)),
        )
    }

    @Test fun acceptsEmptyToolCallsMetadata() {
        assertEquals(
            "مرحبًا بالعالم.",
            NvidiaTranslationClient.parseResponse(
                response("مرحبًا بالعالم.", toolCalls = JSONArray())
            ),
        )
    }

    @Test fun acceptsOpenAiStyleTextParts() {
        val parts = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "مرحبًا "))
            .put(JSONObject().put("type", "text").put("text", "بالعالم."))
        assertEquals("مرحبًا بالعالم.", NvidiaTranslationClient.parseResponse(response(parts)))
    }

    @Test fun rejectsTruncationBlankMalformedAndNonText() {
        val invalid = listOf(
            response("مرحبًا", "length"),
            response(" "),
            response(42),
            "{}",
            "not-json",
            response("00:00:01,000 --> 00:00:02,000"),
        )
        invalid.forEach {
            try {
                NvidiaTranslationClient.parseResponse(it)
                fail("Must reject invalid output")
            } catch (_: IllegalStateException) {
                // expected
            }
        }
    }

    @Test fun rejectsActualToolCallsUnexpectedRoleAndCompletionReason() {
        val wrongRole = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("finish_reason", "stop")
            .put("message", JSONObject().put("role", "tool").put("content", "مرحبًا"))))
            .toString()
        val actualToolCall = JSONArray().put(
            JSONObject()
                .put("type", "function")
                .put("function", JSONObject().put("name", "translate").put("arguments", "{}"))
        )
        listOf(
            wrongRole,
            response("مرحبًا", toolCalls = actualToolCall),
            response("مرحبًا", "tool_calls"),
            response("مرحبًا", "content_filter"),
        ).forEach {
            try {
                NvidiaTranslationClient.parseResponse(it)
                fail("Must reject unsafe/incomplete envelope")
            } catch (_: IllegalStateException) {
                // expected
            }
        }
    }

    @Test fun existingSttParserStillProvidesMillisecondFixture() {
        val parsed = NvidiaSttClient.parseResponse("""
            {"text":"Hello world","words":[
              {"word":"Hello","start_time":80,"end_time":400},
              {"word":"world","start_time":500,"end_time":60000}]}
        """.trimIndent())
        assertEquals(80L, parsed.firstWordStartMs)
        assertEquals(60000L, parsed.lastWordEndMs)
        assertEquals("Hello world", parsed.transcript)
    }
}
