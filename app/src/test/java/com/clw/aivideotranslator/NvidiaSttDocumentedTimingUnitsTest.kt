package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Test

class NvidiaSttDocumentedTimingUnitsTest {
    @Test fun shortMillisecondOffsetsAreNeverReinterpretedAsSeconds() {
        val body = """
            {
              "text":"hi",
              "words":[
                {"word":"hi","start_time":80,"end_time":100,"confidence":0.9}
              ]
            }
        """.trimIndent()

        val result = NvidiaSttClient.parseResponse(body)
        assertEquals(80L, result.words.single().startMs)
        assertEquals(100L, result.words.single().endMs)
    }

    @Test fun fractionalMillisecondValuesAreTruncatedOnlyAtLegacyIntegerBoundary() {
        val body = """
            {
              "text":"hello",
              "words":[
                {"word":"hello","start_time":123.9,"end_time":456.8}
              ]
            }
        """.trimIndent()

        val result = NvidiaSttClient.parseResponse(body)
        assertEquals(123L, result.words.single().startMs)
        assertEquals(456L, result.words.single().endMs)
    }
}