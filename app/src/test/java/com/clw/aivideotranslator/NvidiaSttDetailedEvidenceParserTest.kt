package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaSttDetailedEvidenceParserTest {
    @Test fun detailedParsePreservesDocumentedMillisecondOffsetsAndRawEvidence() {
        val body = """
            {
              "text": "Hello world.",
              "words": [
                {"word": "Hello", "start_time": 20, "end_time": 40, "confidence": 0.9},
                {"word": "world.", "start_time": 50, "end_time": 80, "confidence": 0.8}
              ]
            }
        """.trimIndent()

        val accepted = NvidiaSttClient.parseResponse(body, 206)
        val detailed = NvidiaSttDetailedEvidenceParser.parse(body, 206)

        assertEquals(accepted, detailed.result)
        assertEquals(206, detailed.result.httpStatus)
        assertEquals(body, detailed.timingEvidence.rawResponseUtf8)
        assertEquals(NvidiaSttTimingEvidenceInspector.inspect(body), detailed.timingEvidence)
        // These deliberately stay below the old 120-value heuristic threshold. NVIDIA WordInfo
        // defines them as milliseconds, so magnitude must never promote them to seconds.
        assertEquals(20L, detailed.result.words.first().startMs)
        assertEquals(80L, detailed.result.words.last().endMs)
    }

    @Test fun evidenceDoesNotChangeMultiSchemaParserSemantics() {
        val body = """
            {
              "text": "accepted",
              "words_info": {
                "words": [{"word": "info", "start_ms": 1000, "end_ms": 1200}]
              },
              "words": [
                {"word": "root", "start_ms": 1300, "end_ms": 1500}
              ]
            }
        """.trimIndent()

        val detailed = NvidiaSttDetailedEvidenceParser.parse(body)
        val accepted = NvidiaSttClient.parseResponse(body)

        assertEquals(accepted, detailed.result)
        assertEquals(listOf("info", "root"), detailed.result.words.map { it.text })
        assertTrue(detailed.timingEvidence.hasMultipleWordSchemas)
        assertEquals(2, detailed.timingEvidence.sources.size)
    }
}
