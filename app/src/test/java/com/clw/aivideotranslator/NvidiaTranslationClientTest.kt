package com.clw.aivideotranslator

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

    private fun response(content: Any, finish: String = "stop"): String =
        JSONObject().put("choices", org.json.JSONArray().put(JSONObject()
            .put("finish_reason", finish)
            .put("message", JSONObject().put("role", "assistant").put("content", content)))).toString()

    @Test fun readsOnlyCompletedText() {
        assertEquals("مرحبًا بالعالم.", NvidiaTranslationClient.parseResponse(response("مرحبًا بالعالم.")))
    }

    @Test fun rejectsTruncationBlankMalformedAndNonText() {
        val invalid = listOf(response("مرحبًا", "length"), response(" "), response(42),
            "{}", "not-json", response("00:00:01,000 --> 00:00:02,000"))
        invalid.forEach {
            try { NvidiaTranslationClient.parseResponse(it); fail("Must reject invalid output") }
            catch (_: IllegalStateException) { }
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
