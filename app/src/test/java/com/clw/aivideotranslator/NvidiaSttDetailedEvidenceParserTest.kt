package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaSttDetailedEvidenceParserTest {
    @Test fun detailedParsePreservesExactLegacyAcceptedResultAndRawEvidence() {
        val body = """
            {
              "text": "Hello world.",
              "words": [
                {"word": "Hello", "start": 0.08, "end": 0.20, "confidence": 0.9},
                {"word": "world.", "start": 0.22, "end": 0.80, "confidence": 0.8}
              ]
            }
        """.trimIndent()

        val legacy = NvidiaSttClient.parseResponse(body, 206)
        val detailed = NvidiaSttDetailedEvidenceParser.parse(body, 206)

        assertEquals(legacy, detailed.result)
        assertEquals(206, detailed.result.httpStatus)
        assertEquals(body, detailed.timingEvidence.rawResponseUtf8)
        assertEquals(NvidiaSttTimingEvidenceInspector.inspect(body), detailed.timingEvidence)
        assertEquals(80L, detailed.result.words.first().startMs)
        assertEquals(800L, detailed.result.words.last().endMs)
    }

    @Test fun evidenceDoesNotChangeLegacyMultiSchemaParserSemantics() {
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
        val legacy = NvidiaSttClient.parseResponse(body)

        assertEquals(legacy, detailed.result)
        assertEquals(listOf("info", "root"), detailed.result.words.map { it.text })
        assertTrue(detailed.timingEvidence.hasMultipleWordSchemas)
        assertEquals(2, detailed.timingEvidence.sources.size)
    }
}
